////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.SequenceIterator;


/**
 * A ReversibleIterator is an interface implemented by any SequenceIterator that is
 * able to deliver items in reverse order (or to supply another iterator that can
 * do so).
 */

public interface ReversibleIterator extends SequenceIterator {

    /**
     * Get a new SequenceIterator that returns the same items in reverse order.
     * If this SequenceIterator is an AxisIterator, then the returned SequenceIterator
     * must also be an AxisIterator.
     *
     * @return an iterator over the items in reverse order
     */

    SequenceIterator getReverseIterator();

}

