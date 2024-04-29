////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.tree.iter.*;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerRange;
import net.sf.saxon.value.IntegerValue;

/**
 * Iterator that produces numeric values in a monotonic sequence,
 * ascending or descending. Although a range expression (N to M) is always
 * in ascending order, applying the reverse() function will produce
 * a RangeIterator that works in descending order.
 */

public class DescendingRangeIterator extends RangeIterator implements AtomicIterator,
        ReversibleIterator,
        LastPositionFinder,
        LookaheadIterator {

    long start;
    long step;
    long currentValue;
    long limit;

    /**
     * Create an iterator over a range of integers in monotonic descending order
     *
     * @param start the first integer to be delivered (the highest in the range)
     * @param step the difference between successive values, supplied as a positive integer
     * @param end   the last integer to be delivered (the lowest in the range). Must be &lt;= start
     */

    public DescendingRangeIterator(long start, long step, long end) {
        assert step > 0;
        assert start - end <= Integer.MAX_VALUE;
//        if (start - end > Integer.MAX_VALUE) {
//            throw new UncheckedXPathException("Saxon limit on sequence length exceeded (2^31)", "XPDY0130");
//        }
        this.start = start;
        this.step = step;
        currentValue = start + step;
        limit = end;
        if (step != 1L) {
            limit = start + ((end - start) / step) * step;
        }
    }

    public boolean isActuallyGrounded() {
        return true;
    }

    /**
     * Return a GroundedValue containing all the items in the sequence returned by this
     * SequenceIterator. This should be an "in-memory" value, not a Closure. This method
     * does not change the state of the iterator (in particular, it does not consume the
     * iterator).
     *
     * @return the corresponding Value
     * @throws UncheckedXPathException in the cases of subclasses (such as the iterator over a MemoClosure)
     *                        which cause evaluation of expressions while materializing the value.
     */
    @Override
    public GroundedValue materialize() {
        return new IntegerRange(start, -step, limit);
    }

    /**
     * Return a GroundedValue containing all the remaining items in the sequence returned by this
     * SequenceIterator, starting at the current position. This should be an "in-memory" value, not a
     * Closure. This method does not change the state of the iterator (in particular, it does not
     * consume the iterator).
     *
     * @return the corresponding Value
     * @throws UncheckedXPathException in the cases of subclasses (such as the iterator over a MemoClosure)
     *                        which cause evaluation of expressions while materializing the value.
     */
    @Override
    public GroundedValue getResidue() {
        return new IntegerRange(currentValue, -step, limit);
    }

    @Override
    public IntegerValue getFirst() {
        return new Int64Value(start);
    }

    @Override
    public IntegerValue getLast() {
        return new Int64Value(limit);
    }

    @Override
    public IntegerValue getMin() {
        return new Int64Value(limit);
    }

    @Override
    public IntegerValue getMax() {
        return new Int64Value(start);
    }

    /**
     * Get the increment between successive values. For a descending iterator this will be negatiive value.
     *
     * @return the increment between successive values
     */
    @Override
    public IntegerValue getStep() {
        return new Int64Value(-step);
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    @Override
    public boolean hasNext() {
        return currentValue - step >= limit;
    }

    /*@Nullable*/
    @Override
    public IntegerValue next() {
        currentValue -= step;
        if (currentValue < limit) {
            return null;
        }
        return Int64Value.makeIntegerValue(currentValue);
    }

    @Override
    public boolean supportsGetLength() {
        return true;
    }

    @Override
    public int getLength() {
        return (int) ((start - limit) + 1);
    }

    @Override
    public AtomicIterator getReverseIterator() {
        return new AscendingRangeIterator(start, step, limit);
    }


}

