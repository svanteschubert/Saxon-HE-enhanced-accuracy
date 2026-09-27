////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.om.GNode;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.nodetest.NodePredicate;

import java.util.function.Function;


/**
 * A RebasedNode is a view of a node, in a virtual tree that maps the base URI and/or
 * system ID to new values
 */

public class RebasedNode extends AbstractVirtualNode implements WrappingFunction {

    protected RebasedNode() {
    }

    /**
     * This constructor is protected: nodes should be created using the makeWrapper
     * factory method
     *
     * @param node   The node to be wrapped
     * @param parent The RebasedNode that wraps the parent of this node
     */

    protected RebasedNode(NodeInfo node, RebasedNode parent) {
        this.node = node;
        this.parent = parent;
    }

    /**
     * Factory method to wrap a node with a wrapper that implements the Saxon
     * NodeInfo interface.
     *
     * @param node       The underlying node
     * @param docWrapper The wrapper for the document node (must be supplied)
     * @param parent     The wrapper for the parent of the node (null if unknown)
     * @return The new wrapper for the supplied node
     */

    /*@NotNull*/
    public static RebasedNode makeWrapper(NodeInfo node,
                                          RebasedDocument docWrapper,
                                          RebasedNode parent) {
        RebasedNode wrapper = new RebasedNode(node, parent);
        wrapper.docWrapper = docWrapper;
        return wrapper;
    }

    /**
     * Factory method to wrap a node with a VirtualNode
     *
     * @param node   The underlying node
     * @param parent The wrapper for the parent of the node (null if unknown)
     * @return The new wrapper for the supplied node
     */

    /*@NotNull*/
    @Override
    public RebasedNode makeWrapper(NodeInfo node, VirtualNode parent) {
        RebasedNode wrapper = new RebasedNode(node, (RebasedNode) parent);
        wrapper.docWrapper = this.docWrapper;
        return wrapper;
    }

    private Function<NodeInfo, String> getBaseUriMappingFunction() {
        return ((RebasedDocument)docWrapper).getBaseUriMapper();
    }

    private Function<NodeInfo, String> getSystemIdMappingFunction() {
        return ((RebasedDocument) docWrapper).getSystemIdMapper();
    }

    /**
     * Get the Base URI for the node, that is, the URI used for resolving a relative URI contained
     * in the node.
     */
    @Override
    public String getBaseURI() {
        return getBaseUriMappingFunction().apply(node);
    }

    /**
     * Get the System ID for the node.
     *
     * @return the System Identifier of the entity in the source document containing the node,
     * or null if not known. Note this is not the same as the base URI: the base URI can be
     * modified by xml:base, but the system ID cannot.
     */
    @Override
    public String getSystemId() {
        return getSystemIdMappingFunction().apply(node);
    }

    /**
     * Determine whether this is the same node as another node.
     * <p>Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)</p>
     *
     * @return true if this Node object and the supplied Node object represent the
     *         same node in the tree.
     */

    public boolean equals(Object other) {
        return other instanceof RebasedNode && node.equals(((RebasedNode) other).node);
    }

    /**
     * The hashCode() method obeys the contract for hashCode(): that is, if two objects are equal
     * (represent the same node) then they must have the same hashCode()
     */
    @Override
    public int hashCode() {
        return node.hashCode();
    }


    /**
     * Determine the relative position of this node and another node, in document order.
     * The other node will always be in the same document.
     *
     * @param other The other node, whose position is to be compared with this node
     * @return -1 if this node precedes the other node, +1 if it follows the other
     *         node, or 0 if they are the same node. (In this case, isSameNode() will always
     *         return true, and the two nodes will produce the same result for generateId())
     */

    @Override
    public int compareOrder(/*@NotNull*/ GNode other) {
        if (other instanceof RebasedNode) {
            return node.compareOrder(((RebasedNode) other).node);
        } else {
            return node.compareOrder(other);
        }
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     */

    /*@Nullable*/
    @Override
    public NodeInfo getParent() {
        if (parent == null) {
            NodeInfo realParent = (NodeInfo)node.getParent();
            if (realParent != null) {
                parent = makeWrapper(realParent, (RebasedDocument) docWrapper, null);
            }
        }
        return parent;
    }

    /**
     * Get an iterator over the ancestor axis, starting at this node; the nodes will
     * be in reverse document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateAncestorAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iterateAncestorAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the ancestor-or-self axis, starting at this node; the nodes will
     * be in reverse document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateAncestorOrSelfAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iterateAncestorOrSelfAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the attribute axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateAttributeAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iterateAttributeAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the child axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateChildAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iterateChildAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the descendant axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateDescendantAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iterateDescendantAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the descendant-or-self axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateDescendantOrSelfAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iterateDescendantOrSelfAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the following axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateFollowingAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iterateFollowingAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the following-or-self axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateFollowingOrSelfAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iterateFollowingOrSelfAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the following-sibling axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateFollowingSiblingAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iterateFollowingSiblingAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the following-sibling-or-self axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateFollowingSiblingOrSelfAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iterateFollowingSiblingOrSelfAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the namespace axis, starting at this node; the nodes will
     * be in reverse document order. The default implementation must be overridden
     * for classes that implement element nodes.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateNamespaceAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iterateNamespaceAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the parent axis, starting at this node; returns zero or one nodes
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateParentAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iterateParentAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the preceding axis, starting at this node; the nodes will
     * be in reverse document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iteratePrecedingAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iteratePrecedingAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the preceding-or-self axis, starting at this node; the nodes will
     * be in reverse document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iteratePrecedingOrSelfAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iteratePrecedingOrSelfAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the preceding-sibling axis, starting at this node; the nodes will
     * be in reverse document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iteratePrecedingSiblingAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iteratePrecedingSiblingAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the preceding-sibling-or-self axis, starting at this node; the nodes will
     * be in reverse document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iteratePrecedingSiblingOrSelfAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iteratePrecedingSiblingOrSelfAxis(predicate), this, null);
    }

    /**
     * Get an iterator over the self axis, starting at this node; there will be zero
     * or one nodes.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateSelfAxis(NodePredicate predicate) {
        return new WrappingIterator(node.iterateSelfAxis(predicate), this, null);
    }


}

