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
import net.sf.saxon.value.DateTimeValue;
import net.sf.saxon.value.DateValue;
import net.sf.saxon.value.TimeValue;


/**
 * This class supports the dateTime($date, $time) function
 */

public class DateTimeConstructor extends SystemFunction {

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
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        DateValue arg0 = (DateValue) arguments[0].head();
        TimeValue arg1 = (TimeValue) arguments[1].head();
        if (arg0 == null || arg1 == null) {
            return ZeroOrOne.empty();
        }
        return new ZeroOrOne(DateTimeValue.makeDateTimeValue(arg0, arg1));
    }

    @Override
    public String getCompilerName() {
        return "DateTimeConstructorCompiler";
    }

}

