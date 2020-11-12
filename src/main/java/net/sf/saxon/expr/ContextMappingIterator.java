////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.FocusIterator;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * ContextMappingIterator merges a sequence of sequences into a single flat
 * sequence. It takes as inputs an iteration, and a mapping function to be
 * applied to each Item returned by that iteration. The mapping function itself
 * returns another iteration. The result is an iteration of the concatenation of all
 * the iterations returned by the mapping function: often referred to as a flat-map operation.
 * <p>This is related to the {@link MappingIterator} class: it differs in that it
 * sets each item being processed as the context item</p>
 */

public final class ContextMappingIterator implements SequenceIterator {

    private FocusIterator base;
    private ContextMappingFunction action;
    private XPathContext context;
    private SequenceIterator stepIterator = null;

    /**
     * Construct a ContextMappingIterator that will apply a specified ContextMappingFunction to
     * each Item returned by the base iterator.
     *
     * @param action  the mapping function to be applied
     * @param context the processing context. The mapping function is applied to each item returned
     *                by context.getCurrentIterator() in turn.
     */

    public ContextMappingIterator(ContextMappingFunction action, XPathContext context) {
        base = context.getCurrentIterator();
        this.action = action;
        this.context = context;
    }

    @Override
    public Item next() throws XPathException {
        Item nextItem;
        while (true) {
            if (stepIterator != null) {
                nextItem = stepIterator.next();
                if (nextItem != null) {
                    break;
                } else {
                    stepIterator = null;
                }
            }
            if (base.next() != null) {
                // Call the supplied mapping function
                stepIterator = action.map(context);
                nextItem = stepIterator.next();
                if (nextItem == null) {
                    stepIterator = null;
                } else {
                    break;
                }

            } else {
                stepIterator = null;
                return null;
            }
        }

        return nextItem;
    }

    @Override
    public void close() {
        base.close();
        if (stepIterator != null) {
            stepIterator.close();
        }
    }


}

