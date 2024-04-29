////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceType;


/**
 * The class implements a xsl:item-type declaration in a stylesheet. This
 * declaration associates a name with an item type, allowing the name (say N)
 * to be used in an itemType with the syntax <code>type(N)</code>.
 */

public class XSLItemType extends StyleElement {

    private StructuredQName itemTypeName;
    private boolean resolved = false;

    @Override
    public boolean isDeclaration() {
        return true;
    }

    @Override
    protected void prepareAttributes() {

        // Avoid reporting errors twice
        if (itemTypeName != null) {
            return;
        }

        String typeAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String value = att.getValue();
            String f = attName.getDisplayName();
            switch (f) {
                case "name":
                    itemTypeName = makeQName(value, null, "name");
                    break;
                case "as":
                    typeAtt = value;
                    break;
                case "visibility":
                    if (!value.equals("private")) {
                        compileErrorInAttribute("Not implemented", "XTSE0010", "visibility");
                    }
                    break;
                default:
                    checkUnknownAttribute(attName);
                    break;
            }
        }

        if (itemTypeName == null) {
            reportAbsence("name");
        }

        if (typeAtt == null) {
            reportAbsence("as");
        }


    }

    @Override
    public StructuredQName getObjectName() {
        if (itemTypeName == null) {
            prepareAttributes();
        }
        return itemTypeName;
    }

    public void indexTypeAlias(ComponentDeclaration decl, PrincipalStylesheetModule top) throws XPathException {
        prepareAttributes();
        top.getTypeAliasManager().processDeclaration(decl);
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        checkEmpty();
        checkTopLevel("XTSE0010", false);
        getConfiguration().checkLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION,
                                                "saxon:item-type",
                                                getPackageData().getLocalLicenseId());
    }

    public ItemType tryToResolve() throws XPathException {
        StaticContext env = new TypeAliasContext(this);
        resolved = true;
        ItemType type = makeItemType(env);
        return resolved ? type : null;
    }

    private void markUnresolved() {
        resolved = false;
    }

    private ItemType makeItemType(StaticContext env) throws XPathException {
        try {
            XPathParser parser =
                    getConfiguration().newExpressionParser("XP", false, env);
            QNameParser qp = new QNameParser(env.getNamespaceResolver())
                    .withAcceptEQName(true)
                    .withErrorOnBadSyntax("XPST0003")
                    .withErrorOnUnresolvedPrefix("XPST0081");
            parser.setQNameParser(qp);
            String typeAtt = getAttributeValue("as");
            if (typeAtt == null) {
                reportAbsence("as");
                typeAtt = "item()";
            }
            SequenceType st = parser.parseExtendedSequenceType(typeAtt, env);
            if (st.getCardinality() != StaticProperty.ALLOWS_ONE) {
                compileError("Item type must not include an occurrence indicator");
            }
            return st.getPrimaryType();
        } catch (XPathException err) {
            compileError(err);
            // recovery path after reporting an error, e.g. undeclared namespace prefix
            return AnyItemType.getInstance();
        }
    }


    /**
     * Compile this XSLT declaration. This always fails for this class.
     *
     * @param exec the Executable
     * @param decl the containing top-level declaration, for example xsl:function or xsl:template
     * @return a compiled expression or null.
     * @throws XPathException (always)
     */

    /*@Nullable*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        return null;
    }

    private static class TypeAliasContext extends ExpressionContext {

        public TypeAliasContext(XSLItemType declaration) {
            super(declaration, NamespaceUri.NULL.qName("as"));
        }

        /**
         * Get type alias. This is a Saxon extension. A type alias is a QName which can
         * be used as a shorthand for an itemtype, using the syntax ~typename anywhere that
         * an item type is permitted.
         *
         * @param typeName the name of the type alias
         * @return the corresponding item type, if the name is recognised; otherwise null.
         */
        @Override
        public ItemType resolveTypeAlias(StructuredQName typeName) {
            ItemType resolved = super.resolveTypeAlias(typeName);
            if (resolved == null) {
                ((XSLItemType)getStyleElement()).markUnresolved();
                return AnyItemType.getInstance();
            } else {
                return resolved;
            }
        }
    }
}

