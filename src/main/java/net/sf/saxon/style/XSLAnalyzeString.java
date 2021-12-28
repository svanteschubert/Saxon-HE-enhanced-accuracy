////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.AnalyzeString;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.List;

/**
 * An xsl:analyze-string elements in the stylesheet. New at XSLT 2.0<BR>
 */

public class XSLAnalyzeString extends StyleElement {

    /*@Nullable*/ private Expression select;
    private Expression regex;
    private Expression flags;
    private StyleElement matching;
    private StyleElement nonMatching;
    private RegularExpression pattern;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    @Override
    public boolean isInstruction() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain an xsl:fallback
     * instruction
     */

    @Override
    public boolean mayContainFallback() {
        return true;
    }


    @Override
    public void prepareAttributes() {
        String selectAtt = null;
        String regexAtt = null;
        String flagsAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            switch (f) {
                case "regex":
                    regexAtt = value;
                    regex = makeAttributeValueTemplate(regexAtt, att);

                    break;
                case "select":
                    selectAtt = value;
                    select = makeExpression(selectAtt, att);
                    break;
                case "flags":
                    flagsAtt = value; // not trimmed, see bugzilla 4315

                    flags = makeAttributeValueTemplate(flagsAtt, att);
                    break;
                default:
                    checkUnknownAttribute(attName);
                    break;
            }
        }

        if (selectAtt == null) {
            reportAbsence("select");
            select = makeExpression(".", null);    // for error recovery
        }

        if (regexAtt == null) {
            reportAbsence("regex");
            regex = makeAttributeValueTemplate("xxx", null);  // for error recovery
        }

        if (flagsAtt == null) {
            flagsAtt = "";
            flags = makeAttributeValueTemplate("", null);
        }


        if (regex instanceof StringLiteral && flags instanceof StringLiteral) {
            try {
                final String regex = ((StringLiteral) this.regex).getStringValue();
                final String flagstr = ((StringLiteral) flags).getStringValue();

                List<String> warnings = new ArrayList<String>();
                pattern = getConfiguration().compileRegularExpression(
                        regex, flagstr, getEffectiveVersion() >= 30 ? "XP30" : "XP20", warnings);
                for (String w : warnings) {
                    issueWarning(w, this);
                }
            } catch (XPathException err) {
                if ("FORX0001".equals(err.getErrorCodeLocalPart())) {
                    invalidFlags("Error in regular expression flags: " + err.getMessage());
                } else {
                    invalidRegex("Error in regular expression: " + err.getMessage());
                }
            }
        }

    }

    private void invalidRegex(String message) {
        compileErrorInAttribute(message, "XTDE1140", "regex");
        // prevent it being reported more than once
        setDummyRegex();
    }

    private void invalidFlags(String message) {
        compileErrorInAttribute(message, "XTDE1145", "flags");
        // prevent it being reported more than once
        setDummyRegex();
    }

    private void setDummyRegex() {
        try {
            pattern = getConfiguration().compileRegularExpression("x", "", "XP20", null);
        } catch (XPathException err) {
            throw new IllegalStateException();
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        //checkWithinTemplate();

        boolean foundFallback = false;
        for (NodeInfo curr : children()) {
            if (curr instanceof XSLFallback) {
                foundFallback = true;
            } else if (curr instanceof XSLMatchingSubstring) {
                boolean b = curr.getLocalPart().equals("matching-substring");
                if (b) {
                    if (matching != null || nonMatching != null || foundFallback) {
                        compileError("xsl:matching-substring element must come first", "XTSE0010");
                    }
                    matching = (StyleElement) curr;
                } else {
                    if (nonMatching != null || foundFallback) {
                        compileError("xsl:non-matching-substring cannot appear here", "XTSE0010");
                    }
                    nonMatching = (StyleElement) curr;
                }
            } else {
                compileError("Only xsl:matching-substring and xsl:non-matching-substring are allowed here", "XTSE0010");
            }
        }

        if (matching == null && nonMatching == null) {
            compileError("At least one xsl:matching-substring or xsl:non-matching-substring element must be present",
                    "XTSE1130");
        }

        select = typeCheck("select", select);
        regex = typeCheck("regex", regex);
        flags = typeCheck("flags", flags);

    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        Expression matchingBlock = null;
        if (matching != null) {
            matchingBlock = matching.compileSequenceConstructor(exec, decl, false);
        }

        Expression nonMatchingBlock = null;
        if (nonMatching != null) {
            nonMatchingBlock = nonMatching.compileSequenceConstructor(exec, decl, false);
        }

        try {
            return new AnalyzeString(select,
                    regex,
                    flags,
                    matchingBlock == null ? null : matchingBlock.simplify(),
                    nonMatchingBlock == null ? null : nonMatchingBlock.simplify(),
                    pattern);
        } catch (XPathException e) {
            compileError(e);
            return null;
        }
    }


}

