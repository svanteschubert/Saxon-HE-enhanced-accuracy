////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.One;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;

/**
 * A Scalar system function is a pure function that accepts a single item as its operand,
 * returns a single atomic value as its result, typically returns an empty sequence if the argument is an
 * empty sequence, and has no context dependencies.
 */

public abstract class ScalarSystemFunction extends SystemFunction {

    /**
     * Abstract method that must be supplied in subclasses to perform the evaluation
     * @param arg the supplied argument
     * @param context the dynamic context
     * @return the result of the evaluation
     * @throws XPathException if a dynamic error occurs
     */

    public abstract AtomicValue evaluate(Item arg, XPathContext context) throws XPathException;

    /**
     * Method that may be supplied in subclasses, to indicate the result that is returned
     * when an empty sequence is supplied as the argument value. The default is to return the
     * empty sequence
     * @return the result of evaluation when the supplied argument is an empty sequence
     */

    public ZeroOrOne resultWhenEmpty() {
        return ZeroOrOne.empty();
    }

    /**
     * Static constant representing a zero-length string
     */

    public final static One ZERO_LENGTH_STRING = One.string("");

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
    public final ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        Item val0 = arguments[0].head();
        if (val0 == null) {
            return resultWhenEmpty();
        }
        return new ZeroOrOne(evaluate(val0, context));

    }

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        SystemFunctionCall call = new SystemFunctionCall(this, arguments) {
            @Override
            public AtomicValue evaluateItem(XPathContext context) throws XPathException {
                // cut out some of the call overhead
                Item val = getArg(0).evaluateItem(context);
                if (val == null) {
                    return (AtomicValue)resultWhenEmpty().head();
                } else {
                    return evaluate(val, context);
                }
            }
        };
        call.setRetainedStaticContext(getRetainedStaticContext());
        return call;
    }
}

