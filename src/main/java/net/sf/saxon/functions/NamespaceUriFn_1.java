////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;

/**
 * This class supports the namespace-uri() function
 */

public class NamespaceUriFn_1 extends ScalarSystemFunction {

    @Override
    public AtomicValue evaluate(Item item, XPathContext context) throws XPathException {
        NamespaceUri uri = ((NodeInfo) item).getNamespaceUri();
        return new AnyURIValue(uri.toUnicodeString());
    }

    @Override
    public AnyURIValue resultWhenEmpty() {
        return new AnyURIValue("");
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new NamespaceUriFnElaborator();
    }

    /**
     * Elaborator for the namespace-uri() function
     */

    public static class NamespaceUriFnElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final Expression arg = fnc.getArg(0);
            final boolean nullable = Cardinality.allowsZero(arg.getCardinality());
            final ItemEvaluator argEval = arg.makeElaborator().elaborateForItem();

            return context -> {
                NodeInfo node = (NodeInfo) argEval.eval(context);
                NamespaceUri uri = nullable && node == null ? NamespaceUri.NULL : node.getNamespaceUri();
                return new AnyURIValue(uri.toUnicodeString());
            };

        }

    }
}

