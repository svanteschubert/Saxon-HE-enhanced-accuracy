////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.tree.util.FastStringBuffer;

import java.util.Arrays;


/**
 * This is an implementation of the CharSequence interface: it implements
 * a CharSequence as a list of arrays of characters (the individual arrays are known
 * as segments). The segments have a fixed size of 65536 characters.
 * <p>This is more efficient than a buffer backed by a contiguous array of characters
 * in cases where the size is likely to grow very large, and where substring operations
 * are rare. As used within the TinyTree, extraction of the string value of a node
 * requires character copying only in the case where the value crosses segment
 * boundaries.</p>
 */

public final class LargeStringBuffer implements AppendableCharSequence {

    private final static int BITS = 16;
    private final static int SEGLEN = 1 << BITS;
    private final static int MASK = SEGLEN - 1;

    // Variant of LargeStringBuffer using fixed-length segments

    private char[][] data;
    private int length;         // total length of the CharSequence
    private int segmentsUsed;

    /**
     * Create an empty LargeStringBuffer with default space allocation
     */

    public LargeStringBuffer() {
        data = new char[1][];
        segmentsUsed = 0;
        length = 0;
    }

    /**
     * Expand the data structure. Note this only involves expanding the "index" (the list of
     * segments), it does not cause any character data to be copied.
     *
     * @param seg the new segment to be added.
     */

    private void addSegment(char[] seg) {
        int segs = data.length;
        if (segmentsUsed + 1 > segs) {
            if (segmentsUsed == 32768) {
                throw new IllegalStateException("Source document too large: more than 1G characters in text nodes");
            }
            data = Arrays.copyOf(data, segs * 2);
        }
        data[segmentsUsed++] = seg;
    }

    /**
     * Append a CharSequence to this LargeStringBuffer
     *
     * @param s the data to be appended
     */

    @Override
    public LargeStringBuffer cat(CharSequence s) {
        // Although we provide variants of this method for different subtypes, Java decides which to use based
        // on the static type of the operand. We want to use the right method based on the dynamic type, to avoid
        // creating objects and copying strings unnecessarily. So we do a dynamic dispatch. (This is only necessary
        // of course because the CharSequence class offers no getChars() method).

        if (s instanceof CompressedWhitespace) {
            FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);
            ((CompressedWhitespace)s).uncompress(fsb);
            return cat(fsb);
        }

        final int len = s.length();
        char[] firstSeg;
        int firstSegOffset = length & MASK;
        if (firstSegOffset == 0) {
            firstSeg = new char[SEGLEN];
            addSegment(firstSeg);
        } else {
            firstSeg = data[length>>BITS];
        }
        int firstSegLen;
        int fullSegments;
        int lastSegLen;
        if (len <= SEGLEN - firstSegOffset) {
            // all fits in the current segment
            firstSegLen = len;
            fullSegments = 0;
            lastSegLen = 0;
        } else {
            firstSegLen = SEGLEN - firstSegOffset;
            fullSegments = (len - firstSegLen) >> BITS;
            lastSegLen = (len - firstSegLen) & MASK;
        }

        if (s instanceof CharSlice) {
            ((CharSlice)s).getChars(0, firstSegLen, firstSeg, firstSegOffset);
            int start = firstSegLen;
            for (int i=0; i<fullSegments; i++) {
                char[] seg = new char[SEGLEN];
                addSegment(seg);
                ((CharSlice)s).getChars(start, start+SEGLEN, seg, 0);
                start += SEGLEN;
            }
            if (lastSegLen > 0) {
                char[] seg = new char[SEGLEN];
                addSegment(seg);
                ((CharSlice)s).getChars(start, len, seg, 0);
            }
            length += len;

        } else if (s instanceof FastStringBuffer) {
            ((FastStringBuffer)s).getChars(0, firstSegLen, firstSeg, firstSegOffset);
            int start = firstSegLen;
            for (int i=0; i<fullSegments; i++) {
                char[] seg = new char[SEGLEN];
                addSegment(seg);
                ((FastStringBuffer)s).getChars(start, start+SEGLEN, seg, 0);
                start += SEGLEN;
            }
            if (lastSegLen > 0) {
                char[] seg = new char[SEGLEN];
                addSegment(seg);
                ((FastStringBuffer)s).getChars(start, len, seg, 0);
            }
            length += len;

        } else {
            if (!(s instanceof String)) {
                s = s.toString();
            }

            ((String)s).getChars(0, firstSegLen, firstSeg, firstSegOffset);
            int start = firstSegLen;
            for (int i=0; i<fullSegments; i++) {
                char[] seg = new char[SEGLEN];
                addSegment(seg);
                ((String)s).getChars(start, start+SEGLEN, seg, 0);
                start += SEGLEN;
            }
            if (lastSegLen > 0) {
                char[] seg = new char[SEGLEN];
                addSegment(seg);
                ((String)s).getChars(start, len, seg, 0);
            }
            length += len;

        }
        return this;
    }

    @Override
    public LargeStringBuffer cat(char c) {
        return cat("" + c);
    }

    /**
     * Returns the length of this character sequence.  The length is the number
     * of 16-bit UTF-16 characters in the sequence.
     *
     * @return the number of characters in this sequence
     */
    @Override
    public int length() {
        return length;
    }

    /**
     * Set the length. If this exceeds the current length, this method is a no-op.
     * If this is less than the current length, characters beyond the specified point
     * are deleted.
     *
     * @param length the new length
     */

    @Override
    public void setLength(int length) {
        if (length < this.length) {
            int usedInLastSegment = length & MASK;
            this.length = length;
            this.segmentsUsed = length / SEGLEN + (usedInLastSegment == 0 ? 0 : 1);
        }
    }

    /**
     * Returns the character at the specified index.  An index ranges from zero
     * to <tt>length() - 1</tt>.  The first character of the sequence is at
     * index zero, the next at index one, and so on, as for array
     * indexing.
     *
     * @param index the index of the character to be returned
     * @return the specified character
     * @throws IndexOutOfBoundsException if the <tt>index</tt> argument is negative or not less than
     *                                   <tt>length()</tt>
     */
    @Override
    public char charAt(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException(index + "");
        }
        return data[index >> BITS][index & MASK];
    }

    /**
     * Returns a new character sequence that is a subsequence of this sequence.
     * The subsequence starts with the character at the specified index and
     * ends with the character at index <tt>end - 1</tt>.  The length of the
     * returned sequence is <tt>end - start</tt>, so if <tt>start == end</tt>
     * then an empty sequence is returned.
     *
     * @param start the start index, inclusive
     * @param end   the end index, exclusive
     * @return the specified subsequence
     * @throws IndexOutOfBoundsException if <tt>start</tt> or <tt>end</tt> are negative,
     *                                   if <tt>end</tt> is greater than <tt>length()</tt>,
     *                                   or if <tt>start</tt> is greater than <tt>end</tt>
     */
    /*@NotNull*/
    @Override
    public CharSequence subSequence(int start, int end) {
        int firstSeg = start >> BITS;
        int lastSeg = (end - 1) >> BITS;
        if (firstSeg == lastSeg) {
            try {
                return new CharSlice(data[firstSeg], start & MASK, end - start);
            } catch (ArrayIndexOutOfBoundsException e) {
                e.printStackTrace();
                throw e;
            }
        } else {
            FastStringBuffer fsb = new FastStringBuffer(end - start);
            int firstSegLen = SEGLEN - (start & MASK);
            fsb.append(data[firstSeg], start & MASK, firstSegLen);
            int doneTo = start + firstSegLen;
            while (true) {
                firstSeg++;
                if (doneTo + SEGLEN < end) {
                    fsb.append(data[firstSeg]);
                    doneTo += SEGLEN;
                } else {
                    fsb.append(data[firstSeg], 0, end - doneTo);
                    break;
                }
            }
            return fsb;
        }
    }

    /**
     * Convert to a string
     */

    public String toString() {
        return subSequence(0, length).toString();
    }

    /**
     * Compare equality
     */

    public boolean equals(Object other) {
        return other instanceof CharSequence && toString().equals(other.toString());
    }

    /**
     * Generate a hash code
     */

    public int hashCode() {
        // Same algorithm as String#hashCode(), but not cached
        int h = 0;
        for (char[] chars : data) {
            for (int i = 0; i < SEGLEN; i++) {
                h = 31 * h + chars[i];
            }
        }
        return h;
    }

    /**
     * Returns a new character sequence that is a subsequence of this sequence.
     * Unlike subSequence, this is guaranteed to return a String.
     *
     * @param start index of the first character to be included
     * @param end   index of the character after the last one to be included
     * @return the substring at the given position
     */

    public String substring(int start, int end) {
        return subSequence(start, end).toString();
    }

    /**
     * Write the value to a writer
     *
     * @param writer the writer to which the value is to be written
     * @throws java.io.IOException if an error occurs downstream
     */

    public void write(java.io.Writer writer) throws java.io.IOException {
        writer.write(toString());
    }

    /**
     * Produce diagnostic dump
     */

//    public void dumpDataStructure() {
//        System.err.println("** Segments:");
//        for (int s = 0; s < segments.size(); s++) {
//            System.err.println("   SEG " + s + " start offset " + startOffsets[s] + " length "
//                    + ((FastStringBuffer)segments.get(s)).length());
//        }
//    }

//    public static void main(String[] args) {
//        LargeStringBuffer lsb = new LargeStringBuffer();
//        for (int i=0; i<30; i++)  {
//            char[] chars = new char[i*5000];
//            Arrays.fill(chars, 'x');
//            lsb.append(new String(chars));
//            lsb.append("");
//        }
//        for (int i=0; i<lsb.length()-10000; i+=10000) {
//            System.out.println(i + ":" + lsb.subSequence(i, i+9999).length());
//        }
//        lsb.dumpDataStructure();
//    }

}

