////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.accum;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.trans.rules.RuleTarget;
import net.sf.saxon.trans.XPathException;

/**
 * This class represents one of the rules making up the definition of an accumulator
 */

public class AccumulatorRule implements RuleTarget {

    private Expression newValueExpression;
    private SlotManager stackFrameMap;
    private boolean postDescent;
    private boolean capturing;

    /**
     * Create a rule
     *
     * @param newValueExpression the expression that computes a new value of the accumulator function
     * @param stackFrameMap      the stack frame used to evaluate this expression
     */

    public AccumulatorRule(Expression newValueExpression, SlotManager stackFrameMap, boolean postDescent) {
        this.newValueExpression = newValueExpression;
        this.stackFrameMap = stackFrameMap;
        this.postDescent = postDescent;
    }

    public Expression getNewValueExpression() {
        return newValueExpression;
    }

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        newValueExpression.export(out);
    }

    public SlotManager getStackFrameMap() {
        return stackFrameMap;
    }

    /**
     * Register a rule for which this is the target
     *
     * @param rule a rule in which this is the target
     */
    @Override
    public void registerRule(Rule rule) {
        // no action
    }

    public void setCapturing(boolean capturing) {
        this.capturing = capturing;
    }

    public boolean isCapturing() {
        return capturing;
    }

    public boolean isPostDescent() {
        return postDescent;
    }
}
