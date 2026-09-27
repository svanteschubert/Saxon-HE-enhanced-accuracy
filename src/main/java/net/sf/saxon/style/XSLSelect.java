////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.util.function.Supplier;


/**
 * An xsl:select element in the stylesheet (XSLT 4.0).
 */

public class XSLSelect extends StyleElement implements AutoDocumentInhibitor {

    private Expression select;
    private SequenceType requiredType;



    // TODO: xsl:fallback child not implemented

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
     * Get the type of the items returned by this instruction
     *
     * @return the static item type
     */
    @Override
    public ItemType getItemType() {
        return AnyItemType.getInstance();
    }

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return in XSLT 2.0, false. In XSLT 3.0 true: yes, it may contain a sequence constructor
     */

    @Override
    protected boolean mayContainSequenceConstructor() {
        return false;
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
            if (f.equals("as")) {
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
        UnicodeString content = null;
        for (NodeInfo n : children()) {
            if (n.getNodeKind() == Type.ELEMENT) {
                if (!(n instanceof XSLFallback)) {
                    compileError("Invalid child of xsl:select - " + n.getDisplayName());
                } else {
                    if (content != null) {
                        compileError("Within xsl:select, text nodes cannot precede xsl:fallback");
                    }
                }
            } else if (n.getNodeKind() == Type.TEXT) {
                UnicodeString s = n.getUnicodeStringValue();
                if (!Whitespace.isAllWhite(s)) {
                    if (content != null) {
                        compileError("xsl:select has multiple significant text node children");
                    } else {
                        content = s;
                    }
                }
            }
        }
        if (content == null) {
            select = Literal.makeEmptySequence();
        } else {
            select = makeExpression(content.toString(), null);
        }
        typeCheck("select", select);
    }

    /*@Nullable*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        if (requiredType != null) {
            Supplier<RoleDiagnostic> role = () ->
                    new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:select/#content", 0, "XPTY0004");
            select = getConfiguration().getTypeChecker(false).staticTypeCheck(
                    select, requiredType, role, makeExpressionVisitor());
        }
        return select;
    }

}

