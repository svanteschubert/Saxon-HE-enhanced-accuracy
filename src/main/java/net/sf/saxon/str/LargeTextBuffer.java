////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * The segments (other than the last) have a fixed size of 65536 codepoints,
 * which may use one byte per codepoint, two bytes per codepoint, or three bytes per
 * codepoint, depending on the largest codepoint present in the segment.
 * <p>This is more efficient than a buffer backed by a contiguous array of characters
 * in cases where the size is likely to grow very large, and where substring operations
 * are rare. As used within the TinyTree, extraction of the string value of a node
 * requires character copying only in the case where the value crosses segment
 * boundaries.</p>
 */

public final class LargeTextBuffer {

    private final static int BITS = 16;
    private final static int SEGLEN = 1 << BITS;
    private final static int MASK = SEGLEN - 1;
    private final static int MAX_SEGMENTS = 1 << (31 - BITS);

    private final static Segment EMPTY_SEGMENT = new Segment8(new byte[]{});

    private final List<Segment> completeSegments;
    private Segment lastSegment;
    private int lastSegmentLength;
    private int initialSize;

    /**
     * The LargeTextBuffer comprises a number of segments. This interface defines
     * each segment.
     */

    private interface Segment {
        /**
         * Get the number of bits-per-character in this segment (8, 16, or 24)
         * @return the number of bits per character
         */
        int getWidth();

        /**
         * Return a Segment that contains the existing content of this segment, but
         * stretched if necessary to accommodate a given length and width
         * @param oldLength the number of characters (codepoints) currently used
         *                  in the segment
         * @param newLength the number of characters (codepoints) that the new segment
         *               must have room for; must not exceed the maximum segment length
         * @param newWidth the number of bits-per-character that the new segment must
         *              have room for
         * @return either this Segment, or a replacement
         */
        Segment stretch(int oldLength, int newLength, int newWidth);

        /**
         * Get the content of this segment as a {@code UnicodeString}. This method
         * should only be called on a complete segment (that is, a segment other
         * than the last), to avoid extracting bytes that have been allocated
         * but not used.
         */

        UnicodeString asUnicodeString();

        /**
         * Get a substring of this segment
         * @param start the start offset, inclusive (in codepoints)
         * @param end the end offset, exclusive (in codepoints)
         * @return the substring
         */
        UnicodeString substring(int start, int end);

    }

    /**
     * A Segment comprising 8-bit characters (codepoints in the range 0-255)
     */
    private static class Segment8 implements Segment {
        public byte[] bytes;

        public Segment8(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int getWidth() {
            return 8;
        }

        @Override
        public Segment stretch(int oldLength, int newLength, int newWidth) {
            assert newLength <= SEGLEN;
            if (newWidth <= 8) {
                if (newLength > bytes.length) {
                    bytes = Arrays.copyOf(bytes, Math.max(newLength, Math.min(oldLength*2, SEGLEN)));
                }
                return this;
            } else if (newWidth == 16) {
                char[] array16 = new char[newLength];
                StringTool.copy8to16(bytes, 0, array16, 0, oldLength);
                return new Segment16(array16);
            } else {
                byte[] array24 = new byte[newLength*3];
                StringTool.copy8to24(bytes, 0, array24, 0, oldLength);
                return new Segment24(array24);
            }
        }

        @Override
        public UnicodeString asUnicodeString() {
            return new Twine8(bytes);
        }

        @Override
        public UnicodeString substring(int start, int end) {
            return new Slice8(bytes, start, end);
        }

    }

    /**
     * A Segment comprising 16-bit characters (codepoints in the range 0-65535)
     */

    private static class Segment16 implements Segment {
        public char[] chars;

        /**
         * Construct the segment
         *
         * @param chars an array of chars holding the codepoints, arranged
         *              as two bytes per codepoint; the caller warrants that the
         *              char array contains no surrogates.
         */

        public Segment16(char[] chars) {
            this.chars = chars;
        }

        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public Segment stretch(int oldLength, int newLength, int newWidth) {
            assert newLength <= SEGLEN;
            if (newWidth <= 16) {
                if (newLength > chars.length) {
                    chars = Arrays.copyOf(chars, Math.max(newLength, Math.min(oldLength * 2, SEGLEN)));
                }
                return this;
            } else {
                byte[] array24 = new byte[newLength * 3];
                StringTool.copy16to24(chars, 0, array24, 0, oldLength);
                return new Segment24(array24);
            }
        }

        @Override
        public UnicodeString asUnicodeString() {
            return new Twine16(chars);
        }

        @Override
        public UnicodeString substring(int start, int end) {
            return new Slice16(chars, start, end);
        }
    }

    /**
     * A Segment comprising 24-bit characters (any Unicode codepoints)
     */

    private static class Segment24 implements Segment {
        public byte[] bytes;

        /**
         * Construct the segment
         * @param bytes an array of bytes holding the codepoints, arranged
         *              as three bytes per codepoint.
         */

        public Segment24(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int getWidth() {
            return 24;
        }

        @Override
        public Segment stretch(int oldLength, int newLength, int newWidth) {
            assert newLength <= SEGLEN;
            if (newLength * 3 > bytes.length ) {
                bytes = Arrays.copyOf(bytes, Math.max(newLength * 3, Math.min(oldLength * 6, SEGLEN * 3)));
            }
            return this;
        }

        @Override
        public UnicodeString substring(int start, int length) {
            return new Slice24(bytes, start, length);
        }

        @Override
        public UnicodeString asUnicodeString() {
            return new Twine24(bytes);
        }

    }

    /**
     * Create an empty LargeTextBuffer
     * @param initialSize an estimate of the number of characters needed in the first segment
     */

    public LargeTextBuffer(int initialSize) {
        completeSegments = new ArrayList<>(4);
        lastSegment = EMPTY_SEGMENT;
        lastSegmentLength = 0;
        this.initialSize = Math.max(initialSize, 65536);
    }

    /**
     * Add a segment (which must contain exactly SEGLEN characters) to
     * the list of complete segment
     * @param segment the segment to be added
     */

    private void addSegment(Segment segment) {
        if (completeSegments.size() == MAX_SEGMENTS) {
            throw new IllegalStateException("TinyTree capacity exceeded: more than 2^31 characters of text data");
        }
        completeSegments.add(segment);
    }

    /**
     * Get the n'th segment, counting from zero.
     * @param n the required segment (which may be the last, in which case the segment may be
     *          incomplete)
     * @return the n'th segment
     */

    private Segment getSegment(int n) {
        if (n == completeSegments.size()) {
            return lastSegment;
        } else {
            return completeSegments.get(n);
        }
    }

    /**
     * Append a string to the contents of the LargeTextBuffer
     * @param chars the string to be added
     */

    public void appendUnicodeString(UnicodeString chars) {
        if (chars.isEmpty()) {
            return;
        }
        if (lastSegment == EMPTY_SEGMENT) {
            // indicates this is the first string being added
            int newWidth = chars.getWidth();
            int newLength = Math.max(initialSize, chars.length32()) & 65535;
            if (newWidth <= 8) {
                lastSegment = new Segment8(new byte[newLength]);
            } else if (newWidth == 16) {
                lastSegment = new Segment16(new char[newLength]);
            } else {
                lastSegment = new Segment24(new byte[newLength * 3]);
            }
        }
        int spaceAvailableInLastSegment = SEGLEN - lastSegmentLength;
        //System.err.println("appendUnicodeString " + chars.length() + " " + lastSegmentLength);
        long charsSupplied = chars.length();
        if (charsSupplied < spaceAvailableInLastSegment) {
            extendLastSegment(chars);
        } else {
            long start = 0;
            extendLastSegment(chars.substring(0, spaceAvailableInLastSegment));
            charsSupplied -= spaceAvailableInLastSegment;
            start += spaceAvailableInLastSegment;
            while (charsSupplied > SEGLEN) {
                //System.err.println("appendUnicodeString start=" + start + " supplied=" + charsSupplied);
                extendLastSegment(chars.substring(start, start + SEGLEN));
                charsSupplied -= SEGLEN;
                start += SEGLEN;
            }
            if (charsSupplied > 0) {
                //System.err.println("appendUnicodeStringZ start=" + start + " supplied=" + charsSupplied);
                extendLastSegment(chars.substring(start, start + charsSupplied));
            }
        }
    }

    private void extendLastSegment(UnicodeString chars) {
        //System.err.println("Extend last segment (width " + lastSegment.getWidth() + ") from " + lastSegmentLength + " with " + chars.length());
        lastSegment = lastSegment.stretch(lastSegmentLength, lastSegmentLength + chars.length32(), chars.getWidth());
        if (lastSegment instanceof Segment8) {
            chars.copy8bit(((Segment8)lastSegment).bytes, lastSegmentLength);
        } else if (lastSegment instanceof Segment16) {
            chars.copy16bit(((Segment16)lastSegment).chars, lastSegmentLength);
        } else {
            assert lastSegment instanceof Segment24;
            chars.copy24bit(((Segment24)lastSegment).bytes, lastSegmentLength*3);
        }
        lastSegmentLength += chars.length32();
//        if (Configuration.isAssertionsEnabled()) {
//            lastSegment.substring(0, lastSegmentLength).verifyCharacters();
//        }
        if (lastSegmentLength == SEGLEN) {
            addSegment(lastSegment);
            lastSegment = new Segment8(new byte[1024]);
            lastSegmentLength = 0;
        }
        //showSegmentLengths();
    }

    // Diagnostic method
    private void showSegmentLengths() {
        StringBuilder sb = new StringBuilder();
        for (Segment s : completeSegments) {
            sb.append(s.asUnicodeString().length()).append(", ");
        }
        sb.append(lastSegmentLength);
        System.err.println(sb);
    }


    /**
     * Returns a new character sequence that is a subsequence of this sequence.
     * The subsequence starts with the character at the specified index and
     * ends with the character at index <code>end - 1</code>.  The length of the
     * returned sequence is <code>end - start</code>, so if <code>start == end</code>
     * then an empty sequence is returned.
     *
     * @param start the start index, inclusive (codepoints, not bytes)
     * @param end   the end index, exclusive (codepoints, not bytes)
     * @return the specified subsequence
     * @throws IndexOutOfBoundsException if <code>start</code> or <code>end</code> are negative,
     *                                   if <code>end</code> is greater than <code>length()</code>,
     *                                   or if <code>start</code> is greater than <code>end</code>
     */
    /*@NotNull*/
    public UnicodeString substring(int start, int end) {
        int firstSeg = start >> BITS;
        int lastSeg = (end - 1) >> BITS;
        int lastCP = end & MASK;
        if (lastCP == 0) {
            lastCP = SEGLEN;
        }
        if (firstSeg == lastSeg) {
            // String falls entirely within one segment
            try {
                Segment seg = getSegment(firstSeg);
                return seg.substring(start & MASK, lastCP);
            } catch (ArrayIndexOutOfBoundsException e) {
                e.printStackTrace();
                throw e;
            }
        } else {
            // Concatenate strings from two or more segments
            //System.err.println("Cross-segment s=" + start + " e=" + end);
            UnicodeBuilder ub = new UnicodeBuilder();
            int segNr = firstSeg;
            ub.accept(getSegment(segNr++).substring(start & MASK, SEGLEN));
            while (segNr < lastSeg) {
                ub.accept(getSegment(segNr++).asUnicodeString());
            }
            ub.accept(getSegment(lastSeg).substring(0, lastCP));
            return ub.toUnicodeString();

        }
    }

    public void close() {
//        if (lastSegment != null && lastSegmentLength > 0) {
//            addSegment(lastSegment);
//        }
//        lastSegment = null;
    }

    /**
     * Get the number of characters in the LargeTextBuffer
     * @return the number of characters present
     */
    public int length() {
        if (lastSegment == EMPTY_SEGMENT) {
            return 0;
        } else if (lastSegment == null) {
            return (completeSegments.size()-1) * SEGLEN + lastSegmentLength;
        } else {
            return completeSegments.size() * SEGLEN + lastSegmentLength;
        }
    }

    /**
     * Set the length. If this exceeds the current length, this method is a no-op.
     * If this is less than the current length, characters beyond the specified point
     * are deleted.
     *
     * @param newLength the new length
     */

    public void setLength(int newLength) {
        // used to remove a text node if it's found to be a duplicate
        if (newLength < length()) {
            int segCount = completeSegments.size();
            if (newLength <= segCount * SEGLEN) {
                // drop the current "last segment", and make the last segment in the completed list
                // the new "last segment"
                lastSegment = completeSegments.get(segCount - 1);
                completeSegments.remove(segCount - 1);
            }
            lastSegmentLength = newLength & MASK;
        }
    }

}

