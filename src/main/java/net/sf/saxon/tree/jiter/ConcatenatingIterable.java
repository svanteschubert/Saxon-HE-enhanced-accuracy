////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.jiter;

import java.util.Iterator;

/**
 * An iterable over arbitrary objects, that concatenates the objects returned by two supplied iterables.
 */

public class ConcatenatingIterable<E> implements Iterable<E> {

    Iterable<? extends E> first;
    Iterable<? extends E> second;

    /**
     * Create an iterable that concatenates the results of two supplied iterables.
     * @param first the first iterable
     * @param second the second iterable
     */

    public ConcatenatingIterable(Iterable<? extends E> first, Iterable<? extends E> second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public Iterator<E> iterator() {
        return new ConcatenatingIterator<>(first.iterator(), () -> second.iterator());
    }
}

