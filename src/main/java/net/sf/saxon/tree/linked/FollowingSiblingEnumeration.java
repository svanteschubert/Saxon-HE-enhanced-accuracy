////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.om.NodeInfo;

import java.util.function.Predicate;

final class FollowingSiblingEnumeration extends TreeEnumeration {

    public FollowingSiblingEnumeration(NodeImpl node, Predicate<? super NodeInfo> nodeTest) {
        super(node, nodeTest);
        advance();
    }

    @Override
    protected void step() {
        next = next.getNextSibling();
    }

}

