////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.elab.BooleanElaborator;
import net.sf.saxon.expr.elab.BooleanEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.UnicodeStringEvaluator;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;


/**
 * This class implements the 3-argument matches() function for regular expression matching
 */

public class Matches extends RegexFunction {

    @Override
    protected boolean allowRegexMatchingEmptyString() {
        return true;
    }

    public boolean evalMatches(UnicodeString input, UnicodeString regex, UnicodeString flags, XPathContext context) throws XPathException {
        RegularExpression re;

        if (regex == null) {
            return false;
        }

        try {
            String lang = "XP30";
            if (context.getConfiguration().getXsdVersion() == Configuration.XSD11) {
                lang += "/XSD11";
            }
            re = context.getConfiguration().compileRegularExpression(
                    regex, flags.toString(), lang, null);

        } catch (XPathException err) {
            err.maybeSetErrorCode("FORX0002");
            err.maybeSetContext(context);
            throw err;
        }
        return re.containsMatch(input);
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
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        RegularExpression re = getRegularExpression(arguments, 1, 2);
        StringValue arg = (StringValue)arguments[0].head();
        UnicodeString in = arg==null ? EmptyUnicodeString.getInstance() : arg.getUnicodeStringValue();
        boolean result = re.containsMatch(in);
        return BooleanValue.get(result);
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new MatchesFnElaborator();
    }

    public static class MatchesFnElaborator extends BooleanElaborator {

        public BooleanEvaluator elaborateForBoolean() {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final Matches fn = (Matches) fnc.getTargetFunction();
            final UnicodeStringEvaluator arg0eval = fnc.getArg(0).makeElaborator().elaborateForUnicodeString(true);
            final RegularExpression staticRegex = fn.getStaticRegex();
            if (staticRegex == null) {
                final UnicodeStringEvaluator arg1eval = fnc.getArg(1).makeElaborator().elaborateForUnicodeString(true);
                final UnicodeStringEvaluator arg2eval = fn.getArity() == 3
                        ? fnc.getArg(2).makeElaborator().elaborateForUnicodeString(true)
                        : cxt -> EmptyUnicodeString.getInstance();
                return context -> {
                    try {
                        return fn.evalMatches(
                                arg0eval.eval(context),
                                arg1eval.eval(context),
                                arg2eval.eval(context),
                                context);
                    } catch (XPathException err) {
                        throw err.maybeWithLocation(fnc.getLocation()).maybeWithContext(context);
                    }
                };
            } else {
                return context -> staticRegex.containsMatch(arg0eval.eval(context));
            }
        }

    }
}

