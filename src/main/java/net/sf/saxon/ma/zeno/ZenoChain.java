////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.zeno;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An implementation of sequences as a list-of-lists, where the sublists at the
 * end of the master list tend to be small, and the sublists at the start tend
 * to be larger (or the other way around if the list is built by prepending items
 * rather than appending them). The number of sublists is of the order log(N) where
 * N is the length of the sequence, giving logarithmic performance or better for
 * appending items to either end of the sequence, or for getting the Nth item.
 *
 * <p>This is an immutable data structure; updating operations create a new
 * <code>ZenoChain</code> leaving the original unchanged.</p>
 *
 * <p>In effect the ZenoChain is a tree with a constant depth of 3, implemented as
 * a list of lists.</p>
 *
 * <p>For a list built by appending to the end, the size of sublists goes as
 * follows as the list grows: (1).. (32).. (32,1).. (32,32).. (64,1).. (64,32)..
 * (64,32,1).. (64,32,32).. (64,64,1).. (64,64,32).. (128,32,1).. (128,64,1)..
 * (128,64,32,1).. For a list of 20,000 items we get 10 sublists with sizes
 * (8192, 4096, 4096, 2048, 1024, 256, 128, 64, 64, 32). The exact numbers don't matter,
 * the important thing is that the number of sublists is log(N) with shorter
 * sublists at the end of the sequence where append/prepend operations take place.</p>
 *
 * <p>When two lists are concatenated, the two master lists are first concatenated,
 * followed by a consolidation to combine short lists now appearing near the middle
 * of the structure, to reduce the number of sublists.</p>
 *
 * @param <T> the type of the items in the list
 */

public class ZenoChain<T> implements Iterable<T> {

    // The data structure is implemented as a list of lists

    private final ArrayList<ArrayList<T>> masterList;

    /**
     * Create an empty sequence
     */

    public ZenoChain() {
        masterList = new ArrayList<>(8);
    }

    /**
     * Private constructor to create a ZenoChain with a given master list
     * @param masterList the supplied master list
     */

    private ZenoChain(ArrayList<ArrayList<T>> masterList) {
        this.masterList = masterList;
    }

    /**
     * Append an item to this list. This is an immutable operation; the original list is unchanged
     * @param item the item to be appended
     * @return the list that results from the append operation
     */

    public ZenoChain<T> add(T item) {
        ArrayList<ArrayList<T>> masterList2 = new ArrayList<>(masterList);
        // If the list is empty, create a new singleton list
        if (masterList2.isEmpty()) {
            ArrayList<T> newSegment = new ArrayList<>(32);
            newSegment.add(item);
            masterList2.add(newSegment);
            return new ZenoChain<>(masterList2);
        }
        int threshold = 32;
        int index = masterList2.size() - 1;
        // Get the last segment
        ArrayList<T> segment = masterList2.get(index);
        if (segment.size() < threshold) {
            // if the last segment is smaller than the threshold size, copy it,
            // add the item to the new copy, and change the master list to
            // refer to the new segment.
            ArrayList<T> segment2 = new ArrayList<>(32);
            segment2.addAll(segment);
            segment2.add(item);
            masterList2.set(index, segment2);
            return new ZenoChain<>(masterList2);
        } else {
            // if the last segment has reached the threshold size, consider
            // combining it with the penultimate segment
            while (true) {
                index--;
                threshold *= 2;
                if (index < 0) {
                    // we've reached the start of the list. No combining of segments
                    // is possible, so just create a new final segment containing the new item alone
                    ArrayList<T> newFinalSegment = new ArrayList<>();
                    newFinalSegment.add(item);
                    masterList2.add(newFinalSegment);
                    return new ZenoChain<>(masterList2);
                }
                ArrayList<T> priorSegment = masterList2.get(index);
                if (priorSegment.size() + segment.size() <= threshold) {
                    // combine two adjacent segments into one
                    ArrayList<T> combinedSegment = new ArrayList<>(priorSegment.size() + segment.size());
                    combinedSegment.addAll(priorSegment);
                    combinedSegment.addAll(segment);
                    // add the combined segment to the master list, in place of the first of the pair
                    masterList2.set(index, combinedSegment);
                    // remove the second of the pair segment
                    masterList2.remove(index+1);
                    // create a new final segment containing the new item alone
                    ArrayList<T> newFinalSegment = new ArrayList<>();
                    newFinalSegment.add(item);
                    // and add it to the master list
                    masterList2.add(newFinalSegment);
                    return new ZenoChain<>(masterList2);
                }
                // These two segments couldn't be combined because the total size was too large
                // so we now consider merging earlier segments. For example if the segment sizes
                // were (64, 64, 32) we will merge the first two to become (128, 32)
                segment = priorSegment;
                // continue looping
            }
        }
    }

    /**
     * Prepend an item. This is an immutable operation; the original list is unchanged.
     * @param item the item to be added at the start of the sequence
     * @return the list resulting from the prepend operation
     */

    public ZenoChain<T> prepend(T item) {
        ArrayList<ArrayList<T>> masterList2 = new ArrayList<>(masterList);
        // If the list is empty, create a new singleton list
        if (masterList2.isEmpty()) {
            return add(item);
        }
        int threshold = 32;
        int index = 0;
        ArrayList<T> segment = masterList2.get(index);
        // If the first segment is small enough, extend it by creating a copy
        // with one extra item at the start
        if (segment.size() < threshold) {
            ArrayList<T> segment2 = new ArrayList<>(32);
            segment2.add(item);
            segment2.addAll(segment);
            masterList2.set(index, segment2);
            return new ZenoChain<>(masterList2);
        } else {
            // Starting with the first two segments, see if there are two adjacent segments that
            // can be concatenated into a single segment without exceeding a threshold size. The
            // threshold size increases the further you are from the start of the sequence,
            while (true) {
                index++;
                threshold *= 2;
                if (index >= masterList2.size()) {
                    // We've got to the end without finding two segments to concatenate.
                    // Simply add a new singleton segment at the start.
                    ArrayList<T> newInitialSegment = new ArrayList<>();
                    newInitialSegment.add(item);
                    masterList2.add(0, newInitialSegment);
                    return new ZenoChain<>(masterList2);
                }
                ArrayList<T> nextSegment = masterList2.get(index);
                // Try joining this segment and the next segment
                if (nextSegment.size() + segment.size() <= threshold) {
                    ArrayList<T> combinedSegment = new ArrayList<>();
                    combinedSegment.addAll(segment);
                    combinedSegment.addAll(nextSegment);
                    masterList2.set(index, combinedSegment);
                    masterList2.remove(index - 1);
                    // Now add a new singleton segment at the start
                    ArrayList<T> newInitialSegment = new ArrayList<>();
                    newInitialSegment.add(item);
                    masterList2.add(0, newInitialSegment);
                    return new ZenoChain<>(masterList2);
                }
                // Continue looking for a pair of adjacent segments to combine
                segment = nextSegment;
            }
        }
    }

    /**
     * Append a sequence of items. This is an immutable operation; the original list is unchanged.
     * @param items the sequence of items to be appended
     * @return the concatenated sequence
     */

    public ZenoChain<T> addAll(Iterable<? extends T> items) {
        ZenoChain<T> result = this;
        for (T item : items) {
            result = result.add(item);
        }
        return result;
    }

    /**
     * Concatenate two ZenoChains to form a new ZenoChain, leaving the original operands unchanged
     * @param other the ZenoChain to be concatenated after this one
     * @return a new ZenoChain whose elements are the elements of this ZenoChain followed by
     * the elements of the other ZenoChain.
     */

    public ZenoChain<T> concat(ZenoChain<T> other) {
        ArrayList<ArrayList<T>> newMaster = new ArrayList<>(masterList.size() + other.masterList.size());
        newMaster.addAll(masterList);
        newMaster.addAll(other.masterList);
        return new ZenoChain<T>(newMaster).reorganize();
    }


    /**
     * Replace the item at position n, zero-based
     *
     * @param n     the requested index
     * @param value the replacement value
     * @return the new ZenoChain
     * @throws IndexOutOfBoundsException if n is negative or beyond the end of the list
     */

    public ZenoChain<T> replace(int n, T value) {
        if (n < 0) {
            throw new IndexOutOfBoundsException("Index " + n + " is negative");
        }
        int offset = 0;
        ArrayList<ArrayList<T>> masterList2 = new ArrayList<>(masterList.size());
        boolean done = false;
        for (ArrayList<T> segment : masterList) {
            if (offset + segment.size() > n && !done) {
                ArrayList<T> replacementSegment = new ArrayList<>(segment.size());
                replacementSegment.addAll(segment.subList(0, n - offset));
                replacementSegment.add(value);
                replacementSegment.addAll(segment.subList(n - offset + 1, segment.size()));
                masterList2.add(replacementSegment);
                done = true;
            } else {
                masterList2.add(segment);
            }
            offset += segment.size();
        }
        if (!done) {
            throw new IndexOutOfBoundsException("Index " + n + " is too large");
        }
        return new ZenoChain<>(masterList2);
    }

    /**
     * Remove the item at position n, zero-based
     *
     * @param n the requested index
     * @return the new ZenoChain
     * @throws IndexOutOfBoundsException if n is negative or beyond the end of the list
     */

    public ZenoChain<T> remove(int n) {
        if (n < 0) {
            throw new IndexOutOfBoundsException("Index " + n + " is negative");
        }
        int offset = 0;
        ArrayList<ArrayList<T>> masterList2 = new ArrayList<>(masterList.size());
        boolean done = false;
        for (ArrayList<T> segment : masterList) {
            if (offset + segment.size() > n && !done) {
                if (segment.size() > 1) {
                    ArrayList<T> replacementSegment = new ArrayList<>(segment.size() - 1);
                    replacementSegment.addAll(segment.subList(0, n - offset));
                    replacementSegment.addAll(segment.subList(n - offset + 1, segment.size()));
                    masterList2.add(replacementSegment);
                }
                done = true;
            } else {
                masterList2.add(segment);
            }
            offset += segment.size();
        }
        if (!done) {
            throw new IndexOutOfBoundsException("Index " + n + " is too large");
        }
        return new ZenoChain<>(masterList2);
    }


    /**
     * Insert an item at position n, zero-based
     *
     * @param n     the requested index
     * @param value the replacement value
     * @return the new ZenoChain
     * @throws IndexOutOfBoundsException if n is negative or beyond the end of the list
     */

    public ZenoChain<T> insert(int n, T value) {
        if (n < 0) {
            throw new IndexOutOfBoundsException("Index " + n + " is negative");
        }
        if (n == 0) {
            return prepend(value);
        }
        int length = size();
        if (n == length) {
            return add(value);
        }
        if (n > length) {
            throw new IndexOutOfBoundsException("Index " + n + " is too large");
        }

        int offset = 0;
        ArrayList<ArrayList<T>> masterList2 = new ArrayList<>(masterList.size());
        boolean done = false;
        for (ArrayList<T> segment : masterList) {
            if (offset + segment.size() > n && !done) {
                ArrayList<T> replacementSegment = new ArrayList<>(segment.size() + 1);
                replacementSegment.addAll(segment.subList(0, n - offset));
                replacementSegment.add(value);
                replacementSegment.addAll(segment.subList(n - offset, segment.size()));
                masterList2.add(replacementSegment);
                done = true;
            } else {
                masterList2.add(segment);
            }
            offset += segment.size();
        }
        if (!done) {
            // Shouldn't happen
            throw new IndexOutOfBoundsException("Index " + n + " is too large");
        }
        return new ZenoChain<>(masterList2);
    }


    /**
     * Internal method to reduce fragmentation in a ZenoChain
     * @return a new ZenoChain with identical contents to the supplied ZenoChain,
     * but with better segmentation.
     */

    private ZenoChain<T> reorganize() {
        // Useful after concatenating multiple chains, to reduce the number of segments.
        // Starting from the right, if we find a segment that is smaller than both its
        // neighbours, merge it with its left-hand neighbour.
        for (int i=masterList.size()-2; i>=1; i--) {
            int priorSize = masterList.get(i-1).size();
            int segSize = masterList.get(i).size();
            int nextSize = masterList.get(i+1).size();
            if (segSize <= priorSize && segSize <= nextSize) {
                ArrayList<T> combinedSegment = new ArrayList<>(priorSize + segSize);
                combinedSegment.addAll(masterList.get(i-1));
                combinedSegment.addAll(masterList.get(i));
                masterList.set(i-1, combinedSegment);
                masterList.remove(i);
            }
        }
        return new ZenoChain<T>(masterList);
    }


    /**
     * Get the item at position n, zero-based
     * @param n the requested index
     * @return the item at position n
     * @throws IndexOutOfBoundsException if n is negative or beyond the end of the list
     */

    public T get(int n) {
        if (n < 0) {
            throw new IndexOutOfBoundsException("Index " + n + " is negative");
        }
        int offset = 0;
        for (ArrayList<T> segment : masterList) {
            if (offset + segment.size() > n) {
                return segment.get(n - offset);
            }
            offset += segment.size();
        }
        throw new IndexOutOfBoundsException("Index " + n + " is too large");
    }

    /**
     * Get a sublist of this list.
     * <p>Note that unlike {@link List#subList(int, int)}, the returned list is not "backed" by the
     * original list; changes to the returned list will not affect the original list in any way.
     * (This is inevitable, since both lists are immutable).</p>
     * @param start the zero-based start position of the required sublist, inclusive
     * @param end the zero-based end position of the required sublist, exclusive
     * @return the sublist, as a new ZenoChain
     * @throws IndexOutOfBoundsException under the same conditions as for {@link List#subList(int, int)}:
     *  (start &lt; 0 || end &gt; size || start &gt; end)
     */

    public ZenoChain<T> subList(int start, int end) {
        // The implementation approach is as follows. We always create a new master list.
        // Segments of the original list that are fully in the range of the sublist are
        // referenced from the new master list directly, without copying. Segments that
        // overlap the requested start and end points are partially "copied", as required,
        // using the Java {@List.sublist()} mechanism - which does not actually create a
        // copy.
        if (start < 0 || start > end) {
            throw new IndexOutOfBoundsException("start position for subList is out of range");
        }
        ArrayList<ArrayList<T>> newMaster = new ArrayList<>();
        int offset = 0;
        int remainingLength = end - start;
        boolean active = false;
        // Process all the segments, with different treatment for (a) segments before the
        // start position, (b) segments overlapping the start position, (c) segments wholly
        // included in the sublist, (d) segments overlapping the end position, (e) segments
        // beyond the end position.
        for (ArrayList<T> segment : masterList) {
            if (active) {
                if (remainingLength > segment.size()) {
                    // Segment is wholly included
                    remainingLength -= segment.size();
                    newMaster.add(segment);  // No need to copy the segment, because it's immutable
                } else {
                    // Segment spans the end position
                    newMaster.add(new ArrayList<T>(segment.subList(0, remainingLength)));
                    return new ZenoChain<T>(newMaster);
                }
            } else if (offset + segment.size() > start) {
                // segment spans the start position
                int localStart = start - offset;
                if (remainingLength > segment.size() - localStart) {
                    // we copy this segment to the end
                    if (start == 1 && segment.size() > 128) {
                        // special case for tail() - break a long first segment to reduce the cost next time.
                        // This assumes it's likely tail() will be called again on the sublist
                        newMaster.add(new ArrayList<T>(segment.subList(localStart, localStart + 64)));
                        newMaster.add(new ArrayList<T>(segment.subList(localStart + 64, segment.size())));
                    } else {
                        newMaster.add(new ArrayList<T>(segment.subList(localStart, segment.size())));
                    }
                    remainingLength -= (segment.size() - localStart);
                    active = true;
                } else {
                    // segment spans both the start and end positions
                    newMaster.add(new ArrayList<T>(segment.subList(localStart, localStart + remainingLength)));
                    return new ZenoChain<T>(newMaster);
                }
            } else if (remainingLength == 0) {
                break; // do nothing; we're past the end position.
            }
            offset += segment.size();
        }
        if (remainingLength > 0) {
            throw new IndexOutOfBoundsException("end position for subList is out of range");
        }
        return new ZenoChain<T>(newMaster);
    }

    /**
     * Get the size of the list
     * @return the size of the list
     */

    public int size() {
        int total = 0;
        for (ArrayList<T> segment : masterList) {
            total += segment.size();
        }
        return total;
    }

    /**
     * Ask if the list is empty
     * @return true if the size is zero
     */

    public boolean isEmpty() {
        return masterList.isEmpty()
                || (masterList.size()==1 && masterList.get(0).isEmpty());
    }

    /**
     * Ask if the list is a singleton
     *
     * @return true if the size is one
     */

    public boolean isSingleton() {
        return masterList.size() == 1 && masterList.get(0).size() == 1;
    }

    /**
     * Iterate over the items
     * @return a Java-style iterator over the items
     */

    public Iterator<T> iterator() {
        return new ZenoChainIterator<T>(masterList);
    }

    /**
     * Get a string representation of the ZenoChain.
     * @return the string representation in the form <code>(X,Y,Z,...)</code> where <code>X</code>,
     * <code>Y</code>, and <code>Z</code> are the string representations of the elements of the sequence
     */

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (List<T> segment : masterList) {
            sb.append("(");
            for (T item : segment) {
                sb.append(item).append(",");
            }
            sb.setCharAt(sb.length()-1, ')');
        }
        return sb.toString();
    }

    /**
     * Diagnostic display of a {@code ZenoChain}, exposing its internal structure
     * @return a string that shows the sizes of the segments, for example (64,32,4) for a
     * {@code ZenoChain} of length 100.
     */

    public String showMetrics() {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (List<T> segment : masterList) {
            sb.append(segment.size()).append(",");
        }
        sb.setCharAt(sb.length()-1, ')');
        return sb.toString();
    }


}

