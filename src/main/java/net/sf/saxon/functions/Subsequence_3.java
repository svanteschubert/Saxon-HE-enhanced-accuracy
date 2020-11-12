////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.NumericValue;

/**
 * Implements the XPath 2.0 subsequence() function with three arguments
 */


public class Subsequence_3 extends SystemFunction implements Callable {

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-significant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     * @param arguments the actual arguments to the function call
     */

    @Override
    public int getSpecialProperties(Expression[] arguments) {
        return arguments[0].getSpecialProperties();
    }

    /**
     * Call the function
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
     *                  call it only once; if the value is required more than once, it should first be converted
     *                  to a {@link net.sf.saxon.om.GroundedValue} by calling the utility methd
     *                  SequenceTool.toGroundedValue().</p>
     *                  <p>If the expected value is a single item, the item should be obtained by calling
     *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
     *                  {@link net.sf.saxon.om.Item} or {@link net.sf.saxon.value.AtomicValue}.</p>
     *                  <p>It is the caller's responsibility to perform any type conversions required
     *                  to convert arguments to the type expected by the callee. An exception is where
     *                  this Callable is explicitly an argument-converting wrapper around the original
     *                  Callable.</p>
     * @return the result of the function call
     * @throws XPathException if evaluation of the arguments fails
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return SequenceTool.toLazySequence(subSequence(
                arguments[0].iterate(),
                (NumericValue) arguments[1].head(),
                (NumericValue) arguments[2].head(),
                context));
    }


    public static SequenceIterator subSequence(
            SequenceIterator seq, NumericValue startVal, NumericValue lengthVal, XPathContext context)
            throws XPathException {

        if (startVal instanceof Int64Value && lengthVal instanceof Int64Value) {
            // Fast path where the second and third arguments evaluate to integers
            long lstart = startVal.longValue();
            if (lstart > Integer.MAX_VALUE) {
                return EmptyIterator.emptyIterator();
            }
            long llength = lengthVal.longValue();
            if (llength > Integer.MAX_VALUE) {
                llength = Integer.MAX_VALUE;
            }
            if (llength < 1) {
                return EmptyIterator.emptyIterator();
            }
            long lend = lstart + llength - 1;
            if (lend < 1) {
                return EmptyIterator.emptyIterator();
            }
            int start = lstart < 1 ? 1 : (int) lstart;
            return SubsequenceIterator.make(seq, start, (int) lend);
        } else {
            if (startVal.isNaN()) {
                return EmptyIterator.emptyIterator();
            }
            if (startVal.compareTo(Int64Value.MAX_LONG) > 0) {
                return EmptyIterator.emptyIterator();
            }
            startVal = startVal.round(0);

            if (lengthVal.isNaN()) {
                return EmptyIterator.emptyIterator();
            }
            lengthVal = lengthVal.round(0);

            if (lengthVal.compareTo(Int64Value.ZERO) <= 0) {
                return EmptyIterator.emptyIterator();
            }
            NumericValue rend = (NumericValue) ArithmeticExpression.compute(
                    startVal, Calculator.PLUS, lengthVal, context);
            if (rend.isNaN()) {
                // Can happen when start = -INF, length = +INF
                return EmptyIterator.emptyIterator();
            }
            rend = (NumericValue) ArithmeticExpression.compute(
                    rend, Calculator.MINUS, Int64Value.PLUS_ONE, context);
            if (rend.compareTo(Int64Value.ZERO) <= 0) {
                return EmptyIterator.emptyIterator();
            }

            long lstart;
            if (startVal.compareTo(Int64Value.PLUS_ONE) <= 0) {
                lstart = 1;
            } else {
                lstart = startVal.longValue();
            }
            if (lstart > Integer.MAX_VALUE) {
                return EmptyIterator.emptyIterator();
            }

            long lend;
            if (rend.compareTo(Int64Value.MAX_LONG) >= 0) {
                lend = Integer.MAX_VALUE;
            } else {
                lend = rend.longValue();
            }
            return SubsequenceIterator.make(seq, (int) lstart, (int) lend);

        }

    }

    @Override
    public String getStreamerName() {
        return "Subsequence";
    }


}

