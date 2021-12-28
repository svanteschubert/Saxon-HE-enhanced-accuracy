////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.value.AtomicValue;


/**
 * A SequenceIterator is used to iterate over a sequence. An AtomicIterator
 * is a SequenceIterator that returns atomic values and throws no checked exceptions.
 */

public interface AtomicIterator<T extends AtomicValue> extends UnfailingIterator {

    /**
     * Get the next atomic value in the sequence. <BR>
     *
     * @return the next Item. If there are no more items, return null.
     */

    @Override
    T next();


}

