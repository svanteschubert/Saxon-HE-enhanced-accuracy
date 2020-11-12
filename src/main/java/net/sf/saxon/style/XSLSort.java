////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.trans.XPathException;

/**
 * An xsl:sort element in the stylesheet. <br>
 */

public class XSLSort extends XSLSortOrMergeKey {


    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {

        super.validate(decl);
        stable = typeCheck("stable", stable);
        sortKeyDefinition.setStable(stable);

    }


    @Override
    public Expression getStable() {
        return stable;
    }


}

