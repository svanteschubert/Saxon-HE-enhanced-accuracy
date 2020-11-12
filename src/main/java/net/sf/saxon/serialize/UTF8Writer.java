////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * Specialized buffering UTF-8 writer.
 * The main reason for custom version is to allow for efficient
 * buffer recycling; the second benefit is that encoder has less
 * overhead for short content encoding (compared to JDK default
 * codecs).
 *
 * @author Tatu Saloranta
 */
public final class UTF8Writer
        extends Writer {
    private final static int MIN_BUF_LEN = 32;
    private final static int DEFAULT_BUF_LEN = 4000;

    final static int SURR1_FIRST = 0xD800;
    final static int SURR1_LAST = 0xDBFF;
    final static int SURR2_FIRST = 0xDC00;
    final static int SURR2_LAST = 0xDFFF;

    /*@Nullable*/ protected OutputStream _out;

    protected byte[] _outBuffer;

    final protected int _outBufferLast;

    protected int _outPtr;

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
        _out = out;
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

        output_loop:
        for (; off < len; ) {
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
                ascii_loop:
                while (true) {
                    if (off >= maxInCount) { // done with max. ascii seq
                        continue output_loop;
                    }
                    c = cbuf[off++];
                    if (c >= 0x80) {
                        break ascii_loop;
                    }
                    outBuf[outPtr++] = (byte) c;
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

        output_loop:
        for (; off < len; ) {
            // First, let's ensure we can output at least 4 bytes
            // (longest UTF-8 encoded codepoint):
            if (outPtr >= outBufLast) {
                _out.write(outBuf, 0, outPtr);
                outPtr = 0;
            }

            int c = str.charAt(off++);
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
                ascii_loop:
                while (true) {
                    if (off >= maxInCount) { // done with max. ascii seq
                        continue output_loop;
                    }
                    c = str.charAt(off++);
                    if (c >= 0x80) {
                        break ascii_loop;
                    }
                    outBuf[outPtr++] = (byte) c;
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
                c = _convertSurrogate(str.charAt(off++));
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
    private final int _convertSurrogate(int secondPart)
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

