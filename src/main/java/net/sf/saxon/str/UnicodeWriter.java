////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.str;

import java.io.IOException;
import java.util.Arrays;

/**
 * Interface that accepts strings in the form of {@link UnicodeString} objects,
 * which are written to some destination. It also accepts ordinary {@link java.lang.String}
 * objects.
 */

public interface UnicodeWriter {

    /**
     * Process a supplied string
     * @param chars the characters to be processed
     * @throws IOException if processing fails for any reason
     */

    void write(UnicodeString chars) throws IOException;

    /**
     * Write a supplied string known to consist entirely of ASCII characters,
     * supplied as a byte array. (The significance of this is that, for a variety
     * of encodings including UTF-8 and Latin-1, the bytes can be written to the
     * output stream "as is", because many encodings have ASCII as a subset.)
     * @param content byte array holding ASCII characters only (the effect if
     *                the high-order bit is set is undefined)
     * @throws IOException if processing fails for any reason
     */

    void writeAscii(byte[] content) throws IOException;

    /**
     * Process a single character. The default implementation wraps the codepoint
     * into a single-character {@link UnicodeString}
     *
     * @param codepoint the character to be processed. Must not be a surrogate
     * @throws IOException if processing fails for any reason
     */

    default void writeCodePoint(int codepoint) throws IOException {
        write(new UnicodeChar(codepoint));
    }

    /**
     * Process a single character repeatedly. The default implementation fills a byte
     * array and calls {@link #writeAscii(byte[])}.
     *
     * @param asciiChar the character to be processed.
     * @param count the number of times the character is to be output
     * @throws IOException if processing fails for any reason
     */

    default void writeRepeatedAscii(byte asciiChar, int count) throws IOException {
        assert asciiChar >= 0;
        byte[] array = new byte[count];
        Arrays.fill(array, asciiChar);
        writeAscii(array);
    }

    /**
     * Process a supplied string
     *
     * @param chars the characters to be processed
     * @throws IOException if processing fails for any reason
     */

    void write(String chars) throws IOException;


    /**
     * Complete the writing of characters to the result. The default implementation
     * does nothing.
     * @throws IOException if processing fails for any reason
     */

    default void close() throws IOException {}

    /**
     * Flush the contents of any buffers. The default implementation
     * does nothing.
     *
     * @throws IOException if processing fails for any reason
     */

    default void flush() throws IOException {
    }


}

