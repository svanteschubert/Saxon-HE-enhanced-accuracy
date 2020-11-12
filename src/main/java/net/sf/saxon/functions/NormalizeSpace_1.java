////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.One;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

/**
 * Implement the XPath normalize-space() function
 */

public class NormalizeSpace_1 extends ScalarSystemFunction {

    @Override
    public ZeroOrOne resultWhenEmpty() {
        return One.string("");
    }

    @Override
    public AtomicValue evaluate(Item arg, XPathContext context) throws XPathException {
        return normalizeSpace((StringValue) arg);
    }

    public static StringValue normalizeSpace(StringValue sv) {
        if (sv == null) {
            return StringValue.EMPTY_STRING;
        }
        return StringValue.makeStringValue(
                Whitespace.collapseWhitespace(sv.getStringValueCS()));
    }

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        return new SystemFunctionCall(this, arguments) {
            @Override
            public boolean effectiveBooleanValue(XPathContext c) throws XPathException {
                AtomicValue sv = (AtomicValue) getArg(0).evaluateItem(c);
                if (sv == null) {
                    return false;
                }
                CharSequence cs = sv.getStringValueCS();
                return !Whitespace.isWhite(cs);
            }
        };
    }

    @Override
    public String getCompilerName() {
        return "NormalizeSpaceCompiler";
    }

}

