////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.value.SequenceExtent;

/**
 * This interface is an extension to the SequenceIterator interface; it represents
 * a SequenceIterator that is based on an in-memory representation of a sequence,
 * and that is therefore capable of returning a Sequence containing all the items
 * in the sequence.
 *
 * <p>We stretch the concept to consider an iterator over a MemoClosure as a grounded
 * iterator, on the basis that the in-memory sequence might exist already or might
 * be created as a side-effect of navigating the iterator. This is why materializing
 * the iterator can raise an exception.</p>
 */

public interface GroundedIterator extends SequenceIterator {

    /**
     * Ask if the iterator is actually grounded. This method must be called before calling
     * {@link #materialize()} or {@link #getResidue()}, because the iterator might
     * be grounded under some conditions and not others (usually when it delegates
     * to another iterator)
     * @return true if this iterator is grounded
     */

    boolean isActuallyGrounded();

    /**
     * Return a GroundedValue containing all the remaining items in the sequence returned by this
     * SequenceIterator, starting at the current position. This should be an "in-memory" value, not a
     * Closure. This method does not change the state of the iterator (in particular, it does not
     * consume the iterator).
     *
     * @return the corresponding Value
     * @throws UncheckedXPathException in the cases of subclasses (such as the iterator over a MemoClosure)
     *                        which cause evaluation of expressions while materializing the value.
     */

    GroundedValue getResidue();

    /**
     * Create a GroundedValue (a sequence materialized in memory) containing all the values delivered
     * by this SequenceIterator. The method must only be used when the SequenceIterator is positioned
     * at the start. If it is not positioned at the start, then it is implementation-dependant whether
     * the returned sequence contains all the nodes delivered by the SequenceIterator from the beginning,
     * or only those delivered starting at the current position.
     * <p>It is implementation-dependant whether this method consumes the SequenceIterator. (More specifically,
     * in the current implementation: if the iterator is backed by a {@link GroundedValue}, then that
     * value is returned and the iterator is not consumed; otherwise, the iterator is consumed and the
     * method returns the remaining items after the current position only).</p>
     *
     * @return a sequence containing all the items delivered by this SequenceIterator.
     * @throws UncheckedXPathException if reading the SequenceIterator throws an error
     */

    default GroundedValue materialize() {
        return SequenceExtent.from(this).reduce();
    }
}

