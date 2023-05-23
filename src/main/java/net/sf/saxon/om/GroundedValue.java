////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.expr.SingletonIntersectExpression;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.UnfailingIterator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A value that exists in memory and that can be directly addressed
 * @since 9.5.  Generified in 9.9. De-generified in 10.0
 */
public interface GroundedValue extends Sequence {

    /**
     * Get an iterator over all the items in the sequence. This differs from the superclass method
     * in not allowing an exception, either during this method call, or in the subsequent processing
     * of the returned iterator.
     *
     * @return an iterator (meaning a Saxon {@link SequenceIterator} rather than a Java
     * {@link java.util.Iterator}) over all the items in this Sequence.
     */

    @Override
    UnfailingIterator iterate();

    /**
     * Get the n'th item in the value, counting from zero (0)
     *
     * @param n the index of the required item, with zero (0) representing the first item in the sequence
     * @return the n'th item if it exists, or null if the requested position is out of range
     */

    Item itemAt(int n);

    /**
     * Get the first item of the sequence. This differs from the parent interface in not allowing an exception
     * @return the first item of the sequence, or null if the sequence is empty
     */

    @Override
    Item head();

    /**
     * Get a subsequence of the value
     *
     * @param start  the index of the first item to be included in the result, counting from zero.
     *               A negative value is taken as zero. If the value is beyond the end of the sequence, an empty
     *               sequence is returned
     * @param length the number of items to be included in the result. Specify Integer.MAX_VALUE to
     *               get the subsequence up to the end of the base sequence. If the value is negative, an empty sequence
     *               is returned. If the length goes off the end of the sequence, the result returns items up to the end
     *               of the sequence
     * @return the required subsequence.
     */

    GroundedValue subsequence(int start, int length);

    /**
     * Get the size of the value (the number of items in the value, considered as a sequence)
     *
     * @return the number of items in the sequence. Note that for a single item, including a map or array,
     * the result is always 1 (one).
     */

    int getLength();

    /**
     * Get the effective boolean value of this sequence
     *
     * @return the effective boolean value
     * @throws XPathException if the sequence has no effective boolean value (for example a sequence of two integers)
     */

    default boolean effectiveBooleanValue() throws XPathException {
        return ExpressionTool.effectiveBooleanValue(iterate());
    }

    /**
     * Get the string value of this sequence. The string value of an item is the result of applying the string()
     * function. The string value of a sequence is the space-separated result of applying the string-join() function
     * using a single space as the separator
     *
     * @return the string value of the sequence.
     * @throws XPathException if the sequence contains items that have no string value (for example, function items)
     */

    String getStringValue() throws XPathException;

    /**
     * Get the string value of this sequence. The string value of an item is the result of applying the string()
     * function. The string value of a sequence is the space-separated result of applying the string-join() function
     * using a single space as the separator
     *
     * @return the string value of the sequence.
     * @throws XPathException if the sequence contains items that have no string value (for example, function items)
     */

    CharSequence getStringValueCS() throws XPathException;

    /**
     * Reduce the sequence to its simplest form. If the value is an empty sequence, the result will be
     * EmptySequence.getInstance(). If the value is a single atomic value, the result will be an instance
     * of AtomicValue. If the value is a single item of any other kind, the result will be an instance
     * of Item. Otherwise, the result will typically be unchanged.
     *
     * @return the simplified sequence
     */

    default GroundedValue reduce() {
        return this;
    }

    /**
     * Create a {@link GroundedValue} containing the same items as this Sequence.
     * Since this Sequence is already a {@code GroundedValue} this method returns
     * this {@code GroundedValue} unchanged.
     *
     * @return this {@link GroundedValue}
     */

    @Override
    default GroundedValue materialize() {
        return this;
    }

    /**
     * Produce a short representation of the value of the sequence, suitable for use in error messages
     * @return a short representation of the value
     */

    default String toShortString() {
        return Err.depictSequence(this).toString();
    }

    default Iterable<? extends Item> asIterable() {
        // For .NEU - don't use a lambda expression here
        return new Iterable<Item>() {
            @Override
            public Iterator<Item> iterator() {
                final UnfailingIterator base = iterate();
                return new Iterator<Item>() {

                    Item pending = null;

                    @Override
                    public boolean hasNext() {
                        pending = base.next();
                        return pending != null;
                    }

                    @Override
                    public Item next() {
                        return pending;
                    }
                };
            }
        };
    }

    /**
     * Determine whether a particular node is present in the value
     * @param sought the sought-after node
     * @return true if the sought node is present
     * @throws XPathException This should never happen
     */

    default boolean containsNode(NodeInfo sought) throws XPathException {
        return SingletonIntersectExpression.containsNode(iterate(), sought);
    }

    /**
     * Append two or more grounded values to form a new grounded value
     * @param others one or more grounded values that are to be concatenated with this
     *               one, in order
     * @return the concatenation of the supplied sequences (none of which is modified by the operation)
     */

    default GroundedValue concatenate(GroundedValue... others) {
        List<GroundedValue> c = new ArrayList<>();
        c.add(this);
        Collections.addAll(c, others);
        return new Chain(c);
    }
}

