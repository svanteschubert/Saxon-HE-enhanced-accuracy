////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;

/**
 * An immutable integer set containing every integer
 */
public class IntUniversalSet implements IntSet {

    private static IntUniversalSet THE_INSTANCE = new IntUniversalSet();

    public static IntUniversalSet getInstance() {
        return THE_INSTANCE;
    }


    private IntUniversalSet() {
        // no action
    }

    @Override
    public IntSet copy() {
        return this;
    }

    @Override
    public IntSet mutableCopy() {
        return new IntComplementSet(new IntHashSet());
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("IntUniversalSet is immutable");
    }

    @Override
    public int size() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean contains(int value) {
        return true;
    }

    @Override
    public boolean remove(int value) {
        throw new UnsupportedOperationException("IntUniversalSet is immutable");
    }

    @Override
    public boolean add(int value) {
        throw new UnsupportedOperationException("IntUniversalSet is immutable");
    }

    @Override
    public IntIterator iterator() {
        throw new UnsupportedOperationException("Cannot enumerate an infinite set");
    }

    @Override
    public IntSet union(IntSet other) {
        return this;
    }

    @Override
    public IntSet intersect(IntSet other) {
        return other.copy();
    }

    @Override
    public IntSet except(IntSet other) {
        if (other instanceof IntUniversalSet) {
            return IntEmptySet.getInstance();
        } else {
            return new IntComplementSet(other.copy());
        }
    }

    @Override
    public boolean containsAll(/*@NotNull*/ IntSet other) {
        return true;
    }
}


