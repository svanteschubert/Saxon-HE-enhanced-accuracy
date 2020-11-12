////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;

/**
 * This is a special pattern that matches the "anchor node". It is used for the selectors
 * that arise when evaluating XPath expressions in streaming mode; the anchor
 * node is the context node for the streamed XPath evaluation.
 *
 * Given a streamed evaluation of an expression such as ./BOOKS/BOOK/PRICE, the way we evaluate
 * this is to turn it into a pattern, which is then tested against all descendant nodes.
 * Conceptually the pattern is $A/BOOKS/BOOK/PRICE, where $A is referred to as the anchor
 * node. When we evaluate the pattern against (say) a PRICE element, the match will only succeed
 * if the name of the element is "PRICE" and its ancestors are, in order, a BOOK element, a
 * BOOKS element, and the anchor node $A.
 */
public class AnchorPattern extends Pattern {

    private static AnchorPattern THE_INSTANCE = new AnchorPattern();

    public static AnchorPattern getInstance() {
        return THE_INSTANCE;
    }

    protected AnchorPattern() {
    }

    /**
     * Get a UType indicating which kinds of items this Pattern can match.
     *
     * @return a UType indicating all the primitive types of item that the pattern can match.
     */
    @Override
    public UType getUType() {
        return UType.PARENT_NODE_KINDS;
    }

    /**
     * Type-check the pattern.
     *
     * @param visitor         the expression visitor
     * @param contextItemType the type of the context item at the point where the pattern
     *                        is defined. Set to null if it is known that the context item is undefined.
     * @return the optimised Pattern
     */

    @Override
    public Pattern typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        return this;
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
        return anchor == null || node == anchor;
    }

    /**
     * Determine whether this Pattern matches the given Node. This is the main external interface
     * for matching patterns: it sets current() to the node being tested
     *
     * @param item    The NodeInfo representing the Element or other node to be tested against the Pattern
     * @param context The dynamic context. Only relevant if the pattern
     *                uses variables, or contains calls on functions such as document() or key().
     * @return true if the node matches the Pattern, false otherwise
     */

    @Override
    public boolean matches(Item item, XPathContext context) throws XPathException {
        throw new AssertionError();
        //return true;
    }

    /**
     * Get a NodeTest that all the nodes matching this pattern must satisfy
     *
     * @return a NodeTest, as specific as possible, which all the matching nodes satisfy
     */

    @Override
    public ItemType getItemType() {
        return AnyNodeTest.getInstance();
    }

    @Override
    public String reconstruct() {
        return ".";
    }

    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("p.anchor");
        presenter.endElement();
    }

    /**
     * Copy an AnchorPattern.
     * Since there is only one, return the same.
     *
     * @param rebindings variables that need to be re-bound
     * @return the original nodeTest
     */

    /*@NotNull*/
    @Override
    public Pattern copy(RebindingMap rebindings) {
        return this;
    }
}

