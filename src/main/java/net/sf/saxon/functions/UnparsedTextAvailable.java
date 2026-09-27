////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;

import java.util.Map;


public class UnparsedTextAvailable extends UnparsedTextFunction implements Callable {

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
        StringValue hrefVal = (StringValue) arguments[0].head();
        if (hrefVal == null) {
            return BooleanValue.FALSE;
        }
        String encoding = null;
        boolean normalizeNewlines = false;
        if (getArity() == 2) {
            Item arg1 = arguments[1].head();
            if (arg1 instanceof StringValue) {
                encoding = arg1.getStringValue();
            } else if (arg1 instanceof MapItem) {
                Map<String, GroundedValue> options = UnparsedText.OPTION_DETAILS.processSuppliedOptions(((MapItem) arg1), context, 40);
                GroundedValue encodingOption = options.get("encoding");
                if (encodingOption != null) {
                    encoding = encodingOption.getStringValue();
                }
                BooleanValue normalizeNewlinesOption = (BooleanValue) options.get("normalize-newlines");
                if (normalizeNewlinesOption != null && normalizeNewlinesOption.getBooleanValue()) {
                    normalizeNewlines = normalizeNewlinesOption.effectiveBooleanValue();
                }
            }
        }
        return BooleanValue.get(
                evalUnparsedTextAvailable(hrefVal, encoding, context));
    }

    public boolean evalUnparsedTextAvailable(StringValue hrefVal, String encoding, XPathContext context) {
        try {
            UnparsedText.evalUnparsedText(hrefVal, getStaticBaseUriString(), encoding, false, false, context);
            return true;
        } catch (XPathException err) {
            return false;
        }
    }


}

