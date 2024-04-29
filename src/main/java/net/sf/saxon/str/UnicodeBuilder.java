////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntIterator;

import java.io.IOException;
import java.util.Arrays;

/**
 * Builder class to construct a UnicodeString by appending text incrementally
 */

public final class UnicodeBuilder implements UniStringConsumer, UnicodeWriter {

    // The data held by the UnicodeBuilder is in two parts: an archive part
    // of arbitrary length, held as a ZenoString, and an active part which
    // is typically up to 65535 characters. For short strings the archive part
    // is always empty. The active part is held as an integer array, 32 bits per
    // character.

    // As characters are added to the active part, the variable "bits" is used
    // to track the widest character added so far, which is subsequently used
    // to reduce the memory requirement for storing the string.

    private int[] codepoints;
    private int used;
    private int bits;
    private ZenoString archive = ZenoString.EMPTY;

    /**
     * Create a Unicode builder with an initial allocation of 256 codepoints
     */
    public UnicodeBuilder() {
        this(256);
    }

    /**
     * Create a Unicode builder with an initial space allocation
     * @param allocate the initial space allocation, in codepoints (32-bit integers)
     */
    public UnicodeBuilder(int allocate) {
        codepoints = new int[allocate];
    }

    /**
     * Append a character, which must not be a surrogate. (Method needed for C#, because implicit
     * conversion of char to int isn't supported)
     * @param ch the character
     * @return this builder, with the new character added
     */
    public UnicodeBuilder append(char ch) {
        append((int) ch);
        return this;
    }

    /**
     * Append a single unicode character to the content
     * @param codePoint the unicode codepoint. The caller is responsible for ensuring that this
     *                  is not a surrogate
     * @return this builder, with the new character added
     */

    public UnicodeBuilder append(int codePoint) {
        ensureCapacity(1);
        codepoints[used++] = codePoint;
        bits |= codePoint;
        return this;
    }

    /**
     * Append multiple unicode characters to the content
     *
     * @param codePoints an iterator delivering the codepoints to be added.
     * @return this builder, with the new characters added
     */

    public UnicodeBuilder append(IntIterator codePoints) {
        while (codePoints.hasNext()) {
            append(codePoints.next());
        }
        return this;
    }

    /**
     * Append a Java string to the content. The caller is responsible for ensuring that this
     * consists entirely of characters in the Latin-1 character set
     *
     * @param str the string to be appended
     * @return this builder, with the new string added
     */

    public UnicodeBuilder appendLatin(String str) {
        return append(new BMPString(str));
    }

    /**
     * Append the string values of all the items in a sequence, with no separator
     * @param iter the sequence of items
     * @return this builder, with the new items added
     */

    public UnicodeBuilder appendAll(SequenceIterator iter) {
        // Note: used from bytecode
        for (Item item; (item = iter.next()) != null; ) {
            append(item.getUnicodeStringValue());
        }
        return this;
    }

    /**
     * Append a Java CharSequence to the content. This may contain arbitrary characters including
     * well formed surrogate pairs
     *
     * @param str the string to be appended
     * @return this builder, with the new string added
     */

    public UnicodeBuilder append(CharSequence str) {
        return append(StringTool.fromCharSequence(str));
    }

    /**
     * Append a UnicodeString object to the content.
     *
     * @param str the string to be appended. The length is currently restricted to 2^31.
     * @return this builder, with the new string added
     */

    public UnicodeBuilder append(UnicodeString str) {
        int len = str.length32();
        if (len == 0) {
            return this;
        }
        ensureCapacity(len);
        str.copy32bit(codepoints, used);
        used += len;

        int width = str.getWidth();
        if (width > 8) {
            if (width > 16) {
                bits |= 0xffffff;
            } else {
                bits |= 0xffff;
            }
        }
        return this;
    }

    /**
     * Get the number of codepoints currently in the builder
     * @return the size in codepoints
     */
    public long length() {
        return archive.length() + used;
    }

    /**
     * Ask whether the content of the builder is empty
     * @return true if the size is zero
     */
    public boolean isEmpty() {
        return archive.isEmpty() && used == 0;
    }

    /**
     * Ensure the buffer has enough capacity for a string of a given length
     *
     * @param required the number of codepoints that need to be added to the buffer
     */

    private void ensureCapacity(int required) {
        // For very long strings, archive what we've already accumulated as a ZenoString
        if (used > 65535) {
            archive = archive.concat(getActivePart());
            used = 0;
            bits = 0xff;
        }
        while (used + required > codepoints.length) {
            codepoints = Arrays.copyOf(codepoints, codepoints.length * 2);
        }
    }


    /**
     * Construct a UnicodeString whose value is formed from the contents of this builder
     * @return the constructed {@link UnicodeString}
     */

    public UnicodeString toUnicodeString() {
        if (archive.isEmpty()) {
            return getActivePart();
        } else {
            return archive.concat(getActivePart());
        }
    }

    /**
     * Get the contents of the active part, as a UnicodeString
     * @return a UnicodeString representing the active part of the builder's data
     */

    private UnicodeString getActivePart() {
        if ((bits & 0xff0000) != 0) {
            // use 24-bit codes
            return new Twine24(codepoints, used);
        } else if ((bits & 0xff00) != 0) {
            // use 16-bit codes
            char[] chars = new char[used];
            for (int i = 0; i < used; i++) {
                chars[i] = (char) (codepoints[i] & 0xffff);
            }
            return new Twine16(chars);
        } else {
            byte[] bytes = new byte[used];
            for (int i = 0; i < used; i++) {
                bytes[i] = (byte) (codepoints[i] & 0xff);
            }
            return new Twine8(bytes);
        }
    }

    /**
     * Construct a StringValue whose value is formed from the contents of this builder
     * @param type the required type, for example BuiltInAtomicType.STRING or
     *             BuiltInAtomicType.UNTYPED_ATOMIC. The caller warrants that the value is
     *             a valid instance of this type. No validation or whitespace normalization
     *             is carried out
     * @return the constructed StringValue
     */

    public StringValue toStringItem(AtomicType type) {
        return new StringValue(toUnicodeString(), type);
    }

    /**
     * Return a string containing the character content of this builder
     * @return the character content of this builder as a Java String
     */

    public String toString() {
        return toUnicodeString().toString();
    }


    /**
     * Reset the contents of this builder to be empty
     */

    public void clear() {
        archive = ZenoString.EMPTY;
        used = 0;
        bits = 0;
    }

    /**
     * Expand a byte array from 1-byte-per-character to 2-bytes-per-character
     * @param in the input byte array
     * @param start the start offset in bytes
     * @param used the end offset in bytes
     * @param allocate the number of code points to allow for in the output byte array
     * @return the new byte array
     */

    public static byte[] expand1to2(byte[] in, int start, int used, int allocate) {
        byte[] result = new byte[allocate*2];
        for (int i=start, j=0; i<used;) {
            result[j++] = 0;
            result[j++] = in[i++];
        }
        return result;
    }

    public static char[] expandBytesToChars(byte[] in, int start, int end) {
        char[] result = new char[end - start];
        for (int i = start, j = 0; i < end; ) {
            result[j++] = (char)in[i++];
        }
        return result;
    }


    /**
     * Expand a byte array from 1-byte-per-character to 3-bytes-per-character
     *
     * @param in the input byte array
     * @param start the start offset in bytes
     * @param used the end offset in bytes
     * @param allocate the number of code points to allow for in the output byte array
     * @return the new byte array
     */

    public static byte[] expand1to3(byte[] in, int start, int used, int allocate) {
        byte[] result = new byte[allocate*3];
        for (int i = start, j = 0; i < used; ) {
            result[j++] = 0;
            result[j++] = 0;
            result[j++] = in[i++];
        }
        return result;
    }

    /**
     * Expand a byte array from 2-bytes-per-character to 3-bytes-per-character
     *
     * @param in the input byte array
     * @param start the start offset in bytes
     * @param used the end offset in bytes
     * @param allocate the number of code points to allow for in the output byte array
     * @return the new byte array
     */

    public static byte[] expand2to3(byte[] in, int start, int used, int allocate) {
        byte[] result = new byte[allocate*3];
        for (int i = start, j = 0; i < used; ) {
            result[j++] = 0;
            result[j++] = in[i++];
            result[j++] = in[i++];
        }
        return result;
    }

    /**
     * Expand the width of the characters in a byte array
     * @param in the input byte array
     * @param start the start offset in bytes
     * @param end the end offset in bytes
     * @param oldWidth the width of the characters (number of bytes per character) in the input array
     * @param newWidth the width of the characters (number of bytes per character) in the output array. If
     *                 newWidth LE oldWidth then the input array is copied; the width is never reduced
     * @param allocate the number of code points to allow for in the output byte array; if zero (or insufficient)
     *                 the output array will have no spare space for expansion
     * @return the new byte array
     */

    public static byte[] expand(byte[] in, int start, int end, int oldWidth, int newWidth, int allocate) {
        if (allocate <= (end - start) / oldWidth) {
            allocate = (end - start) / oldWidth;
        }
        if (newWidth <= oldWidth) {
            // leave the width unchanged; we don't narrow it
            byte[] out = new byte[allocate * newWidth];
            System.arraycopy(in, start, out, 0, end*oldWidth);
            return out;
        }
        if (oldWidth == 1 && newWidth == 2) {
            return expand1to2(in, start, end, allocate);
        }
        if (oldWidth == 1 && newWidth == 3) {
            return expand1to3(in, start, end, allocate);
        }
        if (oldWidth == 2 && newWidth == 3) {
            return expand2to3(in, start, end, allocate);
        }
        throw new IllegalArgumentException();
    }

    /**
     * Process a supplied string
     *
     * @param chars the characters to be processed
     */
    @Override
    public UnicodeBuilder accept(UnicodeString chars) {
        return append(chars);
    }

    @Override
    public void write(UnicodeString chars) {
        append(chars);
    }

    /**
     * Write a supplied string known to consist entirely of ASCII characters,
     * supplied as a byte array
     *
     * @param content byte array holding ASCII characters only
     */
    @Override
    public void writeAscii(byte[] content) throws IOException {
        accept(new Twine8(content));
    }

    /**
     * Process a supplied string
     *
     * @param chars the characters to be processed
     * @throws IOException if processing fails for any reason
     */
    @Override
    public void write(String chars) throws IOException {
        append(chars);
    }

    public void trimToSize() {
        // used from bytecode
        codepoints = Arrays.copyOf(codepoints, used);
    }

    /**
     * Complete the writing of characters to the result. The default implementation
     * does nothing.
     */
    @Override
    public void close() { }
}

