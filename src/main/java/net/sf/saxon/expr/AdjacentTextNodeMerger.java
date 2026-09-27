////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.expr.instruct.ValueOf;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AdjacentTextNodeMergingIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;


/**
 * This class performs the first phase of processing in "constructing simple content":
 * it takes an input sequence, eliminates empty text nodes, and combines adjacent text nodes
 * into one.
 *
 * @since 9.3
 */
public class AdjacentTextNodeMerger extends UnaryExpression {

    public AdjacentTextNodeMerger(Expression p0) {
        super(p0);
    }

    /**
     * Make an AdjacentTextNodeMerger expression with a given operand, or a simpler equivalent expression if appropriate
     *
     * @param base the operand expression
     * @return an AdjacentTextNodeMerger or equivalent expression
     */

    public static Expression makeAdjacentTextNodeMerger(Expression base) {
        if (base instanceof Literal && ((Literal) base).getGroundedValue() instanceof AtomicSequence) {
            return base;
        } else {
            return new AdjacentTextNodeMerger(base);
        }
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.SAME_FOCUS_ACTION;
    }

    @Override
    public Expression simplify() throws XPathException {
        Expression operand = getBaseExpression();
        if (operand instanceof Literal && ((Literal) operand).getGroundedValue() instanceof AtomicValue) {
            return operand;
        } else {
            return super.simplify();
        }
    }

    private static boolean canDeliverAdjacentTextNodes(Expression expr, TypeHierarchy th) {
        return th.relationship(expr.getItemType(), NodeKindType.TEXT) != Affinity.DISJOINT
                        && Cardinality.allowsMany(expr.getCardinality());
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().typeCheck(visitor, contextInfo);

        // This wrapper expression is unnecessary if the base expression cannot return text nodes,
        // or if it can return at most one item
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        if (!canDeliverAdjacentTextNodes(getBaseExpression(), th)) {
            Expression base = getBaseExpression();
            base.setParentExpression(getParentExpression());
            return base;
        }
        // In a `choose` expression, we can push the wrapper down to the action branches (whence it may disappear)
        if (getBaseExpression() instanceof Choose choose) {
            for (int i = 0; i < choose.size(); i++) {
                if (canDeliverAdjacentTextNodes(choose.getAction(i), th)) {
                    AdjacentTextNodeMerger atm2 = new AdjacentTextNodeMerger(choose.getAction(i));
                    choose.setAction(i, atm2);
                }
            }
            ExpressionTool.validateTree(choose);
            return choose;
        }
        // In a Block expression, check whether adjacent text nodes can occur (used in test strmode089)
        if (getBaseExpression() instanceof Block block) {
            Operand[] actions = block.getOperanda();
            boolean prevtext = false;
            boolean needed = false;
            boolean maybeEmpty = false;
            for (Operand o : actions) {
                Expression action = o.getChildExpression();
                boolean maybetext;
                if (action instanceof ValueOf) {
                    maybetext = true;
                    Expression content = ((ValueOf) action).getSelect();
                    if (content instanceof StringLiteral) {
                        // if it's empty, we could remove it now, but that's awkward and probably doesn't happen
                        maybeEmpty |= ((StringLiteral) content).getUnicodeString().isEmpty();
                    } else {
                        maybeEmpty = true;
                    }
                } else {
                    maybetext = action.getStaticUType(contextInfo.getContextItemUType()).overlaps(UType.TEXT);
                    maybeEmpty |= maybetext;
                }
                if (prevtext && maybetext) {
                    needed = true;
                    break; // may contain adjacent text nodes
                }
                if (maybetext && Cardinality.allowsMany(action.getCardinality())) {
                    needed = true;
                    break; // may contain adjacent text nodes
                }
                prevtext = maybetext;
            }
            if (!needed) {
                // We don't need to merge adjacent text nodes, we only need to remove empty ones.
                if (maybeEmpty) {
                    return new EmptyTextNodeRemover(block);
                } else {
                    return block;
                }
            }
        }
        return this;
    }

    /**
     * Determine the data type of the expression, if possible. The default
     * implementation for unary expressions returns the item type of the operand
     *
     * @return the item type of the items in the result sequence, insofar as this
     *         is known statically.
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getBaseExpression().getItemType();
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
        return getBaseExpression().getStaticUType(contextItemType);
    }

    @Override
    protected int computeCardinality() {
        return getBaseExpression().getCardinality() | StaticProperty.ALLOWS_ZERO;
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
        AdjacentTextNodeMerger a2 = new AdjacentTextNodeMerger(getBaseExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, a2);
        return a2;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered.
     */

    @Override
    public int getImplementationMethod() {
        return Expression.PROCESS_METHOD | Expression.ITERATE_METHOD | ITEM_FEED_METHOD | WATCH_METHOD;
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "AdjacentTextNodeMerger";
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return new AdjacentTextNodeMergingIterator(getBaseExpression().iterate(context));
    }


    @Override
    public String getExpressionName() {
        return "mergeAdj";
    }

    /**
     * Ask whether an item is a text node
     *
     * @param item the item in question
     * @return true if the item is a node of kind text
     */

    public static boolean isTextNode(Item item) {
        return item instanceof NodeInfo && ((NodeInfo) item).getNodeKind() == Type.TEXT;
    }

    /**
     * Make an elaborator for this expression
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new AdjacentTextNodeMergerElaborator();
    }

    /**
     * Elaborator for an adjacent text node merging expression - inserted into a pipeline for node construction
     */

    public static class AdjacentTextNodeMergerElaborator extends PullElaborator {

        public PullEvaluator elaborateForPull() {

            final AdjacentTextNodeMerger expr = (AdjacentTextNodeMerger) getExpression();
            final PullEvaluator baseEval = expr.getBaseExpression().makeElaborator().elaborateForPull();

            return context -> new AdjacentTextNodeMergingIterator(baseEval.iterate(context));
        }

    }
}

