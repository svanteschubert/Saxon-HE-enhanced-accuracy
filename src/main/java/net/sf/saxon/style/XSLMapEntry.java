////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.SequenceInstr;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.ma.map.MapFunctionSet;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

/**
 * Handler for xsl:map-entry instructions in an XSLT 3.0 stylesheet. <br>
 */

public class XSLMapEntry extends StyleElement {

    Expression key = null;
    Expression select = null;

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
     * Determine the type of item returned by this instruction (only relevant if
     * it is an instruction).
     *
     * @return the item type returned
     */

    protected ItemType getReturnedItemType() {
        return MapType.ANY_MAP_TYPE;
    }

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return true: yes, it may contain a sequence constructor
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return true;
    }

    @Override
    public void prepareAttributes() {

        String keyAtt = null;
        String selectAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            if (f.equals("key")) {
                keyAtt = value;
                key = makeExpression(keyAtt, att);
            } else if (f.equals("select")) {
                selectAtt = value;
                select = makeExpression(selectAtt, att);
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (keyAtt == null) {
            reportAbsence("key");
        }

    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        key = typeCheck("key", key);
        select = typeCheck("select", select);
        if (select != null) {
            for (NodeInfo kid : children()) {
                if (!(kid instanceof XSLFallback)) {
                    compileError("An xsl:map-entry element with a select attribute must be empty", "XTSE3280");
                    return;
                }
            }
        }
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        if (select == null) {
            select = compileSequenceConstructor(exec, decl, false);
            select = select.simplify();
        }
        Expression exp = MapFunctionSet.getInstance().makeFunction("entry", 2).makeFunctionCall(key, select);
        if (getConfiguration().getBooleanProperty(Feature.STRICT_STREAMABILITY)) {
            exp = new SequenceInstr(exp);
        }
        return exp;
    }


}
