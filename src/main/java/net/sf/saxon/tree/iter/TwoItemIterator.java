////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceExtent;

/**
 * An iterator over a pair of objects (typically sub-expressions of an expression)
 */
public class TwoItemIterator implements SequenceIterator, LookaheadIterator, GroundedIterator, LastPositionFinder {

    private final Item one;
    private final Item two;
    private int pos = 0;

    /**
     * Create an iterator over two objects
     *
     * @param one the first object to be returned
     * @param two the second object to be returned
     */

    public TwoItemIterator(Item one, Item two) {
        this.one = one;
        this.two = two;
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    /**
     * Returns <code>true</code> if the iteration has more elements. (In other
     * words, returns <code>true</code> if <code>next</code> would return an element
     * rather than throwing an exception.)
     *
     * @return <code>true</code> if the iterator has more elements.
     */

    @Override
    public boolean hasNext() {
        return pos < 2;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration, or null if there are no more
     */
    @Override
    public Item next() {
        switch (pos++) {
            case 0:
                return one;
            case 1:
                return two;
            default:
                return null;
        }
    }

    @Override
    public boolean supportsGetLength() {
        return true;
    }

    @Override
    public int getLength() {
        return 2;
    }

    public boolean isActuallyGrounded() {
        return true;
    }

    @Override
    public GroundedValue getResidue() {
        switch (pos) {
            case 0:
                return new SequenceExtent.Of<Item>(new Item[]{one, two});
            case 1:
                return two;
            default:
                return EmptySequence.getInstance();
        }
    }
}

