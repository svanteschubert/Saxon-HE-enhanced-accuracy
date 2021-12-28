////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
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

    /**
     * Interface used by compiled bytecode
     *
     * @param input   the value to be tested
     * @param regex   the regular expression
     * @param flags   the flags
     * @param context the dynamic context
     * @return true if the string matches the regex
     * @throws XPathException if a dynamic error occurs
     */

    public boolean evalMatches(AtomicValue input, AtomicValue regex, CharSequence flags, XPathContext context) throws XPathException {
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
                    regex.getStringValueCS(), flags.toString(), lang, null);

        } catch (XPathException err) {
            XPathException de = new XPathException(err);
            de.maybeSetErrorCode("FORX0002");
            de.setXPathContext(context);
            throw de;
        }
        return re.containsMatch(input.getStringValueCS());
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
        RegularExpression re = getRegularExpression(arguments);
        StringValue arg = (StringValue)arguments[0].head();
        CharSequence in = arg==null ? "" : arg.getStringValueCS();
        boolean result = re.containsMatch(in);
        return BooleanValue.get(result);
    }

    @Override
    public String getCompilerName() {
        return "MatchesCompiler";
    }


}

