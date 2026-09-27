////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.gnode;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.Nilled_1;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.PortableNamedXNodeType;
import net.sf.saxon.pattern.UnionQNameTest;
import net.sf.saxon.pattern.nodetest.NamedXNodePredicate;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.pattern.nodetest.NodeVectorMatchMaker;
import net.sf.saxon.pattern.qname.*;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.type.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * {@code NamedXNodeType} is a node type corresponding to the syntax
 * {@code element(N, T)} or {@code attribute(N, T)} or {@code namespace(N)}
 * or {@code processing-instruction(N)}.
 * <p>It tests for a node of a particular kind, whose name matches a
 * QNameTest (which embraces wildcards and choices) and whose type
 * annotation matches a given schema type; it is also conditional on whether
 * the node is nilled.</p>
 * <p>A {@code NamedXNodeType} is tied to one {@link Configuration}, which means it
 * cannot be used in the signature of a built-in function, as these are shared
 * across configurations. An alternative is therefore available in the class
 * {@link PortableNamedXNodeType}.</p>
 */

public class NamedXNodeType extends XNodeType implements NamedXNodePredicate, NodeVectorMatchMaker {

    private final int kind;
    private final QNameTest nameTest;
    private final SchemaType schemaType;
    private final boolean nillable;
    private final Configuration config;
    private boolean implicitSchemaType;
    /**
     * Create an NamedNodeKindType
     *
     * @param nodeKind   the kind of nodes to be matched: always elements or attributes
     * @param nameTest   constrains the name of the node
     * @param schemaType the required type annotation, as a simple or complex schema type
     * @param nillable   indicates whether an element with xsi:nil=true satisifies the test
     * @param config     the Configuration, supplied because this KindTest needs access to schema information
     */

    public NamedXNodeType(int nodeKind, QNameTest nameTest, SchemaType schemaType, boolean nillable, Configuration config) {
        this.kind = nodeKind;
        this.nameTest = nameTest == null ? AnyQNameTest.getInstance() : nameTest;
        this.schemaType = schemaType;
        this.config = config;
        this.nillable = nillable;
    }

    /**
     * Simplified convenience constructor
     * @param nodeKind   the kind of nodes to be matched: always elements or attributes
     * @param nameTest   constrains the name of the node
     * @param config     the Configuration, supplied because this KindTest needs access to schema information
     */
    public NamedXNodeType(int nodeKind, QNameTest nameTest, Configuration config) {
        this.kind = nodeKind;
        this.nameTest = nameTest;
        this.schemaType = AnyType.INSTANCE;
        this.config = config;
        this.nillable = true;
        this.implicitSchemaType = true;
    }

    /**
     * Simplified convenience constructor
     *
     * @param nodeKind the kind of nodes to be matched: always elements or attributes
     * @param nameTest constrains the name of the node
     * @param config   the Configuration, supplied because this KindTest needs access to schema information
     */
    public NamedXNodeType(int nodeKind, StructuredQName nameTest, Configuration config) {
        this.kind = nodeKind;
        this.nameTest = new SpecificQNameTest(nameTest, config.getNamePool());
        this.schemaType = AnyType.INSTANCE;
        this.config = config;
        this.nillable = true;
        this.implicitSchemaType = true;
    }

    public static NodeTest make(int nodeKind, NamespaceUri uri, String localName, Configuration config) {
        return new NamedXNodeType(nodeKind, new StructuredQName("", uri, localName), config);
    }

    public static NodeTest make(int nodeKind, int fingerprint, Configuration config) {
        return new NamedXNodeType(nodeKind, config.getNamePool().getStructuredQName(fingerprint), config);
    }

    /**
     * Say that the schema type is implicit. As an item type, element(E) and element(E, xs:anyType?) are equivalent,
     * but as XSLT patterns, they have different default priority, and therefore need to be distinguished. If the
     * schema type was omitted in the pattern we set implicitType to true, and this affects the calculation
     * of a default priority.
     * @param implicitSchemaType true if the node test in a pattern did not contain an explicit schema type
     */

    public void setImplicitSchemaType(boolean implicitSchemaType) {
        this.implicitSchemaType = implicitSchemaType;
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return switch (kind) {
            case Type.ELEMENT -> UType.ELEMENT;
            case Type.ATTRIBUTE -> UType.ATTRIBUTE;
            case Type.PROCESSING_INSTRUCTION -> UType.PI;
            case Type.NAMESPACE -> UType.NAMESPACE;
            default -> UType.ANY;
        };
    }

    /**
     * Extract a QNameTest (the strongest one possible) that must be satisfied by a node
     * if it is to satisfy this NodeTest
     *
     * @return the strongest possible QNameTest
     */
    @Override
    public QNameTest getQNameTest() {
        return nameTest;
    }

    /**
     * Get the set of allowed node names that this type if capable
     * of matching
     *
     * @return the allowed node names
     */
    @Override
    public QNameTest getAllowedNodeNames() {
        return nameTest;
    }

    /**
     * For an {@code XNodeType} that can only match one kind of node
     * and one node name, return that node name, as an integer
     * fingerprint. In other cases, return -1.
     *
     * @return an integer fingerprint in the case of a node type that
     * only matches one node kind and one qualified name. In other
     * cases return -1.
     */
    @Override
    public int getRequiredFingerprint() {
        if (nameTest instanceof SpecificQNameTest qTest) {
            return qTest.getFingerprint();
        }
        return -1;
    }

    @Override
    public ItemType getPrimitiveItemType() {
        return NodeKindType.makeNodeKindTest(kind);
    }

    /**
     * Get the corresponding StructuredQName: the name that all nodes must have
     * if they are to satisfy the predicate
     *
     * @return if all nodes selected by the node test are of the required nodeKind, and
     * have the same name, then return that name; otherwise return null.
     */
    @Override
    public StructuredQName getMatchingNodeName() {
        if (nameTest instanceof SpecificQNameTest) {
            return ((SpecificQNameTest) nameTest).getStructuredQName();
        }
        return null;
    }

    /**
     * Ask whether having the required fingerprint and node kind is a sufficient
     * condition for a node to satisfy the predicate, or whether other conditions
     * (such as type annotation or nillability) must also be satisfied.
     *
     * @return true if matching the fingerprint is a sufficient condition.
     */
    @Override
    public boolean isFingerprintSufficient() {
        if (kind == Type.ELEMENT) {
            return nillable && (schemaType.equals(AnyType.INSTANCE));
        }
        if (kind == Type.ATTRIBUTE) {
            return schemaType.equals(BuiltInAtomicType.ANY_ATOMIC) || schemaType == AnyType.INSTANCE;
        }
        return true;
    }

    /**
     * Get the Saxon Configuration to which this type belongs
     * @return the Saxon configuration
     */

    public Configuration getConfiguration() {
        return config;
    }


    /**
     * The test is nillable if a question mark was specified as the occurrence indicator
     *
     * @return true if the test is nillable
     */

    public boolean isNillable() {
        return nillable;
    }

    public SchemaType getContentType() {
        return schemaType;
    }

    public int getNodeKind() {
        return kind;
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
            if (node.getNodeKind() != kind) {
                return false;
            }
            if (node.hasFingerprint() && nameTest instanceof SpecificQNameTest qTest) {
                return qTest.getFingerprint() == node.getFingerprint() &&
                        matchesAnnotation(node.getSchemaType())
                        && (nillable || !Nilled_1.isNilled(node));
            } else {
                return nameTest.matches(node.getQName()) &&
                        matchesAnnotation(node.getSchemaType())
                        && (nillable || !Nilled_1.isNilled(node));
            }
        } else {
            return false;
        }
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
    public IntPredicate getMatcher(NodeVectorTree tree) {
        if (nameTest instanceof SpecificQNameTest) {
            if (schemaType == AnyType.INSTANCE && nillable) {
                int requiredFingerprint = ((SpecificQNameTest) nameTest).getFingerprint();
                return nodeNr -> (tree.getNodeKind(nodeNr) & 0x0f) == kind
                                && tree.getFingerprint(nodeNr) == requiredFingerprint;
            }
        }
        return nodeNr -> (tree.getNodeKind(nodeNr) & 0x0f) == kind
                                      && matches(tree.getNode(nodeNr));
    }




    private boolean matchesAnnotation(SchemaType annotation) {
        if (annotation == null) {
            return true;
        }
        if (schemaType == AnyType.INSTANCE) {
            return true;
        }

        if (annotation.getFingerprint() == schemaType.getFingerprint()) {
            if (annotation.equals(schemaType)) {
                return true;
            }
            throw new UncheckedXPathException("There are two different types with the same name " + annotation.getEQName() +
                                  "; this probably means the schema has been loaded twice, once to validate a source document, " +
                                  "and once as a static schema import.", "XTSE0220");
        }

        // see if the type annotation is a subtype of the required type
        Affinity r = Subsumption.schemaTypeRelationship(annotation, schemaType);
        return r == Affinity.SAME_TYPE || r == Affinity.SUBSUMED_BY;
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    @Override
    public final double getDefaultPriority() {
        return getDefaultPriority(implicitSchemaType, nameTest);
    }

    private static double getDefaultPriority(boolean implicitSchemaType, QNameTest nameTest) {
        if (nameTest instanceof UnionQNameTest union) {
            double max = Double.NEGATIVE_INFINITY;
            for (QNameTest qTest : union.getTests()) {
                double prio = getDefaultPriority(implicitSchemaType, qTest);
                if (prio > max) {
                    max = prio;
                }
            }
            return max;
        }
        if (implicitSchemaType) {
            if (nameTest instanceof AnyQNameTest) {
                return -0.5;
            } else if (nameTest instanceof SpecificQNameTest) {
                return 0;
            } else if (nameTest instanceof LocalQNameTest || nameTest instanceof NamespaceQNameTest) {
                return -0.25;
            } else {
                // Not sure what this covers...
                return 0;
            }
        } else {
            if (nameTest instanceof AnyQNameTest) {
                return 0;
            } else if (nameTest instanceof SpecificQNameTest) {
                return 0.25;
            } else if (nameTest instanceof LocalQNameTest || nameTest instanceof NamespaceQNameTest) {
                return 0.125;
            } else {
                // Not sure what this covers...
                return 0;
            }
        }
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
                ((ComplexType) schemaType).getVariety() == ComplexVariety.ELEMENT_ONLY);
    }

    public String toString() {
        String displayedType = "";
        if (schemaType != AnyType.INSTANCE && schemaType != Untyped.INSTANCE && schemaType != BuiltInAtomicType.UNTYPED_ATOMIC) {
            displayedType = ", " + schemaType.getEQName();
            if (nillable) {
                displayedType += "?";
            }
        }
        return switch (kind) {
            case Type.ELEMENT
                    -> "element(" + nameTest.toString() + displayedType + ')';
            case Type.ATTRIBUTE
                    -> "attribute(" + nameTest.toString() + displayedType + ')';
            case Type.PROCESSING_INSTRUCTION
                    -> "processing-instruction(" + nameTest.toString() + ")";
            case Type.NAMESPACE
                    -> "namespace(" + nameTest.toString() + ")";
            default -> "???";
        };
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
        return (kind == Type.ELEMENT ? "element(" : "attribute(") +
                nameTest.toString() + ',' +
                TypeHierarchy.getNearestNamedType(schemaType).getEQName() +
                (nillable? "?" : "") + ')';
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
        if (other instanceof NamedXNodeType t2) {
            return t2.kind == kind &&
                    t2.nameTest.equals(nameTest) &&
                    t2.schemaType == schemaType &&
                    t2.nillable == nillable;
        }
        if (other instanceof PortableNamedXNodeType p2) {
            return p2.getNodeKind() == kind &&
                    p2.getAllowedNodeNames().equals(nameTest);
        }
        return false;
    }

    @Override
    public ItemType normalizeItemType() {
        if (nameTest instanceof UnionQNameTest u) {
            // Convert element(a|b, T) to element(a, T) | element(b, T)
            Configuration config = getConfiguration();
            Set<QNameTest> tests = u.getTests();
            SchemaType contentType = getContentType();
            int nodeKind = getNodeKind();
            List<ItemType> memberTypes = new ArrayList<>();
            boolean nillable = isNillable();
            for (QNameTest test : tests) {
                NamedXNodeType contentTypeTest = new NamedXNodeType(
                        nodeKind, test, contentType, nillable, config);
                memberTypes.add(contentTypeTest);
            }
            return new ChoiceItemType(memberTypes);
        }
        if (nameTest instanceof AnyQNameTest && nillable && schemaType == AnyType.INSTANCE) {
            return NodeKindType.of(kind);
        }
        return this;
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
        if (!nameTest.matches(NameOfNode.makeName(node).getStructuredQName())) {
            return Optional.of("The node name does not match");
        }
        if (!matchesAnnotation(((NodeInfo)item).getSchemaType())) {
            if (node.getSchemaType() == Untyped.INSTANCE) {
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
        return switch (kind) {
            case Type.ELEMENT -> "NE";
            case Type.ATTRIBUTE -> "NA";
            case Type.TEXT -> "NT";
            case Type.COMMENT -> "NC";
            case Type.PROCESSING_INSTRUCTION -> "NP";
            case Type.DOCUMENT -> "ND";
            case Type.NAMESPACE -> "NN";
            default -> "*";
        };
    }

//    /**
//     * Get the full alpha code for this item type. As well as the basic alpha code, this contains
//     * additional information, for example <code>element(EFG)</code> has a basic alpha code of
//     * <code>NE</code>, but the full alpha code of <code>NE nQ{}EFG</code>.
//     *
//     * @return the alpha code for the type
//     */
//
//    public String getFullAlphaCode() {
//        StringBuilder sb = new StringBuilder(getBasicAlphaCode());
//        if (nameTest instanceof SpecificQNameTest) {
//            sb.append(" n");
//            sb.append(((SpecificQNameTest) nameTest).getStructuredQName().getEQName());
//        }
//        if (schemaType != Untyped.INSTANCE && schemaType != AnyType.INSTANCE && schemaType != BuiltInAtomicType.UNTYPED_ATOMIC) {
//            sb.append(" c");
//            StructuredQName name = schemaType.getStructuredQName();
//            if (name.hasURI(NamespaceUri.SCHEMA)) {
//                sb.append("~").append(name.getLocalPart());
//            } else {
//                sb.append(name.getEQName());
//            }
//        }
//        return sb.toString();
//    }


}

