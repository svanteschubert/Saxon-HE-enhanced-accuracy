////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.elab.BooleanEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.ComparisonException;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.function.Supplier;

/**
 * ValueComparison: a boolean expression that compares two atomic values
 * for equals, not-equals, greater-than or less-than. Implements the operators
 * eq, ne, lt, le, gt, ge
 */

public final class ValueComparison extends BinaryExpression implements ComparisonExpression, Negatable {

    /*@Nullable*/ private BooleanValue resultWhenEmpty = null;
    private boolean needsRuntimeCheck;

    /**
     * Create a comparison expression identifying the two operands and the operator
     *
     * @param p1 the left-hand operand
     * @param op the operator, as a token returned by the Tokenizer (e.g. Token.LT)
     * @param p2 the right-hand operand
     */

    public ValueComparison(Expression p1, OperatorSymbol op, Expression p2) {
        super(p1, op, p2);
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */

    @Override
    public String getExpressionName() {
        return "ValueComparison";
    }

    /**
     * Get the AtomicComparer used to compare atomic values. This encapsulates any collation that is used.
     * Note that the comparer is always known at compile time.
     */

    @Override
    public AtomicComparer getAtomicComparer() {
        // TODO: this is scaffolding. ValueComparison no longer uses an AtomicComparer, but this method
        // is retained for paths that require one, e.g. EqualityPatternOptimizer
        ItemType t0 = getLhsExpression().getItemType().getPrimitiveItemType();
        if (!(t0 instanceof BuiltInAtomicType)) {
            // This can happen after loading from a SEF file; the static type information is not always available
            t0 = BuiltInAtomicType.ANY_ATOMIC;
        }
        ItemType t1 = getRhsExpression().getItemType().getPrimitiveItemType();
        if (!(t1 instanceof BuiltInAtomicType)) {
            // This can happen after loading from a SEF file; the static type information is not always available
            t1 = BuiltInAtomicType.ANY_ATOMIC;
        }
        int version = getRetainedStaticContext().getPackageData().getHostLanguageVersion();
        return GenericAtomicComparer.makeAtomicComparer(
                (BuiltInAtomicType)t0,
                (BuiltInAtomicType)t1,
                getStringCollator(),
                version,
                getConfiguration().getConversionContext());

    }

    /**
     * Get the StringCollator used to compare string values.
     *
     * @return the collator. May return null if the expression will never be used to compare strings
     */
    @Override
    public StringCollator getStringCollator() {
        try {
            return getConfiguration().getCollation((getRetainedStaticContext().getDefaultCollationName()));
        } catch (XPathException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Get the primitive (singleton) operator used: one of Token.FEQ, Token.FNE, Token.FLT, Token.FGT,
     * Token.FLE, Token.FGE
     */

    @Override
    public OperatorSymbol getSingletonOperator() {
        return operator;
    }

    /**
     * Determine whether untyped atomic values should be converted to the type of the other operand
     *
     * @return true if untyped values should be converted to the type of the other operand, false if they
     *         should be converted to strings.
     */

    @Override
    public boolean convertsUntypedToOther() {
        return false;
    }

    /**
     * Set the result to be returned if one of the operands is an empty sequence
     *
     * @param value the result to be returned if an operand is empty. Supply null to mean the empty sequence.
     */

    public void setResultWhenEmpty(BooleanValue value) {
        resultWhenEmpty = value;
    }

    /**
     * Get the result to be returned if one of the operands is an empty sequence
     *
     * @return BooleanValue.TRUE, BooleanValue.FALSE, or null (meaning the empty sequence)
     */

    public BooleanValue getResultWhenEmpty() {
        return resultWhenEmpty;
    }

    /**
     * Determine whether a run-time check is needed to check that the types of the arguments
     * are comparable
     *
     * @return true if a run-time check is needed
     */

    public boolean needsRuntimeComparabilityCheck() {
        return needsRuntimeCheck;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        resetLocalStaticProperties();
        getLhs().typeCheck(visitor, contextInfo);
        getRhs().typeCheck(visitor, contextInfo);

        Configuration config = visitor.getConfiguration();
        boolean allow40 = visitor.getStaticContext().getPackageData().getHostLanguageVersion() >= 40;

        if (Literal.isEmptySequence(getLhsExpression())) {
            return resultWhenEmpty == null ? getLhsExpression() : Literal.makeLiteral(resultWhenEmpty, this);
        }

        if (Literal.isEmptySequence(getRhsExpression())) {
            return resultWhenEmpty == null ? getRhsExpression() : Literal.makeLiteral(resultWhenEmpty, this);
        }

        if (convertsUntypedToOther()) {
            return this; // we've already done all that needs to be done
        }

        final SequenceType optionalAtomic = SequenceType.OPTIONAL_ATOMIC;
        TypeChecker tc = config.getTypeChecker(false);

        Supplier<RoleDiagnostic> role0 = () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, operator.toString(), 0);
        setLhsExpression(tc.staticTypeCheck(getLhsExpression(), optionalAtomic, role0, visitor));

        Supplier<RoleDiagnostic> role1 = () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, operator.toString(), 1);
        setRhsExpression(tc.staticTypeCheck(getRhsExpression(), optionalAtomic, role1, visitor));

        PlainType t0 = getLhsExpression().getItemType().getAtomizedItemType();
        PlainType t1 = getRhsExpression().getItemType().getAtomizedItemType();

        if (t0.getUType().union(t1.getUType()).overlaps(UType.EXTENSION)) {
            throw new XPathException("Cannot perform comparisons involving external objects")
                    .asTypeError().withErrorCode("XPTY0004").withLocation(getLocation());
        }

        BuiltInAtomicType p0 = (BuiltInAtomicType) t0.getPrimitiveItemType();
        if (p0.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            p0 = BuiltInAtomicType.STRING;
        }
        BuiltInAtomicType p1 = (BuiltInAtomicType) t1.getPrimitiveItemType();
        if (p1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            p1 = BuiltInAtomicType.STRING;
        }

        // If both types are strings, then supplying an untypedAtomic would serve just as well, so we can
        // eliminate any forced conversion from untypedAtomic to string

        if (p0 == BuiltInAtomicType.STRING && p1 == BuiltInAtomicType.STRING) {
            if (getLhsExpression() instanceof CastExpression lhsCast
                    && lhsCast.getBaseExpression().getItemType() == BuiltInAtomicType.UNTYPED_ATOMIC) {
                setLhsExpression(lhsCast.getBaseExpression());
            }
            if (getRhsExpression() instanceof CastExpression rhsCast
                    && rhsCast.getBaseExpression().getItemType() == BuiltInAtomicType.UNTYPED_ATOMIC) {
                setRhsExpression(rhsCast.getBaseExpression());
            }
        }

        needsRuntimeCheck =
                p0.equals(BuiltInAtomicType.ANY_ATOMIC) || p1.equals(BuiltInAtomicType.ANY_ATOMIC);

        if (!needsRuntimeCheck && !Type.isPossiblyComparable(p0, p1, OperatorInfo.isOrderedOperator(operator), allow40)) {
            boolean opt0 = Cardinality.allowsZero(getLhsExpression().getCardinality());
            boolean opt1 = Cardinality.allowsZero(getRhsExpression().getCardinality());
            if (opt0 || opt1) {
                // This is a comparison such as (xs:integer? eq xs:date?). This is almost
                // certainly an error, but we need to let it through because it will work if
                // one of the operands is an empty sequence.

                String which = null;
                if (opt0) {
                    which = "the first operand is";
                }
                if (opt1) {
                    which = "the second operand is";
                }
                if (opt0 && opt1) {
                    which = "one or both operands are";
                }

                visitor.getStaticContext().issueWarning("Comparison of " + t0 +
                        (opt0 ? "?" : "") + " to " + t1 +
                        (opt1 ? "?" : "") + " will fail unless " + which + " empty", SaxonErrorCode.SXWN9026, getLocation());
                needsRuntimeCheck = true;
            } else {
                String message = "In {" + toShortString() + "}: cannot compare " +
                        t0 + " to " + t1;
                throw new XPathException(message)
                        .asTypeError().withErrorCode("XPTY0004").withLocation(getLocation());
            }
        }
        if (!allow40 && !(operator == OperatorSymbol.FEQ || operator == OperatorSymbol.FNE)) {
            mustBeOrdered(t0, p0);
            mustBeOrdered(t1, p1);
        }
        return this;
    }

    private void mustBeOrdered(PlainType t1, BuiltInAtomicType p1) throws XPathException {
        if (!p1.isOrdered(true)) {
            throw new XPathException("Type " + t1.toString() + " is not an ordered type")
                    .withErrorCode("XPTY0004")
                    .asTypeError()
                    .withLocation(getLocation());
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
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        getLhs().optimize(visitor, contextInfo);
        getRhs().optimize(visitor, contextInfo);

        return visitor.obtainOptimizer().optimizeValueComparison(this, visitor, contextInfo);
    }





    /**
     * Check whether this specific instance of the expression is negatable
     *
     * @return true if it is
     * @param th the type hierarchy
     */

    @Override
    public boolean isNegatable(TypeHierarchy th) {
        // Expression is not negatable if it might involve NaN
        return isNeverNaN(getLhsExpression(), th) && isNeverNaN(getRhsExpression(), th);
    }

    private boolean isNeverNaN(Expression exp, TypeHierarchy th) {
        return th.relationship(exp.getItemType(), BuiltInAtomicType.DOUBLE) == Affinity.DISJOINT &&
                th.relationship(exp.getItemType(), BuiltInAtomicType.FLOAT) == Affinity.DISJOINT;
    }

    /**
     * Return the negation of this value comparison: that is, a value comparison that returns true()
     * if and only if the original returns false(). The result must be the same as not(this) even in the
     * case where one of the operands is ().
     *
     * @return the inverted comparison
     */

    @Override
    public Expression negate() {
        ValueComparison vc = new ValueComparison(getLhsExpression(), OperatorInfo.negate(operator), getRhsExpression());
        if (resultWhenEmpty == null || resultWhenEmpty == BooleanValue.FALSE) {
            vc.resultWhenEmpty = BooleanValue.TRUE;
        } else {
            vc.resultWhenEmpty = BooleanValue.FALSE;
        }
        vc.needsRuntimeCheck = needsRuntimeCheck;
        ExpressionTool.copyLocationInfo(this, vc);
        return vc;
    }



    @Override
    public boolean equals(Object other) {
        return other instanceof ValueComparison &&
                super.equals(other)
                //&& comparer.equals(((ValueComparison) other).comparer)
        ;
    }

    /**
     * Get a hashCode for comparing two expressions. Note that this hashcode gives the same
     * result for (A op B) and for (B op A), whether or not the operator is commutative.
     */
    @Override
    protected int computeHashCode() {
        return super.computeHashCode();
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings  variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        ValueComparison vc = new ValueComparison(getLhsExpression().copy(rebindings), operator, getRhsExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, vc);
        vc.resultWhenEmpty = resultWhenEmpty;
        vc.needsRuntimeCheck = needsRuntimeCheck;
        return vc;
    }

    /**
     * Evaluate the effective boolean value of the expression
     *
     * @param context the given context for evaluation
     * @return a boolean representing the result of the comparison of the two operands
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForBoolean().eval(context);
    }

    /**
     * Compare two atomic values, using a specified operator and collation
     *
     * @param v0         the first operand
     * @param op         the operator, as defined by constants such as {@link net.sf.saxon.expr.parser.OperatorSymbol#FEQ} or
     *                   {@link net.sf.saxon.expr.parser.OperatorSymbol#FLT}
     * @param v1         the second operand
     * @param comparer   used to compare values. If the comparer is context-sensitive then the context must
     *                   already have been bound using comparer.provideContext().
     * @param checkTypes set to true if it is necessary to check that the types of the arguments are comparable
     * @return the result of the comparison: -1 for LT, 0 for EQ, +1 for GT
     * @throws XPathException if the values are not comparable
     */

    public static boolean compare(AtomicValue v0, OperatorSymbol op, AtomicValue v1, AtomicComparer comparer, boolean checkTypes)
            throws XPathException {
        if (checkTypes &&
                !Type.isGuaranteedComparable(v0.getPrimitiveType(), v1.getPrimitiveType(), OperatorInfo.isOrderedOperator(op))) {
            throw new XPathException("Cannot compare " + Type.displayTypeName(v0) +
                    " to " + Type.displayTypeName(v1)).withErrorCode("XPTY0004").asTypeError();
        }
        if (v0.isNaN() || v1.isNaN()) {
            return op == OperatorSymbol.FNE;
        }
        try {
            return switch (op) {
                case FEQ -> comparer.comparesEqual(v0, v1);
                case FNE -> !comparer.comparesEqual(v0, v1);
                case FGT -> comparer.compareAtomicValues(v0, v1) > 0;
                case FLT -> comparer.compareAtomicValues(v0, v1) < 0;
                case FGE -> comparer.compareAtomicValues(v0, v1) >= 0;
                case FLE -> comparer.compareAtomicValues(v0, v1) <= 0;
                default -> throw new UnsupportedOperationException("Unknown operator " + op);
            };
        } catch (ComparisonException err) {
            throw err.getReason();
        } catch (ClassCastException err) {
            //err.printStackTrace();
            throw new XPathException("Cannot compare " + Type.displayTypeName(v0) +
                    " to " + Type.displayTypeName(v1)).withErrorCode("XPTY0004").asTypeError();
        }
    }

    /**
     * Evaluate the expression in a given context
     *
     * @param context the given context for evaluation
     * @return a BooleanValue representing the result of the numeric comparison of the two operands,
     *         or null representing the empty sequence
     */

    @Override
    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        return (BooleanValue)makeElaborator().elaborateForItem().eval(context);
    }

    /**
     * Determine the data type of the expression
     *
     * @return Type.BOOLEAN
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return BuiltInAtomicType.BOOLEAN;
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType the static type of the context item
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return UType.BOOLEAN;
    }

    /**
     * Determine the static cardinality.
     */

    @Override
    protected int computeCardinality() {
        if (resultWhenEmpty != null) {
            return StaticProperty.EXACTLY_ONE;
        } else {
            return super.computeCardinality();
        }
    }

    @Override
    protected String tag() {
        return "vc";
    }

    @Override
    protected void explainExtraAttributes(ExpressionPresenter out) {
        if (resultWhenEmpty != null) {
            out.emitAttribute("onEmpty", resultWhenEmpty.getBooleanValue() ? "1" : "0");
        }
        if ("JS".equals(out.getOptions().target) && out.getOptions().targetVersion >= 2) {
            // for backwards compatibility, output a comp attribute
            AtomicComparer comparer = getAtomicComparer();
            out.emitAttribute("comp", comparer.save());
        }
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new ValueComparisonElaborator();
    }

    /**
     * Elaborator for a value comparison (such as {@code A eq B}), including the case
     * where a general comparison is reduced to a value comparison by the optimiser
     */

    public static class ValueComparisonElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            final ValueComparison expr = (ValueComparison) getExpression();
            final ItemEvaluator p0 = expr.getLhsExpression().makeElaborator().elaborateForItem();
            final ItemEvaluator p1 = expr.getRhsExpression().makeElaborator().elaborateForItem();
            final BooleanValue resultWhenEmpty = expr.getResultWhenEmpty();
            final int version = expr.getRetainedStaticContext().getPackageData().getHostLanguageVersion();

            StringCollator defaultCollation;
            try {
                defaultCollation = expr.getConfiguration().getCollation(expr.getRetainedStaticContext().getDefaultCollationName());
            } catch (XPathException e) {
                throw new IllegalStateException("Unknown default collation in static context: " + expr.getRetainedStaticContext().getDefaultCollationName());
            }
            final OperatorSymbol operator = expr.getOperator();

            final int card0 = expr.getLhsExpression().getCardinality();
            final int card1 = expr.getRhsExpression().getCardinality();
            if (card0 == StaticProperty.ALLOWS_ZERO || card1 == StaticProperty.ALLOWS_ZERO) {
                return context -> resultWhenEmpty;
            }

            GenericAtomicComparer.AtomicComparisonFunction comparer =
                    GenericAtomicComparer.makeAtomicComparisonFunction(
                            operandType(expr.getLhsExpression()),
                            operandType(expr.getRhsExpression()),
                            defaultCollation,
                            operator, true, version);


            final boolean nullable0 = Cardinality.allowsZero(card0);
            final boolean nullable1 = Cardinality.allowsZero(card1);

            if (!nullable0 && !nullable1) {
                return context -> BooleanValue.get(
                        comparer.compare((AtomicValue) p0.eval(context), (AtomicValue) p1.eval(context), context));
            } else {
                return context -> {
                    AtomicValue v0 = (AtomicValue) p0.eval(context);
                    if (v0 == null) {
                        return resultWhenEmpty;  // normally false
                    }
                    AtomicValue v1 = (AtomicValue) p1.eval(context);
                    if (v1 == null) {
                        return resultWhenEmpty;  // normally false
                    }
                    return BooleanValue.get(comparer.compare(v0, v1, context));
                };
            }

        }

        private BuiltInAtomicType operandType(Expression operand) {
            ItemType type = operand.getItemType();
            if (type == AnyItemType.INSTANCE || type == ErrorType.getInstance()) {
                return BuiltInAtomicType.ANY_ATOMIC;
            } else {
                return (BuiltInAtomicType) type.getPrimitiveItemType();
            }
        }

        public BooleanEvaluator elaborateForBoolean() {
            final ValueComparison expr = (ValueComparison) getExpression();
            final int specVersion = expr.getRetainedStaticContext().getPackageData().getHostLanguageVersion();
            final ItemEvaluator p0 = expr.getLhsExpression().makeElaborator().elaborateForItem();
            final ItemEvaluator p1 = expr.getRhsExpression().makeElaborator().elaborateForItem();
            StringCollator defaultCollation;
            try {
                defaultCollation = expr.getConfiguration().getCollation(expr.getRetainedStaticContext().getDefaultCollationName());
            } catch (XPathException e) {
                throw new IllegalStateException("Unknown default collation in static context: " + expr.getRetainedStaticContext().getDefaultCollationName());
            }
            final OperatorSymbol operator = expr.getOperator();
            final boolean resultWhenEmpty = expr.getResultWhenEmpty() != null && expr.getResultWhenEmpty().getBooleanValue();

            final int card0 = expr.getLhsExpression().getCardinality();
            final int card1 = expr.getRhsExpression().getCardinality();
            if (card0 == StaticProperty.ALLOWS_ZERO || card1 == StaticProperty.ALLOWS_ZERO) {
                return context -> resultWhenEmpty;
            }

            ItemType t0 = expr.getLhsExpression().getItemType().getPrimitiveItemType();
            if (!(t0 instanceof BuiltInAtomicType)) {
                // This can happen after loading from a SEF file; the static type information is not always available
                t0 = BuiltInAtomicType.ANY_ATOMIC;
            }
            ItemType t1 = expr.getRhsExpression().getItemType().getPrimitiveItemType();
            if (!(t1 instanceof BuiltInAtomicType)) {
                // This can happen after loading from a SEF file; the static type information is not always available
                t1 = BuiltInAtomicType.ANY_ATOMIC;
            }
            final GenericAtomicComparer.AtomicComparisonFunction comparer = GenericAtomicComparer.makeAtomicComparisonFunction(
                    (BuiltInAtomicType) t0, (BuiltInAtomicType) t1, defaultCollation, operator, true, specVersion);

            final boolean nullable0 = Cardinality.allowsZero(card0);
            final boolean nullable1 = Cardinality.allowsZero(card1);
            if (!nullable0 && !nullable1) {
                return context -> comparer.compare((AtomicValue) p0.eval(context), (AtomicValue) p1.eval(context), context);
            } else {
                return context -> {
                    AtomicValue v0 = (AtomicValue) p0.eval(context);
                    if (v0 == null) {
                        return resultWhenEmpty;  // normally false
                    }
                    AtomicValue v1 = (AtomicValue) p1.eval(context);
                    if (v1 == null) {
                        return resultWhenEmpty;  // normally false
                    }
                    return comparer.compare(v0, v1, context);
                };
            }

        }



    }
}

