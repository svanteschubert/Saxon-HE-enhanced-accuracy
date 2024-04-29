////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntSingletonIterator;

import java.util.function.IntPredicate;

/**
 * A UnicodeString containing a single codepoint
 */

public class UnicodeChar extends UnicodeString {

    private final int codepoint;

    public UnicodeChar(int codepoint) {
        this.codepoint = codepoint;
    }

    /**
     * Get an iterator over the code points present in the string. Note that this method
     * is always available, whether or not the {@code UnicodeString} has been prepared for codePoint access.
     *
     * @return an iterator that delivers the individual code points
     */
    @Override
    public IntIterator codePoints() {
        return new IntSingletonIterator(codepoint);
    }

    /**
     * Get the codepoint represented by this {@code UnicodeChar}
     * @return the relevant codepoint
     */

    public int getCodepoint() {
        return codepoint;
    }

    /**
     * Get the length of the string
     *
     * @return the number of code points in the string
     */
    @Override
    public long length() {
        return 1;
    }

    /**
     * Get the number of bits needed to hold all the characters in this string
     *
     * @return 7 for ascii characters, 8 for latin-1, 16 for BMP, 24 for general Unicode.
     */
    @Override
    public int getWidth() {
        if (codepoint < 128) {
            return 7;
        } else if (codepoint < 256) {
            return 8;
        } else if (codepoint < 65536) {
            return 16;
        } else {
            return 24;
        }
    }

    /**
     * Get the position of the first occurrence of the specified codepoint,
     * starting the search at a given position in the string
     *
     * @param codePoint the sought codePoint
     * @param from      the position from which the search should start (0-based)
     * @return the position (0-based) of the first occurrence found, or -1 if not found
     */
    @Override
    public long indexOf(int codePoint, long from) {
        return (this.codepoint == codePoint && from <= 0) ? 0 : -1;
    }

    /**
     * Get the code point at a given position in the string
     *
     * @param index the given position (0-based)
     * @throws IndexOutOfBoundsException if index is not zero
     */
    @Override
    public int codePointAt(long index) {
        if (index == 0) {
            return codepoint;
        } else {
            throw new IndexOutOfBoundsException("Only valid index for a single-character string is zero");
        }
    }

    /**
     * Get a substring of this codepoint sequence, with a given start and end position
     *
     * @param start the start position (0-based): that is, the position of the first
     *              code point to be included
     * @param end   the end position (0-based): specifically, the position of the first
     *              code point not to be included
     * @throws IndexOutOfBoundsException if the start/end positions are out of range
     */
    @Override
    public UnicodeString substring(long start, long end) {
        checkSubstringBounds(start, end);
        if (start == 0 && end == 1) {
            return this;
        } else {
            return EmptyUnicodeString.getInstance();
        }
    }

    /**
     * Get the position of the first occurrence of the specified codepoint,
     * starting the search at a given position in the string
     *
     * @param predicate condition that the codepoint must satisfy
     * @param from      the position from which the search should start (0-based)
     * @return the position (0-based) of the first codepoint to match the predicate, or -1 if not found
     */
    @Override
    public long indexWhere(IntPredicate predicate, long from) {
        return (from == 0 && predicate.test(codepoint)) ? 0 : -1;
    }

    @Override
    public String toString() {
        if (codepoint < 65536) {
            return "" + (char)codepoint;
        } else {
            return "" + UTF16CharacterSet.highSurrogate(codepoint) + UTF16CharacterSet.lowSurrogate(codepoint);
        }
    }

    @Override
    void copy8bit(byte[] target, int offset) {
        target[offset] = (byte)(codepoint & 0xFF);
    }

    @Override
    void copy16bit(char[] target, int offset) {
        target[offset] = (char)codepoint;
    }

    @Override
    void copy24bit(byte[] target, int offset) {
        target[offset] = (byte)(codepoint>>16);
        target[offset+1] = (byte) (codepoint >> 8);
        target[offset+2] = (byte) (codepoint & 0xff);
    }

    @Override
    void copy32bit(int[] target, int offset) {
        target[offset] = codepoint;
    }
}

