////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import com.saxonica.ee.stream.ManualGroupIterator;
import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;

import java.util.Arrays;

/**
 * A SortedGroupIterator is a modified SortedIterator. It sorts a sequence of groups,
 * and is itself a GroupIterator. The modifications retain extra information about
 * the items being sorted. The items are each the leading item of a group, and as well
 * as the item itself, the iterator preserves information about the group: specifically,
 * an iterator over the items in the group, and the value of the grouping key (if any).
 */

public class SortedGroupIterator extends SortedIterator implements GroupIterator {

    public SortedGroupIterator(XPathContext context,
                               GroupIterator base,
                               SortKeyEvaluator sortKeyEvaluator,
                               AtomicComparer[] comparators
    ) {
        super(context, base, sortKeyEvaluator, comparators, true);
        setHostLanguage(HostLanguage.XSLT);
    }

    /**
     * Override the method that builds the array of values and sort keys.
     *
     * @throws XPathException in the event of a dynamic error
     */

    @Override
    protected void buildArray() throws XPathException {
        int allocated;
        if (base.getProperties().contains(Property.LAST_POSITION_FINDER)) {
            allocated = ((LastPositionFinder) base).getLength();
        } else {
            allocated = 100;
        }

        values = new GroupToBeSorted[allocated];
        count = 0;

        XPathContextMajor c2 = context.newContext();
        c2.setCurrentIterator((FocusIterator)base);
        GroupIterator groupIter = (GroupIterator)((FocusTrackingIterator)base).getUnderlyingIterator();
        c2.setCurrentGroupIterator(groupIter);

        // initialise the array with data

        Item item;
        while ((item = base.next()) != null) {
            if (count == allocated) {
                allocated *= 2;
                values = Arrays.copyOf(values, allocated);
            }
            GroupToBeSorted gtbs = new GroupToBeSorted(comparators.length);
            values[count] = gtbs;
            gtbs.value = item;
            for (int n = 0; n < comparators.length; n++) {
                gtbs.sortKeyValues[n] = sortKeyEvaluator.evaluateSortKey(n, c2);
            }
            gtbs.originalPosition = count++;
            gtbs.currentGroupingKey = groupIter.getCurrentGroupingKey();
            gtbs.currentGroup = new MemoSequence(groupIter.iterateCurrentGroup());
        }
    }

    /*@Nullable*/
    @Override
    public AtomicSequence getCurrentGroupingKey() {
        return ((GroupToBeSorted) values[position - 1]).currentGroupingKey;
    }

    @Override
    public SequenceIterator iterateCurrentGroup() throws XPathException {
        return ((GroupToBeSorted) values[position - 1]).currentGroup.iterate();
    }


}

