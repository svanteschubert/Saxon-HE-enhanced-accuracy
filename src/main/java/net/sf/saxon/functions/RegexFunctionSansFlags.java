////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

/**
 * This class implements any of the functions matches(), replace(), tokenize(), analyze-string(), in which the
 * final "flags" argument is omitted.
 */
public class RegexFunctionSansFlags extends SystemFunction {

    private SystemFunction addFlagsArgument() {
        Configuration config = getRetainedStaticContext().getConfiguration();
        SystemFunction fixed = config.makeSystemFunction(
            getFunctionName().getLocalPart(), getArity() + 1);

        fixed.setRetainedStaticContext(getRetainedStaticContext());
        return fixed;
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
    public Expression makeFunctionCall(Expression... arguments) {
        SystemFunction withFlags = addFlagsArgument();
        Expression[] newArgs = new Expression[arguments.length + 1];
        System.arraycopy(arguments, 0, newArgs, 0, arguments.length);
        newArgs[arguments.length] = new StringLiteral("");
        return withFlags.makeFunctionCall(newArgs);
    }

    /**
     * Invoke the function. Used only for dynamic calls.
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs within the function
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] args) throws XPathException {
        SystemFunction withFlags = addFlagsArgument();
        Sequence[] newArgs = new Sequence[args.length + 1];
        System.arraycopy(args, 0, newArgs, 0, args.length);
        newArgs[args.length] = StringValue.EMPTY_STRING;
        return withFlags.call(context, newArgs);
    }
}

