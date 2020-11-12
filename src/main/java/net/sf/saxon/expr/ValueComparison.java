////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.*;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

/**
 * ValueComparison: a boolean expression that compares two atomic values
 * for equals, not-equals, greater-than or less-than. Implements the operators
 * eq, ne, lt, le, gt, ge
 */

public final class ValueComparison extends BinaryExpression implements ComparisonExpression, Negatable {

    private AtomicComparer comparer;
    /*@Nullable*/ private BooleanValue resultWhenEmpty = null;
    private boolean needsRuntimeCheck;

    /**
     * Create a comparison expression identifying the two operands and the operator
     *
     * @param p1 the left-hand operand
     * @param op the operator, as a token returned by the Tokenizer (e.g. Token.LT)
     * @param p2 the right-hand operand
     */

    public ValueComparison(Expression p1, int op, Expression p2) {
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
     * Set the AtomicComparer used to compare atomic values
     *
     * @param comparer the AtomicComparer
     */

    public void setAtomicComparer(AtomicComparer comparer) {
        this.comparer = comparer;
    }

    /**
     * Get the AtomicComparer used to compare atomic values. This encapsulates any collation that is used.
     * Note that the comparer is always known at compile time.
     */

    @Override
    public AtomicComparer getAtomicComparer() {
        return comparer;
    }

    /**
     * Get the primitive (singleton) operator used: one of Token.FEQ, Token.FNE, Token.FLT, Token.FGT,
     * Token.FLE, Token.FGE
     */

    @Override
    public int getSingletonOperator() {
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
        return comparer instanceof UntypedNumericComparer;
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
        StaticContext env = visitor.getStaticContext();

        if (Literal.isEmptySequence(getLhsExpression())) {
            return resultWhenEmpty == null ? getLhsExpression() : Literal.makeLiteral(resultWhenEmpty, this);
        }

        if (Literal.isEmptySequence(getRhsExpression())) {
            return resultWhenEmpty == null ? getRhsExpression() : Literal.makeLiteral(resultWhenEmpty, this);
        }

        if (comparer instanceof UntypedNumericComparer) {
            return this; // we've already done all that needs to be done
        }

        final SequenceType optionalAtomic = SequenceType.OPTIONAL_ATOMIC;
        TypeChecker tc = config.getTypeChecker(false);

        RoleDiagnostic role0 = new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, Token.tokens[operator], 0);
        setLhsExpression(tc.staticTypeCheck(getLhsExpression(), optionalAtomic, role0, visitor));

        RoleDiagnostic role1 = new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, Token.tokens[operator], 1);
        setRhsExpression(tc.staticTypeCheck(getRhsExpression(), optionalAtomic, role1, visitor));

        PlainType t0 = getLhsExpression().getItemType().getAtomizedItemType();
        PlainType t1 = getRhsExpression().getItemType().getAtomizedItemType();

        if (t0.getUType().union(t1.getUType()).overlaps(UType.EXTENSION)) {
            XPathException err = new XPathException("Cannot perform comparisons involving external objects");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            err.setLocation(getLocation());
            throw err;
        }

        BuiltInAtomicType p0 = (BuiltInAtomicType) t0.getPrimitiveItemType();
        if (p0.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            p0 = BuiltInAtomicType.STRING;
        }
        BuiltInAtomicType p1 = (BuiltInAtomicType) t1.getPrimitiveItemType();
        if (p1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            p1 = BuiltInAtomicType.STRING;
        }

        needsRuntimeCheck =
                p0.equals(BuiltInAtomicType.ANY_ATOMIC) || p1.equals(BuiltInAtomicType.ANY_ATOMIC);

        if (!needsRuntimeCheck && !Type.isPossiblyComparable(p0, p1, Token.isOrderedOperator(operator))) {
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

                visitor.getStaticContext().issueWarning("Comparison of " + t0.toString() +
                        (opt0 ? "?" : "") + " to " + t1.toString() +
                        (opt1 ? "?" : "") + " will fail unless " + which + " empty", getLocation());
                needsRuntimeCheck = true;
            } else {
                String message = "In {" + toShortString() + "}: cannot compare " +
                        t0.toString() + " to " + t1.toString();
                XPathException err = new XPathException(message);
                err.setIsTypeError(true);
                err.setErrorCode("XPTY0004");
                err.setLocation(getLocation());
                throw err;
            }
        }
        if (!(operator == Token.FEQ || operator == Token.FNE)) {
            mustBeOrdered(t0, p0);
            mustBeOrdered(t1, p1);
        }

        if (comparer == null) {
            // In XSLT, only do this the first time through, otherwise the default-collation attribute may be missed
            final String defaultCollationName = env.getDefaultCollationName();
            StringCollator comp = config.getCollation(defaultCollationName);
            if (comp == null) {
                comp = CodepointCollator.getInstance();
            }
            comparer = GenericAtomicComparer.makeAtomicComparer(
                    p0, p1, comp, env.getConfiguration().getConversionContext());
        }
        return this;
    }

    private void mustBeOrdered(PlainType t1, BuiltInAtomicType p1) throws XPathException {
        if (!p1.isOrdered(true)) {
            XPathException err = new XPathException("Type " + t1.toString() + " is not an ordered type");
            err.setErrorCode("XPTY0004");
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            throw err;
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
        ValueComparison vc = new ValueComparison(getLhsExpression(), Token.negate(operator), getRhsExpression());
        vc.comparer = comparer;
        if (resultWhenEmpty == null || resultWhenEmpty == BooleanValue.FALSE) {
            vc.resultWhenEmpty = BooleanValue.TRUE;
        } else {
            vc.resultWhenEmpty = BooleanValue.FALSE;
        }
        ExpressionTool.copyLocationInfo(this, vc);
        return vc;
    }



    @Override
    public boolean equals(Object other) {
        return other instanceof ValueComparison &&
                super.equals(other) &&
                comparer.equals(((ValueComparison) other).comparer);
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
        vc.comparer = comparer;
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
        try {
            AtomicValue v0 = (AtomicValue) getLhsExpression().evaluateItem(context);
            if (v0 == null) {
                return resultWhenEmpty == BooleanValue.TRUE;  // normally false
            }
            AtomicValue v1 = (AtomicValue) getRhsExpression().evaluateItem(context);
            if (v1 == null) {
                return resultWhenEmpty == BooleanValue.TRUE;  // normally false
            }
            return compare(v0, operator, v1, comparer.provideContext(context), needsRuntimeCheck);
        } catch (XPathException e) {
            // re-throw the exception with location information added
            e.maybeSetLocation(getLocation());
            e.maybeSetContext(context);
            throw e;
        }
    }

    /**
     * Compare two atomic values, using a specified operator and collation
     *
     * @param v0         the first operand
     * @param op         the operator, as defined by constants such as {@link net.sf.saxon.expr.parser.Token#FEQ} or
     *                   {@link net.sf.saxon.expr.parser.Token#FLT}
     * @param v1         the second operand
     * @param comparer   used to compare values. If the comparer is context-sensitive then the context must
     *                   already have been bound using comparer.provideContext().
     * @param checkTypes set to true if it is necessary to check that the types of the arguments are comparable
     * @return the result of the comparison: -1 for LT, 0 for EQ, +1 for GT
     * @throws XPathException if the values are not comparable
     */

    public static boolean compare(AtomicValue v0, int op, AtomicValue v1, AtomicComparer comparer, boolean checkTypes)
            throws XPathException {
        if (checkTypes &&
                !Type.isGuaranteedComparable(v0.getPrimitiveType(), v1.getPrimitiveType(), Token.isOrderedOperator(op))) {
            XPathException e2 = new XPathException("Cannot compare " + Type.displayTypeName(v0) +
                    " to " + Type.displayTypeName(v1));
            e2.setErrorCode("XPTY0004");
            e2.setIsTypeError(true);
            throw e2;
        }
        if (v0.isNaN() || v1.isNaN()) {
            return op == Token.FNE;
        }
        try {
            switch (op) {
                case Token.FEQ:
                    return comparer.comparesEqual(v0, v1);
                case Token.FNE:
                    return !comparer.comparesEqual(v0, v1);
                case Token.FGT:
                    return comparer.compareAtomicValues(v0, v1) > 0;
                case Token.FLT:
                    return comparer.compareAtomicValues(v0, v1) < 0;
                case Token.FGE:
                    return comparer.compareAtomicValues(v0, v1) >= 0;
                case Token.FLE:
                    return comparer.compareAtomicValues(v0, v1) <= 0;
                default:
                    throw new UnsupportedOperationException("Unknown operator " + op);
            }
        } catch (ComparisonException err) {
            throw err.getCause();
        } catch (ClassCastException err) {
            err.printStackTrace();
            XPathException e2 = new XPathException("Cannot compare " + Type.displayTypeName(v0) +
                    " to " + Type.displayTypeName(v1));
            e2.setErrorCode("XPTY0004");
            e2.setIsTypeError(true);
            throw e2;
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
        try {
            AtomicValue v0 = (AtomicValue) getLhsExpression().evaluateItem(context);
            if (v0 == null) {
                return resultWhenEmpty;
            }
            AtomicValue v1 = (AtomicValue) getRhsExpression().evaluateItem(context);
            if (v1 == null) {
                return resultWhenEmpty;
            }
            return BooleanValue.get(compare(v0, operator, v1, comparer.provideContext(context), needsRuntimeCheck));
        } catch (XPathException e) {
            // re-throw the exception with location information added
            e.maybeSetLocation(getLocation());
            e.maybeSetContext(context);
            throw e;
        }
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
    public int computeCardinality() {
        if (resultWhenEmpty != null) {
            return StaticProperty.EXACTLY_ONE;
        } else {
            return super.computeCardinality();
        }
    }

    @Override
    public String tag() {
        return "vc";
    }

    @Override
    protected void explainExtraAttributes(ExpressionPresenter out) {
        if (resultWhenEmpty != null) {
            out.emitAttribute("onEmpty", resultWhenEmpty.getBooleanValue() ? "1" : "0");
        }
        out.emitAttribute("comp", comparer.save());
    }



}

