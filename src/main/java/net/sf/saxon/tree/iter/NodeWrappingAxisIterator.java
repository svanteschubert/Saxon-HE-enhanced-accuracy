////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.NodeInfo;

import java.util.EnumSet;
import java.util.Iterator;

/**
 * An AxisIterator that wraps a Java Iterator.
 * @param <B> the class of the external nodes being wrapped.
 */

public class NodeWrappingAxisIterator<B>
        implements AxisIterator, LookaheadIterator {


    Iterator<? extends B> base;
    private final NodeWrappingFunction<? super B, NodeInfo> wrappingFunction;


    /**
     * Create a SequenceIterator over a given iterator
     *
     * @param base             the base Iterator
     * @param wrappingFunction a function that wraps objects of type B in a Saxon NodeInfo
     */

    public NodeWrappingAxisIterator(
            Iterator<? extends B> base,
            NodeWrappingFunction<? super B, NodeInfo> wrappingFunction) {
        this.base = base;
        this.wrappingFunction = wrappingFunction;
    }

    public Iterator<? extends B> getBaseIterator() {
        return base;
    }

    public NodeWrappingFunction<? super B, NodeInfo> getNodeWrappingFunction() {
        return wrappingFunction;
    }


    @Override
    public boolean hasNext() {
        return base.hasNext();
    }

    /*@Nullable*/
    @Override
    public NodeInfo next() {
        while (base.hasNext()) {
            B next = base.next();
            if (!isIgnorable(next)) {
                return wrappingFunction.wrap(next);
            }
        }
        return null;
    }

    public boolean isIgnorable(B node) {
        return false;
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

