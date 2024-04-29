////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.elab.StringEvaluator;
import net.sf.saxon.expr.elab.UnicodeStringEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.StringElaborator;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;


/**
 * Implements the fn:substring-before() function with the collation already known
 */
public class SubstringBefore extends CollatingFunctionFixed {

    @Override
    public boolean isSubstringMatchingFunction() {
        return true;
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
    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        UnicodeString s0 = getUniStringArg(arguments[0]);
        UnicodeString s1 = getUniStringArg(arguments[1]);

        StringCollator collator = getStringCollator();
        return new StringValue(((SubstringMatcher)collator).substringBefore(s0, s1));
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new SubstringBeforeFnElaborator();
    }

    public static class SubstringBeforeFnElaborator extends StringElaborator {

        public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent) {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final SubstringBefore fn = (SubstringBefore) fnc.getTargetFunction();
            final SubstringMatcher stringCollator = (SubstringMatcher) fn.getStringCollator();
            assert stringCollator != null; // We don't go down this path until the collation is known

            final UnicodeStringEvaluator arg0Eval = fnc.getArg(0).makeElaborator().elaborateForUnicodeString(true);
            final UnicodeStringEvaluator arg1Eval = fnc.getArg(1).makeElaborator().elaborateForUnicodeString(true);

            if (stringCollator instanceof CodepointCollator) {
                return context -> {
                    UnicodeString s0 = arg0Eval.eval(context);
                    UnicodeString s1 = arg1Eval.eval(context);
                    if (s1.isEmpty()) {
                        return s1;
                    }
                    if (s0.isEmpty()) {
                        return EmptyUnicodeString.getInstance();
                    }
                    long j = s0.indexOf(s1, 0);
                    if (j < 0) {
                        return EmptyUnicodeString.getInstance();
                    }
                    return s0.prefix(j);
                };
            } else {
                return context -> {
                    UnicodeString s0 = arg0Eval.eval(context);
                    UnicodeString s1 = arg1Eval.eval(context);
                    return stringCollator.substringBefore(s0, s1);
                };
            }
        }


        public StringEvaluator elaborateForString(boolean zeroLengthWhenAbsent) {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final SubstringBefore fn = (SubstringBefore) fnc.getTargetFunction();
            final SubstringMatcher stringCollator = (SubstringMatcher) fn.getStringCollator();
            assert stringCollator != null; // We don't go down this path until the collation is known

            if (stringCollator instanceof CodepointCollator) {
                final StringEvaluator arg0Eval = fnc.getArg(0).makeElaborator().elaborateForString(true);
                final StringEvaluator arg1Eval = fnc.getArg(1).makeElaborator().elaborateForString(true);

                return context -> {
                    String s0 = arg0Eval.eval(context);
                    String s1 = arg1Eval.eval(context);
                    if (s1.isEmpty()) {
                        return s0;
                    }
                    if (s0.isEmpty()) {
                        return "";
                    }
                    int j = s0.indexOf(s1, 0);
                    if (j < 0) {
                        return "";
                    }
                    return s0.substring(0, j);
                };
            } else {
                return super.elaborateForString(zeroLengthWhenAbsent);
            }
        }

    }
}

