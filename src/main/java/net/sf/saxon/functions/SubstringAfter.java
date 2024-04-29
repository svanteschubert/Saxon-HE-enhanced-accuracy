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
import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

/**
 * Implements the fn:substring-after() function with the collation already known
 */
public class SubstringAfter extends CollatingFunctionFixed {

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

        return new StringValue(substringAfter(s0, s1, (SubstringMatcher)getStringCollator()));
    }

    private static UnicodeString substringAfter(UnicodeString arg1, UnicodeString arg2, SubstringMatcher collator) {
        if (arg1 == null) {
            arg1 = EmptyUnicodeString.getInstance();
        }
        if (arg2 == null) {
            arg2 = EmptyUnicodeString.getInstance();
        }
        if (arg2.isEmpty()) {
            return arg1;
        }
        if (arg1.isEmpty()) {
            return EmptyUnicodeString.getInstance();
        }

        return collator.substringAfter(arg1, arg2);
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new SubstringAfterFnElaborator();
    }

    public static class SubstringAfterFnElaborator extends StringElaborator {

        public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent) {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final SubstringAfter fn = (SubstringAfter) fnc.getTargetFunction();
            final SubstringMatcher stringCollator = (SubstringMatcher) fn.getStringCollator();
            assert stringCollator != null; // We don't go down this path until the collation is known

            final UnicodeStringEvaluator arg0Eval = fnc.getArg(0).makeElaborator().elaborateForUnicodeString(true);
            final UnicodeStringEvaluator arg1Eval = fnc.getArg(1).makeElaborator().elaborateForUnicodeString(true);

            if (stringCollator instanceof CodepointCollator) {
                return context -> {
                    UnicodeString s0 = arg0Eval.eval(context);
                    UnicodeString s1 = arg1Eval.eval(context);
                    if (s1.isEmpty()) {
                        return s0;
                    }
                    if (s0.isEmpty()) {
                        return EmptyUnicodeString.getInstance();
                    }
                    long i = s0.indexOf(s1, 0);
                    if (i < 0) {
                        return EmptyUnicodeString.getInstance();
                    }
                    return s0.substring(i + s1.length());
                };
            } else {
                return context -> {
                    UnicodeString s0 = arg0Eval.eval(context);
                    UnicodeString s1 = arg1Eval.eval(context);
                    if (s1.isEmpty()) {
                        return s0;
                    }
                    if (s0.isEmpty()) {
                        return EmptyUnicodeString.getInstance();
                    }
                    return stringCollator.substringAfter(s0, s1);
                };
            }
        }

        public StringEvaluator elaborateForString(boolean zeroLengthWhenAbsent) {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final SubstringAfter fn = (SubstringAfter) fnc.getTargetFunction();
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
                    int i = s0.indexOf(s1, 0);
                    if (i < 0) {
                        return "";
                    }
                    return s0.substring(i + s1.length());
                };
            } else {
                return super.elaborateForString(zeroLengthWhenAbsent);
            }
        }

    }
}
