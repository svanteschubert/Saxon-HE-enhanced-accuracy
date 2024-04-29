////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
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

    private final Iterator<S> input;
    private final java.util.function.Function<S, T> mapper;
    private T nextItem;

    /**
     * Create a mapping iterator
     * @param in the input sequence
     * @param mapper the mapping function to be applied to each item in the input sequence to
     *               generate the corresponding item in the result sequence. If the mapping
     *               function returns null for a particular input, the item is omitted from the
     *               result sequence.
     */

    public MappingJavaIterator(Iterator<S> in, java.util.function.Function<S, T> mapper) {
        this.input = in;
        this.mapper = mapper;
        advance();
    }

    @Override
    public boolean hasNext() {
        return nextItem != null;
    }

    @Override
    public T next() {
        T result = nextItem;
        if (result != null) {
            advance();
        }
        return result;
    }

    private void advance() {
        while (input.hasNext()) {
            nextItem = mapper.apply(input.next());
            if (nextItem != null) {
                return;
            }
        }
        nextItem = null;
    }
    //C# emulation of java.util.Iterator omits remove() method
    @Override
    public void remove() {
        input.remove();
    }
}

