////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Operand;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.List;


/**
 * An xsl:apply-imports or xsl:next-match element in the stylesheet.
 */

public abstract class ApplyNextMatchingTemplate extends Instruction implements ITemplateCall {

    private WithParam[] actualParams;
    private WithParam[] tunnelParams;

    public ApplyNextMatchingTemplate() {
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered. For instructions this is the process() method.
     */

    @Override
    public int getImplementationMethod() {
        return super.getImplementationMethod() | Expression.WATCH_METHOD;
    }


    /**
     * Get the actual parameters passed to the called template
     *
     * @return the non-tunnel parameters
     */


    @Override
    public WithParam[] getActualParams() {
        return actualParams;
    }

    /**
     * Get the tunnel parameters passed to the called template
     *
     * @return the tunnel parameters
     */


    @Override
    public WithParam[] getTunnelParams() {
        return tunnelParams;
    }

    public void setActualParams(WithParam[] params) {
        this.actualParams = params;
    }

    public void setTunnelParams(WithParam[] params) {
        this.tunnelParams = params;
    }

    @Override
    public Iterable<Operand> operands() {
        List<Operand> operanda = new ArrayList<Operand>(actualParams.length + tunnelParams.length);
        WithParam.gatherOperands(this, actualParams, operanda);
        WithParam.gatherOperands(this, tunnelParams, operanda);
        return operanda;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression).
     *
     *
     *
     * @return the simplified expression
     * @throws XPathException
     *          if an error is discovered during expression
     *          rewriting
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        WithParam.simplify(getActualParams());
        WithParam.simplify(getTunnelParams());
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        WithParam.typeCheck(actualParams, visitor, contextInfo);
        WithParam.typeCheck(tunnelParams, visitor, contextInfo);
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        WithParam.optimize(visitor, actualParams, contextInfo);
        WithParam.optimize(visitor, tunnelParams, contextInfo);
        return this;
    }

    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true (which is almost invariably the case, so it's not worth
     * doing any further analysis to find out more precisely).
     */

    @Override
    public final boolean mayCreateNewNodes() {
        return true;
    }


}

