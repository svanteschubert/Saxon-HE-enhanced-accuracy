////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.parray;

import java.util.List;

/**
 * An immutable list of elements
 * @param <E> the type of the elements in the list
 */

public abstract class ImmList<E> implements Iterable<E> {

    /**
     * Return an empty list
     * @param <E> the (nominal) type of the list element
     * @return an empty immutable list
     */

    public static <E> ImmList<E> empty() {
        return (ImmList<E>)ImmList0.INSTANCE;
    }

    /**
     * Return a singleton list (a list containing one item)
     * @param member the single member of the list
     * @param <E> the type of the list members
     * @return the singleton list
     */

    public static <E> ImmList<E> singleton(E member) {
        return new ImmList1<>(member);
    }

    /**
     * Return a list of length 2 (two)
     *
     * @param first the first member of the list
     * @param second the second member of the list
     * @param <E>    the type of the list members
     * @return the two-member list
     */

    public static <E> ImmList<E> pair(E first, E second) {
        return new ImmList2<>(new ImmList1<>(first), new ImmList1<>(second));
    }

    /**
     * Construct an immutable list from a Java list of members
     * @param members the members to be added to the list
     * @param <E> the type of the list members
     * @return the immutable list
     */
    public static <E> ImmList<E> fromList(List<E> members) {
        int size = members.size();
        if (size == 0) {
            return empty();
        } else if (size == 1) {
            return singleton(members.get(0));
        } else {
            int split = size/2;
            List<E> left = members.subList(0, split);
            List<E> right = members.subList(split, size);
            return new ImmList2<>(fromList(left), fromList(right));
        }
    }

    /**
     * Get the element at a given index
     * @param index the required index (zero-based)
     * @return the element at the given index
     * @throws IndexOutOfBoundsException if the index is out of range
     */

    abstract public E get(int index);

    /**
     * Get the first element in the list
     * @return the result of get(0)
     * @throws IndexOutOfBoundsException if the list is empty
     */

    public E head() {
        return get(0);
    }

    /**
     * Get the size of the list
     * @return the number of members in the list
     */

    abstract public int size();

    /**
     * Ask if the list is empty
     * @return true if the list contains no elements, otherwise false
     */

    abstract public boolean isEmpty();

    /**
     * Replace the element at a given index
     *
     * @param index the index (zero-based) of the element to be replaced
     * @param member the replacement member to be included in the new list
     * @return a new list, identical to the old except for the replacement of one member
     * @throws IndexOutOfBoundsException if the index is out of range
     */

    abstract public ImmList<E> replace(int index, E member);

    /**
     * Insert an element at a given position
     *
     * @param index  the position (zero-based) for the insertion. The new element will
     *               be inserted before the existing element at this position. If the index
     *               is equal to the list size, the new element is inserted at the end.
     * @param member the new member to be included in the new list
     * @return a new list, identical to the old except for the addition of one member
     * @throws IndexOutOfBoundsException if the index is out of range
     */

    abstract public ImmList<E> insert(int index, E member);

    /**
     * Append an element at the end of the list
     * @param member the new member to be included in the new list
     * @return a new list, identical to the old except for the addition of one member
     */

    abstract public ImmList<E> append(E member);

    /**
     * Append multiple elements at the end of the list
     *
     * @param members the new members to be included in the new list
     * @return a new list, identical to the old except for the addition of new members
     */

    abstract public ImmList<E> appendList(ImmList<E> members);

    /**
     * Remove the member at a given position
     * @param index the zero-based index position of the member to be removed
     * @return a new list, identical to the old except for the removal of one member
     * @throws IndexOutOfBoundsException if the index is out of range
     */

    abstract public ImmList<E> remove(int index);

    /**
     * Return a sub-sequence with a given start and end position
     * @param start the zero-based index position of the first member to be extracted
     * @param end the zero-based index position of the first member after the sub-sequence
     *            to be extracted
     * @return a new list containing the elements from the specified range of positions
     * @throws IndexOutOfBoundsException if either index is out of range or if end precedes start
     */

    abstract public ImmList<E> subList(int start, int end);

    /**
     * Get a list containing all elements of the list except the first
     * @return the result of {@code subList(1, size())}, or equivalently {@code remove(0)}
     * @throws IndexOutOfBoundsException if the list is empty
     */

    public ImmList<E> tail() {
        return remove(0);
    }

    /**
     * Return a list containing the same elements as this list, but optimized for efficient access
     * @return either this list, or a copy containing the same elements in the same order
     */

    protected ImmList<E> rebalance() {
        return this;
    }

    /**
     * Convenience method for use by subclasses to throw an IndexOutOfBounds exception when needed
     * @param requested the index value that was requested by the caller
     * @param actual the actual size of the list
     * @return an {@code IndexOutOfBoundsException} with suitable message text
     */

    protected IndexOutOfBoundsException outOfBounds(int requested, int actual) {
        return new IndexOutOfBoundsException("Requested " + requested + ", actual size " + actual);
    }

}

