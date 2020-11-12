////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.SingletonIterator;

/**
 * This expression is equivalent to (A intersect B) in the case where A has cardinality
 * zero-or-one. This is handled as a special case because the standard sort-merge algorithm
 * involves an unnecessary sort on B.
 */
public class SingletonIntersectExpression extends VennExpression {

    /**
     * Special case of an intersect expression where the first argument is a singleton
     *
     * @param p1 the first argument, always a singleton
     * @param op the operator, always Token.INTERSECT
     * @param p2 the second argument
     */

    public SingletonIntersectExpression(final Expression p1, final int op, final Expression p2) {
        super(p1, op, p2);
    }

    /**
     * Simplify the expression
     *
     */
    @Override
    public Expression simplify() throws XPathException {
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
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during this phase
     *          (typically a type error)
     */
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        return this;
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
        SingletonIntersectExpression exp = new SingletonIntersectExpression(getLhsExpression().copy(rebindings), operator, getRhsExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    /**
     * Iterate over the value of the expression. The result will always be sorted in document order,
     * with duplicates eliminated
     *
     * @param c The context for evaluation
     * @return a SequenceIterator representing the union of the two operands
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext c) throws XPathException {
        NodeInfo m = (NodeInfo) getLhsExpression().evaluateItem(c);
        if (m == null) {
            return EmptyIterator.getInstance();
        }
        SequenceIterator iter = getRhsExpression().iterate(c);
        NodeInfo n;
        while ((n = (NodeInfo) iter.next()) != null) {
            if (n.equals(m)) {
                return SingletonIterator.makeIterator(m);
            }
        }
        return EmptyIterator.getInstance();
    }

    /**
     * Get the effective boolean value. In the case of a union expression, this
     * is reduced to an OR expression, for efficiency
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext c) throws XPathException {
        NodeInfo m = (NodeInfo) getLhsExpression().evaluateItem(c);
        return m != null && containsNode(getRhsExpression().iterate(c), m);
    }

    /**
     * Ask whether the sequence supplied in the first argument contains the node
     * supplied in the second
     * @param iter an iterator over nodes. The iterator will be closed if the node is found.
     * @param m the node to be tested
     * @return true if (and only if) the sequence contains the node
     * @throws XPathException if evaluating the iterator fails
     */

    public static boolean containsNode(SequenceIterator iter, NodeInfo m) throws XPathException {
        NodeInfo n;
        while ((n = (NodeInfo) iter.next()) != null) {
            if (n.equals(m)) {
                iter.close();
                return true;
            }
        }
        return false;
    }

    @Override
    public String getExpressionName() {
        return "singleton-intersect";
    }

    /**
     * Display the operator used by this binary expression
     *
     * @return String representation of the operator (for diagnostic display only)
     */

    @Override
    protected String displayOperator() {
        return "among";
    }

    /**
     * Get the element name used to identify this expression in exported expression format
     *
     * @return the element name used to identify this expression
     */
    @Override
    protected String tag() {
        return "among";
    }
}

