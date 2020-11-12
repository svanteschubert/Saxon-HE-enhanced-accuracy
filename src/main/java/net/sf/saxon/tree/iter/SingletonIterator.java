////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.value.EmptySequence;

import java.util.EnumSet;


/**
 * SingletonIterator: an iterator over a sequence of zero or one values
 */

public class SingletonIterator<T extends Item> implements SequenceIterator, UnfailingIterator,
        ReversibleIterator, LastPositionFinder, GroundedIterator, LookaheadIterator {

    private final T item;
    boolean gone = false;

    /**
     * Private constructor: external classes should use the factory method
     *
     * @param value the item to iterate over
     */

    public SingletonIterator(T value) {
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
    public static <T extends Item> UnfailingIterator makeIterator(T item) {
        if (item == null) {
            return EmptyIterator.emptyIterator();
        } else {
            return new SingletonIterator<>(item);
        }
    }

    /**
     * Factory method for use when it is known the item will not be null
     *
     * @param item the item to iterate over; must not be null
     * @return a SingletonIterator over the supplied item
     */


    public static <T extends Item> SingletonIterator<T> rawIterator(T item) {
        assert item != null;
        return new SingletonIterator<>(item);
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
        return !gone;
    }

    /*@Nullable*/
    @Override
    public T next() {
        if (gone) {
            return null;
        } else {
            gone = true;
            return item;
        }
    }

    @Override
    public int getLength() {
        return 1;
    }

    /*@NotNull*/
    @Override
    public SingletonIterator<T> getReverseIterator() {
        return new SingletonIterator<>(item);
    }

    public T getValue() {
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
        if (item != null) {
            return item;
        } else {
            return EmptySequence.getInstance();
        }
    }

    @Override
    public GroundedValue getResidue() {
        return gone ? EmptySequence.getInstance() : materialize();
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
        return EnumSet.of(Property.LOOKAHEAD, Property.GROUNDED, Property.LAST_POSITION_FINDER);
    }

}

