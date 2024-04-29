////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.str;

import net.sf.saxon.z.IntIterator;

/**
 * Interface that accepts a a sequence of Unicode codepoints. These may be supplied in various ways,
 * which are expected to be equivalent: an array holding one, two or three bytes per Unicode character,
 * or an iterator over the integer codepoints.
 **/

public interface TwineConsumer {

    /**
     * Append an array (or part of an array) of 8-bit characters to the content
     * of the LargeTextBuffer
     *
     * @param bytes the array of characters to be added
     * @param start the start offset of the first character to be copied
     * @param len   the number of characters (codepoints, one byte per character) to be copied
     */

    void append8(byte[] bytes, int start, int len);

    /**
     * Append an array (or part of an array) of 16-bit characters to the content
     * of the LargeTextBuffer
     *
     * @param chars the array of characters to be added. This must not contain any surrogates
     * @param start the start offset of the first character to be copied
     * @param len   the number of characters (codepoints, two bytes per character) to be copied
     */

    void append16(char[] chars, int start, int len);

    /**
     * Append an array (or part of an array) of 24-bit characters to the content
     * of the LargeTextBuffer
     *
     * @param bytes the array holding the characters to be added, three bytes per character
     * @param start the start offset of the first character to be copied (codepoint offset, not byte offset)
     * @param len   the number of characters (codepoints, three bytes per character) to be copied
     */

    void append24(byte[] bytes, int start, int len);

    /**
     * Append a sequence of codepoints supplied as an iterator
     * @param codePoints iterator over the codepoints to be delivered
     */

    void append(IntIterator codePoints);
}

