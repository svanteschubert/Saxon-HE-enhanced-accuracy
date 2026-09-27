// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.jnode;

import net.sf.saxon.ma.map.AbstractFixedMap;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.transpile.CSharpSuppressWarnings;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.value.AtomicValue;

import java.util.Iterator;

/**
 * A child JNode that wraps an entry in a map
 */
public class JNodeForMapEntry extends ChildJNode implements SiblingCountingNode {

    private int serialNr = -1;

    /**
     * Create a JNode wrapping an entry in a map
     * @param parent the JNode whose content includes the containing map
     * @param position the 1-based position of the containing map within the content of the parent
     * @param selector the key of the relevant map entry within its containing map
     * @param value the value corresponding to this key
     * @param serialNr a sequential number representing the sibling position of this entry;
     *                 may be -1 if not initially known
     */
    public JNodeForMapEntry(JNode parent, int position, AtomicValue selector, GroundedValue value, int serialNr) {
        super(parent, position, selector, value);
        this.serialNr = serialNr;
    }

    /**
     * Get the index position of this node among its siblings (starting from 0)
     *
     * @return 0 for the first child, 1 for the second child, etc.
     */
    @Override
    public int getSiblingPosition() {
        if (serialNr == -1) {
            allocateSerialNr();
        }
        return serialNr;
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
        SequenceIterator iter = new FollowingSiblingIterator(this, serialNr);
        if (predicate != null && predicate != AnyGNode.TEST) {
            return Navigator.filter(iter, predicate);
        }
        return iter;
    }

    private static class FollowingSiblingIterator implements SequenceIterator {
        private final JNode startJNode;
        private final Iterator<KeyValuePair> pairs;
        private int serialNr;

        public FollowingSiblingIterator(JNode startJNode, int serialNr) {
            this.startJNode = startJNode;
            MapItem container = (MapItem) startJNode.getParent().getContent();
            this.pairs = container.followingKeyValuePairs(startJNode.getSelector());
            this.serialNr = serialNr;
        }

        /**
         * Get the next item in the sequence.
         */
        @Override
        @CSharpSuppressWarnings("UnsafeIteratorConversion")
        public Item next() {
            if (pairs.hasNext()) {
                KeyValuePair next = pairs.next();
                return new JNodeForMapEntry(startJNode.getParent(),
                                            startJNode.getPosition(),
                                            next.key(),
                                            next.value(),
                                            ++serialNr);
            }
            return null;
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
        SequenceIterator iter = new PrecedingSiblingIterator(this, serialNr);
        if (predicate != null && predicate != AnyGNode.TEST) {
            return Navigator.filter(iter, predicate);
        }
        return iter;
    }

    private static class PrecedingSiblingIterator implements SequenceIterator {
        private final JNode startJNode;
        private final Iterator<KeyValuePair> pairs;
        private int serialNr;

        public PrecedingSiblingIterator(JNode startJNode, int serialNr) {
            this.startJNode = startJNode;
            MapItem container = (MapItem) startJNode.getParent().getContent();
            this.pairs = container.precedingKeyValuePairs(startJNode.getSelector());
            this.serialNr = serialNr;
        }

        /**
         * Get the next item in the sequence.
         */
        @Override
        @CSharpSuppressWarnings("UnsafeIteratorConversion")
        public Item next() {
            if (pairs.hasNext()) {
                KeyValuePair next = pairs.next();
                return new JNodeForMapEntry(startJNode.getParent(),
                                            startJNode.getPosition(),
                                            next.key(),
                                            next.value(),
                                            --serialNr);
            }
            return null;
        }
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
        if (other.equals(this)) {
            return 0;
        }
        if (other instanceof RootJNode) {
            return -other.compareOrder(this);
        }
        if (getParent().getContent() == ((JNode)other.getParent()).getContent()) {
            // Comparing two entries in the same map
            if (serialNr == -1) {
                allocateSerialNr();
            }
            if (((JNodeForMapEntry)other).serialNr == -1) {
                ((JNodeForMapEntry) other).allocateSerialNr();
            }
            return serialNr - ((JNodeForMapEntry) other).serialNr;
        }
        return Navigator.compareOrder(this, (SiblingCountingNode)other);
    }

    public boolean equals(Object other) {
        int specVersion = ((MapItem)getParent().getContent()).getSpecVersion();
        return other instanceof JNodeForMapEntry
                && getParent().equals(((JNodeForMapEntry)other).getParent())
                && getSelector().asMapKey(specVersion)
                   .equals(((JNodeForMapEntry) other).getSelector().asMapKey(specVersion))
                && getPosition() == ((JNodeForMapEntry) other).getPosition();
    }

    public int hashCode() {
        int specVersion = ((MapItem) getParent().getContent()).getSpecVersion();
        return getParent().hashCode()
                ^ getSelector().asMapKey(specVersion).hashCode()
                ^ getPosition();
    }

    @CSharpSuppressWarnings("UnsafeIteratorConversion")
    protected void allocateSerialNr() {
        if (parent.getContent() instanceof AbstractFixedMap) {
            this.serialNr = ((AbstractFixedMap)parent.getContent()).getPosition(getSelector());
        }
        // TODO: same logic for extensible maps
        Iterator<KeyValuePair> iter = ((MapItem) parent.getContent()).precedingKeyValuePairs(getSelector());
        int count = 0;
        while (iter.hasNext()) {
            count++;
            KeyValuePair temp = iter.next();
        }
        this.serialNr = count;
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
        getParent().generateId(buffer);
        buffer.append("c").append(serialNr);
    }



}

