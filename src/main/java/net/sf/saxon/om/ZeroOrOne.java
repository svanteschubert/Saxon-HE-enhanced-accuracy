////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ConstrainedIterator;
import net.sf.saxon.value.EmptySequence;

/**
 * A value that is a sequence containing zero or one items.
 */

public class ZeroOrOne<T extends Item> implements GroundedValue {

    private T item; // may be null, to represent an empty sequence

    @SuppressWarnings("rawtypes")
    private static ZeroOrOne EMPTY = new ZeroOrOne<>(null);

    /**
     * Return the instance of ZeroOrOne that represents the empty sequence
     * @return a representation of the empty sequence that satisfies the type ZeroOrOne
     */

    @SuppressWarnings("unchecked")
    public static <T extends Item> ZeroOrOne<T> empty() {
        return EMPTY;
    }

    /**
     * Create a sequence containing zero or one items
     *
     * @param item The item to be contained in the sequence, or null if the sequence
     *             is to be empty
     */

    public ZeroOrOne(T item) {
        this.item = item;
    }

    /**
     * Get the string value of this sequence. The string value of an item is the result of applying the string()
     * function. The string value of an empty sequence is the zero-length string.
     *
     * @return the string value of the sequence.
     */

    @Override
    public CharSequence getStringValueCS() {
        return item == null ? "" : item.getStringValueCS();
    }

    /**
     * Convert the value to a string, using the serialization rules.
     * For atomic values this is the same as a cast; for sequence values
     * it gives a space-separated list. For QNames and NOTATIONS, or lists
     * containing them, it fails.
     */

    /*@NotNull*/
    @Override
    public String getStringValue() {
        return item == null ? "" : item.getStringValue();
    }

    /**
     * Get the first item in the sequence.
     *
     * @return the first item in the sequence if there is one, or null if the sequence
     *         is empty
     */
    @Override
    public T head() {
        return item;
    }

    /**
     * Get the length of the sequence
     */

    @Override
    public int getLength() {
        return item == null ? 0 : 1;
    }

    /**
     * Get the n'th item in the sequence (starting from 0). This is defined for all
     * SequenceValues, but its real benefits come for a SequenceValue stored extensionally
     * (or for a MemoClosure, once all the values have been read)
     * @param n the index of the required item, with 0 representing the first item in the sequence
     * @return the n'th item if it exists, or null otherwise
     */

    /*@Nullable*/
    @Override
    public T itemAt(int n) {
        if (n == 0 && item != null) {
            return item;
        } else {
            return null;
        }
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

    /*@NotNull*/
    @Override
    public GroundedValue subsequence(int start, int length) {
        if (item != null && start <= 0 && start + length > 0) {
            return this;
        } else {
            return EmptySequence.getInstance();
        }
    }

    /**
     * Return an iterator over this value.
     */
    @Override
    public ConstrainedIterator<T> iterate() {
        return new ConstrainedIterator<T>() {
            boolean gone = false;

            @Override
            public boolean hasNext() {
                return item != null && !gone;
            }

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
                return item == null ? 0 : 1;
            }

            @Override
            public GroundedValue materialize() {
                return item == null ? EmptySequence.getInstance() : item;
            }

            @Override
            public GroundedValue getResidue() {
                return gone ? EmptySequence.getInstance() : item;
            }

            @Override
            public SequenceIterator getReverseIterator() {
                return iterate();
            }
        };
    }

    /**
     * Get the effective boolean value
     */

    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        return ExpressionTool.effectiveBooleanValue(item);
    }

    /**
     * Returns a string representation of the object (used only for diagnostics).
     *
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return item==null ? "null" : item.toString();
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
        if (item == null) {
            return EmptySequence.getInstance();
        } else {
            return item;
        }
    }
}
