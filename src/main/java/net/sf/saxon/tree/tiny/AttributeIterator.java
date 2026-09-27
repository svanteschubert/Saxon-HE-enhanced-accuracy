////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.AtomizedValueIterator;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.trans.XPathException;

/**
 * AttributeIterator is an iterator over all the attribute nodes of an Element in the TinyTree.
 */

final class AttributeIterator implements SequenceIterator, AtomizedValueIterator {

    private final TinyTree tree;
    private final int element;
    private final NodePredicate predicate;
    private int index;
    private TinyAttributeImpl current = null;

    /**
     * Constructor. Note: this constructor will only be called if the relevant node
     * is an element and if it has one or more attributes. Otherwise an {@link net.sf.saxon.tree.iter.EmptyIterator}
     * will be constructed instead.
     *
     * @param tree:     the containing TinyTree
     * @param element:  the node number of the element whose attributes are required
     * @param predicate: condition to be applied to the names of the attributes selected
     */

    AttributeIterator(TinyTree tree, int element, NodePredicate predicate) {

        this.predicate = predicate;
        this.tree = tree;
        this.element = element;
        index = tree.alpha[element];

    }

    /**
     * Move to the next node in the iteration. On completion, currentNodeNr points
     * to the next attribute.
     * @return true if there are more items in the sequence
     */

    private boolean moveToNext() {
        while (true) {
            if (index >= tree.numberOfAttributes || tree.attParent[index] != element) {
                index = Integer.MAX_VALUE;
                current = null;
                return false;
            }
            TinyAttributeImpl att = tree.getAttributeNode(index);
            if (predicate.test(att)) {
                current = att;
                index++;
                return true;
            }
            index++;
        }
    }

    /**
     * Get the next item in the sequence.
     *
     * @return the next Item. If there are no more nodes, return null.
     */

    /*@Nullable*/
    @Override
    public NodeInfo next() {
        if (moveToNext()) {
            return current;
        } else {
            return null;
        }
    }

    /**
     * Deliver the atomic value that is next in the atomized result
     *
     * @return the next atomic value
     * @throws net.sf.saxon.trans.XPathException
     *          if a failure occurs reading or atomizing the next value
     */
    @Override
    public AtomicSequence nextAtomizedValue() throws XPathException {
        if (moveToNext()) {
            return current.atomize();
        } else {
            return null;
        }
    }

}

