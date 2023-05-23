////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.Current;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.type.*;

/**
 * A GeneralNodePattern represents a pattern which, because of the presence of positional
 * predicates or otherwise, can only be evaluated "the hard way", by evaluating the equivalent
 * expression with successive ancestors of the tested node as context item.
 */

public final class GeneralNodePattern extends Pattern {

    private Expression equivalentExpr = null;
    private NodeTest itemType = null;
    private Expression topNodeEquivalent = null;

    /**
     * Create a GeneralNodePattern
     *
     * @param expr     the "equivalent expression"
     * @param itemType a type that all matched nodes must satisfy
     */

    public GeneralNodePattern(Expression expr, NodeTest itemType) {
        equivalentExpr = expr;
        this.itemType = itemType;
        makeTopNodeEquivalent();
    }

    /**
     * A pattern whose first step uses the child axis is treated as using the child-or-top axis,
     * which selects a node that is either (a) a child, or (b) a parentless element, comment,
     * text node, or PI. We handle this by creating a variant of the equivalent expression that
     * uses the self axis instead of child, and using this variant if matching a tree that satisfies
     * the other conditions.
     */
    private void makeTopNodeEquivalent() {
        if (equivalentExpr instanceof SlashExpression) {
            Expression head = ((SlashExpression) equivalentExpr).getFirstStep();
            if (ExpressionTool.getAxisNavigation(head) == AxisInfo.CHILD) {
                SlashExpression copy = (SlashExpression) equivalentExpr.copy(new RebindingMap());
                Expression copyHead = copy.getFirstStep();
                while (true) {
                    if (copyHead instanceof FilterExpression) {
                        copyHead = ((FilterExpression) copyHead).getBase();
                    } else if (copyHead instanceof SingleItemFilter) {
                        copyHead = ((SingleItemFilter) copyHead).getBaseExpression();
                    } else {
                        break;
                    }
                }
                if (copyHead instanceof AxisExpression) {
                    ((AxisExpression) copyHead).setAxis(AxisInfo.SELF);
                    topNodeEquivalent = copy;
                }
            }
        }
    }


    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * works off the results of iterateSubExpressions()
     * <p>If the expression is a Callable, then it is required that the order of the operands
     * returned by this function is the same as the order of arguments supplied to the corresponding
     * call() method.</p>
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterable<Operand> operands() {
        return new Operand(this, equivalentExpr, OperandRole.SAME_FOCUS_ACTION);
    }

    /**
     * Test whether a pattern is motionless, that is, whether it can be evaluated against a node
     * without repositioning the input stream. This is a necessary condition for patterns used
     * as the match pattern of a streamed template rule.
     *
     * @return true if the pattern is motionless, that is, if it can be evaluated against a streamed
     * node without changing the position in the streamed input file
     */

    @Override
    public boolean isMotionless() {
        return false;
    }

    /**
     * Type-check the pattern, performing any type-dependent optimizations.
     *
     * @param visitor         an expression visitor
     * @param contextItemType the type of the context item at the point where the pattern appears
     * @return the optimised Pattern
     */

    @Override
    public Pattern typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        ContextItemStaticInfo cit = new ContextItemStaticInfo(AnyNodeTest.getInstance(), false);
        equivalentExpr = equivalentExpr.typeCheck(visitor, cit);
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions. This is the third and final
     * phase of static optimization.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor     an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                    The parameter is set to null if it is known statically that the context item will be undefined.
     *                    If the type of the context item is not known statically, the argument is set to
     *                    {@link Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */
    @Override
    public Pattern optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Configuration config = visitor.getConfiguration();
        ContextItemStaticInfo defaultInfo = config.getDefaultContextItemStaticInfo();
        equivalentExpr = equivalentExpr.optimize(visitor, defaultInfo);
        // See if the expression is now known to be non-positional
        if (equivalentExpr instanceof FilterExpression && !((FilterExpression) equivalentExpr).isFilterIsPositional()) {
            try {
                return PatternMaker.fromExpression(equivalentExpr, config, true)
                    .typeCheck(visitor, defaultInfo);
            } catch (XPathException err) {
                // cannot make pattern from expression - not a problem, just use the original
            }
        }
        return this;
    }

    /**
     * Get the dependencies of the pattern. The only interesting dependencies for a pattern are
     * dependencies on local variables or on user-defined functions. These are analyzed in those
     * patterns containing predicates.
     *
     * @return the dependencies, as a bit-significant mask
     */

    @Override
    public int getDependencies() {
        return equivalentExpr.getDependencies() &
                (StaticProperty.DEPENDS_ON_LOCAL_VARIABLES | StaticProperty.DEPENDS_ON_USER_FUNCTIONS);
    }

    /**
     * Replace any calls on current() by a variable reference bound to the supplied binding
     */
    @Override
    public void bindCurrent(LocalBinding binding) {
        if (ExpressionTool.callsFunction(equivalentExpr, Current.FN_CURRENT, false)) {
            if (equivalentExpr.isCallOn(Current.class)) {
                equivalentExpr = new LocalVariableReference(binding);
            } else {
                replaceCurrent(equivalentExpr, binding);
            }
        }
    }

    /**
     * Allocate slots to any variables used within the pattern
     *
     * @param slotManager details of the stack frame
     * @param nextFree    the next slot that is free to be allocated @return the next slot that is free to be allocated
     */

    @Override
    public int allocateSlots(SlotManager slotManager, int nextFree) {
        return ExpressionTool.allocateSlots(equivalentExpr, nextFree, slotManager);
    }

    /**
     * Determine whether the pattern matches a given item.
     *
     * @param item the item to be tested
     * @return true if the pattern matches, else false
     */

    @Override
    public boolean matches(Item item, XPathContext context) throws XPathException {
        TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
        if (!itemType.matches(item, th)) {
            return false;
        }
        AxisIterator anc = ((NodeInfo) item).iterateAxis(AxisInfo.ANCESTOR_OR_SELF);
        NodeInfo top = (NodeInfo) item;
        while (true) {
            NodeInfo a = anc.next();
            if (a == null) {
                // The first step in a pattern, if it uses the child axis, is interpreted as "child-or-top" (test case match-274)
                if (topNodeEquivalent != null && UType.CHILD_NODE_KINDS.matches(top)) {
                    return isSelected(((NodeInfo) item), top, topNodeEquivalent, context);
                }
                return false;
            }
            if (matchesBeneathAnchor((NodeInfo) item, a, context)) {
                return true;
            }
            top = a;
        }
    }

    /**
     * Determine whether this pattern matches a given Node within the subtree rooted at a given
     * anchor node. This method is used when the pattern is used for streaming.
     *
     * @param node    The NodeInfo representing the Element or other node to be tested against the Pattern
     * @param anchor  The anchor node, which if present must match any AnchorPattern subpattern; may be null
     * @param context The dynamic context. Only relevant if the pattern
     *                uses variables, or contains calls on functions such as document() or key().
     * @return true if the node matches the Pattern, false otherwise
     */

    @Override
    public boolean matchesBeneathAnchor(NodeInfo node, NodeInfo anchor, XPathContext context) throws XPathException {
        if (!itemType.test(node)) {
            return false;
        }

        // for a positional pattern, we do it the hard way: test whether the
        // node is a member of the nodeset obtained by evaluating the
        // equivalent expression

        if (anchor == null) {
            AxisIterator ancestors = node.iterateAxis(AxisInfo.ANCESTOR_OR_SELF);
            while (true) {
                NodeInfo ancestor = ancestors.next();
                if (ancestor == null) {
                    return false;
                }
                if (matchesBeneathAnchor(node, ancestor, context)) {
                    return true;
                }
            }
        }

        return isSelected(node, anchor, equivalentExpr, context);
    }

    private boolean isSelected(NodeInfo node, NodeInfo anchor, Expression selector, XPathContext context) throws XPathException {
        // System.err.println("Testing positional pattern against node " + node.generateId());
        XPathContext c2 = context.newMinorContext();
        ManualIterator iter = new ManualIterator(anchor);
        c2.setCurrentIterator(iter);
        try {
            SequenceIterator nsv = selector.iterate(c2);
            while (true) {
                NodeInfo n = (NodeInfo) nsv.next();
                if (n == null) {
                    return false;
                }
                if (n.equals(node)) {
                    return true;
                }
            }
        } catch (XPathException.Circularity | XPathException.StackOverflow e) {
            throw e;
        } catch (XPathException e) {
            handleDynamicError(e, c2);
            return false;
        }
    }


    /**
     * Get a UType indicating which kinds of items this Pattern can match.
     *
     * @return a UType indicating all the primitive types of item that the pattern can match.
     */
    @Override
    public UType getUType() {
        return itemType.getUType();
    }

    /**
     * Determine the fingerprint of nodes to which this pattern applies.
     * Used for optimisation.
     *
     * @return the fingerprint of nodes matched by this pattern.
     */

    @Override
    public int getFingerprint() {
        return itemType.getFingerprint();
    }

    /**
     * Get a NodeTest that all the nodes matching this pattern must satisfy
     */

    @Override
    public ItemType getItemType() {
        return itemType;
    }

    public Expression getEquivalentExpr() {
        return equivalentExpr;
    }
    /**
     * Determine whether this pattern is the same as another pattern
     *
     * @param other the other object
     */

    public boolean equals(Object other) {
        if (other instanceof GeneralNodePattern) {
            GeneralNodePattern lpp = (GeneralNodePattern) other;
            return equivalentExpr.isEqual(lpp.equivalentExpr);
        } else {
            return false;
        }
    }

    /**
     * hashcode supporting equals()
     */

    @Override
    public int computeHashCode() {
        return 83641 ^ equivalentExpr.hashCode();
    }

    /**
     * Copy a pattern. This makes a deep copy.
     *
     * @return the copy of the original pattern
     * @param rebindings variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Pattern copy(RebindingMap rebindings) {
        GeneralNodePattern n = new GeneralNodePattern(equivalentExpr.copy(rebindings), itemType);
        ExpressionTool.copyLocationInfo(this, n);
        n.setOriginalText(getOriginalText());
        return n;
    }

    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("p.genNode");
        presenter.emitAttribute("test", AlphaCode.fromItemType(itemType));
        equivalentExpr.export(presenter);
        presenter.endElement();
    }

}

