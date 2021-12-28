////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NoNamespaceName;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.*;


/**
 * An xsl:processing-instruction element in the stylesheet, or a processing-instruction
 * constructor in a query
 */

public class ProcessingInstruction extends SimpleNodeConstructor {

    private Operand nameOp;

    /**
     * Create an xsl:processing-instruction instruction
     *
     * @param name the expression used to compute the name of the generated
     *             processing-instruction
     */

    public ProcessingInstruction(Expression name) {
        nameOp = new Operand(this, name, OperandRole.SINGLE_ATOMIC);
    }


    public Expression getNameExp() {
        return nameOp.getChildExpression();
    }

    public void setNameExp(Expression nameExp) {
        nameOp.setChildExpression(nameExp);
    }

    @Override
    public Iterable<Operand> operands() {
        return operandList(selectOp, nameOp);
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     *
     * @return the string "xsl:processing-instruction"
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_PROCESSING_INSTRUCTION;
    }

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return NodeKindTest.PROCESSING_INSTRUCTION;
    }

    @Override
    public int getCardinality() {
        return StaticProperty.EXACTLY_ONE;
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
        ProcessingInstruction exp = new ProcessingInstruction(getNameExp().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        exp.setSelect(getSelect().copy(rebindings));
        return exp;
    }

    @Override
    public void localTypeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        StaticContext env = visitor.getStaticContext();
        nameOp.typeCheck(visitor, contextItemType);

        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "processing-instruction/name", 0);
        // See bug 2110. XQuery does not use the function conversion rules here, and disallows xs:anyURI.
        // In XSLT the name is an AVT so we automatically get a string; in XQuery we'll use the standard
        // mechanism to get an atomic value, and then check the type "by hand" at run time.
        setNameExp(visitor.getConfiguration().getTypeChecker(false).staticTypeCheck(
                getNameExp(), SequenceType.SINGLE_ATOMIC, role, visitor));
        Expression nameExp = getNameExp();
        adoptChildExpression(nameExp);

        // Do early checking of name if known statically

        if (nameExp instanceof Literal && ((Literal)nameExp).getValue() instanceof AtomicValue) {
            AtomicValue val = (AtomicValue) ((Literal) nameExp).getValue();
            checkName(val, env.makeEarlyEvaluationContext());
        }

        // Do early checking of content if known statically

        if (getSelect() instanceof Literal) {
            String s = ((Literal) getSelect()).getValue().getStringValue();
            String s2 = checkContent(s, env.makeEarlyEvaluationContext());
            if (!s2.equals(s)) {
                setSelect(new StringLiteral(s2));
            }
        }
    }

    @Override
    public int getDependencies() {
        return getNameExp().getDependencies() | super.getDependencies();
    }


    /**
     * Process the value of the node, to create the new node.
     *
     * @param value   the string value of the new node
     * @param output the destination for the result
     * @param context the dynamic evaluation context
     * @throws XPathException
     */

    @Override
    public void processValue(CharSequence value, Outputter output, XPathContext context) throws XPathException {
        String expandedName = evaluateName(context);
        if (expandedName != null) {
            String data = checkContent(value.toString(), context);
            output.processingInstruction(expandedName, data, getLocation(), ReceiverOption.NONE);
        }
    }

    /**
     * Check the content of the node, and adjust it if necessary
     *
     * @param data the supplied content
     * @return the original content, unless adjustments are needed
     * @throws XPathException if the content is invalid
     */

    @Override
    protected String checkContent(String data, XPathContext context) throws XPathException {
        if (isXSLT()) {
            return checkContentXSLT(data);
        } else {
            try {
                return checkContentXQuery(data);
            } catch (XPathException err) {
                err.setXPathContext(context);
                err.setLocation(getLocation());
                throw err;
            }
        }
    }

    /**
     * Check the content of the node, and adjust it if necessary, using the XSLT rules
     *
     * @param data the supplied content
     * @return the original content, unless adjustments are needed
     */

    public static String checkContentXSLT(String data) {
        int hh;
        while ((hh = data.indexOf("?>")) >= 0) {
            data = data.substring(0, hh + 1) + ' ' + data.substring(hh + 1);
        }
        return Whitespace.removeLeadingWhitespace(data).toString();
    }

    /**
     * Check the content of the node, and adjust it if necessary, using the XQuery rules
     *
     * @param data the supplied content
     * @return the original content, unless adjustments are needed
     * @throws XPathException if the content is invalid
     */

    public static String checkContentXQuery(String data) throws XPathException {
        if (data.contains("?>")) {
            throw new XPathException("Invalid characters (?>) in processing instruction", "XQDY0026");
        }
        return Whitespace.removeLeadingWhitespace(data).toString();
    }

    @Override
    public NodeName evaluateNodeName(XPathContext context) throws XPathException {
        String expandedName = evaluateName(context);
        return new NoNamespaceName(expandedName);
    }

    /**
     * Evaluate the name of the processing instruction.
     *
     * @param context the dynamic evaluation context
     * @return the name of the processing instruction (an NCName), or null, indicating an invalid name
     * @throws XPathException if evaluation fails, or if the recoverable error is treated as fatal
     */
    private String evaluateName(XPathContext context) throws XPathException {
        AtomicValue av = (AtomicValue) getNameExp().evaluateItem(context);
        if (av instanceof StringValue && !(av instanceof AnyURIValue)) {
            // Always true under XSLT
            return checkName(av, context);
        } else {
            XPathException e = new XPathException("Processing instruction name is not a string");
            e.setXPathContext(context);
            e.setErrorCode("XPTY0004");
            throw dynamicError(getLocation(), e, context);
        }
    }

    private String checkName(AtomicValue name, XPathContext context) throws XPathException {
        if (name instanceof StringValue && !(name instanceof AnyURIValue)) {
            String expandedName = Whitespace.trim(name.getStringValue());
            if (!NameChecker.isValidNCName(expandedName)) {
                XPathException e = new XPathException("Processing instruction name " + Err.wrap(expandedName) + " is not a valid NCName");
                e.setXPathContext(context);
                e.setErrorCode(isXSLT() ? "XTDE0890" : "XQDY0041");
                throw dynamicError(getLocation(), e, context);
            }
            if (expandedName.equalsIgnoreCase("xml")) {
                XPathException e = new XPathException("Processing instructions cannot be named 'xml' in any combination of upper/lower case");
                e.setXPathContext(context);
                e.setErrorCode(isXSLT() ? "XTDE0890" : "XQDY0064");
                throw dynamicError(getLocation(), e, context);
            }
            return expandedName;
        } else {
            XPathException e = new XPathException("Processing instruction name " + Err.wrap(name.getStringValue()) +
                                                          " is not of type xs:string or xs:untypedAtomic");
            e.setXPathContext(context);
            e.setErrorCode("XPTY0004");
            e.setIsTypeError(true);
            throw dynamicError(getLocation(), e, context);
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("procInst", this);
        String flags = "";
        if (isLocal()) {
            flags += "l";
        }
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        out.setChildRole("name");
        getNameExp().export(out);
        out.setChildRole("select");
        getSelect().export(out);
        out.endElement();
    }


}

