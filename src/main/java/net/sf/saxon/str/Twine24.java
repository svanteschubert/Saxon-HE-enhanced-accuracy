////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.z.IntIterator;

import java.util.Arrays;
import java.util.function.IntPredicate;


/**
 * A {@code Twine24} is a Unicode string that accommodates any codepoint value up to 24 bits.
 * It never includes any surrogates. The length of the string is limited to 2^31-1 bytes, with
 * a fixed allocation of 3 bytes per codepoint.
 */

public class Twine24 extends UnicodeString {

    // We hold the value as an array of bytes, with 3 bytes per character

    // Array of bytes, 3 bytes per character
    protected byte[] bytes;
    // Cached hash code
    protected int cachedHash = 0;

    /**
     * Protected constructor
     * @param bytes the Unicode characters, three bytes per character
     */

    protected Twine24(byte[] bytes) {
        this.bytes = bytes;
//        if (Configuration.isAssertionsEnabled()) {
//            verifyCharacters();
//        }
    }



    /**
     * Construct a {@code Twine} from an array of codepoints.
     *
     * @param codePoints the codepoints making up the string: must not contain any surrogates
     *                   (that is, codepoints higher than 65535 must be supplied as a single unit)
     * @param used the number of codepoints within the array that are actually used; anything beyond is
     *             ignored
     */

    public Twine24(int[] codePoints, int used) {
        bytes = new byte[used * 3];
        for (int i = 0, j = 0; i < used; i++, j += 3) {
            int c = codePoints[i];
            bytes[j] = (byte) ((c >> 16) & 0xff);
            bytes[j + 1] = (byte) ((c >> 8) & 0xff);
            bytes[j + 2] = (byte) (c & 0xff);
        }
    }

    /**
     * Construct a {@code Twine24} from an array of codepoints.
     *
     * @param codePoints the codepoints making up the string: must not contain any surrogates
     *                   (that is, codepoints higher than 65535 must be supplied as a single unit)
     */

    public Twine24(int[] codePoints) {
        this(codePoints, codePoints.length);
    }

    /**
     * Get the underlying byte array
     * @return the underlying byte array, with each codepoint occupying three consecutive
     * bytes (most significant first).
     */
    public byte[] getByteArray() {
        return bytes;
    }

    /**
     * Get the length of this string, in codepoints
     * @return the length of the string in Unicode code points
     */

    @Override
    public long length() {
        return bytes.length / 3;
    }

    @Override
    public int length32() {
        return bytes.length / 3;
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
        int start32 = requireInt(start);
        int end32 = requireInt(end);
        int len = length32();
        checkSubstringBounds(start, end);
        if (end == start) {
            return EmptyUnicodeString.getInstance();
        } else if (start == 0 && end == len) {
            return this;
        } else {
            return new Slice24(bytes, start32, end32);
        }

    }

    @Override
    public int codePointAt(long index) throws IndexOutOfBoundsException {
        int index32 = requireInt(index);
        if (index32 < 0 || index32 >= length32()) {
            throw new IndexOutOfBoundsException();
        }
        int offset = index32 * 3;
        return ((bytes[offset] << 16 | (bytes[offset + 1] & 0xff) << 8) | (bytes[offset + 2] & 0xff)) & 0xffffff;
    }

    /**
     * Get the first position, at or beyond start, where a given codepoint appears
     * in this string.
     *
     * @param code the sought codepoint
     * @param from the position (0-based) where searching is to start (counting in codepoints)
     * @return the first position where the substring is found, or -1 if it is not found
     */

    @Override
    public long indexOf(int code, long from) {
        int from32 = requireNonNegativeInt(from);
        if (from32 >= length32()) {
            return -1;
        }

        int last = bytes.length;
        if (code < 0 || code > 0xffffff) {
            return -1;
        }
        byte a = (byte) (code >> 16 & 0xff);
        byte b = (byte) (code >> 8 & 0xff);
        byte c = (byte) (code & 0xff);
        for (int i = from32*3; i < last; i += 3) {
            if (bytes[i + 2] == c && bytes[i + 1] == b && bytes[i] == a) {
                return (i / 3);
            }
        }
        return -1;
    }

    /**
     * Get the first position, at or beyond start, where another string appears as a substring
     * of this string, comparing codepoints.
     *
     * @param other the other (sought) string
     * @param from  the position (0-based) where searching is to start (counting in codepoints)
     * @return the first position where the substring is found, or -1 if it is not found
     */

    @Override
    public long indexOf(UnicodeString other, long from) {
        int from32 = requireInt(from);
        if (from32 < 0) {
            from32 = 0;
        } else if (from32 >= length32()) {
            return -1;
        }
        if (other.isEmpty()) {
            return from;
        }
        int initial = other.codePointAt(0);
        int len = requireInt(other.length());
        int lastPossible = length32() - len;
        while (from32 <= lastPossible) {
            int i = requireInt(indexOf(initial, from32));
            if (i < 0) {
                return -1;
            }
            if (hasSubstring(other, i)) {
                return i;
            }
            from32 = i + 1;
        }
        return -1;
    }


    /**
     * Determine whether the string is a zero-length string. This may
     * be more efficient than testing whether the length is equal to zero
     *
     * @return true if the string is zero length
     */

    @Override
    public boolean isEmpty() {
        return bytes.length == 0;
    }


    @Override
    public int getWidth() {
        return 24;
    }

    /**
     * Get an iterator over the Unicode codepoints in the value. These will always be full codepoints, never
     * surrogates (surrogate pairs are combined where necessary).
     *
     * @return a sequence of Unicode codepoints
     */

    @Override
    public IntIterator codePoints() {
        return new IntIterator() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < bytes.length;
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
        int end = bytes.length;

        for (int i = 0; i < end; i += 3) {
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
     * Test whether this string is equal to another under the rules of the codepoint collation.
     *
     * @param o the value to be compared with this value
     * @return true if the strings are equal on a codepoint-by-codepoint basis
     */

    public boolean equals(Object o) {
        if (o instanceof Twine24) {
            if (hashCode() != o.hashCode()) {
                return false;
            }
            return Arrays.equals(bytes, ((Twine24)o).bytes);
        }
        return super.equals(o);
    }

    @Override
    public int compareTo(UnicodeString other) {
        if (other instanceof Twine24) {
            // TODO: for Java9, use Arrays.compareUnsigned(bytes, o.bytes);
            Twine24 o = (Twine24) other;
            byte[] a = bytes;
            byte[] b = o.bytes;
            int len = Math.min(a.length, b.length);
            for (int i = 0; i < len; i++) {
                int diff = (a[i] & 0xff) - (b[i] & 0xff);
                if (diff != 0) {
                    return diff;
                }
            }
            return Integer.compare(a.length, b.length);
        } else {
            return super.compareTo(other);
        }
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

    void copy24bit(byte[] target, int offset) {
        System.arraycopy(bytes, 0, target, offset, bytes.length);
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
        for (int i = requireNonNegativeInt(from); i < length(); i++) {
            int offset = i * 3;
            int cp = ((bytes[offset] << 16 | (bytes[offset + 1] & 0xff) << 8) | (bytes[offset + 2] & 0xff)) & 0xffffff;
            if (predicate.test(cp)) {
                return i;
            }
        }
        return -1;
    }

    public String details() {
        return "Twine24 bytes.length = " + bytes.length;
    }

}

