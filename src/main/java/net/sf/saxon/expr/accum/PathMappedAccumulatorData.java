////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.accum;

import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import net.sf.saxon.tree.util.Navigator;

import java.util.Stack;

/**
 * Accumulator data for a tree that is obtained by mapping the nodes in this tree
 * to nodes in some other tree (specifically, the subtree from which this tree was
 * originally copied with copy-accumulators=yes) and getting the accumulator value
 * from the corresponding node in the other tree.
 */
public class PathMappedAccumulatorData implements IAccumulatorData {

    private IAccumulatorData originalData;
    private NodeInfo origin;

    PathMappedAccumulatorData(IAccumulatorData original, NodeInfo origin) {
        this.originalData = original;
        this.origin = origin;
    }

    @Override
    public Accumulator getAccumulator() {
        return null;
    }

    @Override
    public Sequence getValue(NodeInfo node, boolean postDescent) throws XPathException {
        return originalData.getValue(map(node), postDescent);
    }

    private NodeInfo map(NodeInfo node) {
        if (origin instanceof TinyNodeImpl && node instanceof TinyNodeImpl) {
            // Fast algorithm where both trees are TinyTrees: map the node numbers
            int nodeNrInSubtree = ((TinyNodeImpl)node).getNodeNumber();
            return ((TinyNodeImpl)origin).getTree().getNode(nodeNrInSubtree + ((TinyNodeImpl)origin).getNodeNumber());
        } else {
            // General algorithm: get the node with a corresponding path in terms of sibling position.
            Stack<Integer> path = new Stack<Integer>();
            NodeInfo ancestor = node;
            while (ancestor != null) {
                path.push(Navigator.getSiblingPosition(ancestor, AnyNodeTest.getInstance(), Integer.MAX_VALUE));
                ancestor = ancestor.getParent();
            }
            NodeInfo target = origin;
            while (!path.isEmpty()) {
                int pos = path.pop();
                AxisIterator kids = target.iterateAxis(AxisInfo.CHILD);
                while (pos-- > 0) {
                    target = kids.next();
                    assert (target != null);
                }
            }
            return target;
        }
    }


}

