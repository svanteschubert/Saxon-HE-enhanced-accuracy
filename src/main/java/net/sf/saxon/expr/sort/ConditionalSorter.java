////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceType;

/**
 * An expression that sorts an underlying sequence into document order if some condition is true, or that
 * returns the sequence "as is" (knowing that it doesn't need sorting) if the condition is false.
 */
public class ConditionalSorter extends Expression {

    private Operand conditionOp;
    private Operand sorterOp;

    /**
     * Generalized abstraction of the simplify(), typeCheck(), and optimize() methods, which
     * in this case are all structurally the same.
     */

    @FunctionalInterface
    private interface RewriteAction {
        // TODO: extend this mechanism to be used by other expression classes.
        Expression rewrite(Expression e) throws XPathException;
    }

    private final static OperandRole DOC_SORTER_ROLE =
            new OperandRole(OperandRole.CONSTRAINED_CLASS,
                            OperandUsage.TRANSMISSION,
                            SequenceType.ANY_SEQUENCE,
                            expr -> expr instanceof DocumentSorter);

    /**
     * Create a conditional document sorter
     *
     * @param condition the conditional expression
     * @param sorter    the sorting expression
     */

    public ConditionalSorter(Expression condition, DocumentSorter sorter) {
        conditionOp = new Operand(this, condition, OperandRole.SINGLE_ATOMIC);
        sorterOp = new Operand(this, sorter, DOC_SORTER_ROLE);
        adoptChildExpression(condition);
        adoptChildExpression(sorter);
    }

    @Override
    public Iterable<Operand> operands() {
        return operandList(conditionOp, sorterOp);
    }


    public void setCondition(Expression condition) {
        conditionOp.setChildExpression(condition);
    }

    public void setDocumentSorter(DocumentSorter documentSorter) {
        sorterOp.setChildExpression(documentSorter);
    }

    /**
     * Get the condition under which the nodes need to be sorted
     *
     * @return the condition (an expression)
     */

    /*@NotNull*/
    public Expression getCondition() {
        return conditionOp.getChildExpression();
    }

    /**
     * Get the document sorter, which sorts the nodes if the condition is true
     *
     * @return the document sorter
     */

    /*@NotNull*/
    public DocumentSorter getDocumentSorter() {
        return (DocumentSorter) sorterOp.getChildExpression();
    }

    @Override
    public Expression simplify() throws XPathException {
        return rewrite(Expression::simplify);
    }

    /**
     * Perform type checking of an expression and its subexpressions. This is the second phase of
     * static optimization.
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        return rewrite(exp -> exp.typeCheck(visitor, contextInfo));
    }

    /**
     * Determine the static cardinality of the expression. This establishes how many items
     * there will be in the result of the expression, at compile time (i.e., without
     * actually evaluating the result.
     *
     * @return one of the values Cardinality.ONE_OR_MORE,
     * Cardinality.ZERO_OR_MORE, Cardinality.EXACTLY_ONE,
     * Cardinality.ZERO_OR_ONE, Cardinality.EMPTY. This default
     * implementation returns ZERO_OR_MORE (which effectively gives no
     * information).
     */

    @Override
    public int getCardinality() {
        return getDocumentSorter().getCardinality();
    }


    /**
     * Compute the special properties of this expression. These properties are denoted by a bit-significant
     * integer, possible values are in class {@link net.sf.saxon.expr.StaticProperty}. The "special" properties are properties
     * other than cardinality and dependencies, and most of them relate to properties of node sequences, for
     * example whether the nodes are in document order.
     *
     * @return the special properties, as a bit-significant integer
     */

    @Override
    protected int computeSpecialProperties() {
        return getCondition().getSpecialProperties()
                | StaticProperty.ORDERED_NODESET
                & ~StaticProperty.REVERSE_DOCUMENT_ORDER;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     * {@link #PROCESS_METHOD}
     */

    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD;
    }

    @Override
    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        return rewrite(exp -> exp.optimize(visitor, contextInfo));
    }

    private Expression rewrite(RewriteAction rewriter) throws XPathException {
        Expression base = rewriter.rewrite(getDocumentSorter());
        if (base instanceof DocumentSorter) {
            sorterOp.setChildExpression(base);
        } else {
            return base;
        }
        Expression cond = rewriter.rewrite(getCondition());
        if (cond instanceof Literal) {
            boolean b = ((Literal) cond).getValue().effectiveBooleanValue();
            if (b) {
                return base;
            } else {
                return ((DocumentSorter) base).getBaseExpression();
            }
        } else {
            conditionOp.setChildExpression(cond);
            return this;
        }
    }

    @Override
    public Expression unordered(boolean retainAllNodes, boolean forStreaming) throws XPathException {
        Expression base = getDocumentSorter().unordered(retainAllNodes, forStreaming);
        if (base instanceof DocumentSorter) {
            return this;
        } else {
            return base;
        }
    }

    /**
     * Compute the static cardinality of this expression
     *
     * @return the computed cardinality, as one of the values {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_ONE},
     * {@link net.sf.saxon.expr.StaticProperty#EXACTLY_ONE}, {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ONE_OR_MORE},
     * {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_MORE}
     */

    @Override
    protected int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        ConditionalSorter cs = new ConditionalSorter(getCondition().copy(rebindings), (DocumentSorter) getDocumentSorter().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, cs);
        return cs;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("conditionalSort", this);
        getCondition().export(out);
        getDocumentSorter().export(out);
        out.endElement();
    }

    /**
     * Determine the data type of the expression, if possible. All expression return
     * sequences, in general; this method determines the type of the items within the
     * sequence, assuming that (a) this is known in advance, and (b) it is the same for
     * all items in the sequence.
     * <p>This method should always return a result, though it may be the best approximation
     * that is available at the time.</p>
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER,
     * Type.NODE, or Type.ITEM (meaning not known at compile time)
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getDocumentSorter().getItemType();
    }


    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     * of the expression
     * @throws net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
     *                                           expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        boolean b = getCondition().effectiveBooleanValue(context);
        if (b) {
            return getDocumentSorter().iterate(context);
        } else {
            return getDocumentSorter().getBaseExpression().iterate(context);
        }
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in export() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "conditionalSort";
    }


}

