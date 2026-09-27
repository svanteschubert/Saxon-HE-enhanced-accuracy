////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.pattern.nodetest.NodePredicate;

final class AncestorEnumeration extends TreeEnumeration {

    public AncestorEnumeration(NodeImpl node,
                               NodePredicate nodeTest, boolean includeSelf) {
        super(node, nodeTest);
        if (!includeSelf || !conforms(node)) {
            advance();
        }
    }

    @Override
    protected void step() {
        nextNode = nextNode.getParent();
    }

}

