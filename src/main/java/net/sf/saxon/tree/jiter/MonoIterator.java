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
 * An iterator over a single object (typically a sub-expression of an expression)
 */
public class MonoIterator<T> implements Iterator<T> {

    private T thing;  // the single object in the collection
    private boolean gone;  // true if the single object has already been returned

    /**
     * Create an iterator of the single object supplied
     *
     * @param thing the object to be iterated over
     */

    public MonoIterator(T thing) {
        gone = false;
        this.thing = thing;
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
        return !gone;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration.
     * @throws NoSuchElementException iteration has no more elements.
     */

    @Override
    public T next() {
        if (gone) {
            throw new NoSuchElementException();
        } else {
            gone = true;
            return thing;
        }
    }

}

