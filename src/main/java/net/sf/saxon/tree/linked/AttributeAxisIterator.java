////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;

/**
 * AttributeAxisIterator is an enumeration of all the attribute nodes of an Element.
 */

final class AttributeAxisIterator implements AxisIterator, LookaheadIterator {

    private final ElementImpl element;
    private final NodeTest nodeTest;
    /*@Nullable*/ private NodeInfo nextNode;
    private int index;
    private final int length;

    /**
     * Constructor
     *
     * @param node:     the element whose attributes are required. This may be any type of node,
     *                  but if it is not an element the enumeration will be empty
     * @param nodeTest: condition to be applied to the names of the attributes selected
     */

    AttributeAxisIterator(ElementImpl node, NodeTest nodeTest) {
        this.element = node;
        this.nodeTest = nodeTest;

        index = 0;
        length = node.attributes().size();
        advance();

    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    /**
     * Test if there are mode nodes still to come.
     * ("elements" is used here in the sense of the Java enumeration class, not in the XML sense)
     */

    @Override
    public boolean hasNext() {
        return nextNode != null;
    }

    /**
     * Get the next node in the iteration, or null if there are no more.
     */

    /*@Nullable*/
    @Override
    public NodeInfo next() {
        if (nextNode == null) {
            return null;
        } else {
            NodeInfo current = nextNode;
            advance();
            return current;
        }
    }

    /**
     * Move to the next node in the iteration.
     */

    private void advance() {
        while (true) {
            if (index >= length) {
                nextNode = null;
                return;
            } else {
                AttributeInfo info = element.attributes().itemAt(index);
                if (info instanceof AttributeInfo.Deleted) {
                    index++;
                } else {
                    nextNode = new AttributeImpl(element, index);
                    index++;
                    if (nodeTest.test(nextNode)) {
                        return;
                    }
                }
            }
        }
    }

}

