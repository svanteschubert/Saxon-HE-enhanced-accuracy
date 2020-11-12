////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;

/**
 * A set of integers represented as int values
 */
public interface IntSet {

    /**
     * Create a copy of this IntSet that leaves the original unchanged.
     *
     * @return an IntSet containing the same integers. The result will not necessarily be the
     *         same class as the original. It will either be an immutable object, or a newly constructed
     *         object.
     */

    IntSet copy();

    /**
     * Create a copy of this IntSet that contains the same set of integers.
     *
     * @return an IntSet containing the same integers. The result will not necessarily be the
     *         same class as the original. It will always be a mutable object
     */

    IntSet mutableCopy();

    /**
     * Ask whether the set permits in-situ modifications using add() and remove()
     */

    default boolean isMutable() {
        return true;
    }

    /**
     * Clear the contents of the IntSet (making it an empty set)
     */
    void clear();

    /**
     * Get the number of integers in the set
     *
     * @return the size of the set
     */

    int size();

    /**
     * Determine if the set is empty
     *
     * @return true if the set is empty, false if not
     */

    boolean isEmpty();

    /**
     * Determine whether a particular integer is present in the set
     *
     * @param value the integer under test
     * @return true if value is present in the set, false if not
     */

    boolean contains(int value);

    /**
     * Remove an integer from the set
     *
     * @param value the integer to be removed
     * @return true if the integer was present in the set, false if it was not present
     */

    boolean remove(int value);

    /**
     * Add an integer to the set
     *
     * @param value the integer to be added
     * @return true if the integer was added, false if it was already present
     */

    boolean add(int value);

    /**
     * Get an iterator over the values
     *
     * @return an iterator over the integers in the set
     */

    IntIterator iterator();

    /**
     * Test if this set is a superset of another set
     *
     * @param other the other set
     * @return true if every item in the other set is also in this set
     */

    default boolean containsAll(IntSet other) {
        if (other == IntUniversalSet.getInstance() || (other instanceof IntComplementSet)) {
            return false;
        }
        IntIterator it = other.iterator();
        while (it.hasNext()) {
            if (!contains(it.next())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Form a new set that is the union of two IntSets.
     *
     * @param other the second set
     * @return the union of the two sets
     */

    default IntSet union(IntSet other) {
        if (other == IntUniversalSet.getInstance()) {
            return other;
        }
        if (this.isEmpty()) {
            return other.copy();
        }
        if (other.isEmpty()) {
            return this.copy();
        }
        if (other instanceof IntComplementSet) {
            return other.union(this);
        }
        IntHashSet n = new IntHashSet(this.size() + other.size());
        IntIterator it = iterator();
        while (it.hasNext()) {
            n.add(it.next());
        }
        it = other.iterator();
        while (it.hasNext()) {
            n.add(it.next());
        }
        return n;
    }

    /**
     * Form a new set that is the intersection of two IntSets.
     *
     * @param other the second set
     * @return the intersection of the two sets
     */

    default IntSet intersect(IntSet other) {
        if (this.isEmpty() || other.isEmpty()) {
            return IntEmptySet.getInstance();
        }
        IntHashSet n = new IntHashSet(size());
        IntIterator it = iterator();
        while (it.hasNext()) {
            int v = it.next();
            if (other.contains(v)) {
                n.add(v);
            }
        }
        return n;
    }

    /**
     * Form a new set that is the difference of this set and another set.
     * The result will either be an immutable object, or a newly constructed object.
     *
     * @param other the second set
     * @return the intersection of the two sets
     */


    default IntSet except(IntSet other) {
        IntHashSet n = new IntHashSet(size());
        IntIterator it = iterator();
        while (it.hasNext()) {
            int v = it.next();
            if (!other.contains(v)) {
                n.add(v);
            }
        }
        return n;
    }

}
