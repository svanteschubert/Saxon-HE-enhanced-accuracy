////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.serialize.UTF8Writer;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.z.IntIterator;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.function.IntPredicate;

/**
 * A Unicode string consisting entirely of 8-bit characters, implemented as a range
 * of an underlying byte array
 */

public class Slice8 extends UnicodeString {

    private final byte[] bytes;
    private final int start;
    private final int end;
    private int cachedHash;

    /**
     * Create a slice of an underlying byte array
     * @param bytes the byte array, containing Unicode codepoints in the range 0-255
     * @param start the offset of the first character within the byte array
     * @param end the offset of the first excluded byte, so the length of the string
     *            is {@code end-start}
     */

    public Slice8(byte[] bytes, int start, int end) {
        this.bytes = bytes;
        this.start = start;
        this.end = end;
    }

    @Override
    public long length() {
        return end - start;
    }

    @Override
    public int getWidth() {
        return 8;
    }

    public byte[] getByteArray() {
        return bytes;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    @Override
    public long indexOf(int codePoint, long from) {
        if (codePoint > 255) {
            return -1;
        }
        byte b = (byte) (codePoint & 0xff);
        for (int i = start + requireNonNegativeInt(from); i < end; i++) {
            if (bytes[i] == b) {
                return i - start;
            }
        }
        return -1;
    }

    @Override
    public int codePointAt(long index) {
        int index32 = requireInt(index);
        if (index32 < 0 || index32 >= length32()) {
            throw new IndexOutOfBoundsException();
        }
        return (bytes[start + index32]) & 0xff;
    }

    @Override
    public UnicodeString substring(long start, long end) {
        checkSubstringBounds(start, end);
        if (end == start) {
            return EmptyUnicodeString.getInstance();
        } else {
            return new Slice8(bytes, requireInt(start) + this.start, requireInt(end) + this.start);
        }
    }

    private void write(Writer writer, long start, long len) throws IOException {
        if (writer instanceof UTF8Writer) {
            ((UTF8Writer) writer).writeLatin1(bytes, this.start + requireInt(start), requireInt(len));
        } else {
            writer.write(substring(requireInt(start), requireInt(start + len)).toString());
        }
    }

    /**
     * Get the position of the first occurrence of the specified codepoint,
     * starting the search at a given position in the string
     *
     * @param predicate condition that the codepoint must satisfy
     * @param from      the position from which the search should start (0-based)
     * @return the position (0-based) of the first codepoint to match the predicate, or -1 if not found
     */
    @Override
    public long indexWhere(IntPredicate predicate, long from) {
        for (int i = requireNonNegativeInt(from) + start; i < end; i++) {
            if (predicate.test(bytes[i] & 0xff)) {
                return i - start;
            }
        }
        return -1;
    }

    @Override
    public IntIterator codePoints() {
        return new IntIterator() {
            int i = start;

            @Override
            public boolean hasNext() {
                return i < end;
            }

            @Override
            public int next() {
                return bytes[i++] & 0xff;
            }
        };
    }


    void copy8bit(byte[] target, int offset) {
        System.arraycopy(bytes, start, target, offset, end - start);
    }

    void copy16bit(char[] target, int offset) {
        for (int i = start, j = offset; i < end; ) {
            target[j++] = (char) (bytes[i++] & 0xff);
        }
    }

    void copy24bit(byte[] target, int offset) {
        for (int i = start, j = offset; i < end; ) {
            target[j++] = (byte) 0;
            target[j++] = (byte) 0;
            target[j++] = bytes[i++];
        }
    }

    @Override
    void copy32bit(int[] target, int offset) {
        for (int i = start, j = offset; i < end; ) {
            target[j++] = bytes[i++] & 0xff;
        }
    }

    /**
     * Compute a hashCode. All implementations of {@code UnicodeString} use compatible hash codes and the
     * hashing algorithm is therefore identical to that for {@code java.lang.String}. This means
     * that for strings containing Astral characters, the hash code needs to be computed by decomposing
     * an Astral character into a surrogate pair.
     *
     * @return the hash code
     */

    public int hashCode() {
        if (cachedHash != 0) {
            return cachedHash;
        }
        int h = 0;
        for (int i = start; i < end; i++) {
            byte b = bytes[i];
            h = 31 * h + (b & 0xff);
        }
        return cachedHash = h;
    }

    /**
     * Concatenate another string
     *
     * @param other the string to be appended to this one
     * @return the result of the concatenation (neither input string is altered)
     */
//    @Override
//    public UnicodeString concat(UnicodeString other) {
//        if (other.getWidth() <= 8) {
//            return ZenoString.concatSegments(this, other);
//        } else {
//            return super.concat(other);
//        }
//    }

    /**
     * Display as a string.
     */

    /*@NotNull*/
    @CSharpReplaceBody(code="return Saxon.Impl.Helpers.StringUtils.LATIN_1.GetString(bytes, start, end - start);")
    public String toString() {
        return new String(bytes, start, end - start, StandardCharsets.ISO_8859_1);
    }

}

