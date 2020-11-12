////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;

/**
 * An immutable integer set containing a single integer
 */
public class IntSingletonSet implements IntSet {

    private int value;

    public IntSingletonSet(int value) {
        this.value = value;
    }

    public int getMember() {
        return value;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("IntSingletonSet is immutable");
    }

    @Override
    public IntSet copy() {
        return this;
    }

    @Override
    public IntSet mutableCopy() {
        IntHashSet intHashSet = new IntHashSet();
        intHashSet.add(value);
        return intHashSet;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean contains(int value) {
        return this.value == value;
    }

    @Override
    public boolean remove(int value) {
        throw new UnsupportedOperationException("IntSingletonSet is immutable");
    }

    @Override
    public boolean add(int value) {
        throw new UnsupportedOperationException("IntSingletonSet is immutable");
    }

    @Override
    public IntIterator iterator() {
        return new IntSingletonIterator(value);
    }

    @Override
    public IntSet union(IntSet other) {
        IntSet n = other.mutableCopy();
        n.add(value);
        return n;
    }

    @Override
    public IntSet intersect(IntSet other) {
        if (other.contains(value)) {
            return this;
        } else {
            return IntEmptySet.getInstance();
        }
    }

    @Override
    public IntSet except(IntSet other) {
        if (other.contains(value)) {
            return IntEmptySet.getInstance();
        } else {
            return this;
        }
    }

    @Override
    public boolean containsAll(/*@NotNull*/ IntSet other) {
        if (other.size() > 1) {
            return false;
        }
        IntIterator ii = other.iterator();
        while (ii.hasNext()) {
            if (value != ii.next()) {
                return false;
            }
        }
        return true;
    }
}

