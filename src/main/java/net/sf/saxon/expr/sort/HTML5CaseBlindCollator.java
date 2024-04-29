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
import net.sf.saxon.str.*;
import net.sf.saxon.z.IntIterator;


/**
 * A collating sequence that compares strings according to the HTML5 rules for case-blind matching.
 * Specifically, case is ignored for ASCII (English) letters, but not for letters outside
 * the ASCII range.
 */

public class HTML5CaseBlindCollator implements StringCollator, SubstringMatcher {

    private static final HTML5CaseBlindCollator theInstance = new HTML5CaseBlindCollator();

    public static HTML5CaseBlindCollator getInstance() {
        return theInstance;
    }

    /**
     * Get the collation URI. It must be possible to use this collation URI to reconstitute the collation
     *
     * @return a collation URI that can be used to reconstruct the collation when an XSLT package is reloaded.
     */
    @Override
    public String getCollationURI() {
        return NamespaceConstant.HTML5_CASE_BLIND_COLLATION_URI;
    }

    /**
     * Compare two string objects.
     *
     * @return &lt;0 if a&lt;b, 0 if a=b, &gt;0 if a&gt;b
     * @throws ClassCastException if the objects are of the wrong type for this Comparer
     * @param a the first string
     * @param b the second string
     */

    @Override
    public int compareStrings(UnicodeString a, UnicodeString b) {
        // Note that Java does UTF-16 code unit comparison, which is not the same as Unicode codepoint comparison
        // except in the "equals" case. So we have to do a character-by-character comparison
        return compareCS(a, b);
    }

    /**
     * Compare two UnicodeString objects. This is hand-coded to avoid converting the objects into
     * Strings.
     *
     * @return &lt;0 if a&lt;b, 0 if a=b, &gt;0 if a&gt;b
     * @throws ClassCastException if the objects are of the wrong type for this Comparer
     */

    @SuppressWarnings("Duplicates")
    private int compareCS(UnicodeString a, UnicodeString b) {
        long alen = a.length();
        long blen = b.length();
        long i = 0;
        long j = 0;
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
            int nexta = a.codePointAt(i++);
            int nextb = b.codePointAt(j++);
            if (nexta >= 'a' && nexta <= 'z') {
                nexta += 'A' - 'a';
            }
            if (nextb >= 'a' && nextb <= 'z') {
                nextb += 'A' - 'a';
            }
            int c = nexta - nextb;
            if (c != 0) {
                return c;
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
    public boolean comparesEqual(UnicodeString s1, UnicodeString s2) {
        return compareCS(s1, s2) == 0;
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
        return normalize(s1).indexOf(normalize(s2), 0) >= 0;
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
        return normalize(s1).hasSubstring(normalize(s2), s1.length() - s2.length());
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
        return normalize(s1).hasSubstring(normalize(s2), 0);
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
        long i = normalize(s1).indexOf(normalize(s2), 0);
        if (i < 0) {
            return EmptyUnicodeString.getInstance();
        }
        return s1.substring(i + s2.length(), s1.length());
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
        long j = normalize(s1).indexOf(normalize(s2), 0);
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
        return normalize(s);
    }

    /**
     * Normalize the strings prior to comparison for substring-comparison operations
     */

    private UnicodeString normalize(UnicodeString cs) {
        UnicodeBuilder sb = new UnicodeBuilder(cs.length32());
        IntIterator iter = cs.codePoints();
        while (iter.hasNext()) {
            int c = iter.next();
            if ('a' <= c && c <= 'z') {
                sb.append((char)(c + 'A' - 'a'));
            } else {
                sb.append(c);
            }
        }
        return sb.toUnicodeString();
    }
}

