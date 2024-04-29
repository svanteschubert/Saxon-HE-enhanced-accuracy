////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.z.IntIterator;

import java.io.IOException;
import java.io.Writer;
import java.util.function.IntPredicate;

/**
 * A Unicode string consisting entirely of 16-bit BMP characters, implemented as a range
 * of an underlying byte array
 */

public class Slice16 extends UnicodeString {

    private char[] chars;
    private int start;
    private int end;
    private int cachedHash;

    /**
     * Create a slice of an underlying char array
     * @param chars the char array, containing Unicode codepoints in the range 0-65535;
     *              the caller warrants that there are no surrogate characters present
     * @param start the offset of the first character within the character array
     * @param end the offset of the first excluded character, so the length of the string
     *            is {@code end-start}
     */

    public Slice16(char[] chars, int start, int end) {
        this.chars = chars;
        this.start = start;
        this.end = end;
    }

    @Override
    public long length() {
        return end - start;
    }

    @Override
    public int getWidth() {
        return 16;
    }

    public char[] getCharArray() {
        return chars;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }


    @Override
    public long indexOf(int codePoint, long from) {
        if (codePoint > 65535) {
            return -1;
        }
        char b = (char)(codePoint & 0xffff);
        int limit = end;
        for (int i = start + requireNonNegativeInt(from); i < limit; i++) {
            if (chars[i] == b) {
                return i - start;
            }
        }
        return -1;
    }

    @Override
    public int codePointAt(long index) {
        int index32 = requireInt(index);
        if (index32 < 0 || index32 >= length32()) {
            throw new IndexOutOfBoundsException();
        }
        return (chars[start + index32]);
    }

    @Override
    public UnicodeString substring(long start, long end) {
        checkSubstringBounds(start, end);
        if (end == start) {
            return EmptyUnicodeString.getInstance();
        } else {
            return new Slice16(chars, requireInt(start) + this.start, requireInt(end) + this.start);
        }
    }

    private void write(Writer writer, long start, long len) throws IOException {
        writer.write(chars, this.start + requireInt(start), requireInt(len));
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
        for (int i = requireNonNegativeInt(from) + start; i < end; i++) {
            if (predicate.test(chars[i])) {
                return i - start;
            }
        }
        return -1;
    }

    void copy16bit(char[] target, int offset) {
        System.arraycopy(chars, start,target, offset, end - start);
    }

    void copy24bit(byte[] target, int offset) {
        for (int i = start, j = offset; i < end; ) {
            char c = chars[i++];
            target[j++] = 0;
            target[j++] = (byte) (c >> 8);
            target[j++] = (byte) (c & 0xff);
        }
    }

    /**
     * Copy this string, as a sequence of 32-bit codepoints, to a specified array
     *
     * @param target the target array: the caller must ensure there is sufficient capacity
     * @param offset the position in the target array as a codepoint offset
     */
    @Override
    void copy32bit(int[] target, int offset) {
        for (int i = start, j = offset; i < end;) {
            target[j++] = chars[i++];
        }
    }

    @Override
    public IntIterator codePoints() {
        return new IntIterator() {
            int i = start;

            @Override
            public boolean hasNext() {
                return i < end;
            }

            @Override
            public int next() {
                return chars[i++];
            }
        };
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
        if (cachedHash != 0) {
            return cachedHash;
        }
        int h = 0;
        for (int i = start; i < end; i++) {
            int b = chars[i];
            h = 31 * h + b;
        }
        return cachedHash = h;
    }

    /**
     * Convert to a string.
     */

    /*@NotNull*/
    public String toString() {
        return new String(chars, start, end - start);
    }

}

