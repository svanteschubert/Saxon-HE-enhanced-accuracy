////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.om.NodeInfo;

import java.util.function.Predicate;

/**
 * This axis cannot be requested directly in an XPath expression
 * but is used when evaluating xsl:number. It is provided because
 * taking the union of the two axes would be very inefficient
 */

final class PrecedingOrAncestorEnumeration extends TreeEnumeration {


    public PrecedingOrAncestorEnumeration(NodeImpl node, Predicate<? super NodeInfo> nodeTest) {
        super(node, nodeTest);
        advance();
    }

    @Override
    protected void step() {
        next = next.getPreviousInDocument();
    }

}

