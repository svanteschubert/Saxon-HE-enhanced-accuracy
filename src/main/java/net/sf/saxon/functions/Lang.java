////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;


public class Lang extends SystemFunction  {

    /**
     * Test whether the context node has the given language attribute
     *
     * @param arglang the language being tested
     * @param target  the target node
     * @return true if the node is tagged with this language code
     */

    public static boolean isLang(String arglang, NodeInfo target) {
        String doclang = null;
        NodeInfo node = target;

        while (node != null) {
            doclang = node.getAttributeValue(NamespaceConstant.XML, "lang");
            if (doclang != null) {
                break;
            }
            node = node.getParent();
            if (node == null) {
                return false;
            }
        }

        if (doclang == null) {
            return false;
        }

        while (true) {
            if (arglang.equalsIgnoreCase(doclang)) {
                return true;
            }
            int hyphen = doclang.lastIndexOf("-");
            if (hyphen < 0) {
                return false;
            }
            doclang = doclang.substring(0, hyphen);
        }
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
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo target;
        if (arguments.length > 1) {
            target = (NodeInfo) arguments[1].head();
        } else {
            target = getAndCheckContextItem(context);
        }
        final Item arg0Val = arguments[0].head();
        final String testLang = arg0Val == null ? "" : arg0Val.getStringValue();
        return BooleanValue.get(isLang(testLang, target));
    }

    /**
     * Get the context item, checking that it exists and is a node
     *
     * @param context the XPath dynamic context
     * @return the context node
     * @throws XPathException if there is no context item or if the context item is not a node
     */

    private NodeInfo getAndCheckContextItem(XPathContext context) throws XPathException {
        NodeInfo target;
        Item current = context.getContextItem();
        if (current == null) {
            XPathException err = new XPathException("The context item for lang() is absent");
            err.setErrorCode("XPDY0002");
            err.setXPathContext(context);
            throw err;
        }
        if (!(current instanceof NodeInfo)) {
            XPathException err = new XPathException("The context item for lang() is not a node");
            err.setErrorCode("XPTY0004");
            err.setXPathContext(context);
            throw err;
        }
        target = (NodeInfo) current;
        return target;
    }
}

