////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.NodeInfo;

/**
 * This class implements an AxisIterator by providing a start item, and a function
 * that selects the next item as a function of the previous item.
 */

public class IncrementalIterator implements AxisIterator {

    // Note: used in SaxonCS

    private java.util.function.Function<NodeInfo, NodeInfo> stepper;
    private NodeInfo nextItem;

    /**
     * Create an IncrementalIterator
     *
     * @param start   the first item to be returned by the iterator
     * @param stepper a function that computes the next item, given the current item, or returns
     *                null at the end of the sequence.
     */
    public IncrementalIterator(NodeInfo start, java.util.function.Function<NodeInfo, NodeInfo> stepper) {
        this.stepper = stepper;
        this.nextItem = stepper.apply(start);
    }

    /**
     * Get the next item in the sequence
     *
     * @return the next item, or null if there are no more items to be returned.
     */
    public NodeInfo next() {
        NodeInfo current = nextItem;
        if (current != null) {
            nextItem = stepper.apply(current);
        }
        return current;
    }

}

