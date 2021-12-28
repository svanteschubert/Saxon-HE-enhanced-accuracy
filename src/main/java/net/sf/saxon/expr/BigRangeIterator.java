////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.value.IntegerValue;

import java.math.BigInteger;
import java.util.EnumSet;

/**
 * An Iterator that produces numeric values in a monotonic sequence,
 * where the integers may exceed the range of a Long
 */

public class BigRangeIterator implements AtomicIterator<IntegerValue>, LastPositionFinder, LookaheadIterator {

    BigInteger start;
    BigInteger currentValue;
    BigInteger limit;

    /**
     * Create an iterator over a range of monotonically increasing integers
     *
     * @param start the first integer in the sequence
     * @param end   the last integer in the sequence. Must be &gt;= start.
     * @throws XPathException if for example the end is before the start
     */

    public BigRangeIterator(BigInteger start, BigInteger end) throws XPathException {
        if (end.subtract(start).compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new XPathException("Saxon limit on sequence length exceeded (2^31)", "XPDY0130");
        }
        this.start = start;
        currentValue = start.subtract(BigInteger.valueOf(1));
        limit = end;
    }

    @Override
    public boolean hasNext() {
        return currentValue.compareTo(limit) < 0;
    }

    /*@Nullable*/
    @Override
    public IntegerValue next() {
        currentValue = currentValue.add(BigInteger.valueOf(1));
        if (currentValue.compareTo(limit) > 0) {
            return null;
        }
        return IntegerValue.makeIntegerValue(currentValue);
    }

    @Override
    public int getLength() {
        BigInteger len = limit.subtract(start).add(BigInteger.valueOf(1));
        if (len.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new UncheckedXPathException(new XPathException("Sequence exceeds Saxon limit (32-bit integer)"));
        }
        return len.intValue();
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

}

