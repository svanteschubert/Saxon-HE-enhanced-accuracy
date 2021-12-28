////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.tree.iter.AxisIterator;

import java.util.function.IntPredicate;

/**
 * This class supports both the descendant:: and descendant-or-self:: axes, which are
 * identical except for the route to the first candidate node.
 * It enumerates descendants of the specified node. This version is for use when it is known
 * that text nodes are not selected.
 * The calling code must ensure that the start node is not an attribute or namespace node.
 */

final class DescendantIteratorSansText implements AxisIterator {

    private final TinyTree tree;
    private int nextNodeNr;
    private final int startDepth;
    private final IntPredicate matcher;

    /**
     * Create an iterator over the descendant axis
     *
     * @param doc         the containing TinyTree
     * @param node        the node whose descendants are required
     * @param nodeTest    test to be satisfied by each returned node
     */

    DescendantIteratorSansText(/*@NotNull*/ TinyTree doc, /*@NotNull*/ TinyNodeImpl node, NodeTest nodeTest) {
        tree = doc;
        nextNodeNr = node.nodeNr;
        startDepth = doc.depth[nextNodeNr];
        matcher = nodeTest.getMatcher(doc);
    }

    /*@Nullable*/
    @Override
    public NodeInfo next() {
        do {
            nextNodeNr++;
            try {
                if (tree.depth[nextNodeNr] <= startDepth) {
                    nextNodeNr = -1;
                    return null;
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                // this shouldn't happen. If it does happen, it means the tree wasn't properly closed
                // during construction (there is no stopper node at the end). In this case, we'll recover
                // by returning end-of sequence
                //System.err.println("********* no stopper node **********");
                nextNodeNr = -1;
                return null;
            }
        } while (!matcher.test(nextNodeNr));

        return tree.getNode(nextNodeNr);
    }

}

