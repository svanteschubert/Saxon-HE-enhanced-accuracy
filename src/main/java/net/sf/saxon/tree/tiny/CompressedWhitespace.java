////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.tree.util.FastStringBuffer;

import java.io.Writer;

/**
 * This class provides a compressed representation of a sequence of whitespace characters. The representation
 * is a sequence of bytes: in each byte the top two bits indicate which whitespace character is used
 * (x9, xA, xD, or x20) and the bottom six bits indicate the number of such characters. A zero byte is a filler.
 * We don't compress the sequence if it would occupy more than 8 bytes, because that's the space we've got available
 * in the TinyTree arrays.
 */

public class CompressedWhitespace implements CharSequence {

    /*@NotNull*/ private static char[] WHITE_CHARS = {0x09, 0x0A, 0x0D, 0x20};
    /*@NotNull*/ private static int[] CODES =
            {-1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, -1, -1, 2, -1, -1,
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                    3};

    private long value;

    public CompressedWhitespace(long compressedValue) {
        value = compressedValue;
    }

    /**
     * Attempt to compress a CharSequence
     *
     * @param in the CharSequence to be compressed
     * @return the compressed sequence if it can be compressed; or the original CharSequence otherwise
     */

    /*@NotNull*/
    public static CharSequence compress(/*@NotNull*/ CharSequence in) {
        final int inlen = in.length();
        if (inlen == 0) {
            return in;
        }
        int runlength = 1;
        int outlength = 0;
        for (int i = 0; i < inlen; i++) {
            final char c = in.charAt(i);
            if (c <= 32 && CODES[c] >= 0) {
                if (i == inlen - 1 || c != in.charAt(i + 1) || runlength == 63) {
                    runlength = 1;
                    outlength++;
                    if (outlength > 8) {
                        return in;
                    }
                } else {
                    runlength++;
                }
            } else {
                return in;
            }
        }
        int ix = 0;
        runlength = 1;
        int[] out = new int[outlength];
        for (int i = 0; i < inlen; i++) {
            final char c = in.charAt(i);
            if (i == inlen - 1 || c != in.charAt(i + 1) || runlength == 63) {
                out[ix++] = (CODES[c] << 6) | runlength;
                runlength = 1;
            } else {
                runlength++;
            }
        }
        long value = 0;
        for (int i = 0; i < outlength; i++) {
            value = (value << 8) | out[i];
        }
        value <<= 8 * (8 - outlength);
        return new CompressedWhitespace(value);
    }

    /**
     * Uncompress the whitespace to a FastStringBuffer
     *
     * @param buffer the buffer to which the whitespace is to be appended. The parameter may be
     *               null, in which case a new buffer is created.
     * @return the FastStringBuffer to which the whitespace has been appended. If a buffer was
     *         supplied in the argument, this will be the same buffer.
     */

    public FastStringBuffer uncompress(/*@Nullable*/ FastStringBuffer buffer) {
        if (buffer == null) {
            buffer = new FastStringBuffer(length());
        }
        uncompress(value, buffer);
        return buffer;
    }

    public static void uncompress(long value, /*@NotNull*/ FastStringBuffer buffer) {
        for (int s = 56; s >= 0; s -= 8) {
            byte b = (byte) ((value >>> s) & 0xff);
            if (b == 0) {
                break;
            }
            char c = WHITE_CHARS[b >>> 6 & 0x3];
            int len = b & 0x3f;
            buffer.ensureCapacity(len);
            for (int j = 0; j < len; j++) {
                buffer.cat(c);
            }
        }
    }

    public long getCompressedValue() {
        return value;
    }

    @Override
    public int length() {
        int count = 0;
        long val = value;
        for (int s = 56; s >= 0; s -= 8) {
            int c = (int) ((val >>> s) & 0x3f);
            if (c == 0) {
                break;
            }
            count += c;
        }
        return count;
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
        int count = 0;
        final long val = value;
        for (int s = 56; s >= 0; s -= 8) {
            byte b = (byte) ((val >>> s) & 0xff);
            if (b == 0) {
                break;
            }
            count += b & 0x3f;
            if (count > index) {
                return WHITE_CHARS[b >>> 6 & 0x3];
            }
        }
        throw new IndexOutOfBoundsException(index + "");
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
        return uncompress(null).subSequence(start, end);
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    public boolean equals(/*@NotNull*/ Object obj) {
        if (obj instanceof CompressedWhitespace) {
            return value == ((CompressedWhitespace) obj).value;
        }
        return uncompress(null).equals(obj);
    }

    /**
     * Returns a hash code value for the object.
     */
    public int hashCode() {
        return uncompress(null).hashCode();
    }

    /**
     * Returns a string representation of the object.
     */
    public String toString() {
        return uncompress(null).toString();
    }

    /**
     * Write the value to a Writer
     *
     * @param writer the writer to write to
     * @throws java.io.IOException if an error occurs downstream
     */

    public void write(/*@NotNull*/ Writer writer) throws java.io.IOException {
        final long val = value;
        for (int s = 56; s >= 0; s -= 8) {
            final byte b = (byte) ((val >>> s) & 0xff);
            if (b == 0) {
                break;
            }
            final char c = WHITE_CHARS[b >>> 6 & 0x3];
            final int len = b & 0x3f;
            for (int j = 0; j < len; j++) {
                writer.write(c);
            }
        }
    }

    /**
     * Write the value to a Writer with escaping of special characters
     *
     * @param specialChars identifies which characters are considered special
     * @param writer       the writer to write to
     * @throws java.io.IOException if an error occurs downstream
     */

    public void writeEscape(boolean[] specialChars, Writer writer) throws java.io.IOException {
        final long val = value;
        for (int s = 56; s >= 0; s -= 8) {
            final byte b = (byte) ((val >>> s) & 0xff);
            if (b == 0) {
                break;
            }
            final char c = WHITE_CHARS[b >>> 6 & 0x3];
            final int len = b & 0x3f;
            if (specialChars[c]) {
                String e = "";
                if (c == '\n') {
                    e = "&#xA;";
                } else if (c == '\r') {
                    e = "&#xD;";
                } else if (c == '\t') {
                    e = "&#x9;";
                }
                for (int j = 0; j < len; j++) {
                    writer.write(e);
                }
            } else {
                for (int j = 0; j < len; j++) {
                    writer.write(c);
                }
            }
        }
    }
}

