////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.value.BooleanValue;

/**
 * An xsl:choose element in the stylesheet.
 */

public class XSLChoose extends XSLChooseOrSwitch {

    @Override
    protected void compileConditions(Compilation exec, ComponentDeclaration decl, Expression[] conditions) {
        int w = 0;
        for (NodeInfo curr : children()) {
            if (curr instanceof XSLWhen) {
                conditions[w] = ((XSLWhen) curr).getCondition();
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

