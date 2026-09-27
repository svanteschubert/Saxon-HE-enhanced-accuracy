////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.om.AtomicArray;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.transpile.CSharpReplaceMethod;
import net.sf.saxon.value.AtomicValue;

/**
 * EmptyIterator: an iterator over an empty sequence. Since such an iterator has no state,
 * only one instance is required; therefore a singleton instance is available via the static
 * getInstance() method.
 */

public enum EmptyAtomicIterator implements AtomicIterator,
        ReversibleIterator, LastPositionFinder, GroundedIterator,
        LookaheadIterator {

    INSTANCE;

    public static EmptyAtomicIterator getInstance() {
        return INSTANCE;
    }

    /**
     * Get the next item.
     *
     * @return the next item. For the EmptyIterator this is always null.
     */
    @Override
    @CSharpReplaceMethod(code="public Saxon.Hej.om.Item next() { return null; }")
    public AtomicValue next() {
        return null;
    }

    @Override
    public boolean supportsGetLength() {
        return true;
    }

    /**
     * Get the position of the last item in the sequence.
     *
     * @return the position of the last item in the sequence, always zero in
     *         this implementation
     */
    @Override
    public int getLength() {
        return 0;
    }

    /**
     * Get another iterator over the same items, in reverse order.
     *
     * @return a reverse iterator over an empty sequence (in practice, it
     *         returns the same iterator each time)
     */
    /*@NotNull*/
    @Override
    public SequenceIterator getReverseIterator() {
        return this;
    }

    /**
     * Return a Value containing all the items in the sequence returned by this
     * SequenceIterator. This should be an "in-memory" value, not a Closure.
     *
     * @return the corresponding Value
     */

    @Override
    public GroundedValue materialize() {
        return AtomicArray.EMPTY_ATOMIC_ARRAY;
    }

    @Override
    public GroundedValue getResidue() {
        return AtomicArray.EMPTY_ATOMIC_ARRAY;
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    /**
     * Determine whether there are more items to come. Note that this operation
     * is stateless and it is not necessary (or usual) to call it before calling
     * next(). It is used only when there is an explicit need to tell if we
     * are at the last element.
     *
     * @return true if there are more nodes
     */

    @Override
    public boolean hasNext() {
        return false;
    }

    public boolean isActuallyGrounded() {
        return true;
    }

    @Override
    public void close() {
    }



 
}

