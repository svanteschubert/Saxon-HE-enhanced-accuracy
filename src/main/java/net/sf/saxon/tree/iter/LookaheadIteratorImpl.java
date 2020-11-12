////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

import java.util.EnumSet;

/**
 * This class wraps any sequence iterator, turning it into a lookahead iterator,
 * by looking ahead one item
 */
public class LookaheadIteratorImpl implements LookaheadIterator {

    private SequenceIterator base;
    /*@Nullable*/ private Item next;

    private LookaheadIteratorImpl(/*@NotNull*/ SequenceIterator base) throws XPathException {
        this.base = base;
        next = base.next();
    }

    /*@NotNull*/
    public static LookaheadIterator makeLookaheadIterator(/*@NotNull*/ SequenceIterator base) throws XPathException {
        if (base.getProperties().contains(Property.LOOKAHEAD)) {
            return (LookaheadIterator) base;
        } else {
            return new LookaheadIteratorImpl(base);
        }
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    /*@Nullable*/
    @Override
    public Item next() throws XPathException {
        Item current = next;
        if (next != null) {
            next = base.next();
        }
        return current;
    }

    @Override
    public void close() {
        base.close();
    }

    @Override
    public EnumSet<Property> getProperties() {
        return EnumSet.of(Property.LOOKAHEAD);
    }
}

