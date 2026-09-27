////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PushElaborator;
import net.sf.saxon.expr.elab.PushEvaluator;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.TemplateRuleTraceListener;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.trans.rules.Rule;

import java.util.Arrays;


/**
 * An xsl:next-match element in the stylesheet
 */

public class NextMatch extends ApplyNextMatchingTemplate {

    boolean useTailRecursion;

    public NextMatch(boolean useTailRecursion) {
        super();
        this.useTailRecursion = useTailRecursion;
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_NEXT_MATCH;
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
        NextMatch nm2 = new NextMatch(useTailRecursion);
        nm2.setActualParams(WithParam.copy(nm2, getActualParams(), rebindings));
        nm2.setTunnelParams(WithParam.copy(nm2, getTunnelParams(), rebindings));
        ExpressionTool.copyLocationInfo(this, nm2);
        return nm2;
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("nextMatch", this);
        String flags = "i";
        if (useTailRecursion) {
            flags = "t";
        }
        out.emitAttribute("flags", flags);
        if (getActualParams().length != 0) {
            WithParam.exportParameters(getActualParams(), out, false);
        }
        if (getTunnelParams().length != 0) {
            WithParam.exportParameters(getTunnelParams(), out, true);
        }
        out.endElement();
    }

    /**
     * A NextMatchPackage is an object that encapsulates the name of a template to be called,
     * the parameters to be supplied, and the execution context. This object can be returned as a tail
     * call, so that the actual call is made from a lower point on the stack, allowing a tail-recursive
     * template to execute in a finite stack size
     */

    private static class NextMatchPackage implements TailCall {

        private final NextMatch instruction;
        private final Rule rule;
        private final ParameterSet params;
        private final ParameterSet tunnelParams;
        private final Outputter output;
        private final XPathContext evaluationContext;

        /**
         * Construct a NextMatchPackage that contains information about a call.
         * @param instruction       the xsl:next-match instruction
         * @param rule              the rule identifying the Template to be called
         * @param params            the parameters to be supplied to the called template
         * @param tunnelParams      the tunnel parameter supplied to the called template
         * @param evaluationContext saved context information from the Controller (current mode, etc)
         *                          which must be reset to ensure that the template is called with all the context information
         *                          intact
         */

        public NextMatchPackage(NextMatch instruction,
                                Rule rule,
                                ParameterSet params,
                                ParameterSet tunnelParams,
                                Outputter output,
                                XPathContext evaluationContext) {
            this.instruction = instruction;
            this.rule = rule;
            this.params = params;
            this.tunnelParams = tunnelParams;
            this.output = output;
            this.evaluationContext = evaluationContext;
        }

        /**
         * Process the template call encapsulated by this package.
         *
         * @return another TailCall. This will never be the original call, but it may be the next
         *         recursive call. For example, if A calls B which calls C which calls D, then B may return
         *         a TailCall to A representing the call from B to C; when this is processed, the result may be
         *         a TailCall representing the call from C to D.
         * @throws XPathException if a dynamic error occurs
         */

        @Override
        public TailCall processLeavingTail() throws XPathException {
            TemplateRule nh = (TemplateRule) rule.getAction();
            nh.initialize();
            XPathContextMajor c2 = evaluationContext.newContext();
            c2.setOrigin(instruction);
            //c2.setOriginatingConstructType(LocationKind.TEMPLATE);
            c2.setLocalParameters(params);
            c2.setTunnelParameters(tunnelParams);
            c2.openStackFrame(nh.getStackFrameMap());
            c2.setCurrentTemplateRule(rule);
            c2.setCurrentComponent(evaluationContext.getCurrentComponent());
            c2.setCurrentMergeGroupIterator(null);

            // System.err.println("Tail call on template");
            Mode mode = evaluationContext.getCurrentMode().getActor();
            if (mode.isModeTracing()) {
                TemplateRuleTraceListener tracer = ((XsltController)evaluationContext.getController()).getTemplateRuleTraceListener();
                tracer.enter("next-match", instruction.getLocation(), evaluationContext.getContextItem(), nh);
                TailCall tc = nh.applyLeavingTail(output, c2);
                tracer.leave();
                return tc;
            } else {
                return nh.applyLeavingTail(output, c2);
            }
        }
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "NextMatch";
    }

    public Elaborator getElaborator() {
        return new NextMatchElaborator();
    }

    private static class NextMatchElaborator extends PushElaborator {

        @Override
        public PushEvaluator elaborateForPush() {
            NextMatch expr = (NextMatch) getExpression();
            Location loc = expr.getLocation();
            return (output, context) -> {
                Controller controller = context.getController();
                assert controller != null;

                // handle parameters if any

                ParameterSet params = assembleParams(context, expr.getActualParams());
                ParameterSet tunnels = assembleTunnelParams(context, expr.getTunnelParams());

                Rule currentRule = context.getCurrentTemplateRule();
                if (currentRule == null) {
                    throw new XPathException("There is no current template rule", "XTDE0560")
                            .withXPathContext(context)
                            .withLocation(loc);
                }
                Component.M modeComponent = context.getCurrentMode();
                if (modeComponent == null) {
                    throw new AssertionError("Current mode is null");
                }
                Mode mode = modeComponent.getActor();

                Item currentItem = context.getCurrentIterator().current();

                XPathContextMajor c1 = context.newContext();
                c1.setCurrentMode(modeComponent);
                c1.setOrigin(expr);
                c1.setCurrentComponent(modeComponent);
                PipelineConfiguration pipe = output.getPipelineConfiguration();
                pipe.setXPathContext(c1);

                Rule rule;
                try {
                    rule = mode.getNextMatchRule(currentItem, currentRule, c1);
                } catch (XPathException e) {
                    throw e.withLocation(this.getExpression().getLocation());
                }

                if (rule == null) {             // use the default action for the node
                    mode.getBuiltInRuleSet().process(currentItem, params, tunnels, output, context, loc);
                } else if (expr.useTailRecursion) {
                    // clear all the local variables: they are no longer needed
                    Arrays.fill(context.getStackFrame().getStackFrameValues(), null);
                    ((XPathContextMajor) context).setCurrentComponent(modeComponent); // bug 2818
                    return new NextMatchPackage(expr, rule, params, tunnels, output, context);
                } else {
                    TemplateRule nh = (TemplateRule) rule.getAction();
                    nh.initialize();
                    XPathContextMajor c2 = context.newContext();
                    c2.setOrigin(expr);
                    //c2.setOriginatingConstructType(LocationKind.TEMPLATE);
                    c2.openStackFrame(nh.getStackFrameMap());
                    c2.setLocalParameters(params);
                    c2.setTunnelParameters(tunnels);
                    c2.setCurrentTemplateRule(rule);
                    c2.setCurrentComponent(modeComponent); // needed in the case where next-match is called from a named template
                    c2.setCurrentMergeGroupIterator(null);
                    if (mode.isModeTracing()) {
                        TemplateRuleTraceListener tracer = ((XsltController) controller).getTemplateRuleTraceListener();
                        tracer.enter("next-match", loc, currentItem, nh);
                        nh.apply(output, c2);
                        tracer.leave();
                    } else {
                        nh.apply(output, c2);
                    }
                }
                return null;
            };
        }
    }



}

