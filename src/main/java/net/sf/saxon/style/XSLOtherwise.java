////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;


/**
 * Handler for xsl:otherwise elements in stylesheet.
 */

public class XSLOtherwise extends StyleElement {

    private Expression select;

    @Override
    public void prepareAttributes() {
        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            if (f.equals("select")) {
                // Saxon extension
                requireSyntaxExtensions("select");
                select = makeExpression(att.getValue(), att);
            } else {
                checkUnknownAttribute(attName);
            }
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        if (!(getParent() instanceof XSLChoose)) {
            compileError("xsl:otherwise must be immediately within xsl:choose", "XTSE0010");
        }
        if (select != null && hasChildNodes()) {
            compileError("xsl:otherwise element must be empty if @select is present", "XTSE0010");
        }
    }

    /**
     * Mark tail-recursive calls on stylesheet functions. For most instructions, this does nothing.
     */

    @Override
    public boolean markTailCalls() {
        StyleElement last = getLastChildInstruction();
        return last != null && last.markTailCalls();
    }

    /*@NotNull*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        throw new UnsupportedOperationException("XSLOtherwise#compile() should not be called");
    }

    @Override
    public Expression compileSequenceConstructor(Compilation compilation, ComponentDeclaration decl,
                                                 boolean includeParams) throws XPathException {
        if (select == null) {
            return super.compileSequenceConstructor(compilation, decl, includeParams);
        } else {
            return select;
        }
    }

}

