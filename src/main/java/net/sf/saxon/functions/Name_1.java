////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.StringEvaluator;
import net.sf.saxon.expr.elab.UnicodeStringEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.StringElaborator;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.StringView;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.StringValue;

/**
 * This class supports the name() function with one argument
 */

public class Name_1 extends ScalarSystemFunction {

    @Override
    public StringValue evaluate(Item item, XPathContext context) throws XPathException {
        return StringValue.makeStringValue(((NodeInfo) item).getDisplayName());
    }

    @Override
    public Sequence resultWhenEmpty() {
        return StringValue.EMPTY_STRING;
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new NameElaborator();
    }


    public static class NameElaborator extends StringElaborator {

        public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthIfAbsent) {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final Expression arg = fnc.getArg(0);
            final boolean nullable = Cardinality.allowsZero(arg.getCardinality());
            final ItemEvaluator argEval = arg.makeElaborator().elaborateForItem();

            if (nullable) {
                return context -> {
                    NodeInfo node = (NodeInfo) argEval.eval(context);
                    if (node == null) {
                        return EmptyUnicodeString.getInstance();
                    }
                    return StringView.of(node.getDisplayName());
                };
            } else {
                return context -> StringView.of(((NodeInfo) argEval.eval(context)).getDisplayName());
            }

        }

        public StringEvaluator elaborateForString(boolean zeroLengthIfAbsent) {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final Expression arg = fnc.getArg(0);
            final boolean nullable = Cardinality.allowsZero(arg.getCardinality());
            final ItemEvaluator argEval = arg.makeElaborator().elaborateForItem();

            if (nullable) {
                return context -> {
                    NodeInfo node = (NodeInfo) argEval.eval(context);
                    if (node == null) {
                        return "";
                    }
                    return node.getDisplayName();
                };
            } else {
                return context -> ((NodeInfo) argEval.eval(context)).getDisplayName();
            }

        }
    }
}

