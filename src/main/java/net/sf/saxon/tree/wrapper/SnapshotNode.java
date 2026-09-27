////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;

/**
 * This class represents a node within a tree produced by the snapshot() function, as a virtual copy of
 * the relevant nodes in another tree. It specializes VirtualCopy, which implements a deep copy as
 * produced by the copy-of() function.
 */

public class SnapshotNode extends VirtualCopy implements NodeInfo {

    protected NodeInfo pivot; // a node in the source tree

    /**
     * Protected constructor: create a virtual copy of a node
     *
     * @param base  the node in the source tree to which the new node should correspond
     * @param pivot the pivot node is the node supplied as argument to the snapshot() function; the snapshot
     *              includes all ancestors of this node, and all descendants of this node (plus their attributes
     *              and namespaces)
     */

    protected SnapshotNode(NodeInfo base, NodeInfo pivot) {
        super(base, pivot.getRoot());
        this.pivot = pivot;
    }

    /**
     * Public factory method: apply the snapshot function to a node
     *
     * @param original the node to be copied
     * @return the snapshot.
     */

    public static SnapshotNode makeSnapshot(NodeInfo original) {
        SnapshotNode vc = new SnapshotNode(original, original);

        Configuration config = original.getConfiguration();
        VirtualTreeInfo doc = new VirtualTreeInfo(config);
        long docNr = config.getDocumentNumberAllocator().allocateDocumentNumber();
        doc.setDocumentNumber(docNr);
        doc.setCopyAccumulators(true);
        vc.tree = doc;
        doc.setRootNode(vc.getRoot());

        return vc;
    }

    /**
     * Wrap a node in a VirtualCopy.
     *
     * @param node the node to be wrapped
     * @return a virtual copy of the node
     */

    /*@NotNull*/
    @Override
    protected SnapshotNode wrap(NodeInfo node) {
        SnapshotNode vc = new SnapshotNode(node, pivot);
        vc.tree = tree;
        return vc;
    }

    /**
     * Get the string value of the item. The string value for a node below the pivot is the same
     * as the string value for the corresponding node in the source tree; the string value for a node above the pivot
     * is the same as the string value of the pivot. For attributes and namespaces the string value is the same
     * as in the original tree.
     *
     * @return the string value of the node, as a {@code UnicodeString}
     */

    @Override
    public UnicodeString getUnicodeStringValue() {
        if (Navigator.isAncestorOrSelf(original, pivot)) {
            return pivot.getUnicodeStringValue();
        } else {
            return original.getUnicodeStringValue();
        }
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     *
     * @return the parent of this node; null if this node has no parent
     */

    /*@Nullable*/
    @Override
    public NodeInfo getParent() {
        if (parent == null) {
            NodeInfo basep = (NodeInfo)original.getParent();
            if (basep == null) {
                return null;
            }
            parent = wrap(basep);
        }
        return parent;
    }

    /**
     * Get the root node of the tree containing this node
     *
     * @return the NodeInfo representing the top-level ancestor of this node.
     * This will not necessarily be a document node
     */
    @Override
    public NodeInfo getRoot() {
        return super.getRoot();
    }

    /**
     * Copy this node to a given outputter
     *
     * @param out         the Receiver to which the node should be copied
     * @param copyOptions a selection of the options defined in {@link net.sf.saxon.om.CopyOptions}
     * @param locationId  Identifies the location of the instruction
     */

    @Override
    public void copy(Receiver out, int copyOptions, Location locationId) throws XPathException {
        Navigator.copy(this, out, copyOptions, locationId);
    }

    /**
     * Get the typed value of this node
     *
     * @return the typed value.
     */

    @Override
    public AtomicSequence atomize() throws XPathException {
        switch (getNodeKind()) {
            case Type.ATTRIBUTE:
            case Type.TEXT:
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                return original.atomize();
            default:
                if (Navigator.isAncestorOrSelf(pivot, original)) {
                    return original.atomize();
                } else {
                    // Ancestors of the pivot node have type xs:anyType. The typed value is therefore the
                    // string value as an instance of xs:untypedAtomic
                    return StringValue.makeUntypedAtomic(pivot.getUnicodeStringValue());
                }
        }
    }


    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    @Override
    public boolean isId() {
        return original.isId();
    }

    /**
     * Determine whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute
     */

    @Override
    public boolean isIdref() {
        return original.isIdref();
    }

    /**
     * Determine whether the node has the is-nilled property
     *
     * @return true if the node has the is-nilled property
     */

    @Override
    public boolean isNilled() {
        return original.isNilled();
    }

    /**
     * Return the public identifier for the _current document event.
     * <p>The return value is the public identifier of the document
     * entity or of the external parsed entity in which the markup that
     * triggered the event appears.</p>
     *
     * @return A string containing the public identifier, or
     * null if none is available.
     * @see #getSystemId
     */
    /*@Nullable*/
    @Override
    public String getPublicId() {
        return original != null ? original.getPublicId() : null;
    }

    /**
     * Test whether this node is an ancestor of the pivot node
     */

    public boolean isAncestorOfPivot() {
        return !original.isSameNodeInfo(pivot) && Navigator.isAncestorOrSelf(original, pivot);
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
        if (isAncestorOfPivot()) {
            return Navigator.filteredSingleton(getChildOfAncestorNode(), predicate);
        } else {
            return super.iterateChildAxis(predicate);
        }
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
        if (isAncestorOfPivot()) {
            // We're on a node above the pivot node. This uses the child axis recursively
            SequenceIterator iter = new Navigator.DescendantIterator(this, false, true);
            return Navigator.filter(iter, predicate);
        } else {
            return super.iterateDescendantAxis(predicate);
        }
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
        if (isAncestorOfPivot()) {
            // We're on a node above the pivot node. This uses the child axis recursively
            SequenceIterator iter = new Navigator.DescendantIterator(this, true, true);
            return Navigator.filter(iter, predicate);
        } else {
            return super.iterateDescendantOrSelfAxis(predicate);
        }
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
        if (isAncestorOfPivot()) {
            return EmptyIterator.INSTANCE;
        } else {
            return super.iterateFollowingSiblingAxis(predicate);
        }
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
        if (isAncestorOfPivot()) {
            return EmptyIterator.INSTANCE;
        } else {
            return super.iteratePrecedingSiblingAxis(predicate);
        }
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
        if (isAncestorOfPivot()) {
            return EmptyIterator.INSTANCE;
        } else {
            return Navigator.filter(
                    new Navigator.FollowingIterator(this), predicate);
        }
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
        if (isAncestorOfPivot()) {
            return EmptyIterator.INSTANCE;
        } else {
            return Navigator.filter(
                    new Navigator.PrecedingIterator(this, false), predicate);
        }
    }

    /**
     * Get the child of this node assuming it is known to be above the pivot
     * @return the child of this node; or null in the case where this node is the parent of
     * the pivot node, and the pivot node is a namespace or attribute node
     */

    private NodeInfo getChildOfAncestorNode() {
        int pivotKind = pivot.getNodeKind();
        SnapshotNode p = wrap(pivot);
        if ((pivotKind == Type.ATTRIBUTE || pivotKind == Type.NAMESPACE) && p.getParent().isSameNodeInfo(this)) {
            return null;
        }
        while (true) {
            SnapshotNode q = (SnapshotNode)p.getParent();
            if (q == null) {
                throw new AssertionError();
            }
            if (q.isSameNodeInfo(this)) {
                return p;
            }
            p = q;
        }

    }


    @Override
    protected boolean isIncludedInCopy(NodeInfo sourceNode) {
        switch (sourceNode.getNodeKind()) {
            case Type.ATTRIBUTE:
            case Type.NAMESPACE:
                return isIncludedInCopy((NodeInfo)sourceNode.getParent());
            default:
                return Navigator.isAncestorOrSelf(pivot, sourceNode) || Navigator.isAncestorOrSelf(sourceNode, pivot);
        }
    }


}

