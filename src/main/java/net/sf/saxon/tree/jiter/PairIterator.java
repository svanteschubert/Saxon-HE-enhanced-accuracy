////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.jiter;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An iterator over a pair of objects (typically sub-expressions of an expression)
 */
public class PairIterator<T> implements Iterator<T> {

    private T one;
    private T two;
    private int pos = 0;

    /**
     * Create an iterator over two objects
     *
     * @param one the first object to be returned
     * @param two the second object to be returned
     */

    public PairIterator(T one, T two) {
        this.one = one;
        this.two = two;
    }

    /**
     * Returns <tt>true</tt> if the iteration has more elements. (In other
     * words, returns <tt>true</tt> if <tt>next</tt> would return an element
     * rather than throwing an exception.)
     *
     * @return <tt>true</tt> if the iterator has more elements.
     */

    @Override
    public boolean hasNext() {
        return pos < 2;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration.
     * @throws NoSuchElementException iteration has no more elements.
     */
    @Override
    public T next() {
        switch (pos++) {
            case 0:
                return one;
            case 1:
                return two;
            default:
                throw new NoSuchElementException();
        }
    }

}

