////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.functions.DeepEqual;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceExtent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.sf.saxon.expr.flwor.Clause.ClauseName.GROUP_BY;

/**
 * This class represents an "group by" clause in a FLWOR expression
 */
public class GroupByClause extends Clause {

    Configuration config;
    LocalVariableBinding[] bindings;          // Variables bound in the output tuple stream.
    // There is one for each grouping variable, then one for each non-grouping variable
    GenericAtomicComparer[] comparers;        // One comparer per grouping variable

    Operand retainedTupleOp;
    Operand groupingTupleOp;
    //TupleExpression retainedTupleExpression;  // variables declared in the FLWOR expression other than grouping variables
    //TupleExpression groupingTupleExpression;  // variables listed in the group by clause

    /**
     * Create a group-by clause
     *
     * @param config the Saxon configuration
     */

    public GroupByClause(Configuration config) {
        this.config = config;
    }

    @Override
    public ClauseName getClauseKey() {
        return GROUP_BY;
    }

    @Override
    public boolean containsNonInlineableVariableReference(Binding binding) {
        return getRetainedTupleExpression().includesBinding(binding) ||
                getGroupingTupleExpression().includesBinding(binding);
    }


    @Override
    public GroupByClause copy(FLWORExpression flwor, RebindingMap rebindings) {
        GroupByClause g2 = new GroupByClause(config);
        g2.setLocation(getLocation());
        g2.setPackageData(getPackageData());
        g2.bindings = new LocalVariableBinding[bindings.length];
        for (int i = 0; i < bindings.length; i++) {
            g2.bindings[i] = bindings[i].copy();
        }
        g2.comparers = comparers;
        g2.initRetainedTupleExpression(flwor, (TupleExpression)getRetainedTupleExpression().copy(rebindings));
        g2.initGroupingTupleExpression(flwor, (TupleExpression)getGroupingTupleExpression().copy(rebindings));
        return g2;
    }

    /**
     * Initialize a tuple expression that evaluates all the non-grouping variables, returning the values these variables
     * take in the grouping input stream
     * @param flwor the containing FLWORExpression
     * @param expr the tuple expression
     */

    public void initRetainedTupleExpression(FLWORExpression flwor, TupleExpression expr) {
        retainedTupleOp = new Operand(flwor, expr, OperandRole.FLWOR_TUPLE_CONSTRAINED);
    }

    /**
     * Set a tuple expression that evaluates all the non-grouping variables, returning the values these variables take
     * in the grouping input stream
     *
     * @param expr the tuple expression
     */

    public void setRetainedTupleExpression(TupleExpression expr) {
        retainedTupleOp.setChildExpression(expr);
    }

    /**
     * Get the tuple expression that evaluates all the non-grouping variables, returning the values these variables
     * take in the grouping input stream
     *
     * @return the tuple expression
     */

    public TupleExpression getRetainedTupleExpression() {
        return (TupleExpression)retainedTupleOp.getChildExpression();
    }

    @Override
    public void optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        ArrayList<LocalVariableBinding> list = new ArrayList<>(Arrays.asList(bindings));
        ArrayList<LocalVariableReference> retainingExpr = new ArrayList<>();
        for (Operand o : getRetainedTupleExpression().operands()) {
            retainingExpr.add((LocalVariableReference)o.getChildExpression());
        }

        int groupingSize = getGroupingTupleExpression().getSize();
        for (int i = list.size() - 1; i >= groupingSize; i--) {
            if (list.get(i).getNominalReferenceCount() == 0) {
                list.remove(i);
                retainingExpr.remove(i - groupingSize);
            }
        }
        bindings = list.toArray(new LocalVariableBinding[0]);
        getRetainedTupleExpression().setVariables(retainingExpr);
    }

    /**
     * Initialize a tuple expression that evaluates all the grouping variables, returning the values these variables
     * take in the input stream
     * @param flwor the containing FLWORExpression
     * @param expr the tuple expression
     */

    public void initGroupingTupleExpression(FLWORExpression flwor, TupleExpression expr) {
        groupingTupleOp = new Operand(flwor, expr, OperandRole.FLWOR_TUPLE_CONSTRAINED);
    }

    /**
     * Set a tuple expression that evaluates all the grouping variables, returning the values these variables
     * take in the input stream
     *
     * @param expr the tuple expression
     */

    public void setGroupingTupleExpression(TupleExpression expr) {
        groupingTupleOp.setChildExpression(expr);
    }

    /**
     * Get the tuple expression that evaluates all the grouping variables, returning the values these variables
     * take in the input stream
     *
     * @return the tuple expression
     */

    public TupleExpression getGroupingTupleExpression() {
        return (TupleExpression)groupingTupleOp.getChildExpression();
    }

    /**
     * Set the bindings of new variables created by the grouping clause, which constitute the variables
     * appearing in the output (post-grouping) tuple stream. There will be one of these for each variable
     * in the input (pre-grouping) stream; by convention the bindings for grouping variables precede the
     * bindings for non-grouping (retained) variables, and the order is preserved.
     *
     * @param bindings the bindings of the variables created in the output stream
     */

    public void setVariableBindings(LocalVariableBinding[] bindings) {
        this.bindings = bindings;
    }

    /**
     * Get the variables bound by this clause
     *
     * @return the variable bindings
     */

    @Override
    public LocalVariableBinding[] getRangeVariables() {
        return bindings;
    }

    /**
     * Set the comparers used for the grouping keys. There is one comparer for each grouping variable
     *
     * @param comparers the comparers for grouping keys.
     */

    public void setComparers(GenericAtomicComparer[] comparers) {
        this.comparers = comparers;
    }

    /**
     * Get a tuple stream that implements the functionality of this clause, taking its
     * input from another tuple stream which this clause modifies
     *
     * @param base    the input tuple stream
     * @param context the XPath dynamic evaluation context
     * @return the output tuple stream
     */
    @Override
    public TuplePull getPullStream(TuplePull base, XPathContext context) {
        return new GroupByClausePull(base, this, context);
    }

    /**
     * Get a push-mode tuple stream that implements the functionality of this clause, supplying its
     * output to another tuple stream
     *
     * @param destination the output tuple stream
     * @param output the destination for the result
     * @param context the dynamic evaluation context
     * @return the push tuple stream that implements the functionality of this clause of the FLWOR
     *         expression
     */
    @Override
    public TuplePush getPushStream(TuplePush destination, Outputter output, XPathContext context) {
        return new GroupByClausePush(output, destination, this, context);
    }

    /**
     * Process the subexpressions of this clause
     *
     * @param processor the expression processor used to process the subexpressions
     */
    @Override
    public void processOperands(OperandProcessor processor) throws XPathException {
        processor.processOperand(groupingTupleOp);
        processor.processOperand(retainedTupleOp);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void explain(ExpressionPresenter out) {
        out.startElement("group-by");
        for (Operand o : getRetainedTupleExpression().operands()) {
            LocalVariableReference ref = (LocalVariableReference)o.getChildExpression();
            out.startSubsidiaryElement("by");
            out.emitAttribute("var", ref.getDisplayName());
            out.emitAttribute("slot", ref.getBinding().getLocalSlotNumber() + "");
            out.endSubsidiaryElement();
        }
        out.endElement();
    }

    public String toString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);
        fsb.append("group by ... ");
        return fsb.toString();
    }

    /**
     * Inner class representing the contents of a tuple from the pre-grouping tuple stream;
     * a set of such objects consitutes a group. The tuple from the input stream is represented
     * as two Tuple objects, one holding the grouping key values and one holding everything else.
     */

    public static class ObjectToBeGrouped {
        public Tuple groupingValues;
        public Tuple retainedValues;
    }


    /**
     * Process a group of tuples from the input stream to generate a single tuple in the output stream.
     * This method takes a group of tuples as input, and sets all the required variables in the local
     * stack frame as required to deliver this group as the current tuple in the post-grouping stream
     *
     * @param group   the group of input tuples
     * @param context the XPath dynamic evaluation context
     * @throws XPathException if a dynamic error occurs
     */

    public void processGroup(List<ObjectToBeGrouped> group, XPathContext context) throws XPathException {
        LocalVariableBinding[] bindings = getRangeVariables();
        Sequence[] groupingValues = group.get(0).groupingValues.getMembers();
        for (int j = 0; j < groupingValues.length; j++) {
            Sequence v = groupingValues[j];
            context.setLocalVariable(bindings[j].getLocalSlotNumber(), v);
        }
        for (int j = groupingValues.length; j < bindings.length; j++) {
            List<Item> concatenatedValue = new ArrayList<>();
            for (ObjectToBeGrouped otbg : group) {
                Sequence val = otbg.retainedValues.getMembers()[j - groupingValues.length];
                SequenceIterator si = val.iterate();
                Item it;
                while ((it = si.next()) != null) {
                    concatenatedValue.add(it);
                }
            }
            SequenceExtent se = new SequenceExtent(concatenatedValue);
            context.setLocalVariable(bindings[j].getLocalSlotNumber(), se);
        }
    }


    /**
     * Callback to get the comparison key for a tuple. Two tuples are equal if their comparison
     * keys compare equal using the equals() method.
     * @param t the tuple whose comparison key is required
     * @return a comparison key suitable for comparing with other tuples
     */

    public TupleComparisonKey getComparisonKey(Tuple t, GenericAtomicComparer[] comparers) {
        return new TupleComparisonKey(t.getMembers(), comparers);
    }

    @Override
    public void addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        throw new UnsupportedOperationException("Cannot use document projection with group-by");
    }

    /**
     * Inner class representing a tuple comparison key: that is, an arbitrary object whose equals() and hashCode()
     * methods can be used to test whether two tuples have equivalent grouping keys
     */

    public class TupleComparisonKey {

        // Note: this is over-engineered. Each grouping value is required to be either a single atomic
        // value or an empty sequence.

        private Sequence[] groupingValues;
        private GenericAtomicComparer[] comparers;

        public TupleComparisonKey(Sequence[] groupingValues, GenericAtomicComparer[] comparers) {
            this.groupingValues = groupingValues;
            this.comparers = comparers;
        }

        public int hashCode() {
            int h = 0x77557755 ^ groupingValues.length;
            for (int i = 0; i < groupingValues.length; i++) {
                GenericAtomicComparer comparer = comparers[i];
                int implicitTimezone = comparer.getContext().getImplicitTimezone();
                try {
                    SequenceIterator atoms = groupingValues[i].iterate();
                    while (true) {
                        AtomicValue val = (AtomicValue) atoms.next();
                        if (val == null) {
                            break;
                        }
                        h ^= i + val.getXPathComparable(false, comparer.getCollator(), implicitTimezone).hashCode();
                    }
                } catch (XPathException e) {
                    // ignore any errors
                }
            }
            return h;
        }

        public boolean equals(Object other) {
            if (!(other instanceof TupleComparisonKey)) {
                return false;
            }
            if (groupingValues.length != ((TupleComparisonKey) other).groupingValues.length) {
                return false;
            }
            for (int i = 0; i < groupingValues.length; i++) {
                try {
                    if (!DeepEqual.deepEqual(
                        groupingValues[i].iterate(),
                        ((TupleComparisonKey) other).groupingValues[i].iterate(),
                        comparers[i], comparers[i].getContext(), 0)) {
                        return false;
                    }
                } catch (XPathException e) {
                    return false;
                }
            }
            return true;
        }
    }

}

// Copyright (c) 2011-2020 Saxonica Limited
