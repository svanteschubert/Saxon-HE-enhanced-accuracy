////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.instruct.CopyOf;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.DocumentSorter;
import net.sf.saxon.functions.Doc;
import net.sf.saxon.functions.DocumentFn;
import net.sf.saxon.functions.KeyFn;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;


/**
 * A slash expression is any expression using the binary slash operator "/". The parser initially generates a slash
 * expression for all occurrences of the binary "/" operator, wrapped in a documentSort() function to do the sorting
 * and de-duplication required by the semantics of path expressions. The documentSort() is subsequently stripped off
 * by the optimizer if sorting and deduplication is found to be unnecessary. The slash expression itself, therefore,
 * does not perform sorting or de-duplication.
 */

public class SlashExpression extends BinaryExpression
        implements ContextSwitchingExpression {

    boolean contextFree;

    /**
     * Constructor
     *
     * @param start The left hand operand (which must always select a sequence of nodes).
     * @param step  The step to be followed from each node in the start expression to yield a new
     *              sequence; this may return either nodes or atomic values (but not a mixture of the two)
     */

    public SlashExpression(Expression start, Expression step) {
        super(start, Token.SLASH, step);
    }

    @Override
    protected OperandRole getOperandRole(int arg) {
        return arg==0 ? OperandRole.FOCUS_CONTROLLING_SELECT : OperandRole.FOCUS_CONTROLLED_ACTION;
    }

    /**
     * Get the left-hand operand
     * @return the left-hand operand
     */

    public Expression getStart() {
        return getLhsExpression();
    }

    /**
     * Set the left-hand operand
     * @param start the left-hand operand
     */

    public void setStart(Expression start) {
        setLhsExpression(start);
    }

    /**
     * Get the right-hand operand
     * @return the right-hand operand
     */

    public Expression getStep() {
        return getRhsExpression();
    }

    /**
     * Set the right-hand operand
     * @param step the right-hand operand
     */

    public void setStep(Expression step) {
        setRhsExpression(step);
    }

    @Override
    public String getExpressionName() {
        return "pathExpression";
    }

    /**
     * Get the start expression (the left-hand operand)
     *
     * @return the first operand
     */

    @Override
    public Expression getSelectExpression() {
        return getStart();
    }

    /**
     * Get the step expression (the right-hand operand)
     *
     * @return the second operand
     */

    @Override
    public Expression getActionExpression() {
        return getStep();
    }

    /**
     * Determine the data type of the items returned by this exprssion
     *
     * @return the type of the step
     */

    /*@NotNull*/
    @Override
    public final ItemType getItemType() {
        return getStep().getItemType();
    }


    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return getStep().getStaticUType(getStart().getStaticUType(contextItemType));
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
    @Override
    public IntegerValue[] getIntegerBounds() {
        return getStep().getIntegerBounds();
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        getLhs().typeCheck(visitor, contextInfo);

        // If the first expression is known to be empty, just return empty without checking the step expression.
        // (Checking the step expression can cause spurious errors, such as "the context item is absent")

        if (Literal.isEmptySequence(getStart())) {
            return getStart();
        }

        // The first operand must be of type node()*

        Configuration config = visitor.getConfiguration();
        TypeChecker tc = config.getTypeChecker(false);
        RoleDiagnostic role0 = new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, "/", 0);
        role0.setErrorCode("XPTY0019");
        setStart(tc.staticTypeCheck(getStart(), SequenceType.NODE_SEQUENCE, role0, visitor));

        // Now check the second operand

        ItemType startType = getStart().getItemType();
        if (startType == ErrorType.getInstance()) {
            // implies the start expression will return an empty sequence, so the whole expression is void
            return Literal.makeEmptySequence();
        }

        ContextItemStaticInfo cit = config.makeContextItemStaticInfo(startType, false);
        cit.setContextSettingExpression(getStart());
        getRhs().typeCheck(visitor, cit);

        // If the expression has the form (a//descendant-or-self::node())/b, try to simplify it to
        // use the descendant axis

        Expression e2 = simplifyDescendantPath(visitor.getStaticContext());
        if (e2 != null) {
            return e2.typeCheck(visitor, contextInfo);
        }

        if (getStart() instanceof ContextItemExpression &&
                getStep().hasSpecialProperty(StaticProperty.ORDERED_NODESET)) {
            return getStep();
        }

        if (getStep() instanceof ContextItemExpression &&
                getStart().hasSpecialProperty(StaticProperty.ORDERED_NODESET)) {
            return getStart();
        }

        if (getStep() instanceof AxisExpression && ((AxisExpression) getStep()).getAxis() == AxisInfo.SELF &&
                config.getTypeHierarchy().isSubType(startType, getStep().getItemType())) {
            return getStart();
        }

        return this;
    }

    // Simplify an expression of the form a//b, where b has no positional filters.
    // This comes out of the constructor above as (a/descendent-or-self::node())/child::b,
    // but it is equivalent to a/descendant::b; and the latter is better as it
    // doesn't require sorting. Note that we can't do this until type information is available,
    // as we need to know whether any filters are positional or not.

    public SlashExpression simplifyDescendantPath(StaticContext env) {

        Expression underlyingStep = getStep();
        while (underlyingStep instanceof FilterExpression) {
            if (((FilterExpression) underlyingStep).isPositional(env.getConfiguration().getTypeHierarchy())) {
                return null;
            }
            underlyingStep = ((FilterExpression) underlyingStep).getSelectExpression();
        }

        if (!(underlyingStep instanceof AxisExpression)) {
            return null;
        }

        Expression st = getStart();

        // detect .//x as a special case; this will appear as descendant-or-self::node()/x

        if (st instanceof AxisExpression) {
            AxisExpression stax = (AxisExpression) st;
            if (stax.getAxis() != AxisInfo.DESCENDANT_OR_SELF) {
                return null;
            }
            ContextItemExpression cie = new ContextItemExpression();
            ExpressionTool.copyLocationInfo(this, cie);
            st = ExpressionTool.makePathExpression(cie, stax.copy(new RebindingMap()));
            ExpressionTool.copyLocationInfo(this, st);
        }

        if (!(st instanceof SlashExpression)) {
            return null;
        }

        SlashExpression startPath = (SlashExpression) st;
        if (!(startPath.getStep() instanceof AxisExpression)) {
            return null;
        }

        AxisExpression mid = (AxisExpression) startPath.getStep();
        if (mid.getAxis() != AxisInfo.DESCENDANT_OR_SELF) {
            return null;
        }



        NodeTest test = mid.getNodeTest();
        if (!(test == null || test instanceof AnyNodeTest)) {
            return null;
        }



        int underlyingAxis = ((AxisExpression) underlyingStep).getAxis();
        if (underlyingAxis == AxisInfo.CHILD ||
                underlyingAxis == AxisInfo.DESCENDANT ||
                underlyingAxis == AxisInfo.DESCENDANT_OR_SELF) {
            int newAxis = underlyingAxis == AxisInfo.DESCENDANT_OR_SELF ? AxisInfo.DESCENDANT_OR_SELF : AxisInfo.DESCENDANT;
            Expression newStep =
                    new AxisExpression(newAxis,
                            ((AxisExpression) underlyingStep).getNodeTest());
            ExpressionTool.copyLocationInfo(this, newStep);

            underlyingStep = getStep();
            // Add any filters to the new expression. We know they aren't
            // positional, so the order of the filters doesn't technically matter
            // (XPath section 2.3.4 explicitly allows us to change it.)
            // However, in the interests of predictable execution, hand-optimization, and
            // diagnosable error behaviour, we retain the original order.
            Stack<Expression> filters = new Stack<Expression>();
            while (underlyingStep instanceof FilterExpression) {
                filters.add(((FilterExpression) underlyingStep).getFilter());
                underlyingStep = ((FilterExpression) underlyingStep).getSelectExpression();
            }
            while (!filters.isEmpty()) {
                newStep = new FilterExpression(newStep, filters.pop());
                ExpressionTool.copyLocationInfo(getStep(), newStep);
            }

            //System.err.println("Simplified this:");
            //    display(10);
            //System.err.println("as this:");
            //    new PathExpression(startPath.start, newStep).display(10);

            Expression newPath = ExpressionTool.makePathExpression(startPath.getStart(), newStep);
            if (!(newPath instanceof SlashExpression)) {
                return null;
            }
            ExpressionTool.copyLocationInfo(this, newPath);
            return (SlashExpression) newPath;
        }

        if (underlyingAxis == AxisInfo.ATTRIBUTE) {

            // turn the expression a//@b into a/descendant-or-self::*/@b

            Expression newStep =
                    new AxisExpression(AxisInfo.DESCENDANT_OR_SELF, NodeKindTest.ELEMENT);
            ExpressionTool.copyLocationInfo(this, newStep);
            Expression e2 = ExpressionTool.makePathExpression(startPath.getStart(), newStep);
            Expression e3 = ExpressionTool.makePathExpression(e2, getStep());
            if (!(e3 instanceof SlashExpression)) {
                return null;
            }
            ExpressionTool.copyLocationInfo(this, e3);
            return (SlashExpression) e3;
        }

        return null;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        Configuration config = visitor.getConfiguration();
        TypeHierarchy th = config.getTypeHierarchy();
        Optimizer opt = visitor.obtainOptimizer();

        getLhs().optimize(visitor, contextItemType);
        if (Literal.isEmptySequence(getStart())) {
            return Literal.makeEmptySequence();
        }

        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(getStart().getItemType(), false);
        cit.setContextSettingExpression(getStart());
        getRhs().optimize(visitor, cit);

        if (Literal.isEmptySequence(getStep())) {
            return Literal.makeEmptySequence();
        }

        if (getStart() instanceof RootExpression && th.isSubType(contextItemType.getItemType(), NodeKindTest.DOCUMENT)) {
            // remove unnecessary leading "/" - helps streaming
            return getStep();
        }

        // Try to simplify descendant-or-self::node()/child::node

        Expression e2 = simplifyDescendantPath(visitor.getStaticContext());
        if (e2 != null) {
            return e2.optimize(visitor, contextItemType);
        }

        // Rewrite a/b[filter] as (a/b)[filter] to improve the chance of indexing

        Expression firstStep = getFirstStep();
        if (!(firstStep.isCallOn(Doc.class) || firstStep.isCallOn(DocumentFn.class))) {
            // Avoid the rewrite if the path starts with doc() for streaming reasons
            Expression lastStep = getLastStep();
            if (lastStep instanceof FilterExpression && !((FilterExpression) lastStep).isPositional(th)) {
                Expression leading = getLeadingSteps();
                Expression p2 = ExpressionTool.makePathExpression(leading, ((FilterExpression) lastStep).getSelectExpression());
                Expression f2 = new FilterExpression(p2, ((FilterExpression) lastStep).getFilter());
                ExpressionTool.copyLocationInfo(this, f2);
                return f2.optimize(visitor, contextItemType);
            }
        }

        if (!visitor.isOptimizeForStreaming()) {
            Expression k = opt.convertPathExpressionToKey(this, visitor);
            if (k != null) {
                return k.typeCheck(visitor, contextItemType).optimize(visitor, contextItemType);
            }
        }

        // Replace //x/y by descendant::y[parent::x] to eliminate the need for sorting
        // into document order, and to make the expression streamable

        e2 = tryToMakeSorted(visitor, contextItemType);
        if (e2 != null) {
            return e2;
        }

        // Replace $x/child::abcd by a SimpleStepExpression, to avoid the need for creating
        // a new dynamic context at run-time.

        if (getStep() instanceof AxisExpression) {
            if (!Cardinality.allowsMany(getStart().getCardinality())) {
                SimpleStepExpression sse = new SimpleStepExpression(getStart(), getStep());
                ExpressionTool.copyLocationInfo(this, sse);
                sse.setParentExpression(getParentExpression());
                return sse;
            } else {
                contextFree = true;
            }
        }

        if (getStart() instanceof RootExpression && getStep().isCallOn(KeyFn.class)) {
            // This happens after optimizations to convert filter expressions to key() calls
            SystemFunctionCall keyCall = (SystemFunctionCall)getStep();
            if (keyCall.getArity() == 3 && keyCall.getArg(2) instanceof ContextItemExpression) {
                keyCall.setArg(2, new RootExpression());
                keyCall.setParentExpression(getParentExpression());
                ExpressionTool.resetStaticProperties(keyCall);
                return keyCall;
            }
        }

        Expression k = promoteFocusIndependentSubexpressions(visitor, contextItemType);
        if (k != this) {
            return k;
        }

        if (visitor.isOptimizeForStreaming()) {
            // rewrite a/copy-of(.) as copy-of(a)
            Expression rawStep = ExpressionTool.unfilteredExpression(getStep(), true);
            if (rawStep instanceof CopyOf && ((CopyOf) rawStep).getSelect() instanceof ContextItemExpression) {
                ((CopyOf) rawStep).setSelect(getStart());
                rawStep.resetLocalStaticProperties();
                getStep().resetLocalStaticProperties();
                return getStep();
            }
        }

        return this;
    }

    /**
     * Test whether a path expression is an absolute path - that is, a path whose first step selects a
     * document node; if not, see if it can be converted to an absolute path. This is possible in cases where
     * the path expression has the form a/b/c and it is known that the context item is a document node; in this
     * case it is safe to change the path expression to /a/b/c
     *
     * @return the path expression if it is absolute; the converted path expression if it can be made absolute;
     *         or null if neither condition applies.
     */

    public SlashExpression tryToMakeAbsolute() {
        Expression first = getFirstStep();
        if (first.getItemType().getPrimitiveType() == Type.DOCUMENT) {
            return this;
        }
        if (first instanceof AxisExpression) {
            // This second test allows keys to be built. See XMark q9.
            ItemType contextItemType = ((AxisExpression) first).getContextItemType();
            if (contextItemType != null && contextItemType.getPrimitiveType() == Type.DOCUMENT) {
                RootExpression root = new RootExpression();
                ExpressionTool.copyLocationInfo(this, root);
                Expression path = ExpressionTool.makePathExpression(root, this.copy(new RebindingMap()));
                if (!(path instanceof SlashExpression)) {
                    return null;
                }
                ExpressionTool.copyLocationInfo(this, path);
                return (SlashExpression) path;
            }
        }
        if (first instanceof DocumentSorter && ((DocumentSorter) first).getBaseExpression() instanceof SlashExpression) {
            // see test case filter-001 in xqts-extra
            SlashExpression se = (SlashExpression) ((DocumentSorter) first).getBaseExpression();
            SlashExpression se2 = se.tryToMakeAbsolute();
            if (se2 != null) {
                if (se2 == se) {
                    return this;
                } else {
                    Expression rest = getRemainingSteps();
                    DocumentSorter ds = new DocumentSorter(se2);
                    return new SlashExpression(ds, rest);
                }
            }
        }
        return null;
    }

    /**
     * Return the estimated cost of evaluating an expression. This is a very crude measure based
     * on the syntactic form of the expression (we have no knowledge of data values). We take
     * the cost of evaluating a simple scalar comparison or arithmetic expression as 1 (one),
     * and we assume that a sequence has length 5. The resulting estimates may be used, for
     * example, to reorder the predicates in a filter expression so cheaper predicates are
     * evaluated first.
     * @return the cost estimate
     */
    @Override
    public double getCost() {
        int factor = Cardinality.allowsMany(getLhsExpression().getCardinality()) ? 5 : 1;
        double lh = getLhsExpression().getCost() + 1;
        double rh = getRhsExpression().getCost();
        double product = lh  + factor * rh;
        return Math.max(product, MAX_COST);
    }

    public Expression tryToMakeSorted(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        // Replace //x/y by descendant::y[parent::x] to eliminate the need for sorting
        // into document order, and to make the expression streamable

        Configuration config = visitor.getConfiguration();
        TypeHierarchy th = config.getTypeHierarchy();
        Optimizer opt = visitor.obtainOptimizer();
        Expression s1 = ExpressionTool.unfilteredExpression(getStart(), false);
        if (!(s1 instanceof AxisExpression && ((AxisExpression) s1).getAxis() == AxisInfo.DESCENDANT)) {
            return null;
        }
        Expression s2 = ExpressionTool.unfilteredExpression(getStep(), false);
        if (!(s2 instanceof AxisExpression && ((AxisExpression) s2).getAxis() == AxisInfo.CHILD)) {
            return null;
        }

        // We're in business; construct the new expression
        Expression x = getStart().copy(new RebindingMap());
        AxisExpression ax = (AxisExpression) ExpressionTool.unfilteredExpression(x, false);
        ax.setAxis(AxisInfo.PARENT);

        Expression y = getStep().copy(new RebindingMap());
        AxisExpression ay = (AxisExpression) ExpressionTool.unfilteredExpression(y, false);
        ay.setAxis(AxisInfo.DESCENDANT);

        Expression k = new FilterExpression(y, x);
        // If we're not starting at the root, ensure we go down at least one level
        if (!th.isSubType(contextItemType.getItemType(), NodeKindTest.DOCUMENT)) {
            k = new SlashExpression(new AxisExpression(AxisInfo.CHILD, NodeKindTest.ELEMENT), k);
            ExpressionTool.copyLocationInfo(this, k);
            opt.trace("Rewrote descendant::X/child::Y as child::*/descendant::Y[parent::X]", k);
        } else {
            ExpressionTool.copyLocationInfo(this, k);
            opt.trace("Rewrote descendant::X/child::Y as descendant::Y[parent::X]", k);
        }
        return k;
    }


    /**
     * If any subexpressions within the step are not dependent on the focus,
     * and if they are not "creative" expressions (expressions that can create new nodes), then
     * promote them: this causes them to be evaluated once, outside the path expression
     *
     * @param visitor         the expression visitor
     * @param contextItemType the type of the context item for evaluating the start expression
     * @return the rewritten expression, or the original expression if no rewrite was possible
     * @throws net.sf.saxon.trans.XPathException
     *          if a static error is detected
     */

    protected Expression promoteFocusIndependentSubexpressions(
            ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {

//        Optimizer opt = getConfiguration().obtainOptimizer();
//
//        PromotionOffer offer = new PromotionOffer(opt);
//        offer.action = PromotionOffer.FOCUS_INDEPENDENT;
//        offer.promoteDocumentDependent = (getStart().getSpecialProperties() & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0;
//        offer.containingExpression = this;
//
//        setStep(doPromotion(getStep(), offer));
//        ExpressionTool.resetStaticProperties(this);
//        if (offer.containingExpression != this) {
//            offer.containingExpression =
//                    offer.containingExpression.typeCheck(visitor, contextItemType).optimize(visitor, contextItemType);
//            return offer.containingExpression;
//        }
        return this;
    }

    /**
     * Replace this expression by a simpler expression that delivers the results without regard
     * to order.
     *
     * @param retainAllNodes set to true if the result must contain exactly the same nodes as the
     *                       original; set to false if the result can eliminate (or introduce) duplicates.
     * @param forStreaming  set to true if optimizing for streaming
     */
    @Override
    public Expression unordered(boolean retainAllNodes, boolean forStreaming) throws XPathException {
        setStart(getStart().unordered(retainAllNodes, forStreaming));
        setStep(getStep().unordered(retainAllNodes, forStreaming));
        return this;
    }


    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet target = getStart().addToPathMap(pathMap, pathMapNodeSet);
        return getStep().addToPathMap(pathMap, target);
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
     * Determine which aspects of the context the expression depends on. The result is
     * a bitwise-or'ed value composed from constants such as XPathContext.VARIABLES and
     * XPathContext.CURRENT_NODE
     */

//    public int computeDependencies() {
//        return getStart().getDependencies() |
//                // not all dependencies in the step matter, because the context node, etc,
//                // are not those of the outer expression
//                (getStep().getDependencies() &
//                        (StaticProperty.DEPENDS_ON_XSLT_CONTEXT |
//                                StaticProperty.DEPENDS_ON_LOCAL_VARIABLES |
//                                StaticProperty.DEPENDS_ON_USER_FUNCTIONS));
//    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        Expression exp = ExpressionTool.makePathExpression(getStart().copy(rebindings), getStep().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    @Override
    public int computeSpecialProperties() {

        int startProperties = getStart().getSpecialProperties();
        int stepProperties = getStep().getSpecialProperties();

        if ((stepProperties & StaticProperty.ALL_NODES_NEWLY_CREATED) != 0) {
            // Deem copies/snapshots to be in document order
            return StaticProperty.ORDERED_NODESET | StaticProperty.PEER_NODESET | StaticProperty.NO_NODES_NEWLY_CREATED;
        }


//        System.err.println("====" + toShortString() + "====");
//        System.err.println("START: " + StaticProperty.display(startProperties));
//        System.err.println("STEP: " + StaticProperty.display(stepProperties));

        int p = 0;
        if (!Cardinality.allowsMany(getStart().getCardinality())) {
            startProperties |= StaticProperty.ORDERED_NODESET |
                    StaticProperty.PEER_NODESET |
                    StaticProperty.SINGLE_DOCUMENT_NODESET;
        }
        if (!Cardinality.allowsMany(getStep().getCardinality())) {
            stepProperties |= StaticProperty.ORDERED_NODESET |
                    StaticProperty.PEER_NODESET |
                    StaticProperty.SINGLE_DOCUMENT_NODESET;
        }

        if ((startProperties & stepProperties & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0) {
            p |= StaticProperty.CONTEXT_DOCUMENT_NODESET;
        }
        if (((startProperties & StaticProperty.SINGLE_DOCUMENT_NODESET) != 0) &&
                ((stepProperties & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0)) {
            p |= StaticProperty.SINGLE_DOCUMENT_NODESET;
        }
        if ((startProperties & stepProperties & StaticProperty.PEER_NODESET) != 0) {
            p |= StaticProperty.PEER_NODESET;
        }
        if ((startProperties & stepProperties & StaticProperty.SUBTREE_NODESET) != 0) {
            p |= StaticProperty.SUBTREE_NODESET;
        }

        if (testNaturallySorted(startProperties, stepProperties)) {
            p |= StaticProperty.ORDERED_NODESET;
        }

        if (testNaturallyReverseSorted()) {
            p |= StaticProperty.REVERSE_DOCUMENT_ORDER;
        }

        if ((startProperties & stepProperties & StaticProperty.NO_NODES_NEWLY_CREATED) != 0) {
            p |= StaticProperty.NO_NODES_NEWLY_CREATED;
        }

        return p;
    }

    /**
     * Determine if we can guarantee that the nodes are delivered in document order.
     * This is true if the start nodes are sorted peer nodes
     * and the step is based on an Axis within the subtree rooted at each node.
     * It is also true if the start is a singleton node and the axis is sorted.
     *
     * @param startProperties the properties of the left-hand expression
     * @param stepProperties  the properties of the right-hand expression
     * @return true if the natural nested-loop evaluation strategy for the expression
     *         is known to deliver results with no duplicates and in document order, that is,
     *         if no additional sort is required
     */

    private boolean testNaturallySorted(int startProperties, int stepProperties) {

        // System.err.println("**** Testing pathExpression.isNaturallySorted()");
        // display(20);
        // System.err.println("Start is ordered node-set? " + start.isOrderedNodeSet());
        // System.err.println("Start is naturally sorted? " + start.isNaturallySorted());
        // System.err.println("Start is singleton? " + start.isSingleton());

        if ((stepProperties & StaticProperty.ORDERED_NODESET) == 0) {
            return false;
        }
        if (Cardinality.allowsMany(getStart().getCardinality())) {
            if ((startProperties & StaticProperty.ORDERED_NODESET) == 0) {
                return false;
            }
        } else {
            //if ((stepProperties & StaticProperty.ORDERED_NODESET) != 0) {
            return true;
            //}
        }

        // We know now that both the start and the step are sorted. But this does
        // not necessarily mean that the combination is sorted.

        // The result is sorted if the start is sorted and the step selects attributes
        // or namespaces

        if ((stepProperties & StaticProperty.ATTRIBUTE_NS_NODESET) != 0) {
            return true;
        }

        // The result is sorted if the step is creative (e.g. a call to copy-of())

        if ((stepProperties & StaticProperty.ALL_NODES_NEWLY_CREATED) != 0) {
            return true;
        }

        // The result is sorted if the start selects "peer nodes" (that is, a node-set in which
        // no node is an ancestor of another) and the step selects within the subtree rooted
        // at the context node

        return ((startProperties & StaticProperty.PEER_NODESET) != 0) &&
                ((stepProperties & StaticProperty.SUBTREE_NODESET) != 0);

    }

    /**
     * Determine if the path expression naturally returns nodes in reverse document order
     *
     * @return true if the natural nested-loop evaluation strategy returns nodes in reverse
     *         document order
     */

    private boolean testNaturallyReverseSorted() {

        // Some examples of path expressions that are naturally reverse sorted:
        //     ancestor::*/@x
        //     ../preceding-sibling::x
        //     $x[1]/preceding-sibling::node()

        // This information is used to do a simple reversal of the nodes
        // instead of a full sort, which is significantly cheaper, especially
        // when using tree models (such as DOM and JDOM) in which comparing
        // nodes in document order is an expensive operation.


        if (!Cardinality.allowsMany(getStart().getCardinality()) &&
                (getStep() instanceof AxisExpression)) {
            return !AxisInfo.isForwards[((AxisExpression) getStep()).getAxis()];
        }

        return !Cardinality.allowsMany(getStep().getCardinality()) &&
                (getStart() instanceof AxisExpression) &&
                !AxisInfo.isForwards[((AxisExpression) getStart()).getAxis()];

    }


    /**
     * Determine the static cardinality of the expression
     */

    @Override
    public int computeCardinality() {
        int c1 = getStart().getCardinality();
        int c2 = getStep().getCardinality();
        return Cardinality.multiply(c1, c2);
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException
     *          if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config) throws XPathException {
        Expression head = getLeadingSteps();
        Expression tail = getLastStep();
        if (head instanceof ItemChecker) {
            // No need to typecheck the context item
            ItemChecker checker = (ItemChecker) head;
            if (checker.getBaseExpression() instanceof ContextItemExpression) {
                return tail.toPattern(config);
            }
        }

        Pattern tailPattern = tail.toPattern(config);
        if (tailPattern instanceof NodeTestPattern) {
            if (tailPattern.getItemType() instanceof ErrorType) {
                return tailPattern;
            }
        } else if (tailPattern instanceof GeneralNodePattern) {
            return new GeneralNodePattern(this, (NodeTest)tailPattern.getItemType());
        }

        int axis = AxisInfo.PARENT;
        Pattern headPattern = null;
        if (head instanceof SlashExpression) {
            SlashExpression start = (SlashExpression) head;
            if (start.getActionExpression() instanceof AxisExpression) {
                AxisExpression mid = (AxisExpression) start.getActionExpression();
                if (mid.getAxis() == AxisInfo.DESCENDANT_OR_SELF &&
                        (mid.getNodeTest() == null || mid.getNodeTest() instanceof AnyNodeTest)) {
                    axis = AxisInfo.ANCESTOR;
                    headPattern = start.getSelectExpression().toPattern(config);
                }
            }
        }
        if (headPattern == null) {
            if (tail instanceof VennExpression) {
                Expression lhExpansion = new SlashExpression(
                        head.copy(new RebindingMap()), ((VennExpression)tail).getLhsExpression());
                Expression rhExpansion = new SlashExpression(
                        head.copy(new RebindingMap()), ((VennExpression) tail).getRhsExpression());
                VennExpression topExpansion = new VennExpression(
                        lhExpansion, ((VennExpression)tail).operator, rhExpansion);
                return topExpansion.toPattern(config);
            } else {
                axis = PatternMaker.getAxisForPathStep(tail);
                headPattern = head.toPattern(config);
            }
        }
        return new AncestorQualifiedPattern(tailPattern, headPattern, axis);
    }

    /**
     * A SlashExpression is context free if the right-hand argument is an AxisExpression.
     * This allows evaluation without creating a new XPathContext or FocusTrackingIterator;
     * the expression can be evaluated as a simple flat-mapping function from nodes to nodes.
     * @return true if the expression has been assessed as context-free, that is, if the
     * right-hand operand is an AxisExpression
     */

    public boolean isContextFree() {
        return contextFree;
    }

    /**
     * Mark this expression as being context free (or not). This is done during reloading from a SEF file.
     * @param free true if the expression has been assessed as context-free, that is, if the
     * right-hand operand is an AxisExpression
     */

    public void setContextFree(boolean free) {
        this.contextFree = free;
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        if (!(other instanceof SlashExpression)) {
            return false;
        }
        SlashExpression p = (SlashExpression) other;
        return getStart().isEqual(p.getStart()) && getStep().isEqual(p.getStep());
    }

    /**
     * get HashCode for comparing two expressions
     */

    @Override
    public int computeHashCode() {
        return "SlashExpression".hashCode() + getStart().hashCode() + getStep().hashCode();
    }

    /**
     * Iterate the path-expression in a given context
     *
     * @param context the evaluation context
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(final XPathContext context) throws XPathException {

        // This class delivers the result of the path expression in unsorted order,
        // without removal of duplicates. If sorting and deduplication are needed,
        // this is achieved by wrapping the path expression in a DocumentSorter

        Expression step = getStep();
        if (contextFree && step instanceof AxisExpression) {
            // see bug 4730, the step might have been rewritten since the flag was set
            return new MappingIterator(
                    getStart().iterate(context),
                    item -> ((AxisExpression) step).iterate((NodeInfo)item));
        }

        XPathContext context2 = context.newMinorContext();
        context2.trackFocus(getStart().iterate(context));
        return new ContextMappingIterator(step::iterate, context2);
    }

//    /**
//     * Mapping function, from a node returned by the start iteration, to a sequence
//     * returned by the child.
//     */
//
//    public SequenceIterator map(XPathContext context) throws XPathException {
//        return getStep().iterate(context);
//    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("slash", this);
        if (this instanceof SimpleStepExpression) {
            destination.emitAttribute("simple", "1");
        } else if (isContextFree()) {
            destination.emitAttribute("simple", "2");
        }
        getStart().export(destination);
        getStep().export(destination);
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
        return ExpressionTool.parenthesize(getStart()) + "/" + ExpressionTool.parenthesize(getStep());
    }

    @Override
    public String toShortString() {
        return ExpressionTool.parenthesizeShort(getStart()) + "/" + ExpressionTool.parenthesizeShort(getStep());
    }

    /**
     * Get the first step in this expression. A path expression A/B/C is represented as (A/B)/C, but
     * the first step is A
     *
     * @return the first step in the expression, after expanding any nested path expressions
     */

    public Expression getFirstStep() {
        if (getStart() instanceof SlashExpression) {
            return ((SlashExpression) getStart()).getFirstStep();
        } else {
            return getStart();
        }
    }


    /**
     * Get all steps after the first.
     * This is complicated by the fact that A/B/C is represented as ((A/B)/C; we are required
     * to return B/C
     *
     * @return a path expression containing all steps in this path expression other than the first,
     *         after expanding any nested path expressions
     */

    public Expression getRemainingSteps() {
        if (getStart() instanceof SlashExpression) {
            List<Expression> list = new ArrayList<Expression>(8);
            gatherSteps(list);
            Expression rem = rebuildSteps(list.subList(1, list.size()));
            ExpressionTool.copyLocationInfo(this, rem);
            return rem;
        } else {
            return getStep();
        }
    }

    /**
     * Flatten the path expression into a flat list of steps
     * @param list a list of expressions making up this path expression
     */

    private void gatherSteps(List<Expression> list) {
        if (getStart() instanceof SlashExpression) {
            ((SlashExpression)getStart()).gatherSteps(list);
        } else {
            list.add(getStart());
        }
        if (getStep() instanceof SlashExpression) {
            ((SlashExpression) getStep()).gatherSteps(list);
        } else {
            list.add(getStep());
        }
    }

    /**
     * Build a tree from a flat list of steps
     * @param list the list of steps
     * @return an Expression, generally a right-heavy binary tree
     */

    private Expression rebuildSteps(List<Expression> list) {
        if (list.size() == 1) {
            return list.get(0).copy(new RebindingMap());
        } else {
            return new SlashExpression(list.get(0).copy(new RebindingMap()), rebuildSteps(list.subList(1, list.size())));
        }
    }

    /**
     * Get the last step of the path expression
     *
     * @return the last step in the expression, after expanding any nested path expressions
     */

    public Expression getLastStep() {
        if (getStep() instanceof SlashExpression) {
            return ((SlashExpression) getStep()).getLastStep();
        } else {
            return getStep();
        }
    }

    /**
     * Get a path expression consisting of all steps except the last
     *
     * @return a path expression containing all steps in this path expression other than the last,
     *         after expanding any nested path expressions
     */

    public Expression getLeadingSteps() {
        if (getStep() instanceof SlashExpression) {
            List<Expression> list = new ArrayList<Expression>(8);
            gatherSteps(list);
            Expression rem = rebuildSteps(list.subList(0, list.size()-1));
            ExpressionTool.copyLocationInfo(this, rem);
            return rem;
        } else {
            return getStart();
        }
    }

    /**
     * Test whether a path expression is an absolute path - that is, a path whose first step selects a
     * document node
     *
     * @return true if the first step in this path expression selects a document node
     */

    public boolean isAbsolute() {
        return getFirstStep().getItemType().getPrimitiveType() == Type.DOCUMENT;
    }


    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "ForEach"; // sic
    }
}

