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
import net.sf.saxon.expr.*;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.EmptySequence;

/**
 * This class implements the fn:base-uri() function in XPath 2.0
 */

public class BaseUri_1 extends SystemFunction implements Callable {

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo node = (NodeInfo)arguments[0].head();
        if (node == null) {
            return EmptySequence.getInstance();
        }
        String s = node.getBaseURI();
        if (s == null) {
            return EmptySequence.getInstance();
        }
        return new AnyURIValue(s);

    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new BaseUriFnElaborator();
    }

    /**
     * Elaborator for simple string-valued properties of nodes such as name(), local-name(), namespace-uri(),
     * and generate-id()
     */

    public static class BaseUriFnElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final Expression arg = fnc.getArg(0);
            final boolean nullable = Cardinality.allowsZero(arg.getCardinality());
            final ItemEvaluator argEval = arg.makeElaborator().elaborateForItem();

            return context -> {
                NodeInfo node = (NodeInfo) argEval.eval(context);
                if (nullable && node == null) {
                    return null;
                }
                String s = node.getBaseURI();
                if (s == null) {
                    return null;
                }
                return new AnyURIValue(s);
            };

        }

    }
}

