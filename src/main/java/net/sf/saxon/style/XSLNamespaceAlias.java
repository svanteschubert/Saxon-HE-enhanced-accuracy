////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;


/**
 * An xsl:namespace-alias element in the stylesheet. <br>
 */

public class XSLNamespaceAlias extends StyleElement {

    private String stylesheetURI;
    private NamespaceBinding resultNamespaceBinding;

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
    public void prepareAttributes() {

        String stylesheetPrefix = null;
        String resultPrefix = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            if (f.equals("stylesheet-prefix")) {
                stylesheetPrefix = Whitespace.trim(value);
            } else if (f.equals("result-prefix")) {
                resultPrefix = Whitespace.trim(value);
            } else {
                checkUnknownAttribute(attName);
            }
        }
        if (stylesheetPrefix == null) {
            reportAbsence("stylesheet-prefix");
            stylesheetPrefix = "";
        }
        if (stylesheetPrefix.equals("#default")) {
            stylesheetPrefix = "";
        }
        if (resultPrefix == null) {
            reportAbsence("result-prefix");
            resultPrefix = "";
        }
        if (resultPrefix.equals("#default")) {
            resultPrefix = "";
        }
        stylesheetURI = getURIForPrefix(stylesheetPrefix, true);
        if (stylesheetURI == null) {
            compileError("stylesheet-prefix " + stylesheetPrefix + " has not been declared", "XTSE0812");
            // recovery action
            stylesheetURI = "";
            resultNamespaceBinding = NamespaceBinding.DEFAULT_UNDECLARATION;
            return;
        }
        String resultURI = getURIForPrefix(resultPrefix, true);
        if (resultURI == null) {
            compileError("result-prefix " + resultPrefix + " has not been declared", "XTSE0812");
            // recovery action
            stylesheetURI = "";
            resultURI = "";
        }
        resultNamespaceBinding = new NamespaceBinding(resultPrefix, resultURI);
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        checkTopLevel("XTSE0010", false);
    }

    /*@Nullable*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        return null;
    }

    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) throws XPathException {
        top.addNamespaceAlias(decl);
    }

    public String getStylesheetURI() {
        return stylesheetURI;
    }

    public NamespaceBinding getResultNamespaceBinding() {
        return resultNamespaceBinding;
    }

}

