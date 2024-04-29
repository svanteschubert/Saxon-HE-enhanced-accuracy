////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.tree.iter.UnparsedTextIterator;

import java.io.Closeable;

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
 * @since 8.4. Significant changes in 9.6. Generics added in 9.9, removed again in 10.0.
 * getProperties() method dropped in 11 (instead, callers should check whether the
 * iterator implements a more specific interface such as {@link net.sf.saxon.expr.LastPositionFinder})
 */

public interface SequenceIterator extends Closeable {

    /**
     * Get the next item in the sequence. This method changes the state of the
     * iterator.
     *
     * @return the next item, or null if there are no more items. Once a call
     * on next() has returned null, no further calls should be made. The preferred
     * action for an iterator if subsequent calls on next() are made is to return
     * null again, and all implementations within Saxon follow this rule.
     * @throws UncheckedXPathException if an error occurs retrieving the next item
     * @since 8.4. Changed in 11 so it no longer throws a checked exception;
     * instead, any error that occurs is thrown as an unchecked exception.
     */

    /*@Nullable*/
    Item next();

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
     * <p>Closing an iterator is not necessary if the iterator is read to completion: if a call
     * on {@link #next()} returns null, the iterator will be closed automatically. An explicit
     * call on {@link #close()} is needed only when iteration is abandoned prematurely.</p>
     * <p>It is not possible to guarantee that an iterator that is not read to completion or
     * will be closed. For example, if a lazy-evaluated variable <code>$var</code> is passed to a user-written function,
     * the function may access <code>$var[1]</code> only; we have no way of knowing whether further items
     * will be read. For this reason, any <code>SequenceIterator</code> that holds resources which need
     * to be closed should use the <code>Cleaner</code> mechanism. The <code>Configuration</code> holds
     * a <code>Cleaner</code>, and resources held by a <code>SequenceIterator</code> should be registered
     * with the <code>Cleaner</code>; if the <code>SequenceIterator</code> is then garbage-collected
     * without being closed, the <code>Cleaner</code> will ensure that the underlying resources are
     * closed. (An example of a <code>SequenceIterator</code> that uses this mechanism is the
     * {@link UnparsedTextIterator}).</p>
     *
     * @since 9.1. Default implementation added in 9.9.
     */

    @Override
    default void close() {
    }

}

