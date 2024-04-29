////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.type.Type;

final class FollowingEnumeration extends TreeEnumeration {

    private final NodeImpl root;

    public FollowingEnumeration(NodeImpl node, NodeTest nodeTest) {
        super(node, nodeTest);
        root = (NodeImpl)node.getRoot();
        // skip the descendant nodes if any
        int type = node.getNodeKind();
        if (type == Type.ATTRIBUTE || type == Type.NAMESPACE) {
            nextNode = node.getParent().getNextInDocument(root);
        } else {
            do {
                nextNode = node.getNextSibling();
                if (nextNode == null) {
                    node = node.getParent();
                }
            } while (nextNode == null && node != null);
        }
        while (!conforms(nextNode)) {
            step();
        }
    }

    @Override
    protected void step() {
        nextNode = nextNode.getNextInDocument(root);
    }

}

