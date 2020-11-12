////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.value.AtomicValue;


/**
 * SingletonIterator: an iterator over a sequence of zero or one values
 */

public class SingleAtomicIterator<T extends AtomicValue>
        extends SingletonIterator<T>
        implements AtomicIterator<T>,
        ReversibleIterator, LastPositionFinder,
        GroundedIterator, LookaheadIterator {

    /**
     * Private constructor: external classes should use the factory method
     *
     * @param value the item to iterate over
     */

    public SingleAtomicIterator(T value) {
        super(value);
    }


    /*@NotNull*/
    @Override
    public SingleAtomicIterator<T> getReverseIterator() {
        return new SingleAtomicIterator<T>(getValue());
    }


}

