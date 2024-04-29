////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.*;
import net.sf.saxon.value.*;

/**
 * An Iterator that produces numeric values in a monotonic sequence,
 * ascending or descending. Applying the reverse() function will produce
 * a DescendingRangeIterator.
 */

public class AscendingRangeIterator extends RangeIterator implements AtomicIterator,
        ReversibleIterator,
        LastPositionFinder,
        LookaheadIterator,
        GroundedIterator {

    long start;
    long step;
    long currentValue;
    long limit;

    // TODO: the step is currently always +1.

    public static AtomicIterator makeRangeIterator(IntegerValue start, IntegerValue step, IntegerValue end) throws XPathException {
        if (start == null || step == null || end == null) {
            return EmptyIterator.ofAtomic();
        } else {
            int direction = step.compareTo(Int64Value.ZERO);
            if (direction == 0 || start.compareTo(end) > 0) {
                return EmptyIterator.ofAtomic();
            }
            if (start instanceof BigIntegerValue || step instanceof BigIntegerValue || end instanceof BigIntegerValue) {
                if (direction < 0) {
                    return new BigRangeIterator(end.asBigInteger(), step.asBigInteger(), start.asBigInteger());
                } else {
                    return new BigRangeIterator(start.asBigInteger(), step.asBigInteger(), end.asBigInteger());
                }
            } else {
                long startVal = start.longValue();
                long stepVal = step.longValue();
                long endVal = end.longValue();
                if ((endVal - startVal) / stepVal > Integer.MAX_VALUE) {
                    throw new XPathException("Saxon limit on sequence length exceeded (2^31)", "XPDY0130");
                }
                if (stepVal > 0) {
                    return new AscendingRangeIterator(startVal, stepVal, endVal);
                } else {
                    return new DescendingRangeIterator(endVal, -stepVal, startVal);
                }
            }
        }
    }


    /**
     * Create an iterator over a range of monotonically increasing integers
     *
     * @param start the first integer in the sequence
     * @param step the increment; must be GT 0
     * @param end   the last integer in the sequence. Must be GE start.
     */

    public AscendingRangeIterator(long start, long step, long end) {
        this.start = start;
        this.step = step;
        currentValue = start - step;
        limit = end;
        if (step != 1L) {
            limit = start + ((end - start)/step) * step;
        }
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
        return new Int64Value(start);
    }

    @Override
    public IntegerValue getMax() {
        return new Int64Value(limit);
    }

    /**
     * Get the increment between successive values. For a descending iterator this will be negatiive value.
     *
     * @return the increment between successive values
     */
    @Override
    public IntegerValue getStep() {
        return new Int64Value(step);
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    @Override
    public boolean hasNext() {
        return currentValue + step <= limit;
    }

    /*@Nullable*/
    @Override
    public IntegerValue next() {
        currentValue += step;
        if (currentValue > limit) {
            return null;
        }
        return Int64Value.makeIntegerValue(currentValue);
    }

    public boolean isActuallyGrounded() {
        return true;
    }

    @Override
    public boolean supportsGetLength() {
        return true;
    }

    @Override
    public int getLength() {
        return (int) ((limit - start) + 1);
    }

    @Override
    public AtomicIterator getReverseIterator() {
        return new DescendingRangeIterator(limit, step, start);
    }

    /**
     * Return a Value containing all the items in the sequence returned by this
     * SequenceIterator. This should be an "in-memory" value, not a Closure.
     *
     * @return the corresponding Value
     */

    @Override
    public GroundedValue materialize() {
        return new IntegerRange(start, step, limit);
    }

    @Override
    public GroundedValue getResidue() {
        return new IntegerRange(currentValue, step, limit);
    }
}

