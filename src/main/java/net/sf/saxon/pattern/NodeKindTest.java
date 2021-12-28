////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.type.*;

import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * NodeTest is an interface that enables a test of whether a node has a particular
 * name and kind. A NodeKindTest matches the node kind only.
 *
 * @author Michael H. Kay
 */

public class NodeKindTest extends NodeTest {

    public static final NodeKindTest DOCUMENT = new NodeKindTest(Type.DOCUMENT);
    public static final NodeKindTest ELEMENT = new NodeKindTest(Type.ELEMENT);
    public static final NodeKindTest ATTRIBUTE = new NodeKindTest(Type.ATTRIBUTE);
    public static final NodeKindTest TEXT = new NodeKindTest(Type.TEXT);
    public static final NodeKindTest COMMENT = new NodeKindTest(Type.COMMENT);
    public static final NodeKindTest PROCESSING_INSTRUCTION = new NodeKindTest(Type.PROCESSING_INSTRUCTION);
    public static final NodeKindTest NAMESPACE = new NodeKindTest(Type.NAMESPACE);


    private int kind;
    private UType uType;

    private NodeKindTest(int nodeKind) {
        kind = nodeKind;
        uType = UType.fromTypeCode(nodeKind);
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

    public static NodeTest makeNodeKindTest(int kind) {
        switch (kind) {
            case Type.DOCUMENT:
                return DOCUMENT;
            case Type.ELEMENT:
                return ELEMENT;
            case Type.ATTRIBUTE:
                return ATTRIBUTE;
            case Type.COMMENT:
                return COMMENT;
            case Type.TEXT:
                return TEXT;
            case Type.PROCESSING_INSTRUCTION:
                return PROCESSING_INSTRUCTION;
            case Type.NAMESPACE:
                return NAMESPACE;
            case Type.NODE:
                return AnyNodeTest.getInstance();
            default:
                throw new IllegalArgumentException("Unknown node kind " + kind + " in NodeKindTest");
        }
    }

    @Override
    public boolean matches(Item item, /*@NotNull*/TypeHierarchy th) {
        return item instanceof NodeInfo && kind == ((NodeInfo) item).getNodeKind();
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
        return kind == nodeKind;
    }

    @Override
    public IntPredicate getMatcher(final NodeVectorTree tree) {
        final byte[] nodeKindArray = tree.getNodeKindArray();
        if (kind == Type.TEXT) {
            return nodeNr -> {
                int k = nodeKindArray[nodeNr];
                return k == Type.TEXT || k == Type.WHITESPACE_TEXT;
            };
        } else {
            return nodeNr -> (nodeKindArray[nodeNr] & 0x0f) == kind;
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
        return node.getNodeKind() == kind;
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
     * Get the content type allowed by this NodeTest (that is, the type of content allowed).
     * Return AnyType if there are no restrictions.
     */

    @Override
    public SchemaType getContentType() {
        switch (kind) {
            case Type.DOCUMENT:
                return AnyType.getInstance();
            case Type.ELEMENT:
                return AnyType.getInstance();
            case Type.ATTRIBUTE:
                return AnySimpleType.getInstance();
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
        return toString(kind);
    }

    public static String toString(int kind) {
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
    public boolean equals(Object other) {
        return other instanceof NodeKindTest &&
                ((NodeKindTest) other).kind == kind;
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
            return Optional.of("The supplied value is " + item.getGenre().getDescription());
        }
    }

    @Override
    public String toShortString() {
        switch (getNodeKind()) {
            case Type.ELEMENT:
                return "*";
            case Type.ATTRIBUTE:
                return "@*";
            case Type.DOCUMENT:
                return "/";
            default:
                return toString();
        }
    }

}

