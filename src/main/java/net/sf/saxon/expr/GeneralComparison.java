////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.*;
import net.sf.saxon.functions.Minimax;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpSimpleEnum;
import net.sf.saxon.tree.iter.RangeIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;


/**
 * GeneralComparison: a boolean expression that compares two expressions
 * for equals, not-equals, greater-than or less-than. This implements the operators
 * =, !=, &lt;, &gt;, etc. This implementation is not used when in backwards-compatible mode
 */

public abstract class GeneralComparison extends BinaryExpression implements ComparisonExpression {

    @CSharpSimpleEnum
    public enum ComparisonCardinality {ONE_TO_ONE, MANY_TO_ONE, MANY_TO_MANY}
    // Note, a one-to-many comparison is inverted into a many-to-one comparison

    protected OperatorSymbol singletonOperator;
    protected AtomicComparer comparer;
    protected boolean runtimeCheckNeeded = true;
    protected ComparisonCardinality comparisonCardinality = ComparisonCardinality.MANY_TO_MANY;
    protected boolean doneWarnings = false;


    /**
     * Create a relational expression identifying the two operands and the operator
     *
     * @param p0 the left-hand operand
     * @param op the operator, as a token returned by the Tokenizer (e.g. Token.LT)
     * @param p1 the right-hand operand
     */

    public GeneralComparison(Expression p0, OperatorSymbol op, Expression p1) {
        super(p0, op, p1);
        singletonOperator = getCorrespondingSingletonOperator(op);
    }

    /**
     * Ask whether a runtime check of the types of the operands is needed
     *
     * @return true if the types of the operands need to be checked at run-time
     */

    public boolean needsRuntimeCheck() {
        return runtimeCheckNeeded;
    }

    /**
     * Say whether a runtime check of the types of the operands is needed
     *
     * @param needsCheck true if the types of the operands need to be checked at run-time
     */

    public void setNeedsRuntimeCheck(boolean needsCheck) {
        runtimeCheckNeeded = needsCheck;
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
     * @return the resolver used for namespace prefix resolution
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
     * Get the StringCollator used to compare string values.
     *
     * @return the collator. May return null if the expression will never be used to compare strings
     */
    @Override
    public StringCollator getStringCollator() {
        return comparer.getCollator();
    }

    /**
     * Get the primitive (singleton) operator used: one of Token.FEQ, Token.FNE, Token.FLT, Token.FGT,
     * Token.FLE, Token.FGE
     */

    @Override
    public OperatorSymbol getSingletonOperator() {
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
    protected int computeCardinality() {
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
        int version = visitor.getStaticContext().getPackageData().getHostLanguageVersion();

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
        Supplier<RoleDiagnostic> role0 =
                () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, operator.toString(), 0);
        setLhsExpression(tc.staticTypeCheck(getLhsExpression(), atomicType, role0, visitor));

        Supplier<RoleDiagnostic> role1 =
                () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, operator.toString(), 1);
        setRhsExpression(tc.staticTypeCheck(getRhsExpression(), atomicType, role1, visitor));

        if (getLhsExpression() != oldOp0) {
            adoptChildExpression(getLhsExpression());
        }

        if (getRhsExpression() != oldOp1) {
            adoptChildExpression(getRhsExpression());
        }

        ItemType t0 = getLhsExpression().getItemType();  // this is always an atomic type or union type or xs:error
        ItemType t1 = getRhsExpression().getItemType();  // this is always an atomic type or union type or xs:error

        if (t0.getUType().union(t1.getUType()).overlaps(UType.EXTENSION)) {
            throw new XPathException("Cannot perform comparisons involving external objects")
                    .asTypeError().withErrorCode("XPTY0004").withLocation(getLocation());
        }

        int c0 = getLhsExpression().getCardinality();
        int c1 = getRhsExpression().getCardinality();

        if (c0 == StaticProperty.EMPTY || c1 == StaticProperty.EMPTY) {
            return Literal.makeLiteral(BooleanValue.FALSE, this);
        }

        // Technically, if one operand raises an error we could return false (and in the past we did so), but that's unfriendly.
        if (t0 instanceof ErrorType) {
            return getLhsExpression();
        }

        if (t1 instanceof ErrorType) {
            return getRhsExpression();
        }

        BuiltInAtomicType pt0 = (BuiltInAtomicType) t0.getPrimitiveItemType();
        BuiltInAtomicType pt1 = (BuiltInAtomicType) t1.getPrimitiveItemType();

        if (pt0.equals(BuiltInAtomicType.ANY_ATOMIC) || pt0.equals(BuiltInAtomicType.UNTYPED_ATOMIC) ||
                pt1.equals(BuiltInAtomicType.ANY_ATOMIC) || pt1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            // then no static type checking is possible
        } else {
            if (!Type.isPossiblyComparable(pt0, pt1, OperatorInfo.isOrderedOperator(singletonOperator), version >= 40)) {
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
                                " operand is empty, and in that case will always be false", SaxonErrorCode.SXWN9025, getLocation());
                    }
                } else {
                    throw new XPathException(message)
                            .withErrorCode("XPTY0004").asTypeError().withLocation(getLocation());
                }
            }

        }

        runtimeCheckNeeded = !Type.isGuaranteedGenerallyComparable(pt0, pt1, OperatorInfo.isOrderedOperator(singletonOperator));

        if (!Cardinality.allowsMany(c0) /*c0 == StaticProperty.EXACTLY_ONE*/ &&
                !Cardinality.allowsMany(c1) /*c1 == StaticProperty.EXACTLY_ONE */ &&
                !pt0.equals(BuiltInAtomicType.ANY_ATOMIC) &&
                !pt1.equals(BuiltInAtomicType.ANY_ATOMIC)) {

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
                    setAtomicComparer(new UntypedNumericComparer());
                    return this;
//                    Expression vun = makeCompareUntypedToNumeric(getLhsExpression(), getRhsExpression(), singletonOperator);
//                    return vun.typeCheck(visitor, contextInfo);
                } else {
                    e0 = new CastExpression(getLhsExpression(), pt1, Cardinality.allowsZero(c0));
                    adoptChildExpression(e0);
                }
            } else if (t1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                if (NumericType.isNumericType(t0)) {
                    setAtomicComparer(new UntypedNumericComparer());
                    return this;
//                    e1 = new CastExpression(getRhsExpression(), BuiltInAtomicType.DOUBLE, false);
//                    adoptChildExpression(e1);
//                    Expression vun = makeCompareUntypedToNumeric(getRhsExpression(), getLhsExpression(), Token.inverse(singletonOperator));
//                    return vun.typeCheck(visitor, contextInfo);
                } else {
                    e1 = new CastExpression(getRhsExpression(), pt0, Cardinality.allowsZero(c1));
                    adoptChildExpression(e1);
                }
            }

            ValueComparison vc = new ValueComparison(e0, singletonOperator, e1);
            //vc.setAtomicComparer(comparer);
            vc.setResultWhenEmpty(BooleanValue.FALSE);
            ExpressionTool.copyLocationInfo(this, vc);
            Optimizer.trace(config, "Replaced general comparison by value comparison", vc);
            return vc.typeCheck(visitor, contextInfo);
        }

        StaticContext env = visitor.getStaticContext();

        final String defaultCollationName = getRetainedStaticContext().getDefaultCollationName();
        StringCollator collation = config.getCollation(defaultCollationName);
        if (collation == null) {
            collation = CodepointCollator.getInstance();
        }
        comparer = GenericAtomicComparer.makeAtomicComparer(
                pt0, pt1, collation, version, config.getConversionContext());


        // evaluate the expression now if both arguments are constant

        if ((getLhsExpression() instanceof Literal) && (getRhsExpression() instanceof Literal)) {
            return Literal.makeLiteral(evaluateItem(env.makeEarlyEvaluationContext()), this);
        }
        return this;
    }


    private static Expression makeMinOrMax(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo,
                                           Expression exp, String function) throws XPathException {
        if (Cardinality.allowsMany(exp.getCardinality())) {
            SystemFunction fn = SystemFunction.makeFunction(function, exp.getRetainedStaticContext(), 1);
            ((Minimax)fn).setIgnoreNaN(true);
            Expression x = fn.makeOptimizedFunctionCall(visitor, contextInfo, exp);
            if (x == null) {
                x = fn.makeFunctionCall(exp);
            }
            return x;
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
     * Get a hashCode for comparing two expressions. Note that this hashcode gives the same
     * result for (A op B) and for (B op A), whether or not the operator is commutative.
     */
    @Override
    protected int computeHashCode() {
        return super.computeHashCode();
    }

    /**
     * Optimize the expression
     *
     * @return the checked expression
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

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
                mc.runtimeCheckNeeded = runtimeCheckNeeded;
                return mc.optimize(visitor, contextInfo);
            } else {
                comparisonCardinality = ComparisonCardinality.ONE_TO_ONE;
            }
        }

        // look for (N to M = I)
        if (operator == OperatorSymbol.EQUALS) {

            // First a variable range...

            if (getLhsExpression() instanceof RangeExpression) {
                Expression min = ((RangeExpression) getLhsExpression()).getStartExpression();
                Expression max = ((RangeExpression) getLhsExpression()).getEndExpression();
                IntegerRangeTest ir = new IntegerRangeTest(getRhsExpression(), min, max);
                ExpressionTool.copyLocationInfo(this, ir);
                return ir;
            }

            if (getRhsExpression() instanceof RangeExpression) {
                Expression min = ((RangeExpression) getRhsExpression()).getStartExpression();
                Expression max = ((RangeExpression) getRhsExpression()).getEndExpression();
                IntegerRangeTest ir = new IntegerRangeTest(getLhsExpression(), min, max);
                ExpressionTool.copyLocationInfo(this, ir);
                return ir;
            }

            // Now a fixed range...

            if (getLhsExpression() instanceof Literal) {
                GroundedValue value0 = ((Literal) getLhsExpression()).getGroundedValue();
                if (value0 instanceof IntegerRange && ((IntegerRange)value0).getStep() == 1) {
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
                GroundedValue value1 = ((Literal) getRhsExpression()).getGroundedValue();
                if (value1 instanceof IntegerRange && ((IntegerRange) value1).getStep() == 1) {
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

        if (operator != OperatorSymbol.EQUALS &&
                operator != OperatorSymbol.NE &&
                (comparisonCardinality == ComparisonCardinality.MANY_TO_MANY ||
                         comparisonCardinality == ComparisonCardinality.MANY_TO_ONE && (manyOperandIsLiftable() || manyOperandIsRangeExpression())) &&
                (NumericType.isNumericType(t0) || NumericType.isNumericType(t1))) {

            // System.err.println("** using minimax optimization **");
            ValueComparison vc;
            switch (operator) {
                case LT:
                case LE:
                    vc = new ValueComparison(makeMinOrMax(visitor, contextInfo, getLhsExpression(), "min"),
                            singletonOperator,
                            makeMinOrMax(visitor, contextInfo, getRhsExpression(), "max"));
                    vc.setResultWhenEmpty(BooleanValue.FALSE);
                    //vc.setAtomicComparer(comparer);
                    break;
                case GT:
                case GE:
                    vc = new ValueComparison(makeMinOrMax(visitor, contextInfo, getLhsExpression(), "max"),
                            singletonOperator,
                            makeMinOrMax(visitor, contextInfo, getRhsExpression(), "min"));
                    vc.setResultWhenEmpty(BooleanValue.FALSE);
                    //vc.setAtomicComparer(comparer);
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown operator " + operator);
            }

            ExpressionTool.copyLocationInfo(this, vc);
            vc.setRetainedStaticContext(getRetainedStaticContext());
            return vc.typeCheck(visitor, contextInfo);
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
                                e instanceof Literal && ((Literal) e).getGroundedValue() instanceof IntegerRange);
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
        return BooleanValue.get(makeElaborator().elaborateForBoolean().eval(context));
    }

    /**
     * Evaluate the expression in a boolean context
     *
     * @param context the given context for evaluation
     * @return a boolean representing the result of the numeric comparison of the two operands
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForBoolean().eval(context);
    }

    /**
     * Compare two atomic values
     *
     *
     * @param a0         the first value
     * @param operator   the singleton version of the comparison operator,
     *                   for example {@link net.sf.saxon.expr.parser.OperatorSymbol#FEQ}
     * @param a1         the second value
     * @param comparer   the comparer to be used to perform the comparison. If the comparer is context-sensitive
     *                   then the context must already have been bound using comparer.provideContext().
     * @param checkTypes set to true if the operand types need to be checked for comparability at runtime
     * @param context    the XPath evaluation context
     * @param staticContext retained static context, used for namespace resolver and spec version
     * @return true if the comparison succeeds
     * @throws XPathException if a dynamic error occurs during the comparison
     */

    public static boolean compare(AtomicValue a0,
                                  OperatorSymbol operator,
                                  AtomicValue a1,
                                  AtomicComparer comparer,
                                  boolean checkTypes,
                                  XPathContext context,
                                  RetainedStaticContext staticContext) throws XPathException {

        int specVersion = staticContext.getPackageData().getHostLanguageVersion();
        boolean u0 = a0.isUntypedAtomic();
        boolean u1 = a1.isUntypedAtomic();
        if (u0 != u1) {
            // one value untyped, the other not
            final ConversionRules rules = context.getConfiguration().getConversionRules();
            if (u0) {
                // a0 is untyped atomic
                if (a1 instanceof NumericValue) {
                    return UntypedNumericComparer.quickCompare(
                            (StringValue) a0, (NumericValue) a1, operator, rules, specVersion);
                } else if (a1 instanceof StringValue) {
                    // no conversion needed
                } else {
                    AtomicType prim = a1.getPrimitiveType();
                    StringConverter sc = prim.getStringConverter(rules);
                    if (a1 instanceof QualifiedNameValue) {
                        sc = (StringConverter) sc.setNamespaceResolver(staticContext);
                    }
                    a0 = sc.convertString(a0.getUnicodeStringValue()).asAtomic();
                }
            } else {
                // a1 is untyped atomic
                if (a0 instanceof NumericValue) {
                    return UntypedNumericComparer.quickCompare(
                            (StringValue) a1, (NumericValue) a0, OperatorInfo.inverse(operator), rules, specVersion);
                } else if (a0 instanceof StringValue) {
                    // no conversion needed
                } else {
                    AtomicType prim = a0.getPrimitiveType();
                    StringConverter sc = prim.getStringConverter(rules);
                    if (a0 instanceof QualifiedNameValue) {
                        sc = (StringConverter) sc.setNamespaceResolver(staticContext);
                    }
                    a1 = sc.convertString(a1.getUnicodeStringValue()).asAtomic();
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
     * @return the corresponding singleton operator, for example {@link OperatorSymbol#FLE}
     */

    public static OperatorSymbol getCorrespondingSingletonOperator(OperatorSymbol op) {
        switch (op) {
            case EQUALS:
                return OperatorSymbol.FEQ;
            case GE:
                return OperatorSymbol.FGE;
            case NE:
                return OperatorSymbol.FNE;
            case LT:
                return OperatorSymbol.FLT;
            case GT:
                return OperatorSymbol.FGT;
            case LE:
                return OperatorSymbol.FLE;
            default:
                return op;
        }
    }

    protected GeneralComparison getInverseComparison() {
        GeneralComparison20 gc2 = new GeneralComparison20(getRhsExpression(), OperatorInfo.inverse(operator), getLhsExpression());
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


    /**
     * Get the element name used to identify this expression in exported expression format
     * @return the element name used to identify this expression
     */

    @Override
    protected String tag() {
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

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new GeneralComparisonElaborator();
    }

    /**
     * Elaborator for a general comparison expression such as (A = B).
     */

    public static class GeneralComparisonElaborator extends BooleanElaborator {

        public BooleanEvaluator elaborateForBoolean() {
            final GeneralComparison exp = (GeneralComparison) getExpression();
            final ComparisonCardinality cardinality = exp.getComparisonCardinality();
            final boolean needsRunTimeCheck = exp.needsRuntimeCheck();
            final AtomicComparer comparer = exp.getAtomicComparer();
            final RetainedStaticContext staticContext = exp.getRetainedStaticContext();
            final OperatorSymbol singletonOperator = exp.getSingletonOperator();

            switch (cardinality) {
                case ONE_TO_ONE: {
                    final ItemEvaluator p0 = exp.getLhsExpression().makeElaborator().elaborateForItem();
                    final ItemEvaluator p1 = exp.getRhsExpression().makeElaborator().elaborateForItem();
                    return context -> {
                        AtomicValue av0 = (AtomicValue) p0.eval(context);
                        if (av0 == null) {
                            return false;
                        }
                        AtomicValue av1 = (AtomicValue) p1.eval(context);
                        if (av1 == null) {
                            return false;
                        }
                        return compare(av0, singletonOperator, av1, comparer.provideContext(context),
                                       needsRunTimeCheck, context, staticContext);
                    };
                }
                case MANY_TO_ONE: {
                    final PullEvaluator p0 = exp.getLhsExpression().makeElaborator().elaborateForPull();
                    final ItemEvaluator p1 = exp.getRhsExpression().makeElaborator().elaborateForItem();
                    return context -> evaluateManyToOne(p0.iterate(context),
                                                        (AtomicValue) p1.eval(context),
                                                        singletonOperator,
                                                        comparer,
                                                        needsRunTimeCheck,
                                                        staticContext,
                                                        exp.getLocation(),
                                                        context);
                }
                case MANY_TO_MANY: {
                    final PullEvaluator p0 = exp.getLhsExpression().makeElaborator().elaborateForPull();
                    final PullEvaluator p1 = exp.getRhsExpression().makeElaborator().elaborateForPull();
                    return context -> evaluateManyToMany(p0.iterate(context),
                                                         p1.iterate(context),
                                                         singletonOperator,
                                                         comparer,
                                                         needsRunTimeCheck,
                                                         staticContext,
                                                         exp.getLocation(),
                                                         context);
                }
                default:
                    throw new UnsupportedOperationException();
            }

        }

        public boolean evaluateManyToOne(SequenceIterator iter0,
                                         AtomicValue value1,
                                         OperatorSymbol singletonOperator,
                                         AtomicComparer comparer,
                                         boolean runTimeCheckNeeded,
                                         RetainedStaticContext staticContext,
                                         Location loc,
                                         XPathContext context) throws XPathException {
            try {
                if (value1 == null || (value1.isNaN() && singletonOperator != OperatorSymbol.FNE)) {
                    iter0.close();
                    return false;
                }
                if (iter0 instanceof RangeIterator) {
                    if (value1.isUntypedAtomic()) {
                        value1 = StringConverter.StringToInteger.INSTANCE.convertString(value1.getUnicodeStringValue()).asAtomic();
                    }
                    RangeIterator ri = (RangeIterator) iter0;
                    switch (singletonOperator) {
                        case FEQ:
                            return ri.containsEq((NumericValue) value1);
                        case FNE:
                            return ri.getFirst().compareTo(ri.getLast()) != 0 || ri.getFirst().compareTo(((NumericValue) value1)) != 0;
                        case FLE:
                            return ri.getMin().compareTo(((NumericValue) value1)) <= 0;
                        case FLT:
                            return ri.getMin().compareTo(((NumericValue) value1)) < 0;
                        case FGE:
                            return ri.getMax().compareTo(((NumericValue) value1)) >= 0;
                        case FGT:
                            return ri.getMax().compareTo(((NumericValue) value1)) > 0;
                        default:
                            throw new AssertionError();
                    }
                }
                AtomicValue item0;
                AtomicComparer boundComparer = comparer.provideContext(context);
                while ((item0 = (AtomicValue) iter0.next()) != null) {
                    if (compare(item0, singletonOperator, value1, boundComparer, runTimeCheckNeeded, context, staticContext)) {
                        iter0.close();
                        return true;
                    }
                }
                return false;
            } catch (XPathException e) {
                throw e.maybeWithLocation(loc).maybeWithContext(context);
            }


        }

        public boolean evaluateManyToMany(SequenceIterator iter0,
                                          SequenceIterator iter1,
                                          OperatorSymbol singletonOperator,
                                          AtomicComparer comparer,
                                          boolean runTimeCheckNeeded,
                                          RetainedStaticContext staticContext,
                                          Location loc,
                                          XPathContext context) throws XPathException {
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
                                if (compare(item0, singletonOperator, item1, boundComparer,
                                            runTimeCheckNeeded, context, staticContext)) {
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
                                if (compare(item0, singletonOperator, item1, boundComparer,
                                            runTimeCheckNeeded, context, staticContext)) {
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
                throw e.maybeWithLocation(loc).maybeWithContext(context);
            }
        }
    }
}

