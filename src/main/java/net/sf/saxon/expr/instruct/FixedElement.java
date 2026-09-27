////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.ComplexNodePushElaborator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PushEvaluator;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.pattern.qname.SpecificQNameTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpInnerClass;
import net.sf.saxon.type.*;
import net.sf.saxon.type.gnode.NamedXNodeType;

import java.util.function.BiConsumer;


/**
 * An instruction that creates an element node whose name is known statically.
 * Used for literal results elements in XSLT, for direct element constructors
 * in XQuery, and for xsl:element in cases where the name and namespace are
 * known statically.
 */

public class FixedElement extends ElementCreator {

    private final NodeName elementName;
    protected NamespaceMap namespaceBindings;
    private ItemType itemType;

    /**
     * Create an instruction that creates a new element node
     *
     * @param elementName                 Represents the name of the element node
     * @param namespaceBindings           List of namespaces to be added to the element node.
     *                                    Supply an empty array if none are required.
     * @param inheritNamespacesToChildren true if the children of this element inherit its namespaces
     * @param inheritNamespacesFromParent true if this element inherits namespaces from its parent
     * @param schema                      the schema to be used
     * @param schemaType                  Type annotation for the new element node
     * @param validation                  Validation mode to be applied, for example STRICT, LAX, SKIP
     */
    public FixedElement(NodeName elementName,
                        NamespaceMap namespaceBindings,
                        boolean inheritNamespacesToChildren,
                        boolean inheritNamespacesFromParent,
                        Schema schema,
                        SchemaType schemaType,
                        int validation) {
        this.elementName = elementName;
        this.namespaceBindings = namespaceBindings;
        this.bequeathNamespacesToChildren = inheritNamespacesToChildren;
        this.inheritNamespacesFromParent = inheritNamespacesFromParent;
        setValidationAction(schema, validation, schemaType);
        this.schema = schema;
        preservingTypes = schemaType == null && validation == Validation.PRESERVE;
    }

    @Override
    public void setLocation(Location id) {
        super.setLocation(id);
    }

    @Override
    public Iterable<Operand> operands() {
        return contentOp;
    }

    /**
     * Simplify an expression. This performs any context-independent rewriting
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

    /**
     * Check statically whether the content of the element creates attributes or namespaces
     * after creating any child nodes
     *
     * @throws net.sf.saxon.trans.XPathException if the content sequence is invalid
     */

    @Override
    protected void checkContentSequence() throws XPathException {
        super.checkContentSequence();
        itemType = computeFixedElementItemType(this, schema,
                getValidationAction(), getSchemaType(), elementName, getContentExpression());
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        Expression e = super.optimize(visitor, contextItemType);
        if (e != this) {
            return e;
        }
        // Remove any unnecessary creation of namespace nodes by child literal result elements.
        // Specifically, if this instruction creates a namespace node, then a child literal result element
        // doesn't need to create the same namespace if all the following conditions are true:
        // (a) the child element is in the same namespace as its parent, and
        // (b) this element doesn't specify xsl:inherit-namespaces="no"
        // (c) the child element is incapable of creating attributes in a non-null namespace

        if (!bequeathNamespacesToChildren) {
            return this;
        }
//        if (!namespaceBindings.isEmpty()) {
//            if (getContentExpression() instanceof FixedElement) {
//                FixedElement fixedContent = (FixedElement) getContentExpression();
//                if (elementName.isInSameNamespace(fixedContent.getElementName())) {
//                    fixedContent.removeRedundantNamespaces(visitor, namespaceBindings);
//                }
//                return this;
//            }
//            if (getContentExpression() instanceof Block) {
//                for (Operand o : getContentExpression().operands()) {
//                    Expression exp = o.getChildExpression();
//                    if (exp instanceof FixedElement &&
//                            elementName.isInSameNamespace(((FixedElement) exp).getElementName())) {
//                        ((FixedElement) exp).removeRedundantNamespaces(visitor, namespaceBindings);
//                    }
//                }
//            }
//        }
        return this;
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
        Schema schema = getSchema();
        FixedElement fe = new FixedElement(elementName, namespaceBindings, bequeathNamespacesToChildren,
                                           inheritNamespacesFromParent, schema, getSchemaType(), getValidationAction());
        ExpressionTool.copyLocationInfo(this, fe);
        fe.setContentExpression(getContentExpression().copy(rebindings));
        fe.preservingTypes = preservingTypes;
        return fe;
    }

    /**
     * Determine the item type of an element being constructed
     *
     * @param instr       the FixedElement instruction
     * @param schema      the in-scope schema for this instruction
     * @param validation  the schema validation mode
     * @param schemaType  the schema type for validation
     * @param elementName the name of the element
     * @param content     the expression that computes the content of the element
     * @return the item type
     * @throws XPathException if a static error is detected
     */

    private ItemType computeFixedElementItemType(FixedElement instr, Schema schema,
                                                 int validation, SchemaType schemaType,
                                                 NodeName elementName, Expression content) throws XPathException {
        final Configuration config = instr.getConfiguration();
        ItemType itemType;
        int fp = elementName.obtainFingerprint(config.getNamePool());   // Bug #2563 - namespaced alias in unoptimized export
        QNameTest elementNameTest = new SpecificQNameTest(
                elementName.getStructuredQName(), config.getNamePool(), fp);
        if (schemaType == null) {
            if (validation == Validation.STRICT) {
                IElementDecl decl = schema.getElementDecl(fp);
                if (decl == null) {
                    throw new XPathException("There is no global element declaration for " +
                            elementName.getStructuredQName().getEQName() + ", so strict validation will fail")
                            .withErrorCode(instr.isXSLT() ? "XTTE1512" : "XQDY0084")
                            .asTypeError() // technically this is a type error in XSLT but not in XQuery
                            .withLocation(instr.getLocation());
                }
                if (decl.isAbstract()) {
                    throw new XPathException("The element declaration for " +
                            elementName.getStructuredQName().getEQName() + " is abstract, so strict validation will fail")
                            .withErrorCode(instr.isXSLT() ? "XTTE1512" : "XQDY0084")
                            .asTypeError() // technically this is a type error in XSLT but not in XQuery
                            .withLocation(instr.getLocation());
                }
                SchemaType declaredType = decl.getType();
                SchemaType xsiType = instr.getXSIType(schema);
                if (xsiType != null) {
                    schemaType = xsiType;
                } else {
                    schemaType = declaredType;
                }

                itemType = new NamedXNodeType(Type.ELEMENT,
                                              elementNameTest,
                                              schemaType, false, config);
                if (xsiType != null || !decl.hasTypeAlternatives()) {
                    instr.setValidationOptions(instr.getValidationOptions().withTopLevelType(schemaType));
                    try {
                        schemaType.analyzeContentExpression(content, Type.ELEMENT, schema);
                    } catch (XPathException e) {
                        throw e.withErrorCode(instr.isXSLT() ? "XTTE1510" : "XQDY0027")
                                .withLocation(instr.getLocation());
                    }
                    if (xsiType != null) {
                        try {
                            config.checkTypeDerivationIsOK(xsiType, declaredType, 0);
                        } catch (SchemaException e) {
                            ValidationFailure ve = new ValidationFailure("The specified xsi:type " + xsiType.getDescription() +
                                " is not validly derived from the required type " + declaredType.getDescription());
                            ve.setConstraintReference(1, "cvc-elt", "4.3");
                            ve.setErrorCode(instr.isXSLT() ? "XTTE1515" : "XQDY0027");
                            ve.setLocator(instr.getLocation());
                            throw ve.makeException();
                        }
                    }
                }
            } else if (validation == Validation.LAX) {
                IElementDecl decl = schema.getElementDecl(fp);
                if (decl == null) {

                    warning("There is no global element declaration for " +
                            elementName.getDisplayName(), SaxonErrorCode.SXWN9031);
                    itemType = new NamedXNodeType(Type.ELEMENT, elementName.getStructuredQName(), config);
                } else {
                    schemaType = decl.getType();
                    instr.setValidationOptions(instr.getValidationOptions().withTopLevelType(schemaType));
                    itemType = new NamedXNodeType(Type.ELEMENT,
                                                  elementNameTest,
                                                  instr.getSchemaType(), false, config);
                    try {
                        schemaType.analyzeContentExpression(content, Type.ELEMENT, schema);
                    } catch (XPathException e) {
                        throw e.withErrorCode(instr.isXSLT() ? "XTTE1515" : "XQDY0027")
                                .withLocation(instr.getLocation());
                    }
                }
            } else if (validation == Validation.PRESERVE) {
                // we know the result will be an element of type xs:anyType
                itemType = new NamedXNodeType(Type.ELEMENT,
                                              elementNameTest,
                                              AnyType.INSTANCE, false, config);
            } else {
                // we know the result will be an untyped element
                itemType = new NamedXNodeType(Type.ELEMENT,
                                              elementNameTest,
                                              Untyped.INSTANCE, false, config);
            }
        } else {
            itemType = new NamedXNodeType(Type.ELEMENT,
                                          elementNameTest,
                                          schemaType, false, config);
            try {
                schemaType.analyzeContentExpression(content, Type.ELEMENT, schema);
            } catch (XPathException e) {
                throw e.withErrorCode(instr.isXSLT() ? "XTTE1540" : "XQDY0027").withLocation(instr.getLocation());
            }
        }
        return itemType;
    }

    /**
     * Get the type of the item returned by this instruction
     *
     * @return the item type
     */
    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        if (itemType == null) {
            return super.getItemType();
        }
        return itemType;
    }

//    /**
//     * Callback from the superclass ElementCreator to get the nameCode
//     * for the element name
//     *
//     * @param context    The evaluation context (not used)
//     * @param copiedNode For the benefit of the xsl:copy instruction, the node to be copied
//     * @return the name code for the element name
//     */
//
//    @Override
//    public NodeName getElementName(XPathContext context, NodeInfo copiedNode) {
//        return elementName;
//    }

    public NodeName getFixedElementName() {
        return elementName;
    }


    /**
     * Get the properties of this object to be included in trace messages, by supplying
     * the property values to a supplied consumer function
     *
     * @param consumer the function to which the properties should be supplied, as (property name,
     *                 value) pairs.
     */
    @Override
    public void gatherProperties(BiConsumer<String, Object> consumer) {
        consumer.accept("name", getFixedElementName());
    }


//
//    @Override
//    public String getNewBaseURI(XPathContext context, NodeInfo copiedNode) {
//        return getStaticBaseURIString();
//    }

    /**
     * Determine whether the element constructor creates a fixed xsi:type attribute, and if so, return the
     * relevant type.
     *
     * @param schema the in-scope schema for this instruction
     * @return the type denoted by the constructor's xsi:type attribute if there is one.
     *         Return null if there is no xsi:type attribute, or if the value of the xsi:type
     *         attribute is a type that is not statically known (this is allowed)
     * @throws XPathException if there is an xsi:type attribute and its value is not a QName.
     */

    private SchemaType getXSIType(Schema schema) throws XPathException {
        if (getContentExpression() instanceof FixedAttribute) {
            return testForXSIType((FixedAttribute) getContentExpression(), schema);
        } else if (getContentExpression() instanceof Block) {
            for (Operand o : getContentExpression().operands()) {
                Expression exp = o.getChildExpression();
                if (exp instanceof FixedAttribute) {
                    SchemaType type = testForXSIType((FixedAttribute) exp, schema);
                    if (type != null) {
                        return type;
                    }
                }
            }
            return null;
        } else {
            return null;
        }
    }

    /**
     * Test whether a FixedAttribute child instruction of this FixedElement is creating an xsi:type
     * attribute whose value is known statically; if this is the case, then return the schema type
     * named in this attribute
     *
     * @param fat The FixedAttribute instruction
     * @param schema The in-scope schema for thi instruction
     * @return the schema type if this is an xsi:type attribute instruction whose value is known at compile time;
     *         otherwise null
     * @throws XPathException if an error occurs
     */

    private SchemaType testForXSIType(FixedAttribute fat, Schema schema) throws XPathException {
        int att = fat.getAttributeFingerprint();
        if (att == StandardNames.XSI_TYPE) {
            Expression attValue = fat.getSelect();
            if (attValue instanceof StringLiteral) {
                try {
                    String[] parts = NameChecker.getQNameParts(
                            ((StringLiteral) attValue).stringify());
                    // The only namespace bindings we can trust are those declared on this element
                    // We could also trust those on enclosing LREs in the same function/template,
                    // but it's not a big win to go looking for them.
                    NamespaceUri uri = namespaceBindings.getNamespaceUri(parts[0]);
                    if (uri == null) {
                        return null;
                    } else {
                        return schema.getSchemaType(new StructuredQName("", uri, parts[1]));
                    }
                } catch (QNameException e) {
                    throw new XPathException(e.getMessage());
                }
            }
        }
        return null;
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
        if (parentType instanceof SimpleType) {
            throw new XPathException("Element " + elementName.getDisplayName() +
                    " is not permitted here: the containing element is of simple type " + parentType.getDescription())
                    .asTypeError().withLocation(getLocation());
        } else if (((ComplexType) parentType).isSimpleContent()) {
            throw new XPathException("Element " + elementName.getDisplayName() +
                    " is not permitted here: the containing element has a complex type with simple content")
                    .asTypeError().withLocation(getLocation());
        }

        // Check that a sequence consisting of this element alone is valid against the content model
        Schema schema = getRetainedStaticContext().getImportedSchema();
        if (whole) {
            Expression parent = getParentExpression();
            Block block = new Block(new Expression[]{this});
            parentType.analyzeContentExpression(block, Type.ELEMENT, schema);
            setParentExpression(parent);
        }

        SchemaType type;
        try {
            int fp = elementName.obtainFingerprint(getConfiguration().getNamePool());
            type = ((ComplexType) parentType).getElementParticleType(fp, schema, true);
        } catch (MissingComponentException e) {
            throw new XPathException(e);
        }
        if (type == null) {
            XPathException err = new XPathException("Element " + elementName.getDisplayName() +
                    " is not permitted in the content model of the complex type " +
                    parentType.getDescription());
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            err.setErrorCode(isXSLT() ? "XTTE1510" : "XQDY0027");
            throw err;
        }
        if (type instanceof AnyType) {
            return;
        }

        try {
            getContentExpression().checkPermittedContents(type, true);
        } catch (XPathException e) {
            throw e.maybeWithLocation(getLocation());
        }
    }

    @Override
    public ElementCreationDetails makeElementCreationDetails() {
        return new ElementCreationDetails() {
            @Override
            public NodeName getNodeName(XPathContext context) {
                return getFixedElementName();
            }

            @Override
            public String getSystemId(XPathContext context) {
                return getStaticBaseURIString();
            }

        };
    }

    /**
     * Callback from the superclass ElementCreator to output the namespace nodes
     * @param out        The receiver to handle the output
     * @param nodeName   the name of this element
     * @param details in the case of xsl:copy, the node being copied
     */

    @Override
    public void outputNamespaceNodes(Outputter out, NodeName nodeName, ElementCreationDetails details)
            throws XPathException {
        for (NamespaceBinding ns : namespaceBindings) {
            out.namespace(ns.getPrefix(), ns.getNamespaceUri(), ReceiverOption.NONE);
        }
    }

    /**
     * Callback to get a list of the intrinsic namespaces that need to be generated for the element.
     * @return the namespace bindings that must be generated for the new element node
     */

    public NamespaceMap getActiveNamespaces() {
        return namespaceBindings;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("elem", this);
        out.emitAttribute("name", elementName.getDisplayName());
        out.emitAttribute("nsuri", elementName.getNamespaceUri().toString());
        String flags = getInheritanceFlags();
        if (!elementName.getNamespaceUri().isEmpty() && elementName.getPrefix().isEmpty()) {
            flags += "d";  // "d" to indicate default namespace
        }
        if (isLocal()) {
            flags += "l";
        }
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        StringBuilder fsb = new StringBuilder(256);
        if (!namespaceBindings.isEmpty()) {
            for (NamespaceBinding ns : namespaceBindings) {
                String prefix = ns.getPrefix();
                if (!prefix.equals("xml")) {
                    fsb.append(prefix.isEmpty() ? "#" : prefix);
                    if (!ns.getNamespaceUri().equals(getRetainedStaticContext().getURIForPrefix(prefix, true))) {
                        fsb.append('=');
                        fsb.append(ns.getNamespaceUri());
                    }
                    fsb.append(' ');
                }
            }
            fsb.setLength(fsb.length() - 1);
            out.emitAttribute("namespaces", fsb.toString());
        }
        exportValidationAndType(out);
        String schemaRole = getRetainedStaticContext().getImportedSchemaRoleName();
        if (!schemaRole.isEmpty()) {
            out.emitAttribute("schemaRole", schemaRole);
        }
        getContentExpression().export(out);
        out.endElement();
    }

    @Override
    public String toString() {
        return "<" + elementName.getStructuredQName().getDisplayName() + " {" + getContentExpression().toString() + "}/>";
    }

    @Override
    public String toShortString() {
        return "<" + elementName.getStructuredQName().getDisplayName() + " {" + getContentExpression().toShortString() + "}/>";
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
        return "element";
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new FixedElementElaborator();
    }

    /**
     * Elaborator for a FixedElement (literal result element) expression.
     */

    public static class FixedElementElaborator extends ComplexNodePushElaborator {

        @Override
        @CSharpInnerClass(outer = false, extra = {"Saxon.Hej.expr.instruct.FixedElement instr", "Saxon.Hej.expr.elab.PushEvaluator contentPusher"})
        public PushEvaluator elaborateForPush() {
            final FixedElement expr = (FixedElement) getExpression();
            final PushEvaluator contentPusher =
                    expr.getContentExpression().makeElaborator().elaborateForPush();

            Schema schema = expr.getRetainedStaticContext().getImportedSchema();
            SchemaType typeCode = switch (expr.getValidationAction()) {
                case Validation.PRESERVE -> AnyType.INSTANCE;
                case Validation.BY_TYPE -> expr.getValidationOptions().getTopLevelType();
                default -> Untyped.INSTANCE;
            };

            int properties = ReceiverOption.NONE;
            if (!expr.bequeathNamespacesToChildren) {
                properties |= ReceiverOption.DISINHERIT_NAMESPACES;
            }
            if (!expr.inheritNamespacesFromParent) {
                properties |= ReceiverOption.REFUSE_NAMESPACES;
            }
            properties |= ReceiverOption.ALL_NAMESPACES;
            final int finalProperties = properties;

            return (out, context) -> {
                try {

                    NodeName elemName = expr.getFixedElementName();

                    Receiver elemOut = out;
                    if (!expr.preservingTypes) {
                        ParseOptions options = expr.getValidationOptions()
                                .withTopLevelElement(elemName.getStructuredQName());
                        context.getConfiguration().prepareValidationReporting(context, options);
                        Receiver validator = schema.getElementValidator(
                                elemOut, options, expr.getLocation());

                        if (validator != elemOut) {
                            out = new ComplexContentOutputter(validator);
                        }
                    }

                    if (out.getSystemId() == null) {
                        out.setSystemId(expr.getStaticBaseURIString());
                    }

                    out.startElement(elemName, typeCode, expr.getLocation(), finalProperties);

                    // output the required namespace nodes via a callback

                    for (NamespaceBinding ns : expr.namespaceBindings) {
                        out.namespace(ns.getPrefix(), ns.getNamespaceUri(), ReceiverOption.NONE);
                    }

                    // process subordinate instructions to generate attributes and content
                    TailCall tc = contentPusher.processLeavingTail(out, context);
                    Expression.dispatchTailCall(tc);

                    // output the element end tag (which will fail if validation fails)
                    out.endElement();

                } catch (XPathException e) {
                    throw e.maybeWithLocation(expr.getLocation()).maybeWithContext(context);
                }
                return null;
            };
        }



    }
}

