////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.GNode;
import net.sf.saxon.om.SequenceIterator;

/**
 * This class implements an AxisIterator by providing a start item, and a function
 * that selects the next item as a function of the previous item.
 */

public class IncrementalIterator implements SequenceIterator {

    // Note: used in SaxonCS

    private final java.util.function.Function<GNode, GNode> stepper;
    private GNode nextItem;

    /**
     * Create an IncrementalIterator
     *
     * @param start   the first item to be returned by the iterator
     * @param stepper a function that computes the next item, given the current item, or returns
     *                null at the end of the sequence.
     */
    public IncrementalIterator(GNode start, java.util.function.Function<GNode, GNode> stepper) {
        this.stepper = stepper;
        this.nextItem = stepper.apply(start);
    }

    /**
     * Get the next item in the sequence
     *
     * @return the next item, or null if there are no more items to be returned.
     */
    public GNode next() {
        GNode current = nextItem;
        if (current != null) {
            nextItem = stepper.apply(current);
        }
        return current;
    }

}

