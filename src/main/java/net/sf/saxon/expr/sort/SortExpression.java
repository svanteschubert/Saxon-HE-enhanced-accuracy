////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.function.Supplier;

/**
 * Expression equivalent to the imaginary syntax
 * expr sortby (sort-key)+
 */

public class SortExpression extends Expression
        implements SortKeyEvaluator {

    private final Operand selectOp;
    private final Operand sortOp;
    private AtomicComparer[] comparators = null;
    // created early if all comparators can be created statically
    // transient because Java RuleBasedCollator is not serializable

    private ItemEvaluator[] sortKeyEvaluators;

    /**
     * Create a sort expression
     *
     * @param select   the expression whose result is to be sorted
     * @param sortKeys the set of sort key definitions to be used, in major to minor order
     */

    public SortExpression(Expression select, SortKeyDefinitionList sortKeys) {
        selectOp = new Operand(this, select, OperandRole.FOCUS_CONTROLLING_SELECT);
        sortOp = new Operand(this, sortKeys, OperandRole.CONSTRAINED_ATOMIC_SEQUENCE);
        adoptChildExpression(select);
        adoptChildExpression(sortKeys);
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
        return "sort";
    }

    /**
     * Get the operand representing the expression being sorted
     *
     * @return the operand representing the expression being sorted
     */

    public Operand getBaseOperand() {
        return selectOp;
    }

    /**
     * Get the expression defining the sequence being sorted
     *
     * @return the expression whose result is to be sorted
     */

    public Expression getBaseExpression() {
        return getSelect();
    }

    /**
     * Get the comparators, if known statically. Otherwise, return null.
     *
     * @return The comparators, if they have been allocated; otherwise null
     */

    public AtomicComparer[] getComparators() {
        return comparators;
    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * returns a zero-length array, appropriate for an expression that has no
     * sub-expressions.
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterable<Operand> operands() {
        return operandList(selectOp, sortOp);
    }


    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        selectOp.typeCheck(visitor, contextInfo);

        Expression select2 = getSelect();
        if (select2 != getSelect()) {
            adoptChildExpression(select2);
            setSelect(select2);
        }
        if (!Cardinality.allowsMany(select2.getCardinality())) {
            // exit now because otherwise the type checking of the sort key can cause spurious failures
            return select2;
        }
        ItemType sortedItemType = getSelect().getItemType();

        boolean allKeysFixed = true;
        for (SortKeyDefinition sortKeyDefinition : getSortKeyDefinitionList()) {
            if (!sortKeyDefinition.isFixed()) {
                allKeysFixed = false;
                break;
            }
        }

        if (allKeysFixed) {
            comparators = new AtomicComparer[getSortKeyDefinitionList().size()];
        }

        TypeChecker tc = visitor.getConfiguration().getTypeChecker(false);
        for (int i = 0; i < getSortKeyDefinitionList().size(); i++) {
            SortKeyDefinition sortKeyDef = getSortKeyDefinition(i);
            Expression sortKey = sortKeyDef.getSortKey();
            if (sortKeyDef.isSetContextForSortKey()) {
                ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(sortedItemType);
                sortKey = sortKey.typeCheck(visitor, cit);
            } else {
                sortKey = sortKey.typeCheck(visitor, contextInfo);
            }
            if (sortKeyDef.isBackwardsCompatible()) {
                sortKey = FirstItemExpression.makeFirstItemExpression(sortKey);
            } else /*if (getRetainedStaticContext().getPackageData().getHostLanguageVersion() < 40 )*/ {
                // TODO: allow composite sort keys in 4.0
                Supplier<RoleDiagnostic> role = () ->
                        new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:sort/select", 0, "XTTE1020");
                sortKey = tc.staticTypeCheck(sortKey, SequenceType.OPTIONAL_ATOMIC, role, visitor);
            }
            sortKeyDef.setSortKey(sortKey, sortKeyDef.isSetContextForSortKey());
            sortKeyDef.typeCheck(visitor, contextInfo);
            if (sortKeyDef.isFixed()) {
                AtomicComparer comp = sortKeyDef.makeComparator(
                        visitor.getStaticContext().makeEarlyEvaluationContext());
                sortKeyDef.setFinalComparator(comp);
                if (allKeysFixed) {
                    comparators[i] = comp;
                }
            }
            if (sortKeyDef.isSetContextForSortKey() && !ExpressionTool.dependsOnFocus(sortKey)) {
                visitor.getStaticContext().issueWarning(
                        "Sort key will have no effect because its value does not depend on the context item",
                        SaxonErrorCode.SXWN9033, sortKey.getLocation());
            }

        }
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        selectOp.optimize(visitor, contextItemType);

        // optimize the sort keys
        ContextItemStaticInfo cit;
        if (getSortKeyDefinition(0).isSetContextForSortKey()) {
            ItemType sortedItemType = getSelect().getItemType();
            cit = visitor.getConfiguration().makeContextItemStaticInfo(sortedItemType);
        } else {
            cit = contextItemType;
        }
        for (SortKeyDefinition sortKeyDefinition : getSortKeyDefinitionList()) {
            Expression sortKey = sortKeyDefinition.getSortKey();
            sortKey = sortKey.optimize(visitor, cit);
            sortKeyDefinition.setSortKey(sortKey, true);
        }
        if (Cardinality.allowsMany(getSelect().getCardinality())) {
            return this;
        } else {
            return getSelect();
        }
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
        int len = getSortKeyDefinitionList().size();
        SortKeyDefinition[] sk2 = new SortKeyDefinition[len];
        for (int i = 0; i < len; i++) {
            sk2[i] = getSortKeyDefinition(i).copy(rebindings);
        }
        SortExpression se2 = new SortExpression(getSelect().copy(rebindings), new SortKeyDefinitionList(sk2));
        ExpressionTool.copyLocationInfo(this, se2);
        se2.comparators = comparators;
        return se2;
    }

    /**
     * Test whether a given expression is one of the sort keys
     *
     * @param child the given expression
     * @return true if the given expression is one of the sort keys
     */

    public boolean isSortKey(Expression child) {
        for (SortKeyDefinition sortKeyDefinition : getSortKeyDefinitionList()) {
            Expression exp = sortKeyDefinition.getSortKey();
            if (exp == child) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine the static cardinality
     */

    @Override
    protected int computeCardinality() {
        return getSelect().getCardinality();
    }

    /**
     * Determine the data type of the items returned by the expression, if possible
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER, Type.NODE,
     *         or Type.ITEM (meaning not known in advance)
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getSelect().getItemType();
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-significant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    @Override
    protected int computeSpecialProperties() {
        int props = 0;
        if (getSelect().hasSpecialProperty(StaticProperty.CONTEXT_DOCUMENT_NODESET)) {
            props |= StaticProperty.CONTEXT_DOCUMENT_NODESET;
        }
        if (getSelect().hasSpecialProperty(StaticProperty.SINGLE_DOCUMENT_NODESET)) {
            props |= StaticProperty.SINGLE_DOCUMENT_NODESET;
        }
        if (getSelect().hasSpecialProperty(StaticProperty.NO_NODES_NEWLY_CREATED)) {
            props |= StaticProperty.NO_NODES_NEWLY_CREATED;
        }
        return props;
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

    /**
     * Enumerate the results of the expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {

        SequenceIterator iter = getSelect().iterate(context);
        if (iter instanceof EmptyIterator) {
            return iter;
        }
        return iterateSorted(iter, context);
    }

    public SequenceIterator iterateSorted(SequenceIterator iter, XPathContext context) throws XPathException {

        AtomicComparer[] comps = comparators;
        if (comparators == null) {
            int len = getSortKeyDefinitionList().size();
            comps = new AtomicComparer[len];
            for (int s = 0; s < len; s++) {
                AtomicComparer comp = getSortKeyDefinition(s).getFinalComparator();
                if (comp == null) {
                    comp = getSortKeyDefinition(s).makeComparator(context);
                }
                comps[s] = comp;
            }
        }
        makeSortKeyEvaluators();
        iter = new SortedIterator(context, iter, this, comps, getSortKeyDefinition(0).isSetContextForSortKey());
        ((SortedIterator) iter).setHostLanguage(getPackageData().getHostLanguage());
        return iter;
    }

    public synchronized void makeSortKeyEvaluators() {
        if (sortKeyEvaluators == null) {
            int len = getSortKeyDefinitionList().size();
            sortKeyEvaluators = new ItemEvaluator[len];
            for (int s = 0; s < len; s++) {
                sortKeyEvaluators[s] = getSortKeyDefinition(s).getSortKey().makeElaborator().elaborateForItem();
            }
        }
    }

    /**
     * Callback for evaluating the sort keys
     *
     * @param n the requested index
     * @param c the XPath context
     * @return the evaluated sort key
     * @throws XPathException if any error occurs
     */

    @Override
    public AtomicValue evaluateSortKey(int n, XPathContext c) throws XPathException {
        return (AtomicValue) sortKeyEvaluators[n].eval(c);
    }

    @Override
    public String toShortString() {
        return "sort(" + getBaseExpression().toShortString() + ")";
    }


    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "SortExpression";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("sort", this);
        out.setChildRole("select");
        getSelect().export(out);
        getSortKeyDefinitionList().export(out);
        out.endElement();
    }

    public Expression getSelect() {
        return selectOp.getChildExpression();
    }

    public void setSelect(Expression select) {
        selectOp.setChildExpression(select);
    }

    public SortKeyDefinitionList getSortKeyDefinitionList() {
        return (SortKeyDefinitionList)sortOp.getChildExpression();
    }

    public SortKeyDefinition getSortKeyDefinition(int i) {
        return getSortKeyDefinitionList().getSortKeyDefinition(i);
    }

    public void setSortKeyDefinitionList(SortKeyDefinitionList skd) {
        sortOp.setChildExpression(skd);
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new SortExprElaborator();
    }

    /**
     * Elaborator for a sort expression - sorts nodes into order based on a user-supplied sort key
     */

    public static class SortExprElaborator extends PullElaborator {

        public PullEvaluator elaborateForPull() {

            // TODO: elaborate the sort key expression, and other expressions in the sort key definition

            final SortExpression expr = (SortExpression) getExpression();
            final PullEvaluator baseEval = expr.getBaseExpression().makeElaborator().elaborateForPull();

            return context -> expr.iterateSorted(baseEval.iterate(context), context);
        }

    }
}

