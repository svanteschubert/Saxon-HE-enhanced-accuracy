////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.ma.zeno.ZenoChain;
import net.sf.saxon.ma.zeno.ZenoSequence;
import net.sf.saxon.om.*;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;

import java.util.Collections;

/**
 * An EmptySequence object represents a sequence containing no items.
 * @since 9.5.  Generified in 9.9.  Generification reverted in 11.0.
 */


public enum EmptySequence implements GroundedValue {

    // This class has a single instance
    INSTANCE;

    public static EmptySequence getInstance() {
        return INSTANCE;
    }


    @Override
    public UnicodeString getUnicodeStringValue() {
        return EmptyUnicodeString.getInstance();
    }

    @Override
    public String getStringValue() {
        return "";
    }

    /**
     * Get the first item in the sequence.
     *
     * @return the first item in the sequence if there is one, or null if the sequence
     *         is empty
     */
    @Override
    public Item head() {
        return null;
    }

    /**
     * Return an iteration over the sequence
     * @return an empty iterator
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate() {
        return EmptyIterator.INSTANCE;
    }

    /**
     * Get the length of the sequence
     *
     * @return always 0 for an empty sequence
     */

    @Override
    public final int getLength() {
        return 0;
    }

    /**
     * Get the effective boolean value - always false
     */

    @Override
    public boolean effectiveBooleanValue() {
        return false;
    }


    /**
     * Get the n'th item in the sequence (starting from 0). This is defined for all
     * Values, but its real benefits come for a sequence Value stored extensionally
     * (or for a MemoClosure, once all the values have been read)
     *
     * @param n position of the required item, counting from zero.
     * @return the n'th item in the sequence, where the first item in the sequence is
     *         numbered zero. If n is negative or &gt;= the length of the sequence, returns null.
     */

    /*@Nullable*/
    @Override
    public Item itemAt(int n) {
        return null;
    }

    /**
     * Get a subsequence of the value
     *
     * @param min    the index of the first item to be included in the result, counting from zero.
     *               A negative value is taken as zero. If the value is beyond the end of the sequence, an empty
     *               sequence is returned
     * @param length the number of items to be included in the result. Specify Integer.MAX_VALUE to
     *               get the subsequence up to the end of the base sequence. If the value is negative, an empty sequence
     *               is returned. If the value goes off the end of the sequence, the result returns items up to the end
     *               of the sequence
     * @return the required subsequence. If min is
     */

    /*@NotNull*/
    @Override
    public GroundedValue subsequence(int min, int length) {
        return this;
    }

    /**
     * Returns a string representation of the object.
     */

    /*@NotNull*/
    public String toString() {
        return "()";
    }

    /**
     * Reduce the sequence to its simplest form. If the value is an empty sequence, the result will be
     * EmptySequence.getInstance(). If the value is a single atomic value, the result will be an instance
     * of AtomicValue. If the value is a single item of any other kind, the result will be an instance
     * of SingletonItem. Otherwise, the result will typically be unchanged.
     *
     * @return the simplified sequence
     */
    @Override
    public GroundedValue reduce() {
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
    public GroundedValue materialize() {
        return this;
    }

    /**
     * Ensure that the sequence is in a form where it can be evaluated more than once. Some
     * sequences (for example {@link LazySequence} and {@link Closure} can only be evaluated
     * once, and this operation causes these to be grounded. However, making it repeatable
     * is not the same as making it grounded; it does not flush out all errors. Indeed, lazy
     * evaluation relies on this property, because an expression that has been lifted out of
     * a loop must not be evaluated unless the loop is executed at least once, to prevent spurious
     * errors.
     *
     * @return An equivalent sequence that can be repeatedly evaluated
     * @throws XPathException if evaluation fails
     */
    @Override
    public Sequence makeRepeatable() throws XPathException {
        return this;
    }

    /**
     * Produce a short representation of the value of the sequence, suitable for use in error messages
     *
     * @return a short representation of the value
     */
    @Override
    public String toShortString() {
        return "()";
    }

    /**
     * Get an {@link Iterable} that wraps this <code>GroundedValue</code>, allowing
     * it to be used in a Java for-each loop.
     *
     * @return an iterable delivering the contents of this value
     */
    @Override
    public Iterable<? extends Item> asIterable() {
        return Collections.emptyList();
    }

    /**
     * Determine whether a particular node is present in the value
     *
     * @param sought the sought-after node
     * @return true if the sought node is present
     * @throws XPathException This should never happen
     */
    @Override
    public boolean containsNode(NodeInfo sought) throws XPathException {
        return false;
    }

    /**
     * Append two or more grounded values to form a new grounded value
     *
     * @param others one or more grounded values that are to be concatenated with this
     *               one, in order
     * @return the concatenation of the supplied sequences (none of which is modified by the operation)
     */
    @Override
    public GroundedValue concatenate(GroundedValue... others) {
        if (others.length == 0) {
            return this;
        }
        if (others.length == 1) {
            return others[0];
        }
        ZenoChain<Item> chain = new ZenoChain<>();
        for (GroundedValue val : others) {
            chain = chain.addAll(val.asIterable());
        }
        return new ZenoSequence(chain);
    }




}

