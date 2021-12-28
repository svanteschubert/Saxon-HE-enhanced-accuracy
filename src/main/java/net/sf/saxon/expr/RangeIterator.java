////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.*;
import net.sf.saxon.value.BigIntegerValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerRange;
import net.sf.saxon.value.IntegerValue;

import java.util.EnumSet;

/**
 * An Iterator that produces numeric values in a monotonic sequence,
 * ascending or descending. Although a range expression (N to M) is always
 * in ascending order, applying the reverse() function will produce
 * a RangeIterator that works in descending order.
 */

public class RangeIterator implements AtomicIterator<IntegerValue>,
        ReversibleIterator,
        LastPositionFinder,
        LookaheadIterator,
        GroundedIterator {

    long start;
    long currentValue;
    long limit;

    public static AtomicIterator<IntegerValue> makeRangeIterator(IntegerValue start, IntegerValue end) throws XPathException {
        if (start == null || end == null) {
            return EmptyIterator.ofAtomic();
        } else {
            if (start.compareTo(end) > 0) {
                return EmptyIterator.ofAtomic();
            }
            if (start instanceof BigIntegerValue || end instanceof BigIntegerValue) {
                return new BigRangeIterator(start.asBigInteger(), end.asBigInteger());
            } else {
                long startVal = start.longValue();
                long endVal = end.longValue();
                if (endVal - startVal > Integer.MAX_VALUE) {
                    throw new XPathException("Saxon limit on sequence length exceeded (2^31)", "XPDY0130");
                }
                return new RangeIterator(startVal, endVal);
            }
        }
    }

    /**
     * Create an iterator over a range of monotonically increasing integers
     *
     * @param start the first integer in the sequence
     * @param end   the last integer in the sequence. Must be &gt;= start.
     */

    public RangeIterator(long start, long end) {
        this.start = start;
        currentValue = start - 1;
        limit = end;
    }

    @Override
    public boolean hasNext() {
        return currentValue < limit;
    }

    /*@Nullable*/
    @Override
    public IntegerValue next() {
        if (++currentValue > limit) {
            return null;
        }
        return Int64Value.makeIntegerValue(currentValue);
    }

    @Override
    public int getLength() {
        return (int) ((limit - start) + 1);
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
        return EnumSet.of(Property.LOOKAHEAD, Property.LAST_POSITION_FINDER, Property.GROUNDED);
    }

    @Override
    public AtomicIterator<IntegerValue> getReverseIterator() {
        try {
            return new ReverseRangeIterator(limit, start);
        } catch (XPathException err) {
            throw new AssertionError(err);
        }
    }

    /**
     * Return a Value containing all the items in the sequence returned by this
     * SequenceIterator. This should be an "in-memory" value, not a Closure.
     *
     * @return the corresponding Value
     */

    @Override
    public GroundedValue materialize() {
        return new IntegerRange(start, limit);
    }

    @Override
    public GroundedValue getResidue() {
        return new IntegerRange(currentValue, limit);
    }
}

