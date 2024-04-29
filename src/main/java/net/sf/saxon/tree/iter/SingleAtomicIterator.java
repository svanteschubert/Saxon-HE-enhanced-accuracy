////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.value.AtomicValue;


/**
 * SingletonIterator: an iterator over a sequence of exactly one atomic value
 */

public class SingleAtomicIterator
        extends SingletonIterator
        implements AtomicIterator,
        ReversibleIterator, LastPositionFinder,
        GroundedIterator, LookaheadIterator {

    /**
     * Protected constructor: external classes should use the factory method
     *
     * @param value the item to iterate over
     */

    protected SingleAtomicIterator(AtomicValue value) {
        super(value);
    }

    /**
     * Factory method.
     *
     * @param item the item to iterate over
     * @return a SingletonIterator over the supplied item, or an EmptyIterator
     * if the supplied item is null.
     */

    /*@NotNull*/
    public static AtomicIterator makeIterator(AtomicValue item) {
        if (item == null) {
            return EmptyIterator.ofAtomic();
        } else {
            return new SingleAtomicIterator(item);
        }
    }

    /*@NotNull*/
    @Override
    public SingleAtomicIterator getReverseIterator() {
        return new SingleAtomicIterator((AtomicValue)getValue());
    }

    @Override
    public AtomicValue next() {
        return (AtomicValue)super.next();
    }
}

