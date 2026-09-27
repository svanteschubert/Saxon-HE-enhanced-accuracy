////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.OperatorSymbol;
import net.sf.saxon.ma.map.StringMapBuilder;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.Item;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.pattern.qname.AnyQNameTest;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.type.ChoiceItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;

import java.util.Objects;
import java.util.Optional;

/**
 * A CombinedNodeTest combines two node tests using one of the operators
 * union (=or), intersect (=and), difference (= "and not"). This arises
 * when optimizing a union (etc) of two path expressions using the same axis.
 */

public class CombinedNodeTest implements NodeTest {

    private final NodeTest nodetest1;
    private final NodeTest nodetest2;
    private final OperatorSymbol operator;

    /**
     * Create a NodeTest that combines two other node tests
     *
     * @param nt1      the first operand. Note that if the defaultPriority of the pattern
     *                 is required, it will be taken from that of the first operand.
     * @param operator one of Token.UNION, Token.INTERSECT, Token.EXCEPT
     * @param nt2      the second operand
     */

    public CombinedNodeTest(NodeTest nt1, OperatorSymbol operator, NodeTest nt2) {
        Objects.requireNonNull(nt1);
        Objects.requireNonNull(nt2);
        Objects.requireNonNull(operator);
        nodetest1 = nt1;
        this.operator = operator;
        nodetest2 = nt2;
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        UType u1 = nodetest1.getUType();
        UType u2 = nodetest2.getUType();
        return switch (operator) {
            case UNION -> u1.union(u2);
            case INTERSECT -> u1.intersection(u2);
            case EXCEPT -> u1;
            default -> throw new IllegalArgumentException("Unknown operator in Combined Node Test");
        };
    }

    @Override
    public NodeTest asXNodeTest(Configuration config) {
        return new CombinedNodeTest(nodetest1.asXNodeTest(config), operator, nodetest2.asXNodeTest(config));
    }

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes.
     *
     * @param node the node to be matched
     */

    @Override
    public boolean test(GNode node) {
        return switch (operator) {
            case UNION -> nodetest1.test(node) || nodetest2.test(node);
            case INTERSECT -> nodetest1.test(node) && nodetest2.test(node);
            case EXCEPT -> nodetest1.test(node) && !nodetest2.test(node);
            default -> throw new IllegalArgumentException();
        };
    }



    public String toString() {
        return makeString(false);
    }

    private String makeString(boolean forExport) {
        return '(' + nodetest1.toString() + ' ' + operator.toString() + ' ' + nodetest2.toString() + ')';
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
    public String export() {
        String s1 = nodetest1.export();
        String s2 = nodetest2.export();
        return "$NT-" + operator + '(' + s1.length() + '#' + s1 + ',' + s2.length() + "#" + s2 + ')';
    }



//    public String getContentTypeForAlphaCode() {
//        if (nodetest1 instanceof NamedXNodeType && operator == OperatorSymbol.INTERSECT && nodetest2 instanceof NamedXNodeType) {
//            return getContentTypeForAlphaCode((NameTest) nodetest1, (NamedXNodeType)nodetest2);
//        } else if (nodetest2 instanceof NameTest && operator == OperatorSymbol.INTERSECT && nodetest1 instanceof NamedXNodeType) {
//            return getContentTypeForAlphaCode((NameTest) nodetest2, (NamedXNodeType) nodetest1);
//        } else {
//            return null;
//        }
//    }

//    private static String getContentTypeForAlphaCode(NameTest nodetest1, NamedXNodeType nodetest2) {
//        if (nodetest1.getNodeKind() == Type.ELEMENT) {
//            if (nodetest2.getContentType() == Untyped.getInstance() && nodetest2.isNillable()) {
//                return null;
//            } else {
//                SchemaType contentType = nodetest2.getContentType();
//                return contentType.getEQName();
//            }
//        } else if (nodetest1.getNodeKind() == Type.ATTRIBUTE) {
//            if (nodetest2.getContentType() == BuiltInAtomicType.UNTYPED_ATOMIC) {
//                return null;
//            } else {
//                SchemaType contentType = nodetest2.getContentType();
//                return contentType.getEQName();
//            }
//        } else {
//            throw new IllegalStateException();
//        }
//    }

    /**
     * Add the "parameters" of the type to a dictionary containing the type information
     * in structured form
     */

    public void addTypeDetails(StringMapBuilder map) {
//        if (nodetest1 instanceof NameTest && operator == OperatorSymbol.INTERSECT) {
//            map.put(new Twine8("n"), new StringValue(nodetest1.getMatchingNodeName().getEQName()));
//            if (nodetest2 instanceof NamedXNodeType) {
//                SchemaType schemaType = ((NamedXNodeType) nodetest2).getSchemaType();
//                if (schemaType != Untyped.getInstance() && schemaType != BuiltInAtomicType.UNTYPED_ATOMIC) {
//                    map.put(new Twine8("c"), new StringValue(schemaType.getEQName() +
//                            (nodetest2.isNillable() ? "?" : "")));
//
//                }
//            }
//        }
    }


//    /**
//     * Get the basic kind of object that this ItemType matches: for a NodeTest, this is the kind of node,
//     * or Type.Node if it matches different kinds of nodes.
//     *
//     * @return the node kind matched by this node test
//     */
//
//    @Override
//    public int getPrimitiveType() {
//        UType mask = getUType();
//        if (mask.equals(UType.ELEMENT)) {
//            return Type.ELEMENT;
//        }
//        if (mask.equals(UType.ATTRIBUTE)) {
//            return Type.ATTRIBUTE;
//        }
//        if (mask.equals(UType.DOCUMENT)) {
//            return Type.DOCUMENT;
//        }
//        return Type.XNODE;
//    }

    /**
     * Extract a QNameTest (the strongest one possible) that must be satisfied by a node
     * if it is to satisfy this NodeTest
     *
     * @return the strongest possible QNameTest
     */

    public QNameTest getQNameTest() {

        boolean b1 = nodetest1 instanceof QNameTest;
        boolean b2 = nodetest2 instanceof QNameTest;

        if (b1 && !b2) {
            return (QNameTest) nodetest1;
        }
        if (b2 && !b1) {
            return (QNameTest) nodetest2;
        }
        if (!b1) {
            return AnyQNameTest.getInstance();
        }
        QNameTest test1 = nodetest1.getQNameTest();
        QNameTest test2 = nodetest2.getQNameTest();
        if (test1.equals(test2)) {
            return test1;
        }
        if (operator == OperatorSymbol.UNION) {
            return new UnionQNameTest(test1, test2);
        }
        return AnyQNameTest.getInstance();
    }

    /**
     * Get an item type that all matching nodes must satisfy
     *
     * @return an item type
     */
    @Override
    public ItemType getItemType() {
        if (operator == OperatorSymbol.UNION) {
            return ChoiceItemType.of(nodetest1.getItemType(), nodetest2.getItemType());
        }
        return nodetest1.getItemType();
    }

    /**
     * Get a concise string representation of this node test for use in diagnostics
     *
     * @return a suitably abbreviated represention of the node test
     */
    @Override
    public String toShortString() {
        return toString();
    }

    //    /**
//     * Get the content type allowed by this NodeTest (that is, the type annotation of the matched nodes).
//     * Return AnyType if there are no restrictions. The default implementation returns AnyType.
//     */
//
//    @Override
//    public SchemaType getContentType() {
//        SchemaType type1 = nodetest1.getContentType();
//        SchemaType type2 = nodetest2.getContentType();
//        if (type1.isSameType(type2)) {
//            return type1;
//        }
//        if (operator == OperatorSymbol.INTERSECT) {
//            if (type2 instanceof AnyType || (type2 instanceof AnySimpleType && type1.isSimpleType())) {
//                return type1;
//            }
//            if (type1 instanceof AnyType || (type1 instanceof AnySimpleType && type2.isSimpleType())) {
//                return type2;
//            }
//        }
//        return AnyType.getInstance();
//    }

//    /**
//     * Get the item type of the atomic values that will be produced when an item
//     * of this type is atomized (assuming that atomization succeeds)
//     */
//
//    /*@NotNull*/
//    @Override
//    public AtomicType getAtomizedItemType() {
//        AtomicType type1 = (AtomicType)nodetest1.getAtomizedItemType();
//        AtomicType type2 = (AtomicType)nodetest2.getAtomizedItemType();
//        if (type1.isSameType(type2)) {
//            return type1;
//        }
//        if (operator == OperatorSymbol.INTERSECT) {
//            if (type2.equals(BuiltInAtomicType.ANY_ATOMIC)) {
//                return type1;
//            }
//            if (type1.equals(BuiltInAtomicType.ANY_ATOMIC)) {
//                return type2;
//            }
//        }
//        return BuiltInAtomicType.ANY_ATOMIC;
//    }

//    /**
//     * Ask whether values of this type are atomizable
//     *
//     * @return true unless it is known that these items will be elements with element-only
//     * content, in which case return false
//     * @param th the type hierarchy cache
//     */
//
//    @Override
//    public boolean isAtomizable(TypeHierarchy th) {
//        switch (operator) {
//            case UNION:
//                return nodetest1.isAtomizable(th) || nodetest2.isAtomizable(th);
//            case INTERSECT:
//                return nodetest1.isAtomizable(th) && nodetest2.isAtomizable(th);
//            case EXCEPT:
//                return nodetest1.isAtomizable(th);
//            default:
//                return true;
//        }
//    }


//    @Override
//    public StructuredQName getMatchingNodeName() {
//        StructuredQName n1 = nodetest1.getMatchingNodeName();
//        StructuredQName n2 = nodetest2.getMatchingNodeName();
//        if (n1 != null && n1.equals(n2)) {
//            return n1;
//        }
//        if (n1 == null && operator == OperatorSymbol.INTERSECT) {
//            return n2;
//        }
//        if (n2 == null && operator == OperatorSymbol.INTERSECT) {
//            return n1;
//        }
//        return null;
//    }

    /**
     * Determine whether the content type (if present) is nillable
     *
     * @return true if the content test (when present) can match nodes that are nilled
     */

    @Override
    public boolean isNillable() {
        // this should err on the safe side
        return nodetest1.isNillable() && nodetest2.isNillable();
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return nodetest1.hashCode() ^ nodetest2.hashCode();
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    public boolean equals(Object other) {
        return other instanceof CombinedNodeTest &&
                ((CombinedNodeTest) other).nodetest1.equals(nodetest1) &&
                ((CombinedNodeTest) other).nodetest2.equals(nodetest2) &&
                ((CombinedNodeTest) other).operator == operator;
    }

    /**
     * Get the default priority of this nodeTest when used as a pattern. In the case of a union, this will always
     * be (arbitrarily) the default priority of the first operand. In other cases, again somewhat arbitrarily, it
     * is 0.25, reflecting the common usage of an intersection to represent the pattern element(E, T).
     */

    @Override
    public double getDefaultPriority() {
        if (operator == OperatorSymbol.UNION) {
            return nodetest1.getDefaultPriority();
        } else {
            return 0.25;
        }
    }

    /**
     * Get the two parts of the combined node test
     *
     * @return the two operands
     */

    /*@NotNull*/
    public NodeTest[] getComponentNodeTests() {
        return new NodeTest[]{nodetest1, nodetest2};
    }

    /**
     * Get the operator used to combine the two node tests: one of {@link net.sf.saxon.expr.parser.Token#VBAR},
     * {@link OperatorSymbol#INTERSECT}, {@link OperatorSymbol#EXCEPT},
     *
     * @return the operator
     */

    public OperatorSymbol getOperator() {
        return operator;
    }

    public NodeTest getOperand(int which) {
        return which == 0 ? nodetest1 : nodetest2;
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
//        Optional<String> explanation = super.explainMismatch(item, th);
//        if (explanation.isPresent()) {
//            return explanation;
//        }
//        if (operator == OperatorSymbol.INTERSECT) {
//            // the most common case
//            if (!nodetest1.test((NodeInfo)item)) {
//                return nodetest1.explainMismatch(item, th);
//            } else if (!nodetest2.test((NodeInfo)item)) {
//                return nodetest2.explainMismatch(item, th);
//            }
//        }
        return Optional.empty();
    }


}

