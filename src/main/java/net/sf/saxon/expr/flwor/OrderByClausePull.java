////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.ItemToBeSorted;
import net.sf.saxon.expr.sort.SortKeyDefinitionList;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceException;

import java.util.ArrayList;

/**
 * Represents the tuple stream delivered by an "order by" clause. This sorts the tuple stream supplied
 * as its input, and outputs the same tuples but in sorted order.
 */
public class OrderByClausePull extends TuplePull {

    private final TuplePull base;
    private final OrderByClause orderByClause;
    private final TupleExpression tupleExpr;
    private int currentPosition = -1;
    private final AtomicComparer[] comparers;
    private final ArrayList<ItemToBeSorted> tupleArray = new ArrayList<ItemToBeSorted>(100);

    public OrderByClausePull(TuplePull base, TupleExpression tupleExpr, OrderByClause orderBy, XPathContext context) {
        this.base = base;
        this.tupleExpr = tupleExpr;
        this.orderByClause = orderBy;

        AtomicComparer[] suppliedComparers = orderBy.getAtomicComparers();
        comparers = new AtomicComparer[suppliedComparers.length];
        for (int n = 0; n < comparers.length; n++) {
            this.comparers[n] = suppliedComparers[n].provideContext(context);
        }
    }

    /**
     * Move on to the next tuple. Before returning, this method must set all the variables corresponding
     * to the "returned" tuple in the local stack frame associated with the context object
     *
     * @param context the dynamic evaluation context
     * @return true if another tuple has been generated; false if the tuple stream is exhausted. If the
     *         method returns false, the values of the local variables corresponding to this tuple stream
     *         are undefined.
     */
    @Override
    @CSharpReplaceException(from="net.sf.saxon.transpile.CSharpUncheckedException", to="System.InvalidOperationException")
    public boolean nextTuple(XPathContext context) throws XPathException {
        if (currentPosition < 0) {
            currentPosition = 0;
            int position = 0;

            while (base.nextTuple(context)) {
                FlworTuple<Sequence> tuple = tupleExpr.evaluateItem(context);
                SortKeyDefinitionList sortKeyDefinitions = orderByClause.getSortKeyDefinitions();
                ItemToBeSorted itbs = new ItemToBeSorted(sortKeyDefinitions.size());
                itbs.value = tuple;
                for (int i = 0; i < sortKeyDefinitions.size(); i++) {
                    itbs.sortKeyValues[i] = orderByClause.evaluateSortKey(i, context);
                }
                itbs.originalPosition = ++position;
                tupleArray.add(itbs);
            }

            try {
                tupleArray.sort((a, b) -> {
                    try {
                        for (int i = 0; i < comparers.length; i++) {
                            int comp = comparers[i].compareAtomicValues(
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
                });
            } catch (ClassCastException e) {
                throw new XPathException("Non-comparable types found while sorting: " + e.getMessage())
                        .withErrorCode("XPTY0004").asTypeError();
            }
        }

        if (currentPosition < tupleArray.size()) {
            tupleExpr.setCurrentTuple(context, (FlworTuple<Sequence>) tupleArray.get(currentPosition++).value);
            return true;
        } else {
            return false;
        }

    }

    /**
     * Close the tuple stream, indicating that although not all tuples have been read,
     * no further tuples are required and resources can be released
     */
    @Override
    public void close() {
        base.close();
    }
}

