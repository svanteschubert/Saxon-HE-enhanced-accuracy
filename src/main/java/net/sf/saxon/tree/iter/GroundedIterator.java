////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

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
     * Return a GroundedValue containing all the items in the sequence returned by this
     * SequenceIterator. This should be an "in-memory" value, not a Closure. This method
     * does not change the state of the iterator (in particular, it does not consume the
     * iterator).
     *
     * @return the corresponding Value
     * @throws XPathException in the cases of subclasses (such as the iterator over a MemoClosure)
     * which cause evaluation of expressions while materializing the value.
     */

    @Override
    GroundedValue materialize() throws XPathException;

    /**
     * Return a GroundedValue containing all the remaining items in the sequence returned by this
     * SequenceIterator, starting at the current position. This should be an "in-memory" value, not a
     * Closure. This method does not change the state of the iterator (in particular, it does not
     * consume the iterator).
     *
     * @return the corresponding Value
     * @throws XPathException in the cases of subclasses (such as the iterator over a MemoClosure)
     *                        which cause evaluation of expressions while materializing the value.
     */

    GroundedValue getResidue() throws XPathException;
}

