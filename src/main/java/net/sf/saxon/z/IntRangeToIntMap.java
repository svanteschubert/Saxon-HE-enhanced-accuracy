////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;




/**
 * Set of int values. This implementation of IntSet uses a sorted array
 * of integer ranges.
 *
 */
public class IntRangeToIntMap implements IntToIntMap {

    // The array of start points, which will always be sorted
    private int[] startPoints;

    // The array of end points, which will always be sorted
    private int[] endPoints;

    // The array of corresponding values
    private int[] values;

    // The number of elements of the above two arrays that are actually in use
    private int used;

    // The number of items in the map
    private int count = 0;

    // The default value
    private int defaultValue = Integer.MIN_VALUE;

    /**
     * Create an empty map, with given initial capacity
     *
     * @param capacity the initial capacity
     */
    public IntRangeToIntMap(int capacity) {
        startPoints = new int[capacity];
        endPoints = new int[capacity];
        values = new int[capacity];
        used = 0;
        count = 0;
    }

    /**
     * Set the value to be returned to indicate an unused entry
     *
     * @param defaultValue the value to be returned by {@link #get(int)} if no entry
     *                     exists for the supplied key
     */
    @Override
    public void setDefaultValue(int defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Get the default value used to indicate an unused entry
     *
     * @return the value to be returned by {@link #get(int)} if no entry
     * exists for the supplied key
     */
    @Override
    public int getDefaultValue() {
        return this.defaultValue;
    }

    /**
     * Create an IntRangeSet given the start points and end points of the integer ranges.
     * The two arrays must be the same length; each must be in ascending order; and the n'th end point
     * must be greater than the n'th start point, and less than the n+1'th start point, for all n.
     *
     * @param startPoints the start points of the integer ranges
     * @param endPoints   the end points of the integer ranges
     * @throws IllegalArgumentException if the two arrays are different lengths. Other error conditions
     *                                  in the input are not currently detected.
     */

    public IntRangeToIntMap(int[] startPoints, int[] endPoints) {
        if (startPoints.length != endPoints.length) {
            throw new IllegalArgumentException("Array lengths differ");
        }
        this.startPoints = startPoints;
        this.endPoints = endPoints;
        used = startPoints.length;
        for (int i = 0; i < used; i++) {
            count += (endPoints[i] - startPoints[i] + 1);
        }
    }

    @Override
    public void clear() {
        startPoints = new int[4];
        endPoints = new int[4];
        values = new int[4];
        used = 0;
    }


    @Override
    public int size() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public boolean contains(int value) {
        if (used == 0) {
            return false;
        }
        if (value > endPoints[used - 1]) {
            return false;
        }
        if (value < startPoints[0]) {
            return false;
        }
        int i = 0;
        int j = used;
        do {
            int mid = i + (j - i) / 2;
            if (endPoints[mid] < value) {
                i = Math.max(mid, i + 1);
            } else if (startPoints[mid] > value) {
                j = Math.min(mid, j - 1);
            } else {
                return true;
            }
        } while (i != j);
        return false;
    }

    public int get(int value) {
        if (used == 0) {
            return getDefaultValue();
        }
        if (value > endPoints[used - 1]) {
            return getDefaultValue();
        }
        if (value < startPoints[0]) {
            return getDefaultValue();
        }
        int i = 0;
        int j = used;
        do {
            int mid = i + (j - i) / 2;
            if (endPoints[mid] < value) {
                i = Math.max(mid, i + 1);
            } else if (startPoints[mid] > value) {
                j = Math.min(mid, j - 1);
            } else {
                return values[mid];
            }
        } while (i != j);
        return getDefaultValue();
    }

    @Override
    public boolean remove(int value) {
        throw new UnsupportedOperationException("remove");
    }

    /**
     * Add an entry to the map. Ranges of entries must be added in ascending numerical order
     *
     * @param start the start of the range to be added
     * @param end the end of the range to be added
     * @param value the value corresponding to this range of integer keys
     */

    public void addEntry(int start, int end, int value) {
        ensureCapacity(used + 1);
        startPoints[used] = start;
        endPoints[used] = end;
        values[used] = value;
        used++;
        count += (end - start + 1);
    }


    private void ensureCapacity(int n) {
        if (startPoints.length < n) {
            int[] s = new int[startPoints.length * 2];
            int[] e = new int[startPoints.length * 2];
            int[] v = new int[startPoints.length * 2];
            System.arraycopy(startPoints, 0, s, 0, used);
            System.arraycopy(endPoints, 0, e, 0, used);
            System.arraycopy(values, 0, v, 0, used);
            startPoints = s;
            endPoints = e;
            values = v;
        }
    }


    public String toString() {
        StringBuilder sb = new StringBuilder(used * 8);
        for (int i = 0; i < used; i++) {
            sb.append(startPoints[i]).append("-")
                    .append(endPoints[i]).append(":")
                    .append(values[i]).append(",");
        }
        return sb.toString();
    }

//    /**
//     * Test whether this set has exactly the same members as another set. Note that
//     * IntRangeSet values are <b>NOT</b> comparable with other implementations of IntSet
//     */
//
//    public boolean equals(Object other) {
//        if (other instanceof IntSet) {
//            if (other instanceof IntRangeToIntMap) {
//                return used == ((IntRangeToIntMap) other).used &&
//                        Arrays.equals(startPoints, ((IntRangeToIntMap) other).startPoints) &&
//                        Arrays.equals(endPoints, ((IntRangeToIntMap) other).endPoints);
//            } else {
//                return containsAll((IntSet) other);
//            }
//        } else {
//            return false;
//        }
//    }
//
//    /**
//     * Construct a hash key that supports the equals() test
//     */
//
//    public int hashCode() {
//        // Note, hashcodes are NOT the same as those used by IntHashSet and IntArraySet
//        if (_hashCode == -1) {
//            int h = 0x436a89f1;
//            for (int i = 0; i < used; i++) {
//                h ^= startPoints[i] + (endPoints[i] << 3);
//            }
//            _hashCode = h;
//        }
//        return _hashCode;
//    }



    /**
     * Get the start points of the ranges
     *
     * @return the start points
     */

    public int[] getStartPoints() {
        return startPoints;
    }

    /**
     * Get the end points of the ranges
     *
     * @return the end points
     */

    public int[] getEndPoints() {
        return endPoints;
    }

    /**
     * Get the number of ranges actually in use
     *
     * @return the number of ranges
     */

    public int getNumberOfRanges() {
        return used;
    }

    /**
     * Adds a key-value pair to the map.
     *
     * @param key   Key
     * @param value Value
     */
    @Override
    public void put(int key, int value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Get an iterator over the integer key values held in the hash map
     *
     * @return an iterator whose next() call returns the key values (in arbitrary order)
     */
    @Override
    public IntIterator keyIterator() {
        IntRangeSet irs = new IntRangeSet(startPoints, endPoints);
        return irs.iterator();
    }
}

