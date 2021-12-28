////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.om.*;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.type.*;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.z.IntSet;
import net.sf.saxon.z.IntUniversalSet;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * A NodeTest is a simple kind of pattern that enables a context-free test of whether
 * a node matches a given node kind and name. There are several kinds of node test: a full name test, a prefix test, and an
 * "any node of a given type" test, an "any node of any type" test, a "no nodes"
 * test (used, e.g. for "@comment()").
 * <p>As well as being used to support XSLT pattern matching, NodeTests act as predicates in
 * axis steps, and also act as item types for type matching.</p>
 * <p>For use in user-written application calling {@link NodeInfo#iterateAxis(int, Predicate)},
 * it is possible to write a user-defined subclass of <code>NodeTest</code> that implements
 * a single method, {@link #matches(int, NodeName, SchemaType)}</p>
 */

public abstract class NodeTest implements Predicate<NodeInfo>, ItemType.WithSequenceTypeCache {

    private SequenceType _one;
    private SequenceType _oneOrMore;
    private SequenceType _zeroOrOne;
    private SequenceType _zeroOrMore;

    /**
     * Determine the Genre (top-level classification) of this type
     *
     * @return the Genre to which this type belongs, specifically {@link Genre#NODE}
     */
    @Override
    public Genre getGenre() {
        return Genre.NODE;
    }

    /**
     * Determine the default priority to use if this node-test appears as a match pattern
     * for a template with no explicit priority attribute.
     *
     * @return the default priority for the pattern
     */

    @Override
    public abstract double getDefaultPriority();


    @Override
    public boolean matches(Item item, TypeHierarchy th) {
        return item instanceof NodeInfo && test((NodeInfo) item);
    }

    /**
     * Get the primitive item type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that INTEGER is considered to be a primitive type.
     */

    /*@NotNull*/
    @Override
    public ItemType getPrimitiveItemType() {
        int p = getPrimitiveType();
        if (p == Type.NODE) {
            return AnyNodeTest.getInstance();
        } else {
            return NodeKindTest.makeNodeKindTest(p);
        }
    }

    /**
     * Get the basic kind of object that this ItemType matches: for a NodeTest, this is the kind of node,
     * or Type.Node if it matches different kinds of nodes.
     *
     * @return the node kind matched by this node test
     */

    @Override
    public int getPrimitiveType() {
        return Type.NODE;
    }

    /**
     * Get the name of the nodes matched by this nodetest, if it matches a specific name.
     * Return -1 if the node test matches nodes of more than one name
     */

    public int getFingerprint() {
        return -1;
    }

    /**
     * Get the name of the nodes matched by this nodetest, if it matches a specific name.
     * Return null if the node test matches nodes of more than one name
     */

    public StructuredQName getMatchingNodeName() {
        return null;
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
        switch (getPrimitiveType()) {
            case Type.NODE:
                return "N";
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

    /**
     * Determine whether this item type is an atomic type
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof
     */
    @Override
    public boolean isAtomicType() {
        return false;
    }

    /**
     * Determine whether this item type is atomic (that is, whether it can ONLY match
     * atomic values)
     *
     * @return false: this is not ANY_ATOMIC_TYPE or a subtype thereof
     */

    @Override
    public boolean isPlainType() {
        return false;
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized (assuming that atomization succeeds)
     */

    /*@NotNull*/
    @Override
    public AtomicType getAtomizedItemType() {
        // This is overridden for a ContentTypeTest
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
        // This is overridden for a ContentTypeTest
        return true;
    }

    /**
     * Get a matching function that can be used to test whether numbered nodes in a TinyTree
     * or DominoTree satisfy the node test. (Calling this matcher must give the same result
     * as calling <code>matchesNode(tree.getNode(nodeNr))</code>, but it may well be faster).
     * @param tree the tree against which the returned function will operate
     * @return an IntPredicate; the matches() method of this predicate takes a node number
     * as input, and returns true if and only if the node identified by this node number
     * matches the node test.
     */

    public IntPredicate getMatcher(final NodeVectorTree tree) {
        return nodeNr -> test(tree.getNode(nodeNr));
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
     * @param annotation The actual content type of the node. Null means no constraint.
     * @return true if the node matches this node test
     */

    public abstract boolean matches(int nodeKind, NodeName name, SchemaType annotation);

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes. The default implementation calls the method
     * {@link #matches(int, NodeName, SchemaType)}
     *
     * @param node the node to be matched
     * @return true if the node test is satisfied by the supplied node, false otherwise
     */

    @Override
    public boolean test(/*@NotNull*/ NodeInfo node) {
        return matches(node.getNodeKind(), NameOfNode.makeName(node), node.getSchemaType());
    }

    /**
     * Get the content type allowed by this NodeTest (that is, the type annotation of the matched nodes).
     * Return AnyType if there are no restrictions. The default implementation returns AnyType.
     *
     * @return the type annotation that all nodes matching this NodeTest must satisfy
     */

    public SchemaType getContentType() {
        Set<PrimitiveUType> m = getUType().decompose();
        Iterator<PrimitiveUType> it = m.iterator();
        if (m.size() == 1 && it.hasNext()) {
            PrimitiveUType p = it.next();
            switch (p) {
                case DOCUMENT:
                    return AnyType.getInstance();
                case ELEMENT:
                    return AnyType.getInstance();
                case ATTRIBUTE:
                    return AnySimpleType.getInstance();
                case COMMENT:
                    return BuiltInAtomicType.STRING;
                case TEXT:
                    return BuiltInAtomicType.UNTYPED_ATOMIC;
                case PI:
                    return BuiltInAtomicType.STRING;
                case NAMESPACE:
                    return BuiltInAtomicType.STRING;
            }
        }
        return AnyType.getInstance();
    }

    /**
     * Get the set of node names allowed by this NodeTest. This is returned as a set of Integer fingerprints.
     * If all names are permitted (i.e. there are no constraints on the node name), returns IntUniversalSet.getInstance().
     * The default implementation returns the universal set.
     *
     * @return the set of integer fingerprints of the node names that this node test can match; or absent
     * if the set of names cannot be represented (for example, with the name tests *:xxx or xxx:*)
     */

    /*@NotNull*/
    public Optional<IntSet> getRequiredNodeNames() {
        return Optional.of(IntUniversalSet.getInstance());
    }

    /**
     * Determine whether the content type (if present) is nillable
     *
     * @return true if the content test (when present) can match nodes that are nilled
     */

    public boolean isNillable() {
        return true;
    }

    /**
     * Copy a NodeTest.
     * Since they are never written to except in their constructors, returns the same.
     *
     * @return the original nodeTest
     */

    /*@NotNull*/
    public NodeTest copy() {
        return this;
    }

    /**
     * Get a sequence type representing exactly one instance of this atomic type
     *
     * @return a sequence type representing exactly one instance of this atomic type
     * @since 9.8.0.2
     */

    @Override
    public SequenceType one() {
        if (_one == null) {
            _one = new SequenceType(this, StaticProperty.EXACTLY_ONE);
        }
        return _one;
    }

    /**
     * Get a sequence type representing zero or one instances of this atomic type
     *
     * @return a sequence type representing zero or one instances of this atomic type
     * @since 9.8.0.2
     */

    @Override
    public SequenceType zeroOrOne() {
        if (_zeroOrOne == null) {
            _zeroOrOne = new SequenceType(this, StaticProperty.ALLOWS_ZERO_OR_ONE);
        }
        return _zeroOrOne;
    }

    /**
     * Get a sequence type representing one or more instances of this atomic type
     *
     * @return a sequence type representing one or more instances of this atomic type
     * @since 9.8.0.2
     */

    @Override
    public SequenceType oneOrMore() {
        if (_oneOrMore == null) {
            _oneOrMore = new SequenceType(this, StaticProperty.ALLOWS_ONE_OR_MORE);
        }
        return _oneOrMore;
    }

    /**
     * Get a sequence type representing one or more instances of this atomic type
     *
     * @return a sequence type representing one or more instances of this atomic type
     * @since 9.8.0.2
     */

    @Override
    public SequenceType zeroOrMore() {
        if (_zeroOrMore == null) {
            _zeroOrMore = new SequenceType(this, StaticProperty.ALLOWS_ZERO_OR_MORE);
        }
        return _zeroOrMore;
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
        if (item instanceof NodeInfo) {
            UType actualKind = UType.getUType(item);
            if (!getUType().overlaps(actualKind)) {
                return Optional.of("The supplied value is " + actualKind.toStringWithIndefiniteArticle());
            }
            return Optional.empty();
        } else {
            return Optional.of("The supplied value is " + item.getGenre().getDescription());
        }
    }

    public String toShortString() {
        return toString();
    }


}

