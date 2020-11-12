////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.ValueOf;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.TextImpl;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for xsl:text elements in stylesheet. <BR>
 */

public class XSLText extends XSLLeafNodeConstructor {

    private boolean disable = false;
    private StringValue value;


    @Override
    public void prepareAttributes() {

        String disableAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String value = att.getValue();
            String f = attName.getDisplayName();
            if (f.equals("disable-output-escaping")) {
                disableAtt = Whitespace.trim(value);
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (disableAtt != null) {
            disable = processBooleanAttribute("disable-output-escaping", disableAtt);
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {

        value = StringValue.EMPTY_STRING;
        for (NodeInfo child : children()) {
            if (child instanceof StyleElement) {
                ((StyleElement) child).compileError("xsl:text must not contain child elements", "XTSE0010");
                return;
            } else {
                value = StringValue.makeStringValue(child.getStringValueCS());
                //continue;
            }
        }
        super.validate(decl);
    }

    /**
     * Get the error code to be returned when the element has a select attribute but is not empty.
     *
     * @return the error code defined for this condition, for this particular instruction
     */

    /*@Nullable*/
    @Override
    protected String getErrorCodeForSelectPlusContent() {
        return null;     // not applicable
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        if (isExpandingText()) {
            TextImpl child = (TextImpl)iterateAxis(AxisInfo.CHILD).next();
            if (child != null) {
                List<Expression> contents = new ArrayList<>(10);
                compileContentValueTemplate(child, contents);
                Expression block = Block.makeBlock(contents);
                block.setLocation(allocateLocation());
                return block;
            } else {
                return new ValueOf(new StringLiteral(StringValue.EMPTY_STRING), disable, false);
            }
        } else {
            return new ValueOf(Literal.makeLiteral(value), disable, false);
        }
    }

}

