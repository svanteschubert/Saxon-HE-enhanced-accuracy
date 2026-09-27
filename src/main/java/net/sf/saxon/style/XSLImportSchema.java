////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;


/**
 * Compile-time representation of an xsl:import-schema declaration
 * in a stylesheet
 */

public class XSLImportSchema extends StyleElement {

    /**
     * Ask whether this node is a declaration, that is, a permitted child of xsl:stylesheet
     * (including xsl:include and xsl:import).
     *
     * @return true for this element
     */

    @Override
    public boolean isDeclaration() {
        return true;
    }

    @Override
    protected void prepareAttributes() {

        String namespace = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String value = att.getValue();
            String f = attName.getDisplayName();
            if (f.equals("schema-location")) {
                //
            } else if (f.equals("namespace")) {
                namespace = Whitespace.trim(value);
            } else if (f.equals("role")) {
                if (requireXslt40Attribute("role")) {
                    String role = Whitespace.trim(value);
                    if (!NameChecker.isValidNCName(role)) {
                        compileErrorInAttribute("Value of @role must be a valid NCName", "XTSE0020", "role");
                    }
                }
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if ("".equals(namespace)) {
            compileError("The zero-length string is not a valid namespace URI. " +
                    "For a schema with no namespace, omit the namespace attribute");
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        checkTopLevel("XTSE0010", false);
    }

    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) throws XPathException {
        //
    }

    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        // No action. The effect of import-schema is compile-time only
    }

    /**
     * Get the schema role (4.0)
     * @return the value of the @role attribute, or null if absent
     */

    public String getSchemaRole() {
        // Called before prepareAttributes() has run
        return getAttributeValue("role");
    }
}

