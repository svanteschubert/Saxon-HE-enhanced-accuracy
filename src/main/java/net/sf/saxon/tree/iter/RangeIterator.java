////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Converter;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.NumericValue;

/**
 * This abstract class represents an iteration over the results of a range expression
 * (A by B to C). There are implementations for ascending and descending iterators,
 * implemented separately for integer ranges within the 64-bit long range, and
 * for ranges that might include larger integers.
 *
 * <p>As well as being able to deliver a sequence of integers, these iterators also
 * allow testing the length of the sequence returned, for the existence of a particular
 * value, for the minimum and maximum values, for the first and last values, and for
 * the Nth value.</p>
 */

public abstract class RangeIterator implements GroundedIterator {
    
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
    public abstract GroundedValue getResidue();

    public abstract IntegerValue getFirst();

    public abstract IntegerValue getLast();

    public abstract IntegerValue getMin();

    public abstract IntegerValue getMax();

    /**
     * Get the increment between successive values. For a descending iterator this will be negatiive value.
     * @return the increment between successive values
     */

    public abstract IntegerValue getStep();

    /**
     * Ask whether the iterator will deliver a value equal to a supplied value
     * @param val the supplied value
     * @return true if the value is one of those that the iterator will deliver
     */

    public boolean containsEq(NumericValue val) {
        IntegerValue intVal;
        // See bug #5625 - I thought this code was unreachable, but qt4 test ByExpr441c hits it. MHK 2022-08-03
        if (val instanceof IntegerValue) {
            intVal = (IntegerValue)val;
        } else {
            if (!val.isWholeNumber()) {
                return false;
            }
            try {
                intVal = (IntegerValue) Converter.NumericToInteger.INSTANCE.convert(val).asAtomic();
            } catch (ValidationException e) {
                return false;
            }
        }
        try {
            return intVal.compareTo(getMin()) >= 0
                    && intVal.compareTo(getMax()) <= 0
                    && intVal.minus(getFirst()).mod(getStep()).equals(Int64Value.ZERO);
        } catch (XPathException e) {
            throw new AssertionError(e);
        }
    }

}

