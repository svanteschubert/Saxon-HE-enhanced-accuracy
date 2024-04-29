////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntIterator;

import java.util.Arrays;

/**
 * This class supports the functions encode-for-uri() and iri-to-uri()
 */

public class IriToUri extends ScalarSystemFunction {

    public static boolean[] allowedASCII = new boolean[128];

    static {
        Arrays.fill(allowedASCII, 0, 32, false);
        Arrays.fill(allowedASCII, 33, 127, true);
        allowedASCII[(int) '"'] = false;
        allowedASCII[(int) '<'] = false;
        allowedASCII[(int) '>'] = false;
        allowedASCII[(int) '\\'] = false;
        allowedASCII[(int) '^'] = false;
        allowedASCII[(int) '`'] = false;
        allowedASCII[(int) '{'] = false;
        allowedASCII[(int) '|'] = false;
        allowedASCII[(int) '}'] = false;
    }

    @Override
    public AtomicValue evaluate(Item arg, XPathContext context) throws XPathException {
        return new StringValue(iriToUri(arg.getUnicodeStringValue()));
    }

    @Override
    public Sequence resultWhenEmpty() {
        return StringValue.EMPTY_STRING;
    }

    /**
     * Escape special characters in a URI. The characters that are %HH-encoded are
     * all non-ASCII characters
     *
     * @param s the URI to be escaped
     * @return the %HH-encoded string
     */

    public static UnicodeString iriToUri(UnicodeString s) {
        // NOTE: implements a late spec change which says that characters that are illegal in an IRI,
        // for example "\", must be %-encoded.
        if (allAllowedAscii(s.codePoints())) {
            // it's worth doing a prescan to avoid the cost of copying in the common all-ASCII case
            return s;
        }
        UnicodeBuilder sb = new UnicodeBuilder(s.length32() + 20);
        IntIterator iter = s.codePoints();
        while (iter.hasNext()) {
            final int c = iter.next();
            if (c >= 0x7f || !allowedASCII[(int) c]) {
                EncodeForUri.escapeChar(c, sb);
            } else {
                sb.append(c);
            }
        }
        return sb.toUnicodeString();
    }

    private static boolean allAllowedAscii(IntIterator codePoints) {
        while (codePoints.hasNext()) {
            int c = codePoints.next();
            if (c >= 0x7f || !allowedASCII[(int) c]) {
                return false;
            }
        }
        return true;
    }


}

