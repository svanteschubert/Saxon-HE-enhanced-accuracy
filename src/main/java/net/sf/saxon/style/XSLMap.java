////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.instruct.SequenceInstr;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.map.HashTrieMap;
import net.sf.saxon.ma.map.MapFunctionSet;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

/**
 * Handler for xsl:map instructions in an XSLT 3.0 stylesheet. <br>
 */

public class XSLMap extends StyleElement {

    private Expression select = null;
    private Expression onDuplicates = null;

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
        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            if (attName.hasURI(NamespaceConstant.SAXON)) {
                if (isExtensionAttributeAllowed(attName.getDisplayName())) {
                    if (attName.getLocalPart().equals("on-duplicates")) {
                        onDuplicates = makeExpression(value, att);
                    }
                }
            } else {
                checkUnknownAttribute(attName);
            }
        }
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        select = compileSequenceConstructor(exec, decl, false);
        select = select.simplify();
        // Custom type-checking; the checking performed by map:merge() gives poor diagnostics
        TypeChecker tc = getConfiguration().getTypeChecker(false);
        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.MISC, "xsl:map sequence constructor", 0);
        role.setErrorCode("XTTE3375");
        select = tc.staticTypeCheck(
                select,
                SequenceType.makeSequenceType(MapType.ANY_MAP_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE),
                role, makeExpressionVisitor());

        Expression optionsExp;

        if (onDuplicates != null) {
            optionsExp = MapFunctionSet.getInstance().makeFunction("entry", 2).makeFunctionCall(
                    Literal.makeLiteral(new QNameValue("", NamespaceConstant.SAXON, "on-duplicates")),
                    onDuplicates);
        } else {
            HashTrieMap options = new HashTrieMap();
            options.initialPut(new StringValue("duplicates"), new StringValue("reject"));
            options.initialPut(new QNameValue("", NamespaceConstant.SAXON, "duplicates-error-code"),
                               new StringValue("XTDE3365"));
            optionsExp = Literal.makeLiteral(options, select);
        }

        Expression exp = MapFunctionSet.getInstance().makeFunction("merge", 2)
                .makeFunctionCall(select, optionsExp);
        if (getConfiguration().getBooleanProperty(Feature.STRICT_STREAMABILITY)) {
            exp = new SequenceInstr(exp);
        }
        return exp;
    }

}
