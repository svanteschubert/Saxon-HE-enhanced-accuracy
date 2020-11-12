////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

import java.util.HashSet;
import java.util.Set;

/**
 * Abstract pattern formed as the union, intersection, or difference of two other patterns;
 * concrete subclasses are used for the different operators
 */

public abstract class VennPattern extends Pattern {

    protected Pattern p1, p2;

    /**
     * Constructor
     *
     * @param p1 the left-hand operand
     * @param p2 the right-hand operand
     */

    public VennPattern(Pattern p1, Pattern p2) {
        this.p1 = p1;
        this.p2 = p2;
        adoptChildExpression(p1);
        adoptChildExpression(p2);
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
        return operandList(
            new Operand(this, p1, OperandRole.SAME_FOCUS_ACTION),
            new Operand(this, p2, OperandRole.SAME_FOCUS_ACTION));
    }

    /**
     * Simplify the pattern: perform any context-independent optimisations
     *
     */

    @Override
    public Pattern simplify() throws XPathException {
        p1 = p1.simplify();
        p2 = p2.simplify();
        return this;
    }

    /**
     * Type-check the pattern.
     * This is only needed for patterns that contain variable references or function calls.
     *
     * @return the optimised Pattern
     */

    @Override
    public Pattern typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        mustBeNodePattern(p1);
        p1 = p1.typeCheck(visitor, contextItemType);
        mustBeNodePattern(p2);
        p2 = p2.typeCheck(visitor, contextItemType);
        return this;
    }

    private void mustBeNodePattern(Pattern p) throws XPathException {
        if (p instanceof NodeTestPattern) {
            ItemType it = p.getItemType();
            if (!(it instanceof NodeTest)) {
                XPathException err = new XPathException("The operands of a union, intersect, or except pattern " +
                        "must be patterns that match nodes", "XPTY0004");
                err.setIsTypeError(true);
                throw err;
            }
        }
    }

    /**
     * Replace any calls on current() by a variable reference bound to the supplied binding
     */
    @Override
    public void bindCurrent(LocalBinding binding) {
        p1.bindCurrent(binding);
        p2.bindCurrent(binding);
    }

    /**
     * Test whether a pattern is motionless, that is, whether it can be evaluated against a node
     * without repositioning the input stream. This is a necessary condition for patterns used
     * as the match pattern of a streamed template rule.
     *
     * @return true if the pattern is motionless, that is, if it can be evaluated against a streamed
     *         node without changing the position in the streamed input file
     */
    @Override
    public boolean isMotionless() {
        return p1.isMotionless() && p2.isMotionless();
    }

    /**
     * Allocate slots to any variables used within the pattern
     *
     * @param slotManager represents the stack frame on which slots are allocated
     * @param nextFree    the next slot that is free to be allocated @return the next slot that is free to be allocated
     */

    @Override
    public int allocateSlots(SlotManager slotManager, int nextFree) {
        nextFree = p1.allocateSlots(slotManager, nextFree);
        nextFree = p2.allocateSlots(slotManager, nextFree);
        return nextFree;
    }

    /**
     * Gather the component (non-Venn) patterns of this Venn pattern
     *
     * @param set the set into which the components will be added
     */

    public void gatherComponentPatterns(Set<Pattern> set) {
        if (p1 instanceof VennPattern) {
            ((VennPattern) p1).gatherComponentPatterns(set);
        } else {
            set.add(p1);
        }
        if (p2 instanceof VennPattern) {
            ((VennPattern) p2).gatherComponentPatterns(set);
        } else {
            set.add(p2);
        }
    }


    /**
     * Get the dependencies of the pattern. The only possible dependency for a pattern is
     * on local variables. This is analyzed in those patterns where local variables may appear.
     *
     * @return the dependencies, as a bit-significant mask
     */

    @Override
    public int getDependencies() {
        return p1.getDependencies() | p2.getDependencies();
    }

    /**
     * Get the LHS of the union
     *
     * @return the first operand of the union
     */

    public Pattern getLHS() {
        return p1;
    }

    /**
     * Get the RHS of the union
     *
     * @return the second operand of the union
     */

    public Pattern getRHS() {
        return p2;
    }

    /**
     * Ask whether the pattern is anchored on a call on current-group()
     *
     * @return true if calls on matchesBeneathAnchor should test with all nodes in the
     * current group as anchor nodes. If false, only the first node in a group is
     * treated as the anchor node
     */
    @Override
    public boolean matchesCurrentGroup() {
        return p1.matchesCurrentGroup() || p2.matchesCurrentGroup();
    }


    /**
     * Determine whether this pattern is the same as another pattern
     *
     * @param other the other object
     */

    public boolean equals(Object other) {
        if (other instanceof VennPattern) {
            Set<Pattern> s0 = new HashSet<>(10);
            gatherComponentPatterns(s0);
            Set<Pattern> s1 = new HashSet<>(10);
            ((VennPattern) other).gatherComponentPatterns(s1);
            return s0.equals(s1);
        } else {
            return false;
        }
    }

    /**
     * Hashcode supporting equals()
     */

    @Override
    public int computeHashCode() {
        return 0x9bd723a6 ^ p1.hashCode() ^ p2.hashCode();
    }

    /**
     * Get the relevant operator: "union", "intersect", or "except"
     * @return the operator, as a string
     */

    protected abstract String getOperatorName();

    /**
     * Get the pattern text for diagnostics
     */
    @Override
    public String reconstruct() {
        return p1 + " " + getOperatorName() + " " + p2;
    }

    @Override
    public void export(ExpressionPresenter presenter) throws XPathException{
        presenter.startElement("p.venn");
        presenter.emitAttribute("op", getOperatorName());
        p1.export(presenter);
        p2.export(presenter);
        presenter.endElement();
    }
}
