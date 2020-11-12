////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.tree.iter.ReversibleIterator;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;

import java.util.EnumSet;

/**
 * Iterator that produces numeric values in a monotonic sequence,
 * ascending or descending. Although a range expression (N to M) is always
 * in ascending order, applying the reverse() function will produce
 * a RangeIterator that works in descending order.
 */

public class ReverseRangeIterator implements AtomicIterator<IntegerValue>,
        ReversibleIterator,
        LastPositionFinder,
        LookaheadIterator {

    long start;
    long currentValue;
    long limit;

    /**
     * Create an iterator over a range of integers in monotonic descending order
     *
     * @param start the first integer to be delivered (the highest in the range)
     * @param end   the last integer to be delivered (the lowest in the range). Must be &lt;= start
     * @throws XPathException if the values are inconsistent or if the length is greater than 2^31
     */

    public ReverseRangeIterator(long start, long end) throws XPathException {
        if (start - end > Integer.MAX_VALUE) {
            throw new XPathException("Saxon limit on sequence length exceeded (2^31)", "XPDY0130");
        }
        this.start = start;
        currentValue = start + 1;
        limit = end;
    }

    @Override
    public boolean hasNext() {
        return currentValue > limit;
    }

    /*@Nullable*/
    @Override
    public IntegerValue next() {
        if (--currentValue < limit) {
            return null;
        }
        return Int64Value.makeIntegerValue(currentValue);
    }

    @Override
    public int getLength() {
        return (int) ((start - limit) + 1);
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
        return EnumSet.of(Property.LOOKAHEAD, Property.LAST_POSITION_FINDER);
    }

    @Override
    public AtomicIterator getReverseIterator() {
        return new RangeIterator(limit, start);
    }


}

