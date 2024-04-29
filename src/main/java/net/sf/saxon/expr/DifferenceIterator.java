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
 * An enumeration representing a nodeset that is teh difference of two other NodeSets.
 * There is an "except" operator in XPath 2.0 to create such an expression.
 */


public class DifferenceIterator implements SequenceIterator {


    private final SequenceIterator p1;
    private final SequenceIterator p2;

    private NodeInfo nextNode1;
    private NodeInfo nextNode2;
    private final Comparator<? super NodeInfo> comparer;

    /**
     * Form an enumeration of the difference of two nodesets, that is, the nodes
     * that are in p1 and that are not in p2.
     *
     * @param p1       the first operand, with nodes delivered in document order
     * @param p2       the second operand, with nodes delivered in document order
     * @param comparer the comparer
     * @throws XPathException if a failure occurs reading from either input sequence
     */

    public DifferenceIterator(SequenceIterator p1, SequenceIterator p2,
                              Comparator<? super NodeInfo> comparer) throws XPathException {
        this.p1 = p1;
        this.p2 = p2;
        this.comparer = comparer;

        // move to the first node in each input nodeset

        nextNode1 = nextNode(p1);
        nextNode2 = nextNode(p2);
    }

    /**
     * Get the next item from one of the input sequences,
     * checking that it is a node.
     *
     * @param iter the iterator from which the next node is to be read
     * @return the node that was read, or null if the stream is exhausted
     * @throws UncheckedXPathException if a failure occurs reading the input
     */

    private NodeInfo nextNode(SequenceIterator iter) {
        return (NodeInfo)iter.next();
        // rely on type-checking to prevent a ClassCastException
    }

    @Override
    public NodeInfo next() {
        // main merge loop: if the node in p1 has a lower key value that that in p2, return it;
        // if they are equal, advance both nodesets; if p1 is higher, advance p2.

        while (true) {

            if (nextNode1 == null) {
                p2.close();
                return null;
            }

            if (nextNode2 == null) {
                // second node-set is exhausted; return the next node from the first node-set
                return deliver();
            }

            int c = comparer.compare(nextNode1, nextNode2);
            if (c < 0) {                              // p1 is lower
                return deliver();

            } else if (c > 0) {                       // p1 is higher
                nextNode2 = nextNode(p2);
                if (nextNode2 == null) {
                    return deliver();
                }

            } else {                                // keys are equal
                nextNode2 = nextNode(p2);
                nextNode1 = nextNode(p1);
            }
        }
    }

    /**
     * Deliver the next node from the first node-set, advancing the iterator to
     * look-ahead for the next item, and setting the current and position variables.
     *
     * @return the next node from the first node-set
     * @throws UncheckedXPathException if a failure occurs reading from the input
     */
    private NodeInfo deliver() {
        NodeInfo current = nextNode1;
        nextNode1 = nextNode(p1);
        return current;
    }

    @Override
    public void close() {
        p1.close();
        p2.close();
    }

}

