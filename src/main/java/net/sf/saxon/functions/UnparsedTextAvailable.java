////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;


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
        String encoding = getArity() == 2 ? arguments[1].head().getStringValue() : null;
        return BooleanValue.get(
                evalUnparsedTextAvailable(hrefVal, encoding, context));
    }

    public boolean evalUnparsedTextAvailable(StringValue hrefVal, String encoding, XPathContext context) {
        try {
            UnparsedText.evalUnparsedText(hrefVal, getStaticBaseUriString(), encoding, context);
            return true;
        } catch (XPathException err) {
            return false;
        }
    }


}

