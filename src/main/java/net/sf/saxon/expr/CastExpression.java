////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.String_1;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.util.function.Supplier;


/**
 * Cast Expression: implements "cast as data-type ( expression )". It also allows an internal
 * cast, which has the same semantics as a user-requested cast, but maps an empty sequence to
 * an empty sequence.
 * <p>This expression class does not handle casting to a list or union type.</p>
 */

public class CastExpression extends CastingExpression implements Callable {

    /**
     * Create a cast expression
     *
     * @param source     expression giving the value to be converted
     * @param target     the type to which the value is to be converted
     * @param allowEmpty true if the expression allows an empty sequence as input, producing
     *                   an empty sequence as output. If false, an empty sequence is a type error.
     */

    public CastExpression(Expression source, AtomicType target, boolean allowEmpty) {
        super(source, target, allowEmpty);
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().typeCheck(visitor, contextInfo);
        SequenceType atomicType = SequenceType.makeSequenceType(BuiltInAtomicType.ANY_ATOMIC, getCardinality());

        Configuration config = visitor.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();
        Supplier<RoleDiagnostic> role = () -> new RoleDiagnostic(RoleDiagnostic.TYPE_OP, "cast as", 0);
        ItemType sourceItemType;

        TypeChecker tc = config.getTypeChecker(false);
        Expression operand = tc.staticTypeCheck(getBaseExpression(), atomicType, role, visitor);
        setBaseExpression(operand);
        sourceItemType = operand.getItemType();


        if (sourceItemType instanceof ErrorType) {
            if (allowsEmpty()) {
                return Literal.makeEmptySequence();
            } else {
                throw new XPathException("Cast does not allow an empty sequence as input")
                        .withErrorCode("XPTY0004")
                        .withLocation(getLocation())
                        .asTypeError();
            }
        }

        PlainType sourceType = (PlainType) sourceItemType;
        Affinity r = th.relationship(sourceType, getTargetType());
        if (r == Affinity.SAME_TYPE) {
            return operand;
        } else if (r == Affinity.SUBSUMED_BY) {
            // It's generally true that any expression defined to return an X is allowed to return a subtype of X.
            // However, people seem to get upset if we treat the cast as a no-op.
            converter = new Converter.UpCastingConverter(getTargetType());
        } else {

            ConversionRules rules = visitor.getConfiguration().getConversionRules();

            if (sourceType.isAtomicType() && sourceType != BuiltInAtomicType.ANY_ATOMIC) {
                //System.err.println("Allocating converter from " + sourceType + " to " + getTargetType());
                converter = rules.getConverter((AtomicType)sourceType, getTargetType());
                if (converter == null) {
                    throw new XPathException("Casting from " + sourceType + " to " + getTargetType() +
                            " can never succeed")
                            .withErrorCode("XPTY0004")
                            .withLocation(getLocation())
                            .asTypeError();
                } else {
                    if (getTargetType().isNamespaceSensitive()) {
                        converter = converter.setNamespaceResolver(getRetainedStaticContext());
                    }
                }

            }
        }

        if (operand instanceof Literal) {
            return preEvaluate();
        }

        return this;
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
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        Expression e2 = super.optimize(visitor, contextInfo);
        if (e2 != this) {
            return e2;
        }
        // Eliminate pointless casting between untypedAtomic and string
        Expression operand = getBaseExpression();
        if (getTargetType() == BuiltInAtomicType.UNTYPED_ATOMIC) {
            if (operand.isCallOn(String_1.class)) {
                Expression e = ((SystemFunctionCall) operand).getArg(0);
                if (e.getItemType() instanceof AtomicType && e.getCardinality() == StaticProperty.EXACTLY_ONE) {
                    return new CastExpression(e, BuiltInAtomicType.UNTYPED_ATOMIC, allowsEmpty());
                }
            } else if (operand instanceof CastExpression) {
                if (((CastExpression) operand).getTargetType() == BuiltInAtomicType.UNTYPED_ATOMIC) {
                    return operand;
                } else if (((CastExpression) operand).getTargetType() == BuiltInAtomicType.STRING) {
                    ((CastExpression) operand).setTargetType(BuiltInAtomicType.UNTYPED_ATOMIC);
                    return operand;
                }
            } else if (operand instanceof AtomicSequenceConverter) {
                if (operand.getItemType() == BuiltInAtomicType.UNTYPED_ATOMIC) {
                    return operand;
                } else if (operand.getItemType() == BuiltInAtomicType.STRING) {
                    AtomicSequenceConverter old = (AtomicSequenceConverter) operand;
                    AtomicSequenceConverter asc = new AtomicSequenceConverter(
                            old.getBaseExpression(),
                            BuiltInAtomicType.UNTYPED_ATOMIC
                    );
                    return asc.typeCheck(visitor, contextInfo)
                            .optimize(visitor, contextInfo);
                }
            }
        }
        // avoid converting anything to a string and then back again
        if (operand.isCallOn(String_1.class)) {
            Expression e = ((SystemFunctionCall) operand).getArg(0);
            ItemType et = e.getItemType();
            if (et instanceof AtomicType &&
                    e.getCardinality() == StaticProperty.EXACTLY_ONE &&
                    th.isSubType(et, getTargetType())) {
                return e;
            }
        }
        // avoid converting anything to untypedAtomic and then back again
        if (operand instanceof CastExpression) {
            ItemType it = ((CastExpression) operand).getTargetType();
            if (th.isSubType(it, BuiltInAtomicType.STRING) || th.isSubType(it, BuiltInAtomicType.UNTYPED_ATOMIC)) {
                Expression e = ((CastExpression) operand).getBaseExpression();
                ItemType et = e.getItemType();
                if (et instanceof AtomicType &&
                        e.getCardinality() == StaticProperty.EXACTLY_ONE &&
                        th.isSubType(et, getTargetType())) {
                    return e;
                }
            }
        }
        if (operand instanceof AtomicSequenceConverter) {
            ItemType it = operand.getItemType();
            if (th.isSubType(it, BuiltInAtomicType.STRING) || th.isSubType(it, BuiltInAtomicType.UNTYPED_ATOMIC)) {
                Expression e = ((AtomicSequenceConverter) operand).getBaseExpression();
                ItemType et = e.getItemType();
                if (et instanceof AtomicType &&
                        e.getCardinality() == StaticProperty.EXACTLY_ONE &&
                        th.isSubType(et, getTargetType())) {
                    return e;
                }
            }
        }
        // if the operand can't be empty, then set allowEmpty to false to provide more information for analysis
        if (!Cardinality.allowsZero(operand.getCardinality())) {
            setAllowEmpty(false);
            resetLocalStaticProperties();
        }

        if (operand instanceof Literal) {
            return preEvaluate();
        }
        return this;
    }

    /**
     * Perform early (compile-time) evaluation, if possible
     * @return the result of pre-evaluation, or the original expression unchanged
     * @throws XPathException if an error is found
     */

    protected Expression preEvaluate() throws XPathException {
        GroundedValue literalOperand = ((Literal) getBaseExpression()).getGroundedValue();
        if (literalOperand instanceof AtomicValue && converter != null) {
            ConversionResult result = converter.convert((AtomicValue) literalOperand);
            if (result instanceof ValidationFailure) {
                ValidationFailure err = (ValidationFailure) result;
                String code = err.getErrorCode();
                if (code == null) {
                    code = "FORG0001";
                }
                throw new XPathException(err.getMessage(), code, this.getLocation());
            } else {
                return Literal.makeLiteral((AtomicValue) result, this);
            }
        }
        if (literalOperand.getLength() == 0) {
            if (allowsEmpty()) {
                return getBaseExpression();
            } else {
                XPathException err = new XPathException("Cast can never succeed: the operand must not be an empty sequence", "XPTY0004", this.getLocation());
                err.setIsTypeError(true);
                throw err;
            }
        }
        return this;
    }

    /**
     * Get the static cardinality of the expression
     */

    @Override
    protected int computeCardinality() {
        return allowsEmpty() && Cardinality.allowsZero(getBaseExpression().getCardinality())
                ? StaticProperty.ALLOWS_ZERO_OR_ONE : StaticProperty.EXACTLY_ONE;
    }

    /**
     * Get the static type of the expression
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getTargetType();
    }


    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType static information about the context item
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return getTargetType().getUType();
    }

    /**
     * Determine the special properties of this expression
     * @return the expression properties
     */
    @Override
    protected int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        if (getTargetType() == BuiltInAtomicType.UNTYPED_ATOMIC) {
            p = p &~ StaticProperty.NOT_UNTYPED_ATOMIC;
        }
        return p;
    }

    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     *         unknown or not applicable.
     */
    /*@Nullable*/
    @Override
    public IntegerValue[] getIntegerBounds() {
        if (converter == Converter.BooleanToInteger.INSTANCE) {
            return new IntegerValue[]{Int64Value.ZERO, Int64Value.PLUS_ONE};
        } else {
            return null;
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings  variables that need to be rebound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        CastExpression c2 = new CastExpression(getBaseExpression().copy(rebindings), getTargetType(), allowsEmpty());
        ExpressionTool.copyLocationInfo(this, c2);
        c2.converter = converter;
        c2.setRetainedStaticContext(getRetainedStaticContext());
        c2.setOperandIsStringLiteral(isOperandIsStringLiteral());
        return c2;
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
        return EVALUATE_METHOD;
    }

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        AtomicValue result = doCast((AtomicValue)arguments[0].head(), context);
        return SequenceTool.itemOrEmpty(result);
    }

    public AtomicValue doCast(AtomicValue value, XPathContext context) throws XPathException {
        if (value == null) {
            if (allowsEmpty()) {
                return null;
            } else {
                throw new XPathException("Cast does not allow an empty sequence")
                        .withXPathContext(context)
                        .withLocation(getLocation())
                        .withErrorCode("XPTY0004");
            }
        }

        Converter converter = this.converter;
        if (converter == null) {
            ConversionRules rules = context.getConfiguration().getConversionRules();
            converter = rules.getConverter(value.getPrimitiveType(), getTargetType());
            if (converter == null) {
                throw new XPathException("Casting from " + value.getPrimitiveType() +
                                                 " to " + getTargetType() + " is not permitted")
                        .withXPathContext(context)
                        .withLocation(getLocation())
                        .withErrorCode("XPTY0004");
            }
            if (getTargetType().isNamespaceSensitive()) {
                converter = converter.setNamespaceResolver(getRetainedStaticContext());
            }
        }
        ConversionResult result = converter.convert(value);
        if (result instanceof ValidationFailure) {
            ValidationFailure err = (ValidationFailure) result;
            throw err.makeException()
                    .maybeWithErrorCode("FORG0001")
                    .maybeWithLocation(getLocation());
        }
        return (AtomicValue) result;
    }

    /**
     * Evaluate the expression
     */

    /*@Nullable*/
    @Override
    public AtomicValue evaluateItem(XPathContext context) throws XPathException {
        return (AtomicValue) makeElaborator().elaborateForItem().eval(context);
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return other instanceof CastExpression &&
                getBaseExpression().isEqual(((CastExpression) other).getBaseExpression()) &&
                getTargetType() == ((CastExpression) other).getTargetType() &&
                allowsEmpty() == ((CastExpression) other).allowsEmpty();
    }

    /**
     * get HashCode for comparing two expressions. Note that this hashcode gives the same
     * result for (A op B) and for (B op A), whether or not the operator is commutative.
     */

    @Override
    protected int computeHashCode() {
        return super.computeHashCode() ^ getTargetType().hashCode();
    }

    /**
     * Represent the expression as a string. The resulting string will be a valid XPath 3.0 expression
     * with no dependencies on namespace bindings.
     *
     * @return the expression as a string in XPath 3.0 syntax
     */

    public String toString() {
        return getTargetType().getEQName() + "(" + getBaseExpression().toString() + ")";
    }

    @Override
    public String toShortString() {
        return getTargetType().getDisplayName() + "(" + getBaseExpression().toShortString() + ")";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        export(out, "cast");
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in export() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "cast";
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new CastExprElaborator();
    }

    /**
     * Elaborator for {@code cast as} expression, or the equivalent constructor function call
     */

    public static class CastExprElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            CastExpression exp = (CastExpression) getExpression();
            Expression arg = exp.getBaseExpression();
            ItemEvaluator argEval = arg.makeElaborator().elaborateForItem();

            return context -> {
                AtomicValue value = (AtomicValue) argEval.eval(context);
                return exp.doCast(value, context);
            };
        }

    }
}

