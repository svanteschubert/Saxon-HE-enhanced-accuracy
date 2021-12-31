////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.*;
import net.sf.saxon.trans.rules.RuleManager;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.Type;

import java.util.ArrayList;
import java.util.List;


/**
 * An instruction representing an xsl:apply-templates element in the stylesheet
 */

public class ApplyTemplates extends Instruction implements ITemplateCall, ComponentInvocation {

    private Operand selectOp;
    private Operand separatorOp;
    private WithParam[] actualParams;
    private WithParam[] tunnelParams;

    protected boolean useCurrentMode = false;
    protected boolean useTailRecursion = false;
    protected Mode mode;
    protected boolean implicitSelect;
    protected boolean inStreamableConstruct = false;
    protected RuleManager ruleManager;
    private int bindingSlot = -1; // for binding the mode

    protected ApplyTemplates() {
    }

    /**
     * Construct an apply-templates instruction
     *
     * @param select           the select expression
     * @param useCurrentMode   true if mode="#current" was specified
     * @param useTailRecursion true if this instruction is the last in its template
     * @param implicitSelect   true if the select expression is implicit, that is, if there was no explicit
     *                         select expression in the call. This information is used only to make error messages more meaningful.
     * @param mode             the mode specified on apply-templates
     */

    public ApplyTemplates(  Expression select,
                            boolean useCurrentMode,
                            boolean useTailRecursion,
                            boolean implicitSelect,
                            boolean inStreamableConstruct,
                            Mode mode,
                            RuleManager ruleManager) {

        selectOp = new Operand(this, select, OperandRole.SINGLE_ATOMIC);
        init(select, useCurrentMode, useTailRecursion, mode);
        this.implicitSelect = implicitSelect;
        this.inStreamableConstruct = inStreamableConstruct;
        this.ruleManager = ruleManager;
    }

    protected void init(Expression select,
                        boolean useCurrentMode,
                        boolean useTailRecursion,
                        Mode mode) {
        this.setSelect(select);
        this.useCurrentMode = useCurrentMode;
        this.useTailRecursion = useTailRecursion;
        this.mode = mode;
        adoptChildExpression(select);
    }

    /**
     * Set the mode to be used.
     *
     * @param target the attribute set to be used
     */

    public void setMode(SimpleMode target) {
        this.mode = target;
    }

    /**
     * Set the separator expression (Saxon extension)
     */

    public void setSeparatorExpression(Expression separator) {
        separatorOp = new Operand(this, separator, OperandRole.SINGLE_ATOMIC);
    }

    public Expression getSeparatorExpression() {
        return separatorOp == null ? null : separatorOp.getChildExpression();
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
        actualParams = params;
    }

    public void setTunnelParams(WithParam[] params) {
        tunnelParams = params;
    }

    @Override
    public Iterable<Operand> operands() {
        List<Operand> operanda = new ArrayList<>();
        operanda.add(selectOp);
        if (separatorOp != null) {
            operanda.add(separatorOp);
        }
        WithParam.gatherOperands(this, getActualParams(), operanda);
        WithParam.gatherOperands(this, getTunnelParams(), operanda);
        return operanda;
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_APPLY_TEMPLATES;
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
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression).
     *
     *
     *
     * @throws XPathException if an error is discovered during expression
     *                        rewriting
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        WithParam.simplify(getActualParams());
        WithParam.simplify(getTunnelParams());
        setSelect(getSelect().simplify());
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        WithParam.typeCheck(actualParams, visitor, contextInfo);
        WithParam.typeCheck(tunnelParams, visitor, contextInfo);
        try {
            selectOp.typeCheck(visitor, contextInfo);
        } catch (XPathException e) {
            if (implicitSelect) {
                String code = e.getErrorCodeLocalPart();
                if ("XPTY0020".equals(code) || "XPTY0019".equals(code)) {
                    XPathException err = new XPathException("Cannot apply-templates to child nodes when the context item is an atomic value");
                    err.setErrorCode("XTTE0510");
                    err.setIsTypeError(true);
                    throw err;
                } else if ("XPDY0002".equals(code)) {
                    XPathException err = new XPathException("Cannot apply-templates to child nodes when the context item is absent");
                    err.setErrorCode("XTTE0510");
                    err.setIsTypeError(true);
                    throw err;
                }
            }
            throw e;
        }
        adoptChildExpression(getSelect());
        if (Literal.isEmptySequence(getSelect())) {
            return getSelect();
        }
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        WithParam.optimize(visitor, actualParams, contextInfo);
        WithParam.optimize(visitor, tunnelParams, contextInfo);
        selectOp.typeCheck(visitor, contextInfo);  // More info available second time around
        selectOp.optimize(visitor, contextInfo);
        if (Literal.isEmptySequence(getSelect())) {
            return getSelect();
        }

        return this;
    }

    @Override
    public int getIntrinsicDependencies() {
        // If the instruction uses mode="#current", this represents a dependency on the context
        // which means the instruction cannot be loop-lifted or moved to a global variable.
        // We overload the dependency DEPENDS_ON_CURRENT_ITEM to achieve this effect.
        return super.getIntrinsicDependencies() | (useCurrentMode ? StaticProperty.DEPENDS_ON_CURRENT_ITEM : 0);
    }

    public RuleManager getRuleManager() {
        return ruleManager;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings a mutable list of (old binding, new binding) pairs
     *               that is used to update the bindings held in any
     *               local variable references that are copied.
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        ApplyTemplates a2 = new ApplyTemplates(
                getSelect().copy(rebindings), useCurrentMode, useTailRecursion, implicitSelect, inStreamableConstruct, mode, ruleManager);
        a2.setActualParams(WithParam.copy(a2, getActualParams(), rebindings));
        a2.setTunnelParams(WithParam.copy(a2, getTunnelParams(), rebindings));
        ExpressionTool.copyLocationInfo(this, a2);
        a2.ruleManager = ruleManager;
        if (separatorOp != null) {
            a2.setSeparatorExpression(getSeparatorExpression().copy(rebindings));
        }
        return a2;
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

    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        apply(output, context, false);
    }

    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        return apply(output, context, useTailRecursion);
    }


    protected NodeInfo makeSeparator(XPathContext context) throws XPathException {
        NodeInfo separator;
        CharSequence sepValue = separatorOp.getChildExpression().evaluateAsString(context);
        Orphan orphan = new Orphan(context.getConfiguration());
        orphan.setNodeKind(Type.TEXT);
        orphan.setStringValue(sepValue);
        separator = orphan;
        return separator;
    }

    protected TailCall apply(Outputter output, XPathContext context, boolean returnTailCall) throws XPathException {

        Component.M targetMode = getTargetMode(context);
        Mode thisMode = targetMode.getActor();
        NodeInfo separator = null;
        if (separatorOp != null) {
            separator = makeSeparator(context);
        }

        // handle parameters if any

        ParameterSet params = assembleParams(context, getActualParams());
        ParameterSet tunnels = assembleTunnelParams(context, getTunnelParams());

        if (returnTailCall) {
            XPathContextMajor c2 = context.newContext();
            c2.setOrigin(this);
            return new ApplyTemplatesPackage(
                    ExpressionTool.lazyEvaluate(getSelect(), context, false),
                    targetMode, params, tunnels, separator, output, c2, getLocation());
        }

        // Get an iterator to iterate through the selected nodes in original order

        SequenceIterator iter = getSelect().iterate(context);

        // Quick exit if the iterator is empty

        if (iter instanceof EmptyIterator) {
            return null;
        }

        // process the selected nodes now

        XPathContextMajor c2 = context.newContext();
        c2.trackFocus(iter);
        c2.setCurrentMode(targetMode);
        c2.setOrigin(this);
        c2.setCurrentComponent(targetMode);
        if (inStreamableConstruct) {
            c2.setCurrentGroupIterator(null);
        }
        PipelineConfiguration pipe = output.getPipelineConfiguration();
        pipe.setXPathContext(c2);

        try {
            TailCall tc = thisMode.applyTemplates(params, tunnels, separator, output, c2, getLocation());
            while (tc != null) {
                tc = tc.processLeavingTail();
            }
        } catch (StackOverflowError e) {
            XPathException err = new XPathException.StackOverflow(
                    "Too many nested apply-templates calls. The stylesheet may be looping.",
                    SaxonErrorCode.SXLM0001, getLocation());
            err.setXPathContext(context);
            throw err;
        }
        pipe.setXPathContext(context);
        return null;

    }

    /**
     * Establish the target mode, at run-time. This (a) resolves overrides across packages,
     * and (b) handles mode="#current".
     * @param context the dynamic context
     * @return the mode to be used, as a Component
     */

    public Component.M getTargetMode(XPathContext context) {
        Component.M targetMode;
        if (useCurrentMode) {
            targetMode = context.getCurrentMode();
        } else {
            if (bindingSlot >= 0) {
                targetMode = (Component.M)context.getTargetComponent(bindingSlot);
                if (targetMode.getVisibility() == Visibility.ABSTRACT) {
                    throw new AssertionError("Modes cannot be abstract");
                }
            } else {
                // fallback
                targetMode = mode.getDeclaringComponent();
            }
        }
        return targetMode;
    }


    /**
     * Get the select expression
     *
     * @return the select expression
     */

    public Expression getSelectExpression() {
        return getSelect();
    }

    /**
     * Ask if the select expression was implicit
     *
     * @return true if no select attribute was explicitly specified
     */

    public boolean isImplicitSelect() {
        return implicitSelect;
    }

    /**
     * Ask if tail recursion is to be used
     *
     * @return true if tail recursion is used
     */

    public boolean useTailRecursion() {
        return useTailRecursion;
    }

    /**
     * Ask if mode="#current" was specified
     *
     * @return true if mode="#current" was specified
     */

    public boolean usesCurrentMode() {
        return useCurrentMode;
    }

    /**
     * Get the Mode
     *
     * @return the mode, or null if mode="#current" was specified
     */

    public Mode getMode() {
        return mode;
    }

    /**
     * Get the target component if this is known in advance, that is, if the target component
     * is private or final, or in some other cases such as xsl:original. Otherwise, return null.
     *
     * @return the bound component if the binding has been fixed
     */
    @Override
    public Component getFixedTarget() {
        return mode.getDeclaringComponent();
    }

    /**
     * Get the symbolic name of the mode that this invocation references
     *
     * @return the symbolic name of the mode used by this instructon, or null if the instruction uses mode="#current"
     */
    @Override
    public SymbolicName getSymbolicName() {
        return mode==null ? null : mode.getSymbolicName();
    }

    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     * <p>The default implementation of this method assumes that an expression does no navigation other than
     * the navigation done by evaluating its subexpressions, and that the subexpressions are evaluated in the
     * same context as the containing expression. The method must be overridden for any expression
     * where these assumptions do not hold. For example, implementations exist for AxisExpression, ParentExpression,
     * and RootExpression (because they perform navigation), and for the doc(), document(), and collection()
     * functions because they create a new navigation root. Implementations also exist for PathExpression and
     * FilterExpression because they have subexpressions that are evaluated in a different context from the
     * calling expression.</p>
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNodeSet representing the points in the source document that are both reachable by this
     *         expression, and that represent possible results of this expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        // This logic is assuming the mode is streamable (so that called templates can't return streamed nodes)
        PathMap.PathMapNodeSet result = super.addToPathMap(pathMap, pathMapNodeSet);
        result.setReturnable(false);
        return new PathMap.PathMapNodeSet(pathMap.makeNewRoot(this));
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out output destination
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {

        out.startElement("applyT", this);
        if (mode != null && !mode.isUnnamedMode()) {
            out.emitAttribute("mode", mode.getModeName());
        }
        String flags = "";
        if (useCurrentMode) {
            flags = "c";
        }
        if (useTailRecursion) {
            flags += "t";
        }
        if (implicitSelect) {
            flags += "i";
        }
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        out.emitAttribute("bSlot", "" + getBindingSlot());
        out.setChildRole("select");
        getSelect().export(out);
        if (separatorOp != null) {
            out.setChildRole("separator");
            getSeparatorExpression().export(out);
        }
        if (getActualParams().length != 0) {
            WithParam.exportParameters(getActualParams(), out, false);
        }
        if (getTunnelParams().length != 0) {
            WithParam.exportParameters(getTunnelParams(), out, true);
        }
        out.endElement();
    }

    /**
     * Get the select expression
     * @return the select expression
     */

    public Expression getSelect() {
        return selectOp.getChildExpression();
    }

    /**
     * Set the select expression
     * @param select the select expression
     */

    public void setSelect(Expression select) {
        selectOp.setChildExpression(select);
    }

    /**
     * Set the binding slot to be used (for the explicit or implicit reference to the mode).
     * This is the offset within the binding vector of the containing
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
     * Get the binding slot to be used (for the explicit or implicit reference to the mode).
     * This is the offset within the binding vector of the containing
     * component where the actual target template is to be found.
     *
     * @return the offset in the binding vector of the containing package where the target template
     * can be found.
     */

    @Override
    public int getBindingSlot() {
        return bindingSlot;
    }


    /**
     * An ApplyTemplatesPackage is an object that encapsulates the sequence of nodes to be processed,
     * the mode, the parameters to be supplied, and the execution context. This object can be returned as a tail
     * call, so that the actual call is made from a lower point on the stack, allowing a tail-recursive
     * template to execute in a finite stack size
     */

    protected static class ApplyTemplatesPackage implements TailCall {

        private Sequence selectedItems;
        private Component.M targetMode;
        private ParameterSet params;
        private ParameterSet tunnelParams;
        private NodeInfo separator;
        private XPathContextMajor evaluationContext;
        private Outputter output;
        private Location locationId;

        ApplyTemplatesPackage(Sequence selectedItems,
                              Component.M targetMode,
                              ParameterSet params,
                              ParameterSet tunnelParams,
                              NodeInfo separator,
                              Outputter output,
                              XPathContextMajor context,
                              Location locationId) {
            this.selectedItems = selectedItems;
            this.targetMode = targetMode;
            this.params = params;
            this.tunnelParams = tunnelParams;
            this.separator = separator;
            this.output = output;
            evaluationContext = context;
            this.locationId = locationId;
        }

        @Override
        public TailCall processLeavingTail() throws XPathException {
            evaluationContext.trackFocus(selectedItems.iterate());
            evaluationContext.setCurrentMode(targetMode);
            evaluationContext.setCurrentComponent(targetMode);
            return targetMode.getActor().applyTemplates(params, tunnelParams, separator, output, evaluationContext, locationId);
        }
    }

    @Override
    public String getStreamerName() {
        return "ApplyTemplates";
    }

}

