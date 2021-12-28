////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;


/**
 * This class implements the replace() function for replacing
 * substrings that match a regular expression
 */

public class Replace extends RegexFunction  {

    private boolean replacementChecked = false;

    @Override
    protected boolean allowRegexMatchingEmptyString() {
        return false;
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
        boolean maybeQ = arguments.length == 4 && (!(arguments[3] instanceof StringLiteral) || ((StringLiteral) arguments[3]).getStringValue().contains("q"));
        if (arguments[2] instanceof StringLiteral && !maybeQ) {
            // Do early checking of the replacement expression if known statically
            String rep = ((StringLiteral) arguments[2]).getStringValue();
            if (checkReplacement(rep) == null) {
                replacementChecked = true;
            }
        }
        return super.makeFunctionCall(arguments);
    }

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
    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {

        StringValue arg0 = (StringValue)arguments[0].head();
        CharSequence input = arg0 == null ? "" : arg0.getStringValueCS();

        StringValue arg2 = (StringValue) arguments[2].head();
        CharSequence replacement = arg2.getStringValueCS();

        RegularExpression re = getRegularExpression(arguments);
        if (!re.getFlags().contains("q")) {
            if (!replacementChecked) {
                // if it is a string literal, the check was done at compile time
                String msg = checkReplacement(replacement);
                if (msg != null) {
                    throw new XPathException(msg, "FORX0004", context);
                }
            }
        }
        CharSequence res = re.replace(input, replacement);
        return StringValue.makeStringValue(res);
    }

    /**
     * Check the contents of the replacement string
     *
     * @param rep the replacement string
     * @return null if the string is OK, or an error message if not
     */

    public static String checkReplacement(CharSequence rep) {
        for (int i = 0; i < rep.length(); i++) {
            char c = rep.charAt(i);
            if (c == '$') {
                if (i + 1 < rep.length()) {
                    char next = rep.charAt(++i);
                    if (next < '0' || next > '9') {
                        return "Invalid replacement string in replace(): $ sign must be followed by digit 0-9";
                    }
                } else {
                    return "Invalid replacement string in replace(): $ sign at end of string";
                }
            } else if (c == '\\') {
                if (i + 1 < rep.length()) {
                    char next = rep.charAt(++i);
                    if (next != '\\' && next != '$') {
                        return "Invalid replacement string in replace(): \\ character must be followed by \\ or $";
                    }
                } else {
                    return "Invalid replacement string in replace(): \\ character at end of string";
                }
            }
        }
        return null;
    }

}

