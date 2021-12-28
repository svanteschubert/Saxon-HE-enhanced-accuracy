////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.AtomizedValueIterator;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.UntypedAtomicValue;
import net.sf.saxon.z.IntSetPredicate;

import java.util.EnumSet;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * This class supports both the child:: and following-sibling:: axes, which are
 * identical except for the route to the first candidate node.
 * It enumerates either the children or the following siblings of the specified node.
 * In the case of children, the specified node must always
 * be a node that has children: to ensure this, construct the iterator
 * using NodeInfo#iterateAxis()
 */

final class SiblingIterator implements AxisIterator, LookaheadIterator, AtomizedValueIterator {

    // NOTE: have experimented with a dedicated iterator for the child axis matching against
    // elements only, by fingerprint - no measurable improvement obtained.

    private TinyTree tree;
    private int nextNodeNr;
    /*@Nullable*/ private Predicate<? super NodeInfo> test;
    private TinyNodeImpl startNode;
    private TinyNodeImpl parentNode;
    private boolean getChildren;
    private boolean needToAdvance = false;
    private final IntPredicate matcher;

    /**
     * Return an enumeration over children or siblings of the context node
     *
     * @param tree        The TinyTree containing the context node
     * @param node        The context node, the start point for the iteration
     * @param nodeTest    Test that the selected nodes must satisfy, or null indicating
     *                    that all nodes are selected
     * @param getChildren True if children of the context node are to be returned, false
     *                    if following siblings are required
     */

    SiblingIterator(/*@NotNull*/ TinyTree tree, /*@NotNull*/ TinyNodeImpl node,
                                 Predicate<? super NodeInfo> nodeTest, boolean getChildren) {
        this.tree = tree;
        test = nodeTest;
        matcher = nodeTest instanceof NodeTest ? ((NodeTest)nodeTest).getMatcher(tree): IntSetPredicate.ALWAYS_TRUE;
        startNode = node;
        this.getChildren = getChildren;
        if (getChildren) {          // child:: axis
            parentNode = node;
            // move to first child
            // ASSERT: we don't invoke this code unless the node has children
            nextNodeNr = node.nodeNr + 1;

        } else {                    // following-sibling:: axis
            parentNode = (TinyNodeImpl) node.getParent();
            if (parentNode == null) {
                nextNodeNr = -1;
            } else {
                // move to next sibling
                nextNodeNr = tree.next[node.nodeNr];
                while (tree.nodeKind[nextNodeNr] == Type.PARENT_POINTER) {
                    // skip dummy nodes
                    nextNodeNr = tree.next[nextNodeNr];
                }
                if (nextNodeNr < node.nodeNr) {
                    // if "next" pointer goes backwards, it's really an owner pointer from the last sibling
                    nextNodeNr = -1;
                }
            }
        }

        // check if this matches the conditions
        if (nextNodeNr >= 0 && nodeTest != null) {
            if (!matcher.test(nextNodeNr)) {
                needToAdvance = true;
            }
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
            final int[] tNext = tree.next;
            final Predicate<? super NodeInfo> nTest = test;
            if (nTest == null) {
                do {
                    nextNodeNr = tNext[nextNodeNr];
                } while (tree.nodeKind[nextNodeNr] == Type.PARENT_POINTER);
            } else {
                do {
                    nextNodeNr = tNext[nextNodeNr];
                } while (nextNodeNr >= thisNode && !matcher.test(nextNodeNr));
            }

            if (nextNodeNr < thisNode) {    // indicates we've got to the last sibling
                nextNodeNr = -1;
                needToAdvance = false;
                return null;
            }
        }

        if (nextNodeNr == -1) {
            return null;
        }
        needToAdvance = true;
        TinyNodeImpl nextNode = tree.getNode(nextNodeNr);
        nextNode.setParentNode(parentNode);
        return nextNode;
    }

    /**
     * Deliver the atomic value that is next in the atomized result
     *
     * @return the next atomic value
     * @throws net.sf.saxon.trans.XPathException
     *          if a failure occurs reading or atomizing the next value
     */
    @Override
    public AtomicSequence nextAtomizedValue() throws XPathException {
        if (needToAdvance) {
            final int thisNode = nextNodeNr;
            final Predicate<? super NodeInfo> nTest = test;
            final int[] tNext = tree.next;
            if (nTest == null) {
                do {
                    nextNodeNr = tNext[nextNodeNr];
                } while (tree.nodeKind[nextNodeNr] == Type.PARENT_POINTER);
            } else {
                do {
                    nextNodeNr = tNext[nextNodeNr];
                } while (nextNodeNr >= thisNode && !matcher.test(nextNodeNr));
            }

            if (nextNodeNr < thisNode) {    // indicates we've got to the last sibling
                nextNodeNr = -1;
                needToAdvance = false;
                return null;
            }
        }

        if (nextNodeNr == -1) {
            return null;
        }
        needToAdvance = true;
        int kind = tree.nodeKind[nextNodeNr];
        switch (kind) {
            case Type.TEXT: {
                return new UntypedAtomicValue(TinyTextImpl.getStringValue(tree, nextNodeNr));
            }
            case Type.WHITESPACE_TEXT: {
                return new UntypedAtomicValue(WhitespaceTextImpl.getStringValueCS(tree, nextNodeNr));
            }
            case Type.ELEMENT:
            case Type.TEXTUAL_ELEMENT: {
                return tree.getTypedValueOfElement(nextNodeNr);
            }
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
                return tree.getAtomizedValueOfUntypedNode(nextNodeNr);
            default:
                throw new AssertionError("Unknown node kind on child axis");
        }
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
            final Predicate<? super NodeInfo> nTest = test;
            final int[] tNext = tree.next;
            if (nTest == null) {
                do {
                    n = tNext[n];
                } while (tree.nodeKind[n] == Type.PARENT_POINTER);
            } else {
                do {
                    n = tNext[n];
                } while (n >= nextNodeNr && !matcher.test(n));
            }

            if (n < nextNodeNr) {    // indicates we've got to the last sibling
                return false;
            }
        }

        return n != -1;
    }

    @Override
    public EnumSet<Property> getProperties() {
        return EnumSet.of(Property.LOOKAHEAD, Property.ATOMIZING);
    }

}

