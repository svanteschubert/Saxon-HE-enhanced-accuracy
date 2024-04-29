////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.XPathException;


/**
 * An xsl:output-character element in the stylesheet. <br>
 */

public class XSLOutputCharacter extends StyleElement {

    private int codepoint = -1;
    // the character to be substituted, as a Unicode codepoint (may be > 65535)
    private String replacementString = null;

    @Override
    protected void prepareAttributes() {

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            if (f.equals("character")) {
                switch (value.length()) {
                    case 0:
                        compileError("character attribute must not be zero-length", "XTSE0020");
                        codepoint = 256; // for error recovery
                        break;
                    case 1:
                        codepoint = value.charAt(0);
                        break;
                    case 2:
                        if (UTF16CharacterSet.isHighSurrogate(value.charAt(0)) &&
                                UTF16CharacterSet.isLowSurrogate(value.charAt(1))) {
                            codepoint = UTF16CharacterSet.combinePair(value.charAt(0), value.charAt(1));
                        } else {
                            compileError("character attribute must be a single XML character", "XTSE0020");
                            codepoint = 256; // for error recovery
                        }
                        break;
                    default:
                        compileError("character attribute must be a single XML character", "XTSE0020");
                        codepoint = 256; // for error recovery
                        break;
                }
            } else if (f.equals("string")) {
                replacementString = value;
            } else {
                checkUnknownAttribute(attName);
            }
        }
        if (codepoint == -1) {
            reportAbsence("character");
            codepoint = 256; // for error recovery
            return;
        }

        if (replacementString == null) {
            reportAbsence("string");
            replacementString = ""; // for error recovery
        }

    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        if (!(getParent() instanceof XSLCharacterMap)) {
            compileError("xsl:output-character may appear only as a child of xsl:character-map", "XTSE0010");
        }
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        return null;
    }

    public int getCodePoint() {
        return codepoint;
    }

    public String getReplacementString() {
        return replacementString;
    }

}

