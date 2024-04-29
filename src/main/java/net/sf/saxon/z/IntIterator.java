////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;

/**
 * An iterator over a sequence of unboxed int values.
 *
 * <p>Note that although {@code IntIterator} uses the same method names as {@link java.util.Iterator},
 * the contract is different. The behavior of the iterator is well defined if each call on
 * {@link #next()} is preceded by exactly one call on {@link #hasNext()} (and that call
 * must have returned {@code true}); in all other cases the result is unpredictable. So unlike
 * {@link java.util.Iterator}, both {@link #hasNext()} and {@link #next()} are allowed
 * to advance the position of the iterator.</p>
 */
public interface IntIterator {

    /**
     * Test whether there are any more integers in the sequence; and change the state of
     * the iterator so a call on {@link #next()} delivers the next integer.
     *
     * <p>The effect of calling {@code #hasNext()} a second time without an intervening
     * call on {@code #next()} is undefined.</p>
     *
     * @return true if there are more integers to come
     */

    boolean hasNext();

    /**
     * Return the next integer in the sequence. The result is undefined unless {@code #hasNext()} has been called
     * and has returned true.
     *
     * @return the next integer in the sequence
     */

    int next();
}
