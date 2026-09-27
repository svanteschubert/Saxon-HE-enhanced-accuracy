////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.accum;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.TraceableComponent;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.trans.rules.RuleTarget;

import java.util.function.BiConsumer;

/**
 * This class represents one of the rules making up the definition of an accumulator
 */

public class AccumulatorRule implements RuleTarget, TraceableComponent {

    private Expression newValueExpression;
    private final SlotManager stackFrameMap;
    private final boolean postDescent;
    private boolean capturing;
    private Location location;
    private StructuredQName accumulatorName;

    /**
     * Create a rule
     *
     * @param newValueExpression the expression that computes a new value of the accumulator function
     * @param stackFrameMap      the stack frame used to evaluate this expression
     * @param postDescent true if this is a post-descent rule, false for a pre-descent rule
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


    // TraceableComponent interface


    public Expression getBody() {
        return newValueExpression;
    }

    public void setLocation(Location loc) {
        this.location = loc;
    }

    @Override
    public Location getLocation() {
        return location;
    }

    @Override
    public StructuredQName getObjectName() {
        return null;
    }

    @Override
    public void setBody(Expression expression) {
        newValueExpression = expression;
    }

    public String getTracingTag() {
        return "xsl:accumulator-rule";
    }

    public void setAccumulatorName(StructuredQName name) {
        this.accumulatorName = name;
    }

    @Override
    public void gatherProperties(BiConsumer<String, Object> consumer) {
        if (accumulatorName != null) {
            consumer.accept("name", accumulatorName.getDisplayName());
        }
        consumer.accept("phase", isPostDescent() ? "end" : "start");
    }
}
