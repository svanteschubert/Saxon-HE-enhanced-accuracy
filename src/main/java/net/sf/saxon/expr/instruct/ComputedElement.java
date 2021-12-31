////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.ContentTypeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;


/**
 * An instruction representing an xsl:element element in an XSLT stylesheet,
 * or a computed element constructor in XQuery. (In both cases, if the element name
 * is expressed as a compile-time expression, then a FixedElement instruction
 * is used instead.)
 *
 * @see FixedElement
 */

public class ComputedElement extends ElementCreator {

    private Operand nameOp;
    private Operand namespaceOp;

    private boolean allowNameAsQName;
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

        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "element/name", 0);
        if (allowNameAsQName) {
            // Can only happen in XQuery
            setNameExp(config.getTypeChecker(false).staticTypeCheck(getNameExp(),
                                                                                SequenceType.SINGLE_ATOMIC, role, visitor));
            ItemType supplied = getNameExp().getItemType();
            if (th.relationship(supplied, BuiltInAtomicType.STRING) == Affinity.DISJOINT &&
                    th.relationship(supplied, BuiltInAtomicType.UNTYPED_ATOMIC) == Affinity.DISJOINT &&
                    th.relationship(supplied, BuiltInAtomicType.QNAME) == Affinity.DISJOINT) {
                XPathException de = new XPathException("The name of a constructed element must be a string, QName, or untypedAtomic");
                de.setErrorCode("XPTY0004");
                de.setIsTypeError(true);
                de.setLocation(getLocation());
                throw de;
            }
        } else {
            if (!th.isSubType(getNameExp().getItemType(), BuiltInAtomicType.STRING)) {
                setNameExp(SystemFunction.makeCall("string", getRetainedStaticContext(), getNameExp()));
            }
        }
        if (Literal.isAtomic(getNameExp())) {
            // Check we have a valid lexical QName, whose prefix is in scope where necessary
            try {
                AtomicValue val = (AtomicValue) ((Literal) getNameExp()).getValue();
                if (val instanceof StringValue) {
                    String[] parts = NameChecker.checkQNameParts(val.getStringValueCS());
                    if (getNamespaceExp() == null) {
                        String prefix = parts[0];
                        String uri = getNamespaceResolver().getURIForPrefix(prefix, true);
                        if (uri == null) {
                            XPathException se = new XPathException("Prefix " + prefix + " has not been declared");
                            se.setErrorCode("XPST0081");
                            se.setIsStaticError(true);
                            throw se;
                        }
                        setNamespaceExp(new StringLiteral(uri));
                    }
                }
            } catch (XPathException e) {
                String code = e.getErrorCodeLocalPart();
                if (code == null || code.equals("FORG0001")) {
                    e.setErrorCode(isXSLT() ? "XTDE0820" : "XQDY0074");
                } else if (code.equals("XPST0081")) {
                    e.setErrorCode(isXSLT() ? "XTDE0830" : "XQDY0074");
                }
                e.maybeSetLocation(getLocation());
                e.setIsStaticError(true);
                throw e;
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
            XPathException err = new XPathException(msg);
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            throw err;
        }
        // NOTE: we could in principle check that if all the elements permitted in the content of the parentType
        // themselves have a simple type (not uncommon, perhaps) then this element must not have element content.
    }


    /**
     * Callback from the superclass ElementCreator to get the nameCode
     * for the element name
     *
     * @param context    The evaluation context (not used)
     * @param copiedNode Not applicable to this overload
     * @return the name code for the element name
     */

    @Override
    public NodeName getElementName(XPathContext context, NodeInfo copiedNode)
            throws XPathException {

        Controller controller = context.getController();
        assert controller != null;

        String prefix;
        String localName;
        String uri = null;

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
            rawName = Whitespace.trimWhitespace(rawName).toString();
            if (rawName.startsWith("Q{") && allowNameAsQName) {
                // Unclear whether this is allowed: see https://github.com/w3c/qtspecs/issues/9
                // It clearly is NOT allowed in XSLT 3.0 (though for no good reason)
                try {
                    StructuredQName qn = StructuredQName.fromEQName(rawName);
                    prefix = "";
                    localName = qn.getLocalPart();
                    uri = qn.getURI();
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
                    uri = ((StringLiteral) getNamespaceExp()).getStringValue();
                } else {
                    uri = getNamespaceExp().evaluateAsString(context).toString();
                    if (!StandardURIChecker.getInstance().isValidURI(uri)) {
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
        if (uri.equals(NamespaceConstant.XMLNS)) {
            String errorCode = isXSLT() ? "XTDE0835" : "XQDY0096";
            XPathException err = new XPathException("Cannot create element in namespace " + uri, errorCode, getLocation());
            throw dynamicError(getLocation(), err, context);
        }
        if (uri.equals(NamespaceConstant.XML) != prefix.equals("xml")) {
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
    public String getNewBaseURI(XPathContext context, NodeInfo copiedNode) {
        return getStaticBaseURIString();
    }

    /**
     * Callback to output namespace nodes for the new element.
     * @param out        the Outputter where the namespace nodes are to be written
     * @param nodeName   The name of the element node being output
     * @param copiedNode Where this is a copied node, the node being copied
     */
    @Override
    public void outputNamespaceNodes(Outputter out, NodeName nodeName, NodeInfo copiedNode)
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


}

