////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.Configuration;
import net.sf.saxon.transpile.CSharpInnerClass;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.PositiveIntIterator;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.IntPredicate;


/**
 * A {@code Twine8} is a Unicode string whose codepoints are all in the range 0-255 (that is, Latin-1).
 * These are held in an array of bytes, one byte per character. The length of the string is limited
 * to 2^31-1 codepoints.
 */

public class Twine8 extends UnicodeString {

    // Array of bytes containing codepoints in the range 0-255
    protected byte[] bytes;
    // Cached hash code
    protected int cachedHash = 0;

    /**
     * Constructor
     * @param bytes the byte array containing the characters in the range 0-255. The caller must ensure that
     *              this array is immutable.
     */

    public Twine8(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Constructor
     *
     * @param bytes the byte array containing the characters in the range 0-255.
     * @param start the position of the first byte to use
     * @param end the position of the first byte to not be used
     */
    public Twine8(byte[] bytes, int start, int end) {
        this(Arrays.copyOfRange(bytes, start, end));
    }

    /**
     * Create a Twine8 from an array of characters that are known to be single byte chars
     * @param chars character array, all characters in range must be LE 255
     * @param start offset of first character to be used
     * @param len number of characters to be used
     */
    public Twine8(char[] chars, int start, int len) {
        bytes = new byte[len];
        for (int i = start; i < len; i++) {
            int c = chars[i];
            if (CHECKING && c > 255) {
                throw new IllegalArgumentException();
            }
            bytes[i] = (byte) (chars[i] & 0xff);
        }
    }

    private final static boolean CHECKING = Configuration.isAssertionsEnabled();

    /**
     * Create a Twine8 from a string whose characters are known to be single byte chars
     *
     * @param str the value, all characters in range must be LE 255
     */
    
    public Twine8(String str) {
        bytes = fromString(str);
    }

    @CSharpReplaceBody(code="return System.Text.Encoding.Latin1.GetBytes(str);")
    private static byte[] fromString(String str) {
        return str.getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Get an array of bytes holding the characters of the string in their Latin-1 encoding
     * @return the bytes making up the string
     */

    public byte[] getByteArray() {
        return bytes;
    }

    /**
     * Get the length of this string, in codepoints
     *
     * @return the length of the string in Unicode code points
     */

    @Override
    public long length() {
        return bytes.length;
    }

    @Override
    public int length32() {
        return bytes.length;
    }

    void copy8bit(byte[] target, int offset) {
        System.arraycopy(bytes, 0, target, offset, bytes.length);
    }

    void copy16bit(char[] target, int offset) {
        for (int i=0, j=offset; i<bytes.length; ) {
             target[j++] = (char)(bytes[i++] & 0xff);
        }
    }

    void copy24bit(byte[] target, int offset) {
        for (int i = 0, j = offset; i < bytes.length; ) {
            target[j++] = (byte) 0;
            target[j++] = (byte) 0;
            target[j++] = bytes[i++];
        }
    }

    @Override
    void copy32bit(int[] target, int offset) {
        for (int i = 0, j = offset; i < bytes.length; ) {
            target[j++] = bytes[i++] & 0xff;
        }
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
        long len = length();
        if (start < 0 || end < start || end > len) {
            throw new IndexOutOfBoundsException();
        }
        if (end == start) {
            return EmptyUnicodeString.getInstance();
        } else if (start == 0 && end == len) {
            return this;
        } else {
            return new Slice8(bytes, requireInt(start), requireInt(end));
        }

    }


    @Override
    public int codePointAt(long index) throws IndexOutOfBoundsException {
        int index32 = requireInt(index);
        if (index32 < 0 || index32 >= length32()) {
            throw new IndexOutOfBoundsException();
        }
        return (int) bytes[index32] & 0xff;
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
        int from32 = requireNonNegativeInt(from);
        if (from32 >= length32()) {
            return -1;
        }
        int last = bytes.length;
        if (codePoint < 0 || codePoint > 255) {
            return -1;
        }
        for (int i = from32; i < last; i++) {
            if ((bytes[i] & 0xff) == codePoint) {
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
        if (from < 0) {
            from = 0L;
        } else if (from >= length()) {
            return -1;
        }
        if (other.isEmpty()) {
            return from;
        }
        int initial = other.codePointAt(0);
        int len = requireInt(other.length());
        int lastPossible = length32() - len;
        while (from <= lastPossible) {
            long i = indexOf(initial, from);
            if (i < 0) {
                return -1;
            }
            if (hasSubstring(other, i)) {
                return i;
            }
            from = i + 1L;
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
        return 8;
    }

    /**
     * Get an iterator over the Unicode codepoints in the value. These will always be full codepoints, never
     * surrogates (surrogate pairs are combined where necessary).
     *
     * @return a sequence of Unicode codepoints
     */

    @Override
    @CSharpInnerClass(outer=false, extra={"byte[] bytes"})
    public IntIterator codePoints() {
        return new IntIterator() {
            int i = 0;

            @Override
            //@CSharpModifiers(code = {"public", "override"})
            public boolean hasNext() {
                return i < bytes.length;
            }

            @Override
            public int next() {
                return bytes[i++] & 0xff;
            }
        };
    }

    @Override
    @CSharpInnerClass(outer = false, extra = {"byte[] bytes"})
    public PositiveIntIterator iterate() {
        return new PositiveIntIterator() {
            int i = 0;

            @Override
            public int next() {
                return i < bytes.length ? bytes[i++] & 0xff : -1;
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
        return cachedHash = super.hashCode();
    }

    /**
     * Test whether this StringValue is equal to another under the rules of the codepoint collation.
     * The type annotation is ignored.
     *
     * @param o the value to be compared with this value
     * @return true if the strings are equal on a codepoint-by-codepoint basis
     */

    public boolean equals(Object o) {
        if (o instanceof Twine8 other) {
            if (this.length32() != other.length32()) {
                return false;
            }
            if (this.cachedHash != 0 && other.cachedHash != 0 & this.cachedHash != other.cachedHash) {
                return false;
            }
            return Arrays.equals(bytes, other.bytes);
        }
        return super.equals(o);
    }

    @Override
    public int compareTo(UnicodeString other) {
        if (other instanceof Twine8) {
            // TODO: for Java9, use Arrays.compareUnsigned(bytes, o.bytes);
            Twine8 o = (Twine8) other;
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
    @CSharpReplaceBody(code="return Saxon.Impl.Helpers.StringUtils.LATIN_1.GetString(bytes);")
    public String toString() {
        return new String(bytes, StandardCharsets.ISO_8859_1);
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
            if (predicate.test(bytes[i] & 0xff)) {
                return i;
            }
        }
        return -1;
    }

    public String details() {
        return "Twine8 bytes.length = " + bytes.length;
    }

}

