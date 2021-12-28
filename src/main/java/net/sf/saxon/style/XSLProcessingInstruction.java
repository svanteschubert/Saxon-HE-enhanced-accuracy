////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.ProcessingInstruction;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

/**
 * An xsl:processing-instruction element in the stylesheet.
 */

public class XSLProcessingInstruction extends XSLLeafNodeConstructor {

    Expression name;

    @Override
    public void prepareAttributes() {
        name = prepareAttributesNameAndSelect();
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        name = typeCheck("name", name);
        select = typeCheck("select", select);
        super.validate(decl);
    }

    /**
     * Get the error code to be returned when the element has a select attribute but is not empty.
     *
     * @return the error code defined for this condition, for this particular instruction
     */

    @Override
    protected String getErrorCodeForSelectPlusContent() {
        return "XTSE0880";
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        ProcessingInstruction inst = new ProcessingInstruction(name);
        compileContent(exec, decl, inst, new StringLiteral(StringValue.SINGLE_SPACE));
        return inst;
    }

}

