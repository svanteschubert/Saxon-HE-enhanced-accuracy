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
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.QNameValue;

/**
 * This class supports the node-name() function with a single argument
 */

public class NodeName_1 extends ScalarSystemFunction {

    @Override
    public AtomicValue evaluate(Item item, XPathContext context) throws XPathException {
        return nodeName((NodeInfo) item);
    }

    public static QNameValue nodeName(NodeInfo node) {
        if (node.getLocalPart().isEmpty()) {
            return null;
        }
        return new QNameValue(node.getPrefix(), node.getNamespaceUri(), node.getLocalPart(), BuiltInAtomicType.QNAME);
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new NodeNameFnElaborator();
    }

    /**
     * Elaborator for the fn:node-name() function
     */

    public static class NodeNameFnElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            Expression arg = fnc.getArg(0);
            ItemEvaluator argEval = arg.makeElaborator().elaborateForItem();

            return context -> {
                NodeInfo node = (NodeInfo) argEval.eval(context);
                if (node == null || node.getLocalPart().isEmpty()) {
                    return null;
                }
                return new QNameValue(node.getPrefix(), node.getNamespaceUri(), node.getLocalPart(), BuiltInAtomicType.QNAME);
            };

        }

    }
}

