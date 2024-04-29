////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.functions.CollationKeyFn;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.value.Base64BinaryValue;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.transpile.*;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

/**
 * A Comparer that treats strings as an alternating sequence of alpha parts and numeric parts. The
 * alpha parts are compared using a base collation supplied as a parameter; the numeric parts are
 * compared numerically. "Numeric" here means a sequence of consecutive ASCII digits 0-9.
 * <p>
 * Note: this StringCollator produces an ordering that is not compatible with equals().
 * </p>
 */

public class AlphanumericCollator implements StringCollator {

    private final StringCollator baseCollator;
    private static final ARegularExpression pattern = ARegularExpression.compile("\\d+", "");
    public final static String PREFIX = "http://saxon.sf.net/collation/alphaNumeric?base=";

    /**
     * Create an alphanumeric collation
     *
     * @param base the collation used to compare the alphabetic parts of the string
     */

    public AlphanumericCollator(StringCollator base) {
        baseCollator = base;
    }

    /**
     * Get the collation URI. It must be possible to use this collation URI to reconstitute the collation
     *
     * @return a collation URI that can be used to reconstruct the collation when an XSLT package is reloaded.
     */
    @Override
    public String getCollationURI() {
        // Note this form of collation URI is not externally documented, it is retained solely to make
        // it possible to reconstitute the collation easily
        return PREFIX + baseCollator.getCollationURI();
    }

    /**
     * Compare two objects.
     *
     * @return &lt;0 if a&lt;b, 0 if a=b, &gt;0 if a&gt;b
     * @param cs1 the first string
     * @param cs2 the second string
     */

    @Override
    public int compareStrings(UnicodeString cs1, UnicodeString cs2) {

        RegexIterator iter1 = pattern.analyze(cs1);
        RegexIterator iter2 = pattern.analyze(cs2);


        while (true) {

            // find the next numeric or non-numeric substring in each string

            StringValue sv1 = iter1.next();
            StringValue sv2 = iter2.next();

            if (sv1 == null) {
                return sv2 == null ? 0 : -1;
            }

            if (sv2 == null) {
                return +1;
            }

            boolean numeric1 = iter1.isMatching();
            boolean numeric2 = iter2.isMatching();

            if (numeric1 && numeric2) {
                BigInteger n1 = new BigInteger(sv1.getStringValue());
                BigInteger n2 = new BigInteger(sv2.getStringValue());
                int c = n1.compareTo(n2);
                if (c != 0) {
                    return c;
                }
            } else {
                UnicodeString u1 = numeric1 ? EmptyUnicodeString.getInstance() : sv1.getUnicodeStringValue();
                UnicodeString u2 = numeric2 ? EmptyUnicodeString.getInstance() : sv2.getUnicodeStringValue();
                int c = baseCollator.compareStrings(u1, u2);
                if (c != 0) {
                    return c;
                }
            }

            // otherwise, the substrings are equal: move on to the next part of the string

        }
    }

    /**
     * Compare two strings for equality. This may be more efficient than using compareStrings and
     * testing whether the result is zero, but it must give the same result
     *
     * @param s1 the first string
     * @param s2 the second string
     * @return true if and only if the strings are considered equal,
     */

    @Override
    public boolean comparesEqual(UnicodeString s1, UnicodeString s2) {
        return compareStrings(s1, s2) == 0;
    }

    /**
     * Get a collation key for a String. The essential property of collation keys
     * is that if (and only if) two strings are equal under the collation, then
     * comparing the collation keys using the equals() method must return true.
     * @param cs the string whose collation key is required
     */

    @Override
    public AtomicMatchKey getCollationKey(/*@NotNull*/ UnicodeString cs) {
        // See bug 5049
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RegexIterator iter = pattern.analyze(cs);
        for (StringValue sv; (sv = iter.next()) != null;) {
            if (iter.isMatching()) {
                // numeric part
                BigInteger n = new BigInteger(sv.getStringValue());
                byte[] bin = n.toByteArray();
                int len = bin.length;
                // Assume max length of numeric part 255
                writeByte(baos, (byte)0); // separator from previous alpha part
                writeByte(baos, (byte)len);
                baos.write(bin, 0, bin.length); // written this way to avoid checked exceptions
            } else {
                Base64BinaryValue b64 = CollationKeyFn.getCollationKey(sv.getUnicodeStringValue(), baseCollator);
                final byte[] bin = b64.getBinaryValue();
                baos.write(bin, 0, bin.length);

            }
        }
        return new Base64BinaryValue(baos.toByteArray());
    }

    @CSharpReplaceBody(code="baos.WriteByte(val);")
    private static void writeByte(ByteArrayOutputStream baos, byte val) {
        baos.write(val);
    }

}

