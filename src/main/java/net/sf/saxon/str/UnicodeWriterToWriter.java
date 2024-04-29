////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.str;

import net.sf.saxon.serialize.charcode.UTF16CharacterSet;

import java.io.IOException;
import java.io.Writer;

/**
 * Implementation of {@link UnicodeWriter} that converts Unicode strings to ordinary
 * Java strings and sends them to a supplied Writer
 */

public class UnicodeWriterToWriter implements UnicodeWriter {

    private final Writer writer;

    public UnicodeWriterToWriter(Writer writer) {
        this.writer = writer;
    }

    /**
     * Process a supplied string
     * @param chars the characters to be processed
     * @throws IOException if processing fails for any reason
     */

    public void write(UnicodeString chars) throws IOException {
        writer.write(chars.toString());
    }

    /**
     * Process a single character. Default implementation wraps the codepoint
     * into a single-character {@link UnicodeString}
     *
     * @param codepoint the character to be processed. Must not be a surrogate
     * @throws IOException if processing fails for any reason
     */

    public void writeCodePoint(int codepoint) throws IOException {
        if (codepoint > 65535) {
            writer.write(UTF16CharacterSet.highSurrogate(codepoint));
            writer.write(UTF16CharacterSet.lowSurrogate(codepoint));
        } else {
            writer.write((char)codepoint);
        }
    }

    /**
     * Process a supplied string
     *
     * @param chars the characters to be processed
     * @throws IOException if processing fails for any reason
     */

    @Override
    public void write(String chars) throws IOException {
        writer.write(chars);
    }

    /**
     * Write a supplied string known to consist entirely of ASCII characters,
     * supplied as a byte array
     *
     * @param content byte array holding ASCII characters only
     */
    @Override
    public void writeAscii(byte[] content) throws IOException {
        char[] chars = new char[content.length];
        StringTool.copy8to16(content, 0, chars, 0, content.length);
        writer.write(chars);
    }

    /**
     * Complete the writing of characters to the result.
     * @throws IOException if processing fails for any reason
     */

    @Override
    public void close() throws IOException {
        writer.close();
    }

    /**
     * Flush the contents of any buffers.
     *
     * @throws IOException if processing fails for any reason
     */
    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}

