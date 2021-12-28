////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.tree.util.FastStringBuffer;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Comparer that treats strings as an alternating sequence of alpha parts and numeric parts. The
 * alpha parts are compared using a base collation supplied as a parameter; the numeric parts are
 * compared numerically. "Numeric" here means a sequence of consecutive ASCII digits 0-9.
 * <p>
 * Note: this StringCollator produces an ordering that is not compatible with equals().
 * </p>
 */

public class AlphanumericCollator implements StringCollator {

    private StringCollator baseCollator;
    private static Pattern pattern = Pattern.compile("\\d+");
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
     */

    @Override
    public int compareStrings(CharSequence cs1, CharSequence cs2) {
        String s1 = cs1.toString();
        String s2 = cs2.toString();
        int pos1 = 0;
        int pos2 = 0;
        Matcher m1 = pattern.matcher(s1);
        Matcher m2 = pattern.matcher(s2);
        while (true) {

            // find the next number in each string

            boolean b1 = m1.find(pos1);
            boolean b2 = m2.find(pos2);
            int m1start = b1 ? m1.start() : s1.length();
            int m2start = b2 ? m2.start() : s2.length();

            // compare an alphabetic pair (even if zero-length)

            int c = baseCollator.compareStrings(s1.substring(pos1, m1start), s2.substring(pos2, m2start));
            if (c != 0) {
                return c;
            }

            // if one match found a number and the other didn't, exit accordingly

            if (b1 && !b2) {
                return +1;
            } else if (b2 && !b1) {
                return -1;
            } else if (!b1) {
                return 0;
            }

            // a number was found in each of the strings: compare the numbers

            BigInteger n1 = new BigInteger(s1.substring(m1start, m1.end()));
            BigInteger n2 = new BigInteger(s2.substring(m2start, m2.end()));
            c = n1.compareTo(n2);
            if (c != 0) {
                return c;
            }

            // the numbers are equal: move on to the next part of the string

            pos1 = m1.end();
            pos2 = m2.end();
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
    public boolean comparesEqual(CharSequence s1, CharSequence s2) {
        return compareStrings(s1, s2) == 0;
    }

    /**
     * Get a collation key for comparing two Strings. The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * compare correctly under the equals() method.
     * @param cs the string whose collation key is required
     */

    @Override
    public AtomicMatchKey getCollationKey(/*@NotNull*/ CharSequence cs) {
        // The string is normalized by removing leading zeros in a numeric component
        String s = cs.toString();
        FastStringBuffer sb = new FastStringBuffer(s.length() * 2);
        int pos1 = 0;
        Matcher m1 = pattern.matcher(s);
        while (true) {

            // find the next number in the string

            boolean b1 = m1.find(pos1);
            int m1start = b1 ? m1.start() : s.length();

            // handle an alphabetic part (even if zero-length)

            sb.append(baseCollator.getCollationKey(s.substring(pos1, m1start)).toString());

            // reached end?

            if (!b1) {
                return UnicodeString.makeUnicodeString(sb);
            }

            // handle a numeric part

            int n1 = Integer.parseInt(s.substring(m1start, m1.end()));
            sb.append(n1 + "");

            // move on to the next part of the string

            pos1 = m1.end();
        }
    }

}

