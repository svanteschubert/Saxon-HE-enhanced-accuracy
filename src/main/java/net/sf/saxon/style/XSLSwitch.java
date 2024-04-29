////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;

/**
 * An xsl:switch element in the stylesheet (XSLT 4.0).
 */

public class XSLSwitch extends XSLChooseOrSwitch {

    private Expression select;
    private LetExpression switchVar;

    @Override
    protected void prepareAttributes() {
        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            if (f.equals("select")) {
                select = makeExpression(value, att);
            } else {
                checkUnknownAttribute(attName);
            }
        }
        if (select == null) {
            reportAbsence("select");
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        requireXslt40Element();
        select = typeCheck("select", select);
        super.validate(decl);
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        LetExpression var = new LetExpression();
        var.setVariableQName(new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, "v" + hashCode()));
        var.setSequence(select);
        var.setRequiredType(SequenceType.SINGLE_ATOMIC);  // TODO type coercion
        switchVar = var;
        Expression choose = super.compile(exec, decl);
        switchVar.setAction(choose);
        return switchVar;
    }

    @Override
    protected void compileConditions(Compilation exec, ComponentDeclaration decl, Expression[] conditions) {
        int w = 0;
        for (NodeInfo curr : children()) {
            if (curr instanceof XSLWhen) {
                Expression values = ((XSLWhen) curr).getCondition();
                conditions[w] = new GeneralComparison20(new LocalVariableReference(switchVar), Token.EQUALS, values);
                w++;
            } else if (curr instanceof XSLOtherwise) {
                Expression otherwise = Literal.makeLiteral(BooleanValue.TRUE);
                otherwise.setRetainedStaticContext(makeRetainedStaticContext());
                conditions[w] = otherwise;
                w++;
            }
        }
    }

}

