////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.jiter;

import java.util.Iterator;

/**
 * A Java Iterator which applies a mapping function to each item in an input sequence
 * @param <S> the type of the input items
 * @param <T> the type of the delivered item
 */


public class MappingJavaIterator<S, T> implements Iterator<T> {

    private Iterator<S> input;
    private java.util.function.Function<S, T> mapper;

    /**
     * Create a mapping iterator
     * @param in the input sequence
     * @param mapper the mapping function to be applied to each item in the ipnut sequence to
     *               generate the corresponding item in the result sequence
     */

    public MappingJavaIterator(Iterator<S> in, java.util.function.Function<S, T> mapper) {
        this.input = in;
        this.mapper = mapper;
    }

    @Override
    public boolean hasNext() {
        return input.hasNext();
    }

    @Override
    public T next() {
        while (true) {
            T next = mapper.apply(input.next());
            if (next != null) {
                return next;
            }
        }
    }

    @Override
    public void remove() {
        input.remove();
    }

}

