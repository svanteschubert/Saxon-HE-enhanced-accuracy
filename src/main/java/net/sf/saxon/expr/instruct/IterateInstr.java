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
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.FocusIterator;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.SequenceType;

/**
 * An IterateInstr is the compiled form of an xsl:iterate instruction
 */

public final class IterateInstr extends Instruction implements ContextSwitchingExpression {

    private Operand selectOp;
    private Operand actionOp;
    private Operand initiallyOp;
    private Operand onCompletionOp;

    /**
     * Create an xsl:iterate instruction
     *
     * @param select       the select expression
     * @param initiallyExp the initialization of the xsl:param elements
     * @param action       the body of the xsl:iterate loop
     * @param onCompletion   the expression to be evaluated before final completion, may be null
     */

    public IterateInstr(Expression select, LocalParamBlock initiallyExp, Expression action, /*@Nullable*/ Expression onCompletion) {
        if (onCompletion == null) {
            onCompletion = Literal.makeEmptySequence();
        }
        selectOp = new Operand(this, select, OperandRole.FOCUS_CONTROLLING_SELECT);
        actionOp = new Operand(this, action, OperandRole.FOCUS_CONTROLLED_ACTION);
        initiallyOp = new Operand(this, initiallyExp,
                                  new OperandRole(OperandRole.CONSTRAINED_CLASS,
                                                  OperandUsage.NAVIGATION,
                                                  SequenceType.ANY_SEQUENCE,
                                                  expr -> expr instanceof LocalParamBlock));
        onCompletionOp = new Operand(this, onCompletion,
                                     new OperandRole(OperandRole.USES_NEW_FOCUS, OperandUsage.TRANSMISSION));
    }

    public void setSelect(Expression select) {
        selectOp.setChildExpression(select);
    }

    public LocalParamBlock getInitiallyExp() {
        return (LocalParamBlock)initiallyOp.getChildExpression();
    }

    public void setInitiallyExp(LocalParamBlock initiallyExp) {
        initiallyOp.setChildExpression(initiallyExp);
    }

    public void setAction(Expression action) {
        actionOp.setChildExpression(action);
    }

    public Expression getOnCompletion() {
        return onCompletionOp.getChildExpression();
    }

    public void setOnCompletion(Expression onCompletion) {
        onCompletionOp.setChildExpression(onCompletion);
    }

    @Override
    public Iterable<Operand> operands() {
        return operandList(selectOp, actionOp, initiallyOp, onCompletionOp);
    }



    /**
     * Get the namecode of the instruction for use in diagnostics
     *
     * @return a code identifying the instruction: typically but not always
     *         the fingerprint of a name in the XSLT namespace
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_ITERATE;
    }

    /**
     * Get the select expression (the select attribute of the xsl:iterate)
     *
     * @return the select expression
     */

    @Override
    public Expression getSelectExpression() {
        return selectOp.getChildExpression();
    }


    /**
     * Get the action expression (the content of the xsl:iterate)
     *
     * @return the body of the xsl:iterate loop
     */

    @Override
    public Expression getActionExpression() {
        return actionOp.getChildExpression();
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        selectOp.typeCheck(visitor, contextInfo);
        initiallyOp.typeCheck(visitor, contextInfo);

        ItemType selectType = getSelectExpression().getItemType();
        if (selectType == ErrorType.getInstance()) {
            selectType = AnyItemType.getInstance();
        }

        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(selectType, false);
        cit.setContextSettingExpression(getSelectExpression());
        actionOp.typeCheck(visitor, cit);

        onCompletionOp.typeCheck(visitor, ContextItemStaticInfo.ABSENT);

        if (Literal.isEmptySequence(getOnCompletion())) {
            if (Literal.isEmptySequence(getSelectExpression()) || Literal.isEmptySequence(getActionExpression())) {
                return getOnCompletion();
            }
        }
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        selectOp.optimize(visitor, contextInfo);
        initiallyOp.optimize(visitor, contextInfo);

        ContextItemStaticInfo cit2 = visitor.getConfiguration().makeContextItemStaticInfo(getSelectExpression().getItemType(), false);
        cit2.setContextSettingExpression(getSelectExpression());
        actionOp.optimize(visitor, cit2);

        onCompletionOp.optimize(visitor, ContextItemStaticInfo.ABSENT);

        if (Literal.isEmptySequence(getOnCompletion())) {
            if (Literal.isEmptySequence(getSelectExpression()) || Literal.isEmptySequence(getActionExpression())) {
                return getOnCompletion();
            }
        }

        return this;
    }

    /**
     * Test whether it is possible to generate byte-code for the instruction.
     * This is not possible unless any xsl:break or xsl:next-iteration instructions
     * are part of the same compilation unit, which is not the case if they appear
     * within a try-catch.
     * @return true if the instruction does not contain an xsl:break or xsl:next-iteration instruction
     * within a try/catch block
     */

    public boolean isCompilable() {
        return !containsBreakOrNextIterationWithinTryCatch(this, false);
    }

    private static boolean containsBreakOrNextIterationWithinTryCatch(Expression exp, boolean withinTryCatch) {
        if (exp instanceof BreakInstr || exp instanceof NextIteration) {
            return withinTryCatch;
        } else {
            boolean found = false;
            boolean inTryCatch = withinTryCatch || exp instanceof TryCatch;
            for (Operand o : exp.operands()) {
                if (containsBreakOrNextIterationWithinTryCatch(o.getChildExpression(), inTryCatch)) {
                    found = true;
                    break;
                }
            }
            return found;
        }
    }

    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the data type
     */

    /*@NotNull*/
    @Override
    public final ItemType getItemType() {
        if (Literal.isEmptySequence(getOnCompletion())) {
            return getActionExpression().getItemType();
        } else {
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            return Type.getCommonSuperType(getActionExpression().getItemType(), getOnCompletion().getItemType(), th);
        }
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true if the "action" creates new nodes.
     * (Nodes created by the condition can't contribute to the result).
     */

    @Override
    public final boolean mayCreateNewNodes() {
        return (getActionExpression().getSpecialProperties() &
                getOnCompletion().getSpecialProperties() &
                StaticProperty.NO_NODES_NEWLY_CREATED) == 0;
    }

    /**
     * Ask whether this expression is, or contains, the binding of a given variable
     *
     * @param binding the variable binding
     * @return true if this expression is the variable binding (for example a ForExpression
     * or LetExpression) or if it is a FLWOR expression that binds the variable in one of its
     * clauses.
     */
    @Override
    public boolean hasVariableBinding(Binding binding) {
        LocalParamBlock paramBlock = getInitiallyExp();
        for (Operand o : paramBlock.operands()) {
            LocalParam setter = (LocalParam)o.getChildExpression();
            if (setter == binding) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "Iterate";
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    @Override
    public int getImplementationMethod() {
        return PROCESS_METHOD;
    }

    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    @Override
    public void checkPermittedContents(SchemaType parentType, boolean whole) throws XPathException {
        getActionExpression().checkPermittedContents(parentType, false);
        getOnCompletion().checkPermittedContents(parentType, false);
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings a mutable list of (old binding, new binding) pairs
     *                   that is used to update the bindings held in any
     *                   local variable references that are copied.
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        IterateInstr exp = new IterateInstr(
                getSelectExpression().copy(rebindings),
                (LocalParamBlock) getInitiallyExp().copy(rebindings),
                getActionExpression().copy(rebindings),
                getOnCompletion().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }


    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        FocusIterator iter = c2.trackFocus(getSelectExpression().iterate(context));
        c2.setCurrentTemplateRule(null);
        PipelineConfiguration pipe = output.getPipelineConfiguration();
        pipe.setXPathContext(c2);

        boolean tracing = context.getController().isTracing();
        TraceListener listener = tracing ? context.getController().getTraceListener() : null;

        getInitiallyExp().process(output, context);

        while (true) {
            Item item = iter.next();
            if (item != null) {
                if (tracing) {
                    listener.startCurrentItem(item);
                }
                getActionExpression().process(output, c2);
                if (tracing) {
                    listener.endCurrentItem(item);
                }
                TailCallLoop.TailCallInfo comp = c2.getTailCallInfo();
                if (comp == null) {
                    // no xsl:next-iteration or xsl:break was encountered; just loop around
                } else if (comp instanceof BreakInstr) {
                    // indicates a xsl:break instruction was encountered: break the loop
                    //System.err.println("IterateInstr found xsl:break");
                    iter.close();
                    return null;
                } else {
                    // a xsl:next-iteration instruction was encountered.
                    // It will have reset the parameters to the loop; we just need to loop round
                }
            } else {
                // Execute on-completion instruction
                XPathContextMinor c3 = context.newMinorContext();
                c3.setCurrentIterator(null);
                getOnCompletion().process(output, c3);
                break;
            }
        }
        pipe.setXPathContext(context);
        return null;
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("iterate", this);
        out.setChildRole("select");
        getSelectExpression().export(out);
        out.setChildRole("params");
        getInitiallyExp().export(out);
        if (!Literal.isEmptySequence(getOnCompletion())) {
            out.setChildRole("on-completion");
            getOnCompletion().export(out);
        }
        out.setChildRole("action");
        getActionExpression().export(out);
        out.endElement();
    }



}



