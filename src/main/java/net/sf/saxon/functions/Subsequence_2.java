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
 * Implements the XPath 2.0 subsequence() function with two arguments
 */

public class Subsequence_2 extends SystemFunction implements Callable {

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
     * Determine the cardinality of the function.
     */

    @Override
    public int getCardinality(Expression[] arguments) {
        return arguments[0].getCardinality() | StaticProperty.ALLOWS_ZERO_OR_ONE;
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
     * @throws XPathException if a dynamic error occurs
     */

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return SequenceTool.toLazySequence(
            subSequence(arguments[0].iterate(), (NumericValue) arguments[1].head()));
    }

    public static SequenceIterator subSequence(
            SequenceIterator seq, NumericValue startVal)
            throws XPathException {

        long lstart;
        if (startVal instanceof Int64Value) {
            lstart = startVal.longValue();
            if (lstart <= 1) {
                return seq;
            }
        } else if (startVal.isNaN()) {
            return EmptyIterator.emptyIterator();
        } else {
            startVal = startVal.round(0);
            if (startVal.compareTo(Int64Value.PLUS_ONE) <= 0) {
                return seq;
            } else if (startVal.compareTo(Int64Value.MAX_LONG) > 0) {
                return EmptyIterator.emptyIterator();
            } else {
                lstart = startVal.longValue();
            }
        }

        if (lstart > Integer.MAX_VALUE) {
            // we don't allow sequences longer than an this
            return EmptyIterator.emptyIterator();
        }

        return TailIterator.make(seq, (int) lstart);

    }


    /**
     * Make an expression that either calls this function, or that is equivalent to a call
     * on this function
     *
     * @param arguments the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result
     */
    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        // Handle the case where the second argument is known statically
        try {
            if (Literal.isAtomic(arguments[1]) && !(arguments[0] instanceof ErrorExpression)) {
                NumericValue start = (NumericValue) ((Literal) arguments[1]).getValue();
                start = start.round(0);
                long intStart = start.longValue();
                if (intStart > Integer.MAX_VALUE) {
                    // Handle this case dynamically. Test case cbcl-subsequence-012
                    return super.makeFunctionCall(arguments);
                }
                if (intStart <= 0) {
                    return arguments[0];
                }
                return new TailExpression(arguments[0], (int) intStart);
            }
        } catch (Exception e) {
            // fall through  (for example, in 1.0 mode start can be a StringValue ...)
        }
        return super.makeFunctionCall(arguments);
    }

    @Override
    public String getStreamerName() {
        return "Subsequence";
    }


}

