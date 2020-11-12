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
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.functions.NormalizeSpace_1;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.*;

import java.util.function.BiConsumer;

/**
 * An instruction derived from an xsl:attribute element in stylesheet, or from
 * an attribute constructor in XQuery. This version deals only with attributes
 * whose name is known at compile time. It is also used for attributes of
 * literal result elements. The value of the attribute is in general computed
 * at run-time.
 */

public final class FixedAttribute extends AttributeCreator {

    private NodeName nodeName;

    /**
     * Construct an Attribute instruction
     *
     * @param nodeName         Represents the attribute name
     * @param validationAction the validation required, for example strict or lax
     * @param schemaType       the schema type against which validation is required, null if not applicable
     *                         of the instruction - zero if the attribute was not present
     */

    public FixedAttribute(NodeName nodeName,
                          int validationAction,
                          SimpleType schemaType) {
        this.nodeName = nodeName;
        setSchemaType(schemaType);
        setValidationAction(validationAction);
        setOptions(ReceiverOption.NONE);
    }

    /**
     * Get the name of this instruction (return 'xsl:attribute')
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_ATTRIBUTE;
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "att";
    }

    public NodeName getAttributeName() {
        return nodeName;
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
        consumer.accept("name", getAttributeName());
    }

    @Override
    public void localTypeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        // If attribute name is xml:id, add whitespace normalization
        if (nodeName.equals(StandardNames.XML_ID_NAME) && !getSelect().isCallOn(NormalizeSpace_1.class)) {
            Expression select = SystemFunction.makeCall(
                    "normalize-space", getRetainedStaticContext(), getSelect());
            setSelect(select);
        }

        Configuration config = visitor.getConfiguration();
        final ConversionRules rules = config.getConversionRules();
        SimpleType schemaType = getSchemaType();
        String errorCode = "XTTE1540";
        if (schemaType == null) {
            int validation = getValidationAction();
            if (validation == Validation.STRICT) {
                SchemaDeclaration decl = config.getAttributeDeclaration(nodeName.getStructuredQName());
                if (decl == null) {
                    XPathException se = new XPathException(
                            "Strict validation fails: there is no global attribute declaration for " +
                                    nodeName.getDisplayName());
                    se.setErrorCode("XTTE1510");
                    se.setLocation(getLocation());
                    throw se;
                }
                schemaType = (SimpleType) decl.getType();
                errorCode = "XTTE1510";
            } else if (validation == Validation.LAX) {
                SchemaDeclaration decl = config.getAttributeDeclaration(nodeName.getStructuredQName());
                if (decl != null) {
                    schemaType = (SimpleType) decl.getType();
                    errorCode = "XTTE1515";
                } else {
                    visitor.getStaticContext().issueWarning(
                            "Lax validation has no effect: there is no global attribute declaration for " +
                                    nodeName.getDisplayName(), getLocation());
                }
            }
        }

        // Attempt early validation if possible
        if (Literal.isAtomic(getSelect()) && schemaType != null && !schemaType.isNamespaceSensitive()) {
            CharSequence value = ((Literal) getSelect()).getValue().getStringValueCS();
            ValidationFailure err = schemaType.validateContent(
                    value, DummyNamespaceResolver.getInstance(), rules);
            if (err != null) {
                XPathException se = new XPathException("Attribute value " + Err.wrap(value, Err.VALUE) +
                        " does not the match the required type " +
                        schemaType.getDescription() + ". " +
                        err.getMessage());
                se.setErrorCode(errorCode);
                throw se;
            }
        }

        // If value is fixed, test whether there are any special characters that might need to be
        // escaped when the time comes for serialization
        if (getSelect() instanceof StringLiteral) {
            boolean special = false;
            CharSequence val = ((StringLiteral) getSelect()).getStringValue();
            for (int k = 0; k < val.length(); k++) {
                char c = val.charAt(k);
                if ((int) c < 33 || (int) c > 126 ||
                        c == '<' || c == '>' || c == '&' || c == '\"' || c == '\'') {
                    special = true;
                    break;
                }
            }
            if (!special) {
                setNoSpecialChars();
            }
        }
    }

    /**
     * Get the name pool name code of the attribute to be constructed
     *
     * @return the attribute's name code
     */

    public int getAttributeFingerprint() {
        return nodeName.getFingerprint();
    }


    @Override
    public int getCardinality() {
        return StaticProperty.EXACTLY_ONE;
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
        FixedAttribute exp = new FixedAttribute(nodeName, getValidationAction(), getSchemaType());
        ExpressionTool.copyLocationInfo(this, exp);
        exp.setSelect(getSelect().copy(rebindings));
        exp.setInstruction(isInstruction());
        return exp;
    }

    @Override
    public NodeName evaluateNodeName(XPathContext context) {
        return nodeName;
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
        int fp = nodeName.getFingerprint();
        if (fp == StandardNames.XSI_TYPE ||
                fp == StandardNames.XSI_SCHEMA_LOCATION ||
                fp == StandardNames.XSI_NIL ||
                fp == StandardNames.XSI_NO_NAMESPACE_SCHEMA_LOCATION) {
            return;
        }
        if (parentType instanceof SimpleType) {
            XPathException err = new XPathException("Attribute " + nodeName.getDisplayName() +
                    " is not permitted in the content model of the simple type " + parentType.getDescription());
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            err.setErrorCode(getPackageData().isXSLT() ? "XTTE1510" : "XQDY0027");
            throw err;
        }
        SchemaType type;
        try {
            type = ((ComplexType) parentType).getAttributeUseType(nodeName.getStructuredQName());
        } catch (SchemaException e) {
            throw new XPathException(e);
        }
        if (type == null) {
            XPathException err = new XPathException("Attribute " + nodeName.getDisplayName() +
                    " is not permitted in the content model of the complex type " + parentType.getDescription());
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            err.setErrorCode(getPackageData().isXSLT() ? "XTTE1510" : "XQDY0027");
            throw err;
        }

        try {
            // When select is a SimpleContentConstructor, this does nothing
            getSelect().checkPermittedContents(type, true);
        } catch (XPathException e) {
            e.maybeSetLocation(getLocation());
            throw e;
        }
    }


    @Override
    public NodeInfo evaluateItem(XPathContext context) throws XPathException {
        Orphan o = (Orphan) super.evaluateItem(context);
        assert o != null;
        validateOrphanAttribute(o, context);
        return o;
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("att", this);
        out.emitAttribute("name", nodeName.getDisplayName());
        if (!nodeName.getStructuredQName().hasURI("")) {
            out.emitAttribute("nsuri", nodeName.getStructuredQName().getURI());
        }
        if (getValidationAction() != Validation.SKIP && getValidationAction() != Validation.BY_TYPE) {
            out.emitAttribute("validation", Validation.toString(getValidationAction()));
        }
        if (getSchemaType() != null) {
            out.emitAttribute("type", getSchemaType().getStructuredQName());
        }
        String flags = "";
        if (isLocal()) {
            flags += "l";
        }
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        getSelect().export(out);
        out.endElement();
    }

    /**
     * Produce a short string identifying the expression for use in error messages
     *
     * @return a short string, sufficient to identify the expression
     */
    @Override
    public String toShortString() {
        return "attr{" + nodeName.getDisplayName() + "=...}";
    }
}

