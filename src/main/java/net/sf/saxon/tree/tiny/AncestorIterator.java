////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.nodetest.NodePredicate;

/**
 * This class iterates the ancestor:: or ancestor-or-self:: axes,
 * starting at a given TinyTree node. The start node will never be the root.
 */

final public class AncestorIterator implements SequenceIterator {

    private NodeInfo current;
    private final NodePredicate test;

    public AncestorIterator(NodeInfo node, NodePredicate nodeTest) {
        test = nodeTest;
        current = node;
    }

    /*@Nullable*/
    @Override
    public NodeInfo next() {
        if (current == null) {
            return null;
        }
        NodeInfo node = (NodeInfo)current.getParent();
        while (node != null && test != null && !test.test(node)) {
            node = (NodeInfo)node.getParent();
        }
        return current = node;
    }

}

