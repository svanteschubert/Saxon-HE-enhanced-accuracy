////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

import java.util.List;

import static net.sf.saxon.expr.flwor.Clause.ClauseName.DIAG;

/**
 * A "let" clause in a FLWOR expression
 */
public class DiagnosticClause extends Clause {

    private Operand sequenceOp;
    private PullEvaluator evaluator;

    @Override
    public ClauseName getClauseKey() {
        return DIAG;
    }

    public PullEvaluator getEvaluator() {
        if (evaluator == null) {
            evaluator = getSequence().makeElaborator().elaborateForPull();
        }
        return evaluator;
    }

    @Override
    public DiagnosticClause copy(FLWORExpression flwor, RebindingMap rebindings) {
        DiagnosticClause diag2 = new DiagnosticClause();
        diag2.setLocation(getLocation());
        diag2.setPackageData(getPackageData());
        diag2.initSequence(flwor, getSequence().copy(rebindings));
        return diag2;
    }

    public void initSequence(FLWORExpression flwor, Expression sequence) {
        sequenceOp = new Operand(flwor, sequence, isRepeated() ? OperandRole.REPEAT_NAVIGATE : OperandRole.NAVIGATE);
    }

//    public void setSequence(Expression sequence) {
//        sequenceOp.setChildExpression(sequence);
//    }

    public Expression getSequence() {
        return sequenceOp.getChildExpression();
    }


    /**
     * Get the number of variables bound by this clause
     *
     * @return the number of variable bindings
     */
    @Override
    public LocalVariableBinding[] getRangeVariables() {
        return new LocalVariableBinding[]{};
    }

    /**
     * Get a tuple stream that implements the functionality of this clause, taking its
     * input from another tuple stream which this clause modifies
     *
     * @param base    the input tuple stream
     * @param context the XPath context
     * @return the output tuple stream
     */

    @Override
    public TuplePull getPullStream(TuplePull base, XPathContext context) {
        return new DiagnosticClausePull(base, this);
    }

    /**
     * Get a push-mode tuple stream that implements the functionality of this clause, supplying its
     * output to another tuple stream
     *
     * @param destination the output tuple stream
     * @param output      the destination for the result
     * @param context     the XPath context
     * @return the push tuple stream that implements the functionality of this clause of the FLWOR
     *         expression
     */
    @Override
    public TuplePush getPushStream(TuplePush destination, Outputter output, XPathContext context) {
        return new DiagnosticClausePush(output, destination, this);
    }

    /**
     * Process the subexpressions of this clause
     *
     * @param processor the expression processor used to process the subexpressions
     */
    @Override
    public void processOperands(OperandProcessor processor) throws XPathException {
        processor.processOperand(sequenceOp);
    }

    /**
     * Type-check the expression
     */

    @Override
    public void typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        //
    }

    @Override
    public void gatherVariableReferences(final ExpressionVisitor visitor, Binding binding, List<VariableReference> references) {
        ExpressionTool.gatherVariableReferences(getSequence(), binding, references);
    }

    @Override
    public void refineVariableType(ExpressionVisitor visitor, List<VariableReference> references, Expression returnExpr) {
        final Expression seq = getSequence();
        final ItemType actualItemType = seq.getItemType();
        for (VariableReference ref : references) {
            ref.refineVariableType(actualItemType, getSequence().getCardinality(),
                    seq instanceof Literal ? ((Literal) seq).getGroundedValue() : null,
                    seq.getSpecialProperties());
            ExpressionTool.resetStaticProperties(returnExpr);
        }
    }


    @Override
    public void addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet varPath = getSequence().addToPathMap(pathMap, pathMapNodeSet);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void explain(ExpressionPresenter out) throws XPathException {
        out.startElement("trace");
        getSequence().export(out);
        out.endElement();
    }

    @Override
    public String toShortString() {
        StringBuilder fsb = new StringBuilder(64);
        fsb.append("trace ");
        fsb.append(getSequence().toShortString());
        return fsb.toString();
    }

    public String toString() {
        StringBuilder fsb = new StringBuilder(64);
        fsb.append("trace ");
        fsb.append(getSequence().toString());
        return fsb.toString();
    }
}

