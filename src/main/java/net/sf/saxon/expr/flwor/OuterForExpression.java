////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.tree.iter.LookaheadIteratorImpl;
import net.sf.saxon.value.EmptySequence;

/**
 * Expression class that implements the "outer for" clause of XQuery 3.0
 */
public class OuterForExpression extends ForExpression {

    /**
     * Get the cardinality of the range variable
     *
     * @return the cardinality of the range variable (StaticProperty.EXACTLY_ONE).
     *         in a subclass
     */

    @Override
    protected int getRangeVariableCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_ONE;
    }

    /**
     * Optimize the expression
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        // Very conservative optimization for the time being

        Expression sequence0 = getSequence();
        getSequenceOp().optimize(visitor, contextItemType);
        Expression action0 = getAction();
        getActionOp().optimize(visitor, contextItemType);

        if (sequence0 != getSequence() || action0 != getAction()) {
            // it's now worth re-attempting the "where" clause optimizations
            return optimize(visitor, contextItemType);
        }

        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings the rebinding map
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        OuterForExpression forExp = new OuterForExpression();
        ExpressionTool.copyLocationInfo(this, forExp);
        forExp.setRequiredType(requiredType);
        forExp.setVariableQName(variableName);
        forExp.setSequence(getSequence().copy(rebindings));
        rebindings.put(this, forExp);
        Expression newAction = getAction().copy(rebindings);
        forExp.setAction(newAction);
        forExp.variableName = variableName;
        return forExp;
    }

    /**
     * Iterate over the result of the expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForPull().iterate(context);
    }

    /**
     * Process this expression as an instruction, writing results to the current
     * outputter
     */

    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        dispatchTailCall(makeElaborator().elaborateForPush().processLeavingTail(output, context));
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
        return "outerFor";
    }

    protected String allowingEmptyString() {
        return " allowing empty";
    }


    @Override
    protected void explainSpecializedAttributes(ExpressionPresenter out) {
        out.emitAttribute("outer", "true");
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new OuterForExprElaborator();
    }

    /**
     * An elaborator for a "for" expression, typically written as {for $x in SEQ return R}.
     *
     * <p>Provides both "pull" and "push" implementations.</p>
     */

    public static class OuterForExprElaborator extends PullElaborator {

        public PullEvaluator elaborateForPull() {
            final ForExpression expr = (ForExpression) getExpression();
            final PullEvaluator selectEval = expr.getSequence().makeElaborator().elaborateForPull();
            final int actionCardinality = expr.getAction().getCardinality();
            final int slot = expr.getLocalSlotNumber();
            final PullEvaluator actionEval = expr.getAction().makeElaborator().elaborateForPull();
            return context -> {
                SequenceIterator base = selectEval.iterate(context);
                LookaheadIterator ahead = LookaheadIteratorImpl.makeLookaheadIterator(base);
                if (ahead.hasNext()) {
                    return MappingIterator.map(ahead, item -> {
                        context.setLocalVariable(slot, item);
                        return actionEval.iterate(context);
                    });
                } else {
                    context.setLocalVariable(slot, EmptySequence.getInstance());
                    return actionEval.iterate(context);
                }
            };
        }

        @Override
        public PushEvaluator elaborateForPush() {
            final ForExpression expr = (ForExpression) getExpression();
            final PullEvaluator selectEval = expr.getSequence().makeElaborator().elaborateForPull();
            final PushEvaluator actionEval = expr.getAction().makeElaborator().elaborateForPush();
            final int slot = expr.getLocalSlotNumber();
            return (output, context) -> {
                SequenceIterator base = selectEval.iterate(context);
                LookaheadIterator ahead = LookaheadIteratorImpl.makeLookaheadIterator(base);
                if (ahead.hasNext()) {
                    while (true) {
                        Item item = ahead.next();
                        if (item == null) break;
                        context.setLocalVariable(slot, item);
                        dispatchTailCall(actionEval.processLeavingTail(output, context));
                    }
                } else {
                    context.setLocalVariable(slot, EmptySequence.getInstance());
                    dispatchTailCall(actionEval.processLeavingTail(output, context));
                }
                return null;
            };
        }

        @Override
        public UpdateEvaluator elaborateForUpdate() {
            final ForExpression expr = (ForExpression) getExpression();
            final PullEvaluator selectEval = expr.getSequence().makeElaborator().elaborateForPull();
            final UpdateEvaluator actionEval = expr.getAction().makeElaborator().elaborateForUpdate();
            final int slot = expr.getLocalSlotNumber();
            return (context, pul) -> {
                SequenceIterator base = selectEval.iterate(context);
                LookaheadIterator ahead = LookaheadIteratorImpl.makeLookaheadIterator(base);
                if (ahead.hasNext()) {
                    while (true) {
                        Item item = ahead.next();
                        if (item == null) {
                            break;
                        }
                        context.setLocalVariable(slot, item);
                        actionEval.registerUpdates(context, pul);
                    }
                } else {
                    context.setLocalVariable(slot, EmptySequence.getInstance());
                    actionEval.registerUpdates(context, pul);
                }
            };
        }
    }

}

// Copyright (c) 2008-2023 Saxonica Limited


