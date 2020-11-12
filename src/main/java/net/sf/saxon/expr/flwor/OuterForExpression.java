////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
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
     * @param rebindings
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

        // First create an iteration of the base sequence.

        // Then create a MappingIterator which applies a mapping function to each
        // item in the base sequence. The mapping function is essentially the "return"
        // expression, wrapped in a MappingAction object that is responsible also for
        // setting the range variable at each step.

        SequenceIterator base = getSequence().iterate(context);
        LookaheadIterator ahead = LookaheadIteratorImpl.makeLookaheadIterator(base);
        if (ahead.hasNext()) {
            MappingFunction map = new MappingAction(context, getLocalSlotNumber(), getAction());
            return new MappingIterator(ahead, map);
        } else {
            context.setLocalVariable(getLocalSlotNumber(), EmptySequence.getInstance());
            return getAction().iterate(context);
        }
    }

    /**
     * Process this expression as an instruction, writing results to the current
     * outputter
     */

    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        SequenceIterator base = getSequence().iterate(context);
        int position = 1;
        int slot = getLocalSlotNumber();
        LookaheadIterator ahead = LookaheadIteratorImpl.makeLookaheadIterator(base);
        if (ahead.hasNext()) {
            while (true) {
                Item item = ahead.next();
                if (item == null) break;
                context.setLocalVariable(slot, item);
                getAction().process(output, context);
            }
        } else {
            context.setLocalVariable(getLocalSlotNumber(), EmptySequence.getInstance());
            getAction().process(output, context);
        }
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
        SequenceIterator base = getSequence().iterate(context);
        int position = 1;
        int slot = getLocalSlotNumber();
        LookaheadIterator ahead = LookaheadIteratorImpl.makeLookaheadIterator(base);
        if (ahead.hasNext()) {
            while (true) {
                Item item = ahead.next();
                if (item == null) break;
                context.setLocalVariable(slot, item);
                getAction().evaluatePendingUpdates(context, pul);
            }
        } else {
            context.setLocalVariable(getLocalSlotNumber(), EmptySequence.getInstance());
            getAction().evaluatePendingUpdates(context, pul);
        }
    }


    @Override
    protected void explainSpecializedAttributes(ExpressionPresenter out) {
        out.emitAttribute("outer", "true");
    }


}

// Copyright (c) 2008-2020 Saxonica Limited


