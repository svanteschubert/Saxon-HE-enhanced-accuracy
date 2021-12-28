////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implements a xsl:next-iteration instruction within the body of xsl:iterate
 */
public class NextIteration extends Instruction implements TailCallLoop.TailCallInfo {

    private WithParam[] actualParams = null;

    public NextIteration() {
    }

    public void setParameters(WithParam[] actualParams) {
        this.actualParams = actualParams;
    }

    public WithParam[] getParameters() {
        return actualParams;
    }

    /**
     * Ask whether the expression can be lifted out of a loop, assuming it has no dependencies
     * on the controlling variable/focus of the loop
     * @param forStreaming
     */

    @Override
    public boolean isLiftable(boolean forStreaming) {
        return false;
    }

    /**
     * Get the namecode of the instruction for use in diagnostics
     *
     * @return a code identifying the instruction: typically but not always
     * the fingerprint of a name in the XSLT namespace
     */
    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_NEXT_ITERATION;
    }

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        WithParam.simplify(actualParams);
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        WithParam.typeCheck(actualParams, visitor, contextInfo);
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        NextIteration c2 = new NextIteration();
        ExpressionTool.copyLocationInfo(this, c2);
        c2.actualParams = WithParam.copy(c2, actualParams, rebindings);
        return c2;
    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * works off the results of iterateSubExpressions()
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterable<Operand> operands() {
        List<Operand> list = new ArrayList<Operand>();
        WithParam.gatherOperands(this, actualParams, list);
        return list;
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "NextIteration";
    }

    /*@Nullable*/
    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        XPathContext c = context;
        while (!(c instanceof XPathContextMajor)) {
            c = c.getCaller();
        }
        XPathContextMajor cm = (XPathContextMajor)c;
        if (actualParams.length == 1) {
            cm.setLocalVariable(actualParams[0].getSlotNumber(), actualParams[0].getSelectValue(context));
        } else {
            // we can't overwrite any of the parameters until we've evaluated all of them: test iterate012
            Sequence[] oldVars = cm.getAllVariableValues();
            Sequence[] newVars = Arrays.copyOf(oldVars, oldVars.length);
            for (WithParam wp : actualParams) {
                newVars[wp.getSlotNumber()] = wp.getSelectValue(context);
            }
            cm.resetAllVariableValues(newVars);
        }
        cm.requestTailCall(this, null);
        return null;
    }


    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("nextIteration", this);
        if (actualParams != null && actualParams.length > 0) {
            WithParam.exportParameters(actualParams, out, false);
        }
        out.endElement();
    }


}

