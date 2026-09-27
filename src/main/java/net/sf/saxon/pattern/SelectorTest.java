////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.Configuration;
import net.sf.saxon.ma.jnode.AnyJNodeType;
import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.nodetest.NamedXNodePredicate;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.pattern.nodetest.NodeVectorMatchMaker;
import net.sf.saxon.pattern.qname.LocalQNameTest;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.pattern.qname.SpecificQNameTest;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.type.*;
import net.sf.saxon.type.gnode.NamedXNodeType;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.QNameValue;

import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * A SelectorTest represents an axis step written as {@code ./NNN} or {@code ./axis::NNN}
 * where {@code NNN} is either an EQName or a wildcard (such as {@code prefix:*} or {@code *:prefix}).
 * The semantics depend on whether we are selecting JNodes or XNodes, which we don't know
 * in advance (except in the case where the axis is the attribute or namespace axis).
 *
 * <p>Once it is known whether the nodes to be matched will be JNodes or XNodes, this
 * node test can be replaced by something more specific; but in many cases that isn't known
 * till evaluation time.</p>
 *
 * <p>The boolean flag asNCName is true if the test was written as a plain
 * NCName. Where JNodes are being matched, this is interpreted as a string-valued
 * key rather than as a QName.</p>
 *
 * <p>The nodeKind represents the principal node kind for the axis, which is only
 * relevant when selecting XNodes.</p>
 */

public class SelectorTest implements NodeTest, NodeVectorMatchMaker, NamedXNodePredicate {

    private final QNameTest key;
    private final boolean asNCName;
    private final int nodeKind;

    public SelectorTest(QNameTest key, boolean asNCName, int nodeKind) {
        this.key = key;
        this.asNCName = asNCName;
        this.nodeKind = nodeKind;
    }

    /**
     * Get the kind of nodes that this node predicate matches
     *
     * @return the kind of nodes, for example {@link Type#ELEMENT} or {@link Type#ATTRIBUTE}
     */
    @Override
    public int getNodeKind() {
        return nodeKind;
    }

    /**
     * Get an equivalent node test, knowing that it will only be used to match XNodes
     * @return an equivalent node test for XNodes
     */
    public NodeTest asXNodeTest(Configuration config) {
        return new NamedXNodeType(nodeKind, getQNameTest(), config);
    }

    /**
     * Get the corresponding {@link UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return switch (nodeKind) {
            case Type.ELEMENT -> UType.JNODE.union(UType.ELEMENT);
            case Type.ATTRIBUTE -> UType.ATTRIBUTE;
            case Type.NAMESPACE -> UType.NAMESPACE;
            default -> UType.GNODE;
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
     * matches the node test.
     */

    public IntPredicate getMatcher(final NodeVectorTree tree) {
        // Default implementation materialises the node
        // TODO: we need to do better than this
        return nodeNr -> tree.getNodeKind(nodeNr) != Type.PARENT_POINTER
                && test(tree.getNode(nodeNr));
    }

    /**
     * Get a fingerprint that can be used to match required nodes.
     *
     * @return if all nodes selected by the node test are of the required nodeKind, and
     * have the same fingerprint, then return that fingerprint; otherwise return -1. Note
     * that it is acceptable for the node test to impose further constraints, for example
     * on the type annotation.
     */

    public int getRequiredFingerprint() {
        if (key instanceof SpecificQNameTest) {
            return ((SpecificQNameTest)key).getFingerprint();
        } else {
            return -1;
        }
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
        return true;
    }

    /**
     * Get an item type that all matching nodes must satisfy
     *
     * @return an item type
     */
    @Override
    public ItemType getItemType() {
        return ChoiceItemType.of(
                AnyJNodeType.getInstance(), NodeKindType.of(this.nodeKind));
    }

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes.
     *
     * @param gnode the node to be matched
     */

    @Override
    public boolean test(GNode gnode) {
        if (gnode instanceof JNode) {
            if (asNCName) {
                String mapKey;
                if (key instanceof LocalQNameTest) {
                    mapKey = ((LocalQNameTest)key).getLocalName();
                } else {
                    mapKey = ((SpecificQNameTest)key).getStructuredQName().getLocalPart();
                }
                AtomicValue selector = ((JNode) gnode).getSelector();
                return selector != null && selector.getStringValue().equals(mapKey);
            } else {
                AtomicValue actual = ((JNode) gnode).getSelector();
                return actual instanceof QNameValue && key.matches(((QNameValue)actual).getStructuredQName());
            }
        } else {
            NodeInfo node = (NodeInfo)gnode;
            return node.getNodeKind() == nodeKind && key.matches(node.getQName());
        }
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    @Override
    public final double getDefaultPriority() {
        return 0.0;
    }

    /**
     * Extract a QNameTest (the strongest one possible) that must be satisfied by a node
     * if it is to satisfy this NodeTest
     *
     * @return the strongest possible QNameTest
     */

    public QNameTest getQNameTest() {
        return key;
    }

    /**
     * Get the name of the nodes matched by this nodetest, if it matches a specific name.
     * Return null if the node test matches nodes of more than one name
     */
    @Override
    public StructuredQName getMatchingNodeName() {
        return this.nodeKind == nodeKind && key instanceof SpecificQNameTest
                ? ((SpecificQNameTest)key).getStructuredQName()
                : null;
    }

    public String toString() {
        return key.toString();
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return key.hashCode();
    }

    /**
     * Determines whether two NameTests are equal
     */

    public boolean equals(Object other) {
        return other instanceof SelectorTest &&
                ((SelectorTest) other).key.equals(key);
    }
//
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
//        Optional<String> explanation = super.explainMismatch(item, th);
//        if (explanation.isPresent()) {
//            return explanation;
//        }
        return Optional.of("The node has the wrong name");
    }

    @Override
    public String toShortString() {
        return toString();
    }

    /**
     * Determine whether the content type (if present) is nillable
     *
     * @return true if the content test (when present) can match nodes that are nilled
     */
    @Override
    public boolean isNillable() {
        return true;
    }

    @Override
    public String export() {
        return "$ST(" + key.exportQNameTest() + "," + asNCName + "," + nodeKind + ")";
    }
}

