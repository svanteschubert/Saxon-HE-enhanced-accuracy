////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.Err;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.*;

import java.util.Optional;

/**
 * A DocumentNodeTest implements the test document-node(element(~,~))
 */

// This is messy because the standard interface for a NodeTest does not allow
// any navigation from the node in question - it only tests for the node kind,
// node name, and type annotation of the node.

public class DocumentNodeTest extends NodeTest {


    private NodeTest elementTest;

    public DocumentNodeTest(NodeTest elementTest) {
        this.elementTest = elementTest;
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.DOCUMENT;
    }

    /**
     * Test whether this node test is satisfied by a given node. This method is only
     * fully supported for a subset of NodeTests, because it doesn't provide all the information
     * needed to evaluate all node tests. In particular (a) it can't be used to evaluate a node
     * test of the form element(N,T) or schema-element(E) where it is necessary to know whether the
     * node is nilled, and (b) it can't be used to evaluate a node test of the form
     * document-node(element(X)). This in practice means that it is used (a) to evaluate the
     * simple node tests found in the XPath 1.0 subset used in XML Schema, and (b) to evaluate
     * node tests where the node kind is known to be an attribute.
     *
     * @param nodeKind   The kind of node to be matched
     * @param name       identifies the expanded name of the node to be matched.
     *                   The value should be null for a node with no name.
     * @param annotation The actual content type of the node
     */
    @Override
    public boolean matches(int nodeKind, NodeName name, SchemaType annotation) {
        if (nodeKind != Type.DOCUMENT) {
            return false;
        }
        throw new UnsupportedOperationException("DocumentNodeTest doesn't support this method");
    }

    /**
     * Determine whether this Pattern matches the given Node.
     *
     * @param node The NodeInfo representing the Element or other node to be tested against the Pattern
     *             uses variables, or contains calls on functions such as document() or key().
     * @return true if the node matches the Pattern, false otherwise
     */

    @Override
    public boolean test(NodeInfo node) {
        if (node.getNodeKind() != Type.DOCUMENT) {
            return false;
        }
        AxisIterator iter = node.iterateAxis(AxisInfo.CHILD);
        // The match is true if there is exactly one element node child, no text node
        // children, and the element node matches the element test.
        boolean found = false;
        NodeInfo n;
        while ((n = iter.next()) != null) {
            int kind = n.getNodeKind();
            if (kind == Type.TEXT) {
                return false;
            } else if (kind == Type.ELEMENT) {
                if (found) {
                    return false;
                }
                if (elementTest.test(n)) {
                    found = true;
                } else {
                    return false;
                }
            }
        }
        return found;
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    @Override
    public final double getDefaultPriority() {
        return elementTest.getDefaultPriority();
    }

    /**
     * Determine the types of nodes to which this pattern applies. Used for optimisation.
     *
     * @return the type of node matched by this pattern. e.g. Type.ELEMENT or Type.TEXT
     */

    @Override
    public int getPrimitiveType() {
        return Type.DOCUMENT;
    }

    /**
     * Get the element test contained within this document test
     *
     * @return the contained element test
     */

    public NodeTest getElementTest() {
        return elementTest;
    }

    public String toString() {
        return "document-node(" + elementTest + ')';
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return elementTest.hashCode() ^ 12345;
    }

    public boolean equals(/*@NotNull*/ Object other) {
        return other instanceof DocumentNodeTest &&
                ((DocumentNodeTest) other).elementTest.equals(elementTest);
    }

    @Override
    public String getFullAlphaCode() {
        return getBasicAlphaCode() + " e[" + elementTest.getFullAlphaCode() + "]";
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
        Optional<String> explanation = super.explainMismatch(item, th);
        if (explanation.isPresent()) {
            return explanation;
        }
        NodeInfo node = (NodeInfo) item;
        AxisIterator iter = node.iterateAxis(AxisInfo.CHILD);
        // The match is true if there is exactly one element node child, no text node
        // children, and the element node matches the element test.
        boolean found = false;
        NodeInfo n;
        while ((n = iter.next()) != null) {
            int kind = n.getNodeKind();
            if (kind == Type.TEXT) {
                return Optional.of("The supplied document node has text node children");
            } else if (kind == Type.ELEMENT) {
                if (found) {
                    return Optional.of("The supplied document node has more than one element child");
                }
                if (elementTest.test(n)) {
                    found = true;
                } else {
                    String s = "The supplied document node has an element child (" + Err.depict(n) +
                            ") that does not satisfy the element test";
                    Optional<String> more = elementTest.explainMismatch(n, th);
                    if (more.isPresent()) {
                        s += ". " + more.get();
                    }
                    return Optional.of(s);
                }
            }
        }
        return Optional.empty();
    }

}

