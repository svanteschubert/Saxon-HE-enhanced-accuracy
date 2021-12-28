////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.ComputedAttribute;
import net.sf.saxon.expr.instruct.FixedAttribute;
import net.sf.saxon.expr.instruct.Instruction;
import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

/**
 * xsl:attribute element in stylesheet. <br>
 */

public final class XSLAttribute extends XSLLeafNodeConstructor {

    /*@Nullable*/ private Expression attributeName;
    private Expression separator;
    private Expression namespace = null;
    private int validationAction = Validation.PRESERVE;
    private SimpleType schemaType;

    @Override
    public void prepareAttributes() {

        String nameAtt = null;
        String namespaceAtt = null;
        String selectAtt = null;
        String separatorAtt = null;
        String validationAtt = null;
        String typeAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String value = att.getValue();
            String f = attName.getDisplayName();
            switch (f) {
                case "name":
                    nameAtt = Whitespace.trim(value);
                    attributeName = makeAttributeValueTemplate(nameAtt, att);
                    break;
                case "namespace":
                    namespaceAtt = Whitespace.trim(value);
                    namespace = makeAttributeValueTemplate(namespaceAtt, att);
                    break;
                case "select":
                    selectAtt = value;
                    select = makeExpression(selectAtt, att);
                    break;
                case "separator":
                    separatorAtt = value;
                    separator = makeAttributeValueTemplate(separatorAtt, att);
                    break;
                case "validation":
                    validationAtt = Whitespace.trim(value);
                    break;
                case "type":
                    typeAtt = Whitespace.trim(value);
                    break;
                default:
                    checkUnknownAttribute(attName);
                    break;
            }
        }

        if (nameAtt == null) {
            reportAbsence("name");
            return;
        }

        if (attributeName instanceof StringLiteral) {
            if (!NameChecker.isQName(((StringLiteral) attributeName).getStringValue())) {
                invalidAttributeName("Attribute name " + Err.wrap(nameAtt) + " is not a valid QName");
            }
            if (nameAtt.equals("xmlns")) {
                if (namespace == null) {
                    invalidAttributeName("Invalid attribute name: xmlns");
                }
            }
            if (nameAtt.startsWith("xmlns:")) {
                if (namespaceAtt == null) {
                    invalidAttributeName("Invalid attribute name: " + Err.wrap(nameAtt));
                } else {
                    // ignore the prefix "xmlns"
                    nameAtt = nameAtt.substring(6);
                    attributeName = new StringLiteral(nameAtt);
                }
            }
        }


        if (namespaceAtt != null) {
            if (namespace instanceof StringLiteral) {
                if (!StandardURIChecker.getInstance().isValidURI(((StringLiteral) namespace).getStringValue())) {
                    compileError("The value of the namespace attribute must be a valid URI", "XTDE0865");
                }
            }
        }

        if (separatorAtt == null) {
            if (selectAtt == null) {
                separator = new StringLiteral(StringValue.EMPTY_STRING);
            } else {
                separator = new StringLiteral(StringValue.SINGLE_SPACE);
            }
        }

        if (validationAtt != null) {
            validationAction = validateValidationAttribute(validationAtt);
        } else {
            validationAction = getDefaultValidation();
        }

        if (typeAtt != null) {
            if (!isSchemaAware()) {
                compileError(
                        "The @type attribute is available only with a schema-aware XSLT processor", "XTSE1660");
            } else {
                SchemaType type = getSchemaType(typeAtt);
                if (type == null) {
                    compileError("Unknown attribute type " + typeAtt, "XTSE1520");
                } else {
                    if (type.isSimpleType()) {
                        schemaType = (SimpleType) type;
                    } else {
                        compileError("Type annotation for attributes must be a simple type", "XTSE1530");
                    }
                }
                validationAction = Validation.BY_TYPE;
            }
        }

        if (typeAtt != null && validationAtt != null) {
            compileError("The validation and type attributes are mutually exclusive", "XTSE1505");
            validationAction = getDefaultValidation();
            schemaType = null;
        }
    }

    private void invalidAttributeName(String message) {
        compileErrorInAttribute(message, "XTDE0850", "name");
        // prevent a duplicate error message...
        attributeName = new StringLiteral("saxon-error-attribute");
//        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        if (schemaType != null) {
            if (schemaType.isNamespaceSensitive()) {
                compileErrorInAttribute("Validation at attribute level must not specify a " +
                        "namespace-sensitive type (xs:QName or xs:NOTATION)", "XTTE1545", "type");
            }
        }

        attributeName = typeCheck("name", attributeName);
        namespace = typeCheck("namespace", namespace);
        select = typeCheck("select", select);
        separator = typeCheck("separator", separator);
        //onEmpty = typeCheck("on-empty", onEmpty);
        super.validate(decl);
    }

    /**
     * Get the error code to be returned when the element has a select attribute but is not empty.
     *
     * @return the error code defined for this condition, for this particular instruction
     */

    @Override
    protected String getErrorCodeForSelectPlusContent() {
        return "XTSE0840";
    }

    @Override
    public Instruction compile(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        NamespaceResolver nsContext = null;

        // deal specially with the case where the attribute name is known statically

        if (attributeName instanceof StringLiteral /*&& onEmpty == null*/) {
            String qName = Whitespace.trim(((StringLiteral) attributeName).getStringValue());
            String[] parts;
            try {
                parts = NameChecker.getQNameParts(qName);
            } catch (QNameException e) {
                // This can't happen, because of previous checks
                return null;
            }

            if (namespace == null) {
                String nsuri = "";
                if (!parts[0].equals("")) {
                    nsuri = getURIForPrefix(parts[0], false);
                    if (nsuri == null) {
                        undeclaredNamespaceError(parts[0], "XTDE0860", "name");
                        return null;
                    }
                }
                NodeName attributeName = new FingerprintedQName(parts[0], nsuri, parts[1]);
                attributeName.obtainFingerprint(getNamePool());
                FixedAttribute inst = new FixedAttribute(attributeName,
                        validationAction,
                        schemaType);
                inst.setInstruction(true);
                inst.setLocation(allocateLocation());
                compileContent(compilation, decl, inst, separator);
                return inst;
            } else if (namespace instanceof StringLiteral) {
                String nsuri = ((StringLiteral) namespace).getStringValue();
                if (nsuri.equals("")) {
                    parts[0] = "";
                } else if (parts[0].equals("")) {
                    // Need to choose an arbitrary prefix
                    // First see if the requested namespace is declared in the stylesheet
                    AxisIterator iter = iterateAxis(AxisInfo.NAMESPACE);
                    NodeInfo ns;
                    while ((ns = iter.next()) != null) {
                        if (ns.getStringValue().equals(nsuri)) {
                            parts[0] = ns.getLocalPart();
                            break;
                        }
                    }
                    // Otherwise see the URI is known to the namepool
                    if (parts[0].equals("")) {
                        String p = getNamePool().suggestPrefixForURI(
                                ((StringLiteral) namespace).getStringValue());
                        if (p != null) {
                            parts[0] = p;
                        }
                    }
                    // Otherwise choose something arbitrary. This will get changed
                    // if it clashes with another attribute
                    if (parts[0].equals("")) {
                        parts[0] = "ns0";
                    }
                }
                NodeName nodeName = new FingerprintedQName(parts[0], nsuri, parts[1]);
                nodeName.obtainFingerprint(getNamePool());
                FixedAttribute inst = new FixedAttribute(nodeName, validationAction, schemaType);
                inst.setInstruction(true);
                compileContent(compilation, decl, inst, separator);
                return inst;
            }
        } else {
            // if the namespace URI must be deduced at run-time from the attribute name
            // prefix, we need to save the namespace context of the instruction

            if (namespace == null) {
                nsContext = getAllNamespaces();
            }
        }

        ComputedAttribute inst = new ComputedAttribute(attributeName,
                namespace,
                nsContext,
                validationAction,
                schemaType,
                false);
        //inst.bindStaticContext(getStaticContext());
        inst.setInstruction(true);
        inst.setLocation(allocateLocation());
        compileContent(compilation, decl, inst, separator);
        return inst;
    }

}

