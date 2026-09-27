////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.SequenceInstr;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

import java.util.function.Supplier;


/**
 * An xsl:sequence element in the stylesheet.
 */

public class XSLSequence extends StyleElement {

    private Expression select;
    private SequenceType requiredType;

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
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return in XSLT 2.0, false. In XSLT 3.0 true: yes, it may contain a sequence constructor
     */

    @Override
    protected boolean mayContainSequenceConstructor() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain an xsl:fallback
     * instruction
     */

    @Override
    protected boolean mayContainFallback() {
        return true;
    }

    public Expression getSelectExpression() {
        return select;
    }

    public void setSelectExpression(Expression select) {
        this.select = select;
    }


    @Override
    protected void prepareAttributes() {

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String value = att.getValue();
            String f = attName.getDisplayName();
            if (f.equals("select")) {
                select = makeExpression(value, att);
            } else if (f.equals("as")) {
                if (requireXslt40Attribute("as")) {
                    try {
                        requiredType = makeSequenceType(value);
                    } catch (XPathException e) {
                        compileErrorInAttribute(e, "as");
                    }
                }
            } else {
                checkUnknownAttribute(attName);
            }
        }

    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        checkSelectXorContent(true);
        select = typeCheck("select", select);
    }

    /*@Nullable*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        String container = select == null ? "xsl:sequence/#content" : "xsl:sequence/select";
        if (select == null) {
            select = compileSequenceConstructor(exec, decl, false);
        }
        if (requiredType != null) {
            Supplier<RoleDiagnostic> role = () ->
                    new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, container, 0, "XPTY0004");
            select = getConfiguration().getTypeChecker(false).staticTypeCheck(
                    select, requiredType, role, makeExpressionVisitor());
        }
        if (getConfiguration().getBooleanProperty(Feature.STRICT_STREAMABILITY)) {
            select = new SequenceInstr(select);
        }
        return select;
    }

}

