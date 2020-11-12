////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ObjectValue;

import java.util.EnumSet;

/**
 * An iterator representing the sorted merge of two merge inputs, retaining all duplicates.
 * This iterator simply returns the items from all the inputs merged into a single sequence;
 * it does not do any grouping of adjacent items that share the same merge key.
 */

public class MergeIterator implements
        SequenceIterator /*<ObjectValue<ItemWithMergeKeys>>*/,
        LookaheadIterator /*<ObjectValue<ItemWithMergeKeys>>*/ {

    private SequenceIterator e1;
    private SequenceIterator e2;
    private ObjectValue<ItemWithMergeKeys> nextItem1 = null;
    private ObjectValue<ItemWithMergeKeys> nextItem2 = null;
    private ItemOrderComparer comparer;

    /**
     * Create the iterator. The two input iterators must return nodes in merge key
     * order for this to work.
     *
     * @param p1       iterator over the first operand sequence (in document order)
     * @param p2       iterator over the second operand sequence
     * @param comparer used to test whether nodes are in document order. Different versions
     *                 are used for intra-document and cross-document operations
     * @throws XPathException if an error occurs reading from either input iterator
     */

    public MergeIterator(SequenceIterator /*<ObjectValue<ItemWithMergeKeys>>*/ p1,
                         SequenceIterator /*<ObjectValue<ItemWithMergeKeys>>*/ p2,
                         ItemOrderComparer comparer) throws XPathException {
        this.e1 = p1;
        this.e2 = p2;
        this.comparer = comparer;

        nextItem1 = (ObjectValue<ItemWithMergeKeys>)e1.next();
        nextItem2 = (ObjectValue<ItemWithMergeKeys>)e2.next();
    }

    @Override
    public boolean hasNext() {
        return nextItem1 != null || nextItem2 != null;
    }

    @Override
    public ObjectValue<ItemWithMergeKeys> next() throws XPathException {

        // main merge loop: take an item from whichever set has the lower value otherwise take value from first and move iterator on by one.

        if (nextItem1 != null && nextItem2 != null) {
            int c;
            try {
                c = comparer.compare(nextItem1, nextItem2);
            } catch (ClassCastException e) {
                ItemWithMergeKeys i1 = nextItem1.getObject();
                ItemWithMergeKeys i2 = nextItem2.getObject();
                AtomicValue a1 = i1.sortKeyValues.get(0);
                AtomicValue a2 = i2.sortKeyValues.get(0);
                XPathException err = new XPathException("Merge key values are of non-comparable types ("
                        + Type.displayTypeName(a1)
                        + " and " + Type.displayTypeName(a2) + ")", "XTTE2230");
                err.setIsTypeError(true);
                throw err;
            }
            if (c <= 0) {
                ObjectValue<ItemWithMergeKeys> current = nextItem1;
                nextItem1 = (ObjectValue<ItemWithMergeKeys>)e1.next();
                return current;

            } else /* (c > 0) */ {
                ObjectValue<ItemWithMergeKeys> current = nextItem2;
                nextItem2 = (ObjectValue<ItemWithMergeKeys>)e2.next();
                return current;
            }
        }

        // collect the remaining items from whichever set has a residue

        if (nextItem1 != null) {
            ObjectValue<ItemWithMergeKeys> current = nextItem1;
            nextItem1 = (ObjectValue<ItemWithMergeKeys>)e1.next();
            return current;
        }
        if (nextItem2 != null) {
            ObjectValue<ItemWithMergeKeys> current = nextItem2;
            nextItem2 = (ObjectValue<ItemWithMergeKeys>)e2.next();
            return current;
        }
        return null;
    }

    @Override
    public void close() {
        e1.close();
        e2.close();
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link net.sf.saxon.om.SequenceIterator.Property#GROUNDED}, {@link net.sf.saxon.om.SequenceIterator.Property#LAST_POSITION_FINDER},
     *         and {@link net.sf.saxon.om.SequenceIterator.Property#LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    @Override
    public EnumSet<Property> getProperties() {
        return EnumSet.of(Property.LOOKAHEAD);
    }

}
