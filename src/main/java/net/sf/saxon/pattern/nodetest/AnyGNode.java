// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern.nodetest;

import net.sf.saxon.om.GNode;
import net.sf.saxon.tree.tiny.NodeVectorTree;

import java.util.function.IntPredicate;

public class AnyGNode implements NodePredicate, NodeVectorMatchMaker {

    private AnyGNode(){};

    public static AnyGNode TEST = new AnyGNode();

    @Override
    public boolean test(GNode node) {
        return true;
    }

    /**
     * Get a matching function that can be used to test whether numbered nodes in a TinyTree
     * or DominoTree satisfy the node test. (Calling this matcher must give the same result
     * as calling <code>matchesNode(tree.getNode(nodeNr))</code>, but it may well be faster).
     *
     * @param tree the tree against which the returned function will operate
     * @return an IntPredicate; the matches() method of this predicate takes a node number
     * as input, and returns true if and only if the node identified by this node number
     * matches the node predicate.
     */
    @Override
    public IntPredicate getMatcher(NodeVectorTree tree) {
        return nr -> true;
    }
}

