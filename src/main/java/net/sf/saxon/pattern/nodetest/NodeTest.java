////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern.nodetest;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.Item;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.type.AlphaCode;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;
import net.sf.saxon.type.gnode.GNodeType;

import java.util.Optional;

/**
 * A {@code NodeTest} represents the XPath construct of the same name: specifically,
 * it represents what can appear after the (explicit or implicit) "::" in a step in
 * a path expression.
 * <p>NodeTests are also used to support XSLT pattern matching.</p>
 * <p>Node tests used in axis step overlap with node types used in ItemType syntax. In previous
 * Saxon versions, the {@code NodeTest} class served both purposes: it represented both a node
 * test used in an axis step, and a node type used in (say) an {@code as} attribute. From Saxon 13
 * these two concept are separated. The {@code NodeTest} class has become an interface,
 * and node tests that are also item types are represented by subclasses of {@link GNodeType}:
 * an abstract class that implements the {@code NodeTest} interface.</p>
 * <p>Some node tests have different semantics depending on whether they are selecting
 * within an XTree or a JTree, and in some cases this cannot be decided until evaluation
 * time.</p>
 */

public interface NodeTest extends NodePredicate {

    /**
     * Determine the default priority to use if this node-test appears as a match pattern
     * for a template with no explicit priority attribute.
     *
     * @return the default priority for the pattern
     */

    double getDefaultPriority();

    /**
     * Extract a QNameTest (the strongest one possible) that must be satisfied by a node
     * if it is to satisfy this NodeTest
     * @return the strongest possible QNameTest
     */

    QNameTest getQNameTest();

    /**
     * Get an item type that all matching nodes must satisfy
     * @return an item type
     */

    ItemType getItemType();

    /**
     * Get an XNodeTest that will match any XNode that this NodeTest matches: that is,
     * eliminate the possibility of matching a JNode.
     */

    NodeTest asXNodeTest(Configuration config);

    /**
     * Get a UType that all matching nodes must satisfy
     * @return a UType
     */

    UType getUType();

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes.
     * @param node the node to be matched
     * @return true if the node test is satisfied by the supplied node, false otherwise
     */
    
    boolean test(GNode node);

    /**
     * Determine whether the content type (if present) is nillable
     *
     * @return true if the content test (when present) can match nodes that are nilled
     */

    boolean isNillable();

    /**
     * Get extra diagnostic information about why a supplied item does not conform to this
     * item type, if available. If extra information is returned, it should be in the form of a complete
     * sentence, minus the closing full stop. No information should be returned for obvious cases.
     *
     * @param item the item that doesn't match this type
     * @param th   the type hierarchy cache
     * @return optionally, a message explaining why the item does not match the type
     */

    Optional<String> explainMismatch(Item item, TypeHierarchy th);

    /**
     * Get a concise string representation of this node test for use in diagnostics
     * @return a suitably abbreviated respresention of the node test
     */

    String toShortString();

    default String export() {
        return AlphaCode.fromItemType(getItemType());
    }


}

