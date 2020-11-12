////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * An iterator over nodes, that prepends a given node to the nodes
 * returned by another iterator. Used to modify an iterator over axis A
 * to one that iterates over A-OR-SELF.
 */

public class PrependSequenceIterator implements SequenceIterator {
    Item start;
    SequenceIterator base;

    public PrependSequenceIterator(Item start, SequenceIterator base) {
        this.start = start;
        this.base = base;
    }


    /**
     * Get the next item in the sequence.
     * @return the next Item. If there are no more items, return null.
     */

    /*@Nullable*/
    @Override
    public Item next() throws XPathException {
        if (start != null) {
            Item temp = start;
            start = null;
            return temp;
        } else {
            return base.next();
        }
    }

    @Override
    public void close() {
        base.close();
    }



}

