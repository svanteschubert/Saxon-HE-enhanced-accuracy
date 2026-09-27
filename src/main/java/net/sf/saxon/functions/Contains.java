////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.elab.BooleanElaborator;
import net.sf.saxon.expr.elab.BooleanEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.UnicodeStringEvaluator;
import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;

/**
 * Implements the fn:contains() function, with the collation already known
 */
public class Contains extends CollatingFunctionFixed implements ArityTwoFunction {

    @Override
    public boolean isSubstringMatchingFunction() {
        return true;
    }

    private static boolean contains(StringValue arg0, StringValue arg1, SubstringMatcher collator) {
        if (arg1 == null || arg1.isEmpty() || collator.isEqualToEmpty(arg1.getUnicodeStringValue())) {
            return true;
        }
        if (arg0 == null || arg0.isEmpty()) {
            return false;
        }
        return collator.contains(arg0.getUnicodeStringValue(), arg1.getUnicodeStringValue());
    }

    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue s0 = (StringValue) arguments[0].head();
        StringValue s1 = (StringValue) arguments[1].head();
        return BooleanValue.get(contains(s0, s1, (SubstringMatcher)getStringCollator()));
    }

    /**
     * Call a function with two arguments
     *
     * @param context the dynamic evaluation context
     * @param arg0    the first argument
     * @param arg1    the second argument
     * @return the result of the function call
     * @throws XPathException if the call fails with a dynamic error
     */
    @Override
    public Sequence call2(XPathContext context, Sequence arg0, Sequence arg1) throws XPathException {
        StringValue s0 = (StringValue) arg0.head();
        StringValue s1 = (StringValue) arg1.head();
        return BooleanValue.get(contains(s0, s1, (SubstringMatcher) getStringCollator()));
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new ContainsFnElaborator();
    }

    /**
     * Expression elaborator for a call to contains(), starts-with(), or ends-with()
     */

    public static class ContainsFnElaborator extends BooleanElaborator {

        public BooleanEvaluator elaborateForBoolean() {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final CollatingFunctionFixed fn = (CollatingFunctionFixed)fnc.getTargetFunction();
            final SubstringMatcher collation = (SubstringMatcher)fn.getStringCollator();
            assert collation != null;
            final String name = fnc.getFunctionName().getLocalPart();
            final UnicodeStringEvaluator arg0Eval = fnc.getArg(0).makeElaborator().elaborateForUnicodeString(true);
            final UnicodeStringEvaluator arg1Eval = fnc.getArg(1).makeElaborator().elaborateForUnicodeString(true);
            return switch (name) {
                case "contains" ->
                        context -> collation.contains(arg0Eval.eval(context), arg1Eval.eval(context));
                case "starts-with" ->
                        context -> collation.startsWith(arg0Eval.eval(context), arg1Eval.eval(context));
                case "ends-with" ->
                        context -> collation.endsWith(arg0Eval.eval(context), arg1Eval.eval(context));
                default -> throw new UnsupportedOperationException();
            };

        }

    }
}

