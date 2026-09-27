// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.jiter;

import java.util.Iterator;
import java.util.function.Supplier;

/**
 * Implements an iterable over a {@code Supplier<Iterator<T>>} -- if a class can
 * supply iterators on demand, then it can implement {@code Iterable}
 * @param <T>
 */
public class IterableUsingIteratorSupplier<T> implements Iterable<T> {

    private final Supplier<Iterator<T>> iteratorSupplier;

    /**
     * Create an iterable, whose {@link #iterator()} method will get an iterator
     * from the supplier provided.
     * @param iteratorSupplier the supplier of iterators, to be called whenever a
     *                         new iterator is required
     */
    public IterableUsingIteratorSupplier(Supplier<Iterator<T>> iteratorSupplier) {
        this.iteratorSupplier = iteratorSupplier;
    }

    /**
     * Returns an iterator over elements of type {@code T}.
     * @return an Iterator.
     */
    @Override
    public Iterator<T> iterator() {
        return iteratorSupplier.get();
    }
}

