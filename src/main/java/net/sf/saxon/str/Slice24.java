////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.z.IntIterator;

import java.util.function.IntPredicate;

/**
 * A Unicode string consisting of 24-bit characters, implemented as a range
 * of an underlying byte array holding three bytes per codepoint
 */

public class Slice24 extends UnicodeString {

    private final byte[] bytes;
    private final int start;
    private final int end;
    private int cachedHash;

    /**
     * Create a slice of an underlying byte array
     * @param bytes the byte array, containing Unicode codepoints in the range 0-255
     * @param start the codepoint offset of the first character within the byte array
     * @param end the codepoint offset of the first excluded character, so the length of the string
     *            is {@code end-start}
     */

    public Slice24(byte[] bytes, int start, int end) {
        this.bytes = bytes;
        this.start = start;
        this.end = end;
//        if (Configuration.isAssertionsEnabled()) {
//            verifyCharacters();
//        }
    }

    @Override
    public long length() {
        return end - start;
    }

    @Override
    public int getWidth() {
        return 24;
    }

    @Override
    public long indexOf(int codePoint, long from) {
        byte b0 = (byte) ((codePoint >> 16) & 0xff);
        byte b1 = (byte) ((codePoint >> 8) & 0xff);
        byte b2 = (byte) (codePoint & 0xff);
        for (int i = (start + requireNonNegativeInt(from))*3; i < end*3; i+=3) {
            if (bytes[i+2] == b2 && bytes[i+1] == b1 && bytes[i] == b0) {
                return i/3 - start;
            }
        }
        return -1;
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
        for (int i = (start + requireInt(from)) * 3; i < end * 3; i += 3) {
            int cp = ((bytes[i] << 16 | (bytes[i + 1] & 0xff) << 8) | (bytes[i + 2] & 0xff)) & 0xffffff;
            if (predicate.test(cp)) {
                return i/3 - start;
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
        int offset = (start + index32) * 3;
        return ((bytes[offset] << 16 | (bytes[offset + 1] & 0xff) << 8) | (bytes[offset + 2] & 0xff)) & 0xffffff;
    }

    @Override
    public UnicodeString substring(long start, long end) {
        checkSubstringBounds(start, end);
        if (end == start) {
            return EmptyUnicodeString.getInstance();
        } else {
            return new Slice24(bytes, requireInt(start) + this.start, requireInt(end) + this.start);
        }
    }

    void copy24bit(byte[] target, int offset) {
        System.arraycopy(bytes, start*3, target, offset, (end - start)*3);
    }

    @Override
    public IntIterator codePoints() {
        return new IntIterator() {
            int i = start*3;
            int j = end*3;

            @Override
            public boolean hasNext() {
                return i < j;
            }

            @Override
            public int next() {
                int result = ((bytes[i] & 0xff) << 16)
                        | ((bytes[i + 1] & 0xff) << 8)
                        | ((bytes[i + 2] & 0xff));
                i += 3;
                return result;
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
        for (int i = start*3; i < end*3; i += 3) {
            int cp = ((bytes[i] << 16 | (bytes[i + 1] & 0xff) << 8) | (bytes[i + 2] & 0xff)) & 0xffffff;
            if ((cp & 0xff0000) != 0) {
                h = 31 * h + UTF16CharacterSet.highSurrogate(cp);
                h = 31 * h + UTF16CharacterSet.lowSurrogate(cp);
            } else {
                h = 31 * h + cp;
            }
        }
        return cachedHash = h;
    }

    /**
     * Display as a string.
     */

    /*@NotNull*/
    public String toString() {
        StringBuilder sb = new StringBuilder(length32());
        IntIterator iter = codePoints();
        while (iter.hasNext()) {
            int x = iter.next();
            sb.appendCodePoint(x);
        }
        return sb.toString();
    }



}

