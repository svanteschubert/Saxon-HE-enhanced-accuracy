////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.OnEmptyExpr;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.trans.XPathException;


/**
 * An xsl:on-empty element in the stylesheet. The rules are identical to xsl:sequence.
 */

public final class XSLOnEmpty extends XSLSequence {

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        super.validate(decl);
        iterateAxis(AxisInfo.FOLLOWING_SIBLING).forEachOrFail(next -> {
            if (!(next instanceof XSLFallback || next instanceof XSLCatch)) {
                compileError("xsl:on-empty must be the last instruction in the sequence constructor");
            }
        });
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        Expression e = super.compile(exec, decl);
        return new OnEmptyExpr(e);
    }
}
