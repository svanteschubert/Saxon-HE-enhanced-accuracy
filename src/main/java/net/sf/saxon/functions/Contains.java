////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;

/**
 * Implements the fn:contains() function, with the collation already known
 */
public class Contains extends CollatingFunctionFixed {

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
            if (collation == CodepointCollator.getInstance()) {
                final StringEvaluator arg0Eval = fnc.getArg(0).makeElaborator().elaborateForString(true);
                final StringEvaluator arg1Eval = fnc.getArg(1).makeElaborator().elaborateForString(true);
                switch (name) {
                    case "contains":
                        return context -> arg0Eval.eval(context).contains(arg1Eval.eval(context));
                    case "starts-with":
                        return context -> arg0Eval.eval(context).startsWith(arg1Eval.eval(context));
                    case "ends-with":
                        return context -> arg0Eval.eval(context).endsWith(arg1Eval.eval(context));
                    default:
                        throw new UnsupportedOperationException();
                }
            } else {
                final UnicodeStringEvaluator arg0Eval = fnc.getArg(0).makeElaborator().elaborateForUnicodeString(true);
                final UnicodeStringEvaluator arg1Eval = fnc.getArg(1).makeElaborator().elaborateForUnicodeString(true);
                switch (name) {
                    case "contains":
                        return context -> {
                            UnicodeString s0 = arg0Eval.eval(context);
                            UnicodeString s1 = arg1Eval.eval(context);
                            return collation.contains(s0, s1);
                        };
                    case "starts-with":
                        return context -> {
                            UnicodeString s0 = arg0Eval.eval(context);
                            UnicodeString s1 = arg1Eval.eval(context);
                            return collation.startsWith(s0, s1);
                        };
                    case "ends-with":
                        return context -> {
                            UnicodeString s0 = arg0Eval.eval(context);
                            UnicodeString s1 = arg1Eval.eval(context);
                            return collation.endsWith(s0, s1);
                        };
                    default:
                        throw new UnsupportedOperationException();
                }
            }

        }

    }
}

