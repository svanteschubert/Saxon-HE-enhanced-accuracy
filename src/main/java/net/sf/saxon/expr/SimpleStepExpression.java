////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.pattern.SelectorTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.SequenceType;


/**
 * A SimpleStepExpression is a special case of a SlashExpression in which the
 * start expression selects a single item (or nothing), and the step expression is
 * a simple AxisExpression. This is designed to avoid the costs of creating a new
 * dynamic context for expressions (common in XQuery) such as
 * for $b in EXPR return $b/title
 */

public final class SimpleStepExpression extends SlashExpression {

    public SimpleStepExpression(Expression start, Expression step) {
        super(start, step);
        if (!(step instanceof AxisExpression)) {
            throw new IllegalArgumentException();
        }
    }

    public AxisExpression getAxisExpression() {
        return (AxisExpression)getStep();
    }

    private static final OperandRole STEP_ROLE = new OperandRole(OperandRole.USES_NEW_FOCUS | OperandRole.HIGHER_ORDER, OperandUsage.TRANSMISSION, SequenceType.ANY_SEQUENCE);

    @Override
    protected OperandRole getOperandRole(int arg) {
        return arg == 0 ? OperandRole.FOCUS_CONTROLLING_SELECT : STEP_ROLE;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        getLhs().typeCheck(visitor, contextInfo);

        ItemType selectType = getStart().getItemType();
        if (selectType == ErrorType.getInstance()) {
            return Literal.makeEmptySequence();
        }

        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(selectType)
                .withContextSetter(getStart());

        getRhs().typeCheck(visitor, cit);

        if (!(getStep() instanceof AxisExpression)) {
            if (Literal.isEmptySequence(getStep())) {
                return getStep();
            }
            SlashExpression se = new SlashExpression(getStart(), getStep());
            ExpressionTool.copyLocationInfo(this, se);
            return se;
        }

        UType contextItemUType = contextInfo.getContextItemUType();
        NodeTest test = ((AxisExpression) getStep()).getNodeTest();
        if (test instanceof SelectorTest) {
            if (UType.JNODE.subsumes(contextItemUType)) {
                // TODO: ((AxisExpression) getStep()).setNodeTest(((SelectorTest) test).asJNodeTest());
            } else if (UType.XNODE.subsumes(contextItemUType)) {
                ((AxisExpression) getStep()).setNodeTest(((SelectorTest) test).asXNodeTest(getConfiguration()));
            }
        }
        if (getStart() instanceof ContextItemExpression && AxisInfo.isForwards[((AxisExpression) getStep()).getAxis()]) {
            return getStep();
        }
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be re-bound
     */
    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        Expression lhs = getStart().copy(rebindings);
        Expression rhs = getStep().copy(rebindings);
        if (!(rhs instanceof AxisExpression)) {  // bug 6698
            SlashExpression se = new SlashExpression(getStart(), getStep());
            ExpressionTool.copyLocationInfo(this, se);
            return se;
        }
        SimpleStepExpression exp = new SimpleStepExpression(lhs, rhs);
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
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
     * Evaluate the expression, returning an iterator over the result
     *
     * @param context the evaluation context
     */
    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForPull().iterate(context);
//        GNode origin;
//        try {
//            origin = (GNode) getStart().evaluateItem(context);
//        } catch (XPathException e) {
//            if (e.hasErrorCode("XPDY0002") && !e.hasBeenReported()) {
//                throw new XPathException("The context item for axis step "
//                    + toShortString() + " is absent", "XPDY0002", getLocation());
//            } else {
//                throw e;
//            }
//        }
//        if (origin == null) {
//            return EmptyIterator.getInstance();
//        }
//        return ((AxisExpression) getStep()).iterate(origin);
    }

    @Override
    public String getExpressionName() {
        return "simpleStep";
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new SimpleStepExprElaborator();
    }

    /**
     * Elaborator for a simple step expression, that is X/axis::Y where X evaluates to a singleton
     */

    public static class SimpleStepExprElaborator extends PullElaborator {

        @Override
        public PullEvaluator elaborateForPull() {
            // TODO: do we need to catch exceptions as in the iterate() method?
            final SimpleStepExpression expr = (SimpleStepExpression) getExpression();
            final ItemEvaluator select = expr.getSelectExpression().makeElaborator().elaborateForItem();
            final AxisExpression step = (AxisExpression) expr.getStep();
//            final boolean nullable = Cardinality.allowsZero(expr.getSelectExpression().getCardinality());
//            final PullEvaluator axisPull = step.getElaborator().elaborateForPull();
            int axis = step.getAxis();
            NodePredicate test = step.getNodeTest();
            switch (axis) {
                case AxisInfo.ANCESTOR:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iterateAncestorAxis(test);
                    };

                case AxisInfo.ANCESTOR_OR_SELF:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iterateAncestorOrSelfAxis(test);
                    };

                case AxisInfo.ATTRIBUTE:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iterateAttributeAxis(test);
                    };

                case AxisInfo.CHILD:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iterateChildAxis(test);
                    };

                case AxisInfo.DESCENDANT:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iterateDescendantAxis(test);
                    };

                case AxisInfo.DESCENDANT_OR_SELF:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iterateDescendantOrSelfAxis(test);
                    };

                case AxisInfo.FOLLOWING:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iterateFollowingAxis(test);
                    };

                case AxisInfo.FOLLOWING_OR_SELF:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iterateFollowingOrSelfAxis(test);
                    };

                case AxisInfo.FOLLOWING_SIBLING:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iterateFollowingSiblingAxis(test);
                    };

                case AxisInfo.FOLLOWING_SIBLING_OR_SELF:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iterateFollowingSiblingOrSelfAxis(test);
                    };

                case AxisInfo.NAMESPACE:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iterateNamespaceAxis(test);
                    };

                case AxisInfo.PARENT:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iterateParentAxis(test);
                    };

                case AxisInfo.PRECEDING:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iteratePrecedingAxis(test);
                    };

                case AxisInfo.PRECEDING_OR_SELF:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iteratePrecedingOrSelfAxis(test);
                    };

                case AxisInfo.PRECEDING_SIBLING:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iteratePrecedingSiblingAxis(test);
                    };

                case AxisInfo.PRECEDING_SIBLING_OR_SELF:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iteratePrecedingSiblingOrSelfAxis(test);
                    };

                case AxisInfo.SELF:
                    return context -> {
                        GNode origin = (GNode) select.eval(context);
                        if (origin == null) {
                            return EmptyIterator.INSTANCE;
                        }
                        return origin.iterateSelfAxis(test);
                    };

                default:
                    throw new IllegalArgumentException("Unknown axis number " + axis);

            }


//            if (nullable) {
//                return context -> {
//                    NodeInfo origin = (NodeInfo) select.eval(context);
//                    if (origin == null) {
//                        return EmptyIterator.getInstance();
//                    }
//                    return axisPull.iterate(context);
//                    return step.iterate(origin);
//                };
//            } else {
//                return context -> {
//                    GNode start = (GNode) select.eval(context);
//                    assert start != null;
//                    return step.iterate(start);
//                };
//            }
        }

    }
}

