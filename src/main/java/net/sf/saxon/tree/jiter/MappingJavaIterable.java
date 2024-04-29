////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.jiter;

import java.util.Iterator;

/**
 * A Java Iterable which applies a mapping function to each item in a supplied Iterable
 * @param <S> the type of the input items
 * @param <T> the type of the delivered item
 */


public class MappingJavaIterable<S, T> implements Iterable<T> {

    private final Iterable<S> input;
    private final java.util.function.Function<S, T> mapper;

    /**
     * Create a mapping iterable
     * @param in the input sequence
     * @param mapper the mapping function to be applied to each item in the input sequence to
     *               generate the corresponding item in the result sequence. If the mapping
     *               function returns null for a particular input, the item is omitted from the
     *               result sequence.
     */

    public MappingJavaIterable(Iterable<S> in, java.util.function.Function<S, T> mapper) {
        this.input = in;
        this.mapper = mapper;
    }

    /**
     * Returns an iterator over elements of type {@code T}.
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<T> iterator() {
        return new MappingJavaIterator<S, T>(input.iterator(), mapper);
    }
}

