////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;


import net.sf.saxon.tree.util.FastStringBuffer;

import java.util.Arrays;

/**
 * Set of int values. This implementation requires that new entries are added in monotonically
 * increasing order; any attempt to add a value out of sequence, or to remove a value, results
 * is an UnsupportedOperationException
 */

public class MonotonicIntSet implements IntSet {

    /**
     * The array of integers, which will always be sorted
     */

    private int[] contents;

    /**
     * The portion of the array currently in use
     */

    private int used = 0;

    /**
     * Create an empty set
     */
    public MonotonicIntSet() {
        contents = new int[4];
        used = 0;
    }

    @Override
    public IntSet copy() {
        MonotonicIntSet i2 = new MonotonicIntSet();
        i2.contents = Arrays.copyOf(contents, used);
        i2.used = used;
        return i2;
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
    public void clear() {
        if (contents.length > used + 20) {
            contents = new int[4];
        }
        used = 0;
    }

    @Override
    public int size() {
        return used;
    }

    @Override
    public boolean isEmpty() {
        return used == 0;
    }

    @Override
    public boolean contains(int value) {
        return Arrays.binarySearch(contents, 0, used, value) >= 0;
    }

    @Override
    public boolean remove(int value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Add an integer to the set
     *
     * @param value the integer to be added (which must be greater than or equal to the
     *              largest integer currently in the set)
     * @return true if the integer was added, false if it was already present
     * @throws UnsupportedOperationException if the set already contains an integer larger
     * than the supplied value
     */

    @Override
    public boolean add(int value) {
        if (used > 0) {
            int last = contents[used - 1];
            if (value == last) {
                return false;
            } else if (value < last) {
                throw new UnsupportedOperationException("Values must be added in monotonic order");
            }
        }
        if (used == contents.length) {
            contents = Arrays.copyOf(contents, used==0 ? 4 : used*2);
        }
        contents[used++] = value;
        return true;
    }

    /**
     * Get an iterator over the values
     *
     * @return an iterator over the values, which will be delivered in sorted order
     */

    @Override
    public IntIterator iterator() {
        return new IntArraySet.IntArrayIterator(contents, used);
    }

    /**
     * Form a new set that is the union of this set with another set.
     *
     * @param other the other set
     * @return the union of the two sets
     */

    @Override
    public IntSet union(IntSet other) {
        // Look for special cases: one set empty, or both sets equal
        if (size() == 0) {
            return other.copy();
        } else if (other.isEmpty()) {
            return copy();
        } else if (other == IntUniversalSet.getInstance()) {
            return other;
        } else if (other instanceof IntComplementSet) {
            return other.union(this);
        }
        if (equals(other)) {
            return copy();
        }
        if (other instanceof MonotonicIntSet) {
            // Form the union by a merge of the two sorted arrays
            int[] merged = new int[size() + other.size()];
            int[] a = contents;
            int[] b = ((MonotonicIntSet) other).contents;
            int m = used;
            int n = ((MonotonicIntSet) other).used;
            int o = 0, i = 0, j = 0;
            while (true) {
                if (a[i] < b[j]) {
                    merged[o++] = a[i++];
                } else if (b[j] < a[i]) {
                    merged[o++] = b[j++];
                } else {
                    merged[o++] = a[i++];
                    j++;
                }
                if (i == m) {
                    System.arraycopy(b, j, merged, o, n - j);
                    o += (n - j);
                    return make(merged, o);
                } else if (j == n) {
                    System.arraycopy(a, i, merged, o, m - i);
                    o += (m - i);
                    return make(merged, o);
                }
            }
        } else {
            return IntSet.super.union(other);
        }
    }

    /**
     * Factory method to construct a set from an array of integers
     *
     * @param in   the array of integers, which must be in ascending order
     * @param size the number of elements in the array that are significant
     * @return the constructed set
     */

    public static MonotonicIntSet make(int[] in, int size) {
        return new MonotonicIntSet(in, size);
    }


    private MonotonicIntSet(int[] content, int used) {
        this.contents = content;
        this.used = used;
    }

    public String toString() {
        FastStringBuffer sb = new FastStringBuffer(used * 4);
        for (int i = 0; i < used; i++) {
            if (i == used - 1) {
                sb.append(contents[i] + "");
            } else if (contents[i] + 1 != contents[i + 1]) {
                sb.append(contents[i] + ",");
            } else {
                int j = i + 1;
                while (contents[j] == contents[j - 1] + 1) {
                    j++;
                    if (j == used) {
                        break;
                    }
                }
                sb.append(contents[i] + "-" + contents[j - 1] + ",");
                i = j;
            }
        }
        return sb.toString();
    }

    /**
     * Test whether this set has exactly the same members as another set
     */

    public boolean equals(Object other) {
        if (other instanceof MonotonicIntSet) {
            MonotonicIntSet s = (MonotonicIntSet) other;
            if (used != s.used) {
                return false;
            }
            for (int i=0; i<used; i++) {
                if (contents[i] != s.contents[i]) {
                    return false;
                }
            }
            return true;
        } else
            return other instanceof IntSet &&
                    used == ((IntSet) other).size() &&
                    containsAll((IntSet) other);
    }

    /**
     * Construct a hash key that supports the equals() test
     */

    public int hashCode() {
        int h = 936247625;
        IntIterator it = iterator();
        while (it.hasNext()) {
            h += it.next();
        }
        return h;
    }


}

