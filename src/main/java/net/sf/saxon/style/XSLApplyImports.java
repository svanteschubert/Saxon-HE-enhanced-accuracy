////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.ApplyImports;
import net.sf.saxon.expr.instruct.WithParam;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.NodeImpl;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;

/**
 * An xsl:apply-imports element in the stylesheet
 */

public class XSLApplyImports extends StyleElement {


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
    public void prepareAttributes() {

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            checkUnknownAttribute(attName);
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        //checkWithinTemplate();
        for (NodeInfo child : children()) {
            if (child instanceof XSLWithParam) {
                // OK;
            } else if (child.getNodeKind() == Type.TEXT) {
                // with xml:space=preserve, white space nodes may still be there
                if (!Whitespace.isWhite(child.getStringValueCS())) {
                    compileError("No character data is allowed within xsl:apply-imports", "XTSE0010");
                }
            } else {
                compileError("Child element " + child.getDisplayName() +
                        " is not allowed as a child of xsl:apply-imports", "XTSE0010");
            }
        }

        NodeImpl parent = getParent();
        while (parent != null) {
            if (parent instanceof XSLOverride) {
                compileError("xsl:apply-imports cannot be used in a template rule declared within xsl:override", "XTSE3460");
            }
            parent = parent.getParent();
        }
    }

    /*@NotNull*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        ApplyImports inst = new ApplyImports();
        WithParam[] nonTunnels = getWithParamInstructions(inst, exec, decl, false);
        WithParam[] tunnels = getWithParamInstructions(inst, exec, decl, true);
        inst.setActualParams(nonTunnels);
        inst.setTunnelParams(tunnels);
        return inst;
    }

}

