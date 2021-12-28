////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;

/**
 * Implementation of the fn:count function
 */
public class Count extends SystemFunction {

    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     *         unknown or not applicable.
     */
    @Override
    public IntegerValue[] getIntegerBounds() {
        return new IntegerValue[]{Int64Value.ZERO, Expression.MAX_SEQUENCE_LENGTH};
    }

    /**
     * Get the number of items in a sequence identified by a SequenceIterator
     *
     * @param iter The SequenceIterator. This method moves the current position
     *             of the supplied iterator; if this isn't safe, make a copy of the iterator
     *             first by calling getAnother(). The supplied iterator must be positioned
     *             before the first item (there must have been no call on next()).
     * @return the number of items in the underlying sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a failure occurs reading the input sequence
     */

    public static int count(/*@NotNull*/ SequenceIterator iter) throws XPathException {
        if (iter.getProperties().contains(SequenceIterator.Property.LAST_POSITION_FINDER)) {
            return ((LastPositionFinder) iter).getLength();
        } else {
            int n = 0;
            while (iter.next() != null) {
                n++;
            }
            return n;
        }
    }

    /**
     * Get the number of items in a sequence identified by a SequenceIterator
     *
     * @param iter The SequenceIterator. The supplied iterator must be positioned
     *             before the first item (there must have been no call on next()). It will
     *             always be consumed
     * @return the number of items in the underlying sequence
     * @throws net.sf.saxon.trans.XPathException if a failure occurs reading the input sequence
     */

    public static int steppingCount(SequenceIterator iter) throws XPathException {
        int n = 0;
        while (iter.next() != null) {
            n++;
        }
        return n;
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public IntegerValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        Sequence arg = arguments[0];
        int size = arg instanceof GroundedValue ? ((GroundedValue)arg).getLength() : count(arg.iterate());
        return Int64Value.makeIntegerValue(size);
    }

    @Override
    public String getCompilerName() {
        return "CountCompiler";
    }

    @Override
    public String getStreamerName() {
        return "Count";
    }




}

