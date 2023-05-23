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
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.expr.sort.UntypedNumericComparer;
import net.sf.saxon.functions.Minimax;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;


/**
 * GeneralComparison: a boolean expression that compares two expressions
 * for equals, not-equals, greater-than or less-than. This implements the operators
 * =, !=, &lt;, &gt;, etc. This implementation is not used when in backwards-compatible mode
 */

public abstract class GeneralComparison extends BinaryExpression implements ComparisonExpression, Callable {

    public enum ComparisonCardinality {ONE_TO_ONE, MANY_TO_ONE, MANY_TO_MANY}
    // Note, a one-to-many comparison is inverted into a many-to-one comparison

    protected int singletonOperator;
    protected AtomicComparer comparer;
    protected boolean needsRuntimeCheck = true;
    protected ComparisonCardinality comparisonCardinality = ComparisonCardinality.MANY_TO_MANY;
    protected boolean doneWarnings = false;


    /**
     * Create a relational expression identifying the two operands and the operator
     *
     * @param p0 the left-hand operand
     * @param op the operator, as a token returned by the Tokenizer (e.g. Token.LT)
     * @param p1 the right-hand operand
     */

    public GeneralComparison(Expression p0, int op, Expression p1) {
        super(p0, op, p1);
        singletonOperator = getCorrespondingSingletonOperator(op);
    }

    /**
     * Ask whether a runtime check of the types of the operands is needed
     *
     * @return true if the types of the operands need to be checked at run-time
     */

    public boolean needsRuntimeCheck() {
        return needsRuntimeCheck;
    }

    /**
     * Say whether a runtime check of the types of the operands is needed
     *
     * @param needsCheck true if the types of the operands need to be checked at run-time
     */

    public void setNeedsRuntimeCheck(boolean needsCheck) {
        needsRuntimeCheck = needsCheck;
    }

    /**
     * Ask whether the comparison is known to be many-to-one, one-to-one, or many-to-many.
     * (Note, an expression that is one-to-many will be converted to one that is many-to-one).
     *
     * @return the Cardinality of the comparison as one of the values {@link ComparisonCardinality#ONE_TO_ONE},
     *         {@link ComparisonCardinality#MANY_TO_MANY}, {@link ComparisonCardinality#MANY_TO_ONE}
     */

    public ComparisonCardinality getComparisonCardinality() {
        return comparisonCardinality;
    }

    /**
     * Say whether the comparison is known to be many-to-one, one-to-one, or many-to-many.
     *
     * @param card the Cardinality of the comparison as one of the values {@link ComparisonCardinality#ONE_TO_ONE},
     *             {@link ComparisonCardinality#MANY_TO_MANY}, {@link ComparisonCardinality#MANY_TO_ONE}
     */
    public void setComparisonCardinality(ComparisonCardinality card) {
        comparisonCardinality = card;
    }

    /**
     * Set the comparer to be used
     *
     * @param comparer the comparer to be used
     */

    public void setAtomicComparer(AtomicComparer comparer) {
        this.comparer = comparer;
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
        return "GeneralComparison";
    }

    /**
     * Get the namespace context for this expression, needed in the event that one operand contains untyped
     * atomic values and the other contains QNames
     */

    public NamespaceResolver getNamespaceResolver() {
        return getRetainedStaticContext();
    }

    /**
     * Get the AtomicComparer used to compare atomic values. This encapsulates any collation that is used
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
        return singletonOperator;
    }

    /**
     * Determine whether untyped atomic values should be converted to the type of the other operand
     *
     * @return true if untyped values should be converted to the type of the other operand, false if they
     *         should be converted to strings.
     */

    @Override
    public boolean convertsUntypedToOther() {
        return true;
    }

    /**
     * Determine the static cardinality. Returns [1..1]
     */

    @Override
    public int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Type-check the expression
     *
     * @return the checked expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        final Configuration config = visitor.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();

        Expression oldOp0 = getLhsExpression();
        Expression oldOp1 = getRhsExpression();

        getLhs().typeCheck(visitor, contextInfo);
        getRhs().typeCheck(visitor, contextInfo);

        // If either operand is statically empty, return false

        if (Literal.isEmptySequence(getLhsExpression()) || Literal.isEmptySequence(getRhsExpression())) {
            return Literal.makeLiteral(BooleanValue.FALSE, this);
        }

        // Neither operand needs to be sorted

        setLhsExpression(getLhsExpression().unordered(false, false));
        setRhsExpression(getRhsExpression().unordered(false, false));

        SequenceType atomicType = SequenceType.ATOMIC_SEQUENCE;

        TypeChecker tc = config.getTypeChecker(false);
        RoleDiagnostic role0 = new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, Token.tokens[operator], 0);
        //role0.setSourceLocator(this);
        setLhsExpression(tc.staticTypeCheck(getLhsExpression(), atomicType, role0, visitor));

        RoleDiagnostic role1 = new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, Token.tokens[operator], 1);
        //role1.setSourceLocator(this);
        setRhsExpression(tc.staticTypeCheck(getRhsExpression(), atomicType, role1, visitor));

        if (getLhsExpression() != oldOp0) {
            adoptChildExpression(getLhsExpression());
        }

        if (getRhsExpression() != oldOp1) {
            adoptChildExpression(getRhsExpression());
        }

        ItemType t0 = getLhsExpression().getItemType();  // this is always an atomic type or union type or xs:error
        ItemType t1 = getRhsExpression().getItemType();  // this is always an atomic type or union type or xs:error

        if (t0 instanceof ErrorType || t1 instanceof ErrorType) {
            return Literal.makeLiteral(BooleanValue.FALSE, this);
        }

        if (t0.getUType().union(t1.getUType()).overlaps(UType.EXTENSION)) {
            XPathException err = new XPathException("Cannot perform comparisons involving external objects");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            err.setLocator(getLocation());
            throw err;
        }

        BuiltInAtomicType pt0 = (BuiltInAtomicType) t0.getPrimitiveItemType();
        BuiltInAtomicType pt1 = (BuiltInAtomicType) t1.getPrimitiveItemType();

        int c0 = getLhsExpression().getCardinality();
        int c1 = getRhsExpression().getCardinality();

        if (c0 == StaticProperty.EMPTY || c1 == StaticProperty.EMPTY) {
            return Literal.makeLiteral(BooleanValue.FALSE, this);
        }

        if (t0.equals(BuiltInAtomicType.ANY_ATOMIC) || t0.equals(BuiltInAtomicType.UNTYPED_ATOMIC) ||
                t1.equals(BuiltInAtomicType.ANY_ATOMIC) || t1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            // then no static type checking is possible
        } else {
            if (!Type.isPossiblyComparable(pt0, pt1, Token.isOrderedOperator(singletonOperator))) {
                String message = "In {" + toShortString() + "}: cannot compare " + t0 + " to " + t1;
                if (Cardinality.allowsZero(c0) || Cardinality.allowsZero(c1)) {
                    if (!doneWarnings) { // avoid duplicate warnings
                        doneWarnings = true;
                        String which = "one";
                        if (Cardinality.allowsZero(c0) && !Cardinality.allowsZero(c1)) {
                            which = "the first";
                        } else if (Cardinality.allowsZero(c1) && !Cardinality.allowsZero(c0)) {
                            which = "the second";
                        }
                        visitor.getStaticContext().issueWarning(
                            message + ". The comparison can succeed only if " + which +
                                " operand is empty, and in that case will always be false", getLocation());
                    }
                } else {
                    XPathException err = new XPathException(message);
                    err.setErrorCode("XPTY0004");
                    err.setIsTypeError(true);
                    err.setLocator(getLocation());
                    throw err;
                }
            }

        }

        needsRuntimeCheck = !Type.isGuaranteedGenerallyComparable(pt0, pt1, Token.isOrderedOperator(singletonOperator));

        if (!Cardinality.allowsMany(c0) /*c0 == StaticProperty.EXACTLY_ONE*/ &&
                !Cardinality.allowsMany(c1) /*c1 == StaticProperty.EXACTLY_ONE */ &&
                !t0.equals(BuiltInAtomicType.ANY_ATOMIC) &&
                !t1.equals(BuiltInAtomicType.ANY_ATOMIC)) {

            // Use a value comparison if both arguments are singletons, and if the comparison operator to
            // be used can be determined.

            Expression e0 = getLhsExpression();
            Expression e1 = getRhsExpression();

            if (t0.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                if (t1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                    e0 = new CastExpression(getLhsExpression(), BuiltInAtomicType.STRING, Cardinality.allowsZero(c0));
                    adoptChildExpression(e0);
                    e1 = new CastExpression(getRhsExpression(), BuiltInAtomicType.STRING, Cardinality.allowsZero(c1));
                    adoptChildExpression(e1);
                } else if (NumericType.isNumericType(t1)) {
                    Expression vun = makeCompareUntypedToNumeric(getLhsExpression(), getRhsExpression(), singletonOperator);
                    return vun.typeCheck(visitor, contextInfo);
                } else {
                    e0 = new CastExpression(getLhsExpression(), pt1, Cardinality.allowsZero(c0));
                    adoptChildExpression(e0);
                }
            } else if (t1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                if (NumericType.isNumericType(t0)) {
//                    e1 = new CastExpression(getRhsExpression(), BuiltInAtomicType.DOUBLE, false);
//                    adoptChildExpression(e1);
                    Expression vun = makeCompareUntypedToNumeric(getRhsExpression(), getLhsExpression(), Token.inverse(singletonOperator));
                    return vun.typeCheck(visitor, contextInfo);
                } else {
                    e1 = new CastExpression(getRhsExpression(), pt0, Cardinality.allowsZero(c1));
                    adoptChildExpression(e1);
                }
            }

            ValueComparison vc = new ValueComparison(e0, singletonOperator, e1);
            vc.setAtomicComparer(comparer);
            vc.setResultWhenEmpty(BooleanValue.FALSE);
            ExpressionTool.copyLocationInfo(this, vc);
            Optimizer.trace(config, "Replaced general comparison by value comparison", vc);
            return vc.typeCheck(visitor, contextInfo);
        }

        StaticContext env = visitor.getStaticContext();
        if (comparer == null) {
            // In XSLT, only do this the first time through, otherwise default-collation may be missed
            final String defaultCollationName = env.getDefaultCollationName();
            StringCollator collation = config.getCollation(defaultCollationName);
            if (collation == null) {
                collation = CodepointCollator.getInstance();
            }
            comparer = GenericAtomicComparer.makeAtomicComparer(
                    pt0, pt1, collation, config.getConversionContext());
        }

        // evaluate the expression now if both arguments are constant

        if ((getLhsExpression() instanceof Literal) && (getRhsExpression() instanceof Literal)) {
            return Literal.makeLiteral(evaluateItem(env.makeEarlyEvaluationContext()), this);
        }
        return this;
    }

    private Expression makeCompareUntypedToNumeric(Expression lhs, Expression rhs, int operator) {
        ValueComparison vc = new ValueComparison(lhs, operator, rhs);
        vc.setAtomicComparer(new UntypedNumericComparer());
        ExpressionTool.copyLocationInfo(this, vc);
        Optimizer.trace(getConfiguration(), "Replaced general comparison by untyped-numeric value comparison", vc);
        return vc;
    }


    private static Expression makeMinOrMax(Expression exp, String function) {
        if (Cardinality.allowsMany(exp.getCardinality())) {
            Expression fn = SystemFunction.makeCall(function, exp.getRetainedStaticContext(), exp);
            assert fn != null;
            ((Minimax) ((SystemFunctionCall) fn).getTargetFunction()).setIgnoreNaN(true);
            return fn;
        } else {
            return exp;
        }
    }

    @Override
    public int getIntrinsicDependencies() {
        // The expression is dependent on the static namespace context if one operand might deliver
        // untypedAtomic and the other might deliver a QName
        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        if (mayInvolveCastToQName(th, getLhsExpression(), getRhsExpression()) ||
                mayInvolveCastToQName(th, getRhsExpression(), getLhsExpression())) {
            return StaticProperty.DEPENDS_ON_STATIC_CONTEXT;
        } else {
            return 0;
        }
    }

    private boolean mayInvolveCastToQName(TypeHierarchy th, Expression e1, Expression e2) {
        SimpleType s1 = (SimpleType) e1.getItemType().getAtomizedItemType();
        return (s1 == BuiltInAtomicType.ANY_ATOMIC || s1.isNamespaceSensitive()) &&
                th.relationship(e2.getItemType().getAtomizedItemType(), BuiltInAtomicType.UNTYPED_ATOMIC) != Affinity.DISJOINT &&
                (e2.getSpecialProperties() & StaticProperty.NOT_UNTYPED_ATOMIC) == 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GeneralComparison &&
                super.equals(other) &&
                comparer.equals(((GeneralComparison) other).comparer);
    }


    /**
     * Optimize the expression
     *
     * @return the checked expression
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        final StaticContext env = visitor.getStaticContext();

        getLhs().optimize(visitor, contextInfo);
        getRhs().optimize(visitor, contextInfo);

        // If either operand is statically empty, return false

        if (Literal.isEmptySequence(getLhsExpression()) || Literal.isEmptySequence(getRhsExpression())) {
            return Literal.makeLiteral(BooleanValue.FALSE, this);
        }

        // Neither operand needs to be sorted

        setLhsExpression(getLhsExpression().unordered(false, false));
        setRhsExpression(getRhsExpression().unordered(false, false));

        if (getLhsExpression() instanceof Literal && getRhsExpression() instanceof Literal) {
            return Literal.makeLiteral(
                    evaluateItem(visitor.getStaticContext().makeEarlyEvaluationContext()).materialize(), this
            );
        }

        ItemType t0 = getLhsExpression().getItemType();
        ItemType t1 = getRhsExpression().getItemType();

        int c0 = getLhsExpression().getCardinality();
        int c1 = getRhsExpression().getCardinality();

        // Check if neither argument allows a sequence of >1

        boolean many0 = Cardinality.allowsMany(c0);
        boolean many1 = Cardinality.allowsMany(c1);

        if (many0) {
            if (many1) {
                comparisonCardinality = ComparisonCardinality.MANY_TO_MANY;
            } else {
                comparisonCardinality = ComparisonCardinality.MANY_TO_ONE;
            }
        } else {
            if (many1) {
                GeneralComparison mc = getInverseComparison();
                mc.comparisonCardinality = ComparisonCardinality.MANY_TO_ONE;
                ExpressionTool.copyLocationInfo(this, mc);
                mc.comparer = comparer;
                mc.needsRuntimeCheck = needsRuntimeCheck;
                return mc.optimize(visitor, contextInfo);
            } else {
                comparisonCardinality = ComparisonCardinality.ONE_TO_ONE;
            }
        }

        // look for (N to M = I)
        if (operator == Token.EQUALS) {

            // First a variable range...

            if (getLhsExpression() instanceof RangeExpression) {
                Expression min = ((RangeExpression) getLhsExpression()).getLhsExpression();
                Expression max = ((RangeExpression) getLhsExpression()).getRhsExpression();
                IntegerRangeTest ir = new IntegerRangeTest(getRhsExpression(), min, max);
                ExpressionTool.copyLocationInfo(this, ir);
                return ir;
            }

            if (getRhsExpression() instanceof RangeExpression) {
                Expression min = ((RangeExpression) getRhsExpression()).getLhsExpression();
                Expression max = ((RangeExpression) getRhsExpression()).getRhsExpression();
                IntegerRangeTest ir = new IntegerRangeTest(getLhsExpression(), min, max);
                ExpressionTool.copyLocationInfo(this, ir);
                return ir;
            }

            // Now a fixed range...

            if (getLhsExpression() instanceof Literal) {
                GroundedValue value0 = ((Literal) getLhsExpression()).getValue();
                if (value0 instanceof IntegerRange) {
                    long min = ((IntegerRange) value0).getStart();
                    long max = ((IntegerRange) value0).getEnd();
                    IntegerRangeTest ir = new IntegerRangeTest(getRhsExpression(),
                            Literal.makeLiteral(Int64Value.makeIntegerValue(min), this),
                            Literal.makeLiteral(Int64Value.makeIntegerValue(max), this));
                    ExpressionTool.copyLocationInfo(this, ir);
                    return ir;
                }
            }

            if (getRhsExpression() instanceof Literal) {
                GroundedValue value1 = ((Literal) getRhsExpression()).getValue();
                if (value1 instanceof IntegerRange) {
                    long min = ((IntegerRange) value1).getStart();
                    long max = ((IntegerRange) value1).getEnd();
                    IntegerRangeTest ir = new IntegerRangeTest(getLhsExpression(),
                            Literal.makeLiteral(Int64Value.makeIntegerValue(min), this),
                            Literal.makeLiteral(Int64Value.makeIntegerValue(max), this));
                    ExpressionTool.copyLocationInfo(this, ir);
                    return ir;
                }
            }
        }

        // If the operator is gt, ge, lt, le then replace X < Y by min(X) < max(Y)

        // This optimization is done only in the case where at least one of the
        // sequences is known to be purely numeric. It isn't safe if both sequences
        // contain untyped atomic values, because in that case, the type of the
        // comparison isn't known in advance. For example [(1, U1) < ("fred", U2)]
        // involves both string and numeric comparisons.

        // Generally, do this optimization for a many-to-many comparison, because it prevents
        // early exit on a many-to-one comparison. But with a many-to-one comparison, do it
        // if the "many" branch can be lifted up the expression tree.

        if (operator != Token.EQUALS &&
                operator != Token.NE &&
                (comparisonCardinality == ComparisonCardinality.MANY_TO_MANY ||
                         comparisonCardinality == ComparisonCardinality.MANY_TO_ONE && (manyOperandIsLiftable() || manyOperandIsRangeExpression())) &&
                (NumericType.isNumericType(t0) || NumericType.isNumericType(t1))) {

            // System.err.println("** using minimax optimization **");
            ValueComparison vc;
            switch (operator) {
                case Token.LT:
                case Token.LE:
                    vc = new ValueComparison(makeMinOrMax(getLhsExpression(), "min"),
                            singletonOperator,
                            makeMinOrMax(getRhsExpression(), "max"));
                    vc.setResultWhenEmpty(BooleanValue.FALSE);
                    vc.setAtomicComparer(comparer);
                    break;
                case Token.GT:
                case Token.GE:
                    vc = new ValueComparison(makeMinOrMax(getLhsExpression(), "max"),
                            singletonOperator,
                            makeMinOrMax(getRhsExpression(), "min"));
                    vc.setResultWhenEmpty(BooleanValue.FALSE);
                    vc.setAtomicComparer(comparer);
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown operator " + operator);
            }

            ExpressionTool.copyLocationInfo(this, vc);
            vc.setRetainedStaticContext(getRetainedStaticContext());
            return vc.typeCheck(visitor, contextInfo).optimize(visitor, contextInfo);
        }

        // evaluate the expression now if both arguments are constant

        if ((getLhsExpression() instanceof Literal) && (getRhsExpression() instanceof Literal)) {
            return Literal.makeLiteral(evaluateItem(env.makeEarlyEvaluationContext()), this);
        }

        // Finally, convert to use the GeneralComparisonEE algorithm if in Saxon-EE
        return visitor.obtainOptimizer()
                .optimizeGeneralComparison(visitor, this, false, contextInfo);
    }

    private boolean manyOperandIsLiftable() {
        if (getParentExpression() instanceof ContextSwitchingExpression &&
                ((ContextSwitchingExpression)getParentExpression()).getActionExpression() == this) {
            for (Operand o : operands()) {
                if (Cardinality.allowsMany(o.getChildExpression().getCardinality())) {
                    if (ExpressionTool.dependsOnFocus(o.getChildExpression())) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    private boolean manyOperandIsRangeExpression() {
        for (Operand o : operands()) {
            Expression e = o.getChildExpression();
            if (Cardinality.allowsMany(e.getCardinality())) {
                return (e instanceof RangeExpression ||
                        e instanceof Literal && ((Literal)e).getValue() instanceof IntegerRange);
            }
        }
        return false; // shouldn't reach here.
    }

    /**
     * Evaluate the expression in a given context
     *
     * @param context the given context for evaluation
     * @return a BooleanValue representing the result of the numeric comparison of the two operands
     */

    /*@Nullable*/
    @Override
    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        return BooleanValue.get(effectiveBooleanValue(context));
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws XPathException if a dynamic error occurs during the evaluation of the expression
     */

    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        switch (comparisonCardinality) {
            case ONE_TO_ONE: {
                AtomicValue value0 = (AtomicValue) arguments[0].head();
                AtomicValue value1 = (AtomicValue) arguments[1].head();
                return BooleanValue.get(evaluateOneToOne(value0, value1, context));
            }
            case MANY_TO_ONE: {
                SequenceIterator iter0 = arguments[0].iterate();
                AtomicValue value1 = (AtomicValue) arguments[1].head();
                return BooleanValue.get(evaluateManyToOne(iter0, value1, context));
            }
            case MANY_TO_MANY: {
                SequenceIterator iter1 = arguments[0].iterate();
                SequenceIterator iter2 = arguments[1].iterate();
                return BooleanValue.get(evaluateManyToMany(iter1, iter2, context));
            }
        }
        return null;
    }

    /**
     * Evaluate the expression in a boolean context
     *
     * @param context the given context for evaluation
     * @return a boolean representing the result of the numeric comparison of the two operands
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        switch (comparisonCardinality) {
            case ONE_TO_ONE: {
                AtomicValue value0 = (AtomicValue) getLhsExpression().evaluateItem(context);
                AtomicValue value1 = (AtomicValue) getRhsExpression().evaluateItem(context);
                return evaluateOneToOne(value0, value1, context);
            }
            case MANY_TO_ONE: {
                SequenceIterator iter0 = getLhsExpression().iterate(context);
                AtomicValue value1 = (AtomicValue) getRhsExpression().evaluateItem(context);
                return evaluateManyToOne(iter0, value1, context);
            }
            case MANY_TO_MANY: {
                SequenceIterator iter1 = getLhsExpression().iterate(context);
                SequenceIterator iter2 = getRhsExpression().iterate(context);
                return evaluateManyToMany(iter1, iter2, context);
            }
        }
        return false;
    }

    /**
     * Evaluate a (zero-or-one)-to-(zero-or-one) comparison
     *
     * @param value0  the first value, or null if empty
     * @param value1  the second value, or null if empty
     * @param context dynamic evaluation context
     * @return the comparison result
     * @throws XPathException if a dynamic error occurs
     */

    private boolean evaluateOneToOne(AtomicValue value0, AtomicValue value1, XPathContext context) throws XPathException {
        try {
            return !(value0 == null || value1 == null) &&
                    compare(value0, singletonOperator, value1, comparer.provideContext(context),
                            needsRuntimeCheck, context, getRetainedStaticContext());
        } catch (XPathException e) {
            // re-throw the exception with location information added
            e.maybeSetLocation(getLocation());
            e.maybeSetContext(context);
            throw e;
        }

    }

    /**
     * Evaluate a (zero-to-many)-to-(zero-or-one) comparison
     *
     * @param iter0   iterator over the first value
     * @param value1  the second value, or null if empty
     * @param context dynamic evaluation context
     * @return the comparison result
     * @throws XPathException if a dynamic error occurs
     */

    private boolean evaluateManyToOne(SequenceIterator iter0, AtomicValue value1, XPathContext context) throws XPathException {
        try {
            if (value1 == null) {
                return false;
            }
            AtomicValue item0;
            AtomicComparer boundComparer = comparer.provideContext(context);
            while ((item0 = (AtomicValue) iter0.next()) != null) {
                if (compare(item0, singletonOperator, value1, boundComparer, needsRuntimeCheck, context, getRetainedStaticContext())) {
                    iter0.close();
                    return true;
                }
            }
            return false;
        } catch (XPathException e) {
            // re-throw the exception with location information added
            e.maybeSetLocation(getLocation());
            e.maybeSetContext(context);
            throw e;
        }

    }

    /**
     * Evaluate a (zero-or-one)-to-(zero-or-one) comparison
     *
     * @param iter0   iterator over the first value
     * @param iter1   iterator the second value
     * @param context dynamic evaluation context
     * @return the comparison result
     * @throws XPathException if a dynamic error occurs
     */

    public boolean evaluateManyToMany(SequenceIterator iter0, SequenceIterator iter1, XPathContext context) throws XPathException {
        try {
            boolean exhausted0 = false;
            boolean exhausted1 = false;

            List<AtomicValue> value0 = new ArrayList<>();
            List<AtomicValue> value1 = new ArrayList<>();

            AtomicComparer boundComparer = comparer.provideContext(context);

            // Read items from the two sequences alternately, in each case comparing the item to
            // all items that have previously been read from the other sequence. In the worst case
            // the number of comparisons is N*M, and the memory usage is (max(N,M)*2) where N and M
            // are the number of items in the two sequences. In practice, either M or N is often 1,
            // meaning that in this case neither list will ever hold more than one item.

            while (true) {
                if (!exhausted0) {
                    AtomicValue item0 = (AtomicValue) iter0.next();
                    if (item0 == null) {
                        if (exhausted1) {
                            return false;
                        }
                        exhausted0 = true;
                    } else {
                        for (AtomicValue item1 : value1) {
                            if (compare(item0, singletonOperator, item1, boundComparer, needsRuntimeCheck, context, getRetainedStaticContext())) {
                                iter0.close();
                                iter1.close();
                                return true;
                            }
                        }
                        if (!exhausted1) {
                            value0.add(item0);
                        }
                    }
                }
                if (!exhausted1) {
                    AtomicValue item1 = (AtomicValue) iter1.next();
                    if (item1 == null) {
                        if (exhausted0) {
                            return false;
                        }
                        exhausted1 = true;
                    } else {
                        for (AtomicValue item0 : value0) {
                            if (compare(item0, singletonOperator, item1, boundComparer, needsRuntimeCheck, context, getRetainedStaticContext())) {
                                iter0.close();
                                iter1.close();
                                return true;
                            }
                        }
                        if (!exhausted0) {
                            value1.add(item1);
                        }
                    }
                }
            }
        } catch (XPathException e) {
            // re-throw the exception with location information added
            e.maybeSetLocation(getLocation());
            e.maybeSetContext(context);
            throw e;
        }

    }

    /**
     * Compare two atomic values
     *
     *
     * @param a0         the first value
     * @param operator   the singleton version of the comparison operator,
     *                   for example {@link net.sf.saxon.expr.parser.Token#FEQ}
     * @param a1         the second value
     * @param comparer   the comparer to be used to perform the comparison. If the comparer is context-sensitive
     *                   then the context must already have been bound using comparer.provideContext().
     * @param checkTypes set to true if the operand types need to be checked for comparability at runtime
     * @param context    the XPath evaluation context
     * @param nsResolver namespace resolver
     * @return true if the comparison succeeds
     * @throws XPathException if a dynamic error occurs during the comparison
     */

    public static boolean compare(AtomicValue a0,
                                  int operator,
                                  AtomicValue a1,
                                  AtomicComparer comparer,
                                  boolean checkTypes,
                                  XPathContext context,
                                  NamespaceResolver nsResolver) throws XPathException {

        boolean u0 = a0 instanceof UntypedAtomicValue;
        boolean u1 = a1 instanceof UntypedAtomicValue;
        if (u0 != u1) {
            // one value untyped, the other not
            final ConversionRules rules = context.getConfiguration().getConversionRules();
            if (u0) {
                // a0 is untyped atomic
                if (a1 instanceof NumericValue) {
                    return UntypedNumericComparer.quickCompare((UntypedAtomicValue) a0, (NumericValue) a1, operator, rules);
                } else if (a1 instanceof StringValue) {
                    // no conversion needed
                } else {
                    StringConverter sc = a1.getItemType().getPrimitiveItemType().getStringConverter(rules);
                    if (a1 instanceof QualifiedNameValue) {
                        sc = (StringConverter) sc.setNamespaceResolver(nsResolver);
                    }
                    a0 = sc.convertString(a0.getStringValueCS()).asAtomic();
                }
            } else {
                // a1 is untyped atomic
                if (a0 instanceof NumericValue) {
                    return UntypedNumericComparer.quickCompare((UntypedAtomicValue) a1, (NumericValue) a0, Token.inverse(operator), rules);
                } else if (a0 instanceof StringValue) {
                    // no conversion needed
                } else {
                    StringConverter sc = a0.getItemType().getPrimitiveItemType().getStringConverter(rules);
                    if (a0 instanceof QualifiedNameValue) {
                        sc = (StringConverter) sc.setNamespaceResolver(nsResolver);
                    }
                    a1 = sc.convertString(a1.getStringValueCS()).asAtomic();
                }
            }
            checkTypes = false; // No further checking needed if conversion succeeded
        }
        return ValueComparison.compare(a0, operator, a1, comparer, checkTypes);

    }

    /**
     * Determine the data type of the expression
     *
     * @return the value BuiltInAtomicType.BOOLEAN
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
     * @param contextItemType static information about the context item
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return UType.BOOLEAN;
    }

    /**
     * Return the singleton form of the comparison operator, e.g. FEQ for EQUALS, FGT for GT
     *
     * @param op the many-to-many form of the operator, for example {@link Token#LE}
     * @return the corresponding singleton operator, for example {@link Token#FLE}
     */

    public static int getCorrespondingSingletonOperator(int op) {
        switch (op) {
            case Token.EQUALS:
                return Token.FEQ;
            case Token.GE:
                return Token.FGE;
            case Token.NE:
                return Token.FNE;
            case Token.LT:
                return Token.FLT;
            case Token.GT:
                return Token.FGT;
            case Token.LE:
                return Token.FLE;
            default:
                return op;
        }
    }

    protected GeneralComparison getInverseComparison() {
        GeneralComparison20 gc2 = new GeneralComparison20(getRhsExpression(), Token.inverse(operator), getLhsExpression());
        gc2.setRetainedStaticContext(getRetainedStaticContext());
        return gc2;
    }

  /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "GeneralComparison";
    }


//    protected String displayOperator() {
//        return "many-to-many " + super.displayOperator();
//    }

    /**
     * Get the element name used to identify this expression in exported expression format
     * @return the element name used to identify this expression
     */

    @Override
    public String tag() {
        return "gc";
    }

    @Override
    protected void explainExtraAttributes(ExpressionPresenter out) {
        String cc = "";
        switch (comparisonCardinality) {
            case ONE_TO_ONE:
                cc = "1:1";
                break;
            case MANY_TO_ONE:
                cc = "N:1";
                break;
            case MANY_TO_MANY:
                cc = "M:N";
                break;
        }
        out.emitAttribute("card", cc);
        out.emitAttribute("comp", comparer.save());
    }

}

