////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.serialize.charcode.XMLCharacterData;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Base64BinaryValue;
import net.sf.saxon.z.IntIterator;

import java.util.function.IntPredicate;

/**
 * A UnicodeString is a sequence of Unicode codepoints that supports codepoint addressing.
 * <p>The interface is future-proofed to support code points in the range 0 to 2^31, and string lengths of
 * up to 2^63 characters. Implementations may (and do) impose lower limits.</p>
 */
public abstract class UnicodeString implements AtomicMatchKey, Comparable<UnicodeString> {

    /**
     * Ensure that the implementation is capable of counting codepoints in the string. This is
     * normally a null operation, but it may cause internal reorganisation.
     * @return this {@code UnicodeString}, or another that represents the same sequence
     * of characters.
     */

    public UnicodeString tidy() {
        return this;
    }

    public UnicodeString economize() {
        return this;
    }

    /**
     * Get the length of the string
     * @return the number of code points in the string
     */

    public abstract long length();

    /**
     * Get the length of the string, provided it is less than 2^31 characters
     * @return the length of the string if it fits within a Java {@code int}
     * @throws UnsupportedOperationException if the string is longer than 2^31 characters
     */

    public int length32() {
        return requireInt(length());
    }

    /**
     * Get the estimated length of the string, suitable for space allocation.
     *
     * @return for a {@code UnicodeString}, the actual length of the string in codepoints
     */

    public long estimatedLength() {
        return length();
    }

    /**
     * Ask whether the string is empty
     * @return true if the length of the string is zero
     */

    public boolean isEmpty() {
        return length() == 0;
    }

    /**
     * Get the number of bits needed to hold all the characters in this string
     * @return 7 for ascii characters (not used??), 8 for latin-1, 16 for BMP, 24 for general Unicode.
     */

    public abstract int getWidth();

    /**
     * Get the position of the first occurrence of the specified codepoint,
     * starting the search at the beginning
     *
     * @param codePoint the sought codePoint
     * @return the position (0-based) of the first occurrence found, or -1 if not found,
     * counting codePoints rather than UTF16 chars.
     * @throws UnsupportedOperationException if the {@code UnicodeString} has not been prepared
     * for codePoint access
     */

    public long indexOf(int codePoint) {
        return indexOf(codePoint, 0);
    }

    /**
     * Get the position of the first occurrence of the specified codepoint,
     * starting the search at a given position in the string
     *
     * @param codePoint the sought codePoint
     * @param from      the position from which the search should start (0-based). A negative value is
     *                  treated as zero. A position beyond the end of the string results in a return
     *                  value of -1 (meaning not found).
     * @return the position (0-based) of the first occurrence found, or -1 if not found
     * @throws UnsupportedOperationException if the {@code UnicodeString} has not been prepared
     *                                       for codePoint access
     */

    public abstract long indexOf(int codePoint, long from);

    /**
     * Get the position of the first occurrence of a codepoint that matches a supplied predicate,
     * starting the search at a given position in the string
     *
     * @param predicate condition that the codepoint must satisfy
     * @param from      the position from which the search should start (0-based). A negative value is
     *                  treated as zero. A position beyond the end of the string results in a return
     *                  value of -1 (meaning not found).
     * @return the position (0-based) of the first codepoint to match the predicate, or -1 if not found
     */

    public abstract long indexWhere(IntPredicate predicate, long from);

    /**
     * Get the first position, at or beyond <code>from</code>, where another string appears as a substring
     * of this string, comparing codepoints.
     *
     * @param other the other (sought) string
     * @param from      the position from which the search should start (0-based). A negative value is
     *                  treated as zero. A position beyond the end of the string results in a return
     *                  value of -1 (meaning not found).
     * @return the first position where the substring is found, or -1 if it is not found. Also returns
     * -1 if {@code from} is negative, or beyond the length of the string.
     */

    public long indexOf(UnicodeString other, long from) {
        if (from < 0 || from >= length()) {
            return -1;
        }
        if (other.isEmpty()) {
            return from;
        }
        int initial = other.codePointAt(0);
        long len = other.length();
        long lastPossible = length() - len;
        while (from <= lastPossible) {
            long i = indexOf(initial, from);
            if (i < 0) {
                return -1;
            }
            if (hasSubstring(other, i)) {
                return i;
            }
            from = i + 1;
        }
        return -1;
    }

    /**
     * Ask whether this string has another string as its content starting at a given offset
     *
     * @param other  the other string
     * @param offset the starting position in this string (counting in codepoints)
     * @return true if the other string appears as a substring of this string starting at the
     * given position.
     * @throws IndexOutOfBoundsException if {@code offset} is less than zero or greater than the
     * length of this string. Note that there is no exception if {@code offset + other.length()}
     * exceeds {@code this.length()} - instead this results in a return value of {@code false}.
     */

    public boolean hasSubstring(UnicodeString other, long offset) {
        if (offset < 0 || offset > length()) {
            throw new IndexOutOfBoundsException();
        }
        long len = other.length();
        if (len + offset > length()) {
            return false;
        }
        for (long k = 0; k < len; k++) {
            if (codePointAt(offset + k) != other.codePointAt(k)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get an iterator over the code points present in the string.
     *
     * @return an iterator that delivers the individual code points
     */

    public abstract IntIterator codePoints();

    /**
     * Get the code point at a given position in the string
     * @param index the given position (0-based)
     * @return the code point at the given position
     * @throws IndexOutOfBoundsException if the index is out of range
     */

    public abstract int codePointAt(long index);

    /**
     * Get a substring of this codepoint sequence, with a given start position,
     * finishing at the end of the string
     *
     * @param start the start position (0-based): that is, the position of the first
     *              code point to be included
     * @return the requested substring
     * @throws IndexOutOfBoundsException if the start position is out of range
     */

    public UnicodeString substring(long start) {
        return substring(start, length());
    }

    /**
     * Get a substring of this string, with a given start and end position
     *
     * @param start the start position (0-based): that is, the position of the first
     *              code point to be included
     * @param end   the end position (0-based): specifically, the position of the first
     *              code point not to be included
     * @return the requested substring
     * @throws IndexOutOfBoundsException if the start/end positions are out of range (the conditions
     *                                   are the same as for <code>String.substring()</code>)
     */

    public abstract UnicodeString substring(long start, long end);

    /**
     * Get a substring of this string, starting at position 0, with a given end position
     * @param end the end position (0-based): specifically, the position of the first
     *          code point not to be included
     * @return the requested substring
     * @throws IndexOutOfBoundsException if the end position is out of range
     */

    public UnicodeString prefix(long end) {
        return substring(0, end);
    }

    /**
     * Concatenate with another string, returning a new string
     * @param other the string to be appended
     * @return the result of concatenating this string followed by the other
     */

    public UnicodeString concat(UnicodeString other) {
        return ZenoString.of(this).concat(other);
    }


    protected void checkSubstringBounds(long start, long end) {
        if (start < 0) {
            throw new IndexOutOfBoundsException("UnicodeString.substring(): start (" + start + ") < 0");
        }
        if (end < start) {
            throw new IndexOutOfBoundsException("UnicodeString.substring(): end (" + end + ") < start ( + start + ");
        }
        if (end > length()) {
            throw new IndexOutOfBoundsException("UnicodeString.substring(): end (" + end + ") > length (" + length() + ")");
        }
    }

    /**
     * Diagnostic method: verify that all the characters in the string are valid XML codepoints
     * @throws IllegalStateException if the contents are invalid
     */
    public void verifyCharacters() {
        IntIterator iter = codePoints();
        int p = 0;
        while (iter.hasNext()) {
            int x = iter.next();
            if (!XMLCharacterData.isValid11(x)) {
                throw new IllegalStateException("Invalid char " + x + " in " + getClass() + " at offset " + p);
            }
            p++;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UnicodeString) {
            UnicodeString other = (UnicodeString) obj;
            IntIterator iter1 = codePoints();
            IntIterator iter2 = other.codePoints();
            while (true) {
                boolean more1 = iter1.hasNext();
                boolean more2 = iter2.hasNext();
                if (more1 && more2) {
                    int ch1 = iter1.next();
                    int ch2 = iter2.next();
                    if (ch1 != ch2) {
                        return false;
                    }
                } else {
                    return !(more1 || more2);
                }
            }
        } else {
            return false;
        }
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
        int h = 0;
        IntIterator iter = codePoints();
        while (iter.hasNext()) {
            int cp = iter.next();
            if ((cp & 0xff0000) != 0) {
                h = 31 * h + UTF16CharacterSet.highSurrogate(cp);
                h = 31 * h + UTF16CharacterSet.lowSurrogate(cp);
            } else {
                h = 31 * h + cp;
            }
        }
        return h;
    }

    /**
     * Compare this string to another using codepoint comparison
     * @param other the other string
     * @return -1 if this string comes first, 0 if they are equal, +1 if the other string comes first
     */

    @Override
    public int compareTo(UnicodeString other) {
        IntIterator iter1 = codePoints();
        IntIterator iter2 = other.codePoints();
        while (true) {
            boolean more1 = iter1.hasNext();
            boolean more2 = iter2.hasNext();
            if (more1 && more2) {
                int ch1 = iter1.next();
                int ch2 = iter2.next();
                int diff = ch1 - ch2;
                if (diff != 0) {
                    return diff;
                }
            } else if (!more1 && !more2) {
                return 0;
            } else {
                return more1 ? 1 : -1;
            }
        }
    }

    /**
     * Get the codepoints of the string as a byte array, allocating three
     * bytes per character. (Max unicode codepoint = x10FFFF)
     *
     * @return a byte array that can act as a collation key
     */

    private byte[] getCodepointCollationKey() {
        UnicodeString prep = tidy();
        int len = requireInt(prep.length());
        byte[] result = new byte[len * 3];
        IntIterator iter = prep.codePoints();
        int j=0;
        while (iter.hasNext()) {
            int c = iter.next();
            result[j++] = (byte) (c >> 16);
            result[j++] = (byte) (c >> 8);
            result[j++] = (byte) c;
        }
        return result;
    }

    /**
     * Get an atomic value that encapsulates this match key. Needed to support the collation-key() function.
     *
     * @return an atomic value that encapsulates this match key
     */
    @Override
    public AtomicValue asAtomic() {
        return new Base64BinaryValue(getCodepointCollationKey());
    }

    /**
     * Utility method for use where strings longer than 2^31 characters cannot yet be handled.
     * @param value the actual value of a character position within a string, or the length of
     *              a string
     * @return the value as an integer if it is within range
     * @throws UnsupportedOperationException if the supplied value exceeds {@link Integer#MAX_VALUE}
     */

    public static int requireInt(long value) {
        if (value > Integer.MAX_VALUE) {
            throw new UnsupportedOperationException("String offset exceeds 2^31 characters");
        }
        return (int) value;
    }

    /**
     * Utility method for use where strings longer than 2^31 characters cannot yet be handled;
     * and where negative offsets are to be treated as zero
     *
     * @param value the actual value of a character position within a string, or the length of
     *              a string
     * @return the value as an integer if it is within range
     * @throws UnsupportedOperationException if the supplied value exceeds {@link Integer#MAX_VALUE}
     */

    public static int requireNonNegativeInt(long value) {
        if (value > Integer.MAX_VALUE) {
            throw new UnsupportedOperationException("String exceeds 2^31 characters");
        }
        return (int) Math.max(value, 0);
    }

    /**
     * Copy this string, as a sequence of 8-bit characters, to a specified array
     * @param target the target array: the caller must ensure there is sufficient capacity
     * @param offset the position in the target array
     * @throws UnsupportedOperationException if this UnicodeString is capable of containing characters
     * needing more than 8 bits
     */

    void copy8bit(byte[] target, int offset) {
        throw new UnsupportedOperationException();
    }

    /**
     * Copy this string, as a sequence of 16-bit characters, to a specified array
     *
     * @param target the target array: the caller must ensure there is sufficient capacity
     * @param offset the position in the target array
     * @throws UnsupportedOperationException if this UnicodeString is capable of containing characters
     *                                       needing more than 16 bits
     */

    void copy16bit(char[] target, int offset) {
        throw new UnsupportedOperationException();
    }

    /**
     * Copy this string, as a sequence of 24-bit characters, to a specified array
     *
     * @param target the target array: the caller must ensure there is sufficient capacity
     * @param offset the position in the target array as a byte offset (that is, the character
     *               offset times 3)
     */

    void copy24bit(byte[] target, int offset) {
        throw new UnsupportedOperationException();
    }

    /**
     * Copy this string, as a sequence of 32-bit codepoints, to a specified array
     *
     * @param target the target array: the caller must ensure there is sufficient capacity
     * @param offset the position in the target array as a codepoint offset
     */

    void copy32bit(int[] target, int offset) {
        IntIterator iter = codePoints();
        while (iter.hasNext()) {
            target[offset++] = iter.next();
        }
    }


}

