////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.AtomicSortComparer;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.ma.arrays.ArraySort;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceExtent;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the function fn:sort#1, which is a standard function in XPath 3.1
 */

public class Sort_1 extends SystemFunction {

    public static class ItemToBeSorted {
        public Item value;
        public GroundedValue sortKey;
        public int originalPosition;
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        final List<ItemToBeSorted> inputList = getItemsToBeSorted(arguments[0]);
        StringCollator collation = context.getConfiguration().getCollation(getRetainedStaticContext().getDefaultCollationName());
        return doSort(inputList, collation, context);
    }

    protected List<ItemToBeSorted> getItemsToBeSorted(Sequence input) throws XPathException {
        final List<ItemToBeSorted> inputList = new ArrayList<>();
        int i = 0;
        SequenceIterator iterator = input.iterate();
        Item item;
        while ((item = iterator.next()) != null) {
            ItemToBeSorted member = new ItemToBeSorted();
            member.value = item;
            member.originalPosition = i++;
            member.sortKey = item.atomize();
            inputList.add(member);
        }
        return inputList;
    }

    protected Sequence doSort(final List<ItemToBeSorted> inputList, StringCollator collation, XPathContext context) throws XPathException {
        final AtomicComparer atomicComparer = AtomicSortComparer.makeSortComparer(
                collation, StandardNames.XS_ANY_ATOMIC_TYPE, context);
        try {
            inputList.sort((a, b) -> {
                int result = ArraySort.compareSortKeys(a.sortKey, b.sortKey, atomicComparer);
                if (result == 0) {
                    // TODO: unnecessary, we are now using a stable sort routine
                    return a.originalPosition - b.originalPosition;
                } else {
                    return result;
                }
            });
            //GenericSorter.quickSort(0, inputList.size(), sortable);
        } catch (ClassCastException e) {
            XPathException err = new XPathException("Non-comparable types found while sorting: " + e.getMessage());
            err.setErrorCode("XPTY0004");
            throw err;
        }
        List<Item> outputList = new ArrayList<>(inputList.size());
        for (ItemToBeSorted member : inputList) {
            outputList.add(member.value);
        }
        return new SequenceExtent(outputList);
    }


}
