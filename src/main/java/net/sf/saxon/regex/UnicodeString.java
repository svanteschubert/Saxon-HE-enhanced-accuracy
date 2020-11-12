////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Base64BinaryValue;

/**
 * An abstract class that efficiently handles Unicode strings including
 * non-BMP characters; it has three subclasses, respectively handling
 * strings whose maximum character code is 255, 65535, or 1114111.
 */

public abstract class UnicodeString implements CharSequence, Comparable<UnicodeString>, AtomicMatchKey {

    private int cachedHash = -1;

    /**
     * Make a UnicodeString for a given CharSequence
     *
     * @param in the input CharSequence
     * @return a UnicodeString using an appropriate implementation class
     */

    public static UnicodeString makeUnicodeString(CharSequence in) {
        if (in instanceof UnicodeString) {
            return (UnicodeString)in;
        }
        if (in.length() == 0) {
            return EmptyString.THE_INSTANCE;
        }
        int width = getMaxWidth(in);
        if (width == 1) {
            return new LatinString(in);
        } else if (width == 2) {
            return new BMPString(in);
        } else {
            return new GeneralUnicodeString(in);
        }
    }

    /**
     * Make a UnicodeString for a given array of codepoints
     *
     * @param in the input CharSequence
     * @return a UnicodeString using an appropriate implementation class
     */

    public static UnicodeString makeUnicodeString(int[] in) {
        for (int ch : in) {
            if (ch > 65535) {
                return new GeneralUnicodeString(in, 0, in.length);
            }
        }
        FastStringBuffer fsb = new FastStringBuffer(in.length);
        for (int ch : in) {
            fsb.cat((char) ch);
        }
        return new BMPString(fsb);
    }

    /**
     * Test whether a CharSequence contains Unicode codepoints outside the BMP range
     *
     * @param value the string to be tested
     * @return true if the string contains non-BMP codepoints
     */

    public static boolean containsSurrogatePairs(CharSequence value) {
        if (value instanceof BMPString || value instanceof LatinString || value instanceof EmptyString) {
            return false;
        } else if (value instanceof GeneralUnicodeString) {
            GeneralUnicodeString gus = (GeneralUnicodeString)value;
            for (int i = 0; i < gus.uLength(); i++) {
                if (gus.uCharAt(i) >= 65535) {
                    return true;
                }
            }
            return false;
        } else {
            for (int i = 0; i < value.length(); i++) {
                int c = (int) value.charAt(i);
                if (c >= 55296 && c <= 56319) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Get the maximum width of codepoints in this string, in bytes
     * @param value the string to be examined
     * @return 1, 2, or 4 depending on whether the string contains characters
     * with codepoints &gt;255 and/or &gt;65535
     */

    private static int getMaxWidth(CharSequence value) {
        if (value instanceof LatinString || value instanceof EmptyString) {
            return 1;
        }
        if (value instanceof BMPString) {
            return 2;
        }
        if (value instanceof GeneralUnicodeString) {
            return 4;
        }
        boolean nonLatin = false;
        for (int i = 0; i < value.length(); i++) {
            int c = (int) value.charAt(i);
            if (c > 255) {
                nonLatin = true;
            }
            if (c >= 55296 && c <= 56319) {
                return 4;
            }
        }
        return nonLatin ? 2 : 1;
    }

    /**
     * Get a substring of this string
     *
     * @param beginIndex the index of the first character to be included (counting
     *                   codepoints, not 16-bit characters)
     * @param endIndex   the index of the first character to be NOT included (counting
     *                   codepoints, not 16-bit characters)
     * @return a substring
     * @throws IndexOutOfBoundsException if the selection goes off the start or end of the string
     *         (this function follows the semantics of String.substring(), not the XPath semantics)
     */
    public abstract UnicodeString uSubstring(int beginIndex, int endIndex);

    /**
     * Get the first match for a given character
     *
     * @param search the character to look for
     * @param start  the first position to look
     * @return the position of the first occurrence of the sought character, or -1 if not found
     */

    public abstract int uIndexOf(int search, int start);

    /**
     * Get the character at a specified position
     *
     * @param pos the index of the required character (counting
     *            codepoints, not 16-bit characters)
     * @return a character (Unicode codepoint) at the specified position.
     */

    public abstract int uCharAt(int pos);

    /**
     * Get the length of the string, in Unicode codepoints
     *
     * @return the number of codepoints in the string
     */

    public abstract int uLength();

    /**
     * Ask whether a given position is at (or beyond) the end of the string
     *
     * @param pos the index of the required character (counting
     *            codepoints, not 16-bit characters)
     * @return <tt>true</tt> iff if the specified index is after the end of the character stream
     */

    public abstract boolean isEnd(int pos);

    /**
     * Implementations of UnicodeString can be compared with each other, but not with
     * other implementations of CharSequence
     * @return a hashCode that distinguishes this UnicodeString from others
     */

    @Override
    public int hashCode() {
        // Same result as String#hashCode() in the case where all characters are BMP chars
        if (cachedHash == -1) {
            int h = 0;
            for (int i = 0; i < uLength(); i++) {
                h = 31 * h + uCharAt(i);
            }
            cachedHash = h;
        }
        return cachedHash;
    }

    /**
     * Implementations of UnicodeString can be compared with each other, but not with
     * other implementations of CharSequence
     * @param obj the object to be compared
     * @return true if obj is a UnicodeString containing the same codepoints
     */

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UnicodeString)) {
            return false;
        }
        if (uLength() != ((UnicodeString)obj).uLength()) {
            return false;
        }
        for (int i=0; i<uLength(); i++) {
            if (uCharAt(i) != ((UnicodeString)obj).uCharAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compare two unicode strings in codepoint collating sequence
     *
     * @param other the object to be compared
     * @return less than 0, 0, or greater than 0 depending on the ordering of the two strings
     */

    @Override
    public int compareTo(UnicodeString other) {
        int alen = uLength();
        int blen = other.uLength();
        int i = 0;
        int j = 0;
        while (true) {
            if (i == alen) {
                if (j == blen) {
                    return 0;
                } else {
                    return -1;
                }
            }
            if (j == blen) {
                return +1;
            }

            int nexta = uCharAt(i++);
            int nextb = other.uCharAt(j++);
            int c = nexta - nextb;
            if (c != 0) {
                return c;
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
        int len = uLength();
        byte[] result = new byte[len * 3];
        for (int i = 0, j = 0; i < len; i++) {
            int c = uCharAt(i);
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
}
