////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.*;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PushElaborator;
import net.sf.saxon.expr.elab.PushEvaluator;
import net.sf.saxon.expr.elab.StringEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StandardDiagnostics;
import net.sf.saxon.om.*;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.Message;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.str.BMPString;
import net.sf.saxon.str.StringView;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import java.util.Properties;

/**
 * An xsl:message or xsl:assert element in the stylesheet.
 */

public class MessageInstr extends Instruction {

    private final Operand selectOp;
    private final Operand terminateOp;
    private final Operand errorCodeOp;

    private boolean isAssert;

    /**
     * Create an xsl:message instruction
     *
     * @param select    the expression that constructs the message (composite of the select attribute
     *                  and the contained sequence constructor)
     * @param terminate expression that calculates terminate = yes or no.
     * @param errorCode expression used to compute the error code
     */

    public MessageInstr(Expression select, Expression terminate, Expression errorCode) {
        if (errorCode == null) {
            errorCode = new StringLiteral(BMPString.of("Q{" + NamespaceConstant.ERR + "}XTMM9000"));
        }
        selectOp = new Operand(this, select, OperandRole.SINGLE_ATOMIC);
        terminateOp = new Operand(this, terminate, OperandRole.SINGLE_ATOMIC);
        errorCodeOp = new Operand(this, errorCode, OperandRole.SINGLE_ATOMIC);
    }


    public Expression getSelect() {
        return selectOp.getChildExpression();
    }

    public void setSelect(Expression select) {
        selectOp.setChildExpression(select);
    }

    public Expression getTerminate() {
        return terminateOp.getChildExpression();
    }

    public void setTerminate(Expression terminate) {
        terminateOp.setChildExpression(terminate);
    }

    public Expression getErrorCode() {
        return errorCodeOp.getChildExpression();
    }

    public void setErrorCode(Expression errorCode) {
        errorCodeOp.setChildExpression(errorCode);
    }

    @Override
    public Iterable<Operand> operands() {
        return operandList(selectOp, terminateOp, errorCodeOp);
    }


    /**
     * Say whether this instruction is implementing xsl:message or xsl:assert
     *
     * @param isAssert true if this is xsl:assert; false if it is xsl:message
     */

    public void setIsAssert(boolean isAssert) {
        this.isAssert = isAssert;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings the rebinding map
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        MessageInstr exp = new MessageInstr(getSelect().copy(rebindings), getTerminate().copy(rebindings), getErrorCode().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     */

    @Override
    public int getInstructionNameCode() {
        return isAssert ? StandardNames.XSL_ASSERT : StandardNames.XSL_MESSAGE;
    }

    /**
     * Get the item type. To avoid spurious compile-time type errors, we falsely declare that the
     * instruction can return anything
     *
     * @return AnyItemType
     */
    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return AnyItemType.getInstance();
    }

    /**
     * Get the static cardinality. To avoid spurious compile-time type errors, we falsely declare that the
     * instruction returns zero or one items - this is always acceptable
     *
     * @return zero or one
     */

    @Override
    public int getCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_ONE;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true.
     */

    @Override
    public final boolean mayCreateNewNodes() {
        return true;
    }

    /**
     * Perform optimisation of an expression and its subexpressions. This is the third and final
     * phase of static optimization.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor     an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                    The parameter is set to null if it is known statically that the context item will be undefined.
     *                    If the type of the context item is not known statically, the argument is set to
     *                    {@link Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        return super.optimize(visitor, contextInfo);
    }

    @CSharpReplaceBody(code="return new Saxon.Api.Message((Saxon.Api.XdmNode) Saxon.Api.XdmValue.Wrap(content), new Saxon.Api.QName(errorCode), abort, new Saxon.Api.Location(getLocation()));")
    private Message makeMessage(boolean abort, StructuredQName errorCode, NodeInfo content) {
        return new Message((XdmNode) XdmNode.wrap(content), new QName(errorCode), abort, getLocation());
    }

    @CSharpReplaceBody(code="return new Saxon.Ejavax.xml.transform.stream.StreamResult(System.Console.Error);")
    private static Result standardErrorResult() {
        return new StreamResult(System.err);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("message", this);
        out.setChildRole("select");
        getSelect().export(out);
        out.setChildRole("terminate");
        getTerminate().export(out);
        out.setChildRole("error");
        getErrorCode().export(out);
        out.endElement();
    }


    /**
     * The MessageAdapter is a filter applied to the message pipeline which is designed to ensure that outputting an attribute
     * with no containing element (for example &lt;xsl:message select="@x"/&gt;) is not an error. Instead (see bug 6609) we
     * output a text node containing the attribute's value.
     */

    private static class MessageAdapter extends ProxyOutputter {

        public MessageAdapter(Outputter next) {
            super(next);
        }

        @Override
        public void attribute(NodeName attName, SimpleType typeCode, String value, Location location, int properties) throws XPathException {
            try {
                super.attribute(attName, typeCode, value, location, properties);
            } catch (XPathException e) {
                characters(StringView.of(value), location, properties);
                //processingInstruction("attribute", StringView.of("name=\"" + attName.getDisplayName() + "\" value=\"" + value + "\""), location, ReceiverOption.NONE);
            }
        }

        @Override
        public void namespace(String prefix, NamespaceUri namespaceUri, int properties) throws XPathException {
            try {
                super.namespace(prefix, namespaceUri, properties);
            } catch (XPathException e) {
                characters(namespaceUri.toUnicodeString(), Loc.NONE, properties);
                //processingInstruction("namespace", StringView.of("prefix=\"" + prefix + "\" uri=\"" + namespaceUri + "\""), Loc.NONE, ReceiverOption.NONE);
            }
        }

        @Override
        public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
            if (item instanceof NodeInfo) {
                int kind = ((NodeInfo) item).getNodeKind();
                if (kind == Type.ATTRIBUTE || kind == Type.NAMESPACE) {
                    characters(item.getUnicodeStringValue(), locationId, ReceiverOption.NONE);
                    return;
                }
            } else if (item instanceof FunctionItem && !((FunctionItem) item).isArray()) {
                String representation = ((FunctionItem) item).isMap() ? Err.depict(item) : "Function " + Err.depict(item);
                characters(StringView.of(representation), locationId, ReceiverOption.NONE);
                return;
            }
            getNextOutputter().append(item, locationId, copyNamespaces);
        }
    }

    public Elaborator getElaborator() {
        return new MessageInstrElaborator();
    }

    private static class MessageInstrElaborator extends PushElaborator {

        @Override
        public PushEvaluator elaborateForPush() {
            MessageInstr expr = (MessageInstr) getExpression();
            PushEvaluator select = expr.getSelect().makeElaborator().elaborateForPush();
            StringEvaluator terminate = expr.getTerminate().makeElaborator().elaborateForString(true);
            StringEvaluator errorCodeEval = expr.getErrorCode().makeElaborator().elaborateForString(true);
            return (out, context) -> {
                if (!(context.getController() instanceof XsltController)) {
                    // fallback code allowing xsl:message to do something useful when called from
                    // the XSLT function library within free-standing XPath or XQuery, typically when debugging
                    SequenceIterator iter = expr.getSelect().iterate(context);
                    QueryResult.serializeSequence(iter, context.getConfiguration(), standardErrorResult(), new Properties());
                    return null;
                }
                XsltController controller = (XsltController) context.getController();
                if (expr.isAssert && !controller.isAssertionsEnabled()) {
                    return null;
                }

                boolean abort = evaluateBooleanAVT(context, terminate, "terminate", "xsl:message");

                String code;
                try {
                    code = errorCodeEval.eval(context);
                } catch (XPathException err) {
                    // use the error code of the failure in place of the intended error code
                    code = err.getErrorCodeQName().getEQName();
                }

                StructuredQName errorCode;
                try {
                    errorCode = StructuredQName.fromLexicalQName(
                            code, false, StructuredQName.QUPL, expr.getRetainedStaticContext());
                    // Note, we're being a bit over-liberal here: XSLT 3.0 does not allow Q{uri}prefix:local
                    if (errorCode.hasURI(NamespaceUri.ERR) && errorCode.getPrefix().isEmpty()) {
                        // cosmetic - add the err: prefix
                        errorCode = new StructuredQName("err", NamespaceUri.ERR, errorCode.getLocalPart());
                    }
                } catch (XPathException err) {
                    // The spec says we fall back to XTMM9000
                    errorCode = new StructuredQName("err", NamespaceUri.ERR, "XTMM9000");
                }

                controller.incrementMessageCounter(errorCode);

                Builder builder = controller.makeBuilder();
                builder.setDurability(Durability.TEMPORARY);
                builder.setTiming(false);

                ComplexContentOutputter cco = new ComplexContentOutputter(builder);
                Outputter rec = new MessageAdapter(cco);
                rec.open();
                rec.startDocument(abort ? ReceiverOption.TERMINATE : ReceiverOption.NONE);

                try {
                    try {
                        TailCall tc = select.processLeavingTail(rec, context);
                        dispatchTailCall(tc);
                    } catch (UncheckedXPathException e) {
                        throw e.getXPathException();
                    }
                } catch (XPathException e) {
                    int lineno = expr.getLocation().getLineNumber();
                    rec.append(new StringValue("Error " + e.showErrorCode()
                                                       + " while evaluating xsl:message "
                                                       + (lineno > 0 ? "at line " + lineno + " of ": "in ")
                                                       + expr.getLocation().getSystemId()
                                                       + ": " + e.getMessage()));
                }

                rec.endDocument();
                rec.close();
                builder.close();
                NodeInfo content = builder.getCurrentRoot();
                Message message = expr.makeMessage(abort, errorCode, content);
                try {
                    controller.getMessageHandler().accept(message);
                } catch (Exception e) {
                    // Ignore exceptions from the message handler - see bug 6464
                }
                // Allow the message handler to indicate that the message is to be treated as fatal
                String reason = "";
                if (!abort && message.isTerminate()) {
                    abort = true;
                    reason = "user-written message handler for ";
                }
                abort |= message.isTerminate();
                if (abort) {
                    TerminationException te = new TerminationException(
                            "Processing terminated by " + reason + StandardDiagnostics.getInstructionNameDefault(expr) +
                                    " at line " + expr.getLocation().getLineNumber() +
                                    " in " + StandardDiagnostics.abbreviateLocationURIDefault(expr.getLocation().getSystemId()),
                            message);
                    te.setLocation(expr.getLocation());
                    te.setErrorCodeQName(errorCode);
                    te.setErrorObject(content);
                    throw te;
                }
                return null;

            };
        }
    }

    /**
     * Evaluate an expression representing a boolean-valued attribute value template, such as xsl:message/@terminate
     * @param context the evaluation context
     * @param terminate an evaluator for the AVT expression
     * @param attName the name of the attribute (for diagnostics)
     * @param instr the name of the containing instruction (for diagnostics)
     * @return the boolean value of the attribute
     * @throws XPathException if the value is invalid
     */
    public static boolean evaluateBooleanAVT(XPathContext context, StringEvaluator terminate, String attName, String instr) throws XPathException {
        String term = Whitespace.trim(terminate.eval(context));
        return switch (term) {
            case "no", "false", "0" -> false;
            case "yes", "true", "1" -> true;
            default ->
                    throw new XPathException("The " + attName + " attribute of " + instr + " must be yes|true|1 or no|false|0")
                            .withXPathContext(context)
                            .withErrorCode("XTDE0030");
        };
    }

}
