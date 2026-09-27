////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;

/**
 * An iterator over nodes, that concatenates the nodes returned by two supplied iterators.
 */

public class ConcatenatingIterator implements SequenceIterator {

    SequenceIterator first;
    SequenceIterator second;
    SequenceIterator active;

    public ConcatenatingIterator(SequenceIterator first, SequenceIterator second) {
        this.first = first;
        this.second = second;
        this.active = first;
    }

    /**
     * Get the next item in the sequence. <BR>
     *
     * @return the next Item. If there are no more nodes, return null.
     */

    /*@Nullable*/
    @Override
    public Item next() {
        Item n = active.next();
        if (n == null && active == first) {
            active = second;
            n = second.next();
        }
        return n;
    }

    @Override
    public void close() {
        first.close();
        second.close();
    }



}

