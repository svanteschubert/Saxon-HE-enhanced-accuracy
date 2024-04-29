////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeString;


/**
 * A collating sequence that uses Unicode codepoint ordering
 */

public class CodepointCollator implements StringCollator, SubstringMatcher {

    private static final CodepointCollator theInstance = new CodepointCollator();

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
     * @param a the first string
     * @param b the second string
     */

    @Override
    public int compareStrings(UnicodeString a, UnicodeString b) {
        return a.compareTo(b);
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
    public boolean comparesEqual(UnicodeString s1, UnicodeString s2) {
        return s1.equals(s2);
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
    public boolean contains(UnicodeString s1, UnicodeString s2) {
        return s1.indexOf(s2, 0) >= 0;
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
    public boolean endsWith(UnicodeString s1, UnicodeString s2) {
        if (s2.length() > s1.length()) {
            return false;
        }
        return s1.hasSubstring(s2, s1.length() - s2.length());
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
    public boolean startsWith(UnicodeString s1, UnicodeString s2) {
        return s1.hasSubstring(s2, 0);
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
    public UnicodeString substringAfter(UnicodeString s1, UnicodeString s2) {
        long i = s1.indexOf(s2, 0);
        if (i < 0) {
            return EmptyUnicodeString.getInstance();
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
    public UnicodeString substringBefore(/*@NotNull*/ UnicodeString s1, UnicodeString s2) {
        long j = s1.indexOf(s2, 0);
        if (j < 0) {
            return EmptyUnicodeString.getInstance();
        }
        return s1.prefix(j);
    }

    /**
     * Get a collation key for a string. The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * compare correctly under the equals() method.
     * @param s the string whose collation key is required
     */

    @Override
    public AtomicMatchKey getCollationKey(UnicodeString s) {
        return s;
    }

    /**
     * Test if a supplied string compares equal to the empty string
     *
     * @param s1 the supplied string
     */
    @Override
    public boolean isEqualToEmpty(UnicodeString s1) {
        return s1.isEmpty();
    }
}

