////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.DecimalSymbols;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler for xsl:decimal-format elements in stylesheet. <br>
 */

public class XSLDecimalFormat extends StyleElement {

    boolean prepared = false;

    String name;
    String decimalSeparator;
    String groupingSeparator;
    String exponentSeparator;
    String infinity;
    String minusSign;
    String NaN;
    String percent;
    String perMille;
    String zeroDigit;
    String digit;
    String patternSeparator;

    private Map<String, UnicodeString> decimalProperties = new HashMap<>(10);

    DecimalSymbols symbols;

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

        if (prepared) {
            return;
        }
        prepared = true;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            switch (f) {
                case "name":
                    name = Whitespace.trim(value);
                    break;
                case "decimal-separator":
                case "grouping-separator":
                case "infinity":
                case "minus-sign":
                case "NaN":
                case "percent":
                case "per-mille":
                case "zero-digit":
                case "digit":
                case "exponent-separator":
                case "pattern-separator":
                    decimalProperties.put(f, StringView.of(value));
                    break;
                default:
                    checkUnknownAttribute(attName);
                    break;
            }
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        checkTopLevel("XTSE0010", false);
        checkEmpty();
        int precedence = decl.getPrecedence();

        if (symbols == null) {
            return; // error already reported
        }

        for (Map.Entry<String, UnicodeString> pair : decimalProperties.entrySet()) {
            symbols.setProperty(pair.getKey(), pair.getValue(), precedence);
        }
    }


    /**
     * Method supplied by declaration elements to add themselves to a stylesheet-level index
     *
     * @param decl the Declaration being indexed. (This corresponds to the StyleElement object
     *             except in cases where one module is imported several times with different precedence.)
     * @param top  the outermost XSLStylesheet element
     */

    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) {
        prepareAttributes();
        DecimalFormatManager dfm = getCompilation().getPrincipalStylesheetModule().getDecimalFormatManager();
        if (name == null) {
            symbols = dfm.getDefaultDecimalFormat();
        } else {
            StructuredQName formatName = makeQName(name, null, "name");
            symbols = dfm.obtainNamedDecimalFormat(formatName);
            symbols.setHostLanguage(HostLanguage.XSLT, 30);
        }
    }

    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        // no action
    }

}
