// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.tree.NamespaceNode;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.IncrementalIterator;
import net.sf.saxon.tree.iter.PrependIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.Type;

/**
 * A generalized node: that is, an XNode or JNode. New in 4.0. The GNode class itself is abstract,
 * as is the underlying XDM type: every GNode is either an XNode or a JNode.
 *
 * <p>This class contains default implementations of all the axes except parent, child,
 * following-sibling, preceding-sibling, attribute, and namespace.</p>
 */

public interface GNode extends Item {

    int getNodeKind();

    /**
     * Get an iterator over the ancestor axis, starting at this node; the nodes will
     * be in reverse document order.
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iterateAncestorAxis(NodePredicate predicate) {
        IncrementalIterator iter = new IncrementalIterator(this, GNode::getParent);
        return predicate == null ? iter : Navigator.filter(iter, predicate);
    }

    /**
     * Get an iterator over the ancestor-or-self axis, starting at this node; the nodes will
     * be in reverse document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iterateAncestorOrSelfAxis(NodePredicate predicate) {
        SequenceIterator ancestors = iterateAncestorAxis(predicate);
        return Navigator.orSelf(this, ancestors, predicate);
    }

    /**
     * Get an iterator over the attribute axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iterateAttributeAxis(NodePredicate predicate) {
        return EmptyIterator.INSTANCE;
    }

    /**
     * Get an iterator over the child axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    SequenceIterator iterateChildAxis(NodePredicate predicate);

    /**
     * Get an iterator over the descendant axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iterateDescendantAxis(NodePredicate predicate) {
        SequenceIterator desc = new Navigator.DescendantIterator(this, false, true);
        return predicate != null ? Navigator.filter(desc, predicate) : desc;
    }

    /**
     * Get an iterator over the descendant-or-self axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iterateDescendantOrSelfAxis(NodePredicate predicate) {
        SequenceIterator desc = new Navigator.DescendantIterator(this, true, true);
        return predicate != null ? Navigator.filter(desc, predicate) : desc;
    }

    /**
     * Get an iterator over the following axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iterateFollowingAxis(NodePredicate predicate) {
        SequenceIterator foll = new Navigator.FollowingIterator(this);
        return predicate != null ? Navigator.filter(foll, predicate) : foll;
    }

    /**
     * Get an iterator over the following-or-self axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iterateFollowingOrSelfAxis(NodePredicate predicate) {
        SequenceIterator foll = iterateFollowingAxis(predicate);
        return Navigator.orSelf(this, foll, predicate);
    }

    /**
     * Get an iterator over the following-sibling axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    SequenceIterator iterateFollowingSiblingAxis(NodePredicate predicate);

    /**
     * Get an iterator over the following-sibling-or-self axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iterateFollowingSiblingOrSelfAxis(NodePredicate predicate) {
        SequenceIterator foll = iterateFollowingSiblingAxis(predicate);
        return Navigator.orSelf(this, foll, predicate);
    }

    /**
     * Get an iterator over the namespace axis, starting at this node; the nodes will
     * be in reverse document order. The default implementation must be overridden
     * for classes that implement element nodes.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iterateNamespaceAxis(NodePredicate predicate) {
        if (getNodeKind() == Type.ELEMENT) {
            return NamespaceNode.makeIterator((NodeInfo)this, predicate);
        }
        return EmptyIterator.INSTANCE;
    }

    /**
     * Get an iterator over the parent axis, starting at this node; returns zero or one nodes
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iterateParentAxis(NodePredicate predicate) {
        return Navigator.filteredSingleton(getParent(), predicate);
    }

    /**
     * Get an iterator over the preceding axis, starting at this node; the nodes will
     * be in reverse document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iteratePrecedingAxis(NodePredicate predicate) {
        SequenceIterator prec = new Navigator.PrecedingIterator(this, false);
        return predicate != null ? Navigator.filter(prec, predicate) : prec;
    }

    /**
     * Get an iterator over the preceding-or-self axis, starting at this node; the nodes will
     * be in reverse document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iteratePrecedingOrSelfAxis(NodePredicate predicate) {
        SequenceIterator prec = iteratePrecedingAxis(predicate);
        return predicate == null || predicate.test(this)
                ? new PrependIterator(this, prec)
                : prec;
    }

    /**
     * Get an iterator over the preceding-sibling axis, starting at this node; the nodes will
     * be in reverse document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    SequenceIterator iteratePrecedingSiblingAxis(NodePredicate predicate);

    /**
     * Get an iterator over the preceding-sibling-or-self axis, starting at this node; the nodes will
     * be in reverse document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iteratePrecedingSiblingOrSelfAxis(NodePredicate predicate) {
        SequenceIterator prec = iteratePrecedingSiblingAxis(predicate);
        return predicate == null || predicate.test(this)
                ? new PrependIterator(this, prec)
                : prec;
    }

    /**
     * Get an iterator over the self axis, starting at this node; there will be zero
     * or one nodes.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    default SequenceIterator iterateSelfAxis(NodePredicate predicate) {
        return Navigator.filteredSingleton(this, predicate);
    }

    /**
     * Get the parent of this node
     * @return the parent node if there is one, or null otherwise
     */

    GNode getParent();

    /**
     * Ask whether the node has children
     * @return true if the node has children
     */

    boolean hasChildNodes();

    /**
     * Compare document order of this node against another node.
     *  <p>The other node must always be in the same tree; the effect of calling this method
     *  when the two nodes are in different trees is undefined. To obtain a global ordering
     *  of nodes, the application should first compare the result of getDocumentNumber(),
     *  and only if the document number is the same should compareOrder() be called.</p>
     *
     * @param other the other node
     * @return -1 if this node precedes the other, 0 if they are the same node, +1 if
     * this node follows the other
     */

    int compareOrder(GNode other);

    /**
     * Construct a character string that uniquely identifies this node.
     * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer a buffer which will be updated to hold a string
     *               that uniquely identifies this node, across all documents.
     * @since 8.7
     * <p>Changed in Saxon 8.7 to generate the ID value in a client-supplied buffer</p>
     */

    void generateId(StringBuilder buffer);

}

