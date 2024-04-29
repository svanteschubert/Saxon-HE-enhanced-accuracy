////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.*;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.*;
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
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
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
     * with no containing element (for example &lt;xsl:message select="@x"/>) is not an error. Such an attribute is wrapped in
     * a processing instruction so it can exist as a child of a document node.
     */

    private static class MessageAdapter extends ProxyReceiver {

        public MessageAdapter(SequenceReceiver next) {
            super(next);
        }

        @Override
        public void startDocument(int properties) throws XPathException {
            super.startDocument(properties);
            //processingInstruction("error-code", errorCode, location, ReceiverOption.NONE);
        }

        @Override
        public void startElement(NodeName elemName, SchemaType type,
                                 AttributeMap attributes, NamespaceMap namespaces,
                                 Location location, int properties) throws XPathException {
            super.startElement(elemName, type, attributes, namespaces, location, properties);
        }

        @Override
        public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
            if (item instanceof NodeInfo) {
                int kind = ((NodeInfo) item).getNodeKind();
                if (kind == Type.ATTRIBUTE) {
                    String attName = ((NodeInfo)item).getDisplayName();
                    processingInstruction("attribute", StringView.of("name=\"" + attName + "\" value=\"" + item.getUnicodeStringValue() + "\""), locationId, ReceiverOption.NONE);
                    return;
                } else if (kind == Type.NAMESPACE) {
                    String prefix = ((NodeInfo) item).getLocalPart();
                    processingInstruction("namespace", StringView.of("prefix=\"" + prefix + "\" uri=\"" + item.getUnicodeStringValue() + "\""), Loc.NONE, ReceiverOption.NONE);
                    return;
                }
            } else if (item instanceof FunctionItem && !((FunctionItem) item).isArray()) {
                String representation = ((FunctionItem) item).isMap() ? Err.depict(item) : "Function " + Err.depict(item);
                nextReceiver.characters(StringView.of(representation), locationId, ReceiverOption.NONE);
                return;
            }
            nextReceiver.append(item, locationId, copyNamespaces);
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

                boolean abort = false;
                String term = Whitespace.trim(terminate.eval(context));
                switch (term) {
                    case "no":
                    case "false":
                    case "0":
                        // no action
                        break;
                    case "yes":
                    case "true":
                    case "1":
                        abort = true;
                        break;
                    default:
                        throw new XPathException("The terminate attribute of xsl:message must be yes|true|1 or no|false|0")
                                .withXPathContext(context)
                                .withErrorCode("XTDE0030");
                }

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
                            code, false, true, expr.getRetainedStaticContext());
                } catch (XPathException err) {
                    // The spec says we fall back to XTMM9000
                    errorCode = new StructuredQName("err", NamespaceUri.ERR, "XTMM9000");
                }

                controller.incrementMessageCounter(errorCode);

                Builder builder = controller.makeBuilder();
                builder.setDurability(Durability.TEMPORARY);
                builder.setTiming(false);
                Receiver rec = new MessageAdapter(new TreeReceiver(builder));
                rec.open();

                ComplexContentOutputter cco = new ComplexContentOutputter(rec);
                cco.startDocument(abort ? ReceiverOption.TERMINATE : ReceiverOption.NONE);

                try {
                    TailCall tc = select.processLeavingTail(cco, context);
                    dispatchTailCall(tc);
                } catch (XPathException e) {
                    cco.append(StringValue.bmp("Error " + e.showErrorCode() +
                                                       " while evaluating xsl:message at line "
                                                       + expr.getLocation().getLineNumber() + " of " + expr.getLocation().getSystemId() +
                                                       ": " + e.getMessage()));
                }

                cco.endDocument();
                cco.close();
                builder.close();
                NodeInfo content = builder.getCurrentRoot();
                Message message = expr.makeMessage(abort, errorCode, content);
                controller.getMessageHandler().accept(message);
                if (abort) {
                    TerminationException te = new TerminationException(
                            "Processing terminated by " + StandardDiagnostics.getInstructionNameDefault(expr) +
                                    " at line " + expr.getLocation().getLineNumber() +
                                    " in " + StandardDiagnostics.abbreviateLocationURIDefault(expr.getLocation().getSystemId()));
                    te.setLocation(expr.getLocation());
                    te.setErrorCodeQName(errorCode);
                    te.setErrorObject(content);
                    throw te;
                }
                return null;

            };
        }
    }

}
