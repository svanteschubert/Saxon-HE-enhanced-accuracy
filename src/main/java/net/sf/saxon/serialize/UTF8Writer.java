////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.str.*;
import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.z.IntIterator;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Arrays;

/**
 * Specialized buffering UTF-8 writer.
 * The main reason for custom version is to allow for efficient
 * buffer recycling; the second benefit is that encoder has less
 * overhead for short content encoding (compared to JDK default
 * codecs).
 *
 * @author Tatu Saloranta. Modified by Michael Kay to enable efficient output
 * of Unicode strings.
 */

@CSharpInjectMembers(code="public override System.Text.Encoding Encoding { get { return System.Text.Encoding.UTF8; } }")

public final class UTF8Writer extends Writer implements UnicodeWriter {

    private final static int MIN_BUF_LEN = 32;
    private final static int DEFAULT_BUF_LEN = 4096;

    final static int SURR1_FIRST = 0xD800;
    final static int SURR1_LAST = 0xDBFF;
    final static int SURR2_FIRST = 0xDC00;
    final static int SURR2_LAST = 0xDFFF;

    private OutputStream _out;

    private byte[] _outBuffer;

    final private int _outBufferLast;

    private int _outPtr;

    /**
     * When outputting chars from BMP, surrogate pairs need to be coalesced.
     * To do this, both pairs must be known first; and since it is possible
     * pairs may be split, we need temporary storage for the first half
     */
    int _surrogate = 0;

    public UTF8Writer(OutputStream out) {
        this(out, DEFAULT_BUF_LEN);
    }

    public UTF8Writer(OutputStream out, int bufferLength) {
        if (bufferLength < MIN_BUF_LEN) {
            bufferLength = MIN_BUF_LEN;
        }
        _out = new BufferedOutputStream(out);

        _outBuffer = new byte[bufferLength];
        /* Max. expansion for a single Unicode code point is 4 bytes when
         * recombining UCS-2 surrogate pairs, so:
         */
        _outBufferLast = bufferLength - 4;
        _outPtr = 0;
    }

    /*
    ////////////////////////////////////////////////////////
    // java.io.Writer implementation
    ////////////////////////////////////////////////////////
     */

    /* Due to co-variance between Appendable and
     * Writer, this would not compile with javac 1.5, in 1.4 mode
     * (source and target set to "1.4". Not a huge deal, but since
     * the base impl is just fine, no point in overriding it.
     */
    /*
    public Writer append(char c) throws IOException {
    // note: this is a JDK 1.5 method
        write(c);
        return this;
    }
    */

    @Override
    public void close() throws IOException {
        if (_out != null) {
            _flushBuffer();
            _outBuffer = null;
            _out.close();
            _out = null;

            /* If we are left with partial surrogate we have a problem, but
             * let's not let it prevent closure of the underlying stream
             */
            if (_surrogate != 0) {
                int code = _surrogate;
                // but let's clear it, to get just one problem?
                _surrogate = 0;
                throwIllegal(code);
            }
        }
    }

    @Override
    public void flush() throws IOException {
        _flushBuffer();
        _out.flush();
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        write(cbuf, 0, cbuf.length);
    }

    @Override
    public void write(char[] cbuf, int off, int len)
            throws IOException {
        assert off + len <= cbuf.length;
        if (len < 2) {
            if (len == 1) {
                write(cbuf[off]);
            }
            return;
        }

        // First: do we have a leftover surrogate to deal with?
        if (_surrogate > 0) {
            char second = cbuf[off++];
            --len;
            write(_convertSurrogate(second));
            // will have at least one more char
        }

        int outPtr = _outPtr;
        byte[] outBuf = _outBuffer;
        int outBufLast = _outBufferLast; // has 4 'spare' bytes

        // All right; can just loop it nice and easy now:
        len += off; // len will now be the end of input buffer

        while (off < len) {
            /* First, let's ensure we can output at least 4 bytes
             * (longest UTF-8 encoded codepoint):
             */
            if (outPtr >= outBufLast) {
                _out.write(outBuf, 0, outPtr);
                outPtr = 0;
            }

            int c = cbuf[off++];
            // And then see if we have an Ascii char:
            if (c < 0x80) { // If so, can do a tight inner loop:
                outBuf[outPtr++] = (byte) c;
                // Let's calc how many ascii chars we can copy at most:
                int maxInCount = (len - off);
                int maxOutCount = (outBufLast - outPtr);

                if (maxInCount > maxOutCount) {
                    maxInCount = maxOutCount;
                }
                maxInCount += off;
                boolean continueOuter = false;
                while (true) {
                    if (off >= maxInCount) { // done with max. ascii seq
                        continueOuter = true;
                        break;
                    }
                    c = cbuf[off++];
                    if (c >= 0x80) {
                        break;
                    }
                    outBuf[outPtr++] = (byte) c;
                }
                if (continueOuter) {
                    continue;
                }
            }

            // Nope, multi-byte:
            if (c < 0x800) { // 2-byte
                outBuf[outPtr++] = (byte) (0xc0 | (c >> 6));
                outBuf[outPtr++] = (byte) (0x80 | (c & 0x3f));
            } else { // 3 or 4 bytes
                // Surrogates?
                if (c < SURR1_FIRST || c > SURR2_LAST) {
                    outBuf[outPtr++] = (byte) (0xe0 | (c >> 12));
                    outBuf[outPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                    outBuf[outPtr++] = (byte) (0x80 | (c & 0x3f));
                    continue;
                }
                // Yup, a surrogate:
                if (c > SURR1_LAST) { // must be from first range
                    _outPtr = outPtr;
                    throwIllegal(c);
                }
                _surrogate = c;
                // and if so, followed by another from next range
                if (off >= len) { // unless we hit the end?
                    break;
                }
                c = _convertSurrogate(cbuf[off++]);
                if (c > 0x10FFFF) { // illegal, as per RFC 3629
                    _outPtr = outPtr;
                    throwIllegal(c);
                }
                outBuf[outPtr++] = (byte) (0xf0 | (c >> 18));
                outBuf[outPtr++] = (byte) (0x80 | ((c >> 12) & 0x3f));
                outBuf[outPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                outBuf[outPtr++] = (byte) (0x80 | (c & 0x3f));
            }
        }
        _outPtr = outPtr;
    }

    public void writeLatin1(byte[] bytes, int off, int len) throws IOException {
        assert off + len <= bytes.length;
        int outPtr = _outPtr;
        byte[] outBuf = _outBuffer;
        int outBufLast = _outBufferLast; // has 4 'spare' bytes

        // All right; can just loop it nice and easy now:
        len += off; // len will now be the end of input buffer

        while (off < len) {
            /* First, let's ensure we can output at least 4 bytes
             * (longest UTF-8 encoded codepoint):
             */
            if (outPtr >= outBufLast) {
                _out.write(outBuf, 0, outPtr);
                outPtr = 0;
            }

            int c = bytes[off++]&0xff;
            // And then see if we have an Ascii char:
            if (c < 0x80) { // If so, can do a tight inner loop:
                outBuf[outPtr++] = (byte) c;
                // Let's calc how many ascii chars we can copy at most:
                int maxInCount = (len - off);
                int maxOutCount = (outBufLast - outPtr);

                if (maxInCount > maxOutCount) {
                    maxInCount = maxOutCount;
                }
                maxInCount += off;
                boolean continueOuter = false;
                while (true) {
                    if (off >= maxInCount) { // done with max. ascii seq
                        continueOuter = true;
                        break;
                    }
                    c = bytes[off++]&0xff;
                    if (c >= 0x80) {
                        break;
                    }
                    outBuf[outPtr++] = (byte) c;
                }
                if (continueOuter) {
                    continue;
                }
            }

            outBuf[outPtr++] = (byte) (0xc0 | (c >> 6));
            outBuf[outPtr++] = (byte) (0x80 | (c & 0x3f));
        }
        _outPtr = outPtr;
    }

    /**
     * Write a sequence of ASCII characters. The caller is responsible for ensuring
     * that each byte represents a character in the range 1-127
     * @param content the content to be written
     */
    @Override
    public void writeAscii(byte[] content) throws IOException {
        writeAscii(content, 0, content.length);
    }

    /**
     * Write a sequence of ASCII characters. The caller is responsible for ensuring
     * that each byte represents a character in the range 1-127
     * @param chars the characters to be written
     * @param off the offset of the first character to be included
     * @param len the number of characters to be written
     */
    public void writeAscii(byte[] chars, int off, int len) throws IOException {

        int outPtr = _outPtr;
        byte[] outBuf = _outBuffer;
        int outBufLast = _outBufferLast; // has 4 'spare' bytes

        while (len > 0) {
            if (outPtr >= outBufLast) {
                _out.write(outBuf, 0, outPtr);
                outPtr = 0;
            }

            int available = outBufLast - outPtr;
            int count = Math.min(len, available);

            System.arraycopy(chars, off, outBuf, outPtr, count);
            outPtr += count;
            off += count;
            len -= count;
        }
        _outPtr = outPtr;
    }

    /**
     * Write an ASCII character repeatedly. Used for serializing whitespace.
     * @param ch the ASCII character to be serialized (must be less than 0x7f)
     * @param repeat the number of occurrences to output
     * @throws IOException if it fails
     */
    public void writeRepeatedAscii(byte ch, int repeat) throws IOException {

        int outPtr = _outPtr;
        byte[] outBuf = _outBuffer;
        int outBufLast = _outBufferLast; // has 4 'spare' bytes

        while (repeat > 0) {
            if (outPtr >= outBufLast) {
                _out.write(outBuf, 0, outPtr);
                outPtr = 0;
            }

            int available = outBufLast - outPtr;
            int count = Math.min(repeat, available);

            Arrays.fill(outBuf, outPtr, outPtr+count, ch);
            outPtr += count;
            repeat -= count;
        }
        _outPtr = outPtr;
    }

    /**
     * Process a single character. Default implementation wraps the codepoint
     * into a single-character {@link UnicodeString}
     *
     * @param codepoint the character to be processed. Must not be a surrogate
     * @throws IOException if processing fails for any reason
     */
    @Override
    public void writeCodePoint(int codepoint) throws IOException {
        // The implementation of write(int) in this class appears to handle astral characters, although
        // the interface definition for Java.io.Writer suggests otherwise
        write(codepoint);
    }

    /**
     * Write a single char.
     * <p>Note (MHK) Although the Writer interface says that the top half of the int is ignored, this
     * implementation appears to accept a Unicode codepoint which is output as a 4-byte UTF-8 sequence.</p>
     * @param c the char to be written
     * @throws IOException If an I/O error occurs
     */
    @Override
    public void write(int c)
            throws IOException {
        // First; do we have a left over surrogate?
        if (_surrogate > 0) {
            c = _convertSurrogate(c);
            // If not, do we start with a surrogate?
        } else if (c >= SURR1_FIRST && c <= SURR2_LAST) {
            // Illegal to get second part without first:
            if (c > SURR1_LAST) {
                throwIllegal(c);
            }
            // First part just needs to be held for now
            _surrogate = c;
            return;
        }

        if (_outPtr >= _outBufferLast) { // let's require enough room, first
            _flushBuffer();
        }

        if (c < 0x80) { // ascii
            _outBuffer[_outPtr++] = (byte) c;
        } else {
            int ptr = _outPtr;
            if (c < 0x800) { // 2-byte
                _outBuffer[ptr++] = (byte) (0xc0 | (c >> 6));
                _outBuffer[ptr++] = (byte) (0x80 | (c & 0x3f));
            } else if (c <= 0xFFFF) { // 3 bytes
                _outBuffer[ptr++] = (byte) (0xe0 | (c >> 12));
                _outBuffer[ptr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                _outBuffer[ptr++] = (byte) (0x80 | (c & 0x3f));
            } else { // 4 bytes
                if (c > 0x10FFFF) { // illegal, as per RFC 3629
                    throwIllegal(c);
                }
                _outBuffer[ptr++] = (byte) (0xf0 | (c >> 18));
                _outBuffer[ptr++] = (byte) (0x80 | ((c >> 12) & 0x3f));
                _outBuffer[ptr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                _outBuffer[ptr++] = (byte) (0x80 | (c & 0x3f));
            }
            _outPtr = ptr;
        }
    }

    /**
     * Process a supplied string
     *
     * @param chars the characters to be processed
     * @throws IOException if processing fails for any reason
     */
    @Override
    public void write(UnicodeString chars) throws IOException {
        if (chars instanceof StringView || chars instanceof BMPString) {
            write(chars.toString());
        } else if (chars instanceof UnicodeChar) {
            writeCodePoint(((UnicodeChar) chars).getCodepoint());
        } else if (chars instanceof ZenoString) {
            ((ZenoString)chars).writeSegments(this);
        } else if (chars.getWidth() <= 8) {
            writeWidth8OrLower(chars);

        } else if (chars.getWidth() == 16) {
            if (chars instanceof Twine16) {
                write(((Twine16)chars).getCharArray());
            } else if (chars instanceof Slice16) {
                Slice16 s16 = (Slice16) chars;
                write(s16.getCharArray(), s16.getStart(), s16.getEnd() - s16.getStart());
            }
        } else {
            IntIterator iter = chars.codePoints();
            while (iter.hasNext()) {
                write(iter.next());
            }
        }
    }

    private void writeWidth8OrLower(UnicodeString chars) throws IOException
    {
        if (chars instanceof Twine8) {
            int width = chars.getWidth();
            if (width == 7) {
                writeAscii(((Twine8) chars).getByteArray(), 0, chars.length32());
            } else if (width == 8) {
                writeLatin1(((Twine8) chars).getByteArray(), 0, chars.length32());
            }
        } else if (chars instanceof Slice8) {
            Slice8 s8 = (Slice8) chars;
            int width = chars.getWidth();
            if (width == 7) {
                writeAscii(s8.getByteArray(), s8.getStart(), s8.getEnd() - s8.getStart());
            } else if (width == 8) {
                writeLatin1(s8.getByteArray(), s8.getStart(), s8.getEnd() - s8.getStart());
            }
        } else if (chars instanceof WhitespaceString) {
            ((WhitespaceString)chars).write(this);
        } else {
            // probably doesn't happen
            IntIterator iter = chars.codePoints();
            while (iter.hasNext()) {
                write(iter.next());
            }
        }
    }


    @Override
    public void write(String str)
            throws IOException {
        write(str, 0, str.length());
    }

    @Override
    public void write(String str, int off, int len)
            throws IOException {
        if (len < 2) {
            if (len == 1) {
                write(str.charAt(off));
            }
            return;
        }

        // First: do we have a leftover surrogate to deal with?
        if (_surrogate > 0) {
            char second = str.charAt(off++);
            --len;
            write(_convertSurrogate(second));
            // will have at least one more char (case of 1 char was checked earlier on)
        }

        int outPtr = _outPtr;
        byte[] outBuf = _outBuffer;
        int outBufLast = _outBufferLast; // has 4 'spare' bytes

        // All right; can just loop it nice and easy now:
        len += off; // len will now be the end of input buffer

        while (off < len) {
            // First, let's ensure we can output at least 4 bytes
            // (longest UTF-8 encoded codepoint):
            if (outPtr >= outBufLast) {
                _out.write(outBuf, 0, outPtr);
                outPtr = 0;
            }

            int c = str.charAt(off++);
            // And then see if we have an Ascii char:
            if (c < 0x80)
            { // If so, can do a tight inner loop:
                outBuf[outPtr++] = (byte) c;
                // Let's calc how many ascii chars we can copy at most:
                int maxInCount = (len - off);
                int maxOutCount = (outBufLast - outPtr);

                if (maxInCount > maxOutCount) {
                    maxInCount = maxOutCount;
                }
                maxInCount += off;
                boolean continueOuter = false;

                while (true)
                {
                    if (off >= maxInCount)
                    { // done with max. ascii seq
                        continueOuter = true;
                        break;
                    }
                    c = str.charAt(off++);
                    if (c >= 0x80)
                    {
                        break;
                    }
                    outBuf[outPtr++] = (byte) c;
                }
                if (continueOuter) {
                    continue;
                }
            }

            int[] result = writeMultiByte(c, outBuf, outPtr, str, off, len);

            if (result == null)
            {
                break;
            }
            else
            {
                outPtr = result[0];
                off = result[1];
            }
        }
        _outPtr = outPtr;
    }

    private int[] writeMultiByte(int c, byte[] outBuf, int outPtr, String str, int off, int len) throws IOException
    {
        // Nope, multi-byte:
        if (c < 0x800)
        { // 2-byte
            outBuf[outPtr++] = (byte) (0xc0 | (c >> 6));
            outBuf[outPtr++] = (byte) (0x80 | (c & 0x3f));
        }
        else
        { // 3 or 4 bytes
            // Surrogates?
            if (c < SURR1_FIRST || c > SURR2_LAST)
            {
                outBuf[outPtr++] = (byte) (0xe0 | (c >> 12));
                outBuf[outPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                outBuf[outPtr++] = (byte) (0x80 | (c & 0x3f));
                return new int[]{outPtr, off};
            }
            // Yup, a surrogate:
            if (c > SURR1_LAST)
            { // must be from first range
                _outPtr = outPtr;
                throwIllegal(c);
            }

            _surrogate = c;

            // and if so, followed by another from next range
            if (off >= len)
            { // unless we hit the end?
                return null;
            }

            c = _convertSurrogate(str.charAt(off++));

            if (c > 0x10FFFF)
            { // illegal, as per RFC 3629
                _outPtr = outPtr;
                throwIllegal(c);
            }

            outBuf[outPtr++] = (byte) (0xf0 | (c >> 18));
            outBuf[outPtr++] = (byte) (0x80 | ((c >> 12) & 0x3f));
            outBuf[outPtr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
            outBuf[outPtr++] = (byte) (0x80 | (c & 0x3f));
        }

        return new int[]{outPtr, off};
    }

    /*
    ////////////////////////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////////////////////////
     */

    private void _flushBuffer() throws IOException {
        if (_outPtr > 0 && _outBuffer != null) {
            _out.write(_outBuffer, 0, _outPtr);
            _outPtr = 0;
        }
    }

    /**
     * Method called to calculate UTF codepoint, from a surrogate pair.
     */
    private int _convertSurrogate(int secondPart)
            throws IOException {
        int firstPart = _surrogate;
        _surrogate = 0;

        // Ok, then, is the second part valid?
        if (secondPart < SURR2_FIRST || secondPart > SURR2_LAST) {
            throw new IOException("Broken surrogate pair: first char 0x" + Integer.toHexString(firstPart) + ", second 0x" + Integer.toHexString(secondPart) + "; illegal combination");
        }
        return 0x10000 + ((firstPart - SURR1_FIRST) << 10) + (secondPart - SURR2_FIRST);
    }

    private void throwIllegal(int code)
            throws IOException {
        if (code > 0x10FFFF) { // over max?
            throw new IOException("Illegal character point (0x" + Integer.toHexString(code) + ") to output; max is 0x10FFFF as per RFC 3629");
        }
        if (code >= SURR1_FIRST) {
            if (code <= SURR1_LAST) { // Unmatched first part (closing without second part?)
                throw new IOException("Unmatched first part of surrogate pair (0x" + Integer.toHexString(code) + ")");
            }
            throw new IOException("Unmatched second part of surrogate pair (0x" + Integer.toHexString(code) + ")");
        }

        // should we ever get this?
        throw new IOException("Illegal character point (0x" + Integer.toHexString(code) + ") to output");
    }
}

