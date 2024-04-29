////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.serialize.UTF8Writer;
import net.sf.saxon.z.ConcatenatingIntIterator;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntRepeatIterator;

import java.util.Arrays;

/**
 * This class provides a compressed representation of a string used to represent indentation: specifically,
 * an integer number of newlines followed by an integer number of spaces.  It's a little cheaper than
 * the {@link CompressedWhitespace} class, and is specifically used for constructing indentation and passing
 * it to a {@link UTF8Writer}, which recognizes it specially. Unlike {@link CompressedWhitespace}, it can't
 * handle arbitrary whitespace strings, only those consisting of newlines followed by spaces.
 */

public class IndentWhitespace extends WhitespaceString {

    private final int newlines;
    private final int spaces;

    private IndentWhitespace(int newlines, int spaces) {
        this.newlines = newlines;
        this.spaces = spaces;
    }

    /**
     * Create an IndentWhitespace object
     * @param newlines the number of newlines at the start
     * @param spaces the number of spaces following the newlines
     * @return the constructed (or potentially cached) IndentWhitespace object
     */

    public static IndentWhitespace of(int newlines, int spaces) {
        // Uses a factory method to permit pooling, not currently implemented
        return new IndentWhitespace(newlines, spaces);
    }

    /**
     * Uncompress the whitespace to a (normal) UnicodeString
     * @return the uncompressed value
     */

    @Override
    public UnicodeString uncompress() {
        byte[] bytes = new byte[newlines + spaces];
        Arrays.fill(bytes, 0, newlines, (byte)0x0a);
        Arrays.fill(bytes, newlines, newlines + spaces, (byte)0x20);
        return new Twine8(bytes);
    }

    @Override
    public long length() {
        return newlines + spaces;
    }

    @Override
    public int length32() {
        return newlines + spaces;
    }

    /**
     * Get the number of newlines at the start
     * @return the number of newline characters
     */

    public int getNewlines() {
        return newlines;
    }

    /**
     * Get the number of spaces following the newlines
     * @return the number of space characters
     */

    public int getSpaces() {
        return spaces;
    }

    /**
     * Returns the codepoint value at the specified index.  An index ranges from zero
     * to <code>length() - 1</code>.  The first codepoint value of the sequence is at
     * index zero, the next at index one, and so on, as for array
     * indexing.
     *
     * @param index the index of the codepoint value to be returned
     * @return the specified codepoint value
     * @throws IndexOutOfBoundsException if the <code>index</code> argument is negative or not less than
     *                                   <code>length()</code>
     */
    @Override
    public int codePointAt(long index) {
        if (index >= 0 && index < newlines) {
            return 0x0A;
        } else if (index <= newlines + spaces) {
            return 0x20;
        } else {
            throw new IndexOutOfBoundsException();
        }
    }


    @Override
    public IntIterator codePoints() {
        return new ConcatenatingIntIterator(
                new IntRepeatIterator(10, newlines),
                () -> new IntRepeatIterator(32, spaces));
    }

    /**
     * Returns a string representation of the object.
     */
    public String toString() {
        char[] chars = new char[newlines + spaces];
        Arrays.fill(chars, 0, newlines, '\n');
        Arrays.fill(chars, newlines, newlines + spaces, ' ');
        return new String(chars);
    }

    /**
     * Write the value to a UnicodeWriter
     *
     * @param writer the writer to write to
     * @throws java.io.IOException if an error occurs downstream
     */

    @Override
    public void write(/*@NotNull*/ UnicodeWriter writer) throws java.io.IOException {
        if (newlines > 0) {
            writer.writeRepeatedAscii((byte) 0x0A, newlines);
        }
        if (spaces > 0) {
            writer.writeRepeatedAscii((byte) 0x20, spaces);
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
        if (specialChars[0x0A]) {
            for (int i=0; i<newlines; i++) {
                writer.writeAscii(StringConstants.ESCAPE_NL);
            }
        } else {
            writer.writeRepeatedAscii((byte) 0x0A, newlines);
        }
        writer.writeRepeatedAscii((byte) 0x20, spaces);
    }
}

