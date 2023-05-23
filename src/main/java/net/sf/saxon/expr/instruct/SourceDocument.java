////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.DocumentFn;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.AllElementsSpaceStrippingRule;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.QuitParsingException;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.SequenceType;

import java.util.HashSet;
import java.util.Set;

/**
 * Non-streamable implementation of the xsl:source-document instruction
 */

public class SourceDocument extends Instruction {

    protected Operand hrefOp;
    protected Operand bodyOp;

    protected ParseOptions parseOptions;
    protected Set<? extends Accumulator> accumulators = new HashSet<>();

    public SourceDocument(Expression hrefExp, Expression body, ParseOptions options) {
        hrefOp = new Operand(this, hrefExp, OperandRole.SINGLE_ATOMIC);
        bodyOp = new Operand(this, body, new OperandRole(OperandRole.HAS_SPECIAL_FOCUS_RULES, OperandUsage.TRANSMISSION));
        this.parseOptions = options;
        this.accumulators = options.getApplicableAccumulators();
    }


    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "xsl:source-document";
    }

    public String getExportTag() {
        return "sourceDoc";
    }

    public Expression getHref() {
        return hrefOp.getChildExpression();
    }

    public void setHref(Expression href) {
        hrefOp.setChildExpression(href);
    }

    public Expression getBody() {
        return bodyOp.getChildExpression();
    }

    public void setBody(Expression body) {
        bodyOp.setChildExpression(body);
    }

    public void setUsedAccumulators(Set<? extends Accumulator> used) {
        accumulators = used;
    }

    @Override
    public Iterable<Operand> operands() {
        return operandList(hrefOp, bodyOp);
    }

    /**
     * Ask whether common subexpressions found in the operands of this expression can
     * be extracted and evaluated outside the expression itself. The result is irrelevant
     * in the case of operands evaluated with a different focus, which will never be
     * extracted in this way, even if they have no focus dependency.
     *
     * @return false for this kind of expression
     */
    @Override
    public boolean allowExtractingCommonSubexpressions() {
        return false;
    }

    /**
     * Perform type checking of an expression and its subexpressions. This is the second phase of
     * static optimization.
     */
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        hrefOp.typeCheck(visitor, contextInfo);
        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:stream/href", 0);
        TypeChecker tc = visitor.getConfiguration().getTypeChecker(false);
        hrefOp.setChildExpression(tc.staticTypeCheck(
                hrefOp.getChildExpression(), SequenceType.SINGLE_STRING, role, visitor));
        ContextItemStaticInfo newType =
                getConfiguration().makeContextItemStaticInfo(NodeKindTest.DOCUMENT, false);
        newType.setContextPostureStriding();
        bodyOp.typeCheck(visitor, newType);
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions. This is the third and final
     * phase of static optimization.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException
     *          if an error is discovered during this phase
     *          (typically a type error)
     */
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        ContextItemStaticInfo newType =
                getConfiguration().makeContextItemStaticInfo(NodeKindTest.DOCUMENT, false);
        newType.setContextPostureStriding();

        hrefOp.optimize(visitor, contextItemType);
        bodyOp.optimize(visitor, newType);

        return this;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns a default value of false
     *
     * @return true if the instruction creates new nodes (or if it can't be proved that it doesn't)
     */
    @Override
    public boolean mayCreateNewNodes() {
        return !getBody().hasSpecialProperty(StaticProperty.NO_NODES_NEWLY_CREATED);
    }


    /**
     * Compute the dependencies of an expression, as the union of the
     * dependencies of its subexpressions. (This is overridden for path expressions
     * and filter expressions, where the dependencies of a subexpression are not all
     * propogated). This method should be called only once, to compute the dependencies;
     * after that, getDependencies should be used.
     *
     * @return the depencies, as a bit-mask
     */

    @Override
    public int computeDependencies() {
        // Focus-dependency in the body is not relevant.
        int dependencies = 0;
        dependencies |= getHref().getDependencies();
        dependencies |= getBody().getDependencies() & ~StaticProperty.DEPENDS_ON_FOCUS;
        return dependencies;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */
    @Override
    public int computeSpecialProperties() {
        // Not sure of the general rules here but we'll pick up some special cases which are useful for XQuery streaming
        // use cases written using saxon:stream() - where the body is always a call on snapshot()
        Expression body = getBody();
        if ((body.getSpecialProperties() & StaticProperty.ALL_NODES_NEWLY_CREATED) != 0) {
            return StaticProperty.ORDERED_NODESET | StaticProperty.PEER_NODESET;
        }
        return super.computeSpecialProperties();
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        ExpressionPresenter.ExportOptions options = (ExpressionPresenter.ExportOptions) out.getOptions();
        if ("JS".equals(options.target) && options.targetVersion == 1) {
            throw new XPathException("xsl:source-document is not supported in Saxon-JS 1.*", SaxonErrorCode.SXJS0001);
        }
        out.startElement(getExportTag(), this);
        int validation = parseOptions.getSchemaValidationMode();
        if (validation != Validation.SKIP && validation != Validation.BY_TYPE) {
            out.emitAttribute("validation", validation+"");
        }
        SchemaType schemaType = parseOptions.getTopLevelType();
        if (schemaType != null) {
            out.emitAttribute("schemaType", schemaType.getStructuredQName());
        }
        SpaceStrippingRule xsltStripSpace = getPackageData() instanceof StylesheetPackage
                ? ((StylesheetPackage) getPackageData()).getSpaceStrippingRule()
                : null;
        String flags = "";
        if (parseOptions.getSpaceStrippingRule() == xsltStripSpace) {
            flags += "s";
        } else if (parseOptions.getSpaceStrippingRule() == AllElementsSpaceStrippingRule.getInstance()) {
            flags += "S";
        }
        if (parseOptions.isLineNumbering()) {
            flags += "l";
        }
        if (parseOptions.isExpandAttributeDefaults()) {
            flags += "a";
        }
        if (parseOptions.getDTDValidationMode() == Validation.STRICT) {
            flags += "d";
        }
        if (parseOptions.isXIncludeAware()) {
            flags += "i";
        }
        out.emitAttribute("flags", flags);
        if (accumulators != null && !accumulators.isEmpty()) {
            FastStringBuffer fsb = new FastStringBuffer(256);
            for (Accumulator acc : accumulators) {
                if (!fsb.isEmpty()) {
                    fsb.append(" ");
                }
                fsb.append(acc.getAccumulatorName().getEQName());
            }
            out.emitAttribute("accum", fsb.toString());
        }
        out.setChildRole("href");
        getHref().export(out);
        out.setChildRole("body");
        getBody().export(out);
        out.endElement();
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be re-bound
     */
    @Override
    public Expression copy(RebindingMap rebindings) {
        SourceDocument exp = new SourceDocument(getHref().copy(rebindings), getBody().copy(rebindings), parseOptions);
        exp.setRetainedStaticContext(getRetainedStaticContext());
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    /**
     * ProcessLeavingTail: called to do the real work of this instruction. This method
     * must be implemented in each subclass. The results of the instruction are written
     * to the current Receiver, which can be obtained via the Controller.
     *
     *
     * @param output the destination for the result
     * @param context The dynamic context of the transformation, giving access to the current node,
     *                the current variables, etc.
     * @return null if the instruction has completed execution; or a TailCall indicating
     *         a function call or template call that is delegated to the caller, to be made after the stack has
     *         been unwound so as to save stack space.
     */
    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        try {
            push(output, context);
        } catch (QuitParsingException q) {
            // no action, this is an early exit indicating success
            //throw q;
        } catch (XPathException e) {
            e.maybeSetLocation(getLocation());
            if (e.getErrorCodeQName() == null) {
                e.setErrorCode("FODC0002");
            }
            throw e;
        }
        return null;
    }

    /**
     * Evaluate the instruction in push mode
     * @param context the evaluation context
     * @throws XPathException in the event of a failure
     * @throws QuitParsingException if there was an early exit, that is, if the instruction was evaluated
     * without reading the input to completion
     */

    public void push(Outputter output, XPathContext context) throws XPathException {
        String href = hrefOp.getChildExpression().evaluateAsString(context).toString();
        NodeInfo doc = DocumentFn.makeDoc(href, getStaticBaseURIString(), getPackageData(),
                                          parseOptions, context, getLocation(), false);
        if (doc != null) {
            Controller controller = context.getController();
            if (accumulators != null && controller instanceof XsltController) {
                ((XsltController) controller).getAccumulatorManager().setApplicableAccumulators(
                        doc.getTreeInfo(), accumulators);
            }
            XPathContext c2 = context.newMinorContext();
            c2.setCurrentIterator(new ManualIterator(doc));
            bodyOp.getChildExpression().process(output, c2);
        }
    }


}

