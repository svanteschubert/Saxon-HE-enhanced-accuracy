////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.TextFragmentValue;
import net.sf.saxon.value.UntypedAtomicValue;


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

    private boolean textOnly;
    /*@Nullable*/ private String constantText;

    /**
     * Create a document constructor instruction
     *
     * @param textOnly     true if the content contains text nodes only
     * @param constantText if the content contains text nodes only and the text is known at compile time,
     *                     supplies the textual content
     */

    public DocumentInstr(boolean textOnly,
                         String constantText) {
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

    public /*@Nullable*/ CharSequence getConstantText() {
        return constantText;
    }

    /**
     * Check statically that the sequence of child instructions doesn't violate any obvious constraints
     * on the content of the node
     *
     * @param env the static context
     * @throws XPathException
     */

    @Override
    protected void checkContentSequence(StaticContext env) throws XPathException {
        checkContentSequence(env, getContentOperand(), getValidationOptions());
    }

    protected static void checkContentSequence(StaticContext env, Operand content, ParseOptions validationOptions)
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
                        SchemaDeclaration decl = env.getConfiguration().getElementDeclaration(
                                ((FixedElement) component).getElementName().getFingerprint());
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
    public int computeSpecialProperties() {
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
                return new StringLiteral(new UntypedAtomicValue(constantText));
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
     * @param rebindings
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        DocumentInstr doc = new DocumentInstr(textOnly, constantText);
        ExpressionTool.copyLocationInfo(this, doc);
        doc.setContentExpression(getContentExpression().copy(rebindings));
        doc.setValidationAction(getValidationAction(), getSchemaType());
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
        return NodeKindTest.DOCUMENT;
    }

    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        if (preservingTypes && !textOnly) {
            output.startDocument(ReceiverOption.NONE);
            getContentExpression().process(output, context);
            output.endDocument();
            return null;
        } else {
            Item item = evaluateItem(context);
            if (item != null) {
                output.append(item, getLocation(), ReceiverOption.ALL_NAMESPACES);
            }
            return null;
        }
    }

    /**
     * Evaluate as an item.
     */

    @Override
    public NodeInfo evaluateItem(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        Configuration config = controller.getConfiguration();

        NodeInfo root;
        if (textOnly) {
            CharSequence textValue;
            if (constantText != null) {
                textValue = constantText;
            } else {
                FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C64);
                SequenceIterator iter = getContentExpression().iterate(context);
                Item item;
                while ((item = iter.next()) != null) {
                    sb.cat(item.getStringValueCS());
                }
                textValue = sb.condense();
            }
            root = TextFragmentValue.makeTextFragment(config, textValue, getStaticBaseURIString());
        } else {
            try {
                PipelineConfiguration pipe = controller.makePipelineConfiguration();
                pipe.setXPathContext(context);

                Builder builder;
                builder = controller.makeBuilder();
                builder.setUseEventLocation(false);

                if (builder instanceof TinyBuilder) {
                    ((TinyBuilder) builder).setStatistics(config.getTreeStatistics().SOURCE_DOCUMENT_STATISTICS);
                }

                builder.setBaseURI(getStaticBaseURIString());
                builder.setTiming(false);

                pipe.setHostLanguage(getPackageData().getHostLanguage());
                builder.setPipelineConfiguration(pipe);

                ComplexContentOutputter out =
                        ComplexContentOutputter.makeComplexContentReceiver(builder, getValidationOptions());
                out.open();
                out.startDocument(ReceiverOption.NONE);

                getContentExpression().process(out, context);

                out.endDocument();
                out.close();
                root = builder.getCurrentRoot();
            } catch (XPathException e) {
                e.maybeSetLocation(getLocation());
                e.maybeSetContext(context);
                throw e;
            }
        }
        return root;
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
        if (!out.isRelocatable()) {
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
            out.emitAttribute("text", constantText);
        }
        if (getValidationAction() != Validation.SKIP && getValidationAction() != Validation.BY_TYPE) {
            out.emitAttribute("validation", Validation.toString(getValidationAction()));
        }
        final SchemaType schemaType = getSchemaType();
        if (schemaType != null) {
            out.emitAttribute("type", schemaType.getStructuredQName());
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
}

