////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * This class wraps any sequence iterator, turning it into a lookahead iterator,
 * by looking ahead one item
 */
public class LookaheadIteratorImpl implements LookaheadIterator {

    private final SequenceIterator base;
    /*@Nullable*/ private Item _next;

    private LookaheadIteratorImpl(/*@NotNull*/ SequenceIterator base) throws XPathException {
        this.base = base;
        _next = base.next();
    }

    /*@NotNull*/
    public static LookaheadIterator makeLookaheadIterator(/*@NotNull*/ SequenceIterator base) throws XPathException {
        if (base instanceof LookaheadIterator && ((LookaheadIterator)base).supportsHasNext()) {
            return (LookaheadIterator) base;
        } else {
            return new LookaheadIteratorImpl(base);
        }
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }


    @Override
    public boolean hasNext() {
        return _next != null;
    }

    /*@Nullable*/
    @Override
    public Item next() {
        Item current = _next;
        if (_next != null) {
            _next = base.next();
        }
        return current;
    }

    @Override
    public void close() {
        base.close();
    }

}

