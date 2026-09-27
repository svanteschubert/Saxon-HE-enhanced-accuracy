////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.CallableDelegate;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.ma.Parcel;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;


public class Lang extends SystemFunction implements IContextAccessorFunction {

    @Override
    public boolean dependsOnContext() {
        return getArity() == 1;
    }

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
            doclang = node.getAttributeValue(NamespaceUri.XML, "lang");
            if (doclang != null) {
                break;
            }
            node = (NodeInfo)node.getParent();
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
     * Bind context information to appear as part of the function's closure. If this method
     * has been called, the supplied context will be used in preference to the
     * context at the point where the function is actually called.
     *
     * @param context the context to which the function applies. Must not be null.
     */
    @Override
    public FunctionItem bindContext(XPathContext context) throws XPathException {
        if (getArity() == 2) {
            return this;
        }
        CallableDelegate.Lambda body;
        try {
            NodeInfo target = getAndCheckContextItem(context);
            body = (cxt, args) -> BooleanValue.get(isLang(args[0].head().getStringValue(), target));
        } catch (XPathException e) {
            // Test function-lookup-275. Don't throw the error unless and until the function is called.
            body = (cxt, args) -> {
                        throw new UncheckedXPathException(e);
                    };
        }
        return new CallableFunction(
                new SymbolicName.F(getFunctionName(), 1),
                body,
                new SpecificFunctionType(SequenceType.OPTIONAL_STRING, SequenceType.SINGLE_BOOLEAN));
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
            throw new XPathException("The context item for lang() is absent")
                    .withErrorCode("XPDY0002")
                    .withXPathContext(context);
        }
        if (current instanceof Parcel parcel) {
            GroundedValue val = parcel.getValue();
            if (val.getLength() != 1) {
                throw new XPathException("The context value for lang() is not a single node")
                        .withErrorCode("XPTY0004")
                        .withXPathContext(context);
            }
            current = val.head();
        }
        if (!(current instanceof NodeInfo)) {
            throw new XPathException("The context item for lang() is not a node")
                    .withErrorCode("XPTY0004")
                    .withXPathContext(context);
        }
        target = (NodeInfo) current;
        return target;
    }
}

