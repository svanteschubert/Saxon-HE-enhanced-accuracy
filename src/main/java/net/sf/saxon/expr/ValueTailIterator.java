////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;

/**
 * <code>ValueTailIterator</code> iterates over a base sequence starting at an element other than the first.
 * It is used in the case where the base sequence is "grounded", that is, it exists in memory and
 * supports efficient direct addressing.
 */

public class ValueTailIterator
        implements SequenceIterator, GroundedIterator, LookaheadIterator {

    private final GroundedValue baseValue;
    private final int start;  // zero-based
    private int pos = 0;

    /**
     * Construct a ValueTailIterator
     *
     * @param base  The items to be filtered
     * @param start The position of the first item to be included (zero-based)
     */

    public ValueTailIterator(GroundedValue base, int start) {
        baseValue = base;
        this.start = start;
        pos = 0;
    }

    @Override
    public Item next() {
        return baseValue.itemAt(start + pos++);
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    @Override
    public boolean hasNext() {
        return baseValue.itemAt(start + pos) != null;
    }

    public boolean isActuallyGrounded() {
        return true;
    }

    /**
     * Return a Value containing all the items in the sequence returned by this
     * SequenceIterator. This should be an "in-memory" value, not a Closure.
     *
     * @return the corresponding Value
     */

    @Override
    public GroundedValue materialize() {
        if (start == 0) {
            return baseValue;
        } else {
            return baseValue.subsequence(start, Integer.MAX_VALUE);
        }
    }

    @Override
    public GroundedValue getResidue() {
        if (start == 0 && pos == 0) {
            return baseValue;
        } else {
            return baseValue.subsequence(start + pos, Integer.MAX_VALUE);
        }
    }

}

