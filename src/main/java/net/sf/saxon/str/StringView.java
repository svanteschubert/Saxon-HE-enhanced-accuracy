////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.z.IntIterator;

import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * An implementation of the UnicodeString interface that wraps an ordinary Java string.
 */

public class StringView extends UnicodeString {

    private final String baseString;
    private UnicodeString baseUnicodeString; // created on demand

    private StringView(String baseString) {
        Objects.requireNonNull(baseString);
        this.baseString = baseString;
    }

    public static UnicodeString of(String base) {
        return new StringView(base);
    }

    public static UnicodeString wrap(String base) {
        return new StringView(base).tidy();
    }

    public static UnicodeString tidy(String base) {
        return new StringView(base).tidy();
    }

    @Override
    public synchronized UnicodeString tidy() {
        if (baseUnicodeString != null) {
            return baseUnicodeString;
        } else {
            return baseUnicodeString = StringTool.fromCharSequence(baseString);
        }
    }


    @Override
    public boolean isEmpty() {
        return baseString.isEmpty();
    }

    @Override
    public long estimatedLength() {
        return baseUnicodeString != null ? baseUnicodeString.length() : baseString.length();
    }

    /**
     * Get the length of the string
     *
     * @return the number of code points in the string
     */
    @Override
    public long length() {
        return tidy().length();
    }

    /**
     * Get the number of bits needed to hold all the characters in this string
     *
     * @return 7 for ascii characters, 8 for latin-1, 16 for BMP, 24 for general Unicode.
     */
    @Override
    public int getWidth() {
        return tidy().getWidth();
    }

    /**
     * Get the position of the first occurrence of the specified codepoint,
     * starting the search at a given position in the string
     *
     * @param codePoint the sought codepoint
     * @param from      the position from which the search should start (0-based). There
     *                  is no restriction on the value. Negative values are treated as zero;
     *                  values greater than or equal to length() return -1.
     * @return the position (zero-based) of the first occurrence found, or -1 if not found
     */
    @Override
    public long indexOf(int codePoint, long from) {
        return tidy().indexOf(codePoint, from);
    }

    @Override
    public long indexWhere(IntPredicate predicate, long from) {
        return tidy().indexWhere(predicate, from);
    }

    /**
     * Get the code point at a given position in the string
     *
     * @param index the given position (0-based)
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    @Override
    public int codePointAt(long index) {
        return tidy().codePointAt(index);
    }

    /**
     * Get a substring of this codepoint sequence, with a given start and end position
     *
     * @param start the start position (0-based): that is, the position of the first
     *              code point to be included
     * @param end   the end position (0-based): specifically, the position of the first
     *              code point not to be included
     * @throws UnsupportedOperationException if the {@code UnicodeString} has not been prepared
     *                                       for codePoint access
     */
    @Override
    public UnicodeString substring(long start, long end) {
        return tidy().substring(start, end);
    }


    @Override
    public IntIterator codePoints() {
        return baseUnicodeString != null ? baseUnicodeString.codePoints() : StringTool.codePoints(baseString);
    }

    @Override
    public UnicodeString concat(UnicodeString other) {
        if (other instanceof StringView) {
            StringView sv = new StringView(baseString + ((StringView)other).baseString);
            UnicodeString us1 = baseUnicodeString;
            UnicodeString us2 = ((StringView) other).baseUnicodeString;
            if (us1 != null && us2 != null) {
                baseUnicodeString = us1.concat(us2);
            }
            return sv;
        } else {
            UnicodeBuilder ub = new UnicodeBuilder();
            return ub.accept(this).accept(other).toUnicodeString();
        }
    }

    @Override
    public int compareTo(UnicodeString other) {
        if (baseUnicodeString != null && other instanceof StringView && ((StringView)other).baseUnicodeString != null) {
            return baseString.compareTo(((StringView)other).baseString);
        } else {
            return super.compareTo(other);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof StringView) {
            return this.baseString.equals(((StringView) obj).baseString);
        } else if (obj instanceof BMPString) {
            return this.baseString.equals(obj.toString());
        } else {
            return super.equals(obj);
        }
    }

    @Override
    @CSharpReplaceBody(code="return (baseUnicodeString != null) ? baseUnicodeString.GetHashCode() : base.GetHashCode();")
    public int hashCode() {
        return baseString.hashCode();
    }

    @Override
    public UnicodeString economize() {
        return tidy();
    }

    @Override
    public String toString() {
        return baseString;
    }

    @Override
    void copy8bit(byte[] target, int offset) {
        tidy().copy8bit(target, offset);
    }

    @Override
    void copy16bit(char[] target, int offset) {
        tidy().copy16bit(target, offset);
    }

    @Override
    void copy24bit(byte[] target, int offset) {
        tidy().copy24bit(target, offset);
    }
}

