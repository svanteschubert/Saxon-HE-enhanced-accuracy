////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.om.FocusIterator;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.value.EmptySequence;


/**
 * SingletonIterator: an iterator over a sequence exactly one value
 */

public class SingletonIterator implements SequenceIterator, FocusIterator,
        ReversibleIterator, LastPositionFinder, GroundedIterator, LookaheadIterator {

    private final Item item;
    private int currentPosition = -1;

    /**
     * Constructor
     *
     * @param value the item to iterate over. Must not be null.
     */

    public SingletonIterator(Item value) {
        assert value != null;
        //Instrumentation.count("SINGLETON ITERATOR");
        this.item = value;
    }

    /**
     * Factory method.
     *
     * @param item the item to iterate over
     * @return a SingletonIterator over the supplied item, or an EmptyIterator
     *         if the supplied item is null.
     */

    /*@NotNull*/
    public static SequenceIterator makeIterator(Item item) {
        if (item == null) {
            return EmptyIterator.getInstance();
        } else {
            return new SingletonIterator(item);
        }
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }


    /**
     * Determine whether there are more items to come. Note that this operation
     * is stateless and it is not necessary (or usual) to call it before calling
     * next(). It is used only when there is an explicit need to tell if we
     * are at the last element.
     *
     * @return true if there are more items
     */

    @Override
    public boolean hasNext() {
        return currentPosition < 0;
    }

    /*@Nullable*/
    @Override
    public Item next() {
        return ++currentPosition == 0 ? item : null;
    }

    @Override
    public Item current() {
        return currentPosition == 0 ? item : null;
    }

    @Override
    public int position() {
        return currentPosition + 1;
    }

    @Override
    public void close() {
        // no action
    }

    @Override
    public boolean supportsGetLength() {
        return true;
    }

    @Override
    public int getLength() {
        return 1;
    }

    public boolean isActuallyGrounded() {
        return true;
    }

    /*@NotNull*/
    @Override
    public SingletonIterator getReverseIterator() {
        return new SingletonIterator(item);
    }

    public Item getValue() {
        return item;
    }

    /**
     * Return a Value containing all the items in the sequence returned by this
     * SequenceIterator
     *
     * @return the corresponding Value. If the value is a closure or a function call package, it will be
     *         evaluated and expanded.
     */

    /*@NotNull*/
    @Override
    public GroundedValue materialize() {
        return item;
    }

    @Override
    public GroundedValue getResidue() {
        return currentPosition < 0 ? item : EmptySequence.getInstance();
    }

}

