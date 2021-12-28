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
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.NumericValue;

/**
 * A GeneralPositionalPattern is a pattern of the form A[P] where A is an axis expression using the child axis
 * and P is an expression that depends on the position.
 * <p>This class handles cases where the predicate P is arbitrarily complex. Simple comparisons of position() against
 * an integer value are handled by the class SimplePositionalPattern.</p>
 */
public class GeneralPositionalPattern extends Pattern {

    private NodeTest nodeTest;
    private Expression positionExpr;
    private boolean usesPosition = true;

    /**
     * Create a GeneralPositionalPattern
     *
     * @param base         the base expression (to be matched independently of position)
     * @param positionExpr the positional filter which matches only if the position of the node is correct
     */

    public GeneralPositionalPattern(NodeTest base, Expression positionExpr) {
        this.nodeTest = base;
        this.positionExpr = positionExpr;
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
        return new Operand(this, positionExpr, OperandRole.FOCUS_CONTROLLED_ACTION);
    }

    /**
     * Get the filter assocated with the pattern
     *
     * @return the filter predicate
     */

    public Expression getPositionExpr() {
        return positionExpr;
    }

    /**
     * Get the base pattern
     *
     * @return the base pattern before filtering
     */

    public NodeTest getNodeTest() {
        return nodeTest;
    }

    public void setUsesPosition(boolean usesPosition) {
        this.usesPosition = usesPosition;
    }

    /**
     * Simplify the pattern: perform any context-independent optimisations
     *
     */

    @Override
    public Pattern simplify() throws XPathException {
        positionExpr = positionExpr.simplify();
        return this;
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

        // analyze each component of the pattern
        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(getItemType(), false);

        positionExpr = positionExpr.typeCheck(visitor, cit);
        positionExpr = ExpressionTool.unsortedIfHomogeneous(positionExpr, false);


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
        ContextItemStaticInfo cit = config.makeContextItemStaticInfo(getItemType(), false);
        positionExpr = positionExpr.optimize(visitor, cit);

        if (Literal.isConstantBoolean(positionExpr, true)) {
            return new NodeTestPattern(nodeTest);
        } else if (Literal.isConstantBoolean(positionExpr, false)) {
            // if a filter is constant false, the pattern doesn't match anything
            return new NodeTestPattern(ErrorType.getInstance());
        }

        if ((positionExpr.getDependencies() & StaticProperty.DEPENDS_ON_POSITION) == 0) {
            usesPosition = false;
        }

        // See if the expression is now known to be non-positional (see bugs 1908, 1992, test mode-0011)
        if (!FilterExpression.isPositionalFilter(positionExpr, config.getTypeHierarchy())) {
            byte axis = AxisInfo.CHILD;
            if (nodeTest.getPrimitiveType() == Type.ATTRIBUTE) {
                axis = AxisInfo.ATTRIBUTE;
            } else if (nodeTest.getPrimitiveType() == Type.NAMESPACE) {
                axis = AxisInfo.NAMESPACE;
            }
            AxisExpression ae = new AxisExpression(axis, nodeTest);
            FilterExpression fe = new FilterExpression(ae, positionExpr);
            return PatternMaker.fromExpression(fe, config, true)
                .typeCheck(visitor, contextInfo);
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
        // the only dependency that's interesting is a dependency on local variables
        return positionExpr.getDependencies() &
                (StaticProperty.DEPENDS_ON_LOCAL_VARIABLES | StaticProperty.DEPENDS_ON_USER_FUNCTIONS);
    }

    /**
     * Allocate slots to any variables used within the pattern
     *
     * @param slotManager manages allocation of slots in a stack frame
     * @param nextFree    the next slot that is free to be allocated @return the next slot that is free to be allocated
     */

    @Override
    public int allocateSlots(SlotManager slotManager, int nextFree) {
        return ExpressionTool.allocateSlots(positionExpr, nextFree, slotManager);
    }

    /**
     * Determine whether the pattern matches a given item.
     *
     * @param item the item to be tested
     * @return true if the pattern matches, else false
     */

    @Override
    public boolean matches(Item item, XPathContext context) throws XPathException {
        return item instanceof NodeInfo && matchesBeneathAnchor((NodeInfo) item, null, context);
    }

    /**
     * Determine whether this pattern matches a given Node within the subtree rooted at a given
     * anchor node. This method is used when the pattern is used for streaming.
     *
     * @param node    The NodeInfo representing the Element or other node to be tested against the Pattern
     * @param anchor  The anchor node, which must match any AnchorPattern subpattern
     * @param context The dynamic context. Only relevant if the pattern
     *                uses variables, or contains calls on functions such as document() or key().
     * @return true if the node matches the Pattern, false otherwise
     */

    @Override
    public boolean matchesBeneathAnchor(NodeInfo node, NodeInfo anchor, XPathContext context) throws XPathException {
        return internalMatches(node, anchor, context);
    }

    /**
     * Test whether the pattern matches, but without changing the current() node
     */

    private boolean internalMatches(NodeInfo node, NodeInfo anchor, XPathContext context) throws XPathException {
        if (!nodeTest.test(node)) {
            return false;
        }

        XPathContext c2 = context.newMinorContext();
        ManualIterator iter = new ManualIterator(node);
        c2.setCurrentIterator(iter);

        try {
            XPathContext c = c2;
            int actualPosition = -1;
            if (usesPosition) {
                actualPosition = getActualPosition(node, Integer.MAX_VALUE, context.getCurrentIterator());
                ManualIterator man = new ManualIterator(node, actualPosition);
                XPathContext c3 = c2.newMinorContext();
                c3.setCurrentIterator(man);
                c = c3;
            }
            Item predicate = positionExpr.evaluateItem(c);
            if (predicate instanceof NumericValue) {
                NumericValue position = (NumericValue) positionExpr.evaluateItem(context);
                int requiredPos = position.asSubscript();
                if (actualPosition < 0 && requiredPos != -1) {
                    actualPosition = getActualPosition(node, requiredPos, context.getCurrentIterator());
                }
                return requiredPos != -1 && actualPosition == requiredPos;
            } else {
                return ExpressionTool.effectiveBooleanValue(predicate);
            }

        } catch (XPathException.Circularity | XPathException.StackOverflow e) {
            throw e;
        } catch (XPathException e) {
            handleDynamicError(e, c2);
            return false;
        }
    }

    private int getActualPosition(NodeInfo node, int max, FocusIterator iterator) {
        if (iterator instanceof FocusTrackingIterator) {
            // This path makes use of cached information
            return ((FocusTrackingIterator)iterator).getSiblingPosition(node, nodeTest, max);
        }
        return Navigator.getSiblingPosition(node, nodeTest, max);
    }


    /**
     * Get a UType indicating which kinds of items this Pattern can match.
     *
     * @return a UType indicating all the primitive types of item that the pattern can match.
     */
    @Override
    public UType getUType() {
        return nodeTest.getUType();
    }

    /**
     * Determine the fingerprint of nodes to which this pattern applies.
     * Used for optimisation.
     *
     * @return the fingerprint of nodes matched by this pattern.
     */

    @Override
    public int getFingerprint() {
        return nodeTest.getFingerprint();
    }

    /**
     * Get an ItemType that all the nodes matching this pattern must satisfy
     */

    @Override
    public ItemType getItemType() {
        return nodeTest;
    }

    /**
     * Determine whether this pattern is the same as another pattern
     *
     * @param other the other object
     */

    public boolean equals(Object other) {
        if (other instanceof GeneralPositionalPattern) {
            GeneralPositionalPattern fp = (GeneralPositionalPattern) other;
            return nodeTest.equals(fp.nodeTest) && positionExpr.isEqual(fp.positionExpr);
        } else {
            return false;
        }
    }

    /**
     * hashcode supporting equals()
     */

    @Override
    public int computeHashCode() {
        return nodeTest.hashCode() ^ positionExpr.hashCode();
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
        GeneralPositionalPattern n = new GeneralPositionalPattern(nodeTest.copy(), positionExpr.copy(rebindings));
        ExpressionTool.copyLocationInfo(this, n);
        n.setOriginalText(getOriginalText());
        return n;
    }

    /**
     * Get a string representation of the pattern. This will be in a form similar to the
     * original pattern text, but not necessarily identical. It is not guaranteed to be
     * in legal pattern syntax.
     */
    @Override
    public String reconstruct() {
        return nodeTest + "[" + positionExpr + "]";
    }

    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("p.genPos");
        presenter.emitAttribute("test", AlphaCode.fromItemType(nodeTest));
        if (!usesPosition) {
            // flag is this way around for backwards compatibility with 9.8
            presenter.emitAttribute("flags", "P");
        }
        positionExpr.export(presenter);
        presenter.endElement();
    }


}
// Copyright (c) 2012-2020 Saxonica Limited
