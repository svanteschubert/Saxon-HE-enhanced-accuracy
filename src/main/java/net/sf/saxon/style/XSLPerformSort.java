////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.sort.SortExpression;
import net.sf.saxon.expr.sort.SortKeyDefinitionList;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;


/**
 * Handler for xsl:perform-sort elements in stylesheet (XSLT 2.0). <br>
 */

public class XSLPerformSort extends StyleElement {

    /*@Nullable*/ Expression select = null;

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
     * Determine whether this type of element is allowed to contain a template-body
     *
     * @return true: yes, it may contain a template-body
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return true;
    }

    /**
     * Specify that xsl:sort is a permitted child
     */

    @Override
    protected boolean isPermittedChild(StyleElement child) {
        return (child instanceof XSLSort);
    }

    @Override
    public void prepareAttributes() {

        String selectAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String value = att.getValue();
            String f = attName.getDisplayName();
            if (f.equals("select")) {
                selectAtt = value;
                select = makeExpression(selectAtt, att);
            } else {
                checkUnknownAttribute(attName);
            }
        }

    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        checkSortComesFirst(true);

        if (select != null) {
            // if there is a select attribute, check that there are no children other than xsl:sort and xsl:fallback
            for (NodeInfo child : children()) {
                if (child instanceof XSLSort || child instanceof XSLFallback) {
                    // no action
                } else if (child.getNodeKind() == Type.TEXT && !Whitespace.isWhite(child.getStringValueCS())) {
                    // with xml:space=preserve, white space nodes may still be there
                    compileError("Within xsl:perform-sort, significant text must not appear if there is a select attribute",
                            "XTSE1040");
                } else {
                    ((StyleElement) child).compileError(
                            "Within xsl:perform-sort, child instructions are not allowed if there is a select attribute",
                            "XTSE1040");
                }
            }
        }
        select = typeCheck("select", select);
    }

    @Override
    public Expression compile(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        SortKeyDefinitionList sortKeys = makeSortKeys(compilation, decl);
        if (select != null) {
            return new SortExpression(select, sortKeys);
        } else {
            Expression body = compileSequenceConstructor(compilation, decl, true);
            if (body == null) {
                body = Literal.makeEmptySequence();
            }
            try {
                return new SortExpression(body.simplify(), sortKeys);
            } catch (XPathException e) {
                compileError(e);
                return null;
            }
        }
    }


}
