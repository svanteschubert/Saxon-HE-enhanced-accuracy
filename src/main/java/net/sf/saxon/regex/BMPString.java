////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

/**
 * An implementation of UnicodeString optimized for strings that contain
 * no characters outside the BMP (i.e. no characters whose codepoints exceed 65535)
 */
public final class BMPString extends UnicodeString {

    private final CharSequence src;

    /**
     * Create a BMPString
     * @param src - encapsulated CharSequence.
     *            The client must ensure that this contains no surrogate pairs, and that
     *            it is immutable
     */
    public BMPString(CharSequence src) {
        this.src = src;
    }

    @Override
    public UnicodeString uSubstring(int beginIndex, int endIndex) {
        return new BMPString(src.subSequence(beginIndex, endIndex));
    }

    @Override
    public int uCharAt(int pos) {
        return src.charAt(pos);
    }

    @Override
    public int uIndexOf(int search, int pos) {
        if (search > 65535) {
            return -1;
        } else {
            for (int i = pos; i < src.length(); i++) {
                if (src.charAt(i) == (char) search) {
                    return i;
                }
            }
            return -1;
        }
    }

    @Override
    public int uLength() {
        return src.length();
    }

    @Override
    public boolean isEnd(int pos) {
        return pos >= src.length();
    }

    public String toString() {
        return src.toString();
    }

    /**
     * Get the underlying CharSequence
     * @return the underlying CharSequence
     */

    public CharSequence getCharSequence() {
        return src;
    }

    /**
     * Returns the length of this character sequence.  The length is the number
     * of 16-bit <code>char</code>s in the sequence.
     *
     * @return the number of <code>char</code>s in this sequence
     */
    @Override
    public int length() {
        return src.length();
    }

    /**
     * Returns the <code>char</code> value at the specified index.  An index ranges from zero
     * to <tt>length() - 1</tt>.  The first <code>char</code> value of the sequence is at
     * index zero, the next at index one, and so on, as for array
     * indexing.
     * <p>If the <code>char</code> value specified by the index is a
     * <a href="Character.html#unicode">surrogate</a>, the surrogate
     * value is returned.</p>
     *
     * @param index the index of the <code>char</code> value to be returned
     * @return the specified <code>char</code> value
     * @throws IndexOutOfBoundsException if the <tt>index</tt> argument is negative or not less than
     *                                   <tt>length()</tt>
     */
    @Override
    public char charAt(int index) {
        return src.charAt(index);
    }

    /**
     * Returns a new <code>CharSequence</code> that is a subsequence of this sequence.
     * The subsequence starts with the <code>char</code> value at the specified index and
     * ends with the <code>char</code> value at index <tt>end - 1</tt>.  The length
     * (in <code>char</code>s) of the
     * returned sequence is <tt>end - start</tt>, so if <tt>start == end</tt>
     * then an empty sequence is returned.
     *
     * @param start the start index, inclusive
     * @param end   the end index, exclusive
     * @return the specified subsequence
     * @throws IndexOutOfBoundsException if <tt>start</tt> or <tt>end</tt> are negative,
     *                                   if <tt>end</tt> is greater than <tt>length()</tt>,
     *                                   or if <tt>start</tt> is greater than <tt>end</tt>
     */
    @Override
    public CharSequence subSequence(int start, int end) {
        return src.subSequence(start, end);
    }
}
