////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import java.text.Normalizer;

/**
 * Implement the XPath normalize-unicode() function (both the 1-argument and 2-argument versions)
 */

public class NormalizeUnicode extends SystemFunction {

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
    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue sv = (StringValue) arguments[0].head();
        if (sv == null) {
            return StringValue.EMPTY_STRING;
        }
        String nf = "NFC";
        if (arguments.length > 1) {
            Item option = arguments[1].head();
            if (option != null) {
                nf = Whitespace.trim(option.getStringValue());
            }
        }
        return new StringValue(normalize(sv.getStringValue(), nf));
    }

    public static String normalize(String sv, String form) throws XPathException {
        Normalizer.Form fb;

        if (form.equalsIgnoreCase("NFC")) {
            fb = Normalizer.Form.NFC;
        } else if (form.equalsIgnoreCase("NFD")) {
            fb = Normalizer.Form.NFD;
        } else if (form.equalsIgnoreCase("NFKC")) {
            fb = Normalizer.Form.NFKC;
        } else if (form.equalsIgnoreCase("NFKD")) {
            fb = Normalizer.Form.NFKD;
        } else if (form.isEmpty()) {
            return sv;
        } else {
            String msg = "Normalization form " + form + " is not supported";
            throw new XPathException(msg, "FOCH0003");
        }
        
        return Normalizer.normalize(sv, fb);
    }

}

