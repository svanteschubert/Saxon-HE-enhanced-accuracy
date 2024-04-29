////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.trans.UncheckedXPathException;


/**
 * A LastPositionFinder is an interface implemented by any SequenceIterator that is
 * able to return the position of the last item in the sequence.
 */

public interface LastPositionFinder {

    /**
     * Ask whether this iterator supports use of the {@link #getLength()} method. This
     * method should always be called before calling {@link #getLength()}, because an iterator
     * that implements this interface may support use of {@link #getLength()} in some situations
     * and not in others
     * @return true if the {@link #getLength()} method can be called to determine the length
     * of the underlying sequence.
     */

    boolean supportsGetLength();

    /**
     * Get the last position (that is, the number of items in the sequence). This method is
     * non-destructive: it does not change the state of the iterator.
     * The result is undefined if the next() method of the iterator has already returned null.
     * This method must not be called unless the {@link #supportsGetLength()} has been called
     * and has returned true.
     *
     * @return the number of items in the sequence
     * @throws UncheckedXPathException if an error occurs evaluating the sequence in order to determine
     *                        the number of items
     * @throws ClassCastException if the capability is not available. (This exception is not guaranteed.)
     */

    int getLength();

}

