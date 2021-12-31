////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.accum;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.Evaluator;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.tree.util.Navigator;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the values of an accumulator function for one non-streamed document
 */
public class AccumulatorData implements IAccumulatorData {

    private Accumulator accumulator;
    private List<DataPoint> values = new ArrayList<>();
    private boolean building = false;

    public AccumulatorData(Accumulator acc) {
        this.accumulator = acc;
    }

    /**
     * Get the associated accumulator
     * @return the accumulator
     */

    @Override
    public Accumulator getAccumulator() {
        return accumulator;
    }

    /**
     * Build a data structure containing the values of the accumulator for each node in a document.
     * The data structure holds the value for all nodes where the value changes; the value for other
     * nodes is obtained by interpolation
     *
     * @param doc     the root of the tree for which the accumulator is to be evaluated
     * @param context the dynamic evaluation context
     * @throws XPathException if a dynamic error occurs while evaluating the accumulator
     */

    public void buildIndex(NodeInfo doc, XPathContext context) throws XPathException {
        //System.err.println("Building accData " + this);
        if (building) {
            throw new XPathException("Accumulator " + accumulator.getAccumulatorName().getDisplayName() +
                " requires access to its own value", "XTDE3400");
        }
        building = true;
        Expression initialValue = accumulator.getInitialValueExpression();
        XPathContextMajor c2 = context.newContext();
        SlotManager sf = accumulator.getSlotManagerForInitialValueExpression();
        Sequence[] slots = new Sequence[sf.getNumberOfVariables()];
        c2.setStackFrame(sf, slots);
        c2.setCurrentIterator(new ManualIterator(doc));
        Sequence val = initialValue.iterate(c2).materialize();
        values.add(new DataPoint(new Visit(doc, false), val));
        val = visit(doc, val, c2);
        values.add(new DataPoint(new Visit(doc, true), val));
        ((ArrayList) values).trimToSize();
        building = false;
        //diagnosticPrint();
    }

    /*
     * Diagnostic output of the entire data structure
     */

//    public void diagnosticPrint() {
//        for (DataPoint dp : values) {
//            System.err.println((dp.visit.isPostDescent ? "B:" : "A:") + Navigator.getPath(dp.visit.node) + " = " + dp.value);
//        }
//    }

    /**
     * Recursive routine to evaluate the accumulator for a given node, before and after
     * visiting its descendants
     *
     * @param node    the node to be visited
     * @param value   the value of the accumulator before visiting this node
     * @param context the dynamic evaluation context
     * @return the value of the accumulator after visiting this node
     * @throws XPathException if a dynamic evaluation error occurs
     */

    @SuppressWarnings({"InfiniteRecursion"}) //Spurious warning from IntelliJ
    private Sequence visit(NodeInfo node, Sequence value, XPathContext context) throws XPathException {
        try {
            ((ManualIterator)context.getCurrentIterator()).setContextItem(node);
            Rule rule = accumulator.getPreDescentRules().getRule(node, context);
            if (rule != null) {
                value = processRule(rule, node, false, value, context);
                logChange(node, value, context, " BEFORE ");
            }
            for (NodeInfo kid : node.children()) {
                value = visit(kid, value, context);
            }
            ((ManualIterator) context.getCurrentIterator()).setContextItem(node);
            rule = accumulator.getPostDescentRules().getRule(node, context);
            if (rule != null) {
                value = processRule(rule, node, true, value, context);
                logChange(node, value, context, " AFTER ");
            }
            return value;
        } catch (StackOverflowError e) {
            XPathException err = new XPathException.StackOverflow(
                    "Too many nested accumulator evaluations. The accumulator definition may have cyclic dependencies",
                    "XTDE3400", accumulator);
            err.setXPathContext(context);
            throw err;
        }
    }

    private void logChange(NodeInfo node, Sequence value, XPathContext context, String phase) {
        if (accumulator.isTracing()) {
            context.getConfiguration().getLogger().info(
                    accumulator.getAccumulatorName().getDisplayName()
                            + phase + Navigator.getPath(node) + ": " + Err.depictSequence(value));
        }
    }

    /**
     * Apply an accumulator rule
     *
     * @param rule    the rule to apply
     * @param node    the node that was matched
     * @param isPostDescent false for the pre-descent visit to a node, true for the post-descent visit
     * @param value   the value of the accumulator before applying the rule
     * @param context the dynamic evaluation context
     * @return the value of the accumulator after applying the rule
     * @throws XPathException if a dynamic error occurs during the evaluation
     */

    private Sequence processRule(Rule rule, NodeInfo node, boolean isPostDescent, Sequence value, XPathContext context) throws XPathException {
        AccumulatorRule target = (AccumulatorRule) rule.getAction();
        Expression delta = target.getNewValueExpression();
        XPathContextMajor c2 = context.newCleanContext();
        final Controller controller = c2.getController();
        assert controller != null;
        ManualIterator initialNode = new ManualIterator(node);
        c2.setCurrentIterator(initialNode);
        c2.openStackFrame(target.getStackFrameMap());
        c2.setLocalVariable(0, value);
        c2.setCurrentComponent(accumulator.getDeclaringComponent());
        c2.setTemporaryOutputState(StandardNames.XSL_ACCUMULATOR_RULE);
        value = Evaluator.EAGER_SEQUENCE.evaluate(delta, c2);
        //System.err.println("Node " + ((TinyNodeImpl) node).getNodeNumber() + " : " + value);
        if (node.getParent() == null && !isPostDescent && values.size() == 1) {
            // Overwrite the accumulator's initial value with the "before document start" value. Bug 4786.
            values.clear();
        }
        values.add(new DataPoint(new Visit(node, isPostDescent), value));
        return value;
    }

    /**
     * Get the value of the accumulator for a given node
     *
     * @param node        the node in question
     * @param postDescent false if the pre-descent value of the accumulator is required;
     *                    false if the post-descent value is wanted.
     * @return the value of the accumulator for this node
     */

//    public Sequence getValue(NodeInfo node, boolean postDescent) {
//        Visit visit = new Visit(node, postDescent);
//        return search(0, values.size(), visit);
//    }

    @Override
    public Sequence getValue(NodeInfo node, boolean postDescent) {
        Visit visit = new Visit(node, postDescent);
        return search(0, values.size(), visit);
        //System.err.println("Searched " + values.size() + " " + ((TinyNodeImpl) visit.node).getNodeNumber() + " : " + seq);
    }

    /**
     * Recursive binary chop search for the value applicable to a given node
     *
     * @param start  the start index of the search
     * @param end    the end index of the search
     * @param sought the visit being sought
     * @return the value associated with this node
     */

    private Sequence search(int start, int end, Visit sought) {
        //System.err.println("-- Search " + start + ".." + end);
        if (start == end) {
            // sometimes we want the value for the visit we've found, sometimes for the previous visit
            int rel = sought.compareTo(values.get(start).visit);
            if (rel < 0 /*|| (rel == 0 && sought.isPostDescent)*/) {
                return values.get(start - 1).value;
            } else {
                return values.get(start).value;
            }
        }
        int mid = (start + end) / 2;
        if (sought.compareTo(values.get(mid).visit) <= 0) {
            return search(start, mid, sought);
        } else {
            return search(mid + 1, end, sought);
        }

        // 9.6:
//        int mid = (start + end) / 2;
//        if (sought.compareTo(values.get(mid).visit) <= 0) {
//            return search(start, mid, sought);
//        } else {
//            return search(mid + 1, end, sought);
//        }
    }

    /**
     * Class representing one of the two visits to a node during a tree-walk
     */

    private static class Visit implements Comparable<Visit> {
        public NodeInfo node;
        public boolean isPostDescent;

        public Visit(NodeInfo node, boolean isPostDescent) {
            this.node = node;
            this.isPostDescent = isPostDescent;
        }

        /**
         * Compare the order of two node visits.
         *
         * @param other the other node visit
         * @return -1 if this visit is earlier, 0 if they are the same visit, +1 if this visit is later
         */

        @Override
        public int compareTo(Visit other) {
            int relation = Navigator.comparePosition(node, other.node);
            switch (relation) {
                case AxisInfo.SELF:
                    if (isPostDescent == other.isPostDescent) {
                        return 0;
                    } else {
                        return isPostDescent ? +1 : -1;
                    }
                case AxisInfo.PRECEDING:
                    return -1;
                case AxisInfo.FOLLOWING:
                    return +1;
                case AxisInfo.ANCESTOR:
                    return isPostDescent ? +1 : -1;
                case AxisInfo.DESCENDANT:
                    return other.isPostDescent ? -1 : +1;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    /**
     * Class representing a value of the accumulator immediately after a particular visit to a node.
     */

    private static class DataPoint {
        public Visit visit;
        public Sequence value;

        public DataPoint(Visit visit, Sequence value) {
            this.visit = visit;
            this.value = value;
        }
    }

}
