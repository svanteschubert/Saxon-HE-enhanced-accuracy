////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.trans.XPathException;


/**
 * An xsl:apply-imports element in the stylesheet.
 */

public class ApplyImports extends ApplyNextMatchingTemplate implements ITemplateCall {

    public ApplyImports() {
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     */
    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_APPLY_IMPORTS;
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
        ApplyImports ai2 = new ApplyImports();
        ai2.setActualParams(WithParam.copy(ai2, getActualParams(), rebindings));
        ai2.setTunnelParams(WithParam.copy(ai2, getTunnelParams(), rebindings));
        ExpressionTool.copyLocationInfo(this, ai2);
        return ai2;
    }


    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {

        Controller controller = context.getController();
        assert controller != null;

        // handle parameters if any
        ParameterSet params = assembleParams(context, getActualParams());
        ParameterSet tunnels = assembleTunnelParams(context, getTunnelParams());

        Rule currentTemplateRule = context.getCurrentTemplateRule();
        if (currentTemplateRule == null) {
            XPathException e = new XPathException("There is no current template rule");
            e.setXPathContext(context);
            e.setErrorCode("XTDE0560");
            e.setLocation(getLocation());
            throw e;
        }

        int min = currentTemplateRule.getMinImportPrecedence();
        int max = currentTemplateRule.getPrecedence() - 1;
        Component.M modeComponent = context.getCurrentMode();
        if (modeComponent == null) {
            throw new AssertionError("Current mode is null");
        }
        Item currentItem = context.getCurrentIterator().current();

        Mode mode = modeComponent.getActor();
        Rule rule = mode.getRule(currentItem, min, max, context);
        if (rule == null) {             // use the default action for the node
            mode.getBuiltInRuleSet().process(currentItem, params, tunnels, output, context, getLocation());
        } else {
            XPathContextMajor c2 = context.newContext();
            TemplateRule nh = (TemplateRule) rule.getAction();
            nh.initialize();
            c2.setOrigin(this);
            //c2.setOriginatingConstructType(Location.TEMPLATE);
            c2.setLocalParameters(params);
            c2.setTunnelParameters(tunnels);
            c2.openStackFrame(nh.getStackFrameMap());
            c2.setCurrentTemplateRule(rule);
            c2.setCurrentComponent(modeComponent);
            c2.setCurrentMergeGroupIterator(null);
            nh.apply(output, c2);
        }
        return null;
        // We never treat apply-imports as a tail call, though we could
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("applyImports", this);
        out.emitAttribute("flags", "i");  // used to mean "allow any item" i.e. non-nodes
        if (getActualParams().length != 0) {
            WithParam.exportParameters(getActualParams(), out, false);
        }
        if (getTunnelParams().length != 0) {
            WithParam.exportParameters(getTunnelParams(), out, true);
        }
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
        return "ApplyImports";
    }


}

