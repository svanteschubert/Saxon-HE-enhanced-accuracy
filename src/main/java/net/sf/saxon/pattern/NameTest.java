////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.type.*;
import net.sf.saxon.z.IntSet;
import net.sf.saxon.z.IntSingletonSet;

import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * NodeTest is an interface that enables a test of whether a node has a particular
 * name and type. A NameTest matches the node kind and the namespace URI and the local
 * name. Note that unlike the XPath production called NameTest, this is a test for a specific
 * name, and does not include wildcard matches.
 *
 * @author Michael H. Kay
 */

public class NameTest extends NodeTest implements QNameTest {

    private int nodeKind;
    private int fingerprint;
    private UType uType;
    private NamePool namePool;
    /*@Nullable*/ private String uri = null;  // the URI corresponding to the fingerprint - computed lazily
    /*@Nullable*/ private String localName = null; //the local name corresponding to the fingerprint - computed lazily

    /**
     * Create a NameTest to match nodes by name
     *
     * @param nodeKind  the kind of node, for example {@link Type#ELEMENT}
     * @param uri       the namespace URI of the required nodes. Supply "" to match nodes that are in
     *                  no namespace
     * @param localName the local name of the required nodes. Supply "" to match unnamed nodes
     * @param namePool  the namePool holding the name codes
     * @since 9.0
     */

    public NameTest(int nodeKind, String uri, String localName, NamePool namePool) {
        this.uri = uri;
        this.localName = localName;
        this.nodeKind = nodeKind;
        this.fingerprint = namePool.allocateFingerprint(uri, localName) & NamePool.FP_MASK;
        this.namePool = namePool;
        this.uType = UType.fromTypeCode(nodeKind);
    }

    /**
     * Create a NameTest to match nodes by their nameCode allocated from the NamePool
     *
     * @param nodeKind the kind of node, for example {@link Type#ELEMENT}
     * @param nameCode the nameCode representing the name of the node
     * @param namePool the namePool holding the name codes
     * @since 8.4
     */

    public NameTest(int nodeKind, int nameCode, NamePool namePool) {
        this.nodeKind = nodeKind;
        this.fingerprint = nameCode & NamePool.FP_MASK;
        this.namePool = namePool;
        this.uType = UType.fromTypeCode(nodeKind);
    }

    /**
     * Create a NameTest to match nodes by name
     *
     * @param nodeKind the kind of node, for example {@link Type#ELEMENT}
     * @param name     the name of the nodes that this NameTest will match
     * @param pool     the namePool holding the name codes
     * @since 9.4
     */

    public NameTest(int nodeKind, NodeName name, NamePool pool) {
        this.uri = name.getURI();
        this.localName = name.getLocalPart();
        this.nodeKind = nodeKind;
        this.fingerprint = name.obtainFingerprint(pool);
        this.namePool = pool;
        this.uType = UType.fromTypeCode(nodeKind);
    }

    /**
     * Get the NamePool associated with this NameTest
     *
     * @return the NamePool
     */

    public NamePool getNamePool() {
        return namePool;
    }

    /**
     * Get the node kind that this name test matches
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
        if (nodeKind != this.nodeKind) {
            return false;
        }
        if (name.hasFingerprint()) {
            return name.getFingerprint() == this.fingerprint;
        } else {
            computeUriAndLocal();
            return name.hasURI(uri) && name.getLocalPart().equals(localName);
        }
    }

    @Override
    public IntPredicate getMatcher(final NodeVectorTree tree) {
        final byte[] nodeKindArray = tree.getNodeKindArray();
        final int[] nameCodeArray = tree.getNameCodeArray();
        return nodeNr -> (nameCodeArray[nodeNr] & 0xfffff) == fingerprint &&
                (nodeKindArray[nodeNr] & 0x0f) == nodeKind;
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
        if (node.getNodeKind() != nodeKind) {
            return false;
        }

        // Two different algorithms are used for name matching. If the fingerprint of the node is readily
        // available, we use it to do an integer comparison. Otherwise, we do string comparisons on the URI
        // and local name. In practice, Saxon's native node implementations use fingerprint matching, while
        // DOM and JDOM nodes use string comparison of names

        if (node.hasFingerprint()) {
            return node.getFingerprint() == fingerprint;
        } else {
            computeUriAndLocal();
            return localName.equals(node.getLocalPart()) && uri.equals(node.getURI());
        }
    }

    private void computeUriAndLocal() {
        if (uri == null || localName == null) {
            StructuredQName name = namePool.getUnprefixedQName(fingerprint);
            uri = name.getURI();
            localName = name.getLocalPart();
        }
    }

    /**
     * Test whether the NameTest matches a given QName
     *
     * @param qname the QName to be matched
     * @return true if the name matches
     */

    @Override
    public boolean matches(StructuredQName qname) {
        computeUriAndLocal();
        return qname.getLocalPart().equals(localName) && qname.hasURI(uri);
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
        return fingerprint;
    }

    /**
     * Get the name of the nodes matched by this nodetest, if it matches a specific name.
     * Return null if the node test matches nodes of more than one name
     */
    @Override
    public StructuredQName getMatchingNodeName() {
        computeUriAndLocal();
        return new StructuredQName("", uri, localName);
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

    /**
     * Get the set of node names allowed by this NodeTest. This is returned as a set of Integer fingerprints.
     * A null value indicates that all names are permitted (i.e. that there are no constraints on the node name.
     * The default implementation returns null.
     */

    /*@NotNull*/
    @Override
    public Optional<IntSet> getRequiredNodeNames() {
        return Optional.of(new IntSingletonSet(fingerprint));
    }

    /**
     * Get the namespace URI matched by this nametest
     *
     * @return the namespace URI (using "" for the "null namepace")
     */

    public String getNamespaceURI() {
        computeUriAndLocal();
        return uri;
    }

    /**
     * Get the local name matched by this nametest
     *
     * @return the local name
     */

    public String getLocalPart() {
        computeUriAndLocal();
        return localName;
    }

    public String toString() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return "element(" + namePool.getEQName(fingerprint) + ")";
            case Type.ATTRIBUTE:
                return "attribute(" + namePool.getEQName(fingerprint) + ")";
            case Type.PROCESSING_INSTRUCTION:
                return "processing-instruction(" + namePool.getLocalName(fingerprint) + ')';
            case Type.NAMESPACE:
                return "namespace-node(" + namePool.getLocalName(fingerprint) + ')';
        }
        return namePool.getEQName(fingerprint);
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return nodeKind << 20 ^ fingerprint;
    }

    /**
     * Determines whether two NameTests are equal
     */

    public boolean equals(Object other) {
        return other instanceof NameTest &&
                ((NameTest) other).namePool == namePool &&
                ((NameTest) other).nodeKind == nodeKind &&
                ((NameTest) other).fingerprint == fingerprint;
    }

    @Override
    public String getFullAlphaCode() {
        return getBasicAlphaCode() + " n" + getMatchingNodeName().getEQName();
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
        return getMatchingNodeName().getEQName();
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
        computeUriAndLocal();
        return "q.uri==='" + ExpressionPresenter.jsEscape(uri) + "'&&q.local==='" + localName + "'";
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
        return Optional.of("The node has the wrong name");
    }

    @Override
    public String toShortString() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return getNamespaceURI().isEmpty() ? namePool.getLocalName(getFingerprint()) : toString();
            case Type.ATTRIBUTE:
                return "@" + (getNamespaceURI().isEmpty() ? namePool.getLocalName(getFingerprint()) : toString());
            default:
                return toString();
        }
    }

}

