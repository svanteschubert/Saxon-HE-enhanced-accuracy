////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.CalendarValue;
import net.sf.saxon.value.DayTimeDurationValue;

/**
 * This class implements the XPath 2.0 functions
 * adjust-date-to-timezone(), adjust-time-timezone(), and adjust-dateTime-timezone(),
 * with two arguments
 */


public class Adjust_2 extends SystemFunction {

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        CalendarValue in = (CalendarValue) arguments[0].head();
        if (in == null) {
            return ZeroOrOne.empty();
        } else {
            DayTimeDurationValue tz = (DayTimeDurationValue) arguments[1].head();
            if (tz == null) {
                return new ZeroOrOne(in.removeTimezone());
            } else {
                return new ZeroOrOne(in.adjustTimezone(tz));
            }
        }
    }
}

