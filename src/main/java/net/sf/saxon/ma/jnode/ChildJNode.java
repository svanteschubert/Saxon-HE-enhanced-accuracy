// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.jnode;

import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.transpile.CSharpSuppressWarnings;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Int64Value;

import java.util.Iterator;

public abstract class ChildJNode extends JNode implements SiblingCountingNode {
    protected final JNode parent;
    protected final int position;
    protected final AtomicValue selector;
    protected final GroundedValue value;

    protected ChildJNode(JNode parent, int position, AtomicValue selector, GroundedValue value) {
        this.parent = parent;
        this.position = position;
        this.selector = selector;
        this.value = value;
    }

    @Override
    public GroundedValue getContent() {
        return value;
    }


    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public AtomicValue getSelector() {
        return selector;
    }

    @Override
    public JNode getParent() {
        return parent;
    }


//
//    public SequenceIterator getChildren() {
//        return new JNodeChildIterator(this);
//    }

    /**
     * Get an iterator over the child axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateChildAxis(NodePredicate predicate) {
        JNodeChildIterator iter = new JNodeChildIterator(this);
        if (predicate != null) {
            return Navigator.filter(iter, predicate);
        }
        return iter;
    }

    /**
     * Get an iterator over the following-sibling axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public abstract SequenceIterator iterateFollowingSiblingAxis(NodePredicate predicate);

    /**
     * Get an iterator over the preceding-sibling axis, starting at this node; the nodes will
     * be in reverse document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public abstract SequenceIterator iteratePrecedingSiblingAxis(NodePredicate predicate);


//    public boolean equals(Object other) {
//        return other instanceof ChildJNode
//                && getParent().equals(((ChildJNode)other).getParent())
//                && getSelector().asMapKey().equals(((ChildJNode) other).getSelector().asMapKey())
//                && getPosition() == ((ChildJNode) other).getPosition();
//    }
//
//    public int hashCode() {
//        return getParent().hashCode()
//                ^ getSelector().asMapKey().hashCode()
//                ^ getPosition();
//    }

    /**
     * Iterator over the children of this JNode
     */

    protected static class JNodeChildIterator implements SequenceIterator {

        private final ChildJNode origin;
        private final SequenceIterator valueIterator;
        private Iterator<KeyValuePair> mapEntryIterator;
        private Iterator<GroundedValue> arrayEntryIterator;
        private int position = 0;
        private int arrayIndex = 0;
        private int serialNr = 0;

        public JNodeChildIterator(ChildJNode origin) {
            this.origin = origin;
            valueIterator = origin.getContent().iterate();
        }

        /**
         * Get the next item in the sequence.
         */
        @Override
        @CSharpSuppressWarnings("UnsafeIteratorConversion")
        public GNode next() {
            if (mapEntryIterator != null) {
                if (mapEntryIterator.hasNext()) {
                    KeyValuePair nextItem = mapEntryIterator.next();
                    return new JNodeForMapEntry(
                            origin, position, nextItem.key(), nextItem.value(), serialNr++);
                } else {
                    mapEntryIterator = null;
                }
            }
            if (arrayEntryIterator != null) {
                if (arrayEntryIterator.hasNext()) {
                    GroundedValue nextMember = arrayEntryIterator.next();
                    return new JNodeForArrayMember(
                            origin, position, Int64Value.makeIntegerValue(++arrayIndex), nextMember);
                } else {
                    arrayEntryIterator = null;
                }
            }
            while (true) {
                position++;
                Item nextInValue = valueIterator.next();
                if (nextInValue == null) {
                    return null;
                }
                if (nextInValue instanceof ArrayItem) {
                    arrayEntryIterator = ((ArrayItem) nextInValue).members().iterator();
                    arrayIndex = 0;
                    return next();
                }
                if (nextInValue instanceof MapItem) {
                    mapEntryIterator = ((MapItem) nextInValue).keyValuePairs().iterator();
                    return next();
                }
            }
        }
    }


}

