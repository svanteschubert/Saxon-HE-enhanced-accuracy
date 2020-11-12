////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.BreakInstr;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;

/**
 * A xsl:break element in the stylesheet
 */

public class XSLBreak extends XSLBreakOrContinue {

    private Expression select;

    @Override
    public void prepareAttributes() {

        String selectAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            if (f.equals("select")) {
                selectAtt = value;
                select = makeExpression(selectAtt, att);
            } else {
                checkUnknownAttribute(attName);
            }
        }

    }

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return true if this instruction is allowed to contain a sequence constructor
     */

    @Override
    protected boolean mayContainSequenceConstructor() {
        return true;
    }

    /**
     * Check that the stylesheet element is valid. This is called once for each element, after
     * the entire tree has been built. As well as validation, it can perform first-time
     * initialisation. The default implementation does nothing; it is normally overriden
     * in subclasses.
     *
     * @param decl
     */

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        validatePosition();
        if (xslIterate == null) {
            compileError(getDisplayName() + " must be a descendant of an xsl:iterate instruction", "XTSE3120"); //XTSE0010
        }
        if (select != null && hasChildNodes()) {
            compileError("An xsl:break element with a select attribute must be empty", "XTSE3125");
        }
        select = typeCheck("select", select);
    }


    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        // xsl:break containing a sequence constructor is compiled into a call on the sequence constructor, then
        // the break instruction
        Expression val = select;
        if (val == null) {
            val = compileSequenceConstructor(exec, decl, false);
        }

        Expression brake = new BreakInstr();
        brake.setRetainedStaticContext(makeRetainedStaticContext());
        return Block.makeBlock(val, brake);
    }

}
