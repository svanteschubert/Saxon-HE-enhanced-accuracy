////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.StringElaborator;
import net.sf.saxon.expr.elab.StringEvaluator;
import net.sf.saxon.expr.elab.UnicodeStringEvaluator;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.ToLower;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.value.StringValue;


/**
 * This class implements the fn:lower-case() function
 */

public class LowerCase extends ScalarSystemFunction {

    @Override
    public StringValue evaluate(Item arg, XPathContext context) {
        return StringValue.makeUStringValue(ToLower.toLower(arg.getUnicodeStringValue()));
    }

    @Override
    public Sequence resultWhenEmpty() {
        return StringValue.EMPTY_STRING;
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new LowerCaseFnElaborator();
    }

    public static class LowerCaseFnElaborator extends StringElaborator {

        public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent) {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final Expression arg = fnc.getArg(0);
            final UnicodeStringEvaluator argEval = arg.makeElaborator().elaborateForUnicodeString(false);
            return context -> {
                UnicodeString s0 = argEval.eval(context);
                if (s0 == null) {
                    return EmptyUnicodeString.getInstance();
                }
                return ToLower.toLower(s0);
            };
        }

        @CSharpReplaceBody(code = "return elaborateForStringAlternative(zeroLengthWhenAbsent);")
        public StringEvaluator elaborateForString(boolean zeroLengthWhenAbsent) {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final Expression arg = fnc.getArg(0);
            final StringEvaluator argEval = arg.makeElaborator().elaborateForString(false);
            return context -> {
                String s0 = argEval.eval(context);
                if (s0 == null) {
                    return "";
                }
                return s0.toLowerCase();
            };
        }


    }

}

