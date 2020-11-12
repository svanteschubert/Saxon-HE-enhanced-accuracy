////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.EnumSetTool;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;

import java.util.EnumSet;

/**
 * TailIterator iterates over a base sequence starting at an element other than the first.
 * The base sequence is represented by an iterator which is consumed in the process
 */

public class TailIterator
        implements SequenceIterator, LastPositionFinder, LookaheadIterator {

    private SequenceIterator base;
    private int start;

    /**
     * Private constructor: external callers should use the public factory method.
     * Create a TailIterator, an iterator that starts at position N in a sequence and iterates
     * to the end of the sequence
     *
     * @param base  the base sequence of which we want to select the tail. Unusually, this iterator
     *              should be supplied pre-positioned so that the next call on next() returns the first item to
     *              be returned by the TailIterator
     * @param start the index of the first required item in the sequence, starting from one. To
     *              include all items in the sequence except the first, set start = 2. This value is used only
     *              when cloning the iterator or when calculating the value of last().
     */

    private TailIterator(SequenceIterator base, int start) {
        this.base = base;
        this.start = start;
    }

    /**
     * Static factory method. Creates a TailIterator, unless the base Iterator is an
     * ArrayIterator, in which case it optimizes by creating a new ArrayIterator directly over the
     * underlying array. This optimization is important when doing recursion over a node-set using
     * repeated calls of <code>$nodes[position()&gt;1]</code>
     *
     * @param base  An iteration of the items to be filtered
     * @param start The position of the first item to be included (base 1). If &lt;= 1, the whole of the
     *              base sequence is returned
     * @return an iterator over the items in the sequence from the start item to the end of the sequence.
     *         The returned iterator will not necessarily be an instance of this class.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs
     */

    public static <T extends Item> SequenceIterator make(SequenceIterator base, int start) throws XPathException {
        if (start <= 1) {
            return base;
        } else if (base instanceof ArrayIterator) {
            return ((ArrayIterator) base).makeSliceIterator(start, Integer.MAX_VALUE);
        } else if (base.getProperties().contains(SequenceIterator.Property.GROUNDED)) {
            GroundedValue value = base.materialize();
            if (start > value.getLength()) {
                return EmptyIterator.emptyIterator();
            } else {
                return new ValueTailIterator(value, start - 1);
            }
        } else {
            // discard the first n-1 items from the underlying iterator
            for (int i = 0; i < start - 1; i++) {
                Item b = base.next();
                if (b == null) {
                    return EmptyIterator.emptyIterator();
                }
            }
            return new TailIterator(base, start);
        }
    }


    @Override
    public Item next() throws XPathException {
        return base.next();
    }

    @Override
    public boolean hasNext() {
        return ((LookaheadIterator) base).hasNext();
    }

    @Override
    public int getLength() throws XPathException {
        int bl = ((LastPositionFinder) base).getLength() - start + 1;
        return bl > 0 ? bl : 0;
    }

    @Override
    public void close() {
        base.close();
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link net.sf.saxon.om.SequenceIterator.Property#GROUNDED}, {@link net.sf.saxon.om.SequenceIterator.Property#LAST_POSITION_FINDER},
     *         and {@link net.sf.saxon.om.SequenceIterator.Property#LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    @Override
    public EnumSet<Property> getProperties() {
        return EnumSetTool.intersect(
                base.getProperties(),
                EnumSet.of(Property.LAST_POSITION_FINDER, Property.LOOKAHEAD));
    }
}

