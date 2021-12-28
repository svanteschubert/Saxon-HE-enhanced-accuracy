////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;

/**
 * An immutable integer set containing all int values except those in an excluded set
 */
public class IntComplementSet implements IntSet {

    private IntSet exclusions;

    public IntComplementSet(IntSet exclusions) {
        this.exclusions = exclusions.copy();
    }

    public IntSet getExclusions() {
        return exclusions;
    }

    @Override
    public IntSet copy() {
        return new IntComplementSet(exclusions.copy());
    }

    @Override
    public IntSet mutableCopy() {
        return copy();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("IntComplementSet cannot be emptied");
    }

    @Override
    public int size() {
        return Integer.MAX_VALUE - exclusions.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(int value) {
        return !exclusions.contains(value);
    }

    @Override
    public boolean remove(int value) {
        boolean b = contains(value);
        if (b) {
            exclusions.add(value);
        }
        return b;
    }

    @Override
    public boolean add(int value) {
        boolean b = contains(value);
        if (!b) {
            exclusions.remove(value);
        }
        return b;
    }

    @Override
    public IntIterator iterator() {
        throw new UnsupportedOperationException("Cannot enumerate an infinite set");
    }

    @Override
    public IntSet union(IntSet other) {
        return new IntComplementSet(exclusions.except(other));
    }

    @Override
    public IntSet intersect(IntSet other) {
        if (other.isEmpty()) {
            return IntEmptySet.getInstance();
        } else if (other == IntUniversalSet.getInstance()) {
            return copy();
        } else if (other instanceof IntComplementSet) {
            return new IntComplementSet(exclusions.union(((IntComplementSet) other).exclusions));
        } else {
            return other.intersect(this);
        }
    }

    @Override
    public IntSet except(IntSet other) {
        return new IntComplementSet(exclusions.union(other));
    }

    @Override
    public boolean containsAll(/*@NotNull*/ IntSet other) {
        if (other instanceof IntComplementSet) {
            return ((IntComplementSet) other).exclusions.containsAll(exclusions);
        } else if (other instanceof IntUniversalSet) {
            return (!exclusions.isEmpty());
        } else {
            IntIterator ii = other.iterator();
            while (ii.hasNext()) {
                if (exclusions.contains(ii.next())) {
                    return false;
                }
            }
            return true;
        }
    }
}
