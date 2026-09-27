////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.UnicodeStringEvaluator;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Int64Value;

/**
 * Implement the XPath string-length() function
 */

public class StringLength_1 extends ScalarSystemFunction {


    @Override
    public Sequence resultWhenEmpty() {
        return Int64Value.ZERO;
    }

    @Override
    public AtomicValue evaluate(Item arg, XPathContext context) throws XPathException {
        return Int64Value.makeIntegerValue(arg.getUnicodeStringValue().length());
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new StringLengthFnElaborator();
    }

    public static class StringLengthFnElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            Expression arg = fnc.getArg(0);
            UnicodeStringEvaluator argEval = arg.makeElaborator().elaborateForUnicodeString(true);
            return context -> {
                UnicodeString str = argEval.eval(context);
                return Int64Value.makeIntegerValue(str.length());
            };
        }

    }
}

