////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.transpile.CSharpSuppressWarnings;

import java.util.Iterator;
import java.util.function.BiFunction;

/**
 * Defines a SequenceIterator that delivers the items returned by an underlying
 * {@link java.util.Iterator}, modified by applying a mapping function
 * @param <J> the type of items returned by the Java iterator
 */
public class SequenceIteratorOverJavaIterator<J> implements SequenceIterator {

    // See also class IteratorWrapper, which does the same thing without mapping,
    // and WrappingJavaIterator, which does the converse.

    private final Iterator<J> javaIterator;
    private final BiFunction<J, Integer, Item> mapper;
    private int position;

    /**
     * Create a mapping iterator
     * @param javaIterator the underlying Java iterator
     * @param mapper a function to process each item. It takes two arguments, the item returned
     *               by the Java iterator, and a zero-based position incremented for each item.
     */
    public SequenceIteratorOverJavaIterator(Iterator<J> javaIterator, BiFunction<J, Integer, Item> mapper) {
        this.javaIterator = javaIterator;
        this.mapper = mapper;
        this.position = 0;
    }

    @Override
    @CSharpSuppressWarnings("UnsafeIteratorConversion")
    public Item next() {
        if (javaIterator.hasNext()) {
            return mapper.apply(javaIterator.next(), position++);
        } else {
            return null;
        }
    }
}


