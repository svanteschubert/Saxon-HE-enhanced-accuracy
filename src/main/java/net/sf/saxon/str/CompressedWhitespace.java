////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.z.IntIterator;

import java.util.Arrays;

/**
 * This class provides a compressed representation of a sequence of whitespace characters. The representation
 * is a sequence of bytes: in each byte the top two bits indicate which whitespace character is used
 * (x9, xA, xD, or x20) and the bottom six bits indicate the number of such characters. A zero byte is a filler.
 * We don't compress the sequence if it would occupy more than 8 bytes, because that's the space we've got available
 * in the TinyTree arrays.
 */

public class CompressedWhitespace extends WhitespaceString {

    private static final char[] WHITE_CHARS = {'\t', '\n', '\r', ' '};
    private static final int[] CODES =
            {-1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, -1, -1, 2, -1, -1,
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                    3};

    private final long value;

    public CompressedWhitespace(long compressedValue) {
        value = compressedValue;
    }

    public static UnicodeString compressWS(char[] in, int start, int len) {
        int runlength = 1;
        int outlength = 0;
        int end = start + len;
        for (int i = start; i < end; i++) {
            final char c = in[i];
            if (c <= 32 && CODES[c] >= 0) {
                if (i == end - 1 || c != in[i + 1] || runlength == 63) {
                    runlength = 1;
                    outlength++;
                    if (outlength > 8) {
                        return StringTool.compress(in, start, len, false);
                    }
                } else {
                    runlength++;
                }
            } else {
                return StringTool.compress(in, start, len, false);
            }
        }
        int ix = 0;
        runlength = 1;
        int[] out = new int[outlength];
        for (int i = start; i < end; i++) {
            final char c = in[i];
            if (i == end - 1 || c != in[i + 1] || runlength == 63) {
                out[ix++] = (CODES[c] << 6) | runlength;
                runlength = 1;
            } else {
                runlength++;
            }
        }
        long value = 0;
        for (int i = 0; i < outlength; i++) {
            value = (value << 8) | (long)out[i];
        }
        value = value << (8 * (8 - outlength));
        return new CompressedWhitespace(value);
    }

//    /**
//     * Make a compressed whitespace text node suitable for inserting indentation into
//     * serialized output. (When the output is UTF8, the output writer has a method for
//     * outputting repeated characters that uses {@code Arrays.fill()} to directly
//     * populate the output buffer.)
//     * @param newlines the number of newlines to include at the start (generally zero or one)
//     * @param spaces the number of spaces to include
//     * @return a {@link UnicodeString} representing the whitespace
//     */
//    public static CompressedWhitespace makeIndent(int newlines, int spaces) {
//        newlines = Math.min(newlines, 63);
//        spaces = Math.min(spaces, 63*7);
//        int outlength = 0;
//        long result = 0L;
//
//        if (newlines > 0) {
//            result = CODES[10]<<6 | newlines;
//            outlength++;
//        }
//        while (spaces > 0) {
//            int run = Math.min(spaces, 63);
//            result = (result << 8) | (long)(CODES[32] << 6) | (long)run;
//            spaces -= run;
//            outlength++;
//        }
//        result = result << (8 * (8 - outlength));
//        return new CompressedWhitespace(result);
//    }

    /**
     * Uncompress the whitespace to a (normal) UnicodeString
     * @return the uncompressed value
     */

    public UnicodeString uncompress() {
        return uncompress(value);
    }

    public static UnicodeString uncompress(long value) {
        byte[] bytes = new byte[1000];
        int offset = 0;
        for (int s = 56; s >= 0; s -= 8) {
            byte b = (byte) ((value >> s) & 0xff);
            if (b == 0) {
                break;
            }
            byte c = (byte) (WHITE_CHARS[b >> 6 & 0x3] & 0xff);
            int len = b & 0x3f;
            for (int j = 0; j < len; j++) {
                bytes[offset++] = c;
            }
        }
        return new Twine8(Arrays.copyOf(bytes, offset));
    }

    public long getCompressedValue() {
        return value;
    }

    @Override
    public long length() {
        return length(value);
    }

    @Override
    public int length32() {
        return length(value);
    }

    public static int length(long value) {
        int count = 0;
        for (int s = 56; s >= 0; s -= 8) {
            int c = (int) ((value >> s) & 0x3f);
            if (c == 0) {
                break;
            }
            count += c;
        }
        return count;
    }

    /**
     * Get the code point at a given position in the string
     *
     * @param index the given position (0-based)
     * @return the code point at the given position
     * @throws IndexOutOfBoundsException if the index is out of range
     */

    @Override
    public int codePointAt(long index) {
        int count = 0;
        for (int s = 56; s >= 0; s -= 8) {
            byte b = (byte) ((value >> s) & 0xff);
            if (b == 0) {
                break;
            }
            count += b & 0x3f;
            if (count > index) {
                return WHITE_CHARS[b >> 6 & 0x3];
            }
        }
        throw new IndexOutOfBoundsException(index + "");
    }


    @Override
    public IntIterator codePoints() {
        return uncompress().codePoints();
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    public boolean equals(/*@NotNull*/ Object obj) {
        if (obj instanceof CompressedWhitespace) {
            return value == ((CompressedWhitespace) obj).value;
        }
        return uncompress().equals(obj);
    }

    @Override
    public int hashCode() {
        // Included to prevent C# compiler warnings
        return super.hashCode();
    }

    /**
     * Write the value to a Writer
     *
     * @param writer the writer to write to
     * @throws java.io.IOException if an error occurs downstream
     */

    @Override
    public void write(/*@NotNull*/ UnicodeWriter writer) throws java.io.IOException {
        for (int s = 56; s >= 0; s -= 8) {
            final byte b = (byte) ((value >> s) & 0xff);
            if (b == 0) {
                break;
            }
            final char c = WHITE_CHARS[b >> 6 & 0x3];
            final int len = b & 0x3f;
            writer.writeRepeatedAscii((byte) c, len);
        }

    }

    /**
     * Write the value to a Writer with escaping of special characters
     *
     * @param specialChars identifies which characters are considered special
     * @param writer       the writer to write to
     * @throws java.io.IOException if an error occurs downstream
     */

    @Override
    public void writeEscape(boolean[] specialChars, UnicodeWriter writer) throws java.io.IOException {
        for (int s = 56; s >= 0; s -= 8) {
            final byte b = (byte) ((value >> s) & 0xff);
            if (b == 0) {
                break;
            }
            final char c = WHITE_CHARS[b >> 6 & 0x3];
            final int len = b & 0x3f;
            if (specialChars[c]) {
                byte[] e = null;
                if (c == '\n') {
                    e = StringConstants.ESCAPE_NL; //"&#xA;";
                } else if (c == '\r') {
                    e = StringConstants.ESCAPE_CR; //"&#xD;";
                } else if (c == '\t') {
                    e = StringConstants.ESCAPE_TAB; //"&#x9;";
                }
                assert e != null;
                for (int j = 0; j < len; j++) {
                    writer.writeAscii(e);
                }
            } else {
                writer.writeRepeatedAscii((byte)c, len);
            }
        }
    }
}

