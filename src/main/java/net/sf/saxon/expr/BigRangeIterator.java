////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.tree.iter.RangeIterator;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceExtent;

import java.math.BigInteger;

/**
 * An Iterator that produces numeric values in a monotonic ascending or descending sequence,
 * where the integers may exceed the range of a Long
 */

public class BigRangeIterator extends RangeIterator implements AtomicIterator, LastPositionFinder, LookaheadIterator {

    BigInteger start;
    BigInteger step;
    BigInteger currentValue;
    BigInteger limit;
    boolean descending;

    /**
     * Create an iterator over a range of monotonically increasing integers
     *
     * @param start the first integer in the sequence
     * @param step the increment: negative for descending sequence
     * @param end   the last integer in the sequence. Must be &gt;= start if ascending, or &lt;= if descending.
     * @throws XPathException if the max sequence length is exceeded
     */

    public BigRangeIterator(BigInteger start, BigInteger step, BigInteger end) throws XPathException {
        if (end.subtract(start).divide(step).compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new XPathException("Saxon limit on sequence length exceeded (2^31)", "XPDY0130");
        }
        this.start = start;
        this.step = step;
        currentValue = start.subtract(step);
        limit = end;  // TODO normalise
        descending = step.signum() < 0;
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
        return SequenceExtent.makeResidue(this);
    }

    @Override
    public IntegerValue getFirst() {
        return IntegerValue.makeIntegerValue(start);
    }

    @Override
    public IntegerValue getLast() {
        return IntegerValue.makeIntegerValue(limit);
    }

    @Override
    public IntegerValue getMin() {
        return descending ? getLast() : getFirst();
    }

    @Override
    public IntegerValue getMax() {
        return descending ? getFirst() : getLast();
    }

    /**
     * Get the increment between successive values. For a descending iterator this will be negative value.
     *
     * @return the increment between successive values
     */
    @Override
    public IntegerValue getStep() {
        return IntegerValue.makeIntegerValue(step);
    }

    private boolean test(BigInteger value) {
        return descending ? value.compareTo(limit) >= 0 : value.compareTo(limit) <= 0;
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    @Override
    public boolean hasNext() {
        return test(currentValue.add(step));
    }

    /*@Nullable*/
    @Override
    public IntegerValue next() {
        currentValue = currentValue.add(step);
        if (!test(currentValue)) {
            return null;
        }
        return IntegerValue.makeIntegerValue(currentValue);
    }

    @Override
    public boolean supportsGetLength() {
        return true;
    }

    @Override
    public int getLength() {
        // ((end - start) / step) + 1;
        BigInteger len = limit.subtract(start).divideAndRemainder(step)[0];
        if (len.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new UncheckedXPathException(new XPathException("Sequence exceeds Saxon limit (32-bit integer)"));
        }
        return len.intValue() + 1;
    }

    public boolean isActuallyGrounded() {
        return true;
    }

}

