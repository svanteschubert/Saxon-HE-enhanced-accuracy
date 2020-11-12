////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2012 Michael Froh.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.trie;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An immutable list implementation that only supports sequential traversal using an iterator,
 * prepending an item to the start, and extraction of the head()/tail() of the list. Unlike
 * {@link net.sf.saxon.ma.parray.ImmList}, it is optimized for sequential access rather than
 * direct access.
 * @param <T> the type of the elements in the list
 */

public abstract class ImmutableList<T> implements Iterable<T> {

    /**
     * Return an empty list
     *
     * @param <T> the nominal item type of the list elements
     * @return an empty list
     */
    public static <T> ImmutableList<T> empty() {
        return EMPTY_LIST;
    }

    /**
     * Get the first item in the list
     * @return the first item in the list
     */
    public abstract T head();

    /**
     * Get all items in the list other than the first
     * @return a list containing all items except the first
     */

    public abstract ImmutableList<T> tail();

    /**
     * Ask whether the list is empty
     * @return true if and only if the list contains no items
     */

    public abstract boolean isEmpty();

    /**
     * Get the size of the list (the number of items). Note that this is an O(n) operation.
     * @return the size of the list.
     */

    public final int size() {
        ImmutableList<T> input = this;
        int size = 0;
        while (!input.isEmpty()) {
            size++;
            input = input.tail();
        }
        return size;
    }

    /**
     * Return a list with a new item added at the start
     * @param element the item to be added at the start
     * @return a new list
     */

    public ImmutableList<T> prepend(T element) {
        return new NonEmptyList<>(element, this);
    }

    /**
     * Test whether two lists are equal
     * @param o the other list
     * @return true if the other object is an instance of this ImmutableList class, and the
     * elements of the two lists are pairwise equal.
     */

    public boolean equals(Object o) {
        if (!(o instanceof ImmutableList)) {
            return false;
        }
        if (o == this) {
            return true;
        }
        Iterator<T> thisIter = this.iterator();
        Iterator<?> otherIter = ((ImmutableList) o).iterator();
        while (thisIter.hasNext() && otherIter.hasNext()) {
            if (!thisIter.next().equals(otherIter.next())) {
                return false;
            }
        }
        return thisIter.hasNext() == otherIter.hasNext();
    }

    /**
     * Get an iterator over the elements of the list
     * @return an iterator over the list
     */

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private ImmutableList<T> list = ImmutableList.this;

            @Override
            public boolean hasNext() {
                return !list.isEmpty();
            }

            @Override
            public T next() {
                T element = list.head();
                list = list.tail();
                return element;
            }
        };
    }

    /**
     * Return a string representation of the list contents
     * @return a string in the form "[item1, item2, ...]"
     */

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (T elem : this) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(elem);
            first = false;
        }
        builder.append(']');
        return builder.toString();
    }

    /**
     * Return a list with the contents of this list in reverse order
     * @return the reversed list
     */

    public ImmutableList<T> reverse() {
        ImmutableList<T> result = empty();
        for (T element : this) {
            result = result.prepend(element);
        }
        return result;
    }

    private static final EmptyList EMPTY_LIST = new EmptyList();

    /**
     * An empty immutable list
     */

    private static class EmptyList extends ImmutableList {
        @Override
        public Object head() {
            throw new NoSuchElementException("head() called on empty list");
        }

        @Override
        public ImmutableList tail() {
            throw new NoSuchElementException("head() called on empty list");
        }

        @Override
        public boolean isEmpty() {
            return true;
        }
    }

    /**
     * A non-empty immutable list, implemented as a one-way linked list
     * @param <T> the type of the items in the list
     */

    private static class NonEmptyList<T> extends ImmutableList<T> {
        private final T element;
        private final ImmutableList<T> tail;

        private NonEmptyList(final T element, final ImmutableList<T> tail) {
            this.element = element;
            this.tail = tail;
        }

        @Override
        public T head() {
            return element;
        }

        @Override
        public ImmutableList<T> tail() {
            return tail;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
