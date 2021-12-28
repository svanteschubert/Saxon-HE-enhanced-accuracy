////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Represents the tuple stream delivered by an "group by" clause. This groups the tuple stream supplied
 * as its input, and outputs a new set of tuples one per group of the input tuples. No groups are output
 * until all the groups have been read.
 */
public class GroupByClausePull extends TuplePull {

    private TuplePull base;
    private GroupByClause groupByClause;
    /*@Nullable*/ Iterator<List<GroupByClause.ObjectToBeGrouped>> groupIterator;
    private GenericAtomicComparer[] comparers;


    public GroupByClausePull(TuplePull base, GroupByClause groupBy, XPathContext context) {
        this.base = base;
        this.groupByClause = groupBy;
        comparers = new GenericAtomicComparer[groupBy.comparers.length];
        for (int i = 0; i < comparers.length; i++) {
            comparers[i] = groupBy.comparers[i].provideContext(context);
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
    public boolean nextTuple(XPathContext context) throws XPathException {
        if (groupIterator == null) {

            // First do the grouping

            TupleExpression groupingTupleExpr = groupByClause.getGroupingTupleExpression();
            TupleExpression retainedTupleExpr = groupByClause.getRetainedTupleExpression();
            HashMap<Object, List<GroupByClause.ObjectToBeGrouped>> map = new HashMap<>();
            while (base.nextTuple(context)) {
                GroupByClause.ObjectToBeGrouped otbg = new GroupByClause.ObjectToBeGrouped();
                Sequence[] groupingValues = groupingTupleExpr.evaluateItem(context).getMembers();
                GroupByClausePush.checkGroupingValues(groupingValues);
                otbg.groupingValues = new Tuple(groupingValues);
                otbg.retainedValues = retainedTupleExpr.evaluateItem(context);
                Object key = groupByClause.getComparisonKey(otbg.groupingValues, comparers);
                List<GroupByClause.ObjectToBeGrouped> group = map.get(key);
                GroupByClausePush.addToGroup(key, otbg, group, map);
            }
            // get an iterator over the groups

            groupIterator = map.values().iterator();

        }

        if (groupIterator.hasNext()) {
            List<GroupByClause.ObjectToBeGrouped> group = groupIterator.next();
            groupByClause.processGroup(group, context);
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
        groupIterator = null;
    }

}

// Copyright (c) 2011-2020 Saxonica Limited


