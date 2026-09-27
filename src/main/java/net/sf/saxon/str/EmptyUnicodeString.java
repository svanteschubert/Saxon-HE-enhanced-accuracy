////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.expr.sort.EmptyIntIterator;
import net.sf.saxon.z.IntIterator;

import java.util.function.IntPredicate;


/**
 * A zero-length Unicode string
 */

public final class EmptyUnicodeString extends UnicodeString {

    private final static EmptyUnicodeString INSTANCE = new EmptyUnicodeString();

    public static EmptyUnicodeString getInstance() {
        return INSTANCE;
    }

    private EmptyUnicodeString() {
    }

    /**
     * Get the length of this string, in codepoints
     *
     * @return the length of the string in Unicode code points
     */

    @Override
    public long length() {
        return 0;
    }

    @Override
    public int length32() {
        return 0;
    }

    /**
     * Concatenate another string
     *
     * @param other the string to be appended to this one
     * @return the result of the concatenation (neither input string is altered)
     */
    @Override
    public UnicodeString concat(UnicodeString other) {
        return other;
    }

    @Override
    void copy8bit(byte[] target, int offset) {
        // no action
    }

    @Override
    void copy16bit(char[] target, int offset) {
        // no action
    }

    @Override
    void copy24bit(byte[] target, int offset) {
        // no action
    }

    @Override
    void copy32bit(int[] target, int offset) {
        // no action
    }

    /**
     * Get a substring of this string (following the rules of {@link String#substring}, but measuring
     * Unicode codepoints rather than 16-bit code units)
     *
     * @param start the offset of the first character to be included in the result, counting Unicode codepoints
     * @param end   the offset of the first character to be excluded from the result, counting Unicode codepoints
     * @return the substring
     */

    @Override
    public UnicodeString substring(long start, long end) {
        checkSubstringBounds(start, end);
        return this;
    }

    @Override
    public int codePointAt(long index) throws IndexOutOfBoundsException {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public long indexOf(int code, long from) {
        return -1;
    }

    /**
     * Get the first position, at or beyond start, where another string appears as a substring
     * of this string, comparing codepoints.
     *
     * @param other the other (sought) string
     * @param from  the position (0-based) where searching is to start (counting in codepoints)
     * @return the first position where the substring is found, or -1 if it is not found. Also returns
     * -1 if {@code from} is negative, or beyond the length of the string.
     */

    @Override
    public long indexOf(UnicodeString other, long from) {
        return other.isEmpty() && from == 0 ? 0 : -1;
    }


    /**
     * Determine whether the string is a zero-length string. This may
     * be more efficient than testing whether the length is equal to zero
     *
     * @return true if the string is zero length
     */

    @Override
    public boolean isEmpty() {
        return true;
    }
    
    @Override
    public int getWidth() {
        return 0;
    }

    /**
     * Get an iterator over the Unicode codepoints in the value. These will always be full codepoints, never
     * surrogates (surrogate pairs are combined where necessary).
     *
     * @return a sequence of Unicode codepoints
     */

    @Override
    public IntIterator codePoints() {
        return EmptyIntIterator.getInstance();
    }

    /**
     * Compute a hashCode. All implementations of {@code UnicodeString} use compatible hash codes and the
     * hashing algorithm is therefore identical to that for {@code java.lang.String}. This means
     * that for strings containing Astral characters, the hash code needs to be computed by decomposing
     * an Astral character into a surrogate pair.
     *
     * @return the hash code
     */

    public int hashCode() {
        return 0;
    }

    /**
     * Test whether this UnicodeString is equal to another under the rules of the codepoint collation.
     *
     * @param o the value to be compared with this value
     * @return true if the strings are equal on a codepoint-by-codepoint basis
     */

    public boolean equals(Object o) {
        if (o instanceof UnicodeString) {
            return ((UnicodeString) o).isEmpty();
        }
        return false;
    }

    @Override
    public int compareTo(UnicodeString other) {
        return other.isEmpty() ? 0 : -1;
    }

    /**
     * Display as a string.
     */

    /*@NotNull*/
    public String toString() {
        return "";
    }

    /**
     * Get the position of the first occurrence of the specified codepoint,
     * starting the search at a given position in the string
     *
     * @param predicate condition that the codepoint must satisfy
     * @param from      the position from which the search should start (0-based)
     * @return the position (0-based) of the first codepoint to match the predicate, or -1 if not found
     * @throws UnsupportedOperationException if the {@code UnicodeString} has not been prepared
     *                                       for codePoint access
     */
    @Override
    public long indexWhere(IntPredicate predicate, long from) {
        return -1;
    }

    public String details() {
        return "empty string";
    }

}

