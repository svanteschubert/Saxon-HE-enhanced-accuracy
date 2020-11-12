////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;

import java.util.EnumSet;

/**
 * <tt>ValueTailIterator</tt> iterates over a base sequence starting at an element other than the first.
 * It is used in the case where the base sequence is "grounded", that is, it exists in memory and
 * supports efficient direct addressing.
 */

public class ValueTailIterator
        implements SequenceIterator, GroundedIterator, LookaheadIterator {

    private GroundedValue baseValue;
    private int start;  // zero-based
    private int pos = 0;

    /**
     * Construct a ValueTailIterator
     *
     * @param base  The items to be filtered
     * @param start The position of the first item to be included (zero-based)
     */

    public ValueTailIterator(GroundedValue base, int start) {
        baseValue = base;
        this.start = start;
        pos = 0;
    }

    @Override
    public Item next() throws XPathException {
        return baseValue.itemAt(start + pos++);
    }


    @Override
    public boolean hasNext() {
        return baseValue.itemAt(start + pos) != null;
    }

    /**
     * Return a Value containing all the items in the sequence returned by this
     * SequenceIterator. This should be an "in-memory" value, not a Closure.
     *
     * @return the corresponding Value
     */

    @Override
    public GroundedValue materialize() {
        if (start == 0) {
            return baseValue;
        } else {
            return baseValue.subsequence(start, Integer.MAX_VALUE);
        }
    }

    @Override
    public GroundedValue getResidue() {
        if (start == 0 && pos == 0) {
            return baseValue;
        } else {
            return baseValue.subsequence(start + pos, Integer.MAX_VALUE);
        }
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
        return EnumSet.of(Property.LOOKAHEAD, Property.GROUNDED);
    }
}

