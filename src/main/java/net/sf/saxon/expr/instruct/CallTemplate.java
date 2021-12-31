////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Instruction representing an xsl:call-template element in the stylesheet.
 */

public class CallTemplate extends Instruction implements ITemplateCall, ComponentInvocation {

    private NamedTemplate template; // Null only for saxon:call-template
    private StructuredQName calledTemplateName;   // the name of the called template
    private WithParam[] actualParams = WithParam.EMPTY_ARRAY;
    private WithParam[] tunnelParams = WithParam.EMPTY_ARRAY;
    private boolean useTailRecursion;
    private int bindingSlot = -1;
    private boolean isWithinDeclaredStreamableConstruct;

    /**
     * Construct a CallTemplate instruction.
     * @param template the template to be called. This is provisional, and applies only if the template
     *                 is not overridden in another package; the template supplied will be the one with
     *                 matching name in the same package. Null in the case of saxon:call-template, and also
     *                 in the case where the supplied name is xsl:original
     * @param calledTemplateName the name of the template to be called; null in the case of saxon:call-template
     *                           where this is established dynamically
     * @param useTailRecursion true if the call is potentially tail recursive
     *                         where the name of the called template is to be calculated dynamically
     */

    public CallTemplate(NamedTemplate template, StructuredQName calledTemplateName, boolean useTailRecursion, boolean inStreamable) {
        this.template = template;
        this.calledTemplateName = calledTemplateName;
        this.useTailRecursion = useTailRecursion;
        this.isWithinDeclaredStreamableConstruct = inStreamable;
    }

    /**
     * Set the actual parameters on the call
     *
     * @param actualParams the parameters that are not tunnel parameters
     * @param tunnelParams the tunnel parameters
     */

    public void setActualParameters(
            /*@NotNull*/ WithParam[] actualParams,
            /*@NotNull*/ WithParam[] tunnelParams) {
        this.actualParams = actualParams;
        this.tunnelParams = tunnelParams;
        for (WithParam actualParam : actualParams) {
            adoptChildExpression(actualParam.getSelectExpression());
        }
        for (WithParam tunnelParam : tunnelParams) {
            adoptChildExpression(tunnelParam.getSelectExpression());
        }
    }

    /**
     * Get the name (QName) of the template being called
     * @return the name of the target template
     */

    public StructuredQName getCalledTemplateName() {
        return calledTemplateName;
    }

    /**
     * Get the symbolic name of the template being called. This is essentially the component kind (template)
     * plus the QName of the target template
     *
     * @return the symbolic name of the target template, or null in the case of saxon:call-template where
     * the name is defined dynamically
     */

    @Override
    public SymbolicName getSymbolicName() {
        return calledTemplateName == null ? null : new SymbolicName(StandardNames.XSL_TEMPLATE, calledTemplateName);
    }

    public Component getTarget() {
        return template.getDeclaringComponent();
    }

    @Override
    public Component getFixedTarget() {
        Component c = getTarget();
        Visibility v = c.getVisibility();
        if (v == Visibility.PRIVATE || v == Visibility.FINAL) {
            return c;
        } else {
            return null;
        }
    }

    /**
     * Get the actual parameters passed to the called template
     *
     * @return the non-tunnel parameters
     */

    /*@NotNull*/
    @Override
    public WithParam[] getActualParams() {
        return actualParams;
    }

    /**
     * Get the tunnel parameters passed to the called template
     *
     * @return the tunnel parameters
     */

    /*@NotNull*/
    @Override
    public WithParam[] getTunnelParams() {
        return tunnelParams;
    }

    /**
     * Set the target template
     * @param target the target template
     */

    public void setTargetTemplate(NamedTemplate target) {
        this.template = target;
    }

    /**
     * Get the target template
     *
     * @return the target template
     */

    public NamedTemplate getTargetTemplate() {
        return template;
    }

    /**
     * Ask whether this is a tail call
     *
     * @return true if this is a tail call
     */

    public boolean usesTailRecursion() {
        return useTailRecursion;
    }

    /**
     * Return the name of this instruction.
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_CALL_TEMPLATE;
    }

    /**
     * Set the binding slot to be used. This is the offset within the binding vector of the containing
     * component where the actual target template is to be found. The target template is not held directly
     * in the CallTemplate instruction itself because it can be overridden in a using package.
     *
     * @param slot the offset in the binding vector of the containing package where the target template
     *             can be found.
     */

    @Override
    public void setBindingSlot(int slot) {
        bindingSlot = slot;
    }

    /**
     * Get the binding slot to be used. This is the offset within the binding vector of the containing
     * component where the actual target template is to be found.
     *
     * @return the offset in the binding vector of the containing package where the target template
     *         can be found.
     */

    @Override
    public int getBindingSlot() {
        return bindingSlot;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression).
     *
     * @return the simplified expression
     * @throws XPathException if an error is discovered during expression
     *                        rewriting
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        WithParam.simplify(actualParams);
        WithParam.simplify(tunnelParams);
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        WithParam.typeCheck(actualParams, visitor, contextInfo);
        WithParam.typeCheck(tunnelParams, visitor, contextInfo);
        // For non-tunnel parameters, see if the supplied value is type-safe against the declared
        // type of the value, and if so, avoid the dynamic type check
        // Can't do this check unless the target template has been compiled.
        boolean backwards = visitor.getStaticContext().isInBackwardsCompatibleMode();
        TypeChecker tc = visitor.getConfiguration().getTypeChecker(backwards);
        for (int p = 0; p < actualParams.length; p++) {
            WithParam wp = actualParams[p];
            //int id = wp.getParameterId();
            NamedTemplate.LocalParamInfo lp = template.getLocalParamInfo(wp.getVariableQName());
            if (lp != null) {
                SequenceType req = lp.requiredType;
                RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.PARAM, wp.getVariableQName().getDisplayName(), p);
                role.setErrorCode("XTTE0590");
                Expression select = tc.staticTypeCheck(
                        wp.getSelectExpression(), req, role, visitor);
                wp.setSelectExpression(this, select);
                wp.setTypeChecked(true);
            }
        }

        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        WithParam.optimize(visitor, actualParams, contextItemType);
        WithParam.optimize(visitor, tunnelParams, contextItemType);
        return this;
    }


    /**
     * Get the cardinality of the sequence returned by evaluating this instruction
     *
     * @return the static cardinality
     */

    @Override
    public int computeCardinality() {
        if (template == null) {
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        } else {
            return template.getRequiredType().getCardinality();
        }
    }

    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @return the static item type of the instruction
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        if (template == null) {
            return AnyItemType.getInstance();
        } else {
            return template.getRequiredType().getPrimaryType();
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings Variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        CallTemplate ct = new CallTemplate(template, calledTemplateName, useTailRecursion, isWithinDeclaredStreamableConstruct);
        ExpressionTool.copyLocationInfo(this, ct);
        ct.actualParams = WithParam.copy(ct, actualParams, rebindings);
        ct.tunnelParams = WithParam.copy(ct, tunnelParams, rebindings);
        return ct;
    }

    @Override
    public int getIntrinsicDependencies() {
        // we could go to the called template and find which parts of the context it depends on, but this
        // would create the risk of infinite recursion. So we just assume that the dependencies exist
        return StaticProperty.DEPENDS_ON_XSLT_CONTEXT |
                StaticProperty.DEPENDS_ON_FOCUS;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation currently returns true unconditionally.
     */

    @Override
    public final boolean mayCreateNewNodes() {
        return true;
    }

    @Override
    public Iterable<Operand> operands() {
        ArrayList<Operand> list = new ArrayList<>(10);
        WithParam.gatherOperands(this, actualParams, list);
        WithParam.gatherOperands(this, tunnelParams, list);
        return list;
    }


    /**
     * Process this instruction, without leaving any tail calls.
     *
     *
     * @param output the destination for the result
     * @param context the dynamic context for this transformation
     * @throws XPathException if a dynamic error occurs
     */

    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        NamedTemplate t;
        Component target = getFixedTarget();
        if (bindingSlot >= 0) {
            target = context.getTargetComponent(bindingSlot);
            if (target.isHiddenAbstractComponent()) {
                XPathException err = new XPathException("Cannot call an abstract template (" +
                                                                calledTemplateName.getDisplayName() +
                                                                ") with no implementation", "XTDE3052");
                err.setLocation(getLocation());
                throw err;
            }
        }
        t = (NamedTemplate) target.getActor();
        XPathContextMajor c2 = context.newContext();
        c2.setCurrentComponent(target);
        c2.setOrigin(this);
        c2.openStackFrame(t.getStackFrameMap());
        c2.setLocalParameters(assembleParams(context, actualParams));
        c2.setTunnelParameters(assembleTunnelParams(context, tunnelParams));
        if (isWithinDeclaredStreamableConstruct) {
            c2.setCurrentGroupIterator(null);
        }
        c2.setCurrentMergeGroupIterator(null);

        try {
            TailCall tc = t.expand(output, c2);
            while (tc != null) {
                tc = tc.processLeavingTail();
            }
        } catch (StackOverflowError e) {
            XPathException err = new XPathException.StackOverflow(
                    "Too many nested template or function calls. The stylesheet may be looping.",
                    SaxonErrorCode.SXLM0001, getLocation());
            err.setXPathContext(context);
            throw err;
        }
    }

    /**
     * Process this instruction. If the called template contains a tail call (which may be
     * an xsl:call-template or xsl:apply-templates instruction) then the tail call will not
     * actually be evaluated, but will be returned in a TailCall object for the caller to execute.
     *
     *
     * @param output the destination for the result
     * @param context the dynamic context for this transformation
     * @return an object containing information about the tail call to be executed by the
     *         caller. Returns null if there is no tail call.
     */

    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        if (useTailRecursion) {
            Component targetComponent;
            if (bindingSlot >= 0) {
                targetComponent = context.getTargetComponent(bindingSlot);
            } else {
                targetComponent = getFixedTarget();
            }
            if (targetComponent == null) {
                throw new XPathException("Internal Saxon error: No binding available for call-template instruction", SaxonErrorCode.SXPK0001, this.getLocation());
            }
            if (targetComponent.isHiddenAbstractComponent()) {
                throw new XPathException("Cannot call an abstract template (" +
                                                 calledTemplateName.getDisplayName() +
                                                 ") with no implementation", "XTDE3052", this.getLocation());
            }

            // handle parameters if any

            ParameterSet params = assembleParams(context, actualParams);
            ParameterSet tunnels = assembleTunnelParams(context, tunnelParams);

            // Call the named template. Actually, don't call it; rather construct a call package
            // and return it to the caller, who will then process this package.

            //System.err.println("Call template using tail recursion");
            if (params == null) {                  // bug 490967
                params = ParameterSet.EMPTY_PARAMETER_SET;
            }

            // clear all the local variables: they are no longer needed
            Arrays.fill(context.getStackFrame().getStackFrameValues(), null);

            return new CallTemplatePackage(targetComponent, params, tunnels, this, output, context);

        } else {
            process(output, context);
            return null;
        }
    }

    @Override
    public StructuredQName getObjectName() {
        return template == null ? null : template.getTemplateName();
    }

    /**
     * Export of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("callT", this);
        String flags = "";
        if (template != null && template.getTemplateName() != null) {
            out.emitAttribute("name", template.getTemplateName());
        }
        out.emitAttribute("bSlot", ""+getBindingSlot());
        if (isWithinDeclaredStreamableConstruct) {
            flags += "d";
        }
        if (useTailRecursion) {
            flags += "t";
        }
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        if (actualParams.length > 0) {
            WithParam.exportParameters(actualParams, out, false);
        }
        if (tunnelParams.length > 0) {
            WithParam.exportParameters(tunnelParams, out, true);
        }
        out.endElement();
    }


    /**
     * A CallTemplatePackage is an object that encapsulates the name of a template to be called,
     * the parameters to be supplied, and the execution context. This object can be returned as a tail
     * call, so that the actual call is made from a lower point on the stack, allowing a tail-recursive
     * template to execute in a finite stack size
     */

    public static class CallTemplatePackage implements TailCall {

        private Component targetComponent;
        private ParameterSet params;
        private ParameterSet tunnelParams;
        private CallTemplate instruction;
        private Outputter output;
        private XPathContext evaluationContext;

        /**
         * Construct a CallTemplatePackage that contains information about a call.
         *
         * @param targetComponent   the Template to be called
         * @param params            the parameters to be supplied to the called template
         * @param tunnelParams      the tunnel parameter supplied to the called template
         * @param instruction       the xsl:call-template instruction
         * @param evaluationContext saved context information from the Controller (current mode, etc)
         *                          which must be reset to ensure that the template is called with all the context information
         *                          intact
         */

        public CallTemplatePackage(Component targetComponent,
                                   ParameterSet params,
                                   ParameterSet tunnelParams,
                                   CallTemplate instruction,
                                   Outputter output,
                                   XPathContext evaluationContext) {
            this.targetComponent = targetComponent;
            if (!(targetComponent.getActor() instanceof NamedTemplate)) {
                throw new ClassCastException("Target of call-template must be a named template");
            }
            this.params = params;
            this.tunnelParams = tunnelParams;
            this.instruction = instruction;
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
            // TODO: the idea of tail call optimization is to reuse the caller's stack frame rather than
            // creating a new one. We're doing this for the Java stack, but not for the context stack where
            // local variables are held. It should be possible to avoid creating a new context, and instead
            // to update the existing one in situ.
            NamedTemplate template = (NamedTemplate)targetComponent.getActor();
            XPathContextMajor c2 = evaluationContext.newContext();
            c2.setCurrentComponent(targetComponent);
            c2.setOrigin(instruction);
            c2.setLocalParameters(params);
            c2.setTunnelParameters(tunnelParams);
            c2.openStackFrame(template.getStackFrameMap());
            c2.setCurrentMergeGroupIterator(null);

            // System.err.println("Tail call on template");

            return template.expand(output, c2);
        }
    }

    @Override
    public String toString() {
        // fallback implementation
        FastStringBuffer buff = new FastStringBuffer(FastStringBuffer.C64);
        buff.append("CallTemplate#");
        if (template.getObjectName() != null) {
            buff.append(template.getObjectName().getDisplayName());
        }
        boolean first = true;
        for (WithParam p : getActualParams()) {
            buff.append(first ? "(" : ", ");
            buff.append(p.getVariableQName().getDisplayName());
            buff.append("=");
            buff.append(p.getSelectExpression().toString());
            first = false;
        }
        if (!first) {
            buff.append(")");
        }
        return buff.toString();
    }

    @Override
    public String toShortString() {
        // fallback implementation
        FastStringBuffer buff = new FastStringBuffer(FastStringBuffer.C64);
        buff.append("CallTemplate#");
        buff.append(template.getObjectName().getDisplayName());
        return buff.toString();
    }

    @Override
    public String getStreamerName() {
        return "CallTemplate";
    }

}

