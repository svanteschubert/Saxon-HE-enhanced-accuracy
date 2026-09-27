////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.Iterator;

/**
 * This class replicates functionality that is available in a Java Stack, but not
 * in a C# Stack, for example the ability to access elements by direct index
 * (0-based, counting from the bottom of the stack). It is used in place of
 * {@link java.util.Stack} in the few places where this functionality is
 * needed.
 *
 * <p>The stack is iterable, and iteration is in bottom-to-top order (like Java,
 * but unlike a C# stack where iteration is top-to-bottom)</p>
 */

public class IndexedStack<T> implements Iterable<T>{

    private final ArrayList<T> items;

    /**
     * Create an empty stack with a default initial space allocation
     */

    public IndexedStack() {
        items = new ArrayList<>(20);
    }

    /**
     * Create an empty stack with a specified initial space allocation
     * @param size the number of entries to be allocated
     */

    public IndexedStack(int size) {
        items = new ArrayList<>(size);
    }

    /**
     * Get the current height of the stack
     * @return the number of items on the stack
     */

    public int size() {
        return items.size();
    }

    /**
     * Ask if the stack is empty
     * @return true if the stack contains no items
     */

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Add an item to the top of the stack
     * @param item the item to be added
     */

    public void push(T item) {
        items.add(item);
    }

    /**
     * Get the item on the top of the stack, without changing the state of the stack
     * @return the topmost item
     * @throws EmptyStackException if the stack is empty
     */

    public T peek() {
        if (items.isEmpty()) {
            throw new EmptyStackException();
        } else {
            return items.get(items.size()-1);
        }
    }

    /**
     * Get the item on the top of the stack, and remove it from the stack
     *
     * @return the topmost item
     * @throws EmptyStackException if the stack is empty
     */

    public T pop() {
        if (items.isEmpty()) {
            throw new EmptyStackException();
        } else {
            return items.remove(items.size() - 1);
        }
    }

    /**
     * Get the item at position N in the stack, without changing the state of the stack
     * @param i the position of the required item, where the first item counting from
     *          the bottom of the stack is position 0 (zero)
     * @return the item at the specified position
     * @throws IndexOutOfBoundsException if {@code i} is negative, or greater than or equal to the stack size
     */

    public T get(int i) {
        return items.get(i);
    }

    /**
     * Overwrite the item at position N in the stack
     *
     * @param i the position of the required item, where the first item counting from
     *          the bottom of the stack is position 0 (zero)
     * @param value the item to be put at the specified position
     * @throws IndexOutOfBoundsException if {@code i} is negative, or greater than or equal to the stack size
     */

    public void set(int i, T value) {
        items.set(i, value);
    }

    /**
     * Search for an item on the stack
     *
     * @param value the item being sought
     * @return true if the stack contains an item equal to the sought item
     */

    public boolean contains(T value) {
        return items.contains(value);
    }

    /**
     * Search for an item on the stack, starting from the bottom
     * @param value the item being sought
     * @return the index position of the first item found that is equal to {@code value},
     * or -1 if no such item is found
     */

    public int indexOf(T value) {
        return items.indexOf(value);
    }

    /**
     * Iterate the stack in bottom to top order
     * @return an iterator that starts at the bottom of the stack and proceeds to the top
     */

    public Iterator<T> iterator() {
        return items.iterator();
    }




}


