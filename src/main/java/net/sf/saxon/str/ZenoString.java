////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.expr.sort.EmptyIntIterator;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.z.IntIterator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.IntPredicate;

/**
 * A ZenoString is an implementation of UnicodeString that comprises a list
 * of segments representing substrings of the total string. By convention the
 * segments are not themselves ZenoStrings, so the structure is a shallow tree.
 * An index holds pointers to the segments and their offsets within the string
 * as a whole; this is used to locate the codepoint at any particular location
 * in the string.
 *
 * <p>The segments will always be non-empty. An empty string contains no segments.</p>
 *
 * <p>The key to the performance of the data structure (and its name) is
 * the algorithm for consolidating segments when strings are concatenated,
 * so as to keep the number of segments increasing logarithmically with
 * the string size, with short segments at the extremities to allow efficient
 * further concatenation at the ends.</p>
 *
 * <p>For further details see the paper by Michael Kay at Balisage 2021.</p>
 */

public class ZenoString extends UnicodeString {

    private List<UnicodeString> segments = new ArrayList<>();
    private List<Long> offsets = new ArrayList<>();

    /**
     * Private constructor creating an empty ZenoString (containing an empty list of segments)
     */

    private ZenoString() {}

    /**
     * Private constructor creating a ZenoString with a single segment
     */

    private ZenoString(UnicodeString content) {
        segments.add(content);
        offsets.add(0L);
    }

    /**
     * An empty ZenoString
     */

    public static final ZenoString EMPTY = new ZenoString();

    /**
     * Construct a ZenoString from a supplied UnicodeString
     * @param content the supplied UnicodeString
     * @return the resulting ZenoString
     */

    public static ZenoString of(UnicodeString content) {
         if (content instanceof ZenoString) {
             return (ZenoString) content;
         } else if (content.isEmpty()) {
             return new ZenoString();
         } else {
             return new ZenoString(content);
         }
    }

    /**
     * Get the index of the segment containing the character at a given offset in the string
     * @param offset the offset of the character in the string. This must be greater
     *               than or equal to zero, and less than length of the string.
     * @return the index of the segment containing the required character
     * @throws IndexOutOfBoundsException if the supplied offset is out of range.
     */

    private int segmentForOffset(long offset) {
        if (segments.size() == 0) {
            throw new IndexOutOfBoundsException("ZenoString is empty");
        }
        int result = binarySearch(offset, 0, offsets.size() - 1);
        if (result < 0) {
            throw new IndexOutOfBoundsException("Index " + offset + " out of range 0-" + (length()-1));
        }
        return result;
    }

    private int binarySearch(long offset, int start, int end) {
        //System.err.println("BinarySearch " + start + " " + end);
        if (start == end) {
            long s = offsets.get(start);
            long e = s + segments.get(start).length();
            if (s <= offset && e > offset) {
                return start;
            } else {
                return -1;
            }
        } else {
            int mid = start + (end - start + 1) / 2;
            if (offsets.get(mid) > offset) {
                return binarySearch(offset, start, mid-1);
            } else {
                return binarySearch(offset, mid, end);
            }
        }
    }


    /**
     * Get an iterator over the code points present in the string.
     * @return an iterator that delivers the individual code points
     */
    @Override
    @CSharpReplaceBody(code="return new Saxon.Impl.Overrides.ZenoStringCodepoints(segments);")
    public IntIterator codePoints() {
        if (isEmpty()) {
            return EmptyIntIterator.getInstance();
        }
        return new IntIterator() {
            final Iterator<UnicodeString> outerIterator = segments.iterator();
            IntIterator innerIterator;

            @Override
            public boolean hasNext() {
                if (innerIterator == null) {
                    return outerIterator.hasNext();
                } else if (innerIterator.hasNext()) {
                    return true;
                } else {
                    innerIterator = null;
                    return outerIterator.hasNext();
                }
            }

            @Override
            public int next() {
                if (innerIterator == null) {
                    if (outerIterator.hasNext()) {
                        innerIterator = outerIterator.next().codePoints();
                    } else {
                        throw new NoSuchElementException();
                    }
                }
                return innerIterator.next();
            }
        };
    }

    /**
     * Get the length of the string
     *
     * @return the number of code points in the string
     */
    @Override
    public long length() {
        int i = segments.size()-1;
        return i < 0 ? 0L : offsets.get(i) + segments.get(i).length();
    }

    /**
     * Ask whether the string is empty
     *
     * @return true if the length of the string is zero
     */
    @Override
    public boolean isEmpty() {
        return segments.isEmpty();
    }

    /**
     * Get the number of bits needed to hold all the characters in this string
     *
     * @return 7 for ascii characters, 8 for latin-1, 16 for BMP, 24 for general Unicode.
     */
    @Override
    public int getWidth() {
        int maxWidth = 7;
        for (UnicodeString entry : segments) {
            int width = entry.getWidth();
            if (width == 24) {
                return 24;
            } else {
                maxWidth = Math.max(maxWidth, width);
            }
        }
        return maxWidth;
    }

    /**
     * Get the position of the first occurrence of the specified codepoint,
     * starting the search at a given position in the string
     *
     * @param codePoint the sought codePoint
     * @param from      the position from which the search should start (0-based). A negative value is
     *                  treated as zero. A position beyond the end of the string results in a return
     *                  value of -1 (meaning not found).
     * @return the position (0-based) of the first occurrence found, or -1 if not found
     * @throws IndexOutOfBoundsException if the <code>from</code> value is out of range
     */
    @Override
    public long indexOf(int codePoint, long from) {
        from = Math.max(from, 0);
        if (from >= length()) {
            return -1L;
        }
        int first = segmentForOffset(from);
        for (int i=first; i<segments.size(); i++) {
            UnicodeString segment = segments.get(i);
            long offset = offsets.get(i);
            long pos = segment.indexOf(codePoint, i==first ? from - offset : 0);
            if (pos >= 0) {
                return pos + offset;
            }
        }
        return -1;
    }

    @Override
    public long indexWhere(IntPredicate predicate, long from) {
        int first = segmentForOffset(from);
        for (int i = first; i < segments.size(); i++) {
            UnicodeString segment = segments.get(i);
            long offset = offsets.get(i);
            long pos = segment.indexWhere(predicate, i == first ? from - offset : 0);
            if (pos >= 0) {
                return pos + offset;
            }
        }
        return -1;
    }

    /**
     * Get the code point at a given position in the string
     * @param index the given position (0-based)
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    @Override
    public int codePointAt(long index) {
        int entry = segmentForOffset(index);
        UnicodeString segment = segments.get(entry);
        return segment.codePointAt(index - offsets.get(entry));
    }

    /**
     * Get a substring of this codepoint sequence, with a given start and end position
     *
     * @param start the start position (0-based): that is, the position of the first
     *              code point to be included
     * @param end   the end position (0-based): specifically, the position of the first
     *              code point not to be included
     */
    @Override
    public UnicodeString substring(long start, long end) {
        checkSubstringBounds(start, end);
        if (start == end) {
            return EmptyUnicodeString.getInstance();
        } else if (start + 1 == end) {
            return new UnicodeChar(codePointAt(start));
        }
        int first = segmentForOffset(start);
        int last = segmentForOffset(end-1);
        if (first == last) {
            UnicodeString segment = segments.get(first);
            long offset = offsets.get(first);
            return segment.substring(start - offset, end - offset);
        } else {
            ZenoString z = ZenoString.of(segments.get(first).substring(start - offsets.get(first)));
            for (int i=first+1; i<last; i++) {
                z = z.concat(segments.get(i));
            }
            return z.concat(segments.get(last).prefix(end-offsets.get(last)));
        }
    }

    /**
     * Ask whether this string has another string as its content starting at a given offset
     *
     * @param other  the other string
     * @param offset the starting position in this string (counting in codepoints)
     * @return true if the other string appears as a substring of this string starting at the
     * given position.
     * @throws IndexOutOfBoundsException if {@code offset} is less than zero or greater than the
     *     length of this string. Note that there is no exception if {@code offset + other.length()}
     *     exceeds {@code this.length()} - instead this results in a return value of {@code false}.
     */

    public boolean hasSubstring(UnicodeString other, long offset) {
        // Override inherited implementation because codePointAt(n) is relatively expensive
        if (offset < 0 || offset > length()) {
            throw new IndexOutOfBoundsException();
        }
        long len = other.length();
        if (len + offset > length()) {
            return false;
        }
        long end = offset + len;
        int first = segmentForOffset(offset);
        int last = segmentForOffset(end - 1);
        if (first == last) {
            UnicodeString segment = segments.get(first);
            long segmentOffset = offsets.get(first);
            return segment.hasSubstring(other, offset - segmentOffset);
        } else {
            return substring(offset, end).equals(other);
        }

    }


    /**
     * Concatenate another string
     *
     * @param other the string to be appended to this one
     * @return the result of the concatenation (neither input string is altered)
     */
    @Override
    public ZenoString concat(UnicodeString other) {
        // Here's the critical decision - whether or not to merge the new string with the last
        // segment of the previous one
        if (isEmpty()) {
            return other instanceof ZenoString ? (ZenoString)other : new ZenoString(other);
        } else if (other.isEmpty()) {
            return this;
        }
        if (other instanceof ZenoString) {
            ZenoString z = new ZenoString();
            z.segments = new ArrayList<>(segments);
            z.segments.addAll(((ZenoString)other).segments);
            z.offsets = new ArrayList<>(offsets);
            long len = length();
            for (long offset : ((ZenoString) other).offsets) {
                z.offsets.add(offset + len);
            }
            return (len < 32 || other.length() < 32 ? z.consolidate0() : z);
        } else {
            ZenoString z = new ZenoString();
            z.segments = new ArrayList<>(segments);
            z.offsets = new ArrayList<>(offsets);
            z.segments.add(other);
            z.offsets.add(length());
            return z.consolidate0();
        }
    }

    @Override
    void copy8bit(byte[] target, int offset) {
        for (UnicodeString us : segments) {
            us.copy8bit(target, offset);
            offset += us.length32();
        }
    }

    @Override
    void copy16bit(char[] target, int offset) {
        for (UnicodeString us : segments) {
            us.copy16bit(target, offset);
            offset += us.length32();
        }
    }

    @Override
    void copy24bit(byte[] target, int offset) {
        for (UnicodeString us : segments) {
            us.copy24bit(target, offset);
            offset += (us.length32() * 3);
        }
    }


    @Override
    void copy32bit(int[] target, int offset) {
        for (UnicodeString us : segments) {
            us.copy32bit(target, offset);
            offset += us.length32();
        }
    }

    private ZenoString consolidate() {
        // internal, so works in-situ

        int i = segments.size()-2;
        long prevLength = segments.get(i+1).length();
        while (i >= 0) {
            long thisLength = segments.get(i).length();
            long nextLength = i == 0 ? 0 : segments.get(i-1).length();
            if ((thisLength <= prevLength && thisLength <= nextLength) || thisLength + prevLength <= 32) {
                segments.set(i, concatSegments(segments.get(i), segments.get(i + 1)));
                //Instrumentation.count("charCopy", segments.get(i).length32() + segments.get(i + 1).length32());
                //Instrumentation.count("copyOperations");
                segments.remove(i + 1);
                offsets.remove(i + 1);
                prevLength = segments.get(i).length();
            } else {
                prevLength = thisLength;
            }
            i--;
        }
        //showSegmentLengths();
        return this;
    }

    /**
     * Write each of the segments in turn to a UnicodeWriter
     * @param writer the writer to which the string is to be written
     */

    public void writeSegments(UnicodeWriter writer) throws IOException {
        for (UnicodeString str : segments) {
            writer.write(str);
        }
    }

    public static UnicodeString concatSegments(UnicodeString left, UnicodeString right) {
        if (left.getWidth() <= 8 && right.getWidth() <= 8) {
            byte[] newByteArray = new byte[left.length32() + right.length32()];
            left.copy8bit(newByteArray, 0);
            right.copy8bit(newByteArray, left.length32());
            return new Twine8(newByteArray);
        } else if (left.getWidth() <= 16 && right.getWidth() <= 16) {
            char[] newCharArray = new char[left.length32() + right.length32()];
            left.copy16bit(newCharArray, 0);
            right.copy16bit(newCharArray, left.length32());
            return new Twine16(newCharArray);
        } else {
            byte[] newByteArray = new byte[(left.length32() + right.length32()) * 3];
            left.copy24bit(newByteArray, 0);
            right.copy24bit(newByteArray, left.length32() * 3);
            return new Twine24(newByteArray);
        }
    }



    private ZenoString consolidate0() {
        // internal, so works in-situ

        for (int i=segments.size()-2; i>=0; i--) {
            double nextLength = segments.get(i + 1).length() * 1.1;
            if (segments.get(i).length() < nextLength) {
                segments.set(i, concatSegments(segments.get(i), segments.get(i+1)));
                segments.remove(i+1);
                offsets.remove(i+1);
            }
        }

        //verifySegmentLengths();
        return this;
    }

    private ZenoString consolidate1() {
        // internal, so works in-situ

        int halfway = segments.size()/2;
        for (int i=0; i<(halfway-1); i++) {
            if (segments.get(i).length() +segments.get(i+1).length() < (32L << i)) {
                UnicodeString merged = segments.get(i).concat(segments.get(i+1));
                segments.remove(i+1);
                offsets.remove(i+1);
                segments.set(i, merged);
            }
        }

        int distance = 0;
        for (int i = segments.size() - 1; i > halfway; i--) {
            if (segments.get(i).length() + segments.get(i-1).length() <  (32L << (distance++))) {
                UnicodeString merged = segments.get(i-1).concat(segments.get(i));
                segments.remove(i);
                offsets.remove(i);
                segments.set(i-1, merged);
            }
        }

        //showSegmentLengths();
        return this;
    }

    /**
     * Get an equivalent UnicodeString that uses the most economical representation available
     *
     * @return an equivalent UnicodeString
     */
    @Override
    public UnicodeString economize() {
        int segs = segments.size();
        if (segs == 0) {
            return EmptyUnicodeString.getInstance();
        } else if (segs == 1) {
            return segments.get(0);
        } else if (segs < 32 && length() < 256 && getWidth() <= 16) {
            // Return a single wrapped Java String, for economy of any subsequent toString() operations.
            return new BMPString(toString());
        } else {
            return this;
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (UnicodeString str : segments) {
            sb.append(str.toString());
        }
        return sb.toString();
    }

    /**
     * This method is for diagnostics and unit testing only: it exposes
     * the lengths of the internal segments. This is an implementation detail
     * that is subject to change and does not affect the exposed functionality.
     * @return the lengths of the segments
     */

    public List<Long> debugSegmentLengths() {
        List<Long> result = new ArrayList<>(segments.size());
        for (UnicodeString str : segments) {
            result.add(str.length());
        }
        return result;
    }

    // Diagnostic method
    private void showSegmentLengths() {
        StringBuilder sb = new StringBuilder();
        for (UnicodeString str : segments) {
            sb.append(str.length() + ", ");
        }
        System.err.println(sb);
    }

    private void verifySegmentLengths() {
        long total = 0;
        for (int i = 0; i<segments.size(); i++) {
            if (offsets.get(i) != total) {
                showSegmentLengths();
                throw new IllegalStateException("Bad offset for segment " + i);
            }
            total += segments.get(i).length();
        }
    }
//
//    public void showSegments() {
//        StringBuilder sb = new StringBuilder();
//        for (UnicodeString str : segments) {
//            sb.append(str.toString()).append(" ");
//        }
//        System.err.println(sb);
//    }

//    public static void main(String[] args) throws Exception {
//        //testSingleChars();
// //       testSubstring();
//        //ZenoString z = testWords();
//        //testInsertion(z);
//        alphabet();
//    }
//
//    public static void testSubstring() {
//        ZenoString z = new ZenoString();
//        z = z.concat(new BMPString("In a hole in the ground there lived a hobbit. "));
//        z = z.concat(new BMPString("Not an ordinary dirty smelly hole."));
//        System.err.println(z.substring(0, 10));
//        System.err.println(z.substring(24, 44));
//        System.err.println(z.substring(50, 60));
//        System.err.println(z.substring(24, 60));
//    }
//
//    public static ZenoString testWords() throws Exception {
//        ZenoString z = new ZenoString();
//        Processor proc = new Processor(true);
//        DocumentBuilder builder = proc.newDocumentBuilder();
//        XdmNode doc = builder.build(new File("/Users/mike/GitHub/saxon2020/src/test/testdata/othello.xml"));
//        int words = 0;
//        int totalLength = 0;
//        boolean prepend = false;
//        for (XdmNode text : doc.select(Steps.descendant(Predicates.isText())).asListOfNodes()) {
//            for (String s : text.getStringValue().split(" ")) {
//                if (prepend) {
//                    z = new ZenoString(new BMPString(s)).concat(z);
//                } else {
//                    z = z.concat(new BMPString(s));
//                }
//                words++;
//                totalLength += s.length();
//            }
//        }
//        System.err.println("words: " + words + " av length " + (double)totalLength/(double)words);
//        z.showSegmentLengths();
//        return z;
//    }
//
//    private static void testSingleChars() throws Exception {
//        ZenoString z = new ZenoString();
//        for (int i=0; i<1_000_000; i++) {
//            z = z.concat(StringConstants.ASTERISK);
//        }
//        z.showSegmentLengths();
//    }
//
//    private static void testInsertion(ZenoString z) {
//        System.err.println("Before insertions: ");
//        z.showSegmentLengths();
//        long start = 0;
//        ZenoString result = new ZenoString();
//        UnicodeString bed = new BMPString("bed");
//        int occurrences = 0;
//        while (start >= 0 && start < z.length()) {
//            long next = start + 1000;
//            if (next > z.length()) {
//                break;
//            }
//            result = result.concat(z.substring(start, next)).concat(StringConstants.ASTERISK);
//            start = next+1;
//            occurrences++;
//        }
//        System.err.println("Replacements: " + occurrences);
//        result.showSegmentLengths();
//    }
//
//    private static void alphabet() {
//        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
//        alphabet = alphabet + alphabet + alphabet + alphabet;
//        ZenoString z = new ZenoString();
//        for (int i=0; i<alphabet.length(); i++) {
//            z = z.concat(new UnicodeChar(alphabet.charAt(i)));
//            z.showSegments();
//        }
//    }
}

