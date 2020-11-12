////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trans.XPathException;


/**
 * A StringCollator that sorts lowercase before uppercase, or vice versa.
 * <p>Case is irrelevant, unless the strings are equal ignoring
 * case, in which case lowercase comes first.</p>
 */

public class CaseFirstCollator implements StringCollator {

    private StringCollator baseCollator;
    private boolean upperFirst;
    private String uri;

    /**
     * Create a CaseFirstCollator
     *  @param base       the base collator, which determines how characters are sorted irrespective of case
     * @param upperFirst true if uppercase precedes lowercase, false otherwise
     * @param collationURI the URI of the collation
     */

    public CaseFirstCollator(StringCollator base, boolean upperFirst, String collationURI) {
        this.baseCollator = base;
        this.upperFirst = upperFirst;
        this.uri = collationURI;
    }

    public static StringCollator makeCaseOrderedCollator(String uri, StringCollator stringCollator, String caseOrder) throws XPathException {
        switch (caseOrder) {
            case "lower-first":
                stringCollator = new CaseFirstCollator(stringCollator, false, uri);
                break;
            case "upper-first":
                stringCollator = new CaseFirstCollator(stringCollator, true, uri);
                break;
            default:
                throw new XPathException("case-order must be lower-first, upper-first, or #default");
        }
        return stringCollator;
    }

    /**
     * Get the collation URI. It must be possible to use this collation URI to reconstitute the collation
     *
     * @return a collation URI that can be used to reconstruct the collation when an XSLT package is reloaded.
     */
    @Override
    public String getCollationURI() {
        return uri;
    }

    /**
     * Compare two string objects: case is irrelevant, unless the strings are equal ignoring
     * case, in which case lowercase comes first.
     *
     * @return &lt;0 if a&lt;b, 0 if a=b, &gt;0 if a&gt;b
     * @throws ClassCastException if the objects are of the wrong type for this Comparer
     */

    @Override
    public int compareStrings(CharSequence a, CharSequence b) {
        int diff = baseCollator.compareStrings(a, b);
        if (diff != 0) {
            return diff;
        }

        // This is doing a character-by-character comparison, which isn't really right.
        // There might be a sequence of letters constituting a single collation unit.

        int i = 0;
        int j = 0;
        while (true) {
            // Skip characters that are equal in the two strings
            while (i < a.length() && j < b.length() && a.charAt(i) == b.charAt(j)) {
                i++;
                j++;
            }
            // Skip non-letters in the first string
            while (i < a.length() && !Character.isLetter(a.charAt(i))) {
                i++;
            }
            // Skip non-letters in the second string
            while (j < b.length() && !Character.isLetter(b.charAt(j))) {
                j++;
            }
            // If we've got to the end of either string, treat the strings as equal
            if (i >= a.length()) {
                return 0;
            }
            if (j >= b.length()) {
                return 0;
            }
            // If one of the characters is upper/lower case and the other isn't, the issue is decided
            boolean aFirst = upperFirst ? Character.isUpperCase(a.charAt(i++)) : Character.isLowerCase(a.charAt(i++));
            boolean bFirst = upperFirst ? Character.isUpperCase(b.charAt(j++)) : Character.isLowerCase(b.charAt(j++));
            if (aFirst && !bFirst) {
                return -1;
            }
            if (bFirst && !aFirst) {
                return +1;
            }
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
    public boolean comparesEqual(CharSequence s1, /*@NotNull*/ CharSequence s2) {
        return compareStrings(s1, s2) == 0;
    }

    /**
     * Get a collation key for two Strings. The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * compare correctly under the equals() method.
     * @param s
     */

    @Override
    public AtomicMatchKey getCollationKey(CharSequence s) {
        return baseCollator.getCollationKey(s);
    }

}

