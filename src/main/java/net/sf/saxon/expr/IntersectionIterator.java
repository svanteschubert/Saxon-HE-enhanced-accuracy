////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;

import java.util.Comparator;

/**
 * An enumeration representing a nodeset that is an intersection of two other NodeSets.
 * This implements the XPath 2.0 operator "intersect".
 */


public class IntersectionIterator implements SequenceIterator {

    private final SequenceIterator e1;
    private final SequenceIterator e2;
    private NodeInfo nextNode1;
    private NodeInfo nextNode2;
    private final Comparator<? super NodeInfo> comparer;

    /**
     * Form an enumeration of the intersection of the nodes in two nodesets
     *
     * @param p1       the first operand: must be in document order
     * @param p2       the second operand: must be in document order
     * @param comparer Comparer to be used for putting nodes in document order
     * @throws XPathException if an error occurs, for example reading from the input sequence
     */

    public IntersectionIterator(SequenceIterator p1, SequenceIterator p2,
                                Comparator<? super NodeInfo> comparer) throws XPathException {
        e1 = p1;
        e2 = p2;
        this.comparer = comparer;

        // move to the first node in each input nodeset

        nextNode1 = nextNode(e1);
        nextNode2 = nextNode(e2);
    }

    /**
     * Get the next item from one of the input sequences,
     * checking that it is a node.
     *
     * @param iter the iterator from which the next item is to be taken
     * @return the next value returned by that iterator
     * @throws UncheckedXPathException if a failure occurs reading from the input sequence
     */

    private NodeInfo nextNode(SequenceIterator iter) {
        return (NodeInfo)iter.next();
        // rely on type-checking to prevent a ClassCastException
    }

    @Override
    public NodeInfo next() {
        // main merge loop: iterate whichever sequence has the lower value, returning when a pair
        // is found that match.

        if (nextNode1 == null) {
            e2.close();
            return null;
        }

        if (nextNode2 == null) {
            e1.close();
            return null;
        }

        while (nextNode1 != null && nextNode2 != null) {
            int c = comparer.compare(nextNode1, nextNode2);
            if (c < 0) {
                nextNode1 = nextNode(e1);
            } else if (c > 0) {
                nextNode2 = nextNode(e2);
            } else {            // keys are equal
                NodeInfo current = nextNode2;    // which is the same as next1
                nextNode2 = nextNode(e2);
                nextNode1 = nextNode(e1);
                return current;
            }
        }
        return null;
    }

    @Override
    public void close() {
        e1.close();
        e2.close();
    }

}

