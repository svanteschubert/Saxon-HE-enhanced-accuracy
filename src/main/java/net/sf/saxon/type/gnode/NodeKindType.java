////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.gnode;

import net.sf.saxon.om.Genre;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.nodetest.NodeVectorMatchMaker;
import net.sf.saxon.pattern.qname.AnyQNameTest;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.trans.Err;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.type.*;
import net.sf.saxon.z.IntPredicateProxy;

import java.util.Optional;

/**
 * A {@code NodeKindType} matches one of the seven kinds of XNode.
 */

public class NodeKindType extends XNodeType implements NodeVectorMatchMaker {

    public static final NodeKindType DOCUMENT = new NodeKindType(Type.DOCUMENT);
    public static final NodeKindType ELEMENT = new NodeKindType(Type.ELEMENT);
    public static final NodeKindType ATTRIBUTE = new NodeKindType(Type.ATTRIBUTE);
    public static final NodeKindType TEXT = new NodeKindType(Type.TEXT);
    public static final NodeKindType COMMENT = new NodeKindType(Type.COMMENT);
    public static final NodeKindType PROCESSING_INSTRUCTION = new NodeKindType(Type.PROCESSING_INSTRUCTION);
    public static final NodeKindType NAMESPACE = new NodeKindType(Type.NAMESPACE);


    private final int kind;
    private final UType uType;

    private NodeKindType(int nodeKind) {
        kind = nodeKind;
        uType = UType.fromTypeCode(nodeKind);
    }

    /**
     * Determine the Genre (top-level classification) of this type
     *
     * @return the Genre to which this type belongs, for example node or atomic value
     */
    @Override
    public Genre getGenre() {
        return Genre.XNODE;
    }

    public static NodeKindType of(int nodeKind) {
        return new NodeKindType(nodeKind);
    }

    /**
     * Get the node kind matched by this test
     *
     * @return the matching node kind
     */

    public int getNodeKind() {
        return kind;
    }

    /**
     * Get the set of allowed node names that this type if capable
     * of matching
     *
     * @return the allowed node names
     */
    @Override
    public QNameTest getAllowedNodeNames() {
        return AnyQNameTest.getInstance();
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
     * Make a test for a given kind of node
     */

    public static GNodeType makeNodeKindTest(int kind) {
        return switch (kind) {
            case Type.DOCUMENT -> DOCUMENT;
            case Type.ELEMENT -> ELEMENT;
            case Type.ATTRIBUTE -> ATTRIBUTE;
            case Type.COMMENT -> COMMENT;
            case Type.TEXT -> TEXT;
            case Type.PROCESSING_INSTRUCTION -> PROCESSING_INSTRUCTION;
            case Type.NAMESPACE -> NAMESPACE;
            case Type.XNODE -> AnyXNodeType.getInstance();
            default -> throw new IllegalArgumentException("Unknown node kind " + kind + " in NodeKindTest");
        };
    }

    /**
     * Get a matching function that can be used to test whether numbered nodes in a TinyTree
     * or DominoTree satisfy the node test. (Calling this matcher must give the same result
     * as calling <code>matchesNode(tree.getNode(nodeNr))</code>, but it may well be faster).
     *
     * @param tree the tree against which the returned function will operate
     * @return an IntPredicate; the matches() method of this predicate takes a node number
     * as input, and returns true if and only if the node identified by this node number
     * matches the node predicate.
     */
    @Override
    public IntPredicateProxy getMatcher(NodeVectorTree tree) {
        return nodeNr -> (tree.getNodeKind(nodeNr) & 0x0f) == kind;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @param th the type hierarchy cache
     * @return true if some or all instances of this type can be successfully atomized; false
     * if no instances of this type can be atomized
     */
    @Override
    public boolean isAtomizable(TypeHierarchy th) {
        return true;
    }

    @Override
    public boolean matches(Item item) {
        return item instanceof NodeInfo node && kind == node.getNodeKind();
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    @Override
    public final double getDefaultPriority() {
        return -0.5;
    }

    /**
     * Determine the types of nodes to which this pattern applies. Used for optimisation.
     *
     * @return the type of node matched by this pattern. e.g. Type.ELEMENT or Type.TEXT
     */

    @Override
    public int getPrimitiveType() {
        return kind;
    }

    /**
     * Get the content type allowed by this NodeTest (that is, the type annotation).
     * Return AnyType if there are no restrictions. The default implementation returns AnyType.
     */

    /*@NotNull*/
    @Override
    public AtomicType getAtomizedItemType() {
        switch (kind) {
            case Type.DOCUMENT:
                return BuiltInAtomicType.UNTYPED_ATOMIC;
            case Type.ELEMENT:
                return BuiltInAtomicType.ANY_ATOMIC;
            case Type.ATTRIBUTE:
                return BuiltInAtomicType.ANY_ATOMIC;
            case Type.COMMENT:
                return BuiltInAtomicType.STRING;
            case Type.TEXT:
                return BuiltInAtomicType.UNTYPED_ATOMIC;
            case Type.PROCESSING_INSTRUCTION:
                return BuiltInAtomicType.STRING;
            case Type.NAMESPACE:
                return BuiltInAtomicType.STRING;
            default:
                throw new AssertionError("Unknown node kind");
        }
    }

    /*@NotNull*/
    public String toString() {
        return describe(kind);
    }

    public static String describe(int kind) {
        switch (kind) {
            case Type.DOCUMENT:
                return "document-node()";
            case Type.ELEMENT:
                return "element()";
            case Type.ATTRIBUTE:
                return "attribute()";
            case Type.COMMENT:
                return "comment()";
            case Type.TEXT:
                return "text()";
            case Type.PROCESSING_INSTRUCTION:
                return "processing-instruction()";
            case Type.NAMESPACE:
                return "namespace-node()";
            default:
                return "** error **";
        }
    }

    /**
     * Get the name of a node kind
     *
     * @param kind the node kind, for example Type.ELEMENT or Type.ATTRIBUTE
     * @return the name of the node kind, for example "element" or "attribute"
     */

    public static String nodeKindName(int kind) {
        switch (kind) {
            case Type.DOCUMENT:
                return "document";
            case Type.ELEMENT:
                return "element";
            case Type.ATTRIBUTE:
                return "attribute";
            case Type.COMMENT:
                return "comment";
            case Type.TEXT:
                return "text";
            case Type.PROCESSING_INSTRUCTION:
                return "processing-instruction";
            case Type.NAMESPACE:
                return "namespace";
            default:
                return "** error **";
        }
    }


    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return kind;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    @SuppressWarnings("EqualsBetweenInconvertibleTypes")
    public boolean equals(Object other) {
        if (other instanceof NodeKindType k2) {
            return k2.kind == this.kind;
        }
        if (other instanceof NamedXNodeType t2) {
            return t2.equals(this);
        }
        return false;
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
        if (item instanceof NodeInfo) {
            UType actualKind = UType.getUType(item);
            if (!getUType().overlaps(actualKind)) {
                return Optional.of("The supplied value is " + actualKind.toStringWithIndefiniteArticle());
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.of("The supplied value is " + Err.describeGenre(item.getGenre()));
        }
    }

    /**
     * Get an alphabetic code representing the type, or at any rate, the nearest built-in type
     * from which this type is derived. The codes are designed so that for any two built-in types
     * A and B, alphaCode(A) is a prefix of alphaCode(B) if and only if A is a supertype of B.
     *
     * @return the alphacode for the nearest containing built-in type
     */
    @Override
    public String getBasicAlphaCode() {
        switch (kind) {
            case Type.ELEMENT:
                return "NE";
            case Type.ATTRIBUTE:
                return "NA";
            case Type.TEXT:
                return "NT";
            case Type.COMMENT:
                return "NC";
            case Type.PROCESSING_INSTRUCTION:
                return "NP";
            case Type.DOCUMENT:
                return "ND";
            case Type.NAMESPACE:
                return "NN";
            default:
                return "*";
        }
    }

}

