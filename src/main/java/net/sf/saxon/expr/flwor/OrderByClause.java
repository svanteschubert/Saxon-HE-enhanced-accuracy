////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.SortKeyDefinition;
import net.sf.saxon.expr.sort.SortKeyDefinitionList;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;

import static net.sf.saxon.expr.flwor.Clause.ClauseName.ORDER_BY;

/**
 * This class represents an "order by" clause in a FLWOR expression
 */
public class OrderByClause extends Clause {

    public final static OperandRole SORT_KEYS_ROLE =
            new OperandRole(OperandRole.HIGHER_ORDER | OperandRole.CONSTRAINED_CLASS,
                            OperandUsage.NAVIGATION,
                            SequenceType.ANY_SEQUENCE,
                            expr -> expr instanceof SortKeyDefinitionList);

    Operand sortKeysOp; // Holds a SortKeyDefinitionList
    AtomicComparer[] comparators;
    Operand tupleOp; // Holds a TupleExpression

    public OrderByClause(FLWORExpression flwor, SortKeyDefinition[] sortKeys, TupleExpression tupleExpression) {
        this.sortKeysOp = new Operand(flwor, new SortKeyDefinitionList(sortKeys), SORT_KEYS_ROLE);
        this.tupleOp = new Operand(flwor, tupleExpression, OperandRole.FLWOR_TUPLE_CONSTRAINED);
    }

    @Override
    public ClauseName getClauseKey() {
        return ORDER_BY;
    }

    @Override
    public boolean containsNonInlineableVariableReference(Binding binding) {
        return getTupleExpression().includesBinding(binding);
    }

    @Override
    public OrderByClause copy(FLWORExpression flwor, RebindingMap rebindings) {
        SortKeyDefinitionList sortKeys = getSortKeyDefinitions();
        SortKeyDefinition[] sk2 = new SortKeyDefinition[sortKeys.size()];
        for (int i = 0; i < sortKeys.size(); i++) {
            sk2[i] = sortKeys.getSortKeyDefinition(i).copy(rebindings);
        }
        OrderByClause obc = new OrderByClause(flwor, sk2, (TupleExpression) getTupleExpression().copy(rebindings));
        obc.setLocation(getLocation());
        obc.setPackageData(getPackageData());
        obc.comparators = comparators;
        return obc;
    }

    public SortKeyDefinitionList getSortKeyDefinitions() {
        return (SortKeyDefinitionList)sortKeysOp.getChildExpression();
    }

    public AtomicComparer[] getAtomicComparers() {
        return comparators;
    }

    public TupleExpression getTupleExpression() {
        return (TupleExpression)tupleOp.getChildExpression();
    }

    /**
     * Get a tuple stream that implements the functionality of this clause, taking its
     * input from another tuple stream which this clause modifies
     *
     * @param base    the input tuple stream
     * @param context XQuery dynamic context
     * @return the output tuple stream
     */
    @Override
    public TuplePull getPullStream(TuplePull base, XPathContext context) {
        return new OrderByClausePull(base, getTupleExpression(), this, context);
    }

    /**
     * Get a push-mode tuple stream that implements the functionality of this clause, supplying its
     * output to another tuple stream
     *
     * @param destination the output tuple stream
     * @param output the destination for the result
     * @param context     XQuery dynamic context
     * @return the push tuple stream that implements the functionality of this clause of the FLWOR
     *         expression
     */
    @Override
    public TuplePush getPushStream(TuplePush destination, Outputter output, XPathContext context) {
        return new OrderByClausePush(output, destination, getTupleExpression(), this, context);
    }

    /**
     * Process the subexpressions of this clause
     *
     * @param processor the expression processor used to process the subexpressions
     */
    @Override
    public void processOperands(OperandProcessor processor) throws XPathException {
        processor.processOperand(tupleOp);
        processor.processOperand(sortKeysOp);
//        for (SortKeyDefinition sortKey : sortKeys) {
//            sortKey.processSubExpressions(processor);
//        }
    }

    /**
     * Type-check the expression
     */

    @Override
    public void typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        boolean allKeysFixed = true;
        SortKeyDefinitionList sortKeys = getSortKeyDefinitions();
        for (SortKeyDefinition sk : sortKeys) {
            if (!sk.isFixed()) {
                allKeysFixed = false;
                break;
            }
        }

        if (allKeysFixed) {
            comparators = new AtomicComparer[sortKeys.size()];
        }

        int i=0;
        TypeChecker tc = visitor.getConfiguration().getTypeChecker(false);
        for (SortKeyDefinition skd : sortKeys) {
            Expression sortKey = skd.getSortKey();
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.ORDER_BY, "", i);
            role.setErrorCode("XPTY0004");
            sortKey = tc.staticTypeCheck(sortKey, SequenceType.OPTIONAL_ATOMIC, role, visitor);
            skd.setSortKey(sortKey, false);
            skd.typeCheck(visitor, contextInfo);
            if (skd.isFixed()) {
                AtomicComparer comp = skd.makeComparator(
                        visitor.getStaticContext().makeEarlyEvaluationContext());
                skd.setFinalComparator(comp);
                if (allKeysFixed) {
                    comparators[i] = comp;
                }
            }
            i++;
        }
    }

    @Override
    public void addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        SortKeyDefinitionList sortKeys = getSortKeyDefinitions();
        for (SortKeyDefinition skd : sortKeys) {
            Expression sortKey = skd.getSortKey();
            sortKey.addToPathMap(pathMap, pathMapNodeSet);

        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void explain(ExpressionPresenter out) throws XPathException {
        out.startElement("order-by");
        for (SortKeyDefinition k : getSortKeyDefinitions()) {
            out.startSubsidiaryElement("key");
            k.getSortKey().export(out);
            out.endSubsidiaryElement();
        }
        out.endElement();
    }

    public String toString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);
        fsb.append("order by ... ");
        return fsb.toString();
    }

    /**
     * Callback for evaluating the sort keys
     *
     * @param n identifies the sort key to be evaluated
     * @param c the dynamic context for evaluation of the sort key
     * @return the value of the sort key
     * @throws XPathException if evaluation of the sort key fails
     */

    /*@Nullable*/
    public AtomicValue evaluateSortKey(int n, XPathContext c) throws XPathException {
        SortKeyDefinitionList sortKeys = getSortKeyDefinitions();
        return (AtomicValue) sortKeys.getSortKeyDefinition(n).getSortKey().evaluateItem(c);
    }

}

