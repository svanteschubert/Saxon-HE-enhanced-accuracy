////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.jiter;

import java.util.Iterator;
import java.util.function.Supplier;

/**
 * An iterator over nodes, that concatenates the nodes returned by two supplied iterators.
 */

public class ConcatenatingIterator<E> implements Iterator<E> {

    Iterator<? extends E> first;
    Supplier<Iterator<? extends E>> second;
    Iterator<? extends E> active;

    /**
     * Create an iterator that concatenates the results of two supplied iterator. The
     * second iterator isn't created until it is actually needed.
     * @param first the first iterator
     * @param second a function that can be called to supply the second iterator
     */

    public ConcatenatingIterator(Iterator<? extends E> first, Supplier<Iterator<? extends E>> second) {
        this.first = first;
        this.second = second;
        this.active = first;
    }

    @Override
    public boolean hasNext() {
        if (active.hasNext()) {
            return true;
        } else if (active == first) {
            first = null;
            active = second.get();
            return active.hasNext();
        } else {
            return false;
        }
    }

    /**
     * Get the next item in the sequence.
     * @return the next Item. If there are no more items, return null.
     */

    /*@Nullable*/
    @Override
    public E next() {
        return active.next();
    }

}

