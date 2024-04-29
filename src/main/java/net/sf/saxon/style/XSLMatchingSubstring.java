////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;

/**
 * Handler for xsl:matching-substring and xsl:non-matching-substring elements in stylesheet.
 * New at XSLT 2.0<BR>
 */

public class XSLMatchingSubstring extends StyleElement {

    private Expression select = null;

    @Override
    protected void prepareAttributes() {

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String value = att.getValue();
            String f = attName.getDisplayName();
            if (f.equals("select")) {
                if (requireXslt40Attribute("select")) {
                    select = makeExpression(value, att);
                }
            } else {
                checkUnknownAttribute(attName);
            }
        }

    }

    public Expression getSelectExpression() {
        return select;
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body
     *
     * @return true: yes, it may contain a template-body
     */

    @Override
    protected boolean mayContainSequenceConstructor() {
        return true;
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        if (!(getParent() instanceof XSLAnalyzeString)) {
            compileError(getDisplayName() + " must be immediately within xsl:analyze-string", "XTSE0010");
        }
        if (select != null) {
            for (NodeInfo child : children()) {
                if (!(child instanceof XSLFallback)) {
                    if (select != null) {
                        compileError("An " + getDisplayName() + " element with a select attribute must be empty", "XTSE3185");
                    }
                    break;
                }
            }
            select = typeCheck("select", select);
        }
    }

    /*@NotNull*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        throw new UnsupportedOperationException("XSLMatchingSubstring#compile() should not be called");
    }

}

