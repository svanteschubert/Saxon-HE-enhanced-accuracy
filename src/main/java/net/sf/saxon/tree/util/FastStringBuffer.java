////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import net.sf.saxon.regex.BMPString;
import net.sf.saxon.regex.GeneralUnicodeString;
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.tree.tiny.AppendableCharSequence;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.tiny.CompressedWhitespace;

import java.io.Writer;
import java.util.Arrays;

/**
 * A simple implementation of a class similar to StringBuffer. Unlike
 * StringBuffer it is not synchronized. It also offers the capability
 * to remove unused space. (This class could possibly be replaced by
 * StringBuilder in JDK 1.5, but using our own class gives more control.)
 */

public final class FastStringBuffer implements AppendableCharSequence, CharSequenceConsumer {

    public static final int C16 = 16;
    public static final int C64 = 64;
    public static final int C256 = 256;
    public static final int C1024 = 1024;

    private char[] array;
    private int used = 0;

    /**
     * Create a FastStringBuffer with a given initial capacity
     *
     * @param initialSize the initial capacity
     */

    public FastStringBuffer(int initialSize) {
        //System.err.println("FSB " + initialSize);
        array = new char[initialSize];
    }

    /**
     * Create a FastStringBuffer with initial content
     *
     * @param cs the initial content. The buffer is created with just enough capacity for
     *           this content (it will be expanded if more content is added later).
     */

    public FastStringBuffer(CharSequence cs) {
        array = new char[cs.length()];
        this.cat(cs);
    }

    /**
     * Append the contents of a String to the buffer
     *
     * @param s the String to be appended
     * @return the FastStringBuffer with the new contents
     */

    public FastStringBuffer cat(String s) {
        int len = s.length();
        ensureCapacity(len);
        s.getChars(0, len, array, used);
        used += len;
        return this;
    }

    /**
     * Append the contents of a String to the buffer
     *
     * @param s the String to be appended
     */

    public void append(String s) {
        cat(s);
    }

    /**
     * Append the contents of a CharSlice to the buffer
     *
     * @param s the String to be appended
     */

    public void append(CharSlice s) {
        int len = s.length();
        ensureCapacity(len);
        s.copyTo(array, used);
        used += len;
    }

    /**
     * Append the contents of a FastStringBuffer to the buffer
     *
     * @param s the FastStringBuffer to be appended
     */

    public void append(FastStringBuffer s) {
        int len = s.length();
        ensureCapacity(len);
        s.getChars(0, len, array, used);
        used += len;
    }

    /**
     * Append the contents of a StringBuffer to the buffer
     *
     * @param s the StringBuffer to be appended
     */

    public void append(StringBuffer s) {
        int len = s.length();
        ensureCapacity(len);
        s.getChars(0, len, array, used);
        used += len;
    }

    /**
     * Append the contents of a general CharSequence to the buffer
     *
     * @param s the CharSequence to be appended
     * @return the result of the concatenation
     */

    @Override
    public FastStringBuffer cat(CharSequence s) {
        // Although we provide variants of this method for different subtypes, Java decides which to use based
        // on the static type of the operand. We want to use the right method based on the dynamic type, to avoid
        // creating objects and copying strings unnecessarily. So we do a dynamic dispatch.
        final int len = s.length();
        ensureCapacity(len);
        if (s instanceof CharSlice) {
            ((CharSlice) s).copyTo(array, used);
        } else if (s instanceof String) {
            ((String) s).getChars(0, len, array, used);
        } else if (s instanceof FastStringBuffer) {
            ((FastStringBuffer) s).getChars(0, len, array, used);
        } else if (s instanceof CompressedWhitespace) {
            ((CompressedWhitespace) s).uncompress(this);
            return this;
        } else if (s instanceof BMPString) {
            this.cat(((BMPString) s).getCharSequence());
            return this;
        } else if (s instanceof GeneralUnicodeString) {
            for (int i = 0; i < ((GeneralUnicodeString)s).uLength(); i++) {
                appendWideChar(((GeneralUnicodeString)s).uCharAt(i));
            }
            return this;
        } else {
            s.toString().getChars(0, len, array, used);
        }
        used += len;
        return this;
    }

    /**
     * Append the contents of a general CharSequence to the buffer
     *
     * @param s the CharSequence to be appended
     */

    public void append(CharSequence s) {
        cat(s);
    }


    /**
     * Append the contents of a character array to the buffer
     *
     * @param srcArray the array whose contents are to be added
     * @param start    the offset of the first character in the array to be copied
     * @param length   the number of characters to be copied
     */

    public void append(char[] srcArray, int start, int length) {
        ensureCapacity(length);
        System.arraycopy(srcArray, start, array, used, length);
        used += length;
    }

    /**
     * Append the entire contents of a character array to the buffer
     *
     * @param srcArray the array whose contents are to be added
     */

    public void append(char[] srcArray) {
        final int length = srcArray.length;
        ensureCapacity(length);
        System.arraycopy(srcArray, 0, array, used, length);
        used += length;
    }

    /**
     * Append a character to the buffer
     *
     * @param ch the character to be added
     * @return this FastStringBuffer
     */

    @Override
    public FastStringBuffer cat(char ch) {
        ensureCapacity(1);
        array[used++] = ch;
        return this;
    }

    /**
     * Append a wide character to the buffer (as a surrogate pair if necessary)
     *
     * @param ch the character, as a 32-bit Unicode codepoint
     */

    public void appendWideChar(int ch) {
        if (ch > 0xffff) {
            cat(UTF16CharacterSet.highSurrogate(ch));
            cat(UTF16CharacterSet.lowSurrogate(ch));
        } else {
            cat((char) ch);
        }
    }

    /**
     * Append a UnicodeString to the buffer (using surrogate pairs if necessary)
     *
     * @param str the UnicodeString
     */

    public void append(UnicodeString str) {
        if (str instanceof BMPString) {
            this.cat(((BMPString) str).getCharSequence());
        } else {
            for (int i = 0; i < str.uLength(); i++) {
                appendWideChar(str.uCharAt(i));
            }
        }
    }

    /**
     * Prepend a wide character to the buffer (as a surrogate pair if necessary)
     *
     * @param ch the character, as a 32-bit Unicode codepoint
     */

    public void prependWideChar(int ch) {
        if (ch > 0xffff) {
            prepend(UTF16CharacterSet.lowSurrogate(ch));
            prepend(UTF16CharacterSet.highSurrogate(ch));
        } else {
            prepend((char) ch);
        }
    }

    /**
     * Returns the length of this character sequence.  The length is the number
     * of 16-bit <code>char</code>s in the sequence.
     *
     * @return the number of <code>char</code>s in this sequence
     */
    @Override
    public int length() {
        return used;
    }

    /**
     * Ask whether the string buffer is empty
     * @return true if the buffer is empty (length()==0)
     */

    public boolean isEmpty() {
        return used==0;
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
        if (index >= used) {
            throw new IndexOutOfBoundsException("" + index);
        }
        return array[index];
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
        return new CharSlice(array, start, end - start);
    }

    /**
     * Copies characters from this FastStringBuffer into the destination character
     * array.
     * <p>The first character to be copied is at index <code>srcBegin</code>;
     * the last character to be copied is at index <code>srcEnd-1</code>
     * (thus the total number of characters to be copied is
     * <code>srcEnd-srcBegin</code>). The characters are copied into the
     * subarray of <code>dst</code> starting at index <code>dstBegin</code>
     * and ending at index:</p>
     * <blockquote><pre>
     *     dstbegin + (srcEnd-srcBegin) - 1
     * </pre></blockquote>
     *
     * @param srcBegin index of the first character in the string
     *                 to copy.
     * @param srcEnd   index after the last character in the string
     *                 to copy.
     * @param dst      the destination array.
     * @param dstBegin the start offset in the destination array.
     * @throws IndexOutOfBoundsException If any of the following
     *                                   is true:
     *                                   <ul><li><code>srcBegin</code> is negative.
     *                                   <li><code>srcBegin</code> is greater than <code>srcEnd</code>
     *                                   <li><code>srcEnd</code> is greater than the length of this
     *                                   string
     *                                   <li><code>dstBegin</code> is negative
     *                                   <li><code>dstBegin+(srcEnd-srcBegin)</code> is larger than
     *                                   <code>dst.length</code></ul>
     */
    public void getChars(int srcBegin, int srcEnd, char dst[], int dstBegin) {
        if (srcBegin < 0) {
            throw new StringIndexOutOfBoundsException(srcBegin);
        }
        if (srcEnd > used) {
            throw new StringIndexOutOfBoundsException(srcEnd);
        }
        if (srcBegin > srcEnd) {
            throw new StringIndexOutOfBoundsException(srcEnd - srcBegin);
        }
        System.arraycopy(array, srcBegin, dst, dstBegin, srcEnd - srcBegin);
    }

    /**
     * Get the index of the first character equal to a given value
     *
     * @param ch the character to search for
     * @return the position of the first occurrence, or -1 if not found
     */

    public int indexOf(char ch) {
        for (int i = 0; i < used; i++) {
            if (array[i] == ch) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Convert contents of the FastStringBuffer to a string
     */

    public String toString() {
        condense();    // has side-effects which is nasty on the debugger!
        return new String(array, 0, used);
    }

    /**
     * Compare equality
     */

    public boolean equals(Object other) {
        return other instanceof CharSequence && toString().equals(other.toString());
    }

    /**
     * Generate a hash code
     */

    public int hashCode() {
        // Same algorithm as String#hashCode(), but not cached
        int h = 0;
        for (int i = 0; i < used; i++) {
            h = 31 * h + array[i];
        }
        return h;
    }


    /**
     * Get a char[] array containing the characters. The caller should not modify the
     * array.
     *
     * @return a char[] array containing the characters.
     */

    public char[] toCharArray() {
        if (used == array.length) {
            return array;
        } else {
            char[] chars = new char[used];
            System.arraycopy(array, 0, chars, 0, used);
            return chars;
        }
    }

    /**
     * Set the character at a particular offset
     *
     * @param index the index of the character to be set
     * @param ch    the new character to overwrite the existing character at that location
     * @throws IndexOutOfBoundsException if int&lt;0 or int&gt;=length()
     */

    public void setCharAt(int index, char ch) {
        if (index < 0 || index > used) {
            throw new IndexOutOfBoundsException("" + index);
        }
        array[index] = ch;
    }

    /**
     * Insert a character at a particular offset
     *
     * @param index the index of the character to be set
     * @param ch    the new character to insert at that location
     * @throws IndexOutOfBoundsException if int&lt;0 or int&gt;=length()
     */

    public void insert(int index, char ch) {
        if (index < 0 || index > used) {
            throw new IndexOutOfBoundsException("" + index);
        }
        ensureCapacity(1);
        System.arraycopy(array, index, array, index + 1, used - index);
        used++;
        array[index] = ch;
    }

    /**
     * Insert wide character at a particular offset
     *
     * @param index the index of the character to be set
     * @param ch    the character, as a 32-bit Unicode codepoint
     * @throws IndexOutOfBoundsException if int&lt;0 or int&gt;=length()
     */

    public void insertWideChar(int index, int ch) {
        if (index < 0 || index > used) {
            throw new IndexOutOfBoundsException("" + index);
        }
        if (ch > 0xffff) {
            ensureCapacity(2);
            System.arraycopy(array, index, array, index + 2, used - index);
            used += 2;
            array[index] = UTF16CharacterSet.highSurrogate(ch);
            array[index + 1] = UTF16CharacterSet.lowSurrogate(ch);
        } else {
            ensureCapacity(1);
            System.arraycopy(array, index, array, index + 1, used - index);
            used++;
            array[index] = (char) ch;
        }
    }


    /**
     * Remove a character at a particular offset
     *
     * @param index the index of the character to be set
     * @throws IndexOutOfBoundsException if int&lt;0 or int&gt;=length()
     */

    public void removeCharAt(int index) {
        if (index < 0 || index > used) {
            throw new IndexOutOfBoundsException("" + index);
        }
        used--;
        System.arraycopy(array, index + 1, array, index, used - index);
    }

    /**
     * Insert a given character at the start of the buffer
     *
     * @param ch the character to insert
     */

    public void prepend(char ch) {
        char[] a2 = new char[array.length + 1];
        System.arraycopy(array, 0, a2, 1, used);
        a2[0] = ch;
        used += 1;
        array = a2;
    }

    /**
     * Insert a given string at the start of the buffer
     *
     * @param str the string to insert
     */

    public void prepend(CharSequence str) {
        int len = str.length();
        char[] a2 = new char[array.length + len];
        System.arraycopy(array, 0, a2, len, used);
        for (int i = 0; i < len; i++) {
            a2[i] = str.charAt(i);
        }
        used += len;
        array = a2;
    }

    /**
     * Insert a given character N times at the start of the buffer
     *
     * @param ch     the character to insert
     * @param repeat the number of occurrences required. Supplying 0 or a negative number is OK,
     *               and is treated as a no-op.
     */

    public void prependRepeated(char ch, int repeat) {
        if (repeat > 0) {
            char[] a2 = new char[array.length + repeat];
            System.arraycopy(array, 0, a2, repeat, used);
            Arrays.fill(a2, 0, repeat, ch);
            used += repeat;
            array = a2;
        }
    }

    /**
     * Set the length. If this exceeds the current length, this method is a no-op.
     * If this is less than the current length, characters beyond the specified point
     * are deleted.
     *
     * @param length the new length
     */

    @Override
    public void setLength(int length) {
        if (length < 0 || length > used) {
            return;
        }
        used = length;
    }

    /**
     * Expand the character array if necessary to ensure capacity for appended data
     *
     * @param extra the amount of additional capacity needed, in characters
     */

    public void ensureCapacity(int extra) {
        if (used + extra > array.length) {
            int newlen = array.length * 2;
            if (newlen < used + extra) {
                newlen = used + extra * 2;
            }
            array = Arrays.copyOf(array, newlen);
        }
    }

    /**
     * Remove surplus space from the array. This doesn't reduce the array to the minimum
     * possible size; it only reclaims space if it seems worth doing. Specifically, it
     * contracts the array if the amount of wasted space is more than 256 characters, or
     * more than half the allocated size and more than 20 chars.
     *
     * @return the buffer after removing unused space
     */

    public FastStringBuffer condense() {
        if (array.length - used > 256 || (array.length > used * 2 && array.length - used > 20)) {
            array = Arrays.copyOf(array, used);
        }
        return this;
    }

    /**
     * Write the value to a writer
     *
     * @param writer the writer to which the content is to be written
     */

    public void write(Writer writer) throws java.io.IOException {
        writer.write(array, 0, used);
    }

    /**
     * Diagnostic print of the contents of a CharSequence. Ordinary printable ASCII characters
     * are displayed as themselves; anything else is displayed as a \\uNNNN escape sequence
     *
     * @param in the CharSequence whose contents are to be displayed.
     * @return the diagnostic output
     */

    public static String diagnosticPrint(/*@NotNull*/ CharSequence in) {
        FastStringBuffer buff = new FastStringBuffer(in.length() * 2);
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c > 32 && c < 127) {
                buff.cat(c);
            } else {
                buff.append("\\u");
                for (int d = 12; d >= 0; d -= 4) {
                    buff.cat("0123456789ABCDEF".charAt((c >> d) & 0xf));
                }
            }
        }
        return buff.toString();
    }

}

