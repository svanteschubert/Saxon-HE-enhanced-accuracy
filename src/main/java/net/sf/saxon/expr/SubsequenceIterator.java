////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.*;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;


/**
 * A SubsequenceIterator selects a subsequence of a sequence
 */

public class SubsequenceIterator implements SequenceIterator, LastPositionFinder, LookaheadIterator {

    private final SequenceIterator base;
    private int basePosition = 0;
    private final int min;
    private final int max;
    /*@Nullable*/ private Item nextItem = null;

    /**
     * Private Constructor: use the factory method instead!
     *
     * @param base An iteration of the items to be filtered
     * @param min  The position of the first item to be included (1-based)
     * @param max  The position of the last item to be included (1-based)
     * @throws XPathException if a dynamic error occurs
     */

    private SubsequenceIterator(SequenceIterator base, int min, int max) throws XPathException {
        this.base = base;
        this.min = min;
        if (min < 1) {
            min = 1;
        }
        this.max = max;
        if (max < min) {
            nextItem = null;
            return;
        }
        int i = 1;
        while (i++ <= min) {
            nextItem = base.next();
            basePosition++;
            if (nextItem == null) {
                break;
            }
        }
    }

    /**
     * Static factory method. Creates a SubsequenceIterator, unless for example the base Iterator is an
     * ArrayIterator, in which case it optimizes by creating a new ArrayIterator directly over the
     * underlying array. This optimization is important when doing recursion over a node-set using
     * repeated calls of <code>$nodes[position()&gt;1]</code>
     *
     * @param base An iteration of the items to be filtered
     * @param min  The position of the first item to be included (base 1)
     * @param max  The position of the last item to be included (base 1)
     * @return an iterator over the requested subsequence
     * @throws XPathException if a dynamic error occurs
     */

    public static SequenceIterator make(SequenceIterator base, int min, int max) throws XPathException {
        if (base instanceof ArrayIterator) {
            return ((ArrayIterator) base).makeSliceIterator(min, max);
        } else if (max == Integer.MAX_VALUE) {
            return TailIterator.make(base, min);
        } else if (base instanceof GroundedIterator && ((GroundedIterator)base).isActuallyGrounded() && min > 4) {
            try {
                GroundedValue value = SequenceTool.toGroundedValue(base);
                value = value.subsequence(min - 1, max - min + 1);
                return value.iterate();
            } catch (UncheckedXPathException e) {
                throw e.getXPathException();
            }
        } else {
            return new SubsequenceIterator(base, min, max);
        }
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }


    /**
     * Test whether there are any more items available in the sequence
     */

    @Override
    public boolean hasNext() {
        return nextItem != null;
    }

    /**
     * Get the next item if there is one
     */

    @Override
    public Item next() {
        if (nextItem == null) {
            return null;
        }
        Item current = nextItem;
        if (basePosition < max) {
            nextItem = base.next();
            basePosition++;
        } else {
            nextItem = null;
            base.close();
        }
        return current;
    }

    @Override
    public void close() {
        base.close();
    }

    /**
     * Ask whether this iterator supports use of the {@link #getLength()} method. This
     * method should always be called before calling {@link #getLength()}, because an iterator
     * that implements this interface may support use of {@link #getLength()} in some situations
     * and not in others
     *
     * @return true if the {@link #getLength()} method can be called to determine the length
     * of the underlying sequence.
     */
    @Override
    public boolean supportsGetLength() {
        return SequenceTool.supportsGetLength(base);
    }

    /**
     * Get the last position (that is, the number of items in the sequence).
     */

    @Override
    public int getLength() {
        int lastBase = SequenceTool.getLength(base);
        int z = Math.min(lastBase, max);
        return Math.max(z - min + 1, 0);
    }

}

