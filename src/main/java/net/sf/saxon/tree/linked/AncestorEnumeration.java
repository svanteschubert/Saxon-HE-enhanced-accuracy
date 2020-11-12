////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeTest;

import java.util.function.Predicate;

final class AncestorEnumeration extends TreeEnumeration {

    private boolean includeSelf;

    public AncestorEnumeration(NodeImpl node, Predicate<? super NodeInfo> nodeTest, boolean includeSelf) {
        super(node, nodeTest);
        this.includeSelf = includeSelf;
        if (!includeSelf || !conforms(node)) {
            advance();
        }
    }

    @Override
    protected void step() {
        next = next.getParent();
    }

}

