////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.type.Type;

/**
 * This class is a fast path iterator for the child axis in the TinyTree, where the axis specifies
 * an explicit name test for the required element nodes.
 */

final class NamedElementChildIterator implements SequenceIterator, LookaheadIterator, AtomizedValueIterator {

    private final TinyTree tree;
    private final int fingerprint;
    private final TinyNodeImpl startNode;
    private int nextNodeNr;
    private boolean needToAdvance = false;

    /**
     * Return an iterator over children or siblings of the context node
     *
     * @param tree        The TinyTree containing the context node
     * @param node        The context node, the start point for the iteration
     * @param fingerprint The fingerprint of the required element children
     */

    NamedElementChildIterator(TinyTree tree, TinyNodeImpl node, int fingerprint) {
        this.tree = tree;
        this.fingerprint = fingerprint;
        this.startNode = node;

        // move to first child
        // ASSERT: we don't invoke this code unless the node has children
        nextNodeNr = node.nodeNr + 1;

        // check if this matches the conditions
        if (((tree.nodeKind[nextNodeNr] & 0xf) != Type.ELEMENT)
                || (tree.nameCode[nextNodeNr] & NamePool.FP_MASK) != fingerprint) {
            needToAdvance = true;
        }
    }

    /**
     * Return the next node in the sequence
     *
     * @return the next node, or null if the end of the sequence is reached
     */

    /*@Nullable*/
    @Override
    public NodeInfo next() {
        if (needToAdvance) {
            final int thisNode = nextNodeNr;
            do {
                nextNodeNr = tree.next[nextNodeNr];
                if (nextNodeNr < thisNode) {    // indicates we've got to the last sibling
                    nextNodeNr = -1;
                    needToAdvance = false;
                    return null;
                }
            } while (((tree.nameCode[nextNodeNr] & NamePool.FP_MASK) != fingerprint)
                    || ((tree.nodeKind[nextNodeNr] & 0xf) != Type.ELEMENT));
        } else if (nextNodeNr == -1) {
            return null;
        }
        needToAdvance = true;
        TinyNodeImpl nextNode = tree.getNode(nextNodeNr);
        nextNode.setParentNode(startNode);
        return nextNode;
    }

    /**
     * Deliver the atomic value that is next in the atomized result
     *
     * @return the next atomic value
     * @throws XPathException
     *          if a failure occurs reading or atomizing the next value
     */
    @Override
    public AtomicSequence nextAtomizedValue() throws XPathException {
        if (needToAdvance) {
            final int thisNode = nextNodeNr;
            do {
                nextNodeNr = tree.next[nextNodeNr];
                if (nextNodeNr < thisNode) {    // indicates we've got to the last sibling
                    nextNodeNr = -1;
                    needToAdvance = false;
                    return null;
                }
            } while (((tree.nameCode[nextNodeNr] & 0xfffff) != fingerprint) || (tree.nodeKind[nextNodeNr] & 0xf) != Type.ELEMENT);
        } else if (nextNodeNr == -1) {
            return null;
        }
        needToAdvance = true;
        return tree.getTypedValueOfElement(nextNodeNr);

    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    /**
     * Test whether there are any more nodes to come. This method is used only when testing whether the
     * current item is the last in the sequence. It's not especially efficient, but is more efficient than
     * the alternative strategy which involves counting how many nodes there are in the sequence.
     *
     * @return true if there are more items in the sequence
     */

    @Override
    public boolean hasNext() {
        int n = nextNodeNr;
        if (needToAdvance) {
            final int thisNode = n;
            do {
                n = tree.next[n];
                if (n < thisNode) {
                    return false;
                }
            } while ((tree.nodeKind[n] & 0xf) != Type.ELEMENT || (tree.nameCode[n] & 0xfffff) != fingerprint);
            return true;
        } else {
            return n != -1;
        }
    }

}

