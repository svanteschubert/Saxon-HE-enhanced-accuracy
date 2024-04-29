////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.ContentTypeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpInnerClass;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.util.function.Supplier;


/**
 * An instruction representing an xsl:element element in an XSLT stylesheet,
 * or a computed element constructor in XQuery. (In both cases, if the element name
 * is expressed as a compile-time expression, then a FixedElement instruction
 * is used instead.)
 *
 * @see FixedElement
 */

public class ComputedElement extends ElementCreator {

    private final Operand nameOp;
    private Operand namespaceOp;

    private final boolean allowNameAsQName;
    private ItemType itemType;

    /**
     * Create an instruction that creates a new element node
     *  @param elementName       Expression that evaluates to produce the name of the
     *                          element node as a lexical QName
     * @param namespace         Expression that evaluates to produce the namespace URI of
     *                          the element node. Set to null if the namespace is to be deduced from the prefix
     *                          of the elementName.
     * @param schemaType        The required schema type for the content
     * @param validation        Required validation mode (e.g. STRICT, LAX, SKIP)
     * @param inheritNamespaces true if child elements automatically inherit the namespaces of their parent
     * @param allowQName        True if the elementName expression is allowed to return a QNameValue; false if
     */
    public ComputedElement(Expression elementName,
                           Expression namespace,
                           SchemaType schemaType,
                           int validation,
                           boolean inheritNamespaces,
                           boolean allowQName) {

        nameOp = new Operand(this, elementName, OperandRole.SINGLE_ATOMIC);
        if (namespace != null) {
            namespaceOp = new Operand(this, namespace, OperandRole.SINGLE_ATOMIC);
        }
        setValidationAction(validation, schemaType);
        preservingTypes = schemaType == null && validation == Validation.PRESERVE;
        this.bequeathNamespacesToChildren = inheritNamespaces;
        allowNameAsQName = allowQName;
    }

    /**
     * Get the expression used to compute the element name
     *
     * @return the expression used to compute the element name
     */

    public Expression getNameExp() {
        return nameOp.getChildExpression();
    }

    /**
     * Get the expression used to compute the namespace URI
     *
     * @return the expression used to compute the namespace URI
     */

    public Expression getNamespaceExp() {
        return namespaceOp == null ? null : namespaceOp.getChildExpression();
    }

    protected void setNameExp(Expression elementName) {
        nameOp.setChildExpression(elementName);
    }

    protected void setNamespaceExp(Expression namespace) {
        if (namespaceOp == null) {
            namespaceOp = new Operand(this, namespace, OperandRole.SINGLE_ATOMIC);
        } else {
            namespaceOp.setChildExpression(namespace);
        }
    }

    @Override
    public Iterable<Operand> operands() {
        return operandSparseList(contentOp, nameOp, namespaceOp);
    }

    /**
     * Get the namespace resolver that provides the namespace bindings defined in the static context
     *
     * @return the namespace resolver
     */

    public NamespaceResolver getNamespaceResolver() {
        return getRetainedStaticContext();
    }

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        setNameExp(getNameExp().simplify());
        if (getNamespaceExp() != null) {
            setNamespaceExp(getNamespaceExp().simplify());
        }
        Configuration config = getConfiguration();
        boolean schemaAware = getPackageData().isSchemaAware();
        preservingTypes |= !schemaAware;

        final SchemaType schemaType = getSchemaType();
        if (schemaType != null) {
            itemType = new ContentTypeTest(Type.ELEMENT, schemaType, config, false);
            schemaType.analyzeContentExpression(getContentExpression(), Type.ELEMENT);
        } else if (getValidationAction() == Validation.STRIP || !schemaAware) {
            itemType = new ContentTypeTest(Type.ELEMENT, Untyped.getInstance(), config, false);
        } else {
            // paradoxically, we know less about the type if validation="strict" is specified!
            // We know that it won't be untyped, but we have no way of representing that.
            itemType = NodeKindTest.ELEMENT;
        }
        return super.simplify();
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        super.typeCheck(visitor, contextInfo);
        Configuration config = visitor.getConfiguration();
        TypeHierarchy th = config.getTypeHierarchy();

        if (allowNameAsQName) {
            // Can only happen in XQuery
            Supplier<RoleDiagnostic> role = () -> new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "element/name", 0);
            setNameExp(config.getTypeChecker(false).staticTypeCheck(
                    getNameExp(), SequenceType.SINGLE_ATOMIC, role, visitor));
            ItemType supplied = getNameExp().getItemType();
            if (th.relationship(supplied, BuiltInAtomicType.STRING) == Affinity.DISJOINT &&
                    th.relationship(supplied, BuiltInAtomicType.UNTYPED_ATOMIC) == Affinity.DISJOINT &&
                    th.relationship(supplied, BuiltInAtomicType.QNAME) == Affinity.DISJOINT) {
                throw new XPathException("The name of a constructed element must be a string, QName, or untypedAtomic")
                        .withErrorCode("XPTY0004").asTypeError().withLocation(getLocation());
            }
        } else {
            if (!th.isSubType(getNameExp().getItemType(), BuiltInAtomicType.STRING)) {
                setNameExp(SystemFunction.makeCall("string", getRetainedStaticContext(), getNameExp()));
            }
        }
        if (Literal.isAtomic(getNameExp())) {
            // Check we have a valid lexical QName, whose prefix is in scope where necessary
            try {
                AtomicValue val = (AtomicValue) ((Literal) getNameExp()).getGroundedValue();
                if (val instanceof StringValue) {
                    String[] parts = NameChecker.checkQNameParts(val.getStringValue());
                    if (getNamespaceExp() == null) {
                        String prefix = parts[0];
                        NamespaceUri uri = getNamespaceResolver().getURIForPrefix(prefix, true);
                        if (uri == null) {
                            throw new XPathException("Prefix " + prefix + " has not been declared")
                                    .withErrorCode("XPST0081").asStaticError();
                        }
                        setNamespaceExp(new StringLiteral(uri.toString()));
                    }
                }
            } catch (XPathException e) {
                throw e.maybeWithErrorCode(isXSLT() ? "XTDE0820" : "XQDY0074")
                        .replacingErrorCode("FORG0001", isXSLT() ? "XTDE0820" : "XQDY0074")
                        .replacingErrorCode("XPST0081", isXSLT() ? "XTDE0830" : "XQDY0074")
                        .maybeWithLocation(getLocation())
                        .asStaticError();
            }
        }
        return super.typeCheck(visitor, contextInfo);
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
        ComputedElement ce = new ComputedElement(
                getNameExp().copy(rebindings), getNamespaceExp() == null ? null : getNamespaceExp().copy(rebindings),
                /*defaultNamespace,*/ getSchemaType(),
                getValidationAction(), bequeathNamespacesToChildren, allowNameAsQName);
        ExpressionTool.copyLocationInfo(this, ce);
        ce.setContentExpression(getContentExpression().copy(rebindings));
        return ce;
    }

    /**
     * Get the item type of the value returned by this instruction
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


    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    @Override
    public void checkPermittedContents(SchemaType parentType, boolean whole) throws XPathException {
        if (parentType instanceof SimpleType || ((ComplexType) parentType).isSimpleContent()) {
            String msg = "Elements are not permitted here: the containing element ";
            if (parentType instanceof SimpleType) {
                if (parentType.isAnonymousType()) {
                    msg += "is defined to have a simple type";
                } else {
                    msg += "is of simple type " + parentType.getDescription();
                }
            } else {
                msg += "has a complex type with simple content";
            }
            throw new XPathException(msg).asTypeError().withLocation(getLocation());
        }
        // NOTE: we could in principle check that if all the elements permitted in the content of the parentType
        // themselves have a simple type (not uncommon, perhaps) then this element must not have element content.
    }


    /**
     * Callback from the superclass ElementCreator to get the nameCode
     * for the element name
     *
     * @param context    The evaluation context (not used)
     * @return the name code for the element name
     */

    public NodeName getElementName(XPathContext context)
            throws XPathException {

        Controller controller = context.getController();
        assert controller != null;

        String prefix;
        String localName;
        NamespaceUri uri = null;

        // name needs to be evaluated at run-time
        AtomicValue nameValue = (AtomicValue) getNameExp().evaluateItem(context);
        if (nameValue == null) {
            String errorCode = isXSLT() ? "XTDE0820" : "XPTY0004";
            XPathException err1 = new XPathException("Invalid element name (empty sequence)", errorCode, getLocation());
            throw dynamicError(getLocation(), err1, context);
        }
        //nameValue = nameValue.getPrimitiveValue();
        if (nameValue instanceof StringValue) {  // which includes UntypedAtomic
            // this will always be the case in XSLT
            String rawName = nameValue.getStringValue();
            rawName = Whitespace.trim(rawName);
            // this will always be the case in XSLT
            if (rawName.startsWith("Q{") && allowNameAsQName) {
                // Unclear whether this is allowed: see https://github.com/w3c/qtspecs/issues/9
                // It clearly is NOT allowed in XSLT 3.0 (though for no good reason)
                try {
                    StructuredQName qn = StructuredQName.fromEQName(rawName);
                    prefix = "";
                    localName = qn.getLocalPart();
                    uri = qn.getNamespaceUri();
                } catch (IllegalArgumentException e) {
                    throw new XPathException("Invalid EQName in computed element constructor: " + e.getMessage(), "XQDY0074");
                }
                if (!NameChecker.isValidNCName(localName)) {
                    throw new XPathException("Local part of EQName in computed element constructor is invalid", "XQDY0074");
                }
            } else {
                try {
                    String[] parts = NameChecker.getQNameParts(rawName);
                    prefix = parts[0];
                    localName = parts[1];
                } catch (QNameException err) {
                    String message = "Invalid element name. " + err.getMessage();
                    if (rawName.length() == 0) {
                        message = "Supplied element name is a zero-length string";
                    }
                    String errorCode = isXSLT() ? "XTDE0820" : "XQDY0074";
                    XPathException err1 = new XPathException(message, errorCode, getLocation());
                    throw dynamicError(getLocation(), err1, context);
                }
            }
        } else if (nameValue instanceof QNameValue && allowNameAsQName) {
            // this is allowed in XQuery
            localName = ((QNameValue) nameValue).getLocalName();
            uri = ((QNameValue) nameValue).getNamespaceURI();
            prefix = ((QNameValue) nameValue).getPrefix();
            if (prefix.equals("xmlns")) {
                XPathException err = new XPathException("Computed element name has prefix xmlns", "XQDY0096", getLocation());
                throw dynamicError(getLocation(), err, context);
            }
        } else {
            String errorCode = isXSLT() ? "XTDE0820" : "XPTY0004";
            XPathException err = new XPathException("Computed element name has incorrect type", errorCode, getLocation());
            err.setIsTypeError(true);
            throw dynamicError(getLocation(), err, context);
        }

        if (getNamespaceExp() == null && uri == null) {
            uri = getRetainedStaticContext().getURIForPrefix(prefix, true);
            if (uri == null) {
                String errorCode = isXSLT() ? "XTDE0830" : prefix.equals("xmlns") ? "XQDY0096" : "XQDY0074";
                XPathException err = new XPathException("Undeclared prefix in element name: " + prefix, errorCode, getLocation());
                throw dynamicError(getLocation(), err, context);
            }
        } else {
            if (uri == null) {
                if (getNamespaceExp() instanceof StringLiteral) {
                    uri = NamespaceUri.of(((StringLiteral) getNamespaceExp()).stringify());
                } else {
                    uri = NamespaceUri.of(getNamespaceExp().evaluateAsString(context).toString());
                    if (!StandardURIChecker.getInstance().isValidURI(uri.toString())) {
                        XPathException de = new XPathException(
                            "The value of the namespace attribute must be a valid URI", "XTDE0835", getLocation());
                        throw dynamicError(getLocation(), de, context);
                    }
                }
            }
            if (uri.isEmpty()) {
                // there is a special rule for this case in the specification;
                // we force the element to go in the null namespace
                prefix = "";
            }
            if (prefix.equals("xmlns")) {
                // this isn't a legal prefix so we mustn't use it
                prefix = "x-xmlns";
            }
        }
        if (uri.equals(NamespaceUri.XMLNS)) {
            String errorCode = isXSLT() ? "XTDE0835" : "XQDY0096";
            XPathException err = new XPathException("Cannot create element in namespace " + uri, errorCode, getLocation());
            throw dynamicError(getLocation(), err, context);
        }
        if (uri.equals(NamespaceUri.XML) != prefix.equals("xml")) {
            String message;
            if (prefix.equals("xml")) {
                message = "When the prefix is 'xml', the namespace URI must be " + NamespaceConstant.XML;
            } else {
                message = "When the namespace URI is " + NamespaceConstant.XML + ", the prefix must be 'xml'";
            }
            String errorCode = isXSLT() ? "XTDE0835" : "XQDY0096";
            XPathException err = new XPathException(message, errorCode, getLocation());
            throw dynamicError(getLocation(), err, context);
        }

        return new FingerprintedQName(prefix, uri, localName);
    }


    /**
     * Ask whether the name can be supplied as a QName. In practice this is true for XQuery, false for XSLT
     *
     * @return true if the name can be supplied as a QName
     */

    public boolean isAllowNameAsQName() {
        return allowNameAsQName;
    }

    @Override
    @CSharpInnerClass(outer = true)
    public ElementCreationDetails makeElementCreationDetails() {
        return new ElementCreationDetails() {
            @Override
            public NodeName getNodeName(XPathContext context) throws XPathException {
                return getElementName(context);
            }

            @Override
            public String getSystemId(XPathContext context) {
                return getStaticBaseURIString();
            }

            @Override
            public void processContent(Outputter outputter, XPathContext context) throws XPathException {
                getContentExpression().process(outputter, context);
            }
        };
    }

    /**
     * Callback to output namespace nodes for the new element.
     * @param out        the Outputter where the namespace nodes are to be written
     * @param nodeName   The name of the element node being output
     * @param details Where this is a copied node, the node being copied
     */
    @Override
    public void outputNamespaceNodes(Outputter out, NodeName nodeName, ElementCreationDetails details)
            throws XPathException {
        // no action
    }


    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_ELEMENT;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("compElem", this);
        String flags = getInheritanceFlags();
        if (isLocal()) {
            flags += "l";
        }
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        exportValidationAndType(out);
        out.setChildRole("name");
        getNameExp().export(out);
        if (getNamespaceExp() != null) {
            out.setChildRole("namespace");
            getNamespaceExp().export(out);
        }
        out.setChildRole("content");
        getContentExpression().export(out);
        out.endElement();
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new ComputedElementElaborator();
    }

    /**
     * Elaborator for a FixedElement (literal result element) expression.
     */

    public static class ComputedElementElaborator extends ComplexNodePushElaborator {

        @Override
        @CSharpInnerClass(outer = false, extra = {"Saxon.Hej.expr.instruct.FixedElement instr", "Saxon.Hej.expr.elab.PushEvaluator contentPusher"})
        public PushEvaluator elaborateForPush() {
            final ComputedElement expr = (ComputedElement) getExpression();
            final boolean isXsltInstruction = expr.isXSLT();
            final PushEvaluator contentPusher =
                    expr.getContentExpression().makeElaborator().elaborateForPush();

            final StringEvaluator namespaceEvaluator = expr.getNamespaceExp() == null ?
                    null : expr.getNamespaceExp().makeElaborator().elaborateForString(true);
            final ItemEvaluator localNameEvaluator = expr.getNameExp().makeElaborator().elaborateForItem();
            final ItemEvaluator nodeNameEvaluator = context -> {
                Controller controller = context.getController();
                assert controller != null;

                String prefix;
                String localName;
                NamespaceUri uri = null;

                AtomicValue nameValue = (AtomicValue) localNameEvaluator.eval(context);
                if (nameValue == null) {
                    String errorCode = isXsltInstruction ? "XTDE0820" : "XPTY0004";
                    XPathException err1 = new XPathException("Invalid element name (empty sequence)", errorCode, expr.getLocation());
                    throw dynamicError(expr.getLocation(), err1, context);
                }
                if (nameValue instanceof StringValue) {  // which includes UntypedAtomic
                    // this will always be the case in XSLT
                    String rawName = nameValue.getStringValue();
                    rawName = Whitespace.trim(rawName);
                    // this will always be the case in XSLT
                    if (rawName.startsWith("Q{") && expr.allowNameAsQName) {
                        // Unclear whether this is allowed: see https://github.com/w3c/qtspecs/issues/9
                        // It clearly is NOT allowed in XSLT 3.0 (though for no good reason)
                        try {
                            StructuredQName qn = StructuredQName.fromEQName(rawName);
                            prefix = "";
                            localName = qn.getLocalPart();
                            uri = qn.getNamespaceUri();
                        } catch (IllegalArgumentException e) {
                            throw new XPathException("Invalid EQName in computed element constructor: " + e.getMessage(), "XQDY0074");
                        }
                        if (!NameChecker.isValidNCName(localName)) {
                            throw new XPathException("Local part of EQName in computed element constructor is invalid", "XQDY0074");
                        }
                    } else {
                        try {
                            String[] parts = NameChecker.getQNameParts(rawName);
                            prefix = parts[0];
                            localName = parts[1];
                        } catch (QNameException err) {
                            String message = "Invalid element name. " + err.getMessage();
                            if (rawName.length() == 0) {
                                message = "Supplied element name is a zero-length string";
                            }
                            String errorCode = isXsltInstruction ? "XTDE0820" : "XQDY0074";
                            XPathException err1 = new XPathException(message, errorCode, expr.getLocation());
                            throw dynamicError(expr.getLocation(), err1, context);
                        }
                    }
                } else if (nameValue instanceof QNameValue && expr.allowNameAsQName) {
                    // this is allowed in XQuery
                    localName = ((QNameValue) nameValue).getLocalName();
                    uri = ((QNameValue) nameValue).getNamespaceURI();
                    prefix = ((QNameValue) nameValue).getPrefix();
                    if (prefix.equals("xmlns")) {
                        XPathException err = new XPathException("Computed element name has prefix xmlns", "XQDY0096", expr.getLocation());
                        throw dynamicError(expr.getLocation(), err, context);
                    }
                } else {
                    String errorCode = isXsltInstruction ? "XTDE0820" : "XPTY0004";
                    XPathException err = new XPathException("Computed element name has incorrect type", errorCode, expr.getLocation());
                    err.setIsTypeError(true);
                    throw dynamicError(expr.getLocation(), err, context);
                }

                if (namespaceEvaluator == null && uri == null) {
                    uri = expr.getRetainedStaticContext().getURIForPrefix(prefix, true);
                    if (uri == null) {
                        String errorCode = isXsltInstruction ? "XTDE0830" : prefix.equals("xmlns") ? "XQDY0096" : "XQDY0074";
                        XPathException err = new XPathException("Undeclared prefix in element name: " + prefix, errorCode, expr.getLocation());
                        throw dynamicError(expr.getLocation(), err, context);
                    }
                } else {
                    if (uri == null) {
                        String nsUri = namespaceEvaluator.eval(context);
                        uri = NamespaceUri.of(nsUri);
                        // TODO: bypass check if it's a string literal
                        if (!StandardURIChecker.getInstance().isValidURI(uri.toString())) {
                            XPathException de = new XPathException(
                                    "The value of the namespace attribute must be a valid URI", "XTDE0835", expr.getLocation());
                            throw dynamicError(expr.getLocation(), de, context);
                        }
                    }
                    if (uri.isEmpty()) {
                        // there is a special rule for this case in the specification;
                        // we force the element to go in the null namespace
                        prefix = "";
                    }
                    if (prefix.equals("xmlns")) {
                        // this isn't a legal prefix so we mustn't use it
                        prefix = "x-xmlns";
                    }
                }
                if (uri.equals(NamespaceUri.XMLNS)) {
                    String errorCode = isXsltInstruction ? "XTDE0835" : "XQDY0096";
                    XPathException err = new XPathException("Cannot create element in namespace " + uri, errorCode, expr.getLocation());
                    throw dynamicError(expr.getLocation(), err, context);
                }
                if (uri.equals(NamespaceUri.XML) != prefix.equals("xml")) {
                    String message;
                    if (prefix.equals("xml")) {
                        message = "When the prefix is 'xml', the namespace URI must be " + NamespaceConstant.XML;
                    } else {
                        message = "When the namespace URI is " + NamespaceConstant.XML + ", the prefix must be 'xml'";
                    }
                    String errorCode = isXsltInstruction ? "XTDE0835" : "XQDY0096";
                    XPathException err = new XPathException(message, errorCode, expr.getLocation());
                    throw dynamicError(expr.getLocation(), err, context);
                }

                return new QNameValue(prefix, uri, localName);
            };

            SchemaType typeCode = expr.getValidationAction() == Validation.PRESERVE
                    ? AnyType.getInstance()
                    : Untyped.getInstance();

            return (out, context) -> {
                try {

                    QNameValue elemName = (QNameValue)nodeNameEvaluator.eval(context);

                    Receiver elemOut = out;
                    if (!expr.preservingTypes) {
                        ParseOptions options = expr.getValidationOptions()
                                .withTopLevelElement(elemName.getStructuredQName());
                        context.getConfiguration().prepareValidationReporting(context, options);
                        Receiver validator = context.getConfiguration().getElementValidator(
                                elemOut, options, expr.getLocation());

                        if (validator != elemOut) {
                            out = new ComplexContentOutputter(validator);
                        }
                    }

                    if (out.getSystemId() == null) {
                        out.setSystemId(expr.getStaticBaseURIString());
                    }
                    int properties = ReceiverOption.NONE;
                    if (!expr.bequeathNamespacesToChildren) {
                        properties |= ReceiverOption.DISINHERIT_NAMESPACES;
                    }
                    if (!expr.inheritNamespacesFromParent) {
                        properties |= ReceiverOption.REFUSE_NAMESPACES;
                    }
                    properties |= ReceiverOption.ALL_NAMESPACES;

                    out.startElement(new FingerprintedQName(elemName.getStructuredQName()),
                                     typeCode, expr.getLocation(), properties);

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

