////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.tree.iter.LookaheadIterator;

import java.util.function.Consumer;

/**
 * ItemCheckingIterator applies a supplied function to each item in a sequence.
 * The iterator returns the same items as the original, but has side effects (such
 * as throwing an error) determined by the supplied callback function
 */

public class ItemCheckingIterator
        implements SequenceIterator, LookaheadIterator, LastPositionFinder {

    private final SequenceIterator base;
    private final Consumer<Item> action;


    /**
     * Construct an ItemCheckingIterator that will apply a specified Function to
     * each Item returned by the base iterator.
     *
     * @param base   the base iterator
     * @param action the mapping function to be applied.
     */

    public ItemCheckingIterator(SequenceIterator base, Consumer<Item> action) {
        this.base = base;
        this.action = action;
    }

    @Override
    public boolean supportsHasNext() {
        return base instanceof LookaheadIterator && ((LookaheadIterator)base).supportsHasNext();
    }


    @Override
    public boolean hasNext() {
        // Must only be called if this is a lookahead iterator, which will only be true if the base iterator
        // is a lookahead iterator and one-to-one is true
        return ((LookaheadIterator) base).hasNext();
    }

    @Override
    public Item next() {
        Item nextSource = base.next();
        if (nextSource == null) {
            return null;
        }
        // Call the supplied checking function
        action.accept(nextSource);
        return nextSource;
    }

    @Override
    public void close() {
        base.close();
    }

    @Override
    public boolean supportsGetLength() {
        return SequenceTool.supportsGetLength(base);
    }

    @Override
    public int getLength() {
        return SequenceTool.getLength(base);
    }

}

