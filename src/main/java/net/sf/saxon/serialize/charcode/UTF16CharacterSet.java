////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize.charcode;

import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntPredicateProxy;

/**
 * A class to hold some static constants and methods associated with processing UTF16 and surrogate pairs
 */

public class UTF16CharacterSet implements CharacterSet {

    private static final UTF16CharacterSet theInstance = new UTF16CharacterSet();

    /**
     * Private constructor to force the singular instance to be used
     */

    private UTF16CharacterSet() {
    }

    /**
     * Get the singular instance of this class
     *
     * @return the singular instance of this class
     */

    public static UTF16CharacterSet getInstance() {
        return theInstance;
    }

    @Override
    public boolean inCharset(int c) {
        return true;
    }

    @Override
    public String getCanonicalName() {
        return "UTF-16";
    }


    public static final int NONBMP_MIN = 0x10000;
    public static final int NONBMP_MAX = 0x10FFFF;

    public static final char SURROGATE1_MIN = (char)0xD800;
    public static final char SURROGATE1_MAX = (char)0xDBFF;
    public static final char SURROGATE2_MIN = (char)0xDC00;
    public static final char SURROGATE2_MAX = (char)0xDFFF;

    /**
     * Return the non-BMP character corresponding to a given surrogate pair
     * surrogates.
     *
     * @param high The high surrogate.
     * @param low  The low surrogate.
     * @return the Unicode codepoint represented by the surrogate pair
     */
    public static int combinePair(char high, char low) {
        return (high - SURROGATE1_MIN) * 0x400 + (low - SURROGATE2_MIN) + NONBMP_MIN;
    }

    /**
     * Return the high surrogate of a non-BMP character
     *
     * @param ch The Unicode codepoint of the non-BMP character to be divided.
     * @return the first character in the surrogate pair
     */

    public static char highSurrogate(int ch) {
        return (char) (((ch - NONBMP_MIN) >> 10) + SURROGATE1_MIN);
    }

    /**
     * Return the low surrogate of a non-BMP character
     *
     * @param ch The Unicode codepoint of the non-BMP character to be divided.
     * @return the second character in the surrogate pair
     */

    public static char lowSurrogate(int ch) {
        return (char) (((ch - NONBMP_MIN) & 0x3FF) + SURROGATE2_MIN);
    }

    /**
     * Test whether a given character is a surrogate (high or low)
     *
     * @param c the character to test
     * @return true if the character is the high or low half of a surrogate pair
     */

    public static boolean isSurrogate(int c) {
        return (c & 0xF800) == 0xD800;
    }

    /**
     * Test whether the given character is a high surrogate
     *
     * @param ch The character to test.
     * @return true if the character is the first character in a surrogate pair
     */
    public static boolean isHighSurrogate(int ch) {
        return (SURROGATE1_MIN <= ch && ch <= SURROGATE1_MAX);
    }

    /**
     * Test whether the given character is a low surrogate
     *
     * @param ch The character to test.
     * @return true if the character is the second character in a surrogate pair
     */

    public static boolean isLowSurrogate(int ch) {
        return (SURROGATE2_MIN <= ch && ch <= SURROGATE2_MAX);
    }

    /**
     * Test whether all the characters in a CharSequence are valid XML characters
     *
     * @param iter iterator over the character sequence to be tested
     * @param predicate the predicate that all characters must satisfy
     * @return the codepoint of the first invalid character in the character sequence (according to the supplied predicate);
     *         or -1 if all characters in the character sequence are valid
     */

    public static int firstInvalidChar(IntIterator iter, IntPredicateProxy predicate) {
        while (iter.hasNext()) {
            int ch32 = iter.next();
            if (!predicate.test(ch32)) {
                return ch32;
            }
        }
        return -1;
    }

}

