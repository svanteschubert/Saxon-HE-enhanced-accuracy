////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;


/**
 * Handler for xsl:if elements in stylesheet. <br>
 * The xsl:if element has a mandatory attribute test, a boolean expression.
 * The content is output if the test condition is true.
 */

public class XSLIf extends StyleElement {

    private Expression test;
    private Expression thenExp;
    private Expression elseExp;

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

    @Override
    public void prepareAttributes() {
        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            switch (f) {
                case "test":
                    test = makeExpression(att.getValue(), att);
                    break;
                case "then":
                    // Saxon extension
                    requireSyntaxExtensions("then");
                    thenExp = makeExpression(att.getValue(), att);
                    break;
                case "else":
                    // Saxon extension
                    requireSyntaxExtensions("else");
                    elseExp = makeExpression(att.getValue(), att);
                    break;
                default:
                    checkUnknownAttribute(attName);
                    break;
            }
        }

        if (test == null) {
            reportAbsence("test");
        }
    }

    /**
     * Process all the attributes, for an element where the only permitted attribute is "test"
     *
     * @param se the containing element
     * @return the expression represented by the test attribute, or null if the attribute is absent
     */

    public static Expression prepareTestAttribute(StyleElement se) {

        AttributeInfo testAtt = null;

        for (AttributeInfo att : se.attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            if (f.equals("test")) {
                testAtt = att;
            } else {
                se.checkUnknownAttribute(attName);
            }
        }

        if (testAtt == null) {
            return null;
        } else {
            return se.makeExpression(testAtt.getValue(), testAtt);
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        test = typeCheck("test", test);
        if (thenExp != null && hasChildNodes()) {
            compileError("xsl:if element must be empty if @then is present", "XTSE0010");
        }
    }

    /**
     * Mark tail-recursive calls on stylesheet functions. For most instructions, this does nothing.
     */

    @Override
    public boolean markTailCalls() {
        StyleElement last = getLastChildInstruction();
        return last != null && last.markTailCalls();
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        if (test instanceof Literal) {
            GroundedValue testVal = ((Literal) test).getValue();
            // condition known statically, so we only need compile the code if true.
            // This can happen with expressions such as test="function-available('abc')".
            try {
                if (testVal.effectiveBooleanValue()) {
                    return compileSequenceConstructor(exec, decl, true);
                } else {
                    return null;
                }
            } catch (XPathException err) {
                // fall through to non-optimizing case
            }
        }

        Expression action = compileSequenceConstructor(exec, decl, true);
        if (action == null) {
            return null;
        }

        Expression[] conditions;
        Expression[] actions;

        if (elseExp == null) {
            conditions = new Expression[]{test};
            actions = new Expression[]{action};
        } else {
            conditions = new Expression[]{test, Literal.makeLiteral(BooleanValue.TRUE)};
            actions = new Expression[]{action, elseExp};
        }

        Choose choose = new Choose(conditions, actions);
        choose.setInstruction(true);
        return choose;
    }

    @Override
    public Expression compileSequenceConstructor(Compilation compilation, ComponentDeclaration decl,
                                                 boolean includeParams) throws XPathException {
        if (thenExp == null) {
            return super.compileSequenceConstructor(compilation, decl, includeParams);
        } else {
            return thenExp;
        }
    }


}
