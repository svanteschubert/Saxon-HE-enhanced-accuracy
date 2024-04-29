////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.expr.instruct.TerminationException;
import net.sf.saxon.expr.instruct.ValueOf;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.Error;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomizingIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.UntypedAtomizingIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.EmptySequence;

import java.util.Objects;
import java.util.function.Supplier;


/**
 * An Atomizer is an expression corresponding essentially to the fn:data() function: it
 * maps a sequence by replacing nodes with their typed values
 */

public final class Atomizer extends UnaryExpression {

    private boolean untyped = false;       //set to true if it is known that the nodes being atomized will be untyped
    private boolean singleValued = false; // set to true if all atomized nodes will atomize to a single atomic value
    private ItemType operandItemType = null;
    private Supplier<RoleDiagnostic> roleSupplier = null;

    /**
     * Constructor
     *
     * @param sequence the sequence to be atomized
     * @param role (may be null) additional information for use in diagnostics
     */

    public Atomizer(Expression sequence, Supplier<RoleDiagnostic> role) {
        super(sequence);
        this.roleSupplier = role;
        sequence.setFlattened(true);
    }

    /**
     * Make an atomizer with a given operand
     *
     * @param sequence the operand
     * @param roleSupplier (may be null) additional information for diagnostics
     * @return an Atomizer that atomizes the given operand, or another expression that returns the same result
     */

    public static Expression makeAtomizer(Expression sequence, Supplier<RoleDiagnostic> roleSupplier) {
        if (sequence instanceof Literal && ((Literal) sequence).getGroundedValue() instanceof AtomicSequence) {
            return sequence;
        } else {
            return new Atomizer(sequence, roleSupplier);
        }
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.ATOMIC_SEQUENCE;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     *         {@link #PROCESS_METHOD}
     */

    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD | WATCH_METHOD;
    }

    public ItemType getOperandItemType() {
        if (operandItemType == null) {
            operandItemType = getBaseExpression().getItemType();
        }
        return operandItemType;
    }

    /**
     * Simplify an expression
     *
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        //untyped = !getContainer().getPackageData().isSchemaAware();
        untyped = !getPackageData().isSchemaAware();
        computeSingleValued(getConfiguration().getTypeHierarchy());
        Expression operand = getBaseExpression().simplify();
        if (operand instanceof Literal) {
            GroundedValue val = ((Literal) operand).getGroundedValue();

            if (val instanceof AtomicValue) {
                return operand;
            }
            SequenceIterator iter = val.iterate();
            Item i;
            while ((i = iter.next()) != null) {
                if (i instanceof NodeInfo) {
                    return this;
                }
                if (i instanceof FunctionItem) {
                    if (((FunctionItem)i).isArray()) {
                        return this;
                    } else if (((FunctionItem)i).isMap()) {
                        throw new XPathException(expandMessage("Cannot atomize a map (" + i.toShortString() + ")"))
                                .withErrorCode("FOTY0013")
                                .asTypeError()
                                .withLocation(getLocation());
                    } else {
                        throw new XPathException(expandMessage("Cannot atomize a function item"))
                                .withErrorCode("FOTY0013")
                                .asTypeError()
                                .withLocation(getLocation());
                    }
                }
            }
            // if all items in the sequence are atomic (they generally will be, since this is
            // done at compile time), then return the sequence
            return operand;
        } else if (operand instanceof ValueOf &&
                !ReceiverOption.contains(((ValueOf) operand).getOptions(), ReceiverOption.DISABLE_ESCAPING)) {
            // XSLT users tend to use ValueOf unnecessarily
            return ((ValueOf) operand).convertToCastAsString();
        }
        setBaseExpression(operand);
        return this;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().typeCheck(visitor, contextInfo);
        untyped = untyped | !visitor.getStaticContext().getPackageData().isSchemaAware();

        // If the configuration allows typed data, check whether the content type of these particular nodes is untyped
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        computeSingleValued(th);
        resetLocalStaticProperties();
        ItemType operandType = getOperandItemType();
        if (th.isSubType(operandType, BuiltInAtomicType.ANY_ATOMIC)) {
            return getBaseExpression();
        }
        if (!operandType.isAtomizable(th)) {
            XPathException err;
            if (operandType instanceof FunctionItemType) {
                String thing = operandType instanceof MapType ? "map" : "function item";
                err = new XPathException(expandMessage("Cannot atomize a " + thing))
                        .withErrorCode("FOTY0013");
            } else {
                err = new XPathException(
                        expandMessage("Cannot atomize an element that is defined in the schema to have element-only content"))
                        .withErrorCode("FOTY0012");
            }
            throw err.asTypeError().withLocation(getLocation());
        }
        getBaseExpression().setFlattened(true);
        return this;
    }

    private void computeSingleValued(TypeHierarchy th) {
        ItemType operandType = getOperandItemType();
        if (th.relationship(operandType, ArrayItemType.ANY_ARRAY_TYPE) != Affinity.DISJOINT) {
            singleValued = false;
        } else {
            singleValued = untyped;
            if (!singleValued) {
                ItemType nodeType = getBaseExpression().getItemType();
                if (nodeType instanceof NodeTest) {
                    if (!nodeType.getUType().overlaps(UType.ELEMENT.union(UType.ATTRIBUTE))) {
                        singleValued = true;
                    } else {
                        SchemaType st = ((NodeTest) nodeType).getContentType();
                        if (isSingleValuedSchemaType(st)) {  // Bug 5803
                            singleValued = true;
                        }
                    }

                }
            }
        }
    }

    private boolean isSingleValuedSchemaType(SchemaType st) {
        if (st == Untyped.getInstance()) {
            return true;
        }
        if (st.isSimpleType()) {
            SimpleType sim = (SimpleType)st;
            if (sim.isAtomicType()) {
                return true;
            } else if (sim.isListType()) {
                return false;
            } else if (sim.isUnionType()) {
                return ((UnionType)sim).isPlainType();
            } else {
                return false; // can't happen? - fail safe
            }
        }
        if (st.isComplexType()) {
            if (st == AnyType.getInstance()) {
                return false;
            }
            if (((ComplexType)st).isSimpleContent()) {
                return isSingleValuedSchemaType(((ComplexType)st).getSimpleContentType());
            }
        }
        return false; // play safe
    }

    /**
     * Expand an error message with information about the context in which atomization is taking place
     * @param message the message to be expanded
     */

    private String expandMessage(String message) {
        if (roleSupplier == null) {
            return message;
        } else {
            return message + ". Found while atomizing the " + roleSupplier.get().getMessage() +
                    " in {" + toShortString() + "} on line " + getLocation().getLineNumber();
        }
    }


    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during this phase
     *          (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression exp = super.optimize(visitor, contextInfo);
        if (exp == this) {
            final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
            Expression operand = getBaseExpression();
            if (th.isSubType(operand.getItemType(), BuiltInAtomicType.ANY_ATOMIC)) {
                return operand;
            }
            if (operand instanceof ValueOf &&
                    !ReceiverOption.contains(((ValueOf) operand).getOptions(), ReceiverOption.DISABLE_ESCAPING)) {
                // XSLT users tend to use ValueOf unnecessarily
                Expression cast = ((ValueOf) operand).convertToCastAsString();
                return cast.optimize(visitor, contextInfo);
            }
            if (operand instanceof LetExpression || operand instanceof ForExpression) {
                // replace data(let $x := y return z) by (let $x := y return data(z))
                Expression action = ((Assignation) operand).getAction();
                ((Assignation) operand).setAction(new Atomizer(action, roleSupplier));
                return operand.optimize(visitor, contextInfo);
            }
            if (operand instanceof Choose) {
                // replace data(if x then y else z) by (if x then data(y) else data(z)
                ((Choose)operand).atomizeActions();
                return operand.optimize(visitor, contextInfo);
            }
            if (operand instanceof Block) {
                // replace data((x,y,z)) by (data(x), data(y), data(z)) as some of the atomizers
                // may prove to be redundant. (Also, it helps streaming)
                Operand[] children = ((Block) operand).getOperanda();
                Expression[] atomizedChildren = new Expression[children.length];
                for (int i = 0; i < children.length; i++) {
                    atomizedChildren[i] = new Atomizer(children[i].getChildExpression(), roleSupplier);
                }
                Block newBlock = new Block(atomizedChildren);
                return newBlock.typeCheck(visitor, contextInfo).optimize(visitor, contextInfo);
            }
            if (untyped && operand instanceof AxisExpression &&
                    ((AxisExpression)operand).getAxis() == AxisInfo.ATTRIBUTE &&
                    ((AxisExpression) operand).getNodeTest() instanceof NameTest &&
                    !((AxisExpression) operand).isContextPossiblyUndefined()) {
                StructuredQName name = ((AxisExpression) operand).getNodeTest().getMatchingNodeName();
                FingerprintedQName qName = new FingerprintedQName(name, visitor.getConfiguration().getNamePool());
                AttributeGetter ag = new AttributeGetter(qName);
                ExpressionTool.copyLocationInfo(this, ag);
                return ag;
            }
            if (untyped && operand instanceof SimpleStepExpression &&
                    ((SimpleStepExpression) operand).getAxisExpression().getAxis() == AxisInfo.ATTRIBUTE &&
                    ((SimpleStepExpression) operand).getAxisExpression().getNodeTest() instanceof NameTest) {
                StructuredQName name = ((SimpleStepExpression) operand).getAxisExpression().getNodeTest().getMatchingNodeName();
                FingerprintedQName qName = new FingerprintedQName(name, visitor.getConfiguration().getNamePool());
                AttributeGetter ag = new AttributeGetter(qName);
                ExpressionTool.copyLocationInfo(this, ag);
                return new SlashExpression(((SimpleStepExpression) operand).getStart(), ag);
            }
        }
        return exp;
    }

    /**
     * Ask whether it is known that any nodes in the input will always be untyped
     *
     * @return true if it is known that all nodes in the input will be untyped
     */
    public boolean isUntyped() {
        return untyped;
    }


    /**
     * Determine the special properties of this expression
     *
     * @return {@link StaticProperty#NO_NODES_NEWLY_CREATED}.
     */

    @Override
    protected int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        p &= ~StaticProperty.NODESET_PROPERTIES;
//        if (!untyped) {
//            p |= StaticProperty.NOT_UNTYPED_ATOMIC;
//        }
        return p | StaticProperty.NO_NODES_NEWLY_CREATED;
    }

    /**
     * Reset the static properties of the expression to -1, so that they have to be recomputed
     * next time they are used.
     */
    @Override
    public void resetLocalStaticProperties() {
        super.resetLocalStaticProperties();
        operandItemType = null;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be rebound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        Atomizer copy = new Atomizer(getBaseExpression().copy(rebindings), roleSupplier);
        copy.untyped = untyped;
        copy.singleValued = singleValued;
        ExpressionTool.copyLocationInfo(this, copy);
        return copy;
    }

    @Override
    public String getStreamerName() {
        return "Atomizer";
    }

    /**
     * Iterate over the sequence of values
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        try {
            SequenceIterator base = getBaseExpression().iterate(context);
            return getAtomizingIterator(base, untyped && operandItemType instanceof NodeTest);
        } catch (TerminationException | Error.UserDefinedXPathException e) {
            throw e;
        } catch (XPathException e) {
            if (roleSupplier == null) {
                throw e;
            } else {
                String message = expandMessage(e.getMessage());
                throw new XPathException(message)
                        .withErrorCode(e.getErrorCodeQName())
                        .withLocation(e.getLocator())
                        .withXPathContext(context)
                        .maybeWithLocation(getLocation());
            }
        }
    }

    /**
     * Evaluate as an Item. This should only be called if the Atomizer has cardinality zero-or-one,
     * which will only be the case if the underlying expression has cardinality zero-or-one.
     */

    @Override
    public AtomicValue evaluateItem(XPathContext context) throws XPathException {
        return (AtomicValue)makeElaborator().elaborateForItem().eval(context);
    }

    /**
     * Determine the data type of the items returned by the expression, if possible
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER. For this class, the
     *         result is always an atomic type, but it might be more specific.
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        operandItemType = getBaseExpression().getItemType();
        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        return getAtomizedItemType(getBaseExpression(), untyped, th);
    }

    @Override
    public UType getStaticUType(UType contextItemType) {
        return UType.ANY_ATOMIC.intersection(getItemType().getUType());
    }

    /**
     * Compute the type that will result from atomizing the result of a given expression
     *
     * @param operand       the given expression
     * @param alwaysUntyped true if it is known that nodes will always be untyped
     * @param th            the type hierarchy cache
     * @return the item type of the result of evaluating the operand expression, after atomization, or
     *         xs:error if it is known that atomization will return an error
     */

    public static ItemType getAtomizedItemType(Expression operand, boolean alwaysUntyped, TypeHierarchy th) {
        ItemType in = operand.getItemType();
        if (in.isPlainType()) {
            return in;
        } else if (in instanceof NodeTest) {
            UType kinds = in.getUType();
            if (alwaysUntyped) {
                // Some node-kinds always have a typed value that's a string

                if (STRING_KINDS.subsumes(kinds)) {
                    return BuiltInAtomicType.STRING;
                }
                // Some node-kinds are always untyped atomic; some are untypedAtomic provided that the configuration
                // is untyped

                if (UNTYPED_IF_UNTYPED_KINDS.subsumes(kinds)) {
                    return BuiltInAtomicType.UNTYPED_ATOMIC;
                }
            } else {
                if (UNTYPED_KINDS.subsumes(kinds)) {
                    return BuiltInAtomicType.UNTYPED_ATOMIC;
                }
            }

            return in.getAtomizedItemType();
        } else if (in instanceof JavaExternalObjectType) {
            return in.getAtomizedItemType();
        } else if (in instanceof ArrayItemType) {
            PlainType ait = ((ArrayItemType)in).getMemberType().getPrimaryType().getAtomizedItemType();
            return ait == null ? ErrorType.getInstance() : ait;
        } else if (in instanceof FunctionItemType) {
            return ErrorType.getInstance();
        }
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Node kinds whose typed value is always a string
     */
    public static final UType STRING_KINDS =
            UType.NAMESPACE.union(UType.COMMENT).union(UType.PI);

    /**
     * Node kinds whose typed value is always untypedAtomic
     */

    public static final UType UNTYPED_KINDS =
            UType.TEXT.union(UType.DOCUMENT);

    /**
     * Node kinds whose typed value is untypedAtomic if the configuration is untyped
     */

    public static final UType UNTYPED_IF_UNTYPED_KINDS =
            UType.TEXT.union(UType.ELEMENT).union(UType.DOCUMENT).union(UType.ATTRIBUTE);

    /**
     * Determine the static cardinality of the expression
     */

    @Override
    protected int computeCardinality() {
        ItemType in = getOperandItemType();
        Expression operand = getBaseExpression();
        if (singleValued) {
            return operand.getCardinality();
        } else if (untyped && in instanceof NodeTest) {
            return operand.getCardinality();
        } else if (Cardinality.allowsMany(operand.getCardinality())) {
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        } else if (in.isPlainType()) {
            return operand.getCardinality();
        } else if (in instanceof NodeTest) {
            SchemaType schemaType = ((NodeTest) in).getContentType();
            if (schemaType.isAtomicType()) {
                // can return at most one atomic value per node
                return operand.getCardinality();
            }
        }
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }


    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     * <p>The default implementation of this method assumes that an expression does no navigation other than
     * the navigation done by evaluating its subexpressions, and that the subexpressions are evaluated in the
     * same context as the containing expression. The method must be overridden for any expression
     * where these assumptions do not hold. For example, implementations exist for AxisExpression, ParentExpression,
     * and RootExpression (because they perform navigation), and for the doc(), document(), and collection()
     * functions because they create a new navigation root. Implementations also exist for PathExpression and
     * FilterExpression because they have subexpressions that are evaluated in a different context from the
     * calling expression.</p>
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNodeSet representing the points in the source document that are both reachable by this
     *         expression, and that represent possible results of this expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet result = getBaseExpression().addToPathMap(pathMap, pathMapNodeSet);
        if (result != null) {
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            ItemType operandItemType = getBaseExpression().getItemType();
            if (th.relationship(NodeKindTest.ELEMENT, operandItemType) != Affinity.DISJOINT ||
                    th.relationship(NodeKindTest.DOCUMENT, operandItemType) != Affinity.DISJOINT) {
                result.setAtomized();
            }
        }
        return null;
    }

    /**
     * Get an iterator that returns the result of atomizing the sequence delivered by the supplied
     * iterator
     *
     * @param base    the supplied iterator, the input to atomization
     * @param oneToOne this can safely be set to true if it is known that all nodes in the base sequence will
     *                be untyped and that there will be no arrays in the base sequence; but it is always OK
     *                to set it to false.
     * @return an iterator that returns atomic values, the result of the atomization
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic evaluation error occurs
     */

    public static SequenceIterator getAtomizingIterator(SequenceIterator base, boolean oneToOne) throws XPathException {
        if (SequenceTool.supportsGetLength(base)) {
            int count = SequenceTool.getLength(base);
            if (count == 0) {
                return EmptyIterator.getInstance();
            } else if (count == 1) {
                Item first = base.next();
                Objects.requireNonNull(first);
                return first.atomize().iterate();
            }
        } else if (base instanceof AtomizedValueIterator) {
            return new AxisAtomizingIterator((AtomizedValueIterator)base);
        }
        if (oneToOne) {
            return new UntypedAtomizingIterator(base);
        } else {
            return new AtomizingIterator(base);
        }
    }

    public static AtomicSequence atomize(Sequence sequence) throws XPathException {
        if (sequence instanceof AtomicSequence) {
            return (AtomicSequence)sequence;
        } else if (sequence instanceof EmptySequence) {
            return EmptyAtomicSequence.getInstance();
        } else {
            SequenceIterator iter = getAtomizingIterator(sequence.iterate(), false);
            return new AtomicArray(iter);
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public String getExpressionName() {
        return "data";
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */
    @Override
    public String toString() {
        return "data(" + getBaseExpression().toString() + ")";
    }

    @Override
    public String toShortString() {
        return getBaseExpression().toShortString();
    }

    @Override
    protected void emitExtraAttributes(ExpressionPresenter out) {
        if (roleSupplier != null) {
            out.emitAttribute("diag", roleSupplier.get().save());
        }
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new AtomizerElaborator();
    }

    /**
     * Elaborator for an Atomizer
     */

    public static class AtomizerElaborator extends PullElaborator {

        public PullEvaluator elaborateForPull() {
            final Atomizer expr = (Atomizer) getExpression();
            final PullEvaluator baseEval = expr.getBaseExpression().makeElaborator().elaborateForPull();
            final boolean oneToOne = expr.isUntyped() && expr.getBaseExpression().getItemType() instanceof NodeTest;
            return context -> {
                try {
                    SequenceIterator base = baseEval.iterate(context);
                    return getAtomizingIterator(base, oneToOne);
                } catch (TerminationException | Error.UserDefinedXPathException e) {
                    throw e;
                } catch (XPathException e) {
                    if (expr.roleSupplier == null) {
                        throw e;
                    } else {
                        String message = expr.expandMessage(e.getMessage());
                        throw new XPathException(message)
                                .withErrorCode(e.getErrorCodeQName())
                                .withLocation(e.getLocator())
                                .withXPathContext(context)
                                .maybeWithLocation(expr.getLocation());
                    }
                } catch (UncheckedXPathException uxe) {
                    XPathException e = uxe.getXPathException();
                    if (expr.roleSupplier == null) {
                        throw e;
                    } else {
                        String message = expr.expandMessage(e.getMessage());
                        throw new XPathException(message)
                                .withErrorCode(e.getErrorCodeQName())
                                .withLocation(e.getLocator())
                                .withXPathContext(context)
                                .maybeWithLocation(expr.getLocation());
                    }
                }
            };
        }

        @Override
        public ItemEvaluator elaborateForItem() {
            final Atomizer expr = (Atomizer) getExpression();
            final ItemEvaluator baseEval = expr.getBaseExpression().makeElaborator().elaborateForItem();
            final boolean nullable = Cardinality.allowsZero(expr.getBaseExpression().getCardinality());
            if (nullable) {
                return context -> {
                    Item it = baseEval.eval(context);
                    if (it == null) {
                        return null;
                    }
                    return it.atomize().head();
                };
            } else {
                return context -> baseEval.eval(context).atomize().head();
            }
        }
    }
}

