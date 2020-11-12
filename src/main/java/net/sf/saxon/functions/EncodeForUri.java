////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.serialize.charcode.UTF8CharacterSet;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

/**
 * This class supports the function fn:encode-for-uri()
 */

public class EncodeForUri extends ScalarSystemFunction {

    @Override
    public AtomicValue evaluate(Item arg, XPathContext context) throws XPathException {
        final CharSequence s = arg.getStringValueCS();
        return StringValue.makeStringValue(escape(s, "-_.~"));
    }

    @Override
    public ZeroOrOne resultWhenEmpty() {
        return ZERO_LENGTH_STRING;
    }

    /**
     * Escape special characters in a URI. The characters that are %HH-encoded are
     * all non-ASCII characters, plus all ASCII characters except (a) letter A-Z
     * and a-z, (b) digits 0-9, and (c) characters listed in the allowedPunctuation
     * argument
     *
     * @param s                  the URI to be escaped
     * @param allowedPunctuation ASCII characters other than letters and digits that
     *                           should NOT be %HH-encoded
     * @return the %HH-encoded string
     */

    public static CharSequence escape(CharSequence s, String allowedPunctuation) {
        FastStringBuffer sb = new FastStringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.cat(c);
            } else if (c <= 0x20 || c >= 0x7f) {
                escapeChar(c, (i + 1) < s.length() ? s.charAt(i + 1) : ' ', sb);
            } else if (allowedPunctuation.indexOf(c) >= 0) {
                sb.cat(c);
            } else {
                escapeChar(c, ' ', sb);
            }

        }
        return sb;
    }

    private static final String hex = "0123456789ABCDEF";

    /**
     * Escape a single character in %HH representation, or a pair of two chars representing
     * a surrogate pair
     *
     * @param c  the character to be escaped, or the first character of a surrogate pair
     * @param c2 the second character of a surrogate pair
     * @param sb the buffer to contain the escaped result
     */

    public static void escapeChar(char c, char c2, FastStringBuffer sb) {
        byte[] array = new byte[4];
        int used = UTF8CharacterSet.getUTF8Encoding(c, c2, array);
        for (int b = 0; b < used; b++) {
            int v = (int) array[b] & 0xff;
            sb.cat('%');
            sb.cat(hex.charAt(v / 16));
            sb.cat(hex.charAt(v % 16));
        }
    }

    /**
     * Check that any percent-encoding within a URI is well-formed. The method assumes that a percent
     * sign followed by two hex digits represents an octet of the UTF-8 representation of a character;
     * any other percent sign is assumed to represent itself.
     *
     * @param uri the string to be checked for validity
     * @throws XPathException if the string is not validly percent-encoded
     */

    public static void checkPercentEncoding(String uri) throws XPathException {
        final String hexDigits = "0123456789abcdefABCDEF";
        for (int i = 0; i < uri.length(); ) {
            char c = uri.charAt(i);
            @SuppressWarnings("MismatchedReadAndWriteOfArray") byte[] bytes;
            // Note: we're translating the UTF-8 byte sequence but then not using the value
            int expectedOctets;
            if (c == '%') {
                if (i + 2 >= uri.length()) {
                    throw new XPathException("% sign in URI must be followed by two hex digits" +
                            Err.wrap(uri));
                }
                int h1 = hexDigits.indexOf(uri.charAt(i + 1));
                if (h1 > 15) {
                    h1 -= 6;
                }

                int h2 = hexDigits.indexOf(uri.charAt(i + 2));
                if (h2 > 15) {
                    h2 -= 6;
                }
                if (h1 >= 0 && h2 >= 0) {
                    int b = h1 << 4 | h2;
                    expectedOctets = UTF8RepresentationLength[h1];
                    if (expectedOctets == -1) {
                        throw new XPathException("First %-encoded octet in URI is not valid as the start of a UTF-8 " +
                                "character: first two bits must not be '10'" +
                                Err.wrap(uri));
                    }
                    bytes = new byte[expectedOctets];
                    bytes[0] = (byte) b;
                    i += 3;
                    for (int q = 1; q < expectedOctets; q++) {
                        if (i + 2 > uri.length() || uri.charAt(i) != '%') {
                            throw new XPathException("Incomplete %-encoded UTF-8 octet sequence in URI " +
                                    Err.wrap(uri));
                        }
                        h1 = hexDigits.indexOf(uri.charAt(i + 1));
                        if (h1 > 15) {
                            h1 -= 6;
                        }

                        h2 = hexDigits.indexOf(uri.charAt(i + 2));
                        if (h2 > 15) {
                            h2 -= 6;
                        }
                        if (h1 < 0 || h2 < 0) {
                            throw new XPathException("Invalid %-encoded UTF-8 octet sequence in URI" +
                                    Err.wrap(uri));
                        }
                        if (UTF8RepresentationLength[h1] != -1) {
                            throw new XPathException("In a URI, a %-encoded UTF-8 octet after the first " +
                                    "must have '10' as the first two bits" +
                                    Err.wrap(uri));
                        }
                        b = h1 << 4 | h2;
                        bytes[q] = (byte) b;
                        i += 3;
                    }
                } else {
                    throw new XPathException("% sign in URI must be followed by two hex digits" +
                            Err.wrap(uri));
                }
            } else {
                i++;
            }

        }

    }

    // Length of a UTF8 byte sequence, as a function of the first nibble
    private static int[] UTF8RepresentationLength = {1, 1, 1, 1, 1, 1, 1, 1, -1, -1, -1, -1, 2, 2, 3, 4};
}

