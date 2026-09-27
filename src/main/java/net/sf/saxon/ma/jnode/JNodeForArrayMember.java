// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.jnode;

import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.value.Int64Value;

public class JNodeForArrayMember extends ChildJNode implements SiblingCountingNode {

    public JNodeForArrayMember(JNode parent, int position, Int64Value selector, GroundedValue value) {
        super(parent, position, selector, value);
    }

    /**
     * Get the index position of this node among its siblings (starting from 0)
     *
     * @return 0 for the first child, 1 for the second child, etc.
     */
    @Override
    public int getSiblingPosition() {
        return (int) ((Int64Value) selector).longValue() - 1;
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
        SequenceIterator iter = new FollowingSiblingIterator(this, getSiblingPosition() + 1);
        if (predicate != null && predicate != AnyGNode.TEST) {
            return Navigator.filter(iter, predicate);
        }
        return iter;
    }

    private static class FollowingSiblingIterator implements SequenceIterator {

        private final JNode startJNode;
        private final ArrayItem wholeArray;
        private int index; // 0-based position of next item to be returned

        public FollowingSiblingIterator(JNode startJNode, int startIndex) {
            this.startJNode = startJNode;
            this.wholeArray = (ArrayItem) startJNode.getParent().getContent();
            this.index = startIndex;
        }

        /**
         * Get the next item in the sequence. This method changes the state of the
         * iterator.
         */
        @Override
        public Item next() {
            if (index >= wholeArray.arrayLength()) {
                return null;
            }
            ;
            GroundedValue val = wholeArray.get(index);
            Int64Value selector = new Int64Value(index + 1);
            index++;
            return new JNodeForArrayMember(startJNode.getParent(),
                                           startJNode.getPosition(),
                                           selector,
                                           val);
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
        SequenceIterator iter = new PrecedingSiblingIterator(this, getSiblingPosition() - 1);
        if (predicate != null && predicate != AnyGNode.TEST) {
            return Navigator.filter(iter, predicate);
        }
        return iter;
    }

    private static class PrecedingSiblingIterator implements SequenceIterator {

        private final JNode startJNode;
        private final ArrayItem wholeArray;
        private int index; // 0-based position of next item to be returned

        public PrecedingSiblingIterator(JNode startJNode, int startIndex) {
            this.startJNode = startJNode;
            this.wholeArray = (ArrayItem) startJNode.getParent().getContent();
            this.index = startIndex;
        }

        /**
         * Get the next item in the sequence. This method changes the state of the
         * iterator.
         */
        @Override
        public Item next() {
            if (index < 0) {
                return null;
            };
            GroundedValue val = wholeArray.get(index);
            Int64Value selector = new Int64Value(index + 1);
            index--;
            return new JNodeForArrayMember(startJNode.getParent(),
                                    startJNode.getPosition(),
                                    selector,
                                    val);
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
        if (getParent().equals(other.getParent())) {
            // Comparing two members in the same array
            int c = Integer.compare(getPosition(), ((JNode) other).getPosition());
            if (c == 0) {
                return getSiblingPosition() - ((JNodeForArrayMember) other).getSiblingPosition();
            } else {
                return c;
            }
        }
        return Navigator.compareOrder(this, (SiblingCountingNode)other);
    }

    public boolean equals(Object other) {
        return other instanceof JNodeForArrayMember
                && getParent().equals(((JNodeForArrayMember)other).getParent())
                && getSiblingPosition() == (((JNodeForArrayMember) other).getSiblingPosition())
                && getPosition() == ((JNodeForArrayMember) other).getPosition();
    }

    public int hashCode() {
        return getParent().hashCode()
                ^ getSiblingPosition() << 12
                ^ getPosition() << 24;
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
        buffer.append("m").append(getSiblingPosition());
    }


}

