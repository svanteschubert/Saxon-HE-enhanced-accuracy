////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Class to do a sorted iteration
 */

public class SortedIterator implements SequenceIterator, LastPositionFinder, LookaheadIterator {

    // the items to be sorted
    private final SequenceIterator base;

    // the call-back function used to evaluate sort keys
    protected SortKeyEvaluator sortKeyEvaluator;

    // the comparators corresponding to these sort keys
    protected AtomicComparer[] comparators;

    // The items and keys are read into an array (nodeKeys) for sorting. This
    // array contains one "record" representing each node: the "record" contains
    // first, the Item itself, then an entry for each of its sort keys, in turn;
    // the last sort key is the position of the Item in the original sequence.
    protected ObjectToBeSorted[] values;

    // The number of items to be sorted. -1 means not yet known.
    protected int count = -1;

    // The next item to be delivered from the sorted iteration
    protected int position = 0;

    // The context for the evaluation of sort keys
    protected final XPathContext context;

    // The host language (XSLT, XQuery, XPath). Used only to decide which error code to use on dynamic errors.
    private HostLanguage hostLanguage;

    protected SequenceIterator getBaseIterator() {
        return base;
    }

    /**
     * Create a sorted iterator
     *
     * @param context          the dynamic XPath evaluation context
     * @param base             an iterator over the sequence to be sorted
     * @param sortKeyEvaluator an object that allows the n'th sort key for a given item to be evaluated
     * @param comparators      an array of AtomicComparers, one for each sort key, for comparing sort key values
     * @param createNewContext true if sort keys are computed relative to the item being sorted (as in XSLT but not XQuery)
     */

    public SortedIterator(XPathContext context, SequenceIterator base,
                          SortKeyEvaluator sortKeyEvaluator, AtomicComparer[] comparators, boolean createNewContext) {

        if (createNewContext) {
            this.context = context.newMinorContext();
            this.context.setTemporaryOutputState(StandardNames.XSL_SORT);
            this.base = this.context.trackFocus(base);
        } else {
            this.context = context;
            this.base = base;
        }
        this.sortKeyEvaluator = sortKeyEvaluator;
        this.comparators = new AtomicComparer[comparators.length];
        for (int n = 0; n < comparators.length; n++) {
            this.comparators[n] = comparators[n].provideContext(context);
        }

        // Avoid doing the sort until the user wants the first item. This is because
        // sometimes the user only wants to know whether the collection is empty.
    }

    /**
     * Set the host language
     *
     * @param language the host language (for example {@link HostLanguage#XQUERY})
     */

    public void setHostLanguage(HostLanguage language) {
        hostLanguage = language;
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    /**
     * Determine whether there are more items to come. Note that this operation
     * is stateless, and it is not necessary (or usual) to call it before calling
     * next(). It is used only when there is an explicit need to tell if we
     * are at the last element.
     * <p>This method must not be called unless the method {@link #supportsHasNext()} returns true.</p>
     *
     * @return true if there are more items in the sequence
     */

    @Override
    public boolean hasNext() {
        if (position < 0) {
            return false;
        }
        if (count < 0) {
            // haven't started sorting yet
            if (base instanceof LookaheadIterator && ((LookaheadIterator)base).supportsHasNext()) {
                return ((LookaheadIterator) base).hasNext();
            } else {
                try {
                    doSort();
                    return count > 0;
                } catch (XPathException err) {
                    throw new UncheckedXPathException(err);
                }
            }
        } else {
            return position < count;
        }
    }

    /**
     * Get the next item, in sorted order
     */

    /*@Nullable*/
    @Override
    public Item next() {
        if (position < 0) {
            return null;
        }
        if (count < 0) {
            try {
                doSort();
            } catch (XPathException e) {
                throw new UncheckedXPathException(e);
            }
        }
        if (position < count) {
            return (Item) values[position++].value;
        } else {
            position = -1;
            return null;
        }
    }

    /**
     * Ask whether this iterator supports use of the {@link #getLength()} method. This
     * method should always be called before calling {@link #getLength()}, because an iterator
     * that implements this interface may support use of {@link #getLength()} in some situations
     * and not in others
     *
     * @return true if the {@link #getLength()} method can be called to determine the length
     * of the underlying sequence.
     */
    @Override
    public boolean supportsGetLength() {
        return true;
    }

    @Override
    public int getLength() {
        if (count < 0) {
            try {
                doSort();
            } catch (XPathException e) {
                throw new UncheckedXPathException(e);
            }
        }
        return count;
    }

    /**
     * Create an array holding the items to be sorted and the values of their sort keys
     *
     * @throws XPathException if an error occurs for example in evaluating a sort key
     */

    protected void buildArray() throws XPathException {
        int allocated = SequenceTool.supportsGetLength(base) ? SequenceTool.getLength(base) : 100;
        values = new ItemToBeSorted[allocated];
        count = 0;

        // initialise the array with data

        Item item;
        while ((item = base.next()) != null) {
            if (count == allocated) {
                allocated *= 2;
                ObjectToBeSorted[] nk2 = new ObjectToBeSorted[allocated];
                System.arraycopy(values, 0, nk2, 0, count);
                values = nk2;
            }
            ItemToBeSorted itbs = new ItemToBeSorted(comparators.length);
            values[count] = itbs;
            itbs.value = item;
            // TODO: delay evaluating the sort keys until we know they are needed. Often the 2nd and subsequent
            // sort key values will never be used. The only problem is with sort keys that depend on position().
            for (int n = 0; n < comparators.length; n++) {
                itbs.sortKeyValues[n] = sortKeyEvaluator.evaluateSortKey(n, context);
            }
            // make the sort stable by adding the record number
            itbs.originalPosition = count++;
        }

        // If there's lots of unused space, reclaim it

        if (allocated * 2 < count || (allocated - count) > 2000) {
            ObjectToBeSorted[] nk2 = new ObjectToBeSorted[count];
            System.arraycopy(values, 0, nk2, 0, count);
            values = nk2;
        }
    }


    private static class SortComparer implements Comparator<ObjectToBeSorted> {

        private final AtomicComparer[] comparators;

        public SortComparer(AtomicComparer[] comparators) {
            this.comparators = comparators;
        }
        @Override
        public int compare(ObjectToBeSorted a, ObjectToBeSorted b) {
            try {
                for (int i = 0; i < comparators.length; i++) {
                    int comp = comparators[i].compareAtomicValues(
                            a.sortKeyValues[i], b.sortKeyValues[i]);
                    if (comp != 0) {
                        // we have found a difference, so we can return
                        return comp;
                    }
                }
            } catch (NoDynamicContextException e) {
                throw new AssertionError("Sorting without dynamic context: " + e.getMessage());
            }
            // all sort keys equal: return the items in their original order
            // TODO: unnecessary, we are now using a stable sort routine
            return a.originalPosition - b.originalPosition;
        }


    }

    private void doSort() throws XPathException {
        buildArray();
        if (count < 2) {
            return;
        }

        // sort the array

        try {
            Arrays.sort(values, 0, count, new SortComparer(comparators));
        } catch (ClassCastException e) {
            throw new XPathException("Non-comparable types found while sorting: " + e.getMessage())
                    .withErrorCode(hostLanguage == HostLanguage.XSLT ? "XTDE1030" : "XPTY0004");
        }
    }

}

