////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.event.Stripper;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.qname.*;
import net.sf.saxon.trans.XPathException;

import java.util.StringTokenizer;

/**
 * An xsl:preserve-space or xsl:strip-space elements in stylesheet. <br>
 */

public class XSLPreserveSpace extends StyleElement {

    private String elements;

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

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            if (f.equals("elements")) {
                elements = att.getValue();
            } else {
                checkUnknownAttribute(attName);
            }
        }
        if (elements == null) {
            reportAbsence("elements");
            elements = "*";   // for error recovery
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        checkEmpty();
        checkTopLevel("XTSE0010", false);
    }

    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) {
        if (getFingerprint() == StandardNames.XSL_STRIP_SPACE) {
            if (getFingerprint() == StandardNames.XSL_STRIP_SPACE) {
                String elements = getAttributeValue(NamespaceUri.NULL, "elements");
                if (elements != null && !elements.trim().isEmpty()) {
                    top.getStylesheetPackage().setStripsWhitespace(true);
                }
            }
        }
    }

    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl)  {
        Stripper.StripRuleTarget preserve =
                getFingerprint() == StandardNames.XSL_PRESERVE_SPACE ? Stripper.PRESERVE : Stripper.STRIP;
        PrincipalStylesheetModule psm = getCompilation().getPrincipalStylesheetModule();
        SpaceStrippingRule stripperRules = psm.getStylesheetPackage().getStripperRules();
        if (!(stripperRules instanceof SelectedElementsSpaceStrippingRule)) {
            stripperRules = new SelectedElementsSpaceStrippingRule(true);
            psm.getStylesheetPackage().setStripperRules(stripperRules);
        }

        SelectedElementsSpaceStrippingRule rules = (SelectedElementsSpaceStrippingRule) stripperRules;

        // elements is a space-separated list of element names or name tests

        StringTokenizer st = new StringTokenizer(elements, " \t\n\r", false);
        try {
            while (st.hasMoreTokens()) {
                String s = st.nextToken();
                QNameTest test = makeQNameTest(s, true, "elements");
                rules.addRule(test, preserve, decl.getModule(), decl.getSourceElement().getLineNumber());
            }
        } catch (XPathException e) {
            compileError(e.maybeWithLocation(allocateLocation()));
        }
    }


}

