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
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

/**
 * Implement the XPath normalize-space() function
 */

public class NormalizeSpace_1 extends ScalarSystemFunction {

    @Override
    public Sequence resultWhenEmpty() {
        return StringValue.EMPTY_STRING;
    }

    @Override
    public AtomicValue evaluate(Item arg, XPathContext context) throws XPathException {
        return new StringValue(normalizeSpace(arg.getUnicodeStringValue()));
    }

    public static UnicodeString normalizeSpace(UnicodeString sv) {
        if (sv == null) {
            return EmptyUnicodeString.getInstance();
        }
        return Whitespace.collapseWhitespace(sv);
    }

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        return new SystemFunctionCall(this, arguments) {
            @Override
            @CSharpModifiers(code = {"public", "override"})
            public boolean effectiveBooleanValue(XPathContext c) throws XPathException {
                AtomicValue sv = (AtomicValue) this.getArg(0).evaluateItem(c);
                if (sv == null) {
                    return false;
                }
                return !Whitespace.isAllWhite(sv.getUnicodeStringValue());
            }
        };
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new NormalizeSpaceFnElaborator();
    }

    public static class NormalizeSpaceFnElaborator extends StringElaborator {

        public boolean returnZeroLengthWhenAbsent() {
            return true;
        }

        public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent) {
            SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            UnicodeStringEvaluator argEval = fnc.getArg(0).makeElaborator().elaborateForUnicodeString(true);
            return context -> Whitespace.collapseWhitespace(argEval.eval(context));
        }

        public StringEvaluator elaborateForString(boolean zeroLengthWhenAbsent) {
            SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            Expression arg = fnc.getArg(0);
            StringEvaluator argEval = arg.makeElaborator().elaborateForString(zeroLengthWhenAbsent);
            return context -> {
                String in = argEval.eval(context);
                if (in == null || in.isEmpty()) {
                    return handleNullString(zeroLengthWhenAbsent);
                }
                return Whitespace.collapseWhitespace(in);
            };
        }

        /**
         * Get a function that evaluates the underlying expression in the form of
         * a boolean, this being the effective boolean value of the expression.
         *
         * <p>This method is implemented for normalize-space() because it is a common
         * idiom to use normalize-space() in a boolean context to test whether a value
         * contains non-whitespace characters, and this can be done without actually
         * constructing the normalized string.</p>
         *
         * @return an evaluator for the expression that returns a boolean.
         */

        @Override
        public BooleanEvaluator elaborateForBoolean() {
            SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            Expression arg = fnc.getArg(0);
            UnicodeStringEvaluator argEval = arg.makeElaborator().elaborateForUnicodeString(false);
            return context -> {
                UnicodeString in = argEval.eval(context);
                if (in == null || in.isEmpty()) {
                    return false;
                }
                return !Whitespace.isAllWhite(in);
            };
        }
    }
}

