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
import net.sf.saxon.tree.iter.LookaheadIterator;

import java.util.EnumSet;


/**
 * A SubsequenceIterator selects a subsequence of a sequence
 */

public class SubsequenceIterator implements SequenceIterator, LastPositionFinder, LookaheadIterator {

    private SequenceIterator base;
    private int basePosition = 0;
    private int min;
    private int max;
    /*@Nullable*/ private Item nextItem = null;

    /**
     * Private Constructor: use the factory method instead!
     *
     * @param base An iteration of the items to be filtered
     * @param min  The position of the first item to be included (1-based)
     * @param max  The position of the last item to be included (1-based)
     * @throws XPathException if a dynamic error occurs
     */

    private SubsequenceIterator(SequenceIterator base, int min, int max) throws XPathException {
        this.base = base;
        this.min = min;
        if (min < 1) {
            min = 1;
        }
        this.max = max;
        if (max < min) {
            nextItem = null;
            return;
        }
        int i = 1;
        while (i++ <= min) {
            nextItem = base.next();
            basePosition++;
            if (nextItem == null) {
                break;
            }
        }
    }

    /**
     * Static factory method. Creates a SubsequenceIterator, unless for example the base Iterator is an
     * ArrayIterator, in which case it optimizes by creating a new ArrayIterator directly over the
     * underlying array. This optimization is important when doing recursion over a node-set using
     * repeated calls of <code>$nodes[position()&gt;1]</code>
     *
     * @param base An iteration of the items to be filtered
     * @param min  The position of the first item to be included (base 1)
     * @param max  The position of the last item to be included (base 1)
     * @return an iterator over the requested subsequence
     * @throws XPathException if a dynamic error occurs
     */

    public static <T extends Item> SequenceIterator make(SequenceIterator base, int min, int max) throws XPathException {
        if (base instanceof ArrayIterator) {
            return ((ArrayIterator<T>) base).makeSliceIterator(min, max);
        } else if (max == Integer.MAX_VALUE) {
            return TailIterator.make(base, min);
        } else if (base.getProperties().contains(SequenceIterator.Property.GROUNDED) && min > 4) {
            GroundedValue value = base.materialize();
            value = value.subsequence(min - 1, max - min + 1);
            return value.iterate();
        } else {
            return new SubsequenceIterator(base, min, max);
        }
    }

    /**
     * Test whether there are any more items available in the sequence
     */

    @Override
    public boolean hasNext() {
        return nextItem != null;
    }

    /**
     * Get the next item if there is one
     */

    @Override
    public Item next() throws XPathException {
        if (nextItem == null) {
            return null;
        }
        Item current = nextItem;
        if (basePosition < max) {
            nextItem = base.next();
            basePosition++;
        } else {
            nextItem = null;
            base.close();
        }
        return current;
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
        EnumSet<Property> p = EnumSetTool.intersect(base.getProperties(), EnumSet.of(Property.LAST_POSITION_FINDER));
        return EnumSetTool.union(p, EnumSet.of(Property.LOOKAHEAD));
    }

    /**
     * Get the last position (that is, the number of items in the sequence). This method is
     * non-destructive: it does not change the state of the iterator.
     * The result is undefined if the next() method of the iterator has already returned null.
     * This method must not be called unless the result of getProperties() on the iterator
     * includes the bit setting {@link net.sf.saxon.om.SequenceIterator.Property#LAST_POSITION_FINDER}
     */

    @Override
    public int getLength() throws XPathException {
        int lastBase = ((LastPositionFinder) base).getLength();
        int z = Math.min(lastBase, max);
        return Math.max(z - min + 1, 0);
    }

}

