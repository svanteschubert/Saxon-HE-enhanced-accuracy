////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.LocalName_1;
import net.sf.saxon.functions.PositionAndLast;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.registry.VendorFunctionSetHE;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.math.BigInteger;


/**
 * A FilterExpression contains a base expression and a filter predicate, which may be an
 * integer expression (positional filter), or a boolean expression (qualifier)
 */

public final class FilterExpression extends BinaryExpression implements ContextSwitchingExpression {

    private boolean filterIsPositional;         // true if the value of the filter might depend on
    // the context position
    private boolean filterIsSingletonBoolean;   // true if the filter expression always returns a single boolean
    private boolean filterIsIndependent;        // true if the filter expression does not
    // depend on the context item or position. (It may depend on last()).
    public boolean doneReorderingPredicates = false;
    public static final int FILTERED = 10000;

    public final static OperandRole FILTER_PREDICATE =
            new OperandRole(OperandRole.USES_NEW_FOCUS | OperandRole.HIGHER_ORDER, OperandUsage.INSPECTION, SequenceType.ANY_SEQUENCE);


    /**
     * Constructor
     *
     * @param base   The base expression to be filtered.
     * @param filter An expression defining the filter predicate
     */

    public FilterExpression(Expression base, Expression filter) {
        super(base, Token.LSQB, filter);
        base.setFiltered(true);
    }

    @Override
    protected OperandRole getOperandRole(int arg) {
        return arg == 0 ? OperandRole.SAME_FOCUS_ACTION : FILTER_PREDICATE;
    }

    public Expression getBase() {
        return getLhsExpression();
    }

    public void setBase(Expression base) {
        setLhsExpression(base);
    }

    /**
     * Get the filter expression
     *
     * @return the expression acting as the filter predicate
     */

    public Expression getFilter() {
        return getRhsExpression();
    }


    public void setFilter(Expression filter) {
        setRhsExpression(filter);
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the expression.
     */

    @Override
    public String getExpressionName() {
        return "filter";
    }

    /**
     * Get the data type of the items returned
     *
     * @return an integer representing the data type
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        // special case the expression B[. instance of x]
        if (getFilter() instanceof InstanceOfExpression &&
                ((InstanceOfExpression) getFilter()).getBaseExpression() instanceof ContextItemExpression) {
            return ((InstanceOfExpression) getFilter()).getRequiredItemType();
        }
        return getBase().getItemType();
    }


    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @param contextItemType the static type of the context item
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return getBase().getStaticUType(contextItemType);
    }


    /**
     * Get the base expression
     *
     * @return the base expression being filtered
     */

    @Override
    public Expression getSelectExpression() {
        return getBase();
    }

    /**
     * Ask if the filter is positional (used from bytecode)
     * @return true filter is positional
     */
    public boolean isFilterIsPositional() {
        return filterIsPositional;
    }

    /**
     * Get the subexpression that is evaluated in the new context
     *
     * @return the subexpression evaluated in the context set by the controlling expression
     */

    @Override
    public Expression getActionExpression() {
        return getFilter();
    }


    /**
     * Determine if the filter is positional
     *
     * @param th the Type Hierarchy (for cached access to type information)
     * @return true if the value of the filter depends on the position of the item against
     * which it is evaluated
     */

    public boolean isPositional(TypeHierarchy th) {
        return isPositionalFilter(getFilter(), th);
    }

    /**
     * Test if the filter always returns a singleton boolean.
     * <p>This information is available only after typeCheck() has been called.</p>
     *
     * @return true if the filter is a simple boolean expression
     */

    public boolean isSimpleBooleanFilter() {
        return filterIsSingletonBoolean;
    }

    /**
     * Determine whether the filter is independent of the context item and position
     * <p>This information is available only after typeCheck() has been called.</p>
     *
     * @return true if the filter is a numeric value that does not depend on the context item or position
     */

    public boolean isIndependentFilter() {
        return filterIsIndependent;
    }

    /**
     * Simplify an expression
     *
     * @throws XPathException if any failure occurs
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {

        setBase(getBase().simplify());
        setFilter(getFilter().simplify());

        // ignore the filter if the base expression is an empty sequence
        if (Literal.isEmptySequence(getBase())) {
            return getBase();
        }

        // check whether the filter is a constant true() or false()
        if (getFilter() instanceof Literal && !(((Literal) getFilter()).getValue() instanceof NumericValue)) {
            try {
                if (getFilter().effectiveBooleanValue(new EarlyEvaluationContext(getConfiguration()))) {
                    return getBase();
                } else {
                    return Literal.makeEmptySequence();
                }
            } catch (XPathException e) {
                e.maybeSetLocation(getLocation());
                throw e;
            }
        }

        // check whether the filter is [last()] (note, [position()=last()] is handled elsewhere)

        if (getFilter().isCallOn(PositionAndLast.Last.class)) {
            setFilter(new IsLastExpression(true));
            adoptChildExpression(getFilter());
        }

        return this;

    }

    /**
     * Type-check the expression
     *
     * @param visitor         the expression visitor
     * @param contextInfo return the expression after type-checking (potentially modified to add run-time
     *                    checks and/or conversions)
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Configuration config = visitor.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();
        getLhs().typeCheck(visitor, contextInfo);
        getBase().setFiltered(true);
        if (Literal.isEmptySequence(getBase())) {
            return getBase();
        }

        ContextItemStaticInfo baseItemType = config.makeContextItemStaticInfo(getSelectExpression().getItemType(), false);
        baseItemType.setContextSettingExpression(getBase());
        getRhs().typeCheck(visitor, baseItemType);

        // The filter expression usually doesn't need to be sorted

        Expression filter2 = ExpressionTool.unsortedIfHomogeneous(getFilter(), visitor.isOptimizeForStreaming());
        if (filter2 != getFilter()) {
            setFilter(filter2);
        }

        // detect head expressions (E[1]) and treat them specially

        if (Literal.isConstantOne(getFilter())) {
            Expression fie = FirstItemExpression.makeFirstItemExpression(getBase());
            ExpressionTool.copyLocationInfo(this, fie);
            return fie;
        }

        // determine whether the filter might depend on position
        filterIsPositional = isPositionalFilter(getFilter(), th);

        // determine whether the filter always evaluates to a single boolean
        filterIsSingletonBoolean =
                getFilter().getCardinality() == StaticProperty.EXACTLY_ONE &&
                        getFilter().getItemType().equals(BuiltInAtomicType.BOOLEAN);

        // determine whether the filter expression is independent of the focus

        filterIsIndependent = (getFilter().getDependencies() & StaticProperty.DEPENDS_ON_FOCUS) == 0;

        ExpressionTool.resetStaticProperties(this);
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {

        if (visitor.isOptimizeForStreaming() && getSelectExpression() instanceof GlobalVariableReference) {
            // the leading variable reference can't be a streamed node, so ignore streaming for this expression (bug 5475)
            visitor.setOptimizeForStreaming(false);
            Expression eOpt = optimize(visitor, contextItemType);
            visitor.setOptimizeForStreaming(true);
            return eOpt;
        }

        final Configuration config = visitor.getConfiguration();
        final Optimizer opt = visitor.obtainOptimizer();
        final boolean tracing = config.getBooleanProperty(Feature.TRACE_OPTIMIZER_DECISIONS);
        final TypeHierarchy th = config.getTypeHierarchy();

        getLhs().optimize(visitor, contextItemType);
        if (Literal.isEmptySequence(getSelectExpression())) {
            return getSelectExpression();
        }
        getBase().setFiltered(true);

        ContextItemStaticInfo baseItemType = config.makeContextItemStaticInfo(getSelectExpression().getItemType(), false);
        baseItemType.setContextSettingExpression(getBase());
        getRhs().optimize(visitor, baseItemType);

        // The filter expression usually doesn't need to be sorted

        Expression filter2 = ExpressionTool.unsortedIfHomogeneous(getFilter(), visitor.isOptimizeForStreaming());
        if (filter2 != getFilter()) {
            setFilter(filter2);
        }

        // Rewrite child::X[last()] as child::X[empty(following-sibling::X)] - especially useful for patterns

        if (getFilter() instanceof IsLastExpression &&
                ((IsLastExpression) getFilter()).getCondition() &&
                getBase() instanceof AxisExpression &&
                ((AxisExpression) getBase()).getAxis() == AxisInfo.CHILD) {
            NodeTest test = ((AxisExpression) getBase()).getNodeTest();
            AxisExpression fs = new AxisExpression(AxisInfo.FOLLOWING_SIBLING, test);
            setFilter(SystemFunction.makeCall("empty", getRetainedStaticContext(), fs));
            if (tracing) {
                Optimizer.trace(config, "Replaced [last()] predicate by test for following-sibling", this);
            }
        }

        // rewrite axis::*[local-name() = 'literal'] as axis::*:local (people write this a lot in XSLT 1.0)

        if (getBase() instanceof AxisExpression
                && ((AxisExpression)getBase()).getNodeTest() == NodeKindTest.ELEMENT
                && getFilter() instanceof CompareToStringConstant
                && ((CompareToStringConstant) getFilter()).getSingletonOperator() == Token.FEQ
                && ((CompareToStringConstant) getFilter()).getLhsExpression().isCallOn(LocalName_1.class)
                && ((SystemFunctionCall)((CompareToStringConstant) getFilter()).getLhsExpression()).getArg(0) instanceof ContextItemExpression) {
            AxisExpression ax2 = new AxisExpression(((AxisExpression) getBase()).getAxis(),
                                       new LocalNameTest(config.getNamePool(), Type.ELEMENT, ((CompareToStringConstant) getFilter()).getComparand()));
            ExpressionTool.copyLocationInfo(this, ax2);
            return ax2;
        }

        // if the result of evaluating the filter cannot include numeric values, then we can use
        // its effective boolean value

        ItemType filterType = getFilter().getItemType();
        if (!th.isSubType(filterType, BuiltInAtomicType.BOOLEAN)
                && th.relationship(filterType, NumericType.getInstance()) == Affinity.DISJOINT) {
            Expression f = SystemFunction.makeCall("boolean", getRetainedStaticContext(), getFilter());
            setFilter(f.optimize(visitor, baseItemType));
        }

        // the filter expression may have been reduced to a constant boolean by previous optimizations
        if (getFilter() instanceof Literal && ((Literal) getFilter()).getValue() instanceof BooleanValue) {
            if (((BooleanValue) ((Literal) getFilter()).getValue()).getBooleanValue()) {
                if (tracing) {
                    opt.trace("Redundant filter removed", getBase());
                }
                return getBase();
            } else {
                Expression result = Literal.makeEmptySequence();
                ExpressionTool.copyLocationInfo(this, result);
                if (tracing) {
                    opt.trace("Filter expression eliminated because predicate is always false", result);
                }
                return result;
            }
        }

        // determine whether the filter might depend on position
        filterIsPositional = isPositionalFilter(getFilter(), th);
        filterIsSingletonBoolean =
                getFilter().getCardinality() == StaticProperty.EXACTLY_ONE &&
                        getFilter().getItemType().equals(BuiltInAtomicType.BOOLEAN);

        // determine whether the filter is indexable
        if (!filterIsPositional && !visitor.isOptimizeForStreaming()) {
            int isIndexable = opt.isIndexableFilter(getFilter());

            // If the filter is indexable consider creating a key, or an indexed filter expression
            // (This happens in Saxon-EE only)
            if (isIndexable != 0) {
                boolean contextIsDoc = contextItemType != null && contextItemType.getItemType() != ErrorType.getInstance() &&
                        th.isSubType(contextItemType.getItemType(), NodeKindTest.DOCUMENT);
                Expression f = opt.tryIndexedFilter(this, visitor, isIndexable > 0, contextIsDoc);
                if (f != this) {
                    return f.typeCheck(visitor, contextItemType).optimize(visitor, contextItemType);
                }
            }
        }

        // if the filter is positional, try changing f[a and b] to f[a][b] to increase
        // the chances of finishing early.

        if (filterIsPositional &&
                getFilter() instanceof BooleanExpression &&
                ((BooleanExpression) getFilter()).operator == Token.AND) {
            BooleanExpression bf = (BooleanExpression) getFilter();
            if (isExplicitlyPositional(bf.getLhsExpression()) &&
                    !isExplicitlyPositional(bf.getRhsExpression())) {
                Expression p0 = forceToBoolean(bf.getLhsExpression());
                Expression p1 = forceToBoolean(bf.getRhsExpression());
                FilterExpression f1 = new FilterExpression(getBase(), p0);
                ExpressionTool.copyLocationInfo(this, f1);
                FilterExpression f2 = new FilterExpression(f1, p1);
                ExpressionTool.copyLocationInfo(this, f2);
                if (tracing) {
                    opt.trace("Composite filter replaced by nested filter expressions", f2);
                }
                return f2.optimize(visitor, contextItemType);
            }
            if (isExplicitlyPositional(bf.getRhsExpression()) &&
                    !isExplicitlyPositional(bf.getLhsExpression())) {
                Expression p0 = forceToBoolean(bf.getLhsExpression());
                Expression p1 = forceToBoolean(bf.getRhsExpression());
                FilterExpression f1 = new FilterExpression(getBase(), p1);
                ExpressionTool.copyLocationInfo(this, f1);
                FilterExpression f2 = new FilterExpression(f1, p0);
                ExpressionTool.copyLocationInfo(this, f2);
                if (tracing) {
                    opt.trace("Composite filter replaced by nested filter expressions", f2);
                }
                return f2.optimize(visitor, contextItemType);
            }
        }

        if (getFilter() instanceof IsLastExpression &&
                ((IsLastExpression) getFilter()).getCondition()) {

            if (getBase() instanceof Literal) {
                setFilter(Literal.makeLiteral(new Int64Value(((Literal) getBase()).getValue().getLength()), this));
            } else {
                return new LastItemExpression(getBase());
            }
        }

        Expression subsequence = tryToRewritePositionalFilter(visitor, tracing);
        if (subsequence != null) {
            if (tracing) {
                subsequence.setRetainedStaticContext(getRetainedStaticContext());  // Avoids errors in debug explain
                opt.trace("Rewrote Filter Expression as:", subsequence);
            }
            ExpressionTool.copyLocationInfo(this, subsequence);
            return subsequence.simplify()
                    .typeCheck(visitor, contextItemType)
                    .optimize(visitor, contextItemType);
        }

        // If there are two non-positional filters, consider changing their order based on the estimated cost
        // of evaluation, so we evaluate the cheapest predicates first

        if (!filterIsPositional && !doneReorderingPredicates && !(getParentExpression() instanceof FilterExpression)) {
            FilterExpression f2 = opt.reorderPredicates(this, visitor, contextItemType);
            if (f2 != this) {
                f2.doneReorderingPredicates = true;
                return f2;
            }
        }

        final Sequence sequence = tryEarlyEvaluation(visitor);
        if (sequence != null) {
            GroundedValue value = sequence.materialize();
            return Literal.makeLiteral(value, this);
        }

        return this;

    }

    /**
     * Return the estimated cost of evaluating an expression. This is a very crude measure based
     * on the syntactic form of the expression (we have no knowledge of data values). We take
     * the cost of evaluating a simple scalar comparison or arithmetic expression as 1 (one),
     * and we assume that a sequence has length 5. The resulting estimates may be used, for
     * example, to reorder the predicates in a filter expression so cheaper predicates are
     * evaluated first.
     * @return the estimated cost
     */
    @Override
    public double getCost() {
        return Math.max(getLhsExpression().getCost() + 5 * getRhsExpression().getCost(), MAX_COST);
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
     * unknown or not applicable.
     */
    @Override
    public IntegerValue[] getIntegerBounds() {
        return getBase().getIntegerBounds();
    }


    private Sequence tryEarlyEvaluation(ExpressionVisitor visitor) {
        // Attempt early evaluation of a filter expression if the base sequence is constant and the
        // filter depends only on the context. (This can't be done if, for example, the predicate uses
        // local variables, even variables declared within the predicate)
        try {
            if (getBase() instanceof Literal &&
                    !ExpressionTool.refersToVariableOrFunction(getFilter()) &&
                    (getFilter().getDependencies() & ~StaticProperty.DEPENDS_ON_FOCUS) == 0) {
                XPathContext context = visitor.getStaticContext().makeEarlyEvaluationContext();
                return iterate(context).materialize();
            }
        } catch (Exception e) {
            // can happen for a variety of reasons, for example the filter references a global parameter,
            // references the doc() function, uses element constructors, etc.
            return null;
        }
        return null;
    }

    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     * expression is the first operand of a path expression or filter expression
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet target = getBase().addToPathMap(pathMap, pathMapNodeSet);
        getFilter().addToPathMap(pathMap, target);
        return target;
    }

    /**
     * Construct an expression that obtains the effective boolean value of a given expression,
     * by wrapping it in a call of the boolean() function
     *
     * @param in the given expression
     * @return an expression that wraps the given expression in a call to the fn:boolean() function
     */

    private static Expression forceToBoolean(Expression in) {
        if (in.getItemType().getPrimitiveType() == StandardNames.XS_BOOLEAN) {
            return in;
        }
        return SystemFunction.makeCall("boolean", in.getRetainedStaticContext(), in);
    }

    /**
     * Attempt to rewrite a filter expression whose predicate is a test of the form
     * [position() op expr] as a call on functions such as subsequence, remove, or saxon:itemAt
     *
     * @param visitor the current expression visitor
     * @return the rewritten expression if a rewrite was possible, or null otherwise
     * @throws XPathException if an error occurs
     */

    private Expression tryToRewritePositionalFilter(ExpressionVisitor visitor, boolean tracing) throws XPathException {
        Configuration config = visitor.getConfiguration();
        TypeHierarchy th = config.getTypeHierarchy();
        if (getFilter() instanceof Literal) {
            GroundedValue val = ((Literal) getFilter()).getValue();
            if (val instanceof NumericValue) {
                Expression result;
                int lvalue = ((NumericValue)val).asSubscript();
                if (lvalue != -1) {
                    if (lvalue == 1) {
                        result = FirstItemExpression.makeFirstItemExpression(getBase());
                    } else {
                        result = new SubscriptExpression(getBase(), getFilter());
                    }
                } else {
                    result = Literal.makeEmptySequence();
                }
                if (tracing) {
                    Optimizer.trace(config, "Rewriting numeric filter expression with constant subscript", result);
                }
                return result;
            } else {
                Expression result = ExpressionTool.effectiveBooleanValue(val.iterate()) ? getBase() : Literal.makeEmptySequence();
                if (tracing) {
                    Optimizer.trace(config, "Rewriting boolean filter expression with constant subscript", result);
                }
                return result;
            }
        }
        if (NumericType.isNumericType(getFilter().getItemType()) &&
                !Cardinality.allowsMany(getFilter().getCardinality()) &&
                (getFilter().getDependencies() & StaticProperty.DEPENDS_ON_FOCUS) == 0) {
            Expression result = new SubscriptExpression(getBase(), getFilter());
            if (tracing) {
                Optimizer.trace(config, "Rewriting numeric filter expression with focus-independent subscript", result);
            }
            return result;
        }
        if (getFilter() instanceof ComparisonExpression) {
            //VendorFunctionLibrary lib = getConfiguration().getVendorFunctionLibrary();
            Expression lhs = ((ComparisonExpression) getFilter()).getLhsExpression();
            Expression rhs = ((ComparisonExpression) getFilter()).getRhsExpression();
            int operator = ((ComparisonExpression) getFilter()).getSingletonOperator();
            Expression comparand;
            if (lhs.isCallOn(PositionAndLast.Position.class)
                    && NumericType.isNumericType(rhs.getItemType())) {
                comparand = rhs;
            } else if (rhs.isCallOn(PositionAndLast.Position.class)
                    && NumericType.isNumericType(lhs.getItemType())) {
                comparand = lhs;
                operator = Token.inverse(operator);
            } else {
                return null;
            }

            if (ExpressionTool.dependsOnFocus(comparand)) {
                return null;
            }

            int card = comparand.getCardinality();
            if (Cardinality.allowsMany(card)) {
                return null;
            }

            // If the comparand might be an empty sequence, do the base rewrite and then wrap the
            // rewritten expression EXP in "let $n := comparand if exists($n) then EXP else ()
            if (Cardinality.allowsZero(card)) {
                LetExpression let = new LetExpression();
                let.setRequiredType(SequenceType.makeSequenceType(comparand.getItemType(), card));
                let.setVariableQName(new StructuredQName("pp", NamespaceConstant.SAXON, "pp" + let.hashCode()));
                let.setSequence(comparand);
                comparand = new LocalVariableReference(let);
                LocalVariableReference existsArg = new LocalVariableReference(let);
                Expression exists = SystemFunction.makeCall("exists", getRetainedStaticContext(), existsArg);
                Expression rewrite = tryToRewritePositionalFilterSupport(getBase(), comparand, operator, th);
                if (rewrite == null) {
                    return this;
                }
                Expression choice = Choose.makeConditional(exists, rewrite);
                let.setAction(choice);
                return let;
            } else {
                return tryToRewritePositionalFilterSupport(getBase(), comparand, operator, th);
            }
        } else if (getFilter() instanceof IntegerRangeTest) {
            // rewrite SEQ[position() = N to M]
            // => let $n := N return subsequence(SEQ, $n, (M - ($n - 1))
            // (precise form is optimized for the case where $n is a literal, especially N = 1)
            Expression val = ((IntegerRangeTest) getFilter()).getValue();
            if (!val.isCallOn(PositionAndLast.class)) {
                return null;
            }
            Expression min = ((IntegerRangeTest) getFilter()).getMin();
            Expression max = ((IntegerRangeTest) getFilter()).getMax();

            if (ExpressionTool.dependsOnFocus(min)) {
                return null;
            }
            if (ExpressionTool.dependsOnFocus(max)) {
                if (max.isCallOn(PositionAndLast.Last.class)) {
                    Expression result = SystemFunction.makeCall("subsequence", getRetainedStaticContext(), getBase(), min);
                    if (tracing) {
                        Optimizer.trace(config, "Rewriting numeric range filter expression using subsequence()", result);
                    }
                    return result;
                } else {
                    return null;
                }
            }

            LetExpression let = new LetExpression();
            let.setRequiredType(SequenceType.SINGLE_INTEGER);
            let.setVariableQName(new StructuredQName("nn", NamespaceConstant.SAXON, "nn" + let.hashCode()));
            let.setSequence(min);
            min = new LocalVariableReference(let);
            LocalVariableReference min2 = new LocalVariableReference(let);
            Expression minMinusOne = new ArithmeticExpression(
                    min2, Token.MINUS, Literal.makeLiteral(Int64Value.makeIntegerValue(1), this));
            Expression length = new ArithmeticExpression(max, Token.MINUS, minMinusOne);
            Expression subs = SystemFunction.makeCall("subsequence", getRetainedStaticContext(), getBase(), min, length);
            let.setAction(subs);
            if (tracing) {
                Optimizer.trace(config, "Rewriting numeric range filter expression using subsequence()", subs);
            }
            return let;

        } else {
            return null;
        }
    }

    private static Expression tryToRewritePositionalFilterSupport(
            Expression start, Expression comparand, int operator,
            TypeHierarchy th)
            throws XPathException {
        if (th.isSubType(comparand.getItemType(), BuiltInAtomicType.INTEGER)) {
            switch (operator) {
                case Token.FEQ: {
                    if (Literal.isConstantOne(comparand)) {
                        return FirstItemExpression.makeFirstItemExpression(start);
                    } else if (comparand instanceof Literal && ((IntegerValue) ((Literal) comparand).getValue()).asBigInteger().compareTo(BigInteger.ZERO) <= 0) {
                        return Literal.makeEmptySequence();
                    } else {
                        return new SubscriptExpression(start, comparand);
                    }
                }
                case Token.FLT: {

                    Expression[] args = new Expression[3];
                    args[0] = start;
                    args[1] = Literal.makeLiteral(Int64Value.makeIntegerValue(1), start);
                    if (Literal.isAtomic(comparand)) {
                        long n = ((NumericValue) ((Literal) comparand).getValue()).longValue();
                        args[2] = Literal.makeLiteral(Int64Value.makeIntegerValue(n - 1), start);
                    } else {
                        ArithmeticExpression decrement = new ArithmeticExpression(
                                comparand, Token.MINUS, Literal.makeLiteral(Int64Value.makeIntegerValue(1), start));
                        decrement.setCalculator(Calculator.getCalculator(      // bug 2704
                                                                               StandardNames.XS_INTEGER, StandardNames.XS_INTEGER, Calculator.MINUS, true));
                        args[2] = decrement;
                    }
                    return SystemFunction.makeCall("subsequence", start.getRetainedStaticContext(), args);
                }
                case Token.FLE: {
                    Expression[] args = new Expression[3];
                    args[0] = start;
                    args[1] = Literal.makeLiteral(Int64Value.makeIntegerValue(1), start);
                    args[2] = comparand;
                    return SystemFunction.makeCall("subsequence", start.getRetainedStaticContext(), args);
                }
                case Token.FNE: {
                    return SystemFunction.makeCall("remove", start.getRetainedStaticContext(), start, comparand);
                }
                case Token.FGT: {
                    Expression[] args = new Expression[2];
                    args[0] = start;
                    if (Literal.isAtomic(comparand)) {
                        long n = ((NumericValue) ((Literal) comparand).getValue()).longValue();
                        args[1] = Literal.makeLiteral(Int64Value.makeIntegerValue(n + 1), start);
                    } else {
                        args[1] = new ArithmeticExpression(
                                comparand, Token.PLUS, Literal.makeLiteral(Int64Value.makeIntegerValue(1), start));
                    }
                    return SystemFunction.makeCall("subsequence", start.getRetainedStaticContext(), args);
                }
                case Token.FGE: {
                    return SystemFunction.makeCall("subsequence", start.getRetainedStaticContext(), start, comparand);
                }
                default:
                    throw new IllegalArgumentException("operator");
            }

        } else {
            // the comparand is not known statically to be an integer
            switch (operator) {
                case Token.FEQ: {
                    return new SubscriptExpression(start, comparand);
                }
                case Token.FLT: {
                    // rewrite SEQ[position() lt V] as
                    // let $N := V return subsequence(SEQ, 1, if (is-whole-number($N)) then $N-1 else floor($N)))
                    LetExpression let = new LetExpression();
                    let.setRequiredType(SequenceType.makeSequenceType(
                            comparand.getItemType(), StaticProperty.ALLOWS_ONE));
                    let.setVariableQName(new StructuredQName("pp", NamespaceConstant.SAXON, "pp" + let.hashCode()));
                    let.setSequence(comparand);
                    LocalVariableReference isWholeArg = new LocalVariableReference(let);
                    LocalVariableReference arithArg = new LocalVariableReference(let);
                    LocalVariableReference floorArg = new LocalVariableReference(let);
                    Expression isWhole = VendorFunctionSetHE.getInstance().makeFunction("is-whole-number", 1).makeFunctionCall(isWholeArg);
                    Expression minusOne = new ArithmeticExpression(
                            arithArg, Token.MINUS, Literal.makeLiteral(Int64Value.makeIntegerValue(1), start));
                    Expression floor = SystemFunction.makeCall("floor", start.getRetainedStaticContext(), floorArg);
                    Expression choice = Choose.makeConditional(isWhole, minusOne, floor);
                    Expression subs = SystemFunction.makeCall(
                            "subsequence", start.getRetainedStaticContext(), start, Literal.makeLiteral(Int64Value.makeIntegerValue(1), start), choice);
                    let.setAction(subs);
                    //decl.fixupReferences(let);
                    return let;
                }
                case Token.FLE: {
                    Expression floor = SystemFunction.makeCall("floor", start.getRetainedStaticContext(), comparand);
                    return SystemFunction.makeCall(
                            "subsequence", start.getRetainedStaticContext(), start, Literal.makeLiteral(Int64Value.makeIntegerValue(1), start), floor);
                }
                case Token.FNE: {
                    // rewrite SEQ[position() ne V] as
                    // let $N := V return remove(SEQ, if (is-whole-number($N)) then xs:integer($N) else 0)
                    LetExpression let = new LetExpression();
                    ExpressionTool.copyLocationInfo(start, let);
                    let.setRequiredType(SequenceType.makeSequenceType(
                            comparand.getItemType(), StaticProperty.ALLOWS_ONE));
                    let.setVariableQName(new StructuredQName("pp", NamespaceConstant.SAXON, "pp" + let.hashCode()));
                    let.setSequence(comparand);
                    LocalVariableReference isWholeArg = new LocalVariableReference(let);
                    LocalVariableReference castArg = new LocalVariableReference(let);
                    Expression isWhole = VendorFunctionSetHE.getInstance().makeFunction("is-whole-number", 1).makeFunctionCall(isWholeArg);
                    ExpressionTool.copyLocationInfo(start, isWhole);
                    Expression cast = new CastExpression(castArg, BuiltInAtomicType.INTEGER, false);
                    ExpressionTool.copyLocationInfo(start, cast);
                    Expression choice = Choose.makeConditional(
                            isWhole, cast, Literal.makeLiteral(Int64Value.makeIntegerValue(0), start));
                    Expression rem = SystemFunction.makeCall("remove", start.getRetainedStaticContext(), start, choice);
                    let.setAction(rem);
                    return let;
                }
                case Token.FGT: {
                    // rewrite SEQ[position() gt V] as
                    // let $N := V return subsequence(SEQ, if (is-whole-number($N)) then $N+1 else ceiling($N)))
                    LetExpression let = new LetExpression();
                    let.setRequiredType(SequenceType.makeSequenceType(
                            comparand.getItemType(), StaticProperty.ALLOWS_ONE));
                    let.setVariableQName(new StructuredQName("pp", NamespaceConstant.SAXON, "pp" + let.hashCode()));
                    let.setSequence(comparand);
                    LocalVariableReference isWholeArg = new LocalVariableReference(let);
                    LocalVariableReference arithArg = new LocalVariableReference(let);
                    LocalVariableReference ceilingArg = new LocalVariableReference(let);
                    Expression isWhole = VendorFunctionSetHE.getInstance().makeFunction("is-whole-number", 1).makeFunctionCall(isWholeArg);
                    Expression plusOne = new ArithmeticExpression(
                            arithArg, Token.PLUS, Literal.makeLiteral(Int64Value.makeIntegerValue(1), start));
                    Expression ceiling = SystemFunction.makeCall("ceiling", start.getRetainedStaticContext(), ceilingArg);
                    Expression choice = Choose.makeConditional(isWhole, plusOne, ceiling);
                    Expression subs = SystemFunction.makeCall("subsequence", start.getRetainedStaticContext(), start, choice);
                    let.setAction(subs);
                    return let;
                }
                case Token.FGE: {
                    // rewrite SEQ[position() ge V] => subsequence(SEQ, ceiling(V))
                    Expression ceiling = SystemFunction.makeCall("ceiling", start.getRetainedStaticContext(), comparand);
                    return SystemFunction.makeCall("subsequence", start.getRetainedStaticContext(), start, ceiling);
                }
                default:
                    throw new IllegalArgumentException("operator");
            }
        }
    }

    /**
     * Replace this expression by a simpler expression that delivers the results without regard
     * to order.
     *
     * @param retainAllNodes set to true if the result must contain exactly the same nodes as the
     *                       original; set to false if the result can eliminate (or introduce) duplicates.
     * @param forStreaming   set to true if optimizing for streaming
     */
    @Override
    public Expression unordered(boolean retainAllNodes, boolean forStreaming) throws XPathException {
        if (!filterIsPositional) {
            setBase(getBase().unordered(retainAllNodes, forStreaming));
        }
        return this;
    }

    /**
     * Rearrange a filter expression so that predicates that are independent of a given
     * set of range variables come first, allowing them to be promoted along with the base
     * expression
     *
     * @param bindings the given set of range variables
     * @param opt      the Optimizer
     * @param th       the type hierarchy cache
     * @return the expression after promoting independent predicates
     */

    private FilterExpression promoteIndependentPredicates(Binding[] bindings, Optimizer opt, TypeHierarchy th) {
        if (!ExpressionTool.dependsOnVariable(getBase(), bindings)) {
            return this;
        }
        if (isPositional(th)) {
            return this;
        }
        if (getBase() instanceof FilterExpression) {
            FilterExpression fe = (FilterExpression) getBase();
            if (fe.isPositional(th)) {
                return this;
            }
            if (!ExpressionTool.dependsOnVariable(fe.getFilter(), bindings)) {
                return this;
            }
            if (!ExpressionTool.dependsOnVariable(getFilter(), bindings)) {
                FilterExpression result = new FilterExpression(
                        new FilterExpression(fe.getBase(), getFilter()).promoteIndependentPredicates(bindings, opt, th),
                        fe.getFilter());
                opt.trace("Reordered filter predicates:", result);
                return result;
            }
        }
        return this;
    }

    /**
     * Determine whether an expression, when used as a filter, is potentially positional;
     * that is, where it either contains a call on position() or last(), or where it is capable of returning
     * a numeric result.
     *
     * @param exp the expression to be examined
     * @param th  the type hierarchy cache
     * @return true if the expression depends on position() or last() explicitly or implicitly
     */

    public static boolean isPositionalFilter(Expression exp, TypeHierarchy th) {
        ItemType type = exp.getItemType();
        if (type.equals(BuiltInAtomicType.BOOLEAN)) {
            // common case, get it out of the way quickly
            return isExplicitlyPositional(exp);
        }
        return type.equals(BuiltInAtomicType.ANY_ATOMIC) ||
                type instanceof AnyItemType ||
                type.equals(BuiltInAtomicType.INTEGER) ||
                type.equals(NumericType.getInstance()) ||
                NumericType.isNumericType(type) ||
                isExplicitlyPositional(exp);
    }

    /**
     * Determine whether an expression, when used as a filter, has an explicit dependency on position() or last()
     *
     * @param exp the expression being tested
     * @return true if the expression is explicitly positional, that is, if it contains an explicit call on
     * position() or last()
     */

    private static boolean isExplicitlyPositional(Expression exp) {
        return (exp.getDependencies() & (StaticProperty.DEPENDS_ON_POSITION | StaticProperty.DEPENDS_ON_LAST)) != 0;
    }


    /**
     * Get the static cardinality of this expression
     *
     * @return the cardinality. The method attempts to determine the case where the
     * filter predicate is guaranteed to select at most one item from the sequence being filtered
     */

    @Override
    public int computeCardinality() {
        if (getFilter() instanceof Literal && ((Literal) getFilter()).getValue() instanceof NumericValue) {
            if (((NumericValue) ((Literal) getFilter()).getValue()).compareTo(1) == 0 &&
                    !Cardinality.allowsZero(getBase().getCardinality())) {
                return StaticProperty.ALLOWS_ONE;
            } else {
                return StaticProperty.ALLOWS_ZERO_OR_ONE;
            }
        }
        if (filterIsIndependent) {
            ItemType filterType = getFilter().getItemType().getPrimitiveItemType();
            if (filterType == BuiltInAtomicType.INTEGER || filterType == BuiltInAtomicType.DOUBLE ||
                    filterType == BuiltInAtomicType.DECIMAL || filterType == BuiltInAtomicType.FLOAT) {
                return StaticProperty.ALLOWS_ZERO_OR_ONE;
            }
            if (getFilter() instanceof ArithmeticExpression) {
                return StaticProperty.ALLOWS_ZERO_OR_ONE;
            }
        }
        if (getFilter() instanceof IsLastExpression && ((IsLastExpression) getFilter()).getCondition()) {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        }
        if (!Cardinality.allowsMany(getBase().getCardinality())) {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        }

        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-significant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return the static properties of the expression, as a bit-significant value
     */

    @Override
    public int computeSpecialProperties() {
        return getBase().getSpecialProperties();
    }

    /**
     * Is this expression the same as another expression?
     *
     * @param other the expression to be compared with this one
     * @return true if the two expressions are statically equivalent
     */

    public boolean equals(Object other) {
        if (other instanceof FilterExpression) {
            FilterExpression f = (FilterExpression) other;
            return getBase().isEqual(f.getBase()) &&
                    getFilter().isEqual(f.getFilter());
        }
        return false;
    }

    /**
     * get HashCode for comparing two expressions
     *
     * @return the hash code
     */

    @Override
    public int computeHashCode() {
        return "FilterExpression".hashCode() + getBase().hashCode() + getFilter().hashCode();
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config) throws XPathException {
        Expression base = getSelectExpression();
        Expression filter = getFilter();
        TypeHierarchy th = config.getTypeHierarchy();
        Pattern basePattern = base.toPattern(config);
        if (!isPositional(th)) {
            return new BasePatternWithPredicate(basePattern, filter);
        } else if (basePattern instanceof NodeTestPattern &&
                basePattern.getItemType() instanceof NodeTest &&
                filterIsPositional &&
                base instanceof AxisExpression &&
                ((AxisExpression) base).getAxis() == AxisInfo.CHILD &&
                (filter.getDependencies() & StaticProperty.DEPENDS_ON_LAST) == 0) {
            if (filter instanceof Literal && ((Literal) filter).getValue() instanceof IntegerValue) {
                return new SimplePositionalPattern((NodeTest) basePattern.getItemType(), (int) ((IntegerValue) ((Literal) filter).getValue()).longValue());
            } else {
                return new GeneralPositionalPattern((NodeTest) basePattern.getItemType(), filter);
            }
        }
        if (base.getItemType() instanceof NodeTest) {
            return new GeneralNodePattern(this, (NodeTest) base.getItemType());
        } else {
            throw new XPathException("The filtered expression in an XSLT 2.0 pattern must be a simple step");
        }
    }

    /**
     * Iterate over the results, returning them in the correct order
     *
     * @param context the dynamic context for the evaluation
     * @return an iterator over the expression results
     * @throws XPathException if any dynamic error occurs
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {

        // Fast path where the filter value is independent of the focus

        if (filterIsIndependent) {
            try {
                SequenceIterator it = getFilter().iterate(context);
                Item first = it.next();
                if (first == null) {
                    return EmptyIterator.emptyIterator();
                }
                if (first instanceof NumericValue) {
                    if (it.next() != null) {
                        ExpressionTool.ebvError("sequence of two or more items starting with a numeric value", getFilter());
                    } else {
                        // Filter is a constant number
                        int pos = ((NumericValue)first).asSubscript();
                        if (pos != -1) {
                            if (getBase() instanceof VariableReference) {
                                Sequence baseVal = ((VariableReference) getBase()).evaluateVariable(context);
                                if (baseVal instanceof MemoClosure) {
                                    Item m = ((MemoClosure) baseVal).itemAt(pos - 1);
                                    return m == null ? EmptyIterator.emptyIterator() : m.iterate();
                                } else {
                                    Item m = baseVal.materialize().itemAt(pos - 1);
                                    return m == null ? EmptyIterator.emptyIterator() : m.iterate();
                                }
                            } else if (getBase() instanceof Literal) {
                                Item i = ((Literal) getBase()).getValue().itemAt(pos - 1);
                                return i == null ? EmptyIterator.emptyIterator() : i.iterate();
                            } else {
                                SequenceIterator baseIter = getBase().iterate(context);
                                return SubsequenceIterator.make(baseIter, pos, pos);
                            }
                        }
                        // a non-integer value or non-positive number will never be equal to position()
                        return EmptyIterator.emptyIterator();
                    }
                } else {
                    // Filter is focus-independent, but not numeric: need to use the effective boolean value
                    boolean ebv = false;
                    if (first instanceof NodeInfo) {
                        ebv = true;
                    } else if (first instanceof BooleanValue) {
                        ebv = ((BooleanValue) first).getBooleanValue();
                        if (it.next() != null) {
                            ExpressionTool.ebvError("sequence of two or more items starting with a boolean value", getFilter());
                        }
                    } else if (first instanceof StringValue) {
                        ebv = !((StringValue) first).isZeroLength();
                        if (it.next() != null) {
                            ExpressionTool.ebvError("sequence of two or more items starting with a boolean value", getFilter());
                        }
                    } else {
                        ExpressionTool.ebvError("sequence starting with an atomic value other than a boolean, number, or string", getFilter());
                    }
                    if (ebv) {
                        return getBase().iterate(context);
                    } else {
                        return EmptyIterator.emptyIterator();
                    }
                }
            } catch (XPathException e) {
                e.maybeSetLocation(getLocation());
                throw e;
            }
        }

        // get an iterator over the base nodes

        SequenceIterator baseIter = getBase().iterate(context);

        // quick exit for an empty sequence

        if (baseIter instanceof EmptyIterator) {
            return baseIter;
        }

        if (filterIsPositional && !filterIsSingletonBoolean) {
            return new FilterIterator(baseIter, getFilter(), context);
        } else {
            return new FilterIterator.NonNumeric(baseIter, getFilter(), context);
        }

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
    public Expression copy(RebindingMap rebindings) {
        FilterExpression fe = new FilterExpression(getBase().copy(rebindings), getFilter().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, fe);
        fe.filterIsIndependent = filterIsIndependent;
        fe.filterIsPositional = filterIsPositional;
        fe.filterIsSingletonBoolean = filterIsSingletonBoolean;
        return fe;
    }


    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "FilterExpression";
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */

    public String toString() {
        return ExpressionTool.parenthesize(getBase()) + "[" + getFilter() + "]";
    }

    @Override
    public String toShortString() {
        return getBase().toShortString() + "[" + getFilter().toShortString() + "]";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the ExpressionPresenter to be used
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("filter", this);
        String flags = "";
        if (filterIsIndependent) {
            flags += "i";
        }
        if (filterIsPositional) {
            flags += "p";
        }
        if (filterIsSingletonBoolean) {
            flags += "b";
        }
        out.emitAttribute("flags", flags);
        getBase().export(out);
        getFilter().export(out);
        out.endElement();
    }

    public void setFlags(String flags) {
        filterIsIndependent = flags.contains("i");
        filterIsPositional = flags.contains("p");
        filterIsSingletonBoolean = flags.contains("b");
    }


}

