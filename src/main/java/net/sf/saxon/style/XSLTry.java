////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.TryCatch;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.pattern.QNameTest;
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.List;


/**
 * Handler for xsl:try elements in stylesheet.
 * The xsl:try element contains a sequence constructor or a select expression,
 * which defines the expression to be evaluated, and it may contain one or more
 * xsl:catch elements, which define the value to be returned in the event of
 * dynamic errors.
 */

public class XSLTry extends StyleElement {

    private Expression select;
    private boolean rollbackOutput = true;
    private List<QNameTest> catchTests = new ArrayList<>();
    private List<Expression> catchExprs = new ArrayList<>();

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
     * @return true: yes, it may contain a sequence constructor
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return true;
    }

    @Override
    public void prepareAttributes() {

        String selectAtt = null;
        String rollbackOutputAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            switch (f) {
                case "select":
                    selectAtt = value;
                    select = makeExpression(selectAtt, att);
                    break;
                case "rollback-output":
                    rollbackOutputAtt = value;
                    break;
                default:
                    checkUnknownAttribute(attName);
                    break;
            }
        }

        if (rollbackOutputAtt != null) {
            rollbackOutput = processBooleanAttribute("rollback-output", rollbackOutputAtt);
        }
    }

    @Override
    protected boolean isPermittedChild(StyleElement child) {
        return child instanceof XSLCatch;
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        select = typeCheck("select", select);
        boolean foundCatch = false;
        for (NodeInfo kid : children()) {
            if (kid instanceof XSLCatch) {
                foundCatch = true;
            } else if (kid instanceof XSLFallback) {
                // no action;
            } else {
                if (foundCatch) {
                    compileError("xsl:catch elements must come after all other children of xsl:try (excepting xsl:fallback)", "XTSE0010");
                }
                if (select != null) {
                    compileError("An " + getDisplayName() + " element with a select attribute must be empty", "XTSE3140");
                }
            }
        }
        if (!foundCatch) {
            compileError("xsl:try must have at least one xsl:catch child element", "XTSE0010");
        }
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        Expression content = compileSequenceConstructor(exec, decl, true);
        if (select == null) {
            select = content;
        }
        TryCatch expr = new TryCatch(select);
        for (int i = 0; i < catchTests.size(); i++) {
            expr.addCatchExpression(catchTests.get(i), catchExprs.get(i));
        }
        expr.setRollbackOutput(rollbackOutput);
        return expr;
    }

    public void addCatchClause(QNameTest nameTest, Expression catchExpr) {
        catchTests.add(nameTest);
        catchExprs.add(catchExpr);
    }

}
