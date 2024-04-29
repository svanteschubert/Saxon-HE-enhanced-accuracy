////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.functions.Reverse;
import net.sf.saxon.om.*;
import net.sf.saxon.value.SequenceExtent;

import java.util.Arrays;
import java.util.List;

/**
 * ArrayIterator is used to enumerate items held in a Java array.
 * The items are always held in the correct sorted order for the sequence.
 *
 * The challenge here is getting the generics right, especially in a way
 * that works for C#, which is less tolerant of generic abuse. The solution
 * is to have a non-generic {@code ArrayIterator} class, with
 * {@code ArrayIterator.Of<T extends Item>} as a subclass. A further subtlety
 * is that we need an ArrayIterator of nodes to implement {@code AxisIterator},
 * so we have another subclass {@code ArrayIterator.OfNodes<N extends NodeInfo>}
 * for that purpose.
 */


public abstract class ArrayIterator implements SequenceIterator, FocusIterator,
        LastPositionFinder, LookaheadIterator, GroundedIterator, ReversibleIterator {

    protected int index;          // position in array of current item, zero-based
    // set equal to end+1 when all the items required have been read.
    protected int start;          // position of first item to be returned, zero-based
    protected int end;            // position of first item that is NOT returned, zero-based

    /**
     * Create a new ArrayIterator over the same items,
     * with a different start point and end point
     *
     * @param min the start position (1-based) of the new ArrayIterator
     *            relative to the original
     * @param max the end position (1-based) of the last item to be delivered
     *            by the new ArrayIterator, relative to the original. For example, min=2, max=3
     *            delivers the two items ($base[2], $base[3]). Set this to Integer.MAX_VALUE if
     *            there is no end limit.
     * @return an iterator over the items between the min and max positions
     */

    public abstract SequenceIterator makeSliceIterator(int min, int max);

    public boolean isActuallyGrounded() {
        return true;
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    @Override
    public int position() {
        return index - start;
    }

    @Override
    public int getLength() {
        return end - start;
    }

    /**
     * Parameterised subclass to accept items of a particular item type
     * @param <T> the item type of the items returned by the ArrayIterator
     */

    public static class Of <T extends Item> extends ArrayIterator {

        protected T[] items;

        public Of(T[] items) {
            this.items = items;
            start = 0;
            end = items.length;
            index = 0;
        }

        /**
         * Create an iterator over a range of an array. Note that the start position is zero-based
         *
         * @param items the array (of nodes or simple values) to be processed by
         *              the iterator
         * @param start the position of the first item to be processed
         *              (numbering from zero). Must be between zero and nodes.length-1; if not,
         *              undefined exceptions are likely to occur.
         * @param end   position of first item that is NOT returned, zero-based. Must be
         *              between 1 and nodes.length; if not, undefined exceptions are likely to occur.
         */

        public Of(T[] items, int start, int end) {
            this.items = items;
            this.end = end;
            this.start = start;
            index = start;
        }

        /**
         * Create a new ArrayIterator over the same items,
         * with a different start point and end point
         *
         * @param min the start position (1-based) of the new ArrayIterator
         *            relative to the original
         * @param max the end position (1-based) of the last item to be delivered
         *            by the new ArrayIterator, relative to the original. For example, min=2, max=3
         *            delivers the two items ($base[2], $base[3]). Set this to Integer.MAX_VALUE if
         *            there is no end limit.
         * @return an iterator over the items between the min and max positions
         */

        public SequenceIterator makeSliceIterator(int min, int max) {
            T[] items = getArray();
            int currentStart = getStartPosition();
            int currentEnd = getEndPosition();
            if (min < 1) {
                min = 1;
            }
            int newStart = currentStart + (min - 1);
            if (newStart < currentStart) {
                newStart = currentStart;
            }
            int newEnd = max == Integer.MAX_VALUE ? currentEnd : newStart + max - min + 1;
            if (newEnd > currentEnd) {
                newEnd = currentEnd;
            }
            if (newEnd <= newStart) {
                return EmptyIterator.getInstance();
            }
            return new Of<T>(items, newStart, newEnd);
        }

        /**
         * Test whether there are any more items
         *
         * @return true if there are more items
         */

        @Override
        public boolean hasNext() {
            return index < end;
        }

        /**
         * Get the next item in the array
         *
         * @return the next item in the array
         */

        /*@Nullable*/
        @Override
        public Item next() {
            if (index >= end) {
                index = end + 1;
                return null;
            }
            return items[index++];
        }

        @Override
        public Item current() {
            return index > start && index <= end ? items[index - 1] : null;
        }

        @Override
        public boolean supportsGetLength() {
            return true;
        }

        /**
         * Get the number of items in the part of the array being processed
         *
         * @return the number of items; equivalently, the position of the last
         * item
         */
        @Override
        public int getLength() {
            return end - start;
        }

        /**
         * Get the underlying array
         *
         * @return the underlying array being processed by the iterator
         */

        public T[] getArray() {
            return items;
        }

        /**
         * Get the initial start position
         *
         * @return the start position of the iterator in the array (zero-based)
         */

        public int getStartPosition() {
            return start;
        }

        /**
         * Get the end position in the array
         *
         * @return the position in the array (zero-based) of the first item not included
         * in the iteration
         */

        public int getEndPosition() {
            return end;
        }

        /**
         * Return a SequenceValue containing all the items in the sequence returned by this
         * SequenceIterator
         *
         * @return the corresponding SequenceValue
         */

        /*@NotNull*/
        @Override
        public GroundedValue materialize() {
            SequenceExtent.Of<T> seq;
            if (start == 0 && end == items.length) {
                seq = new SequenceExtent.Of<>(items);
            } else {
                List<T> sublist = Arrays.asList(items).subList(start, end);
                seq = new SequenceExtent.Of<>(sublist);
            }
            return seq.reduce();
        }

        @Override
        public GroundedValue getResidue() {
            SequenceExtent seq;
            if (start == 0 && index == 0 && end == items.length) {
                seq = new SequenceExtent.Of<>(items);
            } else {
                List<T> sublist = Arrays.asList(items).subList(start + index, end);
                seq = new SequenceExtent.Of<>(sublist);
            }
            return seq.reduce();
        }

        @Override
        public SequenceIterator getReverseIterator() {
            if (start == 0 && end == items.length) {
                return Reverse.reverseIterator(Arrays.asList(items));
            } else {
                List<T> sublist = Arrays.asList(items).subList(start, end);
                return Reverse.reverseIterator(sublist);
            }
        }

    }

    /**
     * ArrayIterator.OfNodes is a subclass of ArrayIterator where the array always
     * contains Nodes; it therefore implements the AxisIterator interface.
     */

    public static class OfNodes <N extends NodeInfo> extends ArrayIterator.Of<N> implements AxisIterator {
        public OfNodes(N[] nodes) {
            super(nodes);
        }
        public NodeInfo next() {
            return (NodeInfo)super.next();
        }
    }

}

