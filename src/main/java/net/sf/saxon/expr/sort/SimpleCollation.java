////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.Platform;
import net.sf.saxon.Version;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.str.UnicodeString;

import java.text.RuleBasedCollator;
import java.util.Comparator;

/**
 * A simple collation that just wraps a supplied Comparator
 */

public class SimpleCollation implements StringCollator {

    private Comparator<? super String> comparator;
    private final String uri;
    /*@NotNull*/ private static final Platform platform = Version.platform;

    /**
     * Create a SimpleCollation
     *
     * @param uri the collation URI
     * @param comparator the Comparator that does the actual string comparison
     */

    public SimpleCollation(String uri, Comparator<? super String> comparator) {
        this.uri = uri;
        this.comparator = comparator;
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
     * Compares its two arguments for order.  Returns a negative integer,
     * zero, or a positive integer as the first argument is less than, equal
     * to, or greater than the second.
     *
     * @param o1 the first object to be compared.
     * @param o2 the second object to be compared.
     * @return a negative integer, zero, or a positive integer as the
     *         first argument is less than, equal to, or greater than the
     *         second.
     * @throws ClassCastException if the arguments' types prevent them from
     *                            being compared by this Comparator.
     */
    @Override
    public int compareStrings(UnicodeString o1, UnicodeString o2) {
        return comparator.compare(o1.toString(), o2.toString());
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
        return comparator.compare(s1.toString(), s2.toString()) == 0;
    }

    /**
     * Get the underlying comparator
     *
     * @return the underlying comparator
     */

    public Comparator<? super String> getComparator() {
        return comparator;
    }

    /**
     * Set the underlying comparator
     *
     * @param comparator the underlying comparator
     */

    public void setComparator(Comparator<? super String> comparator) {
        this.comparator = comparator;
    }

    /**
     * Get a collation key for a String. The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * compare correctly under the equals() method.
     * @param s the string whose collation key is required
     */

    @Override
    public AtomicMatchKey getCollationKey(UnicodeString s) {
        return platform.getCollationKey(this, s.toString());
    }

    /**
     * If possible, get a collator capable of substring matching (in functions such as fn:contains()).
     *
     * @return a collator suitable for substring matching, or null if none is available
     */

    public SubstringMatcher getSubstringMatcher() {
        if (comparator instanceof SubstringMatcher) {
            return (SubstringMatcher)comparator;
        }
        if (comparator instanceof RuleBasedCollator) {
            return new RuleBasedSubstringMatcher(uri, (RuleBasedCollator) comparator);
        }
        return null;
    }

}

