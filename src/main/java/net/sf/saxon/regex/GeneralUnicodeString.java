////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.value.*;

/**
 * A Unicode string which, in general, may contain non-BMP characters (that is, codepoints
 * outside the range 0-65535)
 */

public final class GeneralUnicodeString extends UnicodeString {

    private int[] chars;
    private int start;
    private int end;
    private CharSequence charSequence;

    public GeneralUnicodeString(CharSequence in) {
        chars = net.sf.saxon.value.StringValue.expand(in);
        start = 0;
        end = chars.length;
        charSequence = in;
    }

    GeneralUnicodeString(int[] chars, int start, int end) {
        this.chars = chars;
        this.start = start;
        this.end = end;
    }

    @Override
    public UnicodeString uSubstring(int beginIndex, int endIndex) {
        if (endIndex > chars.length) {
            throw new IndexOutOfBoundsException("endIndex=" + endIndex
                    + "; sequence size=" + chars.length);
        }
        if (beginIndex < 0 || beginIndex > endIndex) {
            throw new IndexOutOfBoundsException("beginIndex=" + beginIndex
                    + "; endIndex=" + endIndex);
        }
        return new GeneralUnicodeString(chars, start + beginIndex, start + endIndex);
    }

    @Override
    public int uCharAt(int pos) {
        return chars[start + pos];
    }

    @Override
    public int uIndexOf(int search, int pos) {
        for (int i = pos; i < uLength(); i++) {
            if (chars[start + i] == search) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int uLength() {
        return end - start;
    }

    @Override
    public boolean isEnd(int pos) {
        return pos >= (end - start);
    }

    public String toString() {
        obtainCharSequence();
        charSequence = charSequence.toString();
        return (String)charSequence;
    }

    /**
     * Get a CharSequence representing this string. This is a memo function; the result is saved for
     * use if needed again. The CharSequence returned is one that has efficient support for operations
     * such as charAt(p) where non-BMP characters are represented as surrogate pairs.
     * @return a CharSequence representing the same string, with efficient positional access to
     * UTF16 codepoints.
     */

    private CharSequence obtainCharSequence() {
        if (charSequence == null) {
            int[] c = chars;
            if (start != 0) {
                c = new int[end - start];
                System.arraycopy(chars, start, c, 0, end - start);
            }
            charSequence = StringValue.contract(c, end - start);
        }
        return charSequence;
    }

    /**
     * Returns the length of this character sequence.  The length is the number
     * of 16-bit <code>char</code>s in the sequence.
     *
     * @return the number of <code>char</code>s in this sequence
     */
    @Override
    public int length() {
        return obtainCharSequence().length();
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
        return obtainCharSequence().charAt(index);
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
        return obtainCharSequence().subSequence(start, end);
    }
}
