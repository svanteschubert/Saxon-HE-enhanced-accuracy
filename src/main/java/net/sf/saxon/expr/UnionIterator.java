////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * A multi-way union delivering the sorted results obtained from a number
 * of sorted input iterators
 */

public class UnionIterator implements SequenceIterator, LookaheadIterator {

    // We maintain a sorted list of "intakes", one for each input iterator
    // that is not yet exhausted. Each "intake" contains the iterator itself,
    // and the next node delivered by the iterator; the sorted list is maintained
    // as a Java TreeSet sorted by the document order of the next node to be
    // delivered.

    private static class Intake {
        public SequenceIterator iter;
        public NodeInfo nextNode;

        public Intake(SequenceIterator iter, NodeInfo nextNode) {
            this.iter = iter;
            this.nextNode = nextNode;
        }
    }

    //@CSharpInjectMembers(code = "public override int Compare(net.sf.saxon.expr.UnionIterator.Intake a, net.sf.saxon.expr.UnionIterator.Intake b) { return compare(a, b); }")
    private static class IntakeComparer implements Comparator<Intake> {
        private final Comparator<? super NodeInfo> itemOrderComparer;
        public IntakeComparer(Comparator<? super NodeInfo> itemOrderComparer) {
            this.itemOrderComparer = itemOrderComparer;
        }

        @Override
        public int compare(Intake o1, Intake o2) {
            return itemOrderComparer.compare(o1.nextNode, o2.nextNode);
        }
    }

    private final TreeSet<Intake> intakes;

    /**
     * Create the iterator. The several input iterators must return nodes in document
     * order for this to work.
     *
     * @param inputs   iterators over the first operand sequence (in document order)
     * @param comparer used to test whether nodes are in document order. Different versions
     *                 are used for intra-document and cross-document operations
     * @throws XPathException if an error occurs reading the first item of either operand
     */

    public UnionIterator(List<SequenceIterator> inputs,
                         Comparator<? super NodeInfo> comparer) throws XPathException {

        // The comparator between Intakes is based on the supplied comparator between nodes
        // (The implementation using a static inner class is constrained by the C# conversion)

        Comparator<Intake> comp = new IntakeComparer(comparer);

        // Create a set of intakes, one for each input iterator, primed with the
        // first node delivered by the iterator - unless it is a duplicate, The
        // list of intakes is automatically kept in sorted order.

        intakes = new TreeSet<>(comp);
        for (SequenceIterator seq : inputs) {
            NodeInfo next = (NodeInfo) seq.next();
            while (next != null) {
                boolean added = intakes.add(new Intake(seq, next));
                if (added) {
                    break;
                } else {
                    // the node was a duplicate, so we skip it
                    next = (NodeInfo) seq.next();
                }
            }
        }
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    @Override
    public boolean hasNext() {
        return !intakes.isEmpty();
    }

    @Override
    public NodeInfo next() {

        // Since the intakes are sorted, we can simply take the first.

        Intake nextIntake = intakes.pollFirst();  // takes the first and removes it from the list
        if (nextIntake != null) {

            // This intake contains the node that we will deliver; the task now is to
            // replenish the list. We find the next node returned by the corresponding
            // iterator, provided it is not a duplicate. It is a duplicate if it matches
            // either (a) the node we're just about to return (which is no longer in the TreeMap)
            // or (b) any other entry in the TreeMap

            SequenceIterator iter = nextIntake.iter;
            NodeInfo nextNode = (NodeInfo) iter.next();
            while (nextNode != null) {
                boolean added = false;
                if (!nextNode.isSameNodeInfo(nextIntake.nextNode)) {
                    Intake replacement = new Intake(iter, nextNode);
                    added = intakes.add(replacement);
                }
                if (added) {
                    break;
                } else {
                    nextNode = (NodeInfo) iter.next();
                }
            }
            return nextIntake.nextNode;
        }
        // The set of intakes is now empty, so we're finished
        return null;
    }

    @Override
    public void close() {
        for (Intake intake : intakes) {
            intake.iter.close();
        }
    }

}

