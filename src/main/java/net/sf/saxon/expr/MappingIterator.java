////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * MappingIterator merges a sequence of sequences into a single flat
 * sequence. It takes as inputs an iteration, and a mapping function to be
 * applied to each Item returned by that iteration. The mapping function itself
 * returns another iteration. The result is an iteration of the concatenation of all
 * the iterations returned by the mapping function.
 * <p>This is a powerful class. It is used, with different mapping functions,
 * in a great variety of ways. It underpins the way that "for" expressions and
 * path expressions are evaluated, as well as sequence expressions. It is also
 * used in the implementation of the document(), key(), and id() functions.</p>
 */

public class MappingIterator implements SequenceIterator {

    private SequenceIterator base;
    private MappingFunction action;
    private SequenceIterator results = null;

    /**
     * Construct a MappingIterator that will apply a specified MappingFunction to
     * each Item returned by the base iterator.
     *
     * @param base   the base iterator, which must deliver items of type F
     * @param action the mapping function to be applied
     */

    public MappingIterator(SequenceIterator base, MappingFunction action) {
        this.base = base;
        this.action = action;
    }

    @Override
    public Item next() throws XPathException {
        Item nextItem;
        while (true) {
            if (results != null) {
                nextItem = results.next();
                if (nextItem != null) {
                    break;
                } else {
                    results = null;
                }
            }
            Item nextSource = base.next();
            if (nextSource != null) {
                // Call the supplied mapping function
                SequenceIterator obj = action.map(nextSource);

                // The result may be null (representing an empty sequence),
                //  or a SequenceIterator (any sequence)

                if (obj != null) {
                    results = obj;
                    nextItem = results.next();
                    if (nextItem == null) {
                        results = null;
                    } else {
                        break;
                    }
                }
                // now go round the loop to get the next item from the base sequence
            } else {
                results = null;
                return null;
            }
        }

        return nextItem;
    }

    @Override
    public void close() {
        if (results != null) {
            results.close();
        }
        base.close();
    }

}

