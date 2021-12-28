////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.AlphaCode;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;

/**
 * A SimplePositionalPattern is a pattern of the form A[N] where A is an axis expression using the child axis
 * and P is a numeric literal.
 */


public final class SimplePositionalPattern extends Pattern {

    private NodeTest nodeTest;
    private int position;

    /**
     * Create a SimplePositionalPattern
     *
     * @param nodeTest     the test that the node must satisfy
     * @param position     the required position of the node
     */

    public SimplePositionalPattern(NodeTest nodeTest, int position) {
        this.nodeTest = nodeTest;
        this.position = position;
    }

    /**
     * Get the required position
     *
     * @return the integer appearing as the filter predicate
     */

    public int getPosition() {
        return position;
    }

    /**
     * Get the node test
     * @return the node test used
     */
    public NodeTest getNodeTest() {
        return nodeTest;
    }
    /**
     * Determine whether the pattern matches a given item.
     *
     * @param item the item to be tested
     * @return true if the pattern matches, else false
     */

    @Override
    public boolean matches(Item item, XPathContext context) {
        return item instanceof NodeInfo && matchesBeneathAnchor((NodeInfo) item, null, context);
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
        return nodeTest.getPrimitiveItemType();
    }

    /**
     * Determine whether this pattern is the same as another pattern
     *
     * @param other the other object
     */

    public boolean equals(Object other) {
        if (other instanceof SimplePositionalPattern) {
            SimplePositionalPattern fp = (SimplePositionalPattern) other;
            return nodeTest.equals(fp.nodeTest) && position == fp.position;
        } else {
            return false;
        }
    }

    /**
     * hashcode supporting equals()
     */

    @Override
    public int computeHashCode() {
        return nodeTest.hashCode() ^ (position<<3);
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
        return false;
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
    public boolean matchesBeneathAnchor(NodeInfo node, NodeInfo anchor, XPathContext context) {
        if (!nodeTest.test(node)) {
            return false;
        }

        if (anchor != null && node.getParent() != anchor) {
            return false;
        }

        return position == Navigator.getSiblingPosition(node, nodeTest, position);

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
        SimplePositionalPattern n = new SimplePositionalPattern(nodeTest.copy(), position);
        ExpressionTool.copyLocationInfo(this, n);
        n.setOriginalText(getOriginalText());
        return n;
    }

    /**
     * Get the original pattern text
     */
    @Override
    public String reconstruct() {
        return nodeTest + "[" + position + "]";
    }

    @Override
    public void export(ExpressionPresenter presenter) {
        presenter.startElement("p.simPos");
        presenter.emitAttribute("test", AlphaCode.fromItemType(nodeTest));
        presenter.emitAttribute("pos", position + "");
        presenter.endElement();
    }

}

