////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.jiter;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Wraps a SequenceIterator in a Java iterable, allowing the convenience of
 * processing the items in a Java for-each loop. The iterable can only be used
 * once: it implements both {@code Iterable<T>} and {@code Iterator<T>}, and
 * throws an exception if the {@code iterator()} method is called more than once.
 *
 * @param <T> The type of items returned. It is the caller's responsibility to
 *           ensure that the supplied SequenceIterator returns items of the correct type.
 */


public class IterableSequenceIterator<T extends Item> implements Iterable<T>, Iterator<T> {

    private final SequenceIterator input;
    private boolean used = false;
    private T nextItem;

    /**
     * Create a wrapping iterator
     * @param in the input iterator
     */

    public IterableSequenceIterator(SequenceIterator in) {
        input = in;
    }

    /**
     * Returns an iterator over elements of type {@code T}.
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<T> iterator() {
        if (used) {
            throw new IllegalStateException("This iterable can only be used once");
        }
        used = true;
        advance();
        return this;
    }


    private void advance() {
        //noinspection unchecked
        nextItem = (T)input.next();
    }

    /**
     * Returns {@code true} if the iteration has more elements.
     * @return {@code true} if the iteration has more elements
     */
    @Override
    public boolean hasNext() {
        return nextItem != null;
    }

    /**
     * Returns the next element in the iteration.
     * @return the next element in the iteration
     */
    @Override
    public T next() {
        if (nextItem == null) {
            throw new NoSuchElementException();
        }
        T temp = nextItem;
        advance();
        return temp;
    }
}

