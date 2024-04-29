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
import net.sf.saxon.tree.iter.*;

/**
 * TailIterator iterates over a base sequence starting at an element other than the first.
 * The base sequence is represented by an iterator which is consumed in the process
 */

public class TailIterator
        implements SequenceIterator, LastPositionFinder, LookaheadIterator {

    private final SequenceIterator base;
    private final int start;

    /**
     * Private constructor: external callers should use the public factory method.
     * Create a TailIterator, an iterator that starts at position N in a sequence and iterates
     * to the end of the sequence
     *
     * @param base  the base sequence of which we want to select the tail. Unusually, this iterator
     *              should be supplied pre-positioned so that the next call on next() returns the first item to
     *              be returned by the TailIterator
     * @param start the index of the first required item in the sequence, starting from one. To
     *              include all items in the sequence except the first, set start = 2. This value is used only
     *              when cloning the iterator or when calculating the value of last().
     */

    private TailIterator(SequenceIterator base, int start) {
        this.base = base;
        this.start = start;
    }

    /**
     * Static factory method. Creates a TailIterator, unless the base Iterator is an
     * ArrayIterator, in which case it optimizes by creating a new ArrayIterator directly over the
     * underlying array. This optimization is important when doing recursion over a node-set using
     * repeated calls of <code>$nodes[position()&gt;1]</code>
     *
     * @param base  An iteration of the items to be filtered. The state of this iterator after
     *              the operation is undefined - it may or may not be consumed
     * @param start The position of the first item to be included (origin 1). If &lt;= 1, the whole of the
     *              base sequence is returned
     * @return an iterator over the items in the sequence from the start item to the end of the sequence.
     *         The returned iterator will not necessarily be an instance of this class.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs
     */

    public static SequenceIterator make(SequenceIterator base, int start) throws XPathException {
        if (start <= 1) {
            return base;
        } else if (base instanceof ArrayIterator) {
            return ((ArrayIterator) base).makeSliceIterator(start, Integer.MAX_VALUE);
        } else if (base instanceof GroundedIterator && ((GroundedIterator)base).isActuallyGrounded()) {
            try {
                GroundedValue value = SequenceTool.toGroundedValue(base);
                if (start > value.getLength()) {
                    return EmptyIterator.getInstance();
                } else {
                    return new ValueTailIterator(value, start - 1);
                }
            } catch (UncheckedXPathException e) {
                throw e.getXPathException();
            }
        } else {
            // discard the first n-1 items from the underlying iterator
            for (int i = 0; i < start - 1; i++) {
                Item b = base.next();
                if (b == null) {
                    return EmptyIterator.getInstance();
                }
            }
            return new TailIterator(base, start);
        }
    }


    @Override
    public Item next() {
        return base.next();
    }

    @Override
    public boolean supportsHasNext() {
        return base instanceof LookaheadIterator && ((LookaheadIterator)base).supportsHasNext();
    }


    @Override
    public boolean hasNext() {
        return ((LookaheadIterator) base).hasNext();
    }

    @Override
    public boolean supportsGetLength() {
        return SequenceTool.supportsGetLength(base);
    }

    @Override
    public int getLength() {
        int bl = SequenceTool.getLength(base) - start + 1;
        return Math.max(bl, 0);
    }

    @Override
    public void close() {
        base.close();
    }

}

