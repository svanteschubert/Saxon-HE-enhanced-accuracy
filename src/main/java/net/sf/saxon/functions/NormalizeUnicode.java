////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.serialize.codenorm.Normalizer;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CompressedWhitespace;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

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
        String nf = arguments.length == 1 ? "NFC" : Whitespace.trim(arguments[1].head().getStringValue());
        return normalize(sv, nf, context);
    }

    public static StringValue normalize(StringValue sv, String form, XPathContext c) throws XPathException {
        byte fb;

        if (form.equalsIgnoreCase("NFC")) {
            fb = Normalizer.C;
        } else if (form.equalsIgnoreCase("NFD")) {
            fb = Normalizer.D;
        } else if (form.equalsIgnoreCase("NFKC")) {
            fb = Normalizer.KC;
        } else if (form.equalsIgnoreCase("NFKD")) {
            fb = Normalizer.KD;
        } else if (form.isEmpty()) {
            return sv;
        } else {
            String msg = "Normalization form " + form + " is not supported";
            XPathException err = new XPathException(msg);
            err.setErrorCode("FOCH0003");
            err.setXPathContext(c);
            throw err;
        }

        // fast path for ASCII strings: normalization is a no-op
        boolean allASCII = true;
        CharSequence chars = sv.getStringValueCS();
        if (chars instanceof CompressedWhitespace) {
            return sv;
        }
        for (int i = chars.length() - 1; i >= 0; i--) {
            if (chars.charAt(i) > 127) {
                allASCII = false;
                break;
            }
        }
        if (allASCII) {
            return sv;
        }

        Normalizer norm = Normalizer.make(fb, c.getConfiguration());
        CharSequence result = norm.normalize(sv.getStringValueCS());
        return StringValue.makeStringValue(result);
    }

}

