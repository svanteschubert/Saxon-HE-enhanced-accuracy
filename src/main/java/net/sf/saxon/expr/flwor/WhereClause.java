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
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.ItemType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.sf.saxon.expr.flwor.Clause.ClauseName.WHERE;

/**
 * A "where" clause in a FLWOR expression
 */
public class WhereClause extends Clause {

    private Operand predicateOp;

    public WhereClause(FLWORExpression flwor, Expression predicate) {
        this.predicateOp = new Operand(flwor, predicate, OperandRole.INSPECT);
    }

    @Override
    public void setRepeated(boolean repeated) {
        super.setRepeated(repeated);
        if (repeated) {
            this.predicateOp.setOperandRole(OperandRole.REPEAT_INSPECT);
        }
    }

    @Override
    public ClauseName getClauseKey() {
        return WHERE;
    }

    public Expression getPredicate() {
        return predicateOp.getChildExpression();
    }

    public void setPredicate(Expression predicate) {
        predicateOp.setChildExpression(predicate);
    }

    @Override
    public WhereClause copy(FLWORExpression flwor, RebindingMap rebindings) {
        WhereClause w2 = new WhereClause(flwor, getPredicate().copy(rebindings));
        w2.setLocation(getLocation());
        w2.setPackageData(getPackageData());
        return w2;
    }

    /**
     * Type-check the expression
     */
    @Override
    public void typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        super.typeCheck(visitor, contextInfo);
    }


    /**
     * Get a tuple stream that implements the functionality of this clause, taking its
     * input from another tuple stream which this clause modifies
     *
     * @param base    the input tuple stream
     * @param context the dynamic evaluation context
     * @return the output tuple stream
     */
    @Override
    public TuplePull getPullStream(TuplePull base, XPathContext context) {
        return new WhereClausePull(base, getPredicate());
    }

    @Override
    public void gatherVariableReferences(final ExpressionVisitor visitor, Binding binding, List<VariableReference> references) {
        ExpressionTool.gatherVariableReferences(getPredicate(), binding, references);
    }

    @Override
    public void refineVariableType(ExpressionVisitor visitor, List<VariableReference> references, Expression returnExpr) {
        final ItemType actualItemType = getPredicate().getItemType();
        for (VariableReference ref : references) {
            ref.refineVariableType(actualItemType, getPredicate().getCardinality(),
                    getPredicate() instanceof Literal ? ((Literal) getPredicate()).getValue() : null,
                    getPredicate().getSpecialProperties());
            ExpressionTool.resetStaticProperties(returnExpr);
        }
    }

    /**
     * Get a push-mode tuple stream that implements the functionality of this clause, supplying its
     * output to another tuple stream
     *
     * @param destination the output tuple stream
     * @param output the destination for the result
     * @param context     the dynamic evaluation context
     * @return the push tuple stream that implements the functionality of this clause of the FLWOR
     *         expression
     */
    @Override
    public TuplePush getPushStream(TuplePush destination, Outputter output, XPathContext context) {
        return new WhereClausePush(output, destination, getPredicate());
    }

    /**
     * Process the subexpressions of this clause
     *
     * @param processor the expression processor used to process the subexpressions
     */
    @Override
    public void processOperands(OperandProcessor processor) throws XPathException {
        processor.processOperand(predicateOp);
    }

    @Override
    public void addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        getPredicate().addToPathMap(pathMap, pathMapNodeSet);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void explain(ExpressionPresenter out) throws XPathException {
        out.startElement("where");
        getPredicate().export(out);
        out.endElement();
    }

    @Override
    public String toShortString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);
        fsb.append("where ");
        fsb.append(getPredicate().toShortString());
        return fsb.toString();
    }

    public String toString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);
        fsb.append("where ");
        fsb.append(getPredicate().toString());
        return fsb.toString();
    }

    /**
     * Get information for inclusion in trace output
     *
     * @return a map containing the properties to be output
     */

    @Override
    public Map<String, Object> getTraceInfo() {
        Map<String, Object> info = new HashMap<>(1);
        info.put("condition", getPredicate().toShortString());
        return info;
    }
}

