////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.UType;

import java.util.function.IntPredicate;

/**
 * NodeTest is an interface that enables a test of whether a node has a particular
 * name and type. An AnyNodeTest matches any node.
 *
 * @author Michael H. Kay
 */

public final class AnyNodeTest extends NodeTest implements QNameTest {

    private static AnyNodeTest THE_INSTANCE = new AnyNodeTest();

    /**
     * Get an instance of AnyNodeTest
     * @return the singleton instance of this class
     */

    public static AnyNodeTest getInstance() {
        return THE_INSTANCE;
    }

    /**
     * Private constructor
     */

    private AnyNodeTest() {
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.ANY_NODE;
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
        return nodeKind != Type.PARENT_POINTER;
    }

    @Override
    public IntPredicate getMatcher(NodeVectorTree tree) {
        final byte[] nodeKindArray = tree.getNodeKindArray();
        return nodeNr -> nodeKindArray[nodeNr] != Type.PARENT_POINTER;
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
        return true;
    }

    /**
     * Test whether this QNameTest matches a given QName
     *
     * @param qname the QName to be matched
     * @return true if the name matches, false if not
     */

    @Override
    public boolean matches(StructuredQName qname) {
        return true;
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    @Override
    public final double getDefaultPriority() {
        return -0.5;
    }

    /*@NotNull*/
    public String toString() {
        return "node()";
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
        return "*";
    }

    /**
     * Generate Javascript code to test if a name matches the test.
     *
     * @return JS code as a string. The generated code will be used
     * as the body of a JS function in which the argument name "q" is an
     * XdmQName object holding the name. The XdmQName object has properties
     * uri and local.
     * @param targetVersion The version of Saxon-JS being targeted
     */
    @Override
    public String generateJavaScriptNameTest(int targetVersion) {
        return "true";
    }


}

