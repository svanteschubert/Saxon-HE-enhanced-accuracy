////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.*;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;
import net.sf.saxon.z.IntPredicateLambda;
import net.sf.saxon.z.IntPredicateProxy;
import net.sf.saxon.z.IntSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * NameTestUnion is new in XPath 4.0. It allows node tests of the form element(A|B) or attribute(A|B);
 * the operands of the union can also be wildcards, for example element(p:*|q:*).
 * @since 12.4
 */

public class NameTestUnion extends NodeTest implements QNameTest {

    private final UnionQNameTest nameTest;
    private final int nodeKind;

    /**
     * Create a NameTest to match nodes by name
     *
     * @param nodeKind  the kind of node, for example {@link Type#ELEMENT}
     * @since 12.4
     */

    public NameTestUnion(UnionQNameTest nameTest, int nodeKind) {
        assert(nodeKind == Type.ELEMENT || nodeKind == Type.ATTRIBUTE);
        this.nameTest = nameTest;
        this.nodeKind = nodeKind;
    }

    public static NameTestUnion withTests(List<NodeTest> tests) {
        assert !tests.isEmpty();
        int nodeKind = tests.get(0).getPrimitiveType();
        assert nodeKind == Type.ELEMENT || nodeKind == Type.ATTRIBUTE;
        List<QNameTest> qNameTests = new ArrayList<>(tests.size());
        for (NodeTest test : tests) {
            if (test instanceof QNameTest) {
                qNameTests.add((QNameTest)test);
            } else {
                assert false;
            }
        }
        return new NameTestUnion(new UnionQNameTest(qNameTests), nodeKind);
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
     * Get the corresponding {@link UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return nodeKind == Type.ELEMENT ? UType.ELEMENT : UType.ATTRIBUTE;
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
        return nodeKind == this.nodeKind && nameTest.matches(name.getStructuredQName());
    }

    @Override
    public IntPredicateProxy getMatcher(final NodeVectorTree tree) {
        final byte[] nodeKindArray = tree.getNodeKindArray();
        final int[] nameCodeArray = tree.getNameCodeArray();
        final NamePool pool = tree.getNamePool();
        return IntPredicateLambda.of(nodeNr -> (
                nodeKindArray[nodeNr] & 0x0f) == nodeKind
                && nameTest.matchesFingerprint(pool, nameCodeArray[nodeNr] & NamePool.FP_MASK));
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
        return matches(node.getNodeKind(), NameOfNode.makeName(node), node.getSchemaType());
    }

    /**
     * Test whether the NameTest matches a given QName
     *
     * @param qname the QName to be matched
     * @return true if the name matches
     */

    @Override
    public boolean matches(StructuredQName qname) {
        return nameTest.matches(qname);
    }

    /**
     * Test whether the QNameTest matches a given fingerprint
     *
     * @param namePool the name pool
     * @param fp       the fingerprint of the QName to be matched
     * @return true if the name matches, false if not
     */
    @Override
    public boolean matchesFingerprint(NamePool namePool, int fp) {
        return nameTest.matchesFingerprint(namePool, fp);
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    @Override
    public final double getDefaultPriority() {
        return 1.0;
    }

    /**
     * Get the fingerprint required
     */

    @Override
    public int getFingerprint() {
        return -1;
    }

    /**
     * Get the name of the nodes matched by this nodetest, if it matches a specific name.
     * Return null if the node test matches nodes of more than one name
     */
    @Override
    public StructuredQName getMatchingNodeName() {
        return null;
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
     */

    /*@NotNull*/
    @Override
    public Optional<IntSet> getRequiredNodeNames() {
        return Optional.empty();
    }

    public String toString() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return "element(" + nameTest.toString() + ")";
            case Type.ATTRIBUTE:
                return "attribute(" + nameTest.toString() + ")";
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return nodeKind << 21 ^ nameTest.hashCode();
    }

    /**
     * Determines whether two NameTests are equal
     */

    public boolean equals(Object other) {
        return other instanceof NameTestUnion &&
                ((NameTestUnion) other).nodeKind == nodeKind &&
                ((NameTestUnion) other).nameTest.equals(nameTest);
    }

    @Override
    public String getFullAlphaCode() {
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
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
        return toString();
    }

}

