////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;


/**
 * Class to handle equivalence comparisons of singletons. This only handles equality comparison.
 * It follows the rules used for grouping and for XQuery 3.0 switch expressions:
 * - each operand must be zero or one atomic values
 * - untypedAtomic is treated as string
 * - non-comparable values are not equal (no type errors)
 * - two empty sequences are equal to each other
 * - two NaN values are equal to each other
 */

public class EquivalenceComparison extends BinaryExpression implements ComparisonExpression {

    private AtomicComparer comparer;
    private boolean knownToBeComparable = false;

    /**
     * Create a singleton comparison - that is, a comparison between two singleton (0:1) sequences
     * using the general comparison semantics
     *
     * @param p1       the first operand
     * @param operator the operator
     * @param p2       the second operand
     */

    public EquivalenceComparison(Expression p1, int operator, Expression p2) {
        super(p1, operator, p2);
    }

    /**
     * Type-check the expression. Default implementation for binary operators that accept
     * any kind of operand
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        StaticContext env = visitor.getStaticContext();
        String defaultCollationName = env.getDefaultCollationName();
        final Configuration config = visitor.getConfiguration();
        StringCollator collation = config.getCollation(defaultCollationName);
        if (collation == null) {
            collation = CodepointCollator.getInstance();
        }
        comparer = new EquivalenceComparer(collation, config.getConversionContext());

        Expression oldOp0 = getLhsExpression();
        Expression oldOp1 = getRhsExpression();

        getLhs().typeCheck(visitor, contextInfo);
        getRhs().typeCheck(visitor, contextInfo);

        // Neither operand needs to be sorted

        setLhsExpression(getLhsExpression().unordered(false, false));
        setRhsExpression(getRhsExpression().unordered(false, false));

        SequenceType atomicType = SequenceType.OPTIONAL_ATOMIC;
        TypeChecker tc = config.getTypeChecker(false);

        RoleDiagnostic role0 = new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, "eq", 0);
        setLhsExpression(tc.staticTypeCheck(getLhsExpression(), atomicType, role0, visitor));

        RoleDiagnostic role1 = new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, "eq", 1);
        setRhsExpression(tc.staticTypeCheck(getRhsExpression(), atomicType, role1, visitor));

        if (getLhsExpression() != oldOp0) {
            adoptChildExpression(getLhsExpression());
        }

        if (getRhsExpression() != oldOp1) {
            adoptChildExpression(getRhsExpression());
        }

        ItemType t0 = getLhsExpression().getItemType();  // this is always an atomic type or empty-sequence()
        ItemType t1 = getRhsExpression().getItemType();  // this is always an atomic type or empty-sequence()

        if (t0 instanceof ErrorType) {
            t0 = BuiltInAtomicType.ANY_ATOMIC;
        }
        if (t1 instanceof ErrorType) {
            t1 = BuiltInAtomicType.ANY_ATOMIC;
        }

        if (t0.getUType().union(t1.getUType()).overlaps(UType.EXTENSION)) {
            XPathException err = new XPathException("Cannot perform comparisons involving external objects");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            err.setLocation(getLocation());
            throw err;
        }

        BuiltInAtomicType pt0 = (BuiltInAtomicType) t0.getPrimitiveItemType();
        BuiltInAtomicType pt1 = (BuiltInAtomicType) t1.getPrimitiveItemType();

        if (t0.equals(BuiltInAtomicType.ANY_ATOMIC) || t0.equals(BuiltInAtomicType.UNTYPED_ATOMIC) ||
                t1.equals(BuiltInAtomicType.ANY_ATOMIC) || t1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            // then no static type checking is possible
        } else {
            if (Type.isGuaranteedComparable(pt0, pt1, false)) {
                knownToBeComparable = true;
            } else if (!Type.isPossiblyComparable(pt0, pt1, false)) {
                env.issueWarning("Cannot compare " + t0.toString() + " to " + t1.toString(), getLocation());
                // This is not an error in a switch statement, but it means the branch will never be chosen
            }
        }

        try {
            if ((getLhsExpression() instanceof Literal) && (getRhsExpression() instanceof Literal)) {
                GroundedValue v = evaluateItem(visitor.getStaticContext().makeEarlyEvaluationContext()).materialize();
                return Literal.makeLiteral(v, this);
            }
        } catch (XPathException err) {
            // if early evaluation fails, suppress the error: the value might
            // not be needed at run-time
        }

//        comparer = GenericAtomicComparer.makeAtomicComparer(
//                    pt0, pt1, collation, getConfiguration().getConversionContext());

        return this;
    }


    @Override
    public AtomicComparer getAtomicComparer() {
        return comparer;
    }

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
        return false;
    }

    /**
     * Determine the static cardinality. Returns [1..1]
     */

    @Override
    public int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
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

    public boolean isKnownToBeComparable() {
        return knownToBeComparable;
    }

    public AtomicComparer getComparer() {
        return comparer;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        EquivalenceComparison sc = new EquivalenceComparison(getLhsExpression().copy(rebindings), operator, getRhsExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, sc);
        sc.comparer = comparer;
        sc.knownToBeComparable = knownToBeComparable;
        return sc;
    }

    /**
     * Evaluate the expression in a given context
     *
     * @param context the given context for evaluation
     * @return a BooleanValue representing the result of the numeric comparison of the two operands
     */

    @Override
    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        return BooleanValue.get(effectiveBooleanValue(context));
    }

    /**
     * Evaluate the expression in a boolean context
     *
     * @param context the given context for evaluation
     * @return a boolean representing the result of the numeric comparison of the two operands
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        AtomicValue v0 = (AtomicValue) getLhsExpression().evaluateItem(context);
        AtomicValue v1 = (AtomicValue) getRhsExpression().evaluateItem(context);

        if (v0 == null || v1 == null) {
            return (v0 == v1);
        }

        AtomicComparer comp2 = comparer.provideContext(context);
        return (knownToBeComparable || Type.isGuaranteedComparable(v0.getPrimitiveType(), v1.getPrimitiveType(), false))
                && comp2.comparesEqual(v0, v1);

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
        return "equivalent";
    }

    @Override
    protected void explainExtraAttributes(ExpressionPresenter out) {
        out.emitAttribute("cardinality", "singleton");
    }
}

// Copyright (c) 2010-2020 Saxonica Limited
