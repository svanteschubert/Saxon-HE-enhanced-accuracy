////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;

/**
 * An xsl:choose or xsl:switch element in the stylesheet.
 */

public abstract class XSLChooseOrSwitch extends StyleElement {

    private StyleElement otherwise;
    private int numberOfWhens = 0;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    @Override
    public boolean isInstruction() {
        return true;
    }


    @Override
    protected void prepareAttributes() {
        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            checkUnknownAttribute(attName);
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        XSLFallback fallback = null;
        for (NodeInfo curr : children()) {
            if (curr instanceof XSLWhen) {
                if (otherwise != null) {
                    otherwise.compileError("xsl:otherwise must come last", "XTSE0010");
                } else if (fallback != null) {
                    fallback.compileError("xsl:fallback must come last", "XTSE0010");
                }
                numberOfWhens++;
            } else if (curr instanceof XSLOtherwise) {
                if (otherwise != null) {
                    ((XSLOtherwise) curr).compileError("Only one xsl:otherwise is allowed in an " + getDisplayName(), "XTSE0010");
                } else if (fallback != null) {
                    fallback.compileError("xsl:fallback must come last", "XTSE0010");
                } else {
                    otherwise = (StyleElement) curr;
                }
            } else if (curr instanceof XSLFallback && this instanceof XSLSwitch) {
                fallback = (XSLFallback) curr;
            } else if (curr instanceof StyleElement) {
                ((StyleElement) curr).compileError("Only xsl:when and xsl:otherwise are allowed here", "XTSE0010");
            } else {
                compileError("Only xsl:when and xsl:otherwise are allowed within " + getDisplayName(), "XTSE0010");
            }
        }

        if (numberOfWhens == 0) {
            compileError(getDisplayName() + " must contain at least one xsl:when", "XTSE0010");
        }
    }

    /**
     * Mark tail-recursive calls on templates and functions.
     */

    @Override
    protected boolean markTailCalls() {
        boolean found = false;
        for (NodeInfo curr : children(StyleElement.class::isInstance)) {
            found |= ((StyleElement) curr).markTailCalls();
        }
        return found;
    }


    /*@Nullable*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {

        int entries = numberOfWhens + (otherwise == null ? 0 : 1);
        Expression[] conditions = new Expression[entries];
        Expression[] actions = new Expression[entries];

        compileActions(exec, decl, actions);
        compileConditions(exec, decl, conditions);

        Choose choose = new Choose(conditions, actions);
        choose.setInstruction(true);
        choose.setLocation(saveLocation());
        return choose;

    }

    protected abstract void compileConditions(Compilation exec, ComponentDeclaration decl, Expression[] conditions);



    protected void compileActions(Compilation exec, ComponentDeclaration decl, Expression[] actions) throws XPathException {
        int w = 0;
        for (NodeInfo curr : children()) {
            if (curr instanceof XSLWhen || curr instanceof XSLOtherwise) {
                Expression b = ((StyleElement) curr).compileSequenceConstructor(exec, decl, true);
                if (b == null) {
                    b = Literal.makeEmptySequence();
                    b.setRetainedStaticContext(makeRetainedStaticContext());
                }
                try {
                    b = b.simplify();
                    actions[w] = b;
                } catch (XPathException e) {
                    compileError(e);
                }

                setInstructionLocation((StyleElement) curr, actions[w]);
                w++;
            }
        }

    }

}

