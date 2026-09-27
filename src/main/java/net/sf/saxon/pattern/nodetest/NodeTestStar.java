// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern.nodetest;

import net.sf.saxon.Configuration;
import net.sf.saxon.ma.jnode.AnyJNodeType;
import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.Item;
import net.sf.saxon.pattern.qname.AnyQNameTest;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.type.ChoiceItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;
import net.sf.saxon.type.gnode.NodeKindType;

import java.util.Optional;

/**
 * A node test representing the syntax {@code *} - meaning either all
 * children of a JNode, or all children of the principal node kind of the axis
 * in the case of an XNode
 */

public class NodeTestStar implements NodeTest {

    private final int defaultNodeKind;

    /**
     * Create the test
     * @param defaultNodeKind the default node kind for the axis on which the node
     *                        test appears. This is relevant only when the step
     *                        is used to select within an XTree, but we need to
     *                        keep the information "just in case".
     */
    public NodeTestStar(int defaultNodeKind) {
        this.defaultNodeKind = defaultNodeKind;
    }

    /**
     * Get the default node kind of the axis used in the containing step.
     * @return the default node kind (always element, attribute, or namespace)
     */
    public int getDefaultNodeKind() {
        return defaultNodeKind;
    }

    /**
     * Determine the default priority to use if this node-test appears as a match pattern
     * for a template with no explicit priority attribute.
     *
     * @return the default priority for the pattern
     */
    @Override
    public double getDefaultPriority() {
        return -0.5;
    }

    /**
     * Extract a QNameTest (the strongest one possible) that must be satisfied by a node
     * if it is to satisfy this NodeTest
     *
     * @return the strongest possible QNameTest
     */
    @Override
    public QNameTest getQNameTest() {
        return AnyQNameTest.getInstance();
    }

    /**
     * Convert to a test that only matches XNodes
     */

    public NodeTest asXNodeTest(Configuration config) {
        return NodeKindType.of(defaultNodeKind);
    }

    /**
     * Test whether this node test is satisfied by a given node. 
     *
     * @param node the node to be matched
     * @return true if the node test is satisfied by the supplied node, false otherwise
     */
    @Override
    public boolean test(GNode node) {
        return node instanceof JNode || node.getNodeKind() == defaultNodeKind;
    }

    /**
     * Get an item type that all matching nodes must satisfy
     *
     * @return an item type
     */
    @Override
    public ItemType getItemType() {
        return ChoiceItemType.of(AnyJNodeType.getInstance(), NodeKindType.of(defaultNodeKind));
    }

    @Override
    public String toString() {
        return "*";
    }

    /**
     * Get a UType that all matching nodes must satisfy
     *
     * @return a UType
     */
    @Override
    public UType getUType() {
        return UType.GNODE;
    }

    /**
     * Determine whether the content type (if present) is nillable
     *
     * @return true if the content test (when present) can match nodes that are nilled
     */
    @Override
    public boolean isNillable() {
        return true;
    }

    /**
     * Get extra diagnostic information about why a supplied item does not conform to this
     * item type, if available. If extra information is returned, it should be in the form of a complete
     * sentence, minus the closing full stop. No information should be returned for obvious cases.
     *
     * @param item the item that doesn't match this type
     * @param th   the type hierarchy cache
     * @return optionally, a message explaining why the item does not match the type
     */
    @Override
    public Optional<String> explainMismatch(Item item, TypeHierarchy th) {
        return Optional.empty();
    }

    /**
     * Get a concise string representation of this node test for use in diagnostics
     *
     * @return a suitably abbreviated respresention of the node test
     */
    @Override
    public String toShortString() {
        return toString();
    }
}


