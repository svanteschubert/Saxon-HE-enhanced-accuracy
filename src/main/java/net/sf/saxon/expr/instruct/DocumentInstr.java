////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.Statistics;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.type.*;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.TextFragmentValue;


/**
 * An instruction to create a document node. This corresponds to the xsl:document-node
 * instruction in XSLT. It is also used to support the document node constructor
 * expression in XQuery, and is generated implicitly within an xsl:variable
 * that constructs a temporary tree.
 * <p>Conceptually it represents an XSLT instruction xsl:document-node,
 * with no attributes, whose content is a complex content constructor for the
 * children of the document node.</p>
 */

public class DocumentInstr extends ParentNodeConstructor {

    private final boolean textOnly;
    private final UnicodeString constantText;
    private Statistics treeStatistics = new Statistics();

    /**
     * Create a document constructor instruction
     *
     * @param textOnly     true if the content contains text nodes only
     * @param constantText if the content contains text nodes only and the text is known at compile time,
     *                     supplies the textual content
     */

    public DocumentInstr(boolean textOnly,
                         UnicodeString constantText) {
        this.textOnly = textOnly;
        this.constantText = constantText;
    }

    @Override
    public Iterable<Operand> operands() {
        return contentOp;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered. For instructions this is the process() method.
     */

    @Override
    public int getImplementationMethod() {
        return Expression.EVALUATE_METHOD;
    }

    /**
     * Determine whether this is a "text only" document: essentially, an XSLT xsl:variable that contains
     * a single text node or xsl:value-of instruction.
     *
     * @return true if this is a text-only document
     */

    public boolean isTextOnly() {
        return textOnly;
    }

    /**
     * For a text-only instruction, determine if the text value is fixed and if so return it;
     * otherwise return null
     *
     * @return the fixed text value if appropriate; otherwise null
     */

    public /*@Nullable*/ UnicodeString getConstantText() {
        return constantText;
    }

    /**
     * Check statically that the sequence of child instructions doesn't violate any obvious constraints
     * on the content of the node
     *
     * @throws XPathException if the check fails
     */

    @Override
    protected void checkContentSequence() throws XPathException {
        checkContentSequence(getContentOperand(), getValidationOptions());
    }

    protected static void checkContentSequence(Operand content, ParseOptions validationOptions)
            throws XPathException {
        Operand[] components;
        if (content.getChildExpression() instanceof Block) {
            components = ((Block) content.getChildExpression()).getOperanda();
        } else {
            components = new Operand[]{content};
        }
        int validation = validationOptions == null ? Validation.PRESERVE : validationOptions.getSchemaValidationMode();
        SchemaType type = validationOptions == null ? null : validationOptions.getTopLevelType();
        int elementCount = 0;
        boolean isXSLT = content.getChildExpression().getPackageData().isXSLT();
        for (Operand o : components) {
            Expression component = o.getChildExpression();
            ItemType it = component.getItemType();
            if (it instanceof NodeTest) {
                UType possibleNodeKinds = it.getUType();
                if (possibleNodeKinds.equals(UType.ATTRIBUTE)) {
                    XPathException de = new XPathException("Cannot create an attribute node whose parent is a document node");
                    de.setErrorCode(isXSLT ? "XTDE0420" : "XPTY0004");
                    de.setLocator(component.getLocation());
                    throw de;
                } else if (possibleNodeKinds.equals(UType.NAMESPACE)) {
                    XPathException de = new XPathException("Cannot create a namespace node whose parent is a document node");
                    de.setErrorCode(isXSLT ? "XTDE0420" : "XQTY0024");
                    de.setLocator(component.getLocation());
                    throw de;
                } else if (possibleNodeKinds.equals(UType.ELEMENT)) {
                    elementCount++;
                    if (elementCount > 1 &&
                            (validation == Validation.STRICT || validation == Validation.LAX || type != null)) {
                        XPathException de = new XPathException("A valid document must have only one child element");
                        if (isXSLT) {
                            de.setErrorCode("XTTE1550");
                        } else {
                            de.setErrorCode("XQDY0061");
                        }
                        de.setLocator(component.getLocation());
                        throw de;
                    }
                    if (validation == Validation.STRICT && component instanceof FixedElement) {
                        int fingerprint = ((FixedElement) component).getFixedElementName().getFingerprint();
                        Schema schema = validationOptions.getSchema();
                        IElementDecl decl = schema.getElementDecl(fingerprint);
                        if (decl != null) {
                            ((FixedElement) component).getContentExpression().
                                    checkPermittedContents(decl.getType(), true);
                        }
                    }
                }
            }
        }
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */
    @Override
    protected int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        p |= StaticProperty.SINGLE_DOCUMENT_NODESET;
        if (getValidationAction() == Validation.SKIP) {
            p |= StaticProperty.ALL_NODES_UNTYPED;
        }
        return p;
    }

    /**
     * In the case of a text-only instruction (xsl:variable containing a text node or one or more xsl:value-of
     * instructions), return an expression that evaluates to the textual content as an instance of xs:untypedAtomic
     *
     * @return an expression that evaluates to the textual content
     */

    public Expression getStringValueExpression() {
        if (textOnly) {
            if (constantText != null) {
                return new StringLiteral(StringValue.makeUntypedAtomic(constantText));
            } else if (getContentExpression() instanceof ValueOf) {
                return ((ValueOf) getContentExpression()).convertToCastAsString();
            } else {
                Expression fn = SystemFunction.makeCall(
                        "string-join", getRetainedStaticContext(), getContentExpression(), new StringLiteral(StringValue.EMPTY_STRING));
                CastExpression cast = new CastExpression(fn, BuiltInAtomicType.UNTYPED_ATOMIC, false);
                ExpressionTool.copyLocationInfo(this, cast);
                return cast;
            }
        } else {
            throw new AssertionError("getStringValueExpression() called on non-text-only document instruction");
        }
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
        DocumentInstr doc = new DocumentInstr(textOnly, constantText);
        ExpressionTool.copyLocationInfo(this, doc);
        Schema schema = getSchema();
        doc.setContentExpression(getContentExpression().copy(rebindings));
        doc.setValidationAction(schema, getValidationAction(), getSchemaType());
        return doc;
    }

    /**
     * Get the item type
     *
     * @return the in
     */
    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return NodeKindType.DOCUMENT;
    }

    /**
     * Evaluate as an item.
     */

    @Override
    public NodeInfo evaluateItem(XPathContext context) throws XPathException {
        return (NodeInfo) makeElaborator().elaborateForItem().eval(context);
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     * (the string "document-constructor")
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_DOCUMENT;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("doc", this);
        if (!out.getOptions().relocatable) {
            out.emitAttribute("base", getStaticBaseURIString());
        }
        String flags = "";
        if (textOnly) {
            flags += "t";
        }
        if (isLocal()) {
            flags += "l";
        }
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        if (constantText != null) {
            out.emitAttribute("text", constantText.toString());
        }
        if (getValidationAction() != Validation.SKIP && getValidationAction() != Validation.BY_TYPE) {
            out.emitAttribute("validation", Validation.describe(getValidationAction()));
        }
        final SchemaType schemaType = getSchemaType();
        if (schemaType != null) {
            out.emitAttribute("type", schemaType.getStructuredQName());
        }
        String schemaRole = getRetainedStaticContext().getImportedSchemaRoleName();
        if (!schemaRole.isEmpty()) {
            out.emitAttribute("schemaRole", schemaRole);
        }
        getContentExpression().export(out);
        out.endElement();
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "DocumentInstr";
    }

    @Override
    public Elaborator getElaborator() {
        return new DocumentInstrElaborator();
    }

    /**
     * Elaborator for an AtomicSequenceConverter (including an UntypedAtomicConverter, which is
     * the same except that it uses a different converter internally)
     */

    public static class DocumentInstrElaborator extends PushElaborator {

        public PushEvaluator elaborateForPush() {
            DocumentInstr expr = (DocumentInstr) getExpression();
            PushEvaluator content = expr.getContentExpression().makeElaborator().elaborateForPush();
            if (!expr.textOnly) {
                if (expr.preservingTypes) {
                    return (output, context) -> {
                        output.setSystemId(expr.getStaticBaseURIString());
                        output.startDocument(ReceiverOption.NONE);
                        TailCall tc = content.processLeavingTail(output, context);
                        dispatchTailCall(tc);
                        output.endDocument();
                        return null;
                    };
                } else {
                    Schema schema = expr.getRetainedStaticContext().getImportedSchema();
                    ParseOptions options = expr.getValidationOptions();
                    return (output, context) -> {
                        context.getConfiguration().prepareValidationReporting(context, options);
                        Receiver validator = schema.getDocumentValidator(
                                output, expr.getStaticBaseURIString(), options, expr.getLocation());
                        ComplexContentOutputter outputter = new ComplexContentOutputter(validator);
                        outputter.startDocument(ReceiverOption.NONE);
                        TailCall tc = content.processLeavingTail(outputter, context);
                        dispatchTailCall(tc);
                        outputter.endDocument();
                        return null;
                    };
                }
            } else {
                ItemEvaluator evalAsItem = elaborateForItem();
                Location loc = expr.getLocation();
                return (output, context) -> {
                    Item item = evalAsItem.eval(context);
                    if (item != null) {
                        output.append(item, loc, ReceiverOption.ALL_NAMESPACES);
                    }
                    return null;
                };
            }
        }

        @Override
        public ItemEvaluator elaborateForItem() {
            DocumentInstr expr = (DocumentInstr) getExpression();
            String staticBaseUri = expr.getStaticBaseURIString();

            if (expr.textOnly) {
                if (expr.constantText != null) {
                    UnicodeString text = expr.constantText;
                    return context -> TextFragmentValue.makeTextFragment(
                            context.getConfiguration(), text, staticBaseUri);
                } else {
                    PullEvaluator contentEval = expr.getContentExpression().makeElaborator().elaborateForPull();
                    return context -> {
                        UnicodeBuilder sb = new UnicodeBuilder();
                        SequenceIterator iter = contentEval.iterate(context);
                        for (Item item; (item = iter.next()) != null; ) {
                            sb.accept(item.getUnicodeStringValue());
                        }
                        return TextFragmentValue.makeTextFragment(
                                context.getConfiguration(), sb.toUnicodeString(), staticBaseUri);
                    };
                }
            } else {
                PushEvaluator contentEval = expr.getContentExpression().makeElaborator().elaborateForPush();
                HostLanguage hostLanguage = expr.getPackageData().getHostLanguage();
                int hostLanguageVersion = expr.getPackageData().getHostLanguageVersion();
                return context -> {
                    try {
                        Controller controller = context.getController();
                        PipelineConfiguration pipe = controller.makePipelineConfiguration();
                        pipe.setXPathContext(context);

                        Builder builder;
                        builder = controller.makeBuilder();
                        builder.setUseEventLocation(false);
                        builder.setDurability(Durability.TEMPORARY);

                        if (builder instanceof TinyBuilder) {
                            ((TinyBuilder) builder).setStatistics(expr.treeStatistics);
                        }

                        builder.setBaseURI(staticBaseUri);
                        builder.setTiming(false);

                        pipe.setHostLanguage(hostLanguage, hostLanguageVersion);
                        builder.setPipelineConfiguration(pipe);

                        ComplexContentOutputter out =
                                ComplexContentOutputter.makeComplexContentReceiver(builder, expr.getValidationOptions());
                        out.open();
                        out.startDocument(ReceiverOption.NONE);

                        TailCall tc = contentEval.processLeavingTail(out, context);
                        dispatchTailCall(tc);
                        out.endDocument();
                        out.close();
                        return builder.getCurrentRoot();
                    } catch (XPathException e) {
                        throw e.maybeWithLocation(expr.getLocation()).maybeWithContext(context);
                    }
                };
            }
        }
    }
}

