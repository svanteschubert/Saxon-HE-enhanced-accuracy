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
import net.sf.saxon.trans.XPathException;


/**
 * Handler for xsl:when elements in stylesheet. <br>
 * The xsl:while element has a mandatory attribute test, a boolean expression.
 */

public class XSLWhen extends StyleElement {

    private Expression test;
    private Expression select;

    public Expression getCondition() {
        return test;
    }


    @Override
    protected void prepareAttributes() {
        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            if (f.equals("test")) {
                test = makeExpression(att.getValue(), att);
            } else if (f.equals("select")) {
                // XSLT 4.0 proposed extension
                if (requireXslt40Attribute("select")) {
                    select = makeExpression(att.getValue(), att);
                }
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (test == null) {
            reportAbsence("test");
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        if (!(getParent() instanceof XSLChooseOrSwitch)) {
            compileError("xsl:when must be immediately within xsl:choose or xsl:switch", "XTSE0010");
        }
        test = typeCheck("test", test);
        if (select != null && hasChildNodes()) {
            compileError("xsl:when element must be empty if @select is present", "XTSE0010");
        }
    }

    /**
     * Mark tail-recursive calls on stylesheet functions. For most instructions, this does nothing.
     */

    @Override
    protected boolean markTailCalls() {
        StyleElement last = getLastChildInstruction();
        return last != null && last.markTailCalls();
    }

    /*@Nullable*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        return null;
        // compilation is handled from the xsl:choose element
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
