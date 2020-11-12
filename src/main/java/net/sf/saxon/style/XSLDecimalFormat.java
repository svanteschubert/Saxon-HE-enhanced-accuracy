////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.DecimalSymbols;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.AttributeLocation;
import net.sf.saxon.value.Whitespace;

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
    public void prepareAttributes() {

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
                    decimalSeparator = value;
                    break;
                case "grouping-separator":
                    groupingSeparator = value;
                    break;
                case "infinity":
                    infinity = value;
                    break;
                case "minus-sign":
                    minusSign = value;
                    break;
                case "NaN":
                    NaN = value;
                    break;
                case "percent":
                    percent = value;
                    break;
                case "per-mille":
                    perMille = value;
                    break;
                case "zero-digit":
                    zeroDigit = value;
                    break;
                case "digit":
                    digit = value;
                    break;
                case "exponent-separator":
                    exponentSeparator = value;
                    break;
                case "pattern-separator":
                    patternSeparator = value;
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
        if (decimalSeparator != null) {
            setProp(DecimalSymbols.DECIMAL_SEPARATOR, decimalSeparator, precedence);
        }
        if (groupingSeparator != null) {
            setProp(DecimalSymbols.GROUPING_SEPARATOR, groupingSeparator, precedence);
        }
        if (infinity != null) {
            setProp(DecimalSymbols.INFINITY, infinity, precedence);
        }
        if (minusSign != null) {
            setProp(DecimalSymbols.MINUS_SIGN, minusSign, precedence);
        }
        if (NaN != null) {
            setProp(DecimalSymbols.NAN, NaN, precedence);
        }
        if (percent != null) {
            setProp(DecimalSymbols.PERCENT, percent, precedence);
        }
        if (perMille != null) {
            setProp(DecimalSymbols.PER_MILLE, perMille, precedence);
        }
        if (zeroDigit != null) {
            setProp(DecimalSymbols.ZERO_DIGIT, zeroDigit, precedence);
        }
        if (digit != null) {
            setProp(DecimalSymbols.DIGIT, digit, precedence);
        }
        if (exponentSeparator != null) {
            setProp(DecimalSymbols.EXPONENT_SEPARATOR, exponentSeparator, precedence);
        }
        if (patternSeparator != null) {
            setProp(DecimalSymbols.PATTERN_SEPARATOR, patternSeparator, precedence);
        }
    }

    private void setProp(int propertyCode, String value, int precedence) throws XPathException {
        try {
            symbols.setProperty(propertyCode, value, precedence);
        } catch (XPathException err) {
            String attName = DecimalSymbols.propertyNames[propertyCode];
            err.setLocation(new AttributeLocation(this, StructuredQName.fromClarkName(attName)));
            throw err;
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
