////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.DateTimeValue;
import net.sf.saxon.value.DateValue;
import net.sf.saxon.value.EmptySequence;
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
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        DateValue arg0 = (DateValue) arguments[0].head();
        TimeValue arg1 = (TimeValue) arguments[1].head();
        if (arg0 == null || arg1 == null) {
            return EmptySequence.getInstance();
        }
        return DateTimeValue.makeDateTimeValue(arg0, arg1);
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new DateTimeFnElaborator();
    }

    public static class DateTimeFnElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            SystemFunctionCall sfc = (SystemFunctionCall) getExpression();
            ItemEvaluator arg0eval = sfc.getArg(0).makeElaborator().elaborateForItem();
            ItemEvaluator arg1eval = sfc.getArg(1).makeElaborator().elaborateForItem();
            return context -> DateTimeValue.makeDateTimeValue(
                    (DateValue) arg0eval.eval(context),
                    (TimeValue) arg1eval.eval(context));
        }

    }
}

