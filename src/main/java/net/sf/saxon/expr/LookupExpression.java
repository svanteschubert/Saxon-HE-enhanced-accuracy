////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.SimpleLazyFunction;
import net.sf.saxon.ma.arrays.ArrayFunctionSet;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.ma.map.RecordType;
import net.sf.saxon.ma.map.Shape;
import net.sf.saxon.om.*;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.type.coercion.SequenceCoercer;
import net.sf.saxon.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;


/**
 * A lookup expression is an expression of the form A?B. Here A must be a sequence of maps or arrays.
 * In the general case B is an expression that computes a key/index into the map or array; the case where
 * B is constant needs to be handled efficiently. The class also implements the unary lookup expression
 * ?B, which is interpreted as .?B. It does not handle the case A?* - that is handled as a LookupAllExpression.
 */

public class LookupExpression extends BinaryExpression {

    private boolean isClassified = false;
    protected boolean isArrayLookup = false;
    protected boolean isMapLookup = false;
    protected boolean isSingleContainer = false;
    protected boolean isSingleEntry = false;

    /**
     * Constructor
     *
     * @param start The left hand operand (which must always select a sequence of maps or arrays).
     * @param step  The step to be followed from each map/array in the start expression to yield a new
     *              sequence
     */

    public LookupExpression(Expression start, Expression step) {
        super(start, OperatorSymbol.LOOKUP, step);
    }

    @Override
    protected OperandRole getOperandRole(int arg) {
        return arg == 0 ? OperandRole.INSPECT : OperandRole.ABSORB;
    }

    @Override
    public String getExpressionName() {
        return "lookupExp";
    }


    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the type of the expression, as far as this is known. Prior to type-checking,
     * the method returns {@link AnyItemType}
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        if (isClassified) {
            if (isArrayLookup) {
                ItemType arrayType = getLhsExpression().getItemType();
                if (arrayType instanceof ArrayItemType) {
                    return ((ArrayItemType) arrayType).getMemberType().getPrimaryType();
                }
            } else if (isMapLookup) {
                ItemType mapType = getLhsExpression().getItemType();
                if (mapType instanceof MapType) {
                    return ((MapType) mapType).getValueType().getPrimaryType();
                }
            }
        }
        return AnyItemType.getInstance();
    }


    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @param contextItemType not used
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return getItemType().getUType();
    }

    @CSharpReplaceBody(code="return Saxon.Hej.type.ChoiceItemType.of(Saxon.Hej.ma.map.MapType.ANY_MAP_TYPE,\n"
            + "                Saxon.Hej.ma.arrays.ArrayItemType.ANY_ARRAY_TYPE);")
    public static ItemType getRequiredItemTypeOfLHS() {
        return ChoiceItemType.of(MapType.ANY_MAP_TYPE, ArrayItemType.ANY_ARRAY_TYPE, ExternalObjectType.THE_INSTANCE);
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        Configuration config = visitor.getConfiguration();
        TypeHierarchy th = config.getTypeHierarchy();

        if (Literal.isEmptySequence(getLhsExpression())) {
            return getLhsExpression();
        }

        // Check the first operand
        getLhs().typeCheck(visitor, contextInfo);

        ItemType containerType = getLhsExpression().getItemType();
        if (containerType == ErrorType.getInstance()) {
            return getLhsExpression();
        }
        isArrayLookup = containerType instanceof ArrayItemType;
        isMapLookup = containerType instanceof MapType;
        if (th.isSubType(containerType, AnyExternalObjectType.THE_INSTANCE)) {
            config.checkLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION, "use of lookup expressions on external objects", -1);
            return config.makeObjectLookupExpression(getLhsExpression(), getRhsExpression())
                    .typeCheck(visitor, contextInfo);
        }
        isSingleContainer = getLhsExpression().getCardinality() == StaticProperty.EXACTLY_ONE;

        if (!isArrayLookup && !isMapLookup) {
            Supplier<RoleDiagnostic> role0 = () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, "?", 0);
            setLhsExpression(SequenceCoercer.makeSequenceCoercer(
                    getLhsExpression(),
                    SequenceType.zeroOrMore(getRequiredItemTypeOfLHS()),
                    role0,
                    true));

//            if (th.relationship(containerType, MapType.ANY_MAP_TYPE) == Affinity.DISJOINT) {
//                if (th.relationship(containerType, AnyFunctionType.INSTANCE) == Affinity.DISJOINT &&
//                        th.relationship(containerType, AnyExternalObjectType.THE_INSTANCE) == Affinity.DISJOINT) {
//                    if (visitor.getStaticContext().getXPathVersion() >= 40 &&
//                            th.relationship(containerType, AnyJNodeType.getInstance()) != Affinity.DISJOINT) {
//                        Supplier<RoleDiagnostic> role0 = () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, "?", 0);
//                        setLhsExpression(SequenceCoercer.makeSequenceCoercer(
//                                getLhsExpression(),
//                                SequenceType.zeroOrMore(
//                                        ChoiceItemType.of(MapType.ANY_MAP_TYPE, AnyFunctionType.INSTANCE)),
//                                role0,
//                                true));
//                    } else if (Cardinality.allowsZero(getLhsExpression().getCardinality())) {
//                        visitor.issueWarning("The left-hand operand of '?' must be a map or an array; the expression can succeed only if the operand is an empty sequence " +
//                                                     containerType, SaxonErrorCode.SXWN9026, getLocation());
//                    } else {
//                        throw new XPathException("The left-hand operand of '?' must be a map or an array; "
//                                                         + "the supplied expression is of type " + containerType, "XPTY0004")
//                                .withLocation(getLocation())
//                                .asTypeError()
//                                .withFailingExpression(this);
//                    }
//                }
//            }
        }

        // Now check the second operand

        getRhs().typeCheck(visitor, contextInfo);
        Supplier<RoleDiagnostic> role1 = () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, "?", 1);
        TypeChecker tc = config.getTypeChecker(false);
        SequenceType req = BuiltInAtomicType.ANY_ATOMIC.zeroOrMore();
        if (isArrayLookup) {
            req = BuiltInAtomicType.INTEGER.zeroOrMore();
        }
        setRhsExpression(tc.staticTypeCheck(getRhsExpression(), req, role1, visitor));
        isSingleEntry = getRhsExpression().getCardinality() == StaticProperty.EXACTLY_ONE;

        isClassified = true;
        return this;
    }

    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getLhs().optimize(visitor, contextInfo);
        getRhs().optimize(visitor, contextInfo);
        return this;
    }


    /**
     * Return the estimated cost of evaluating an expression. This is a very crude measure based
     * on the syntactic form of the expression (we have no knowledge of data values). We take
     * the cost of evaluating a simple scalar comparison or arithmetic expression as 1 (one),
     * and we assume that a sequence has length 5. The resulting estimates may be used, for
     * example, to reorder the predicates in a filter expression so cheaper predicates are
     * evaluated first.
     * @return a rough estimate of the cost of evaluation
     */
    @Override
    public double getCost() {
        return getLhsExpression().getCost() * getRhsExpression().getCost();
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     * {@link #PROCESS_METHOD}
     */
    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings a mutable list of (old binding, new binding) pairs
     *                   that is used to update the bindings held in any
     *                   local variable references that are copied.
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public LookupExpression copy(RebindingMap rebindings) {
        LookupExpression exp = new LookupExpression(getLhsExpression().copy(rebindings), getRhsExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        exp.isArrayLookup = isArrayLookup;
        exp.isMapLookup = isMapLookup;
        exp.isSingleEntry = isSingleEntry;
        exp.isSingleContainer = isSingleContainer;
        return exp;
    }


    /**
     * Determine the static cardinality of the expression
     */

    @Override
    protected int computeCardinality() {
        if (isSingleContainer && isSingleEntry) {
            if (isArrayLookup) {
                ItemType arrayType = getLhsExpression().getItemType();
                if (arrayType instanceof ArrayItemType) {
                    int memberCard = ((ArrayItemType) arrayType).getMemberType().getCardinality();
                    if (getRetainedStaticContext().getPackageData().getHostLanguageVersion() >= 40) {
                        return (Cardinality.union(memberCard,
                                                  StaticProperty.ALLOWS_ZERO));
                    } else {
                        return memberCard;
                    }
                }
            } else if (isMapLookup) {
                ItemType mapType = getLhsExpression().getItemType();
                if (mapType instanceof RecordType && getRhsExpression() instanceof StringLiteral) {
                    String fieldName = ((StringLiteral) getRhsExpression()).stringify();
                    SequenceType fieldType = ((RecordType) mapType).getFieldType(fieldName);
                    if (fieldType == null) {
                        return ((RecordType) mapType).isExtensible() ? StaticProperty.ALLOWS_ZERO_OR_MORE : StaticProperty.ALLOWS_ZERO;
                    } else {
                        return fieldType.getCardinality();
                    }
                } else if (mapType instanceof MapType) {
                    return (Cardinality.union(((MapType) mapType).getValueType().getCardinality(),
                                              StaticProperty.ALLOWS_ZERO));
                }
            }
        }
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        if (!(other instanceof LookupExpression)) {
            return false;
        }
        LookupExpression p = (LookupExpression) other;
        return getLhsExpression().isEqual(p.getLhsExpression())
                && getRhsExpression().isEqual(p.getRhsExpression());
    }

    /**
     * get HashCode for comparing two expressions
     */

    @Override
    protected int computeHashCode() {
        return getLhsExpression().hashCode()
                ^ getRhsExpression().hashCode();
    }

    /**
     * Iterate the lookup-expression in a given context
     *
     * @param context the evaluation context
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(final XPathContext context) throws XPathException {
        return makeElaborator().elaborateForPull().iterate(context);
    }

    protected static SequenceIterator optionalGroundedValueIterator(GroundedValue value) {
        if (value == null) {
            return EmptyIterator.INSTANCE;
        } else {
            return value.iterate();
        }
    }

    protected static void mustBeArrayOrMap(Expression exp, Item baseItem) throws XPathException {
        throw new XPathException("The items on the LHS of the '?' operator must be maps or arrays; but value (" +
                                                              baseItem.toShortString() + ") was supplied", "XPTY0004")
                .asTypeError()
                .withLocation(exp.getLocation())
                .withFailingExpression(exp);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("lookup", this);
        getLhsExpression().export(destination);
        getRhsExpression().export(destination);
        destination.endElement();
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     *
     * @return a representation of the expression as a string
     */

    public String toString() {
        String rhs;
        if (getRhsExpression() instanceof Literal) {
            Literal lit = (Literal) getRhsExpression();
            if (lit instanceof StringLiteral && NameChecker.isValidNCName(((StringLiteral) lit).getGroundedValue().codePoints())) {
                rhs = ((StringLiteral) lit).stringify();
            } else if (lit.getGroundedValue() instanceof Int64Value) {
                rhs = lit.getGroundedValue().toString();
            } else {
                rhs = ExpressionTool.parenthesize(lit);
            }
        } else {
            rhs = ExpressionTool.parenthesize(getRhsExpression());
        }
        return ExpressionTool.parenthesize(getLhsExpression()) + "?" + rhs;
    }

    @Override
    public Elaborator getElaborator() {
        return new LookupElaborator();
    }

    public static class LookupElaborator extends PullElaborator {

        public PullEvaluator elaborateForPull() {
            LookupExpression expr = (LookupExpression) getExpression();

            if (expr.isArrayLookup) {
                if (expr.isSingleContainer && expr.isSingleEntry) {
                    ItemEvaluator lhs = expr.getLhsExpression().makeElaborator().elaborateForItem();
                    ItemEvaluator rhs = expr.getRhsExpression().makeElaborator().elaborateForItem();

                    return context -> {
                        ArrayItem array = (ArrayItem) lhs.eval(context);
                        IntegerValue subscript = (IntegerValue) rhs.eval(context);
                        int index = ArrayFunctionSet.checkSubscript(subscript, array.arrayLength());
                        return array.get(index - 1).iterate();
                    };
                } else if (expr.isSingleEntry) {
                    PullEvaluator lhs = expr.getLhsExpression().makeElaborator().elaborateForPull();
                    ItemEvaluator rhs = expr.getRhsExpression().makeElaborator().elaborateForItem();

                    return context -> {
                        IntegerValue subscriptValue = (IntegerValue) rhs.eval(context);
                        return MappingIterator.map(lhs.iterate(context), baseItem -> {
                            ArrayItem array = (ArrayItem) baseItem;
                            int index = ArrayFunctionSet.checkSubscript(subscriptValue, array.arrayLength());
                            return array.get(index - 1).iterate();
                        });
                    };

                } else {
                    PullEvaluator lhs = expr.getLhsExpression().makeElaborator().elaborateForPull();
                    PullEvaluator rhs = expr.getRhsExpression().makeElaborator().elaborateForPull();
                    return context -> {
                        SequenceIterator baseIterator = lhs.iterate(context);
                        GroundedValue rhsValue;
                        try {
                            rhsValue = SequenceTool.toGroundedValue(rhs.iterate(context));
                        } catch (UncheckedXPathException e) {
                            throw e.getXPathException();
                        }
                        return MappingIterator.map(baseIterator, baseItem ->
                                MappingIterator.map(rhsValue.iterate(), index -> {
                                    ArrayItem array = (ArrayItem) baseItem;
                                    int ix = ArrayFunctionSet.checkSubscript((IntegerValue)index, array.arrayLength());
                                    return array.get(ix - 1).iterate();
                                })
                        );
                    };

                }

            } else if (expr.isMapLookup) {

                if (expr.isSingleContainer && expr.isSingleEntry) {
                    ItemEvaluator lhs = expr.getLhsExpression().makeElaborator().elaborateForItem();
                    ItemEvaluator rhs = expr.getRhsExpression().makeElaborator().elaborateForItem();

                    return context -> {
                        MapItem map = (MapItem) lhs.eval(context);
                        AtomicValue key = (AtomicValue) rhs.eval(context);
                        return optionalGroundedValueIterator(map.get(key));
                    };

                } else if (expr.isSingleEntry) {
                    PullEvaluator lhs = expr.getLhsExpression().makeElaborator().elaborateForPull();
                    ItemEvaluator rhs = expr.getRhsExpression().makeElaborator().elaborateForItem();
                    return context -> {
                        SequenceIterator baseIterator = lhs.iterate(context);
                        AtomicValue key = (AtomicValue) rhs.eval(context);
                        return MappingIterator.map(baseIterator, baseItem ->
                                optionalGroundedValueIterator(((MapItem) baseItem).get(key)));
                    };

                } else {
                    PullEvaluator lhs = expr.getLhsExpression().makeElaborator().elaborateForPull();
                    PullEvaluator rhs = expr.getRhsExpression().makeElaborator().elaborateForPull();
                    return context -> {
                        SequenceIterator baseIterator = lhs.iterate(context);
                        GroundedValue rhsVal;
                        try {
                            rhsVal = SequenceTool.toGroundedValue(rhs.iterate(context));
                        } catch (UncheckedXPathException e) {
                            throw e.getXPathException();
                        }
                        return MappingIterator.map(baseIterator, baseItem ->
                                MappingIterator.map(rhsVal.iterate(), index ->
                                        optionalGroundedValueIterator(((MapItem) baseItem).get((AtomicValue) index))
                                ));
                    };

                }
            } else {
                // modifier = content, type of LHS statically unknown
                PullEvaluator lhs = expr.getLhsExpression().makeElaborator().elaborateForPull();
                PullEvaluator rhs = expr.getRhsExpression().makeElaborator().elaborateForPull();
                return context -> {
                    SequenceIterator baseIterator = lhs.iterate(context);
                    GroundedValue rhsVal;
                    try {
                        rhsVal = SequenceTool.toGroundedValue(rhs.iterate(context));
                    } catch (UncheckedXPathException e) {
                        throw e.getXPathException();
                    }
                    MappingFunction mappingFunction = SequenceMapper.of(baseItem -> {
                        switch (baseItem.getGenre()) {
                            case ARRAY: {
                                MappingFunction arrayAccess = SequenceMapper.of(index -> {
                                    if (index instanceof IntegerValue) {
//                                        int i = ((IntegerValue)index).asSubscript();
//                                        if (i > 0 && i <= ((ArrayItem) baseItem).arrayLength()) {
//                                            GroundedValue member = ((ArrayItem) baseItem).get(i - 1);
//                                            return member.iterate();
//                                        }
                                        int subscript = ArrayFunctionSet.checkSubscript(
                                                (IntegerValue) index, ((ArrayItem) baseItem).arrayLength()) - 1;
                                        GroundedValue member = ((ArrayItem) baseItem).get(subscript);
                                        return member.iterate();
                                    }
                                    return EmptyIterator.INSTANCE;
                                    //                                            throw new XPathException(
//                                                    "An item on the LHS of the '?' operator (" + expr.getLhsExpression().toShortString() + ") is an array, but a value on the RHS of the operator (" +
//                                                            baseItem.toShortString() + ") is not an integer", "XPTY0004")
//                                                    .asTypeError()
//                                                    .withLocation(expr.getLocation())
//                                                    .withFailingExpression(expr);
                                    //}
                                });
                                SequenceIterator rhsIter = rhsVal.iterate();
                                return new MappingIterator(rhsIter, arrayAccess);
                            }
                            case MAP: {
                                SequenceIterator rhsIter = rhsVal.iterate();
                                return MappingIterator.map(rhsIter, key ->
                                        optionalGroundedValueIterator(((MapItem) baseItem).get((AtomicValue) key))
                                );
                            }
                            case EXTERNAL: {
                                if (!(rhsVal instanceof StringValue)) {
                                    throw new XPathException(
                                            "An item on the LHS of the '?' operator is an external object, but a value on the RHS of the operator (" +
                                                    baseItem.toShortString() + ") is not a singleton string", "XPTY0004")
                                            .asTypeError()
                                            .withLocation(expr.getLocation())
                                            .withFailingExpression(expr);
                                }
                                String key = ((StringValue) rhsVal).getStringValue();
                                final GroundedValue entry = context.getConfiguration().externalObjectAsMap(
                                        (ObjectValue<?>) baseItem, key).get((StringValue) rhsVal);
                                if (entry == null) {
                                    throw new XPathException("There is no unique method named " + key +
                                                                     " in the external object of type " + ((ObjectValue<?>) baseItem).getObject().getClass().getName(), "XPTY0004");
                                }
                                return entry.iterate();
                            }
                            default: {
                                mustBeArrayOrMap(expr, baseItem);
                                return null;
                            }
                        }
                    });
                    return new MappingIterator(baseIterator, mappingFunction);

                };
            }
        }
    }

    private final static Shape entryShape = new Shape(
            new Twine8("key"),
            new Twine8("value"),
            new Twine8("parent"),
            new Twine8("ancestors"),
            new Twine8("root"));

    public static MapItem makeEntry(AtomicValue key, GroundedValue value, Item container) {
        FunctionItem parent = new SimpleLazyFunction(() -> container, SequenceType.SINGLE_ITEM);
        Item rootItem = container;
        List<Item> ancestorItems = new ArrayList<>();
        ancestorItems.add(container);

        FunctionItem ancestors = new SimpleLazyFunction(
                () -> SequenceExtent.makeSequenceExtent(ancestorItems), SequenceType.ANY_SEQUENCE);
        FunctionItem root = new SimpleLazyFunction(
                () -> rootItem, SequenceType.SINGLE_ITEM);
        return entryShape.make(key, value, parent, ancestors, root);
    }



}

