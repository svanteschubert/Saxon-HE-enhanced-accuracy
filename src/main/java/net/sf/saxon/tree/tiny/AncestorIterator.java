////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.tree.iter.AxisIterator;

import java.util.function.Predicate;

/**
 * This class enumerates the ancestor:: or ancestor-or-self:: axes,
 * starting at a given node. The start node will never be the root.
 */

final public class AncestorIterator implements AxisIterator {

    private NodeInfo startNode;
    private NodeInfo current;
    private Predicate<? super NodeInfo> test;

    public AncestorIterator(NodeInfo node, Predicate<? super NodeInfo> nodeTest) {
        test = nodeTest;
        startNode = node;
        current = startNode;
    }

    /*@Nullable*/
    @Override
    public NodeInfo next() {
        if (current == null) {
            return null;
        }
        NodeInfo node = current.getParent();
        while (node != null && !test.test(node)) {
            node = node.getParent();
        }
        return current = node;
    }

}

