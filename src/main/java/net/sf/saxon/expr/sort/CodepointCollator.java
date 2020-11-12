////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.regex.UnicodeString;


/**
 * A collating sequence that uses Unicode codepoint ordering
 */

public class CodepointCollator implements StringCollator, SubstringMatcher {

    private static CodepointCollator theInstance = new CodepointCollator();

    public static CodepointCollator getInstance() {
        return theInstance;
    }

    /**
     * Get the collation URI. It must be possible to use this collation URI to reconstitute the collation
     *
     * @return a collation URI that can be used to reconstruct the collation when an XSLT package is reloaded.
     */
    @Override
    public String getCollationURI() {
        return NamespaceConstant.CODEPOINT_COLLATION_URI;
    }

    /**
     * Compare two string objects.
     *
     * @return N &lt; 0 if a &lt; b, N = 0 if a=b, N &gt; 0 if a &gt; b
     * @throws ClassCastException if the objects are of the wrong type for this Comparer
     */

    @Override
    public int compareStrings(CharSequence a, CharSequence b) {
        //return ((String)a).compareTo((String)b);
        // Note that Java does UTF-16 code unit comparison, which is not the same as Unicode codepoint comparison
        // except in the "equals" case. So we have to do a character-by-character comparison
        return compareCS(a, b);
    }

    /**
     * Compare two CharSequence objects. This is hand-coded to avoid converting the objects into
     * Strings.
     *
     * @return N &lt; 0 if a &lt; b, N = 0 if a=b, N &gt; 0 if a &gt; b
     * @throws ClassCastException if the objects are of the wrong type for this Comparer
     */

    @SuppressWarnings("Duplicates")
    public static int compareCS(CharSequence a, CharSequence b) {
        if (a instanceof UnicodeString && b instanceof UnicodeString) {
            return ((UnicodeString) a).compareTo((UnicodeString) b);
        } else {
            int alen = a.length();
            int blen = b.length();
            int i = 0;
            int j = 0;
            while (true) {
                if (i == alen) {
                    if (j == blen) {
                        return 0;
                    } else {
                        return -1;
                    }
                }
                if (j == blen) {
                    return +1;
                }
                // Following code is needed when comparing a BMP character against a surrogate pair
                // Note: we could do this comparison without fully computing the codepoint, but it's a very rare case
                int nexta = (int) a.charAt(i++);
                if (nexta >= 55296 && nexta <= 56319) {
                    nexta = ((nexta - 55296) * 1024) + ((int) a.charAt(i++) - 56320) + 65536;
                }
                int nextb = (int) b.charAt(j++);
                if (nextb >= 55296 && nextb <= 56319) {
                    nextb = ((nextb - 55296) * 1024) + ((int) b.charAt(j++) - 56320) + 65536;
                }
                int c = nexta - nextb;
                if (c != 0) {
                    return c;
                }
            }
        }
    }

    /**
     * Test whether one string is equal to another, according to the rules
     * of the XPath compare() function. The result is true if and only if the
     * compare() method returns zero: but the implementation may be more efficient
     * than calling compare and testing the result for zero
     *
     * @param s1 the first string
     * @param s2 the second string
     * @return true iff s1 equals s2
     */

    @Override
    public boolean comparesEqual(CharSequence s1, CharSequence s2) {
        if (s1 instanceof String) {
            return ((String) s1).contentEquals(s2);
        } else if (s1 instanceof UnicodeString) {
            return s1.equals(UnicodeString.makeUnicodeString(s2));
        } else {
            return s1.length() == s2.length() && s1.toString().equals(s2.toString());
        }
    }

    /**
     * Test whether one string contains another, according to the rules
     * of the XPath contains() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return true iff s1 contains s2
     */

    @Override
    public boolean contains(String s1, String s2) {
        return s1.contains(s2);
    }

    /**
     * Test whether one string ends with another, according to the rules
     * of the XPath ends-with() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return true iff s1 ends with s2
     */

    @Override
    public boolean endsWith(String s1, String s2) {
        return s1.endsWith(s2);
    }

    /**
     * Test whether one string starts with another, according to the rules
     * of the XPath starts-with() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return true iff s1 starts with s2
     */

    @Override
    public boolean startsWith(String s1, String s2) {
        return s1.startsWith(s2);
    }

    /**
     * Return the part of a string after a given substring, according to the rules
     * of the XPath substring-after() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return the part of s1 that follows the first occurrence of s2
     */

    @Override
    public String substringAfter(String s1, String s2) {
        int i = s1.indexOf(s2);
        if (i < 0) {
            return "";
        }
        return s1.substring(i + s2.length());
    }

    /**
     * Return the part of a string before a given substring, according to the rules
     * of the XPath substring-before() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return the part of s1 that precedes the first occurrence of s2
     */

    @Override
    public String substringBefore(/*@NotNull*/ String s1, String s2) {
        int j = s1.indexOf(s2);
        if (j < 0) {
            return "";
        }
        return s1.substring(0, j);
    }

    /**
     * Get a collation key for a string. The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * compare correctly under the equals() method.
     * @param s the string whose collation key is required
     */

    @Override
    public AtomicMatchKey getCollationKey(CharSequence s) {
        return UnicodeString.makeUnicodeString(s);
    }
}

