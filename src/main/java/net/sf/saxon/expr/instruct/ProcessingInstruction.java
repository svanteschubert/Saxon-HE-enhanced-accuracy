////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NoNamespaceName;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.StringConstants;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.*;

import java.util.function.Supplier;


/**
 * An xsl:processing-instruction element in the stylesheet, or a processing-instruction
 * constructor in a query
 */

public class ProcessingInstruction extends SimpleNodeConstructor {

    private final Operand nameOp;

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
     * @param rebindings variables to be re-bound
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

        Supplier<RoleDiagnostic> role = () -> new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "processing-instruction/name", 0);
        // See bug 2110. XQuery does not use the function conversion rules here, and disallows xs:anyURI.
        // In XSLT the name is an AVT so we automatically get a string; in XQuery we'll use the standard
        // mechanism to get an atomic value, and then check the type "by hand" at run time.
        setNameExp(visitor.getConfiguration().getTypeChecker(false).staticTypeCheck(
                getNameExp(), SequenceType.SINGLE_ATOMIC, role, visitor));
        Expression nameExp = getNameExp();
        adoptChildExpression(nameExp);

        // Do early checking of name if known statically

        if (nameExp instanceof Literal && ((Literal)nameExp).getGroundedValue() instanceof AtomicValue) {
            AtomicValue val = (AtomicValue) ((Literal) nameExp).getGroundedValue();
            checkName(val, env.makeEarlyEvaluationContext());
        }

        // Do early checking of content if known statically

        if (getSelect() instanceof StringLiteral) {
            UnicodeString s = ((StringLiteral) getSelect()).getGroundedValue().getUnicodeStringValue();
            UnicodeString s2 = checkContent(s, env.makeEarlyEvaluationContext());
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
     * @throws XPathException if things go wrong
     */

    @Override
    public void processValue(UnicodeString value, Outputter output, XPathContext context) throws XPathException {
        String expandedName = evaluateName(context);
        UnicodeString data = checkContent(value, context);
        output.processingInstruction(expandedName, data, getLocation(), ReceiverOption.NONE);
    }

    /**
     * Check the content of the node, and adjust it if necessary
     *
     * @param data the supplied content
     * @return the original content, unless adjustments are needed
     * @throws XPathException if the content is invalid
     */

    @Override
    public UnicodeString checkContent(UnicodeString data, XPathContext context) throws XPathException {
        if (isXSLT()) {
            return checkContentXSLT(data);
        } else {
            try {
                return checkContentXQuery(data);
            } catch (XPathException err) {
                throw err.withLocation(getLocation()).withXPathContext(context);
            }
        }
    }

    /**
     * Check the content of the node, and adjust it if necessary, using the XSLT rules
     *
     * @param data the supplied content
     * @return the original content, unless adjustments are needed
     */

    public static UnicodeString checkContentXSLT(UnicodeString data) {
        long hh;
        while ((hh = data.indexOf(PI_TERMINATOR, 0)) >= 0) {
            data = data.substring(0, hh + 1).concat(StringConstants.SINGLE_SPACE.concat(data.substring(hh + 1)));
        }
        return Whitespace.removeLeadingWhitespace(data);
    }

    private final static UnicodeString PI_TERMINATOR = new Twine8(StringConstants.PI_END);

    /**
     * Check the content of the node, and adjust it if necessary, using the XQuery rules
     *
     * @param data the supplied content
     * @return the original content, unless adjustments are needed
     * @throws XPathException if the content is invalid
     */

    public static UnicodeString checkContentXQuery(UnicodeString data) throws XPathException {
        if (data.indexOf(PI_TERMINATOR, 0) >= 0) {
            throw new XPathException("Invalid characters (?>) in processing instruction", "XQDY0026");
        }
        return Whitespace.removeLeadingWhitespace(data);
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
            XPathException e = new XPathException("Processing instruction name is not a string")
                    .withXPathContext(context)
                    .withErrorCode("XPTY0004");
            throw dynamicError(getLocation(), e, context);
        }
    }

    public String checkName(AtomicValue name, XPathContext context) throws XPathException {
        if (name instanceof StringValue && !(name instanceof AnyURIValue)) {
            String expandedName = Whitespace.trim(name.getStringValue());
            if (!NameChecker.isValidNCName(expandedName)) {
                XPathException e = new XPathException("Processing instruction name " + Err.wrap(expandedName) + " is not a valid NCName")
                        .withXPathContext(context)
                        .withErrorCode(isXSLT() ? "XTDE0890" : "XQDY0041");
                throw dynamicError(getLocation(), e, context);
            }
            if (expandedName.equalsIgnoreCase("xml")) {
                XPathException e = new XPathException("Processing instructions cannot be named 'xml' in any combination of upper/lower case")
                        .withXPathContext(context)
                        .withErrorCode(isXSLT() ? "XTDE0890" : "XQDY0064");
                throw dynamicError(getLocation(), e, context);
            }
            return expandedName;
        } else {
            XPathException e = new XPathException("Processing instruction name " + Err.wrap(name.getUnicodeStringValue()) +
                                                          " is not of type xs:string or xs:untypedAtomic")
                    .withXPathContext(context)
                    .withErrorCode("XPTY0004")
                    .asTypeError();
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
        if (isLocal()) {
            out.emitAttribute("flags", "l");
        }
        out.setChildRole("name");
        getNameExp().export(out);
        out.setChildRole("select");
        getSelect().export(out);
        out.endElement();
    }


    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new ProcessingInstructionElaborator();
    }


    private static class ProcessingInstructionElaborator extends SimpleNodePushElaborator {
        @Override
        public PushEvaluator elaborateForPush() {
            ProcessingInstruction expr = (ProcessingInstruction) getExpression();
            Location loc = expr.getLocation();
            UnicodeStringEvaluator contentEval = expr.getSelect().makeElaborator().elaborateForUnicodeString(true);
            ItemEvaluator nameEval = expr.getNameExp().makeElaborator().elaborateForItem();
            if (expr.isXSLT()) {
                return (out, context) -> {
                    StringValue name = (StringValue)nameEval.eval(context);
                    String checkedName = expr.checkName(name, context);
                    UnicodeString content = contentEval.eval(context);
                    content = ProcessingInstruction.checkContentXSLT(content);
                    out.processingInstruction(checkedName, content, loc, ReceiverOption.NONE);
                    return null;
                };
            } else {
                return (out, context) -> {
                    AtomicValue name = (AtomicValue)nameEval.eval(context);
                    String checkedName = expr.checkName(name, context);
                    UnicodeString content = contentEval.eval(context);
                    ProcessingInstruction.checkContentXQuery(content);
                    out.processingInstruction(checkedName, content, loc, ReceiverOption.NONE);
                    return null;
                };
            }
        }
    }


}

