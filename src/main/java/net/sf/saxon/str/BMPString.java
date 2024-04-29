////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.Configuration;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.z.IntIterator;

import java.util.function.IntPredicate;

/**
 * An implementation of {@code UnicodeString} that wraps a Java string which is known to contain
 * no surrogates. That is, all the characters in the string are in the Basic Multilingual
 * Plane (their codepoints are in the range 0-65535 and not in the surrogate range).
 */

public class BMPString extends UnicodeString {

    private final String baseString;

    /**
     * Protected constructor
     * @param baseString the string to be wrapped: the caller is responsible for ensuring this
     *             contains no surrogates
     */

    protected BMPString(String baseString) {
        this.baseString = baseString;
    }

    /**
     * Wrap a String, which must contain no surrogates
     * @param base the string. The caller warrants that this string contains no surrogates;
     *             this condition is checked only if Java assertions are enabled.
     * @return the wrapped string.
     */

    public static UnicodeString of(String base) {
        if (Configuration.isAssertionsEnabled()) {
            for (int i=0; i<base.length(); i++) {
                assert !UTF16CharacterSet.isSurrogate(base.charAt(i));
            }
        }
        return new BMPString(base);
    }


    @Override
    public long length() {
        return baseString.length();
    }

    @Override
    public boolean isEmpty() {
        return baseString.isEmpty();
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public IntIterator codePoints() {
        return StringTool.codePoints(baseString);
    }

    @Override
    public long indexOf(int codePoint) {
        if (codePoint > 65535) {
            return -1;
        }
        return baseString.indexOf((char)codePoint);
    }

    @Override
    public long indexOf(int codePoint, long from) {
        int from32 = requireInt(from);
        if (codePoint > 65535) {
            return -1;
        }
        return baseString.indexOf((char)codePoint, from32);
    }

    /**
     * Get the position of the first occurrence of the specified codepoint,
     * starting the search at a given position in the string
     *
     * @param predicate condition that the codepoint must satisfy
     * @param from      the position from which the search should start (0-based). A negative value is
     *                  treated as zero. A position beyond the end of the string results in a return
     *                  value of -1 (meaning not found).
     * @return the position (0-based) of the first codepoint to match the predicate, or -1 if not found
     */
    @Override
    public long indexWhere(IntPredicate predicate, long from) {
        for (int i = requireNonNegativeInt(from); i < baseString.length(); i++) {
            if (predicate.test(baseString.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int codePointAt(long index) {
        return baseString.charAt(requireInt(index));
    }

    @Override
    public UnicodeString substring(long start, long end) {
        int start32 = requireInt(start);
        int end32 = requireInt(end);
        return new BMPString(baseString.substring(start32, end32));
    }

    @Override
    public UnicodeString concat(UnicodeString other) {
        if (other instanceof BMPString) {
            return new BMPString(baseString + ((BMPString)other).baseString);
        } else {
            UnicodeBuilder ub = new UnicodeBuilder();
            return ub.accept(this).accept(other).toUnicodeString();
        }
    }

    @Override
    public int compareTo(UnicodeString other) {
        if (other instanceof BMPString) {
            return baseString.compareTo(((BMPString)other).baseString);
        } else {
            return super.compareTo(other);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BMPString) {
            return this.baseString.equals(((BMPString)obj).baseString);
        } else {
            return super.equals(obj);
        }
    }

    // In SaxonJ, the hashCode of a UnicodeString is the same as the hashCode
    // of the corresponding String. This is not the case in SaxonCS. In SaxonCS,
    // we compute the hashCode by inheriting the hashCode() method from the superclass:
    // but we do this explicitly, to avoid compiler warnings that arise when there's
    // an Equals() method but no GetHashCode() method
    @Override
    @CSharpReplaceBody(code="return base.GetHashCode();")
    public int hashCode() {
        return baseString.hashCode();
    }

    @Override
    public String toString() {
        return baseString;
    }

    @Override
    void copy16bit(char[] target, int offset) {
        baseString.getChars(0, baseString.length(), target, offset);
    }

    @Override
    void copy24bit(byte[] target, int offset) {
        char[] chars = baseString.toCharArray();
        for (int i = 0, j = offset; i < chars.length; ) {
            char c = chars[i++];
            target[j++] = 0;
            target[j++] = (byte) (c >> 8);
            target[j++] = (byte) (c & 0xff);
        }
    }

    @Override
    void copy32bit(int[] target, int offset) {
        for (int i = 0, j = offset; i < baseString.length(); ) {
            target[j++] = baseString.charAt(i++);
        }
    }
}

