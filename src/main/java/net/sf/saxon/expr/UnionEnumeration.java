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
import net.sf.saxon.tree.iter.LookaheadIterator;

import java.util.Comparator;

/**
 * An enumeration representing a nodeset that is a union of two other NodeSets.
 */

public class UnionEnumeration implements SequenceIterator, LookaheadIterator {

    // TODO: drop this class (still used when streaming)

    private final SequenceIterator e1;
    private final SequenceIterator e2;
    /*@Nullable*/ private NodeInfo nextNode1 = null;
    private NodeInfo nextNode2 = null;
    private final Comparator<? super NodeInfo> comparer;

    /**
     * Create the iterator. The two input iterators must return nodes in document
     * order for this to work.
     *
     * @param p1       iterator over the first operand sequence (in document order)
     * @param p2       iterator over the second operand sequence
     * @param comparer used to test whether nodes are in document order. Different versions
     *                 are used for intra-document and cross-document operations
     * @throws XPathException if an error occurs reading the first item of either operand
     */

    public UnionEnumeration(SequenceIterator p1, SequenceIterator p2,
                            Comparator<? super NodeInfo> comparer) throws XPathException {
        this.e1 = p1;
        this.e2 = p2;
        this.comparer = comparer;

        nextNode1 = nextNode(e1);
        nextNode2 = nextNode(e2);
    }

    /**
     * Get the next item from one of the input sequences,
     * checking that it is a node.
     *
     * @param iter the sequence from which a node is to be read
     * @return the node that was read
     * @throws UncheckedXPathException if reading from either of the input sequences fails
     */

    private NodeInfo nextNode(SequenceIterator iter) {
        return (NodeInfo) iter.next();
        // we rely on the type-checking mechanism to prevent a ClassCastException here
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    @Override
    public boolean hasNext() {
        return nextNode1 != null || nextNode2 != null;
    }

    @Override
    public NodeInfo next() {

        // main merge loop: take a value from whichever set has the lower value

        if (nextNode1 != null && nextNode2 != null) {
            int c = comparer.compare(nextNode1, nextNode2);
            if (c < 0) {
                NodeInfo current = nextNode1;
                nextNode1 = nextNode(e1);
                return current;

            } else if (c > 0) {
                NodeInfo current = nextNode2;
                nextNode2 = nextNode(e2);
                return current;

            } else {
                NodeInfo current = nextNode2;
                nextNode2 = nextNode(e2);
                nextNode1 = nextNode(e1);
                return current;
            }
        }

        // collect the remaining nodes from whichever set has a residue

        if (nextNode1 != null) {
            NodeInfo current = nextNode1;
            nextNode1 = nextNode(e1);
            return current;
        }
        if (nextNode2 != null) {
            NodeInfo current = nextNode2;
            nextNode2 = nextNode(e2);
            return current;
        }
        return null;
    }

    @Override
    public void close() {
        e1.close();
        e2.close();
    }

}

