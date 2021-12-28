////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.One;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.BooleanValue;

/**
 * This class supports the nilled() function
 */

public class Nilled_1 extends SystemFunction implements Callable {


    /**
     * Determine whether a node has the nilled property
     *
     * @param node the node in question (if null, the function returns null)
     * @return the value of the nilled accessor. Returns null for any node other than an
     *         element node. For an element node, returns true if the element has been validated and
     *         has an xsi:nil attribute whose value is true.
     */

    /*@Nullable*/
    private static BooleanValue getNilledProperty(NodeInfo node) {
        if (node == null || node.getNodeKind() != Type.ELEMENT) {
            return null;
        }
        return BooleanValue.get(node.isNilled());
    }

    /**
     * Determine whether a node is nilled. Returns true if the value
     * of the nilled property is true; false if the value is false or absent
     * @param node the node to be tested
     * @return true if the node is nilled
     */

    public static boolean isNilled(NodeInfo node) {
        BooleanValue b = getNilledProperty(node);
        return b != null && b.getBooleanValue();
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {

        NodeInfo node = (NodeInfo)arguments[0].head();
        if (node == null || node.getNodeKind() != Type.ELEMENT) {
            return ZeroOrOne.empty();
        }
        return One.bool(isNilled(node));
    }
}
