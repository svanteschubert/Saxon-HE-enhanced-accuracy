////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomizingIterator;
import net.sf.saxon.value.SequenceExtent;

import java.io.Closeable;
import java.util.EnumSet;

/**
 * A SequenceIterator is used to iterate over any XPath 2 sequence (of values or nodes).
 * To get the next item in a sequence, call next(); if this returns null, you've
 * reached the end of the sequence.
 * <p>The objects returned by the SequenceIterator will generally be either nodes
 * (class NodeInfo), singleton values (class AtomicValue), or function items: these are represented
 * collectively by the interface {@link Item}.</p>
 * <p>The interface to SequenceIterator is changed in Saxon 9.6 to drop support for the
 * current() and position() methods. Internal iterators no longer need to maintain the values
 * of the current item or the current position. This information is needed (in general) only
 * for an iterator that acts as the current focus; that is, an iterator stored as the current
 * iterator in an XPathContext. SequenceIterators than maintain the value of position()
 * and last() are represented by the interface {@link FocusIterator}.</p>
 *
 * @since 8.4. Significant changes in 9.6. Generics added in 9.9, removed again in 10.0
 */

public interface SequenceIterator extends Closeable {

    /**
     * Get the next item in the sequence. This method changes the state of the
     * iterator.
     *
     * @return the next item, or null if there are no more items. Once a call
     *         on next() has returned null, no further calls should be made. The preferred
     *         action for an iterator if subsequent calls on next() are made is to return
     *         null again, and all implementations within Saxon follow this rule.
     * @throws XPathException if an error occurs retrieving the next item
     * @since 8.4
     */

    /*@Nullable*/
    Item next() throws XPathException;

    /**
     * Close the iterator. This indicates to the supplier of the data that the client
     * does not require any more items to be delivered by the iterator. This may enable the
     * supplier to release resources. After calling close(), no further calls on the
     * iterator should be made; if further calls are made, the effect of such calls is undefined.
     * <p>For example, the iterator returned by the unparsed-text-lines() function has a close() method
     * that causes the underlying input stream to be closed, whether or not the file has been read
     * to completion.</p>
     * <p>Closing an iterator is important when the data is being "pushed" in
     * another thread. Closing the iterator terminates that thread and means that it needs to do
     * no additional work. Indeed, failing to close the iterator may cause the push thread to hang
     * waiting for the buffer to be emptied.</p>
     *
     * @since 9.1. Default implementation added in 9.9.
     */

    @Override
    default void close() {}

    /**
     * Get properties of this iterator.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link Property#GROUNDED}, {@link Property#LAST_POSITION_FINDER},
     *         and {@link Property#LOOKAHEAD}. It is always
     *         acceptable to return the default value {@code EnumSet.noneOf(Property.class)},
     *         indicating that there are no known special properties.
     *         It is acceptable (though unusual) for the properties of the iterator to change depending
     *         on its state.
     * @since 8.6. Default implementation added in 9.9.
     */

    default EnumSet<Property> getProperties() {
        return EnumSet.noneOf(Property.class);
    }

    enum Property {

        /**
         * Property value: the iterator is "grounded". This means that (a) the
         * iterator must be an instance of {@link net.sf.saxon.tree.iter.GroundedIterator}, and (b) the
         * implementation of the materialize() method must be efficient (in particular,
         * it should not involve the creation of new objects)
         */

        GROUNDED,

        /**
         * Property value: the iterator knows the number of items that it will deliver.
         * This means that (a) the iterator must be an instance of {@link net.sf.saxon.expr.LastPositionFinder},
         * and (b) the implementation of the getLastPosition() method must be efficient (in particular,
         * it should take constant time, rather than time proportional to the length of the sequence)
         */

        LAST_POSITION_FINDER,

        /**
         * Property value: the iterator knows whether there are more items still to come. This means
         * that (a) the iterator must be an instance of {@link net.sf.saxon.tree.iter.LookaheadIterator}, and (b) the
         * implementation of the hasNext() method must be efficient (more efficient than the client doing
         * it)
         */

        LOOKAHEAD,

        /**
         * Property value: the iterator can deliver an atomized result. This means that the iterator
         * must be an instance of {@link AtomizingIterator}.
         */

        ATOMIZING

    }

    /**
     * Process all the remaining items delivered by the SequenceIterator using a supplied consumer function.
     *
     * @param consumer the supplied consumer function
     * @throws XPathException if either (a) an error occurs obtaining an item from the input sequence,
     *                        or (b) the consumer throws an exception.
     */

    default void forEachOrFail(ItemConsumer<? super Item> consumer) throws XPathException {
        Item item;
        while ((item = next()) != null) {
            consumer.accept(item);
        }
    }

    /**
     * Create a GroundedValue (a sequence materialized in memory) containing all the values delivered
     * by this SequenceIterator. The method must only be used when the SequenceIterator is positioned
     * at the start. If it is not positioned at the start, then it is implementation-dependant whether
     * the returned sequence contains all the nodes delivered by the SequenceIterator from the beginning,
     * or only those delivered starting at the current position.
     * <p>It is implementation-dependant whether this method consumes the SequenceIterator. (More specifically,
     * in the current implementation: if the iterator is backed by a {@link GroundedValue}, then that
     * value is returned and the iterator is not consumed; otherwise, the iterator is consumed and the
     * method returns the remaining items after the current position only).</p>
     *
     * @return a sequence containing all the items delivered by this SequenceIterator.
     * @throws XPathException if reading the SequenceIterator throws an error
     */

    default GroundedValue materialize() throws XPathException {
        return new SequenceExtent(this).reduce();
    }



}

