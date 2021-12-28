////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;


/**
 * A LastPositionFinder is an interface implemented by any SequenceIterator that is
 * able to return the position of the last item in the sequence.
 */

@FunctionalInterface
public interface LastPositionFinder {

    /**
     * Get the last position (that is, the number of items in the sequence). This method is
     * non-destructive: it does not change the state of the iterator.
     * The result is undefined if the next() method of the iterator has already returned null.
     * This method must not be called unless the result of getProperties() on the iterator
     * includes the bit setting {@link net.sf.saxon.om.SequenceIterator.Property#LAST_POSITION_FINDER}
     *
     * @return the number of items in the sequence
     * @throws XPathException if an error occurs evaluating the sequence in order to determine
     *                        the number of items
     */

    int getLength() throws XPathException;

}

