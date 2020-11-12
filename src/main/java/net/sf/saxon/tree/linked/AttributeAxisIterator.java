////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * AttributeAxisIterator is an enumeration of all the attribute nodes of an Element.
 */

final class AttributeAxisIterator implements AxisIterator, LookaheadIterator {

    private final ElementImpl element;
    private final Predicate<? super NodeInfo> nodeTest;
    /*@Nullable*/ private NodeInfo next;
    private int index;
    private final int length;

    /**
     * Constructor
     *
     * @param node:     the element whose attributes are required. This may be any type of node,
     *                  but if it is not an element the enumeration will be empty
     * @param nodeTest: condition to be applied to the names of the attributes selected
     */

    AttributeAxisIterator(ElementImpl node, Predicate<? super NodeInfo> nodeTest) {
        this.element = node;
        this.nodeTest = nodeTest;

        index = 0;
        length = node.attributes().size();
        advance();

    }

    /**
     * Test if there are mode nodes still to come.
     * ("elements" is used here in the sense of the Java enumeration class, not in the XML sense)
     */

    @Override
    public boolean hasNext() {
        return next != null;
    }

    /**
     * Get the next node in the iteration, or null if there are no more.
     */

    /*@Nullable*/
    @Override
    public NodeInfo next() {
        if (next == null) {
            return null;
        } else {
            NodeInfo current = next;
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
                next = null;
                return;
            } else {
                AttributeInfo info = element.attributes().itemAt(index);
                if (info instanceof AttributeInfo.Deleted) {
                    index++;
                } else {
                    next = new AttributeImpl(element, index);
                    index++;
                    if (nodeTest.test(next)) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link net.sf.saxon.om.SequenceIterator.Property#GROUNDED}, {@link net.sf.saxon.om.SequenceIterator.Property#LAST_POSITION_FINDER},
     *         and {@link net.sf.saxon.om.SequenceIterator.Property#LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    @Override
    public EnumSet<Property> getProperties() {
        return EnumSet.of(Property.LOOKAHEAD);
    }
}

