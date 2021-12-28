////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.Atomizer;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Represents the tuple stream delivered by an "group by" clause. This groups the tuple stream supplied
 * as its input, and outputs a new set of tuples one per group of the input tuples. No groups are output
 * until all the groups have been read.
 */
public class GroupByClausePush extends TuplePush {

    private TuplePush destination;
    private GroupByClause groupByClause;
    private HashMap<Object, List<GroupByClause.ObjectToBeGrouped>> map = new HashMap<>();
    private XPathContext context;
    private GenericAtomicComparer[] comparers;

    public GroupByClausePush(Outputter outputter, TuplePush destination, GroupByClause groupBy, XPathContext context) {
        super(outputter);
        this.destination = destination;
        this.groupByClause = groupBy;
        this.context = context;
        comparers = new GenericAtomicComparer[groupBy.comparers.length];
        for (int i=0; i<comparers.length; i++) {
            comparers[i] = groupBy.comparers[i].provideContext(context);
        }
    }

    /**
     * Move on to the next tuple. Before returning, this method must set all the variables corresponding
     * to the "returned" tuple in the local stack frame associated with the context object
     *
     * @param context the dynamic evaluation context
     */
    @Override
    public void processTuple(XPathContext context) throws XPathException {

        // Allocate the incoming tuple to a group

        TupleExpression groupingTupleExpr = groupByClause.getGroupingTupleExpression();
        TupleExpression retainedTupleExpr = groupByClause.getRetainedTupleExpression();


        GroupByClause.ObjectToBeGrouped otbg = new GroupByClause.ObjectToBeGrouped();
        Sequence[] groupingValues = groupingTupleExpr.evaluateItem(context).getMembers();
        checkGroupingValues(groupingValues);
        otbg.groupingValues = new Tuple(groupingValues);
        otbg.retainedValues = retainedTupleExpr.evaluateItem(context);
        Object key = groupByClause.getComparisonKey(otbg.groupingValues, comparers);
        List<GroupByClause.ObjectToBeGrouped> group = map.get(key);
        addToGroup(key, otbg, group, map);
    }

    protected static void addToGroup(
            Object key,
            GroupByClause.ObjectToBeGrouped objectToBeGrouped,
            List<GroupByClause.ObjectToBeGrouped> group,
            HashMap<Object, List<GroupByClause.ObjectToBeGrouped>> map) {
        if (group != null) {
            group.add(objectToBeGrouped);
            map.put(key, group);
        } else {
            List<GroupByClause.ObjectToBeGrouped> list = new ArrayList<>();
            list.add(objectToBeGrouped);
            map.put(key, list);
        }
    }

    protected static void checkGroupingValues(Sequence[] groupingValues) throws XPathException {
        for (int i = 0; i < groupingValues.length; i++) {
            Sequence v = groupingValues[i];
            if (!(v instanceof EmptySequence || v instanceof AtomicValue)) {
                v = Atomizer.getAtomizingIterator(v.iterate(), false).materialize();
                if (SequenceTool.getLength(v) > 1) {
                    throw new XPathException("Grouping key value cannot be a sequence of more than one item", "XPTY0004");
                }
                groupingValues[i] = v;
            }
        }
    }


    /**
     * Close the tuple stream, indicating that although not all tuples have been read,
     * no further tuples are required and resources can be released
     */

    @Override
    public void close() throws XPathException {

        for (List<GroupByClause.ObjectToBeGrouped> group : map.values()) {
            groupByClause.processGroup(group, context);
            destination.processTuple(context);
        }

        destination.close();
    }


}

// Copyright (c) 2011-2020 Saxonica Limited
