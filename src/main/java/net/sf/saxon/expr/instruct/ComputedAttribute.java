////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

/**
 * An instruction derived from an xsl:attribute element in stylesheet, or from
 * an attribute constructor in XQuery, in cases where the attribute name is not known
 * statically
 */

public final class ComputedAttribute extends AttributeCreator {

    private Operand nameOp;
    private Operand namespaceOp;
    private boolean allowNameAsQName;

    /**
     * Construct an Attribute instruction
     *
     * @param attributeName    An expression to calculate the attribute name
     * @param namespace        An expression to calculate the attribute namespace
     * @param nsContext        a NamespaceContext object containing the static namespace context of the
     *                         stylesheet instruction
     * @param validationAction e.g. validation=strict, lax, strip, preserve
     * @param schemaType       Type against which the attribute must be validated. This must not be a namespace-sensitive
     *                         type; it is the caller's responsibility to check this.
     * @param allowNameAsQName true if the attributeName expression is allowed to evaluate to a value
     *                         of type xs:QName (true in XQuery, false in XSLT)
     */

    public ComputedAttribute(Expression attributeName,
                             Expression namespace,
                             NamespaceResolver nsContext,
                             int validationAction,
                             SimpleType schemaType,
                             boolean allowNameAsQName) {
        nameOp = new Operand(this, attributeName, OperandRole.SINGLE_ATOMIC);
        if (namespace != null) {
            namespaceOp = new Operand(this, namespace, OperandRole.SINGLE_ATOMIC);
        }
        setSchemaType(schemaType);
        setValidationAction(validationAction);
        setOptions(ReceiverOption.NONE);
        this.allowNameAsQName = allowNameAsQName;
    }

    /**
     * Indicate that two attributes with the same name are not acceptable.
     * (This option is set in XQuery, but not in XSLT)
     */

    @Override
    public void setRejectDuplicates() {
        setOptions(getOptions() | ReceiverOption.REJECT_DUPLICATES);
    }

    /**
     * Get the name of this instruction
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_ATTRIBUTE;
    }

    /**
     * Get the expression used to compute the name of the attribute
     *
     * @return the expression used to compute the name of the attribute
     */

    public Expression getNameExp() {
        return nameOp.getChildExpression();
    }

    /**
     * Get the expression used to compute the namespace part of the name of the attribute
     *
     * @return the expression used to compute the namespace part of the name of the attribute
     */

    public Expression getNamespaceExp() {
        return namespaceOp == null ? null : namespaceOp.getChildExpression();
    }

    public void setNameExp(Expression attributeName) {
        nameOp.setChildExpression(attributeName);
    }

    public void setNamespace(Expression namespace) {
        if (namespace != null) {
            if (namespaceOp == null) {
                namespaceOp = new Operand(this, namespace, OperandRole.SINGLE_ATOMIC);
            } else {
                namespaceOp.setChildExpression(namespace);
            }
        }
    }

    @Override
    public Iterable<Operand> operands() {
        return operandSparseList(selectOp, nameOp, namespaceOp);
    }

    /**
     * Get the namespace resolver used to resolve any prefix in the name of the attribute
     *
     * @return the namespace resolver if one has been saved; or null otherwise
     */

    public NamespaceResolver getNamespaceResolver() {
        return getRetainedStaticContext();
    }

    /**
     * Get the static type of this expression
     *
     * @return the static type of the item returned by this expression
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return NodeKindTest.ATTRIBUTE;
    }

    /**
     * Get the static cardinality of this expression
     *
     * @return the static cardinality (exactly one)
     */

    @Override
    public int getCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_ONE;
    }

    /**
     * Ask whether it is allowed for the name to be evaluted as an xs:QName instance (true in XQuery)
     *
     * @return the boolean if name is allowed as a QName
     */
    public boolean isAllowNameAsQName() {
        return allowNameAsQName;
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
        return super.computeSpecialProperties() |
                StaticProperty.SINGLE_DOCUMENT_NODESET;
    }


    @Override
    public void localTypeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        nameOp.typeCheck(visitor, contextItemType);

        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "attribute/name", 0);
        Configuration config = visitor.getConfiguration();
        TypeHierarchy th = config.getTypeHierarchy();

        if (allowNameAsQName) {
            // Can only happen in XQuery
            setNameExp(config.getTypeChecker(false).staticTypeCheck(getNameExp(),
                                                                                        SequenceType.SINGLE_ATOMIC, role, visitor));
            ItemType nameItemType = getNameExp().getItemType();
            boolean maybeString = th.relationship(nameItemType, BuiltInAtomicType.STRING) != Affinity.DISJOINT ||
                    th.relationship(nameItemType, BuiltInAtomicType.UNTYPED_ATOMIC) != Affinity.DISJOINT;
            boolean maybeQName = th.relationship(nameItemType, BuiltInAtomicType.QNAME) != Affinity.DISJOINT;
            if (!(maybeString || maybeQName)) {
                XPathException err = new XPathException(
                        "The attribute name must be either an xs:string, an xs:QName, or untyped atomic");
                err.setErrorCode("XPTY0004");
                err.setIsTypeError(true);
                err.setLocation(getLocation());
                throw err;
            }
        } else {
            if (!th.isSubType(getNameExp().getItemType(), BuiltInAtomicType.STRING)) {
                setNameExp(SystemFunction.makeCall("string", getRetainedStaticContext(), getNameExp()));
            }
        }

        if (getNamespaceExp() != null) {
            namespaceOp.typeCheck(visitor, contextItemType);
        }

        if (Literal.isAtomic(getNameExp())) {
            // Check we have a valid lexical QName, whose prefix is in scope where necessary
            try {
                AtomicValue val = (AtomicValue) ((Literal) getNameExp()).getValue();
                if (val instanceof StringValue) {
                    String[] parts = NameChecker.checkQNameParts(val.getStringValueCS());
                    if (getNamespaceExp() == null) {
                        String uri = getNamespaceResolver().getURIForPrefix(parts[0], false);
                        if (uri == null) {
                            XPathException se = new XPathException("Prefix " + parts[0] + " has not been declared");
                            if (isXSLT()) {
                                se.setErrorCode("XTDE0860");
                                se.setIsStaticError(true);
                                throw se;
                            } else {
                                se.setErrorCode("XQDY0074");
                                se.setIsStaticError(false);
                                throw se;
                            }
                        }
                        setNamespace(new StringLiteral(uri));
                    }
                }
            } catch (XPathException e) {
                if (e.getErrorCodeQName() == null || e.getErrorCodeLocalPart().equals("FORG0001")) {
                    e.setErrorCode(isXSLT() ? "XTDE0850" : "XQDY0074");
                }
                e.maybeSetLocation(getLocation());
                e.setIsStaticError(true);
                throw e;
            }
        }
    }


    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        Expression exp = super.optimize(visitor, contextItemType);
        if (exp != this) {
            return exp;
        }
        // If the name is known statically, use a FixedAttribute instead
        if (getNameExp() instanceof Literal && (getNamespaceExp() == null || getNamespaceExp() instanceof Literal)) {
            XPathContext context = visitor.getStaticContext().makeEarlyEvaluationContext();
            NodeName nc = evaluateNodeName(context);
            FixedAttribute fa = new FixedAttribute(nc, getValidationAction(), getSchemaType());
            fa.setSelect(getSelect());
            return fa;
        }
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
        ComputedAttribute exp = new ComputedAttribute(
                getNameExp() == null ? null : getNameExp().copy(rebindings),
                getNamespaceExp() == null ? null : getNamespaceExp().copy(rebindings),
                getRetainedStaticContext(), getValidationAction(), getSchemaType(), allowNameAsQName);
        ExpressionTool.copyLocationInfo(this, exp);
        exp.setSelect(getSelect().copy(rebindings));
        exp.setInstruction(isInstruction());
        return exp;
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
            String msg = "Attributes are not permitted here: ";
            if (parentType.isAnonymousType()) {
                msg += "the containing element is defined to have a simple type";
            } else {
                msg += "the containing element is of simple type " + parentType.getDescription();
            }
            XPathException err = new XPathException(msg);
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            throw err;
        }
    }


    /**
     * Determine the name to be used for the attribute, as an integer name code
     *
     * @param context Dynamic evaluation context
     * @return the integer name code for the attribute name
     * @throws XPathException if a dynamic error occurs (for example, if the attribute name is invalid)
     */

    @Override
    public NodeName evaluateNodeName(XPathContext context) throws XPathException {
        NamePool pool = context.getNamePool();

        Item nameValue = getNameExp().evaluateItem(context);

        String prefix;
        String localName;
        String uri = null;

        if (nameValue instanceof StringValue) {
            // this will always be the case in XSLT
            String rawName = nameValue.getStringValue();
            rawName = Whitespace.trimWhitespace(rawName).toString(); // required in XSLT; possibly wrong in XQuery
            if (rawName.startsWith("Q{") && allowNameAsQName) { // not allowed in XSLT; a little unclear in XQuery
                try {
                    StructuredQName qn = StructuredQName.fromEQName(rawName);
                    prefix = "";
                    localName = qn.getLocalPart();
                    uri = qn.getURI();
                } catch (IllegalArgumentException e) {
                    throw new XPathException("Invalid EQName in computed attribute constructor: " + e.getMessage(), "XQDY0074");
                }
                if (!NameChecker.isValidNCName(localName)) {
                    throw new XPathException("Local part of EQName in computed attribute constructor is invalid", "XQDY0074");

                }
            } else {
                try {
                    String[] parts = NameChecker.getQNameParts(rawName);
                    prefix = parts[0];
                    localName = parts[1];
                } catch (QNameException err) {
                    String errorCode = isXSLT() ? "XTDE0850" : "XQDY0074";
                    XPathException err1 = new XPathException("Invalid attribute name: " + rawName, errorCode, this.getLocation());
                    throw dynamicError(getLocation(), err1, context);
                }
                if (rawName.toString().equals("xmlns")) {
                    if (getNamespaceExp() == null) {
                        String errorCode = isXSLT() ? "XTDE0855" : "XQDY0044";
                        XPathException err = new XPathException("Invalid attribute name: " + rawName, errorCode, this.getLocation());
                        throw dynamicError(getLocation(), err, context);
                    }
                }
            }
            if (prefix.equals("xmlns")) {
                if (getNamespaceExp() == null) {
                    String errorCode = isXSLT() ? "XTDE0860" : "XQDY0044";
                    XPathException err = new XPathException("Invalid attribute name: " + rawName, errorCode, this.getLocation());
                    throw dynamicError(getLocation(), err, context);
                } else {
                    // ignore the prefix "xmlns"
                    prefix = "";
                }
            }

        } else if (nameValue instanceof QNameValue && allowNameAsQName) {
            // this is allowed in XQuery
            localName = ((QNameValue) nameValue).getLocalName();
            uri = ((QNameValue) nameValue).getNamespaceURI();
            if (localName.equals("xmlns") && uri.isEmpty()) {
                XPathException err = new XPathException("Invalid attribute name: xmlns", "XQDY0044", this.getLocation());
                throw dynamicError(getLocation(), err, context);
            }

            if (uri.isEmpty()) {
                prefix = "";
            } else {
                prefix = ((QNameValue) nameValue).getPrefix();
                if (prefix.isEmpty()) {
                    prefix = pool.suggestPrefixForURI(uri);
                    if (prefix == null) {
                        prefix = "ns0";
                        // If the prefix is a duplicate, a different one will be substituted
                    }
                }
                if (uri.equals(NamespaceConstant.XML) != "xml".equals(prefix)) {
                    String message;
                    if ("xml".equals(prefix)) {
                        message = "When the prefix is 'xml', the namespace URI must be " + NamespaceConstant.XML;
                    } else {
                        message = "When the namespace URI is " + NamespaceConstant.XML + ", the prefix must be 'xml'";
                    }
                    String errorCode = isXSLT() ? "XTDE0835" : "XQDY0044";
                    XPathException err = new XPathException(message, errorCode, this.getLocation());
                    throw dynamicError(getLocation(), err, context);
                }
            }

            if ("xmlns".equals(prefix)) {
                XPathException err = new XPathException("Invalid attribute namespace: http://www.w3.org/2000/xmlns/", "XQDY0044", this.getLocation());
                throw dynamicError(getLocation(), err, context);
            }

        } else {
            XPathException err = new XPathException("Attribute name must be either a string or a QName", "XPTY0004", this.getLocation());
            err.setIsTypeError(true);
            throw dynamicError(getLocation(), err, context);
        }

        if (getNamespaceExp() == null && uri == null) {
            if (prefix.isEmpty()) {
                uri = "";
            } else {
                uri = getRetainedStaticContext().getURIForPrefix(prefix, false);
                if (uri == null) {
                    String errorCode = isXSLT() ? "XTDE0860" : "XQDY0074";
                    XPathException err = new XPathException("Undeclared prefix in attribute name: " + prefix, errorCode, this.getLocation());
                    throw dynamicError(getLocation(), err, context);
                }
            }

        } else {
            if (uri == null) {
                // generate a name using the supplied namespace URI
                if (getNamespaceExp() instanceof StringLiteral) {
                    uri = ((StringLiteral) getNamespaceExp()).getStringValue();
                } else {
                    uri = getNamespaceExp().evaluateAsString(context).toString();
                    if (!StandardURIChecker.getInstance().isValidURI(uri)) {
                        XPathException de = new XPathException("The value of the namespace attribute must be a valid URI", "XTDE0865", this.getLocation());
                        throw dynamicError(getLocation(), de, context);
                    }
                }
            }
            if (uri.isEmpty()) {
                // there is a special rule for this case in the XSLT specification;
                // we force the attribute to go in the null namespace
                prefix = "";

            } else {
                // if a suggested prefix is given, use it; otherwise try to find a prefix
                // associated with this URI; if all else fails, invent one.
                if (prefix.isEmpty()) {
                    prefix = pool.suggestPrefixForURI(uri);
                    if (prefix == null) {
                        prefix = "ns0";
                        // this will be replaced later if it is already in use
                    }
                }
            }
        }

        if (uri.equals(NamespaceConstant.XMLNS)) {
            String errorCode = isXSLT() ? "XTDE0865" : "XQDY0044";
            XPathException err = new XPathException("Cannot create attribute in namespace " + uri, errorCode, this.getLocation());
            throw dynamicError(getLocation(), err, context);
        }

        return new FingerprintedQName(prefix, uri, localName);
    }

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        Item node = super.evaluateItem(context);
        validateOrphanAttribute((Orphan) node, context);
        return node;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("compAtt", this);
        if (getValidationAction() != Validation.SKIP) {
            out.emitAttribute("validation", Validation.toString(getValidationAction()));
        }
        SimpleType type = getSchemaType();
        if (type != null) {
            out.emitAttribute("type", type.getStructuredQName());
        }
        String flags = "";
        if (isLocal()) {
            flags += "l";
        }
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        out.setChildRole("name");
        getNameExp().export(out);
        if (getNamespaceExp() != null) {
            out.setChildRole("namespace");
            getNamespaceExp().export(out);
        }
        out.setChildRole("select");
        getSelect().export(out);
        out.endElement();
    }


}

