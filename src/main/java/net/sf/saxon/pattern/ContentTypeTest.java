////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.Nilled_1;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.type.*;

import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * NodeTest is an interface that enables a test of whether a node matches particular
 * conditions. ContentTypeTest tests for an element or attribute node with a particular
 * type annotation.
 *
 * @author Michael H. Kay
 */

public class ContentTypeTest extends NodeTest {

    private int kind;          // element or attribute
    private SchemaType schemaType;
    private Configuration config;
    private boolean nillable = false;

    /**
     * Create a ContentTypeTest
     *
     * @param nodeKind   the kind of nodes to be matched: always elements or attributes
     * @param schemaType the required type annotation, as a simple or complex schema type
     * @param config     the Configuration, supplied because this KindTest needs access to schema information
     * @param nillable   indicates whether an element with xsi:nil=true satisifies the test
     */

    public ContentTypeTest(int nodeKind, SchemaType schemaType, Configuration config, boolean nillable) {
        this.kind = nodeKind;
        this.schemaType = schemaType;
        this.config = config;
        this.nillable = nillable;
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return kind == Type.ELEMENT ? UType.ELEMENT : UType.ATTRIBUTE;
    }

    /**
     * Indicate whether nilled elements should be matched (the default is false)
     *
     * @param nillable true if nilled elements should be matched
     */
    public void setNillable(boolean nillable) {
        this.nillable = nillable;
    }

    /**
     * The test is nillable if a question mark was specified as the occurrence indicator
     *
     * @return true if the test is nillable
     */

    @Override
    public boolean isNillable() {
        return nillable;
    }

    public SchemaType getSchemaType() {
        return schemaType;
    }

    public int getNodeKind() {
        return kind;
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
        return kind == nodeKind && matchesAnnotation(annotation);
    }

    @Override
    public IntPredicate getMatcher(final NodeVectorTree tree) {
        final byte[] nodeKindArray = tree.getNodeKindArray();
        return nodeNr -> (nodeKindArray[nodeNr]&0x0f) == kind &&
                matchesAnnotation(((TinyTree) tree).getSchemaType(nodeNr)) &&
                (nillable || !((TinyTree) tree).isNilled(nodeNr));
    }

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes.
     *
     * @param node the node to be matched
     */

    @Override
    public boolean test(/*@NotNull*/ NodeInfo node) {
        return node.getNodeKind() == kind &&
                matchesAnnotation(node.getSchemaType())
                && (nillable || !Nilled_1.isNilled(node));
    }

    private boolean matchesAnnotation(SchemaType annotation) {
        if (annotation == null) {
            return false;
        }
        if (schemaType == AnyType.getInstance()) {
            return true;
        }

        if (annotation.equals(schemaType)) {
            return true;
        }

        // see if the type annotation is a subtype of the required type
        Affinity r = config.getTypeHierarchy().schemaTypeRelationship(annotation, schemaType);
        return r == Affinity.SAME_TYPE || r == Affinity.SUBSUMED_BY;
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    @Override
    public final double getDefaultPriority() {
        return 0;
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
     * Get the content type allowed by this NodeTest (that is, the type annotation of the matched nodes).
     * Return AnyType if there are no restrictions. The default implementation returns AnyType.
     */

    @Override
    public SchemaType getContentType() {
        return schemaType;
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized (assuming that atomization succeeds)
     */

    /*@NotNull*/
    @Override
    public AtomicType getAtomizedItemType() {
        SchemaType type = schemaType;
        try {
            if (type.isAtomicType()) {
                return (AtomicType) type;
            } else if (type instanceof ListType) {
                SimpleType mem = ((ListType) type).getItemType();
                if (mem.isAtomicType()) {
                    return (AtomicType) mem;
                }
            } else if (type instanceof ComplexType && ((ComplexType) type).isSimpleContent()) {
                SimpleType ctype = ((ComplexType) type).getSimpleContentType();
                assert ctype != null;
                if (ctype.isAtomicType()) {
                    return (AtomicType) ctype;
                } else if (ctype instanceof ListType) {
                    SimpleType mem = ((ListType) ctype).getItemType();
                    if (mem.isAtomicType()) {
                        return (AtomicType) mem;
                    }
                }
            }
        } catch (MissingComponentException e) {
            return BuiltInAtomicType.ANY_ATOMIC;
        }
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @return true unless it is known that these items will be elements with element-only
     *         content, in which case return false
     * @param th The type hierarchy cache
     */

    @Override
    public boolean isAtomizable(TypeHierarchy th) {
        return !(schemaType.isComplexType() &&
                ((ComplexType) schemaType).getVariety() == ComplexType.VARIETY_ELEMENT_ONLY);
    }

    public String toString() {
        return (kind == Type.ELEMENT ? "element(*, " : "attribute(*, ") +
                schemaType.getEQName() + ')';
    }

    /**
     * Return a string representation of this ItemType suitable for use in stylesheet
     * export files. This differs from the result of toString() in that it will not contain
     * any references to anonymous types. Note that it may also use the Saxon extended syntax
     * for union types and tuple types. The default implementation returns the result of
     * calling {@code toString()}.
     *
     * @return the string representation as an instance of the XPath SequenceType construct
     */
    @Override
    public String toExportString() {
        return (kind == Type.ELEMENT ? "element(*, " : "attribute(*, ") +
                schemaType.getNearestNamedType().getEQName() + ')';
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return kind << 20 ^ schemaType.hashCode();
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    public boolean equals(Object other) {
        return other instanceof ContentTypeTest &&
                ((ContentTypeTest) other).kind == kind &&
                ((ContentTypeTest) other).schemaType == schemaType &&
                ((ContentTypeTest) other).nillable == nillable;
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
        if (!matchesAnnotation(((NodeInfo)item).getSchemaType())) {
            if (node.getSchemaType() == Untyped.getInstance()) {
                return Optional.of("The supplied node has not been schema-validated");
            }
            if (node.getSchemaType() == BuiltInAtomicType.UNTYPED_ATOMIC) {
                return Optional.of("The supplied node has not been schema-validated");
            }
            return Optional.of("The supplied node has the wrong type annotation (" + node.getSchemaType().getDescription() + ")");
        }
        if (Nilled_1.isNilled(node) && !nillable) {
            return Optional.of("The supplied node has xsi:nil='true', which the required type does not allow");
        }
        return Optional.empty();
    }

}

