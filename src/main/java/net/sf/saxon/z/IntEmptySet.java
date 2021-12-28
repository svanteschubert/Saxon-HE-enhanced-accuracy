////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;

/**
 * An immutable integer set containing no integers
 */
public class IntEmptySet implements IntSet {

    private static IntEmptySet THE_INSTANCE = new IntEmptySet();

    public static IntEmptySet getInstance() {
        return THE_INSTANCE;
    }


    private IntEmptySet() {
        // no action
    }

    @Override
    public IntSet copy() {
        return this;
    }

    @Override
    public IntSet mutableCopy() {
        return new IntHashSet();
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("IntEmptySet is immutable");
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean contains(int value) {
        return false;
    }

    @Override
    public boolean remove(int value) {
        throw new UnsupportedOperationException("IntEmptySet is immutable");
    }

    @Override
    public boolean add(int value) {
        throw new UnsupportedOperationException("IntEmptySet is immutable");
    }

    @Override
    public IntIterator iterator() {
        return new IntIterator() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public int next() {
                return Integer.MIN_VALUE;
            }
        };
    }

    @Override
    public IntSet union(IntSet other) {
        return other.copy();
    }

    @Override
    public IntSet intersect(IntSet other) {
        return this;
    }

    @Override
    public IntSet except(IntSet other) {
        return this;
    }

    @Override
    public boolean containsAll(/*@NotNull*/ IntSet other) {
        return other.isEmpty();
    }
}



