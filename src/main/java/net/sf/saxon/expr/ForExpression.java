////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.flwor.OuterForExpression;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;

/**
 * A ForExpression maps an expression over a sequence.
 * We use a ForExpression in preference to a FLWORExpression to handle simple cases
 * (roughly, the XPath subset). In 9.6, we no longer convert a FLWORExpression to a ForExpression
 * if there is a position variable, which simplifies the cases this class has to handle.
 */

public class ForExpression extends Assignation {

    private int actionCardinality = StaticProperty.ALLOWS_MANY;

    /**
     * Create a "for" expression (for $x at $p in SEQUENCE return ACTION)
     */

    public ForExpression() {
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the expression.
     */

    @Override
    public String getExpressionName() {
        return "for";
    }


    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        // The order of events is critical here. First we ensure that the type of the
        // sequence expression is established. This is used to establish the type of the variable,
        // which in turn is required when type-checking the action part.

        getSequenceOp().typeCheck(visitor, contextInfo);
        if (Literal.isEmptySequence(getSequence()) && !(this instanceof OuterForExpression)) {
            return getSequence();
        }

        if (requiredType != null) {
            // if declaration is null, we've already done the type checking in a previous pass
            SequenceType decl = requiredType;
            SequenceType sequenceType = SequenceType.makeSequenceType(
                    decl.getPrimaryType(), StaticProperty.ALLOWS_ZERO_OR_MORE);
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.VARIABLE, variableName.getDisplayName(), 0);
            setSequence(TypeChecker.strictTypeCheck(
                    getSequence(), sequenceType, role, visitor.getStaticContext()));
            ItemType actualItemType = getSequence().getItemType();
            refineTypeInformation(actualItemType,
                                  getRangeVariableCardinality(),
                                  null,
                                  getSequence().getSpecialProperties(), this);
        }

        if (Literal.isEmptySequence(getAction())) {
            return getAction();
        }
        getActionOp().typeCheck(visitor, contextInfo);
        actionCardinality = getAction().getCardinality();
        return this;
    }

    /**
     * Get the cardinality of the range variable
     *
     * @return the cardinality of the range variable (StaticProperty.EXACTLY_ONE). Can be overridden
     * in a subclass
     */

    protected int getRangeVariableCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Optimize the expression
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        Configuration config = visitor.getConfiguration();
        Optimizer opt = visitor.obtainOptimizer();
        boolean debug = config.getBooleanProperty(Feature.TRACE_OPTIMIZER_DECISIONS);

        // Try to promote any WHERE clause appearing immediately within the FOR expression

        if (Choose.isSingleBranchChoice(getAction())) {
            getActionOp().optimize(visitor, contextItemType);
        }

        Expression p = promoteWhereClause();
        if (p != null) {
            if (debug) {
                opt.trace("Promoted where clause in for $" + getVariableName(), p);
            }
            return p.optimize(visitor, contextItemType);
        }

        Expression seq0 = getSequence();
        getSequenceOp().optimize(visitor, contextItemType);
        if (seq0 != getSequence()) {
            // if it changed, re-optimize
            return optimize(visitor, contextItemType);
        }

        if (Literal.isEmptySequence(getSequence()) && !(this instanceof OuterForExpression)) {
            return getSequence();
        }

        Expression act0 = getAction();
        getActionOp().optimize(visitor, contextItemType);
        if (act0 != getAction()) {
            // it's now worth re-attempting the "where" clause optimizations
            return optimize(visitor, contextItemType);
        }

        if (Literal.isEmptySequence(getAction())) {
            return getAction();
        }

        // Simplify an expression of the form "for $b in a/b/c return $b/d".
        // (XQuery users seem to write these a lot!)

        if (getSequence() instanceof SlashExpression && getAction() instanceof SlashExpression) {
            SlashExpression path2 = (SlashExpression) getAction();
            Expression start2 = path2.getSelectExpression();
            Expression step2 = path2.getActionExpression();
            if (start2 instanceof VariableReference && ((VariableReference) start2).getBinding() == this &&
                    ExpressionTool.getReferenceCount(getAction(), this, false) == 1 &&
                    ((step2.getDependencies() & (StaticProperty.DEPENDS_ON_POSITION | StaticProperty.DEPENDS_ON_LAST)) == 0)) {
                Expression newPath = new SlashExpression(getSequence(), path2.getActionExpression());
                ExpressionTool.copyLocationInfo(this, newPath);
                newPath = newPath.simplify().typeCheck(visitor, contextItemType);
                if (newPath instanceof SlashExpression) {
                    // if not, it has been wrapped in a DocumentSorter or Reverser, which makes it ineligible.
                    // see test qxmp299, where this condition isn't satisfied
                    if (debug) {
                        opt.trace("Collapsed return clause of for $" + getVariableName() +
                                          " into path expression", newPath);
                    }
                    return newPath.optimize(visitor, contextItemType);
                }
            }
        }

        // Simplify an expression of the form "for $x in EXPR return $x". These sometimes
        // arise as a result of previous optimization steps.

        if (getAction() instanceof VariableReference && ((VariableReference) getAction()).getBinding() == this) {
            if (debug) {
                opt.trace("Collapsed redundant for expression $" + getVariableName(), getSequence());
            }
            return getSequence();
        }

        // If the cardinality of the sequence is exactly one, rewrite as a LET expression

        if (getSequence().getCardinality() == StaticProperty.EXACTLY_ONE) {
            LetExpression let = new LetExpression();
            let.setVariableQName(variableName);
            let.setRequiredType(SequenceType.makeSequenceType(
                    getSequence().getItemType(),
                    StaticProperty.EXACTLY_ONE));
            let.setSequence(getSequence());
            let.setAction(getAction());
            let.setSlotNumber(slotNumber);
            let.setRetainedStaticContextLocally(getRetainedStaticContext());
            ExpressionTool.rebindVariableReferences(getAction(), this, let);
            return let.typeCheck(visitor, contextItemType).optimize(visitor, contextItemType);
        }

        return this;
    }

    /**
     * Replace this expression by a simpler expression that delivers the results without regard
     * to order.
     *
     * @param retainAllNodes set to true if the result must contain exactly the same nodes as the
     *                       original; set to false if the result can eliminate (or introduce) duplicates.
     * @param forStreaming   set to true if optimizing for streaming
     */
    @Override
    public Expression unordered(boolean retainAllNodes, boolean forStreaming) throws XPathException {
        setSequence(getSequence().unordered(retainAllNodes, forStreaming));
        setAction(getAction().unordered(retainAllNodes, forStreaming));
        return this;
    }

    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     * unknown or not applicable.
     */
    /*@Nullable*/
    @Override
    public IntegerValue[] getIntegerBounds() {
        return getAction().getIntegerBounds();
    }

    /**
     * Promote a WHERE clause whose condition doesn't depend on the variable being bound.
     * This rewrites an expression of the form
     * <p>let $i := SEQ return if (C) then R else ()</p>
     * <p>to the form:</p>
     * <p>if (C) then (let $i := SEQ return R) else ()</p>
     *
     * @return an expression in which terms from the WHERE clause that can be extracted have been extracted
     */

    /*@Nullable*/
    private Expression promoteWhereClause() {
        if (Choose.isSingleBranchChoice(getAction())) {
            Expression condition = ((Choose) getAction()).getCondition(0);
            Binding[] bindingList = new Binding[]{this};
            List<Expression> list = new ArrayList<>(5);
            Expression promotedCondition = null;
            BooleanExpression.listAndComponents(condition, list);
            for (int i = list.size() - 1; i >= 0; i--) {
                Expression term = list.get(i);
                if (!ExpressionTool.dependsOnVariable(term, bindingList)) {
                    if (promotedCondition == null) {
                        promotedCondition = term;
                    } else {
                        promotedCondition = new AndExpression(term, promotedCondition);
                    }
                    list.remove(i);
                }
            }
            if (promotedCondition != null) {
                if (list.isEmpty()) {
                    // the whole if() condition has been promoted
                    Expression oldThen = ((Choose) getAction()).getAction(0);
                    setAction(oldThen);
                    return Choose.makeConditional(promotedCondition, this);
                } else {
                    // one or more terms of the if() condition have been promoted
                    Expression retainedCondition = list.get(0);
                    for (int i = 1; i < list.size(); i++) {
                        retainedCondition = new AndExpression(retainedCondition, list.get(i));
                    }
                    ((Choose) getAction()).setCondition(0, retainedCondition);
                    Expression newIf = Choose.makeConditional(
                            promotedCondition, this, Literal.makeEmptySequence());
                    ExpressionTool.copyLocationInfo(this, newIf);
                    return newIf;
                }
            }
        }
        return null;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings variables that need to be re-bound
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        ForExpression forExp = new ForExpression();
        ExpressionTool.copyLocationInfo(this, forExp);
        forExp.setRequiredType(requiredType);
        forExp.setVariableQName(variableName);
        forExp.setSequence(getSequence().copy(rebindings));
        rebindings.put(this, forExp);
        Expression newAction = getAction().copy(rebindings);
        forExp.setAction(newAction);
        forExp.variableName = variableName;
        forExp.slotNumber = slotNumber;
        return forExp;
    }

    /**
     * Mark tail function calls: only possible if the for expression iterates zero or one times.
     * (This arises in XSLT/XPath, which does not have a LET expression, so FOR gets used instead)
     */

    @Override
    public int markTailFunctionCalls(StructuredQName qName, int arity) {
        if (!Cardinality.allowsMany(getSequence().getCardinality())) {
            return ExpressionTool.markTailFunctionCalls(getAction(), qName, arity);
        } else {
            return UserFunctionCall.NOT_TAIL_CALL;
        }
    }

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     *
     * @return true if this expression is vacuous
     */

    @Override
    public boolean isVacuousExpression() {
        return getAction().isVacuousExpression();
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD | PROCESS_METHOD;
    }

    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    @Override
    public void checkPermittedContents(SchemaType parentType, boolean whole) throws XPathException {
        getAction().checkPermittedContents(parentType, false);
    }

    /**
     * Iterate over the sequence of values
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {

        // First create an iteration of the base sequence.

        // Then create a MappingIterator which applies a mapping function to each
        // item in the base sequence. The mapping function is essentially the "return"
        // expression, wrapped in a MappingAction object that is responsible also for
        // setting the range variable at each step.

        SequenceIterator base = getSequence().iterate(context);
        MappingAction map = new MappingAction(context, getLocalSlotNumber(), getAction());
        switch (actionCardinality) {
            case StaticProperty.EXACTLY_ONE:
                return new ItemMappingIterator(base, map, true);
            case StaticProperty.ALLOWS_ZERO_OR_ONE:
                return new ItemMappingIterator(base, map, false);
            default:
                return new MappingIterator(base, map);
        }
    }

    /**
     * Process this expression as an instruction, writing results to the current
     * outputter
     */

    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        int slot = getLocalSlotNumber();
        getSequence().iterate(context).forEachOrFail(item -> {
            context.setLocalVariable(slot, item);
            getAction().process(output, context);
        });
    }


    /**
     * Evaluate an updating expression, adding the results to a Pending Update List.
     * The default implementation of this method, which is used for non-updating expressions,
     * throws an UnsupportedOperationException
     *
     * @param context the XPath dynamic evaluation context
     * @param pul     the pending update list to which the results should be written
     */

    @Override
    public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
        int slot = getLocalSlotNumber();
        getSequence().iterate(context).forEachOrFail(item -> {
            context.setLocalVariable(slot, item);
            getAction().evaluatePendingUpdates(context, pul);
        });
    }

    /**
     * Determine the data type of the items returned by the expression, if possible
     *
     * @return one of the values Type.STRING, Type.BOOLEAN, Type.NUMBER, Type.NODE,
     * or Type.ITEM (meaning not known in advance)
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getAction().getItemType();
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @param contextItemType the static type of the context item
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return getAction().getStaticUType(contextItemType);
    }

    /**
     * Determine the static cardinality of the expression
     */

    @Override
    public int computeCardinality() {
        int c1 = getSequence().getCardinality();
        int c2 = getAction().getCardinality();
        return Cardinality.multiply(c1, c2);
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     *
     * @return a representation of the expression as a string
     */

    public String toString() {
        return "for $" + getVariableEQName() +
                " in " + (getSequence() == null ? "(...)" : getSequence().toString()) +
                " return " + (getAction() == null ? "(...)" : ExpressionTool.parenthesize(getAction()));
    }

    @Override
    public String toShortString() {
        return "for $" + getVariableQName().getDisplayName() +
                " in " + (getSequence() == null ? "(...)" : getSequence().toShortString()) +
                " return " + (getAction() == null ? "(...)" : getAction().toShortString());
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("for", this);
        explainSpecializedAttributes(out);
        out.emitAttribute("var", getVariableQName());
        ItemType varType = getSequence().getItemType();
        if (varType != AnyItemType.getInstance()) {
            out.emitAttribute("as", AlphaCode.fromItemType(varType));
        }
        out.emitAttribute("slot", "" + getLocalSlotNumber());
        out.setChildRole("in");
        getSequence().export(out);
        out.setChildRole("return");
        getAction().export(out);
        out.endElement();
    }

    protected void explainSpecializedAttributes(ExpressionPresenter out) {
        // no action
    }

    /**
     * The MappingAction represents the action to be taken for each item in the
     * source sequence. It acts as the MappingFunction for the mapping iterator.
     */

    public static class MappingAction
            implements MappingFunction, ItemMappingFunction {

        protected XPathContext context;
        private int slotNumber;
        private Expression action;

        public MappingAction(XPathContext context,
                             int slotNumber,
                             Expression action) {
            this.context = context;
            this.slotNumber = slotNumber;
            this.action = action;
        }

        /*@Nullable*/
        @Override
        public SequenceIterator map(Item item) throws XPathException {
            context.setLocalVariable(slotNumber, item);
            return action.iterate(context);
        }

        /*@Nullable*/
        @Override
        public Item mapItem(Item item) throws XPathException {
            context.setLocalVariable(slotNumber, item);
            return action.evaluateItem(context);
        }

    }

    @Override
    public String getStreamerName() {
        return "ForExpression";
    }


}

