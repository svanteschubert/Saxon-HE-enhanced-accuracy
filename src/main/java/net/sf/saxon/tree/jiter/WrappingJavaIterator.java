////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.jiter;

import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.tree.iter.LookaheadIteratorImpl;
import net.sf.saxon.om.SequenceIterator;

import java.util.Iterator;

/**
 * A Java Iterator which wraps a supplied unfailing SequenceIterator
 * @param <T> the type of the delivered items
 */


public class WrappingJavaIterator<T extends Item> implements Iterator<T> {

    private final LookaheadIterator input;

    /**
     * Create a wrapping iterator
     * @param in the input iterator
     */

    public WrappingJavaIterator(SequenceIterator in) {
        try {
            this.input = LookaheadIteratorImpl.makeLookaheadIterator(in);
        } catch (XPathException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public boolean hasNext() {
        return input.hasNext();
    }

    @Override
    public T next() {
        return (T)input.next();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

}

