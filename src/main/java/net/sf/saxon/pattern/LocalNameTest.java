////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.*;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.type.*;
import net.sf.saxon.z.IntSet;

import java.util.Map;
import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * NodeTest is an interface that enables a test of whether a node has a particular
 * name and type. A LocalNameTest matches the node type and the local name,
 * it represents an XPath 2.0 test of the form *:name.
 */

public final class LocalNameTest extends NodeTest implements QNameTest {

    private NamePool namePool;
    private int nodeKind;
    private String localName;
    private UType uType;

    public LocalNameTest(NamePool pool, int nodeKind, String localName) {
        this.namePool = pool;
        this.nodeKind = nodeKind;
        this.localName = localName;
        uType = UType.fromTypeCode(nodeKind);
    }

    /**
     * Get the node kind matched by this test
     *
     * @return the matching node kind
     */

    public int getNodeKind() {
        return nodeKind;
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return uType;
    }

    /**
     * Get the set of node names allowed by this NodeTest. Return no result, because the
     * set of names cannot be represented.
     *
     * @return the set of integer fingerprints of the node names that this node test can match; or absent
     * if the set of names cannot be represented (for example, with the name tests *:xxx or xxx:*)
     */
    @Override
    public Optional<IntSet> getRequiredNodeNames() {
        // See bug 3713
        return Optional.empty();
    }

    @Override
    public String getFullAlphaCode() {
        return getBasicAlphaCode() + " n*:" + localName;
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
    public boolean matches(int nodeKind, /*@Nullable*/ NodeName name, SchemaType annotation) {
        return name != null && nodeKind == this.nodeKind && localName.equals(name.getLocalPart());
    }

    @Override
    public IntPredicate getMatcher(final NodeVectorTree tree) {
        final byte[] nodeKindArray = tree.getNodeKindArray();
        final int[] nameCodeArray = tree.getNameCodeArray();
        if (nodeKind == Type.ELEMENT && tree instanceof TinyTree) {
             Map<String, IntSet> localNameIndex = ((TinyTree)tree).getLocalNameIndex();
             IntSet intSet = localNameIndex.get(localName);
             if (intSet == null) {
                 return i -> false;
             } else {
                 return nodeNr -> intSet.contains(nameCodeArray[nodeNr] & NamePool.FP_MASK)
                         && (nodeKindArray[nodeNr] & 0x0f) == Type.ELEMENT;
             }
        } else {
            return nodeNr -> (nodeKindArray[nodeNr] & 0x0f) == nodeKind &&
                    localName.equals(namePool.getLocalName(nameCodeArray[nodeNr] & NamePool.FP_MASK));
        }
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
        return localName.equals(node.getLocalPart()) && nodeKind == node.getNodeKind();
    }

    /**
     * Test whether this QNameTest matches a given QName
     *
     * @param qname the QName to be matched
     * @return true if the name matches, false if not
     */

    @Override
    public boolean matches(StructuredQName qname) {
        return localName.equals(qname.getLocalPart());
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    @Override
    public final double getDefaultPriority() {
        return -0.25;
    }

    /**
     * Get the local name used in this LocalNameTest
     *
     * @return the local name matched by the test
     */

    public String getLocalName() {
        return localName;
    }

    /**
     * Determine the types of nodes to which this pattern applies. Used for optimisation.
     * For patterns that match nodes of several types, return Type.NODE
     *
     * @return the type of node matched by this pattern. e.g. Type.ELEMENT or Type.TEXT
     */

    @Override
    public int getPrimitiveType() {
        return nodeKind;
    }

    public String toString() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return "*:" + localName;
            case Type.ATTRIBUTE:
                return "@*:" + localName;
            default:
                return "(*" + nodeKind + "*):" + localName; // should not be used
        }
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return nodeKind << 20 ^ localName.hashCode();
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    public boolean equals(Object other) {
        return other instanceof LocalNameTest &&
                ((LocalNameTest) other).nodeKind == nodeKind &&
                ((LocalNameTest) other).localName.equals(localName);
    }

    public NamePool getNamePool() {return namePool;}

    /**
     * Export the QNameTest as a string for use in a SEF file (typically in a catch clause).
     *
     * @return a string representation of the QNameTest, suitable for use in export files. The format is
     * a sequence of alternatives separated by vertical bars, where each alternative is one of '*',
     * '*:localname', 'Q{uri}*', or 'Q{uri}local'.
     */
    @Override
    public String exportQNameTest() {
        return "*:" + localName;
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
        return "q.local==='" + localName + "'";
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
        return Optional.of("The node has the wrong local name");
    }

}

