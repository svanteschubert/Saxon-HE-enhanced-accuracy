////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.om.*;
import net.sf.saxon.tree.iter.AxisIterator;

/**
 * Abstract class containing functionality common to xsl:break and xsl:next-iteration
 */
public abstract class XSLBreakOrContinue extends StyleElement {

    /*@Nullable*/ protected XSLIterate xslIterate = null;

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
     * Set the attribute list for the element. This is called to process the attributes (note
     * the distinction from processAttributes in the superclass).
     * Must be supplied in a subclass
     */

    @Override
    public void prepareAttributes() {
        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            checkUnknownAttribute(attName);
        }
    }

    /**
     * Test that this xsl:next-iteration or xsl:break instruction appears in a valid position
     */

    protected void validatePosition()  {
        NodeInfo inst = this;
        boolean isLast = true;
        while (true) {
            if (!(inst instanceof XSLWhen)) {
                AxisIterator sibs = inst.iterateAxis(AxisInfo.FOLLOWING_SIBLING);
                while (true) {
                    NodeInfo sib = sibs.next();
                    if (sib == null) {
                        break;
                    }
                    if (sib instanceof XSLFallback || sib instanceof XSLCatch) {
                        continue;
                    }
                    isLast = false;
                }
            }
            inst = inst.getParent();
            if (inst instanceof XSLIterate) {
                xslIterate = (XSLIterate) inst;
                break;
            } else if (inst instanceof XSLTry || inst instanceof XSLCatch) {
                //compilable = false;
            } else if (inst instanceof XSLWhen || inst instanceof XSLOtherwise
                    || inst instanceof XSLIf || inst instanceof XSLChoose) {
                // continue;
            } else if (inst == null) {
                compileError(getDisplayName() + " is not allowed at outermost level", "XTSE3120");//XTSE0010
                return;
            } else {
                compileError(getDisplayName() + " is not allowed within " + inst.getDisplayName(), "XTSE3120");//XTSE0010
                return;
            }
        }
        if (!isLast) {
            compileError(getDisplayName() + " must be the last instruction in the xsl:iterate loop", "XTSE3120");
        }
    }
}
