////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;




import java.util.Arrays;

/**
 * Set of int values. This implementation of IntSet uses a sorted array
 * of integer ranges.
 *
 */
public class IntRangeSet extends IntSet {

    // The array of start points, which will always be sorted
    private int[] startPoints;

    // The array of end points, which will always be sorted
    private int[] endPoints;

    // The number of elements of the above two arrays that are actually in use
    private int used = 0;

    // Hashcode, evaluated lazily
    private int _hashCode = -1;

    // The number of items in the set
    private int count = 0;

    /**
     * Create an empty set
     */
    public IntRangeSet() {
        startPoints = new int[4];
        endPoints = new int[4];
        used = 0;
        count = 0;
        _hashCode = -1;
    }

    /**
     * Create one IntRangeSet as a copy of another
     *
     * @param input the IntRangeSet to be copied
     */

    public IntRangeSet(IntRangeSet input) {
        startPoints = new int[input.used];
        endPoints = new int[input.used];
        used = input.used;
        System.arraycopy(input.startPoints, 0, startPoints, 0, used);
        System.arraycopy(input.endPoints, 0, endPoints, 0, used);
        _hashCode = input._hashCode;
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

    public IntRangeSet(int[] startPoints, int[] endPoints) {
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
        used = 0;
        _hashCode = -1;
    }

    @Override
    public IntSet copy() {
        IntRangeSet s = new IntRangeSet();
        s.startPoints = new int[startPoints.length];
        System.arraycopy(startPoints, 0, s.startPoints, 0, startPoints.length);
        //s.startPoints = Arrays.copyOf(startPoints, startPoints.length);
        s.endPoints = new int[endPoints.length];
        System.arraycopy(endPoints, 0, s.endPoints, 0, endPoints.length);
        //s.endPoints = Arrays.copyOf(endPoints, endPoints.length);
        s.used = used;
        s.count = count;
        return s;
    }

    @Override
    public IntSet mutableCopy() {
        return copy();
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public int size() {
        return count;
    }

    @Override
    public boolean isEmpty() {
        return count == 0;
    }

    @Override
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

    @Override
    public boolean remove(int value) {
        throw new UnsupportedOperationException("remove");
    }

    /**
     * Add an integer to the set
     *
     * @param value the integer to be added
     * @return true if the integer was added, false if it was already present
     */

    @Override
    public boolean add(int value) {
        _hashCode = -1;
        if (used == 0) {
            ensureCapacity(1);
            startPoints[used - 1] = value;
            endPoints[used - 1] = value;
            count++;
            return true;
        }
        if (value > endPoints[used - 1]) {
            if (value == endPoints[used - 1] + 1) {
                endPoints[used - 1]++;
            } else {
                ensureCapacity(used + 1);
                startPoints[used - 1] = value;
                endPoints[used - 1] = value;
            }
            count++;
            return true;
        }
        if (value < startPoints[0]) {
            if (value == startPoints[0] - 1) {
                startPoints[0]--;
            } else {
                ensureCapacity(used + 1);
                System.arraycopy(startPoints, 0, startPoints, 1, used - 1);
                System.arraycopy(endPoints, 0, endPoints, 1, used - 1);
                startPoints[0] = value;
                endPoints[0] = value;
            }
            count++;
            return true;
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
                return false;   // value is already present
            }
        } while (i != j);
        if (i > 0 && endPoints[i - 1] + 1 == value) {
            i--;
        } else if (i < used - 1 && startPoints[i + 1] - 1 == value) {
            i++;
        }
        if (endPoints[i] + 1 == value) {
            if (value == startPoints[i + 1] - 1) {
                // merge the two ranges
                endPoints[i] = endPoints[i + 1];
                System.arraycopy(startPoints, i + 2, startPoints, i + 1, used - i - 2);
                System.arraycopy(endPoints, i + 2, endPoints, i + 1, used - i - 2);
                used--;
            } else {
                endPoints[i]++;
            }
            count++;
            return true;
        } else if (startPoints[i] - 1 == value) {
            if (value == endPoints[i - 1] + 1) {
                // merge the two ranges
                endPoints[i - 1] = endPoints[i];
                System.arraycopy(startPoints, i + 1, startPoints, i, used - i - 1);
                System.arraycopy(endPoints, i + 1, endPoints, i, used - i - 1);
                used--;
            } else {
                startPoints[i]--;
            }
            count++;
            return true;
        } else {
            if (value > endPoints[i]) {
                i++;
            }
            ensureCapacity(used + 1);
            try {
                System.arraycopy(startPoints, i, startPoints, i + 1, used - i - 1);
                System.arraycopy(endPoints, i, endPoints, i + 1, used - i - 1);
            } catch (Exception err) {
                err.printStackTrace();
            }
            startPoints[i] = value;
            endPoints[i] = value;
            count++;
            return true;
        }
    }

    private void ensureCapacity(int n) {
        if (startPoints.length < n) {
            int[] s = new int[startPoints.length * 2];
            int[] e = new int[startPoints.length * 2];
            System.arraycopy(startPoints, 0, s, 0, used);
            System.arraycopy(endPoints, 0, e, 0, used);
            startPoints = s;
            endPoints = e;
        }
        used = n;
    }


    /**
     * Get an iterator over the values
     */

    @Override
    public IntIterator iterator() {
        return new IntRangeSetIterator(this);
    }


    public String toString() {
        StringBuilder sb = new StringBuilder(used * 8);
        for (int i = 0; i < used; i++) {
            sb.append(startPoints[i] + "-" + endPoints[i] + ",");
        }
        return sb.toString();
    }

    /**
     * Test whether this set has exactly the same members as another set. Note that
     * IntRangeSet values are <b>NOT</b> comparable with other implementations of IntSet
     */

    public boolean equals(Object other) {
        if (other instanceof IntSet) {
            if (other instanceof IntRangeSet) {
                return used == ((IntRangeSet) other).used &&
                        Arrays.equals(startPoints, ((IntRangeSet) other).startPoints) &&
                        Arrays.equals(endPoints, ((IntRangeSet) other).endPoints);
            } else {
                return containsAll((IntSet) other);
            }
        } else {
            return false;
        }
    }

    /**
     * Construct a hash key that supports the equals() test
     */

    public int hashCode() {
        // Note, hashcodes are NOT the same as those used by IntHashSet and IntArraySet
        if (_hashCode == -1) {
            int h = 0x436a89f1;
            for (int i = 0; i < used; i++) {
                h ^= startPoints[i] + (endPoints[i] << 3);
            }
            _hashCode = h;
        }
        return _hashCode;
    }

    /**
     * Add a range of integers to the set.
     * This is optimized for the case where these are all greater than any existing integer
     * in the set.
     *
     * @param low  the low end of the new range
     * @param high the high end of the new range
     */

    public void addRange(int low, int high) {
        if (low == high) {
            add(low);
            return;
        }
        _hashCode = -1;
        if (used == 0) {
            ensureCapacity(1);
            startPoints[used - 1] = low;
            endPoints[used - 1] = high;
            count += (high - low + 1);
        } else if (low > endPoints[used - 1]) {
            if (low == endPoints[used - 1] + 1) {
                endPoints[used - 1] = high;
            } else {
                ensureCapacity(used + 1);
                startPoints[used - 1] = low;
                endPoints[used - 1] = high;
            }
            count += (high - low + 1);
        } else if (high < startPoints[0]) {
            ensureCapacity(used + 1);
            System.arraycopy(startPoints, 0, startPoints, 1, used - 1);
            System.arraycopy(endPoints, 0, endPoints, 1, used - 1);
            startPoints[0] = low;
            endPoints[0] = high;
        } else {
            for (int i = 1; i < used; i++) {
                if (startPoints[i] > high && endPoints[i - 1] < low) {
                    ensureCapacity(used + 1);
                    System.arraycopy(startPoints, i, startPoints, i + 1, used - i - 1);
                    System.arraycopy(endPoints, i, endPoints, i + 1, used - i - 1);
                    startPoints[i] = low;
                    endPoints[i] = high;
                    return;
                }
            }
            // otherwise do it the hard way
            for (int i = low; i <= high; i++) {
                add(i);
            }
        }
    }

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
     * Iterator class
     */

    private static class IntRangeSetIterator implements IntIterator {

        private IntRangeSet intRangeSet;
        private int i = 0;
        private int current = 0;

        public IntRangeSetIterator(IntRangeSet intRangeSet) {
            this.intRangeSet = intRangeSet;
            i = -1;
            current = Integer.MIN_VALUE;
        }

        @Override
        public boolean hasNext() {
            if (i < 0) {
                return intRangeSet.count > 0;
            } else {
                return current < intRangeSet.endPoints[intRangeSet.used - 1];
            }
        }

        @Override
        public int next() {
            if (i < 0) {
                i = 0;
                current = intRangeSet.startPoints[0];
                return current;
            }
            if (current == intRangeSet.endPoints[i]) {
                current = intRangeSet.startPoints[++i];
                return current;
            } else {
                return ++current;
            }
        }
    }

}

