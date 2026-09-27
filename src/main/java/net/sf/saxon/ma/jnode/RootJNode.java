// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.jnode;

import net.sf.saxon.ma.MapOrArray;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.transpile.CSharpSuppressWarnings;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Int64Value;

import java.util.Iterator;

/**
 * A root JNode that wraps a map or array
 */
public class RootJNode extends JNode {

    private final MapOrArray value;

    /**
     * Construct a JNode that wraps a given map or array
     * @param value the map or array to be wrapped
     */
    public RootJNode(MapOrArray value) {
        // TODO: avoid multiple JNodes for the same map or array
        this.value = value;
    }

    public static RootJNode obtainRootJNode(MapOrArray value) {
        return value.obtainRootJNode();
    }

    /**
     * Get the value/content property of the JNode
     * @return the wrapped value/content
     */
    @Override
    public MapOrArray getContent() {
        return value;
    }

    /**
     * Get the position property of the JNode
     * @return always absent for a root JNode: return -1
     */
    @Override
    public int getPosition() {
        return -1;
    }

    /**
     * Get the selector property of the JNode
     * @return always absent for a root JNode: return null
     */
    @Override
    public AtomicValue getSelector() {
        return null;
    }

    /**
     * Get the parent property of the JNode
     * @return always absent for a root JNode: return null
     */
    @Override
    public JNode getParent() {
        return null;
    }

    /**
     * Get the children of the JNode
     * @return a sequence of JNodes that wrap the array members or map entries
     */

    public SequenceIterator getChildren() {
        if (value instanceof MapItem) {
            return new JNodeMapIterator(this);
        } else {
            return new JNodeArrayIterator(this);
        }
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
        if (predicate == null || predicate == NodeKindType.ELEMENT /* meaning child::*  */ ) {
            return getChildren();
        }
        return Navigator.filter(getChildren(), predicate);
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
        return EmptyIterator.INSTANCE;
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
        return EmptyIterator.INSTANCE;
    }

    /**
     * Compare document order of this node against another node.
     * <p>The other node must always be in the same tree; the effect of calling this method
     * when the two nodes are in different trees is undefined. To obtain a global ordering
     * of nodes, the application should first compare the result of getDocumentNumber(),
     * and only if the document number is the same should compareOrder() be called.</p>
     *
     * @param other the other node
     * @return -1 if this node precedes the other, 0 if they are the same node, +1 if
     * this node follows the other
     */
    @Override
    public int compareOrder(GNode other) {
        if (!(other instanceof JNode)) {
            throw new IllegalArgumentException("Trying to compare JNodes and XNodes");
        }
        if (other instanceof RootJNode) {
            return System.identityHashCode(this) - System.identityHashCode(other);
        }
        RootJNode otherRoot = ((JNode)other).getRoot();
        if (otherRoot == this) {
            return -1;
        }
        return compareOrder(otherRoot);
    }

    /**
     * Construct a character string that uniquely identifies this node.
     * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer a buffer which will be updated to hold a string
     *               that uniquely identifies this node, across all documents.
     * @since 8.7
     * <p>Changed in Saxon 8.7 to generate the ID value in a client-supplied buffer</p>
     */
    @Override
    public void generateId(StringBuilder buffer) {
        buffer.append("JR").append(System.identityHashCode(this));
    }

    private static class JNodeMapIterator implements SequenceIterator {

        private final RootJNode origin;
        private final Iterator<KeyValuePair> mapEntryIterator;
        private int serialNr = 0;

        public JNodeMapIterator(RootJNode origin) {
            this.origin = origin;
            mapEntryIterator = ((MapItem)origin.getContent()).keyValuePairs().iterator();
        }

        /**
         * Get the next item in the sequence.
         */
        @Override
        @CSharpSuppressWarnings("UnsafeIteratorConversion")
        public GNode next() {
            if (mapEntryIterator.hasNext()) {
                KeyValuePair nextItem = mapEntryIterator.next();
                return new JNodeForMapEntry(origin, 1, nextItem.key(), nextItem.value(), serialNr++);
            } else {
                return null;
            }
        }
    }

    private static class JNodeArrayIterator implements SequenceIterator {

        private final RootJNode origin;
        private final Iterator<GroundedValue> arrayEntryIterator;
        private int index = 1;

        public JNodeArrayIterator(RootJNode origin) {
            this.origin = origin;
            arrayEntryIterator = ((ArrayItem) origin.getContent()).members().iterator();
        }

        /**
         * Get the next item in the sequence.
         */
        @Override
        @CSharpSuppressWarnings("UnsafeIteratorConversion")
        public GNode next() {
            if (arrayEntryIterator.hasNext()) {
                GroundedValue nextItem = arrayEntryIterator.next();
                JNodeForArrayMember j = new JNodeForArrayMember(origin, 1, Int64Value.makeIntegerValue(index), nextItem);
                index++;
                return j;
            } else {
                return null;
            }
        }
    }

}

