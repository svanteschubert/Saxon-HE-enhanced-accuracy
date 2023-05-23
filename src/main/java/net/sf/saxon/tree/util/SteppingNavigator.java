////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.*;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;

import java.util.function.Predicate;


/**
 * The SteppingNavigator is a utility class containing methods to assist with navigating a tree whose nodes
 * implement the {@link SteppingNode} interface
 */

public abstract class SteppingNavigator {

    /**
     * Get the next following node after a given node
     *
     * @param start  the starting node
     * @param anchor the node whose descendants are being scanned; the scan terminates when
     *               the anchor node is reached
     * @return the next node in document order after the starting node, excluding attributes and namespaces;
     * or null if no such node is found
     */

    static <N extends SteppingNode<N>> N getFollowingNode(N start, N anchor) {
        N nodei = start.getFirstChild();
        if (nodei != null) {
            return nodei;
        }
        if (start.isSameNodeInfo(anchor)) {
            return null;
        }
        nodei = start;
        N parenti = start.getParent();
        do {
            nodei = nodei.getNextSibling();
            if (nodei != null) {
                return nodei;
            } else if (parenti.isSameNodeInfo(anchor)) {
                return null;
            }
            nodei = parenti;
            parenti = parenti.getParent();
        } while (parenti != null);

        return null;
    }

    /**
     * Interface representing a function to step from one node to another within a tree
     */

    private interface Stepper<N extends SteppingNode<N>> {
        /**
         * Step from one node to another
         *
         * @param node the start node
         * @return the end node
         */
        N step(N node);
    }

    /**
     * Stepper that steps from one node in a document to the next node in document order,
     * excluding attribute and namespace nodes, returning null when the root of the subtree
     * is reached.
     */

    private static class FollowingNodeStepper<N extends SteppingNode<N>> implements Stepper<N> {

        N anchor;

        /**
         * Create a stepper to step successively through all nodes in a subtree
         *
         * @param anchor the root of the subtree, marking the end point of the iteration
         */

        FollowingNodeStepper(N anchor) {
            this.anchor = anchor;
        }

        @Override
        public N step(N node) {
            return getFollowingNode(node, anchor);
        }
    }

    /**
     * Stepper that steps from one node in a document to the next node in document order,
     * excluding attribute and namespace nodes, returning null when the root of the subtree
     * is reached, and including only nodes that match a specified node test
     */

    private static class FollowingFilteredNodeStepper<N extends SteppingNode<N>> implements Stepper<N> {

        N anchor;
        Predicate<? super NodeInfo> test;

        /**
         * Create a stepper to step successively through selected nodes in a subtree
         *
         * @param anchor the root of the subtree, marking the end point of the iteration
         * @param test   the test that returned nodes must satisfy
         */

        FollowingFilteredNodeStepper(N anchor, Predicate<? super NodeInfo> test) {
            this.anchor = anchor;
            this.test = test;
        }

        @Override
        public N step(N node) {
            do {
                node = getFollowingNode(node, anchor);
            } while (node != null && !test.test(node));
            return node;
        }
    }

    /**
     * Stepper that steps from one element in a document to the next element in document order,
     * excluding attribute and namespace nodes, returning null when the root of the subtree
     * is reached, and including only elements, with optional constraints on the namespace URI
     * and/or local name.
     */

    private static class FollowingElementStepper<N extends SteppingNode<N>> implements Stepper<N> {

        N anchor;
        String uri;
        String local;

        /**
         * Create a stepper to step successively through selected elements in a subtree
         *
         * @param anchor the root of the subtree, marking the end point of the iteration
         * @param uri    either null, or a namespace URI which the selected elements must match
         * @param local  either null, or a local name which the selected elements must match
         */

        FollowingElementStepper(N anchor, String uri, String local) {
            this.anchor = anchor;
            this.uri = uri;
            this.local = local;
        }

        @Override
        public N step(N node) {
            return node.getSuccessorElement(anchor, uri, local);
        }
    }

    /**
     * Stepper that steps from one element in a document to the next element in document order,
     * excluding attribute and namespace nodes, returning null when the root of the subtree
     * is reached, and including only elements, with a constraint on the fingerprint of the element
     */

    private static class FollowingFingerprintedElementStepper<N extends SteppingNode<N>> implements Stepper<N> {

        N anchor;
        int fingerprint;

        /**
         * Create a stepper to step successively through selected elements in a subtree
         *
         * @param anchor      the root of the subtree, marking the end point of the iteration
         * @param fingerprint a fingerprint which selected elements must match
         */

        FollowingFingerprintedElementStepper(N anchor, int fingerprint) {
            this.anchor = anchor;
            this.fingerprint = fingerprint;
        }

        @Override
        public N step(N node) {
            do {
                node = getFollowingNode(node, anchor);
            } while (node != null && node.getFingerprint() != fingerprint);
            return node;
        }
    }


    /**
     * An iterator over the descendant or descendant-or-self axis
     */

    public static class DescendantAxisIterator<N extends SteppingNode<N>> implements AxisIterator {

        private N start;
        private N current;
        private boolean done;

        private Stepper<N> stepper;

        /**
         * Create an iterator over the descendant or descendant-or-self axis
         *
         * @param start       the root of the subtree whose descendants are required
         * @param includeSelf true if this is the descendant-or-self axis
         * @param test        the node-test that selected nodes must satisfy
         */

        public DescendantAxisIterator(N start, boolean includeSelf, Predicate<? super NodeInfo> test) {
            this.start = start;

            if (!(includeSelf && test.test(start))) {
                // initialize currNode to the start node if and only if this is NOT a descendant-or-self scan
                current = start;
            }

            if (test == null || test == AnyNodeTest.getInstance()) {
                stepper = new FollowingNodeStepper<>(start);
            } else if (test instanceof NameTest) {
                if (((NameTest)test).getPrimitiveType() == Type.ELEMENT) {
                    NameTest nt = (NameTest) test;
                    if (start.hasFingerprint()) {
                        stepper = new FollowingFingerprintedElementStepper<>(start, nt.getFingerprint());
                    } else {
                        stepper = new FollowingElementStepper<>(start, nt.getNamespaceURI(), nt.getLocalPart());
                    }
                } else {
                    stepper = new FollowingFilteredNodeStepper<>(start, test);
                }
            } else if (test instanceof NodeKindTest) {
                if (((NodeKindTest)test).getPrimitiveType() == Type.ELEMENT) {
                    stepper = new FollowingElementStepper<>(start, null, null);
                } else {
                    stepper = new FollowingFilteredNodeStepper<>(start, test);
                }
            } else if (test instanceof LocalNameTest) {
                if (((LocalNameTest)test).getPrimitiveType() == Type.ELEMENT) {
                    LocalNameTest nt = (LocalNameTest) test;
                    stepper = new FollowingElementStepper<>(start, null, nt.getLocalName());
                } else {
                    stepper = new FollowingFilteredNodeStepper<>(start, test);
                }
            } else if (test instanceof NamespaceTest) {
                if (((NamespaceTest)test).getPrimitiveType() == Type.ELEMENT) {
                    NamespaceTest nt = (NamespaceTest) test;
                    stepper = new FollowingElementStepper<>(start, nt.getNamespaceURI(), null);
                } else {
                    stepper = new FollowingFilteredNodeStepper<>(start, test);
                }
            } else {
                stepper = new FollowingFilteredNodeStepper<>(start, test);
            }
        }


        @Override
        public N next() {
            if (done) {
                return null;
            }
            if (current == null) {
                // implies includeSelf: first time round, return the start node
                current = start;
                return start;
            }
            N curr = stepper.step(current);
            if (curr == null) {
                done = true;
            }
            return current = curr;
        }

    }
}

