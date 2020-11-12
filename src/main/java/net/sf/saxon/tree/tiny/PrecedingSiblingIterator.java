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
 * This class supports the preceding-sibling axis.
 * The starting node must be an element, text node, comment, or processing instruction:
 * to ensure this, construct the enumeration using NodeInfo#getEnumeration()
 */

final class PrecedingSiblingIterator implements AxisIterator {

    private TinyTree document;
    private TinyNodeImpl startNode;
    private int nextNodeNr;
    private NodeTest test;
    private TinyNodeImpl parentNode;
    private final IntPredicate matcher;

    PrecedingSiblingIterator(TinyTree doc, /*@NotNull*/ TinyNodeImpl node, NodeTest nodeTest) {
        document = doc;
        document.ensurePriorIndex();
        test = nodeTest;
        startNode = node;
        nextNodeNr = node.nodeNr;
        parentNode = node.parent;   // doesn't matter if this is null (unknown)
        this.matcher = nodeTest.getMatcher(doc);
    }

    /*@Nullable*/
    @Override
    public NodeInfo next() {
        if (nextNodeNr < 0) {
            // This check is needed because an errant caller can call next() again after hitting the end of sequence
            return null;
        }
        while (true) {
            nextNodeNr = document.prior[nextNodeNr];
            if (nextNodeNr < 0) {
                return null;
            }
            if (matcher.test(nextNodeNr)) {
                TinyNodeImpl next = document.getNode(nextNodeNr);
                next.setParentNode(parentNode);
                return next;
            }
        }
    }

}

