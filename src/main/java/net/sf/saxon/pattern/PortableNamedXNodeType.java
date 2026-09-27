////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.qname.PortableQNameTest;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;
import net.sf.saxon.type.gnode.NamedXNodeType;
import net.sf.saxon.type.gnode.XNodeType;

import java.util.Optional;

/**
 * {@code PortableNamedXNodeType} is variant of the {@link net.sf.saxon.type.gnode.NamedXNodeType} class
 * that works across Configurations.
 * It is used in the function signatures of functions like {@code fn:analyze-string};
 * these signatures appear in Java static data, so they cannot be tied to a particular
 * configuration.
 */

public class PortableNamedXNodeType extends XNodeType {

    private final int nodeKind;
    private final UType uType;
    private final QNameTest qNameTest;

    /**
     * Create a NameTest to match nodes by name
     *
     * @param nodeKind  the kind of node, for example {@link Type#ELEMENT}
     * @param qNameTest a test that the name of the node must satisfy
     * @since 13.0
     */

    public PortableNamedXNodeType(int nodeKind, QNameTest qNameTest) {
        this.qNameTest = qNameTest;
        this.nodeKind = nodeKind;
        this.uType = UType.fromTypeCode(nodeKind);
    }

    /**
     * Create a NameTest to match nodes by name
     *
     * @param nodeKind  the kind of node, for example {@link Type#ELEMENT}
     * @param requiredName the required name of the nodes
     * @since 13.0
     */

    public PortableNamedXNodeType(int nodeKind, StructuredQName requiredName) {
        this.qNameTest = new PortableQNameTest(requiredName);
        this.nodeKind = nodeKind;
        this.uType = UType.fromTypeCode(nodeKind);
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item) {
        if (item instanceof NodeInfo node) {
            return node.getNodeKind() == nodeKind
                    && qNameTest.matches(node.getQName());
        }
        return false;

    }

    /**
     * Get the set of allowed node names that this type if capable
     * of matching
     *
     * @return the allowed node names
     */
    @Override
    public QNameTest getAllowedNodeNames() {
        return qNameTest;
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
        return uType;
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    @Override
    public final double getDefaultPriority() {
        return 0.0;
    }

    /**
     * Get an alphabetic code representing the type, or at any rate, the nearest built-in type
     * from which this type is derived. The codes are designed so that for any two built-in types
     * A and B, alphaCode(A) is a prefix of alphaCode(B) if and only if A is a supertype of B.
     *
     * @return the alphacode for the nearest containing built-in type. For example: for xs:string
     * return "AS", for xs:boolean "AB", for node() "N", for element() "NE", for map(*) "FM", for
     * array(*) "FA".
     */
    @Override
    public String getBasicAlphaCode() {
        return nodeKind == Type.ELEMENT ? "NE" : "NA";
    }



//    /**
//     * Get the fingerprint required
//     */
//
//    @Override
//    public int getFingerprint() {
//        return -1;
//    }

//    /**
//     * Get the name of the nodes matched by this nodetest, if it matches a specific name.
//     * Return null if the node test matches nodes of more than one name
//     */
//    @Override
//    public StructuredQName getMatchingNodeName() {
//        return new StructuredQName("", uri, localName);
//    }

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
        String n = qNameTest.toString();
        switch (nodeKind) {
            case Type.ELEMENT:
                return "element(" + n + ")";
            case Type.ATTRIBUTE:
                return "attribute(" + n + ")";
            case Type.PROCESSING_INSTRUCTION:
                return "processing-instruction(" + n + ')';
            case Type.NAMESPACE:
                return "namespace-node(" + n + ')';
            default:
                return "???(" + n + ")";
        }
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return nodeKind << 20 ^ qNameTest.hashCode();
    }

    /**
     * Determines whether two NameTests are equal
     */

    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    public boolean equals(Object other) {
        if (other instanceof NamedXNodeType t2) {
            return t2.equals(this);
        }
        return other instanceof PortableNamedXNodeType &&
                ((PortableNamedXNodeType) other).qNameTest.equals(qNameTest) &&
                ((PortableNamedXNodeType) other).nodeKind == nodeKind;
    }

//    @Override
//    public String getFullAlphaCode() {
//        return getBasicAlphaCode() + " n" + getMatchingNodeName().getEQName();
//    }


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
        return Optional.of("The node has the wrong node kind or name");
    }



}

