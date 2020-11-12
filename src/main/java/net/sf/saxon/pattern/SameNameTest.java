////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.*;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.UType;
import net.sf.saxon.z.IntSet;
import net.sf.saxon.z.IntSingletonSet;

import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * NodeTest is an interface that enables a test of whether a node has a particular
 * name and type. A SameNameTest matches a node that has the same node kind and name
 * as a supplied node.
 *
 * <p>Note: it's not safe to use this if the supplied node is mutable.</p>
 *
 * @author Michael H. Kay
 */

public class SameNameTest extends NodeTest implements QNameTest {

    private NodeInfo origin;
    /**
     * Create a SameNameTest to match nodes by name
     *
     * @param origin the node whose node kind and name must be matched
     * @since 9.0
     */

    public SameNameTest(NodeInfo origin) {
        this.origin = origin;
    }

    /**
     * Get the node kind that this name test matches
     *
     * @return the matching node kind
     */

    public int getNodeKind() {
        return origin.getNodeKind();
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.fromTypeCode(origin.getNodeKind());
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
        if (nodeKind != origin.getNodeKind()) {
            return false;
        }
        if (name.hasFingerprint() && origin.hasFingerprint()) {
            return name.getFingerprint() == origin.getFingerprint();
        } else {
            return name.hasURI(origin.getURI()) && name.getLocalPart().equals(origin.getLocalPart());
        }
    }

    @Override
    public IntPredicate getMatcher(final NodeVectorTree tree) {
        final byte[] nodeKindArray = tree.getNodeKindArray();
        final int[] nameCodeArray = tree.getNameCodeArray();
        return nodeNr -> {
            int k = nodeKindArray[nodeNr] & 0x0f;
            if (k == Type.WHITESPACE_TEXT) {
                k = Type.TEXT;
            }
            if (k != origin.getNodeKind()) {
                return false;
            } else if (origin.hasFingerprint()) {
                return (nameCodeArray[nodeNr] & 0xfffff) == origin.getFingerprint();
            } else {
                return Navigator.haveSameName(tree.getNode(nodeNr), origin);
            }
        };
    }

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes.
     *
     * @param node the node to be matched
     */

    @Override
    public boolean test(NodeInfo node) {
        return node == origin ||
            (node.getNodeKind() == origin.getNodeKind() && Navigator.haveSameName(node, origin));
    }

    /**
     * Test whether the NameTest matches a given QName
     *
     * @param qname the QName to be matched
     * @return true if the name matches
     */

    @Override
    public boolean matches(StructuredQName qname) {
        return NameOfNode.makeName(origin).getStructuredQName().equals(qname);
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    @Override
    public final double getDefaultPriority() {
        return 0.0;
    }

    /**
     * Get the fingerprint required
     */

    @Override
    public int getFingerprint() {
        if (origin.hasFingerprint()) {
            return origin.getFingerprint();
        } else {
            NamePool pool = origin.getConfiguration().getNamePool();
            return pool.allocateFingerprint(origin.getURI(), origin.getLocalPart());
        }
    }

    /**
     * Determine the types of nodes to which this pattern applies. Used for optimisation.
     * For patterns that match nodes of several types, return Type.NODE
     *
     * @return the type of node matched by this pattern. e.g. Type.ELEMENT or Type.TEXT
     */

    @Override
    public int getPrimitiveType() {
        return origin.getNodeKind();
    }


    /*@NotNull*/
    @Override
    public Optional<IntSet> getRequiredNodeNames() {
        return Optional.of(new IntSingletonSet(getFingerprint()));
    }

    /**
     * Get the namespace URI matched by this nametest
     *
     * @return the namespace URI (using "" for the "null namepace")
     */

    public String getNamespaceURI() {
        return origin.getURI();
    }

    /**
     * Get the local name matched by this nametest
     *
     * @return the local name
     */

    public String getLocalPart() {
        return origin.getLocalPart();
    }

    public String toString() {
        switch (origin.getNodeKind()) {
            case Type.ELEMENT:
                return "element(" + NameOfNode.makeName(origin).getStructuredQName().getEQName() + ")";
            case Type.ATTRIBUTE:
                return "attribute(" + NameOfNode.makeName(origin).getStructuredQName().getEQName() + ")";
            case Type.PROCESSING_INSTRUCTION:
                return "processing-instruction(" + origin.getLocalPart() + ')';
            case Type.NAMESPACE:
                return "namespace-node(" + origin.getLocalPart() + ')';
            case Type.COMMENT:
                return "comment()";
            case Type.DOCUMENT:
                return "document-node()";
            case Type.TEXT:
                return "text()";
            default:
                return "***";
        }
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return origin.getNodeKind() << 20 ^ origin.getURI().hashCode() ^ origin.getLocalPart().hashCode();
    }

    /**
     * Determines whether two NameTests are equal
     */

    public boolean equals(Object other) {
        return other instanceof SameNameTest &&
                test(((SameNameTest) other).origin);
    }

    /**
     * Generate an equivalent NameTest
     * @return a NameTest that matches the same node kind and name
     */

    public NameTest getEquivalentNameTest() {
        return new NameTest(origin.getNodeKind(), origin.getURI(), origin.getLocalPart(), origin.getConfiguration().getNamePool());
    }

    /**
     * Export the QNameTest as a string for use in a SEF file (typically in a catch clause).
     *
     * @return a string representation of the QNameTest, suitable for use in export files. The format is
     * a sequence of alternatives separated by vertical bars, where each alternative is one of '*',
     * '*:localname', 'Q{uri}*', or 'Q{uri}local'.
     */
    @Override
    public String exportQNameTest() {
        // Not applicable
        return "";
    }

    /**
     * Generate Javascript code to test if a name matches the test.
     *
     * @return JS code as a string. The generated code will be used
     * as the body of a JS function in which the argument name "q" is an
     * XdmQName object holding the name. The XdmQName object has properties
     * uri and local.
     * @param targetVersion the version of Saxon-JS being targeted
     */
    @Override
    public String generateJavaScriptNameTest(int targetVersion) {
        // Not applicable
        return "false";
    }

}

