////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.EmptySequence;

/**
 * An Item is an object that can occur as a member of a sequence.
 * It corresponds directly to the concept of an item in the XPath 2.0 data model.
 * There are four kinds of Item: atomic values, nodes, function items, and external objects.
 * <p>This interface is part of the public Saxon API. As such (starting from Saxon 8.4),
 * methods that form part of the stable API are labelled with a JavaDoc "since" tag
 * to identify the Saxon release at which they were introduced.</p>
 * <p>Note: there is no method getItemType(). This is to avoid having to implement it
 * on every implementation of NodeInfo. Instead, use the static method Type.getItemType(Item).</p>
 *
 */

public interface Item extends GroundedValue {

    /**
     * Get the genre of this item (to distinguish the top-level categories of item,
     * such as nodes, atomic values, and functions)
     * @return the genre
     */

    Genre getGenre();

    /**
     * Get the first item in the sequence. Differs from the superclass {@link Sequence} in that
     *      * no exception is thrown.
     *
     * @return the first item in the sequence if there is one, or null if the sequence
     * is empty
     */

    @Override
    default Item head() {
        return this;
    }

    /**
     * Get the value of the item as a string. For nodes, this is the string value of the
     * node as defined in the XPath 2.0 data model, except that all nodes are treated as being
     * untyped: it is not an error to get the string value of a node with a complex type.
     * For atomic values, the method returns the result of casting the atomic value to a string.
     * <p>If the calling code can handle any CharSequence, the method {@link #getStringValueCS} should
     * be used. If the caller requires a string, this method is preferred.</p>
     *
     * @return the string value of the item
     * @throws UnsupportedOperationException if the item is a function item (an unchecked exception
     *                                       is used here to avoid introducing exception handling to a large number of paths where it is not
     *                                       needed)
     * @see #getStringValueCS
     * @since 8.4
     */

    @Override
    String getStringValue();

    /**
     * Get the string value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String. The method satisfies the rule that
     * <code>X.getStringValueCS().toString()</code> returns a string that is equal to
     * <code>X.getStringValue()</code>.
     * <p>Note that two CharSequence values of different types should not be compared using equals(), and
     * for the same reason they should not be used as a key in a hash table.</p>
     * <p>If the calling code can handle any CharSequence, this method should
     * be used. If the caller requires a string, the {@link #getStringValue} method is preferred.</p>
     *
     * @return the string value of the item
     * @throws UnsupportedOperationException if the item is a function item (an unchecked exception
     *                                       is used here to avoid introducing exception handling to a large number of paths where it is not
     *                                       needed)
     * @see #getStringValue
     * @since 8.4
     */

    @Override
    CharSequence getStringValueCS();

    /**
     * Atomize the item.
     * @return the result of atomization
     * @throws net.sf.saxon.trans.XPathException if atomization is not allowed
     * for this kind of item
     */

    AtomicSequence atomize() throws XPathException;

    /**
     * Provide a short string showing the contents of the item, suitable
     * for use in error messages
     * @return a depiction of the item suitable for use in error messages
     */

    @Override
    default String toShortString() {
        return toString();
    }

//    /**
//     * Get the contents of this value in the form of a Java {@link java.util.Iterator},
//     * so that the sequence value can be used in a for-each expression
//     *
//     * @return an Iterator delivering a sequence of items containing this single item only
//     */
//
//    default Iterator<Item> iterator() {
//        return new MonoIterator<>(head());
//    }

    /**
     * Get the n'th item in the value, counting from 0
     *
     * @param n the index of the required item, with 0 representing the first item in the sequence
     * @return the n'th item if it exists, or null otherwise
     */
    @Override
    default Item itemAt(int n) {
        return n == 0 ? head() : null;
    }

    /**
     * Get a subsequence of the value
     *
     * @param start  the index of the first item to be included in the result, counting from zero.
     *               A negative value is taken as zero. If the value is beyond the end of the sequence, an empty
     *               sequence is returned
     * @param length the number of items to be included in the result. Specify Integer.MAX_VALUE to
     *               get the subsequence up to the end of the base sequence. If the value is negative, an empty sequence
     *               is returned. If the value goes off the end of the sequence, the result returns items up to the end
     *               of the sequence
     * @return the required subsequence. If min is
     */
    @Override
    default GroundedValue subsequence(int start, int length) {
        return start <= 0 && (start + length) > 0 ? this : EmptySequence.getInstance();
    }

    /**
     * Get the size of the value (the number of items)
     *
     * @return the number of items in the sequence. Note that for a single item, including a map or array,
     * the result is always 1 (one).
     */
    @Override
    default int getLength() {
        return 1;
    }

    /**
     * Get an iterator over all the items in the sequence
     *
     * @return an iterator over all the items
     */
    @Override
    default SingletonIterator<? extends Item> iterate() {
        return new SingletonIterator<>(this);
    }

    /**
     * Reduce the sequence to its simplest form. If the value is an empty sequence, the result will be
     * EmptySequence.getInstance(). If the value is a single atomic value, the result will be an instance
     * of AtomicValue. If the value is a single item of any other kind, the result will be an instance
     * of Item. Otherwise, the result will typically be unchanged.
     *
     * @return the simplified sequence
     */
    @Override
    default GroundedValue reduce() {
        return this;
    }

    static GroundedValue toGroundedValue(Item item) {
        return item.reduce();
    }

    /**
     * Ask whether this is a node in a streamed document
     *
     * @return true if the node is in a document being processed using streaming
     */

    default boolean isStreamed() {
        return false;
    }


}


