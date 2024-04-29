////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.z.IntIterator;

import java.util.Arrays;
import java.util.function.IntPredicate;


/**
 * {@code Twine16} is a Unicode string consisting entirely of codepoints in the range 0-65535
 * (that is, the basic multilingual plane), excluding surrogates. The number of codepoints
 * is limited to 2^31-1.
 */

public class Twine16 extends UnicodeString {

    // Array of chars holding the codepoints. Note that there will be no surrogates
    protected char[] chars;
    // Cached hash code
    protected int cachedHash = 0;

    /**
     * Protected constructor
     * @param chars the 16-bit characters comprising the string: must not include any surrogates
     */

    protected Twine16(char[] chars) {
        this.chars = chars;
    }

    /**
     * Constructor taking an array of 16-bit chars, or a substring thereof. The caller warrants that
     * the characters are all BMP characters (no surrogate pairs). The character array is copied,
     * so it can be reused and modified after the call.
     * @param chars the array of characters (must not include any surrogates)
     * @param start start offset into the array
     * @param len the number of characters to be included.
     */

    public Twine16(char[] chars, int start, int len) {
        if (start == 0) {
            this.chars = Arrays.copyOf(chars, len);
        } else {
            this.chars = new char[len];
            System.arraycopy(chars, start, this.chars, 0, len);
        }
    }



    public char[] getCharArray() {
        return chars;
    }

    /**
     * Get the length of this string, in codepoints
     *
     * @return the length of the string in Unicode code points
     */

    @Override
    public long length() {
        return chars.length;
    }

    @Override
    public int length32() {
        return chars.length;
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
            return new Slice16(chars, start32, end32);
        }

    }

    @Override
    public int codePointAt(long index) throws IndexOutOfBoundsException {
        int index32 = requireInt(index);
        if (index32 < 0 || index32 >= length32()) {
            throw new IndexOutOfBoundsException();
        }
        return chars[index32];
    }

    /**
     * Get the first position, at or beyond start, where a given codepoint appears
     * in this string.
     *
     * @param codePoint the sought codepoint
     * @param from the position (0-based) where searching is to start (counting in codepoints)
     * @return the first position where the substring is found, or -1 if it is not found
     */

    @Override
    public long indexOf(int codePoint, long from) {
        if (codePoint < 0 || codePoint > 65535) {
            return -1;
        }
        int from32 = requireNonNegativeInt(from);
        int last = chars.length;
        for (int i = from32; i < last; i++) {
            if (chars[i] == codePoint) {
                return i;
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
        return chars.length == 0;
    }

    void copy16bit(char[] target, int offset) {
        System.arraycopy(chars, 0, target, offset, chars.length);
    }

    void copy24bit(byte[] target, int offset) {
        for (int i=0, j=offset; i<chars.length;) {
            char c = chars[i++];
            target[j++] = 0;
            target[j++] = (byte) (c>>8);
            target[j++] = (byte) (c&0xff);
        }
    }

    @Override
    void copy32bit(int[] target, int offset) {
        for (int i = 0, j = offset; i < chars.length; ) {
            target[j++] = chars[i++];
        }
    }

    @Override
    public int getWidth() {
        return 16;
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
            //@CSharpModifiers(code = {"public"})
            public boolean hasNext() {
                return i < chars.length;
            }

            @Override
            //@CSharpModifiers(code = {"public"})
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
        for (int cp : chars) {
            h = 31 * h + cp;
        }
        return cachedHash = h;
    }

    /**
     * Test whether this StringValue is equal to another under the rules of the codepoint collation.
     * The type annotation is ignored.
     *
     * @param o the value to be compared with this value
     * @return true if the strings are equal on a codepoint-by-codepoint basis
     */

    public boolean equals(Object o) {
        if (o instanceof Twine16) {
            return Arrays.equals(chars, ((Twine16)o).chars);
        }
        return super.equals(o);
    }

    @Override
    public int compareTo(UnicodeString other) {
        if (other instanceof Twine16) {
            // Java9: return Arrays.compare(chars, ((Twine16)other).chars);
            Twine16 o = (Twine16) other;
            char[] a = chars;
            char[] b = o.chars;
            int len = Math.min(a.length, b.length);
            for (int i = 0; i < len; i++) {
                int diff = a[i] - b[i];
                if (diff != 0) {
                    return diff;
                }
            }
            return Long.compare(length(), o.length());
        } else {
            return super.compareTo(other);
        }
    }

    /**
     * Convert to a string.
     */

    /*@NotNull*/
    public String toString() {
        return new String(chars);
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
            if (predicate.test(chars[i])) {
                return i;
            }
        }
        return -1;
    }

    public String details() {
        return "Twine16 length = " + chars.length;
    }

}

