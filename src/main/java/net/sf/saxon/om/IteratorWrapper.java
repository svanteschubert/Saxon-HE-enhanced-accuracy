////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.transpile.CSharpSuppressWarnings;

import java.util.Iterator;

/**
 * Class IteratorWrapper - provides a SequenceIterator over a Java Iterator.
 */
public class IteratorWrapper implements SequenceIterator {

    Iterator<? extends Item> iterator;

    /**
     * Create a IteratorWrapper backed by an iterator
     *
     * @param iterator the iterator that delivers the items in the sequence
     */

    public IteratorWrapper(Iterator<? extends Item> iterator) {
        this.iterator = iterator;
    }


    /**
     * Get the next item in the Iterator
     *
     * @return the next item in the iterator, or null if there are no more items. Once a call
     * on next() has returned null, no further calls should be made.
     */
    @Override
    @CSharpSuppressWarnings("UnsafeIteratorConversion")
    public Item next() {
        return iterator.hasNext() ? iterator.next() : null;
    }
}
