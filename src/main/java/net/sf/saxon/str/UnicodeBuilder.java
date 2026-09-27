////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntIterator;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

/**
 * Builder class to construct a UnicodeString by appending text incrementally
 */

@CSharpInjectMembers(code={"public override System.Text.Encoding Encoding { get => System.Text.Encoding.UTF8; }"})

public final class UnicodeBuilder extends Writer implements UniStringConsumer, UnicodeWriter {

    // The data held by the UnicodeBuilder is in two parts: an archive part
    // of arbitrary length, held as a ZenoString, and an active part which
    // is typically up to 1m characters. For short strings the archive part
    // is always empty. The active part is held as an integer array, 32 bits per
    // character.

    // As characters are added to the active part, the variable "bits" is used
    // to track the widest character added so far, which is subsequently used
    // to reduce the memory requirement for storing the string.

    private int[] activePart;
    private int activePartUsed;
    private int bitMask;
    private ZenoString archive = ZenoString.EMPTY;

    private final static int MAX_ACTIVE_SIZE = 1_000_000;
    // See https://saxonica.plan.io/boards/2/topics/10002

    /**
     * Create a Unicode builder with an initial allocation of 16 codepoints
     */
    public UnicodeBuilder() {
        // See https://saxonica.plan.io/boards/2/topics/10002
        this(16);
    }

    /**
     * Create a Unicode builder with an initial space allocation
     * @param allocate the initial space allocation, in codepoints (32-bit integers) 
     */
    public UnicodeBuilder(int allocate) {  
        activePart = new int[allocate];
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
     *                  is not a surrogate. (In fact, some callers, such as the JSON parser, do
     *                  in fact append unpaired surrogates to the builder, and sort it out
     *                  later.)
     * @return this builder, with the new character added
     */

    public UnicodeBuilder append(int codePoint) {
        ensureCapacity(1);
        activePart[activePartUsed++] = codePoint;
        bitMask |= codePoint;
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
        str.copy32bit(activePart, activePartUsed);
        activePartUsed += len;

        int width = str.getWidth();
        if (width > 8) {
            if (width > 16) {
                bitMask |= 0xffffff;
            } else {
                bitMask |= 0xffff;
            }
        }
        return this;
    }

    /**
     * Get the number of codepoints currently in the builder
     * @return the size in codepoints
     */
    public long length() {
        return archive.length() + activePartUsed;
    }

    /**
     * Ask whether the content of the builder is empty
     * @return true if the size is zero
     */
    public boolean isEmpty() {
        return archive.isEmpty() && activePartUsed == 0;
    }

    /**
     * Ensure the buffer has enough capacity for a string of a given length
     *
     * @param required the number of codepoints that need to be added to the buffer
     */

    private void ensureCapacity(int required) {
        // For very long strings, archive what we've already accumulated as a ZenoString
        if (activePartUsed > MAX_ACTIVE_SIZE || required > MAX_ACTIVE_SIZE) {
            archive = archive.concat(getActivePart());
            activePartUsed = 0;
            bitMask = 0xff;
            activePart = new int[required];
        } else {
            while (activePartUsed + required > activePart.length) {
                activePart = Arrays.copyOf(activePart, activePart.length * 2);
            }
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
        if (activePartUsed == 0) {
            return EmptyUnicodeString.getInstance();
        } else if ((bitMask & 0xff0000) != 0) {
            // use 24-bit codes
            return new Twine24(activePart, activePartUsed);
        } else if ((bitMask & 0xff00) != 0) {
            // use 16-bit codes
            char[] chars = new char[activePartUsed];
            for (int i = 0; i < activePartUsed; i++) {
                chars[i] = (char) (activePart[i] & 0xffff);
            }
            return new Twine16(chars);
        } else {
            byte[] bytes = new byte[activePartUsed];
            for (int i = 0; i < activePartUsed; i++) {
                bytes[i] = (byte) (activePart[i] & 0xff);
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
        activePartUsed = 0;
        bitMask = 0;
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
        append(new Twine8(content));
    }

    /**
     * Process a single character.
     *
     * @param codepoint the Unicode character to be processed. Must not be a surrogate
     * @throws IOException if processing fails for any reason
     */
    @Override
    public void writeCodePoint(int codepoint) throws IOException {
        append(codepoint);
    }

    /**
     * Process a single ASCII character.
     *
     * @param codepoint the Unicode character to be processed. Must be in the range 0-127; this is not necessarily checked
     * @throws IOException if processing fails for any reason
     */
    @Override
    public void writeAscii(int codepoint) {
        append(codepoint);
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

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        append(new String(cbuf, off, len));
    }

    @Override
    public void flush() throws IOException {
        // no action
    }

    /**
     * Complete the writing of characters to the result. The default implementation
     * does nothing.
     */
    @Override
    public void close() { }
}

