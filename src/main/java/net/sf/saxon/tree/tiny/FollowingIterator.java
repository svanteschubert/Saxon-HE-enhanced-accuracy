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
import net.sf.saxon.type.Type;

import java.util.function.IntPredicate;

/**
 * Iterate over the following axis starting at a given node.
 * The start node must not be a namespace or attribute node.
 */

final class FollowingIterator implements AxisIterator {

    private TinyTree tree;
    private TinyNodeImpl startNode;
    private NodeInfo current;
    private NodeTest test;
    private boolean includeDescendants;
    int position = 0;
    private final IntPredicate matcher;
    private NodeInfo pending;

    /**
     * Create an iterator over the following axis
     *
     * @param doc                the containing TinyTree
     * @param node               the start node. If the actual start was an attribute or namespace node, this will
     *                           be the parent element of that attribute or namespace
     * @param nodeTest           condition that all the returned nodes must satisfy
     * @param includeDescendants true if descendants of the start node are to be included. This will
     *                           be false if the actual start was an element node, true if it was an attribute or namespace node
     *                           (since the children of their parent follow the attribute or namespace in document order).
     */

    public FollowingIterator(TinyTree doc, TinyNodeImpl node,
                             NodeTest nodeTest, boolean includeDescendants) {
        tree = doc;
        test = nodeTest;
        startNode = node;
        this.includeDescendants = includeDescendants;
        this.matcher = nodeTest.getMatcher(doc);
    }

    /*@Nullable*/
    @Override
    public NodeInfo next() {
        if (pending != null) {
            NodeInfo p = pending;
            pending = null;
            return p;
        }
        int nodeNr;
        if (position <= 0) {
            if (position < 0) {
                // already at end
                return null;
            }
            // first time call
            nodeNr = startNode.nodeNr;

            // skip the descendant nodes if any
            if (includeDescendants) {
                nodeNr++;
            } else {
                while (true) {
                    int nextSib = tree.next[nodeNr];
                    if (nextSib > nodeNr) {
                        nodeNr = nextSib;
                        break;
                    } else if (tree.depth[nextSib] == 0) {
                        current = null;
                        position = -1;
                        return null;
                    } else {
                        nodeNr = nextSib;
                    }
                }
            }
        } else {
            assert current != null;
            TinyNodeImpl here;
            if (current instanceof TinyTextualElement.TinyTextualElementText) {
                here = (TinyNodeImpl) current.getParent();
            } else {
                here = (TinyNodeImpl) current;
            }
            nodeNr = here.nodeNr + 1;
        }

        while (true) {
            if (tree.depth[nodeNr] == 0) {
                current = null;
                position = -1;
                return null;
            }
            if (tree.nodeKind[nodeNr] == Type.TEXTUAL_ELEMENT) {
                TinyTextualElement e = (TinyTextualElement)tree.getNode(nodeNr);
                NodeInfo t = e.getTextNode();
                if (matcher.test(nodeNr)) {
                    if (test.test(t)) {
                        pending = t;
                    }
                    position++;
                    return current = tree.getNode(nodeNr);
                } else if (test.test(t)) {
                    position++;
                    return current = t;
                }
            } else if (matcher.test(nodeNr)) {
                position++;
                current = tree.getNode(nodeNr);
                return current;
            }
            nodeNr++;
        }
    }

}

