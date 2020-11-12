////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.Fork;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;

/**
 * Handler for xsl:fork elements in XSLT 3.0 stylesheet.
 */

public class XSLFork extends StyleElement {

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
     * @return false: no, it may not contain a sequence constructor
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return false;
    }


    @Override
    public void prepareAttributes() {

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            checkUnknownAttribute(attName);
        }
    }


    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        int foundGroup = 0;
        int foundSequence = 0;
        for (NodeInfo child : children()) {
            if (child instanceof XSLSequence) {
                foundSequence++;
            } else if (child instanceof XSLForEachGroup) {
                foundGroup++;
            } else if (child instanceof XSLFallback) {
                // no action
            } else {
                compileError(child.getDisplayName() + " cannot appear as a child of xsl:fork");
            }
        }
        if (foundGroup > 1) {
            compileError("xsl:fork contains more than one xsl:for-each-group instruction");
        }
        if (foundGroup > 0 && foundSequence > 0) {
            compileError("Cannot mix xsl:sequence and xsl:for-each-group within xsl:fork");
        }
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        Expression content = compileSequenceConstructor(exec, decl, true);
        if (content instanceof Block) {
            return new Fork(((Block) content).getOperanda());
        } else {
            return content;
        }
    }


}
