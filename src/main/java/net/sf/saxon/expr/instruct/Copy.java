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
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;

import java.util.Iterator;


/**
 * Handler for xsl:copy elements in stylesheet. This only handles copying of the context item. An xsl:copy
 * with a select attribute is handled by wrapping the instruction in an xsl:for-each.
 */

public class Copy extends ElementCreator {

    private boolean copyNamespaces;
    private ItemType selectItemType = AnyItemType.getInstance();
    private ItemType resultItemType;

    /**
     * Create a shallow copy instruction
     *
     * param select            selects the node (or other item) to be copied. Never null.
     * param selectSpecified   true if the select attribute of xsl:copy was specified explicitly (in which
     *                          case the context for evaluating the body will change)
     * @param copyNamespaces    true if namespace nodes are to be copied when copying an element
     * @param inheritNamespaces true if child elements are to inherit the namespace nodes of their parent
     * @param schemaType        the Schema type against which the content is to be validated
     * @param validation        the schema validation mode
     */

    public Copy(boolean copyNamespaces,
                boolean inheritNamespaces,
                SchemaType schemaType,
                int validation) {
        this.copyNamespaces = copyNamespaces;
        this.bequeathNamespacesToChildren = inheritNamespaces;
        setValidationAction(validation, schemaType);
        preservingTypes = schemaType == null && validation == Validation.PRESERVE;
    }

    /**
     * Say whether namespace nodes are to be copied (in the case of an element)
     * @param copy set to true if namespace nodes are to be copied
     */

    public void setCopyNamespaces(boolean copy) {
        copyNamespaces = copy;
    }

    /**
     * Ask whether namespace nodes are to be copied (in the case of an element)
     *
     * @return true if all in-scope namespaces are to be copied
     */

    public boolean isCopyNamespaces() {
        return copyNamespaces;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation does nothing.
     *
     *
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during expression rewriting
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        preservingTypes |= !getPackageData().isSchemaAware();
        return super.simplify();
    }


    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        typeCheckChildren(visitor, contextInfo);

        selectItemType = contextInfo.getItemType();

        ItemType selectItemType = contextInfo.getItemType();  //select.getItemType();
        if (selectItemType == ErrorType.getInstance()) {
            XPathException err = new XPathException("No context item supplied for xsl:copy", "XTTE0945");
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            throw err;
        }

        if (selectItemType instanceof NodeTest) {
            switch (selectItemType.getPrimitiveType()) {
                // For elements and attributes, assume the type annotation will change
                case Type.ELEMENT:
                    this.resultItemType = NodeKindTest.ELEMENT;
                    break;
                case Type.DOCUMENT:
                    this.resultItemType = NodeKindTest.DOCUMENT;
                    break;
                case Type.ATTRIBUTE:
                case Type.TEXT:
                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
                case Type.NAMESPACE:
                    ContextItemExpression dot = new ContextItemExpression();
                    ExpressionTool.copyLocationInfo(this, dot);
                    CopyOf c = new CopyOf(dot, copyNamespaces, getValidationAction(), getSchemaType(), false);
                    ExpressionTool.copyLocationInfo(this, c);
                    return c.typeCheck(visitor, contextInfo);
                default:
                    this.resultItemType = selectItemType;
            }
        } else {
            this.resultItemType = selectItemType;
        }

        checkContentSequence(visitor.getStaticContext());
        return this;
    }

    /**
     * Copy this expression (don't be confused by the method name). This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        Copy copy = new Copy(
                copyNamespaces, bequeathNamespacesToChildren, getSchemaType(), getValidationAction());
        ExpressionTool.copyLocationInfo(this, copy);
        copy.setContentExpression(getContentExpression().copy(rebindings));
        copy.resultItemType = resultItemType;
        return copy;
    }

    /**
     * Set the item type of the input
     */

    public void setSelectItemType(ItemType type) {
        selectItemType = type;
    }

    /**
     * Determine which aspects of the context the expression depends on. The result is
     * a bitwise-or'ed value composed from constants such as XPathContext.VARIABLES and
     * XPathContext.CURRENT_NODE. The default implementation combines the intrinsic
     * dependencies of this expression with the dependencies of the subexpressions,
     * computed recursively. This is overridden for expressions such as FilterExpression
     * where a subexpression's dependencies are not necessarily inherited by the parent
     * expression.
     *
     * @return a set of bit-significant flags identifying the dependencies of
     *         the expression
     */

    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_COPY;
    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * returns a zero-length array, appropriate for an expression that has no
     * sub-expressions.
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterable<Operand> operands() {
        return contentOp;
    }


    /**
     * Get the item type of the result of this instruction.
     *
     * @return The context item type.
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        if (resultItemType != null) {
            return resultItemType;
        } else {
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            resultItemType = computeItemType(th);
            return resultItemType;
        }
    }

    private ItemType computeItemType(TypeHierarchy th) {
        ItemType selectItemType = this.selectItemType; //select.getItemType();
        if (!getPackageData().isSchemaAware()) {
            return selectItemType;
        }
        if (selectItemType.getUType().overlaps(UType.ANY_ATOMIC.union(UType.FUNCTION))) {
            return selectItemType;
        }
        // The rest of the code handles the complications of schema-awareness
        Configuration config = th.getConfiguration();
        if (getSchemaType() != null) {
            Affinity e = th.relationship(selectItemType, NodeKindTest.ELEMENT);
            if (e == Affinity.SAME_TYPE || e == Affinity.SUBSUMED_BY) {
                return new ContentTypeTest(Type.ELEMENT, getSchemaType(), config, false);
            }
            Affinity a = th.relationship(selectItemType, NodeKindTest.ATTRIBUTE);
            if (a == Affinity.SAME_TYPE || a == Affinity.SUBSUMED_BY) {
                return new ContentTypeTest(Type.ATTRIBUTE, getSchemaType(), config, false);
            }
            return AnyNodeTest.getInstance();
        } else {
            switch (getValidationAction()) {
                case Validation.PRESERVE:
                    return selectItemType;
                case Validation.STRIP: {
                    Affinity e = th.relationship(selectItemType, NodeKindTest.ELEMENT);
                    if (e == Affinity.SAME_TYPE || e == Affinity.SUBSUMED_BY) {
                        return new ContentTypeTest(Type.ELEMENT, Untyped.getInstance(), config, false);
                    }
                    Affinity a = th.relationship(selectItemType, NodeKindTest.ATTRIBUTE);
                    if (a == Affinity.SAME_TYPE || a == Affinity.SUBSUMED_BY) {
                        return new ContentTypeTest(Type.ATTRIBUTE, BuiltInAtomicType.UNTYPED_ATOMIC, config, false);
                    }
                    if (e != Affinity.DISJOINT || a != Affinity.DISJOINT) {
                        // it might be an element or attribute
                        return AnyNodeTest.getInstance();
                    } else {
                        // it can't be an element or attribute, so stripping type annotations can't affect it
                        return selectItemType;
                    }
                }
                case Validation.STRICT:
                case Validation.LAX:
                    if (selectItemType instanceof NodeTest) {
                        int fp = ((NodeTest) selectItemType).getFingerprint();
                        if (fp != -1) {
                            Affinity e = th.relationship(selectItemType, NodeKindTest.ELEMENT);
                            if (e == Affinity.SAME_TYPE || e == Affinity.SUBSUMED_BY) {
                                SchemaDeclaration elem = config.getElementDeclaration(fp);
                                if (elem != null) {
                                    try {
                                        return new ContentTypeTest(Type.ELEMENT, elem.getType(), config, false);
                                    } catch (MissingComponentException e1) {
                                        return new ContentTypeTest(Type.ELEMENT, AnyType.getInstance(), config, false);
                                    }
                                } else {
                                    // No element declaration now, but there might be one at run-time
                                    return new ContentTypeTest(Type.ELEMENT, AnyType.getInstance(), config, false);
                                }
                            }
                            Affinity a = th.relationship(selectItemType, NodeKindTest.ATTRIBUTE);
                            if (a == Affinity.SAME_TYPE || a == Affinity.SUBSUMED_BY) {
                                SchemaDeclaration attr = config.getElementDeclaration(fp);
                                if (attr != null) {
                                    try {
                                        return new ContentTypeTest(Type.ATTRIBUTE, attr.getType(), config, false);
                                    } catch (MissingComponentException e1) {
                                        return new ContentTypeTest(Type.ATTRIBUTE, AnySimpleType.getInstance(), config, false);
                                    }
                                } else {
                                    // No attribute declaration now, but there might be one at run-time
                                    return new ContentTypeTest(Type.ATTRIBUTE, AnySimpleType.getInstance(), config, false);
                                }
                            }
                        } else {
                            Affinity e = th.relationship(selectItemType, NodeKindTest.ELEMENT);
                            if (e == Affinity.SAME_TYPE || e == Affinity.SUBSUMED_BY) {
                                return NodeKindTest.ELEMENT;
                            }
                            Affinity a = th.relationship(selectItemType, NodeKindTest.ATTRIBUTE);
                            if (a == Affinity.SAME_TYPE || a == Affinity.SUBSUMED_BY) {
                                return NodeKindTest.ATTRIBUTE;
                            }
                        }
                        return AnyNodeTest.getInstance();
                    } else if (selectItemType instanceof AtomicType) {
                        return selectItemType;
                    } else {
                        return AnyItemType.getInstance();
                    }
                default:
                    throw new IllegalStateException();
            }
        }
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        Expression exp = super.optimize(visitor, contextItemType);
        if (exp == this) {
            if (resultItemType == null) {
                resultItemType = computeItemType(visitor.getConfiguration().getTypeHierarchy());
            }
            if (visitor.isOptimizeForStreaming()) {
                UType type = contextItemType.getItemType().getUType();
                if (!type.intersection(MultipleNodeKindTest.LEAF.getUType()).equals(UType.VOID)) {
                    // Bug 4346: only do this optimization once
                    Expression p = getParentExpression();
                    if (p instanceof Choose && ((Choose)p).size() == 2 && ((Choose)p).getAction(1) == this &&
                            ((Choose) p).getAction(0) instanceof CopyOf) {
                        return exp;
                    }
                    Expression copyOf = new CopyOf(
                            new ContextItemExpression(), false, getValidationAction(), getSchemaType(), false);
                    NodeTest leafTest = new MultipleNodeKindTest(type.intersection(MultipleNodeKindTest.LEAF.getUType()));
                    Expression[] conditions = new Expression[]{
                            new InstanceOfExpression(
                                    new ContextItemExpression(),
                                    SequenceType.makeSequenceType(leafTest, StaticProperty.EXACTLY_ONE)),
                            Literal.makeLiteral(BooleanValue.TRUE, this)};
                    Expression[] actions = new Expression[]{copyOf, this};
                    Choose choose = new Choose(conditions, actions);
                    ExpressionTool.copyLocationInfo(this, choose);
                    return choose;
                }
            }
        }
        return exp;
    }

    /**
     * Callback from ElementCreator when constructing an element
     *
     * @param context    XPath dynamic evaluation context
     * @param copiedNode the node being copied
     * @return the name of the element to be constructed
     */

    @Override
    public NodeName getElementName(XPathContext context, NodeInfo copiedNode) {
        return NameOfNode.makeName(copiedNode);
    }

    /**
     * Get the base URI of a copied element node (the base URI is retained in the new copy)
     *
     * @param context    XPath dynamic evaluation context
     * @param copiedNode the node being copied (for xsl:copy), otherwise null
     * @return the base URI
     */

    @Override
    public String getNewBaseURI(XPathContext context, NodeInfo copiedNode) {
        return copiedNode.getBaseURI();
    }

    /**
     * Callback to output namespace nodes for the new element.
     *
     * @param receiver   the Receiver where the namespace nodes are to be written
     * @param nodeName    the element name
     * @param copiedNode  the node being copied (for xsl:copy), otherwise null
     * @throws XPathException if any error occurs
     */

    @Override
    public void outputNamespaceNodes(Outputter receiver, NodeName nodeName, NodeInfo copiedNode)
            throws XPathException {
        if (copyNamespaces) {
            receiver.namespaces(copiedNode.getAllNamespaces(), ReceiverOption.NAMESPACE_OK);
        } else {
            // Always output the namespace of the element name itself
            NamespaceBinding ns = nodeName.getNamespaceBinding();
            if (!ns.isDefaultUndeclaration()) {
                receiver.namespace(ns.getPrefix(), ns.getURI(), ReceiverOption.NONE);
            }
        }
    }

    @Override
    public TailCall processLeavingTail(Outputter out, XPathContext context) throws XPathException {
        Controller controller = context.getController();
        Item item = context.getContextItem();
        if (item == null) {
            XPathException err = new XPathException("There is no context item for xsl:copy", "XTTE0945");
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            err.setXPathContext(context);
            throw err;
        }

        if (!(item instanceof NodeInfo)) {
            out.append(item, getLocation(), ReceiverOption.ALL_NAMESPACES);
            return null;
        }
        NodeInfo source = (NodeInfo) item;
        switch (source.getNodeKind()) {

            case Type.ELEMENT:
                // use the generic code for creating new elements
                return super.processLeavingTail(out, context, (NodeInfo) item);

            case Type.ATTRIBUTE:
                if (getSchemaType() instanceof ComplexType) {
                    dynamicError("Cannot copy an attribute when the type requested for validation is a complex type", "XTTE1535", context);
                }
                try {
                    CopyOf.copyAttribute(source, (SimpleType) getSchemaType(), getValidationAction(), this, out, context, false);
                } catch (NoOpenStartTagException err) {
                    err.setXPathContext(context);
                    throw dynamicError(getLocation(), err, context);
                }
                break;

            case Type.TEXT:
                CharSequence tval = source.getStringValueCS();
                out.characters(tval, getLocation(), ReceiverOption.NONE);
                break;

            case Type.PROCESSING_INSTRUCTION:
                CharSequence pval = source.getStringValueCS();
                out.processingInstruction(source.getDisplayName(), pval, getLocation(), ReceiverOption.NONE);
                break;

            case Type.COMMENT:
                CharSequence cval = source.getStringValueCS();
                out.comment(cval, getLocation(), ReceiverOption.NONE);
                break;

            case Type.NAMESPACE:
                out.namespace(
                        ((NodeInfo) item).getLocalPart(), item.getStringValue(), ReceiverOption.NONE);
                break;

            case Type.DOCUMENT:
                if (!preservingTypes) {
                    ParseOptions options = new ParseOptions(getValidationOptions());
                    options.setSpaceStrippingRule(NoElementsSpaceStrippingRule.getInstance());
                    controller.getConfiguration().prepareValidationReporting(context, options);
                    Receiver val = controller.getConfiguration().
                        getDocumentValidator(out, source.getBaseURI(), options, getLocation());
                    out = new ComplexContentOutputter(val);
                }
                if (out.getSystemId() == null) {
                    out.setSystemId(source.getBaseURI());
                }
                out.startDocument(ReceiverOption.NONE);
                copyUnparsedEntities(source, out);
                getContentExpression().process(out, context);
                out.endDocument();
                break;

            default:
                throw new IllegalArgumentException("Unknown node kind " + source.getNodeKind());

        }
        return null;
    }

    public static void copyUnparsedEntities(NodeInfo source, Outputter out) throws XPathException {
        Iterator<String> unparsedEntities = source.getTreeInfo().getUnparsedEntityNames();
        while (unparsedEntities.hasNext()) {
            String n = unparsedEntities.next();
            String[] details = source.getTreeInfo().getUnparsedEntity(n);
            out.setUnparsedEntity(n, details[0], details[1]);
        }
    }

    /**
     * Evaluate as an item
     */

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        SequenceCollector seq = controller.allocateSequenceOutputter(1);
        seq.getPipelineConfiguration().setHostLanguage(getPackageData().getHostLanguage());
        process(new ComplexContentOutputter(seq), context);
        seq.close();
        Item item = seq.getFirstItem();
        seq.reset();
        return item;
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "Copy";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("copy", this);
        exportValidationAndType(out);
        String flags = "";
        if (copyNamespaces) {
            flags = "c";
        }
        if (bequeathNamespacesToChildren) {
            flags += "i";
        }
        if (inheritNamespacesFromParent) {
            flags += "n";
        }
        if (isLocal()) {
            flags += "l";
        }
        out.emitAttribute("flags", flags);
        String sType = SequenceType.makeSequenceType(selectItemType, getCardinality()).toAlphaCode();
        if (sType != null) {
            out.emitAttribute("sit", sType);
        }
        out.setChildRole("content");
        getContentExpression().export(out);
        out.endElement();
    }


}

