////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.type.Type;

import java.util.function.Predicate;

final class FollowingEnumeration extends TreeEnumeration {

    private NodeImpl root;

    public FollowingEnumeration(NodeImpl node, Predicate<? super NodeInfo> nodeTest) {
        super(node, nodeTest);
        root = (NodeImpl)node.getRoot();
        // skip the descendant nodes if any
        int type = node.getNodeKind();
        if (type == Type.ATTRIBUTE || type == Type.NAMESPACE) {
            next = node.getParent().getNextInDocument(root);
        } else {
            do {
                next = node.getNextSibling();
                if (next == null) {
                    node = node.getParent();
                }
            } while (next == null && node != null);
        }
        while (!conforms(next)) {
            step();
        }
    }

    @Override
    protected void step() {
        next = next.getNextInDocument(root);
    }

}

