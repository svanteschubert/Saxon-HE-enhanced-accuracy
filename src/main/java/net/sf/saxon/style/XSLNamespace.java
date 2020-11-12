////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.NamespaceConstructor;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;

/**
 * An xsl:namespace element in the stylesheet. (XSLT 2.0)
 */

public class XSLNamespace extends XSLLeafNodeConstructor {

    /*@Nullable*/ Expression name;

    @Override
    public void prepareAttributes() {
        name = prepareAttributesNameAndSelect();
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        name = typeCheck("name", name);
        select = typeCheck("select", select);
        int countChildren = 0;
        NodeInfo firstChild = null;
        for (NodeInfo child : children()) {
            if (child instanceof XSLFallback) {
                continue;
            }
            if (select != null) {
                String errorCode = getErrorCodeForSelectPlusContent();
                compileError("An " + getDisplayName() + " element with a select attribute must be empty", errorCode);
            }
            countChildren++;
            if (firstChild == null) {
                firstChild = child;
            } else {
                break;
            }
        }

        if (select == null) {
            if (countChildren == 0) {
                // there are no child nodes and no select attribute
                select = new StringLiteral(StringValue.EMPTY_STRING);
                select.setRetainedStaticContext(makeRetainedStaticContext());
            } else if (countChildren == 1) {
                // there is exactly one child node
                if (firstChild.getNodeKind() == Type.TEXT) {
                    // it is a text node: optimize for this case
                    select = new StringLiteral(firstChild.getStringValueCS());
                    select.setRetainedStaticContext(makeRetainedStaticContext());
                }
            }
        }
    }

    /**
     * Get the error code to be returned when the element has a select attribute but is not empty.
     *
     * @return the error code defined for this condition, for this particular instruction
     */

    @Override
    protected String getErrorCodeForSelectPlusContent() {
        return "XTSE0910";
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        NamespaceConstructor inst = new NamespaceConstructor(name);
        compileContent(exec, decl, inst, new StringLiteral(StringValue.SINGLE_SPACE));
        return inst;
    }

}

