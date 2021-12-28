////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;

import java.util.EnumSet;
import java.util.function.Predicate;

abstract class TreeEnumeration implements AxisIterator, LookaheadIterator {

    protected NodeImpl start;
    /*@Nullable*/ protected NodeImpl next;
    protected Predicate<? super NodeInfo> nodeTest;
    /*@Nullable*/ protected NodeImpl current = null;
    protected int position = 0;

    /**
     * Create an axis enumeration for a given type and name of node, from a given
     * origin node
     *
     * @param origin   the node from which the axis originates
     * @param nodeTest test to be satisfied by the returned nodes, or null if all nodes
     *                 are to be returned.
     */

    public TreeEnumeration(NodeImpl origin, Predicate<? super NodeInfo> nodeTest) {
        next = origin;
        start = origin;
        this.nodeTest = nodeTest;
    }

    /**
     * Test whether a node conforms to the node type and name constraints.
     * Note that this returns true if the supplied node is null, this is a way of
     * terminating a loop.
     *
     * @param node the node to be tested
     * @return true if the node matches the requested node type and name
     */

    protected boolean conforms(/*@Nullable*/ NodeImpl node) {
        return node == null || nodeTest == null || nodeTest.test(node);
    }

    /**
     * Advance along the axis until a node is found that matches the required criteria
     */

    protected final void advance() {
        do {
            step();
        } while (!conforms(next));
    }

    /**
     * Advance one step along the axis: the resulting node might not meet the required
     * criteria for inclusion
     */

    protected abstract void step();

    /**
     * Determine whether there are more items to come. Note that this operation
     * is stateless and it is not necessary (or usual) to call it before calling
     * next(). It is used only when there is an explicit need to tell if we
     * are at the last element.
     *
     * @return true if there are more items in the sequence
     */

    @Override
    public boolean hasNext() {
        return next != null;
    }


    /**
     * Return the next node in the sequence
     */

    /*@Nullable*/
    @Override
    public final NodeInfo next() {
        if (next == null) {
            current = null;
            position = -1;
            return null;
        } else {
            current = next;
            position++;
            advance();
            return current;
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

