////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Affinity;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;


/**
 * A DocumentSorter is an expression that sorts a sequence of nodes into
 * document order.
 */
public class DocumentSorter extends UnaryExpression {

    private ItemOrderComparer comparer;

    public DocumentSorter(Expression base) {
        super(base);
        int props = base.getSpecialProperties();
        if (((props & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0) ||
                (props & StaticProperty.SINGLE_DOCUMENT_NODESET) != 0) {
            comparer = LocalOrderComparer.getInstance();
        } else {
            comparer = GlobalOrderComparer.getInstance();
        }
    }

    public DocumentSorter(Expression base, boolean intraDocument) {
        super(base);
        if (intraDocument) {
            comparer = LocalOrderComparer.getInstance();
        } else {
            comparer = GlobalOrderComparer.getInstance();
        }
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.SAME_FOCUS_ACTION;
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */

    @Override
    public String getExpressionName() {
        return "docOrder";
    }

    public ItemOrderComparer getComparer() {
        return comparer;
    }

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        Expression operand = getBaseExpression().simplify();
        if (operand.hasSpecialProperty(StaticProperty.ORDERED_NODESET)) {
            // this can happen as a result of further simplification
            return operand;
        }
        return this;
    }

    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression e2 = super.typeCheck(visitor, contextInfo);
        if (e2 != this) {
            return e2;
        }
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        if (th.relationship(getBaseExpression().getItemType(), AnyNodeTest.getInstance()) == Affinity.DISJOINT) {
            return getBaseExpression();
        }
        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.MISC, "document-order sorter", 0);
        Expression operand = visitor.getConfiguration().getTypeChecker(false).staticTypeCheck(
                getBaseExpression(), SequenceType.NODE_SEQUENCE, role, visitor);
        setBaseExpression(operand);
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().optimize(visitor, contextInfo);
        Expression sortable = getBaseExpression();
        boolean tryHarder = sortable.isStaticPropertiesKnown();
        while (true) {
            if (sortable.hasSpecialProperty(StaticProperty.ORDERED_NODESET)) {
                // this can happen as a result of further simplification
                return sortable;
            }
            if (!Cardinality.allowsMany(sortable.getCardinality())) {
                return sortable;
            }

            if (sortable instanceof SlashExpression) {
                SlashExpression slash = (SlashExpression)sortable;

                // Bug 3389: try to rewrite sort(conditionalSort($var/child::x) / child::y)
                // as conditionalSort($var, (child::x/child::y))
                Expression lhs = slash.getLhsExpression();
                Expression rhs = slash.getRhsExpression();
                if (lhs instanceof ConditionalSorter &&
                        slash.getRhsExpression().hasSpecialProperty(StaticProperty.PEER_NODESET)) {
                    ConditionalSorter c = (ConditionalSorter) lhs;
                    DocumentSorter d = c.getDocumentSorter();
                    Expression condition = c.getCondition();
                    Expression s = new SlashExpression(d.getBaseExpression(), rhs);
                    s = s.optimize(visitor, contextInfo);
                    return new ConditionalSorter(condition, new DocumentSorter(s));
                }
                // docOrder(docOrder(A)/B) can be rewritten as docOrder(A/B). However, this may not always
                // be wise, because the inner docOrder might eliminate many duplicates, reducing the cost
                // of the /B operation. We therefore do it only if B is a low-cost operation.
                if (lhs instanceof DocumentSorter && rhs instanceof AxisExpression && ((AxisExpression)rhs).getAxis() == AxisInfo.CHILD) {
                    SlashExpression s1 = new SlashExpression(((DocumentSorter)lhs).getBaseExpression(), rhs);
                    ExpressionTool.copyLocationInfo(this, s1);
                    return new DocumentSorter(s1).optimize(visitor, contextInfo);
                }
                // docOrder(A/B) can be rewritten as head(A)!docOrder(B) in the case where B returns nodes
                // and is independent of the focus. We already know it returns nodes otherwise we wouldn't be here.
                // SEE BUG 4640
                if (!ExpressionTool.dependsOnFocus(rhs) &&
                        !rhs.hasSpecialProperty(StaticProperty.HAS_SIDE_EFFECTS) &&
                        rhs.hasSpecialProperty(StaticProperty.NO_NODES_NEWLY_CREATED)) {
                    Expression e1 = FirstItemExpression.makeFirstItemExpression(slash.getLhsExpression());
                    Expression e2 = new DocumentSorter(slash.getRhsExpression());
                    SlashExpression e3 = new SlashExpression(e1, e2);
                    ExpressionTool.copyLocationInfo(this, e3);
                    return e3.optimize(visitor, contextInfo);
                }
            }
            // Try once more after recomputing the static properties of the expression
            if (tryHarder) {
                sortable.resetLocalStaticProperties();
                tryHarder = false;
            } else {
                break;
            }
        }
        if (sortable instanceof SlashExpression && !visitor.isOptimizeForStreaming() && !(getParentExpression() instanceof ConditionalSorter)) {
            return visitor.obtainOptimizer().makeConditionalDocumentSorter(
                    this, (SlashExpression) sortable);
        }
        return this;
    }

    /**
     * Return the net cost of evaluating this expression, excluding the cost of evaluating
     * its operands. We take the cost of evaluating a simple scalar comparison or arithmetic
     * expression as 1 (one).
     *
     * @return the intrinsic cost of this operation, excluding the costs of evaluating
     * the operands
     */
    @Override
    public int getNetCost() {
        return 30;
    }

    /**
     * Replace this expression by a simpler expression that delivers the results without regard
     * to order.
     *
     * @param retainAllNodes set to true if the result must contain exactly the same nodes as the
     *                       original; set to false if the result can eliminate (or introduce) duplicates.
     * @param forStreaming set to true if optimizing for streaming
     */
    @Override
    public Expression unordered(boolean retainAllNodes, boolean forStreaming) throws XPathException {
        Expression operand = getBaseExpression().unordered(retainAllNodes, forStreaming);
        if (operand.hasSpecialProperty(StaticProperty.ORDERED_NODESET)) {
            return operand;
        }
        if (!retainAllNodes) {
            return operand;
        } else if (operand instanceof SlashExpression) {
            // handle the common case of //section/head where it is safe to remove sorting, because
            // no duplicates need to be removed
            SlashExpression exp = (SlashExpression)operand;
            Expression a = exp.getSelectExpression();
            Expression b = exp.getActionExpression();
            a = ExpressionTool.unfilteredExpression(a, false);
            b = ExpressionTool.unfilteredExpression(b, false);
            if (a instanceof AxisExpression &&
                    (((AxisExpression)a).getAxis() == AxisInfo.DESCENDANT || ((AxisExpression)a).getAxis() == AxisInfo.DESCENDANT_OR_SELF) &&
                    b instanceof AxisExpression &&
                    ((AxisExpression)b).getAxis() == AxisInfo.CHILD) {
                return operand.unordered(retainAllNodes, false);
            }
        }
        setBaseExpression(operand);
        return this;
    }


    @Override
    public int computeSpecialProperties() {
        return getBaseExpression().getSpecialProperties() | StaticProperty.ORDERED_NODESET;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        DocumentSorter ds = new DocumentSorter(getBaseExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, ds);
        return ds;
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException
     *          if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config) throws XPathException {
        return getBaseExpression().toPattern(config);
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

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return new DocumentOrderIterator(getBaseExpression().iterate(context), comparer);
    }

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        return getBaseExpression().effectiveBooleanValue(context);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("docOrder", this);
        out.emitAttribute("intra", comparer instanceof LocalOrderComparer ? "1" : "0");
        getBaseExpression().export(out);
        out.endElement();
    }


    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "DocumentSorterAdjunct";
    }
}

