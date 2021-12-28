////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.RangeIterator;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.AtomicArray;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;

import java.util.Iterator;

/**
 * This class represents a sequence of consecutive ascending integers, for example 1 to 50.
 * The integers must be within the range of a Java long.
 */

public class IntegerRange implements AtomicSequence {

    public long start;
    public long end;

    /**
     * Construct an integer range expression
     *
     * @param start the first integer in the sequence (inclusive)
     * @param end   the last integer in the sequence (inclusive). Must be &gt;= start
     */

    public IntegerRange(long start, long end) {
        if (end < start) {
            throw new IllegalArgumentException("end < start in IntegerRange");
        }
        if (end - start > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Maximum length of sequence in Saxon is " + Integer.MAX_VALUE);
        }
        this.start = start;
        this.end = end;
    }

    /**
     * Get the first integer in the sequence (inclusive)
     *
     * @return the first integer in the sequence (inclusive)
     */

    public long getStart() {
        return start;
    }

    /**
     * Get the last integer in the sequence (inclusive)
     *
     * @return the last integer in the sequence (inclusive)
     */

    public long getEnd() {
        return end;
    }


    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     */

    /*@NotNull*/
    @Override
    public AtomicIterator iterate() {
        return new RangeIterator(start, end);
    }

    /**
     * Get the n'th item in the sequence (starting from 0). This is defined for all
     * Values, but its real benefits come for a sequence Value stored extensionally
     * (or for a MemoClosure, once all the values have been read)
     */

    /*@Nullable*/
    @Override
    public IntegerValue itemAt(int n) {
        if (n < 0 || n > (end - start)) {
            return null;
        }
        return Int64Value.makeIntegerValue(start + n);
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
     * @return the required subsequence.
     */

    /*@NotNull*/
    @Override
    public GroundedValue subsequence(int start, int length) {
        if (length <= 0) {
            return EmptySequence.getInstance();
        }
        long newStart = this.start + (start > 0 ? start : 0);
        long newEnd = newStart + length - 1;
        if (newEnd > end) {
            newEnd = end;
        }
        if (newEnd >= newStart) {
            return new IntegerRange(newStart, newEnd);
        } else {
            return EmptySequence.getInstance();
        }
    }

    /**
     * Get the length of the sequence
     */

    @Override
    public int getLength() {
        return (int) (end - start + 1);
    }

    @Override
    public IntegerValue head() {
        return new Int64Value(start);
    }

    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules.
     *
     * @return the canonical lexical representation if defined in XML Schema; otherwise, the result
     *         of casting to string according to the XPath 2.0 rules
     */
    @Override
    public CharSequence getCanonicalLexicalRepresentation() {
        return getStringValueCS();
    }

    /**
     * Get a Comparable value that implements the XML Schema ordering comparison semantics for this value.
     * The default implementation is written to compare sequences of atomic values.
     * This method is overridden for AtomicValue and its subclasses.
     * <p>In the case of data types that are partially ordered, the returned Comparable extends the standard
     * semantics of the compareTo() method by returning the value {@link net.sf.saxon.om.SequenceTool#INDETERMINATE_ORDERING} when there
     * is no defined order relationship between two given values.</p>
     *
     * @return a Comparable that follows XML Schema comparison rules
     */
    @Override
    public Comparable getSchemaComparable() {
        try {
            return new AtomicArray(iterate()).getSchemaComparable();
        } catch (XPathException err) {
            throw new AssertionError(err);
        }
    }

    @Override
    public CharSequence getStringValueCS() {
        try {
            return SequenceTool.getStringValue(this);
        } catch (XPathException err) {
            throw new AssertionError(err);
        }
    }

    @Override
    public String getStringValue() {
        return getStringValueCS().toString();
    }

    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        return ExpressionTool.effectiveBooleanValue(iterate());
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
        if (start == end) {
            return itemAt(0);
        } else {
            return this;
        }
    }

    public String toString() {
        return "(" + start + " to " + end + ")";
    }

    /**
     * Return a Java iterator over the atomic sequence.
     *
     * @return an Iterator.
     */

    @Override
    public Iterator<AtomicValue> iterator() {
        return new Iterator<AtomicValue>() {

            long current = start;

            /**
             * Returns <tt>true</tt> if the iteration has more elements. (In other
             * words, returns <tt>true</tt> if <tt>next</tt> would return an element
             * rather than throwing an exception.)
             *
             * @return <tt>true</tt> if the iterator has more elements.
             */
            @Override
            public boolean hasNext() {
                return current <= end;
            }

            /**
             * Removes from the underlying collection the last element returned by the
             * iterator (optional operation).  This method can be called only once per
             * call to <tt>next</tt>.  The behavior of an iterator is unspecified if
             * the underlying collection is modified while the iteration is in
             * progress in any way other than by calling this method.
             *
             * @throws UnsupportedOperationException if the <tt>remove</tt>
             *                                       operation is not supported by this Iterator.
             * @throws IllegalStateException         if the <tt>next</tt> method has not
             *                                       yet been called, or the <tt>remove</tt> method has already
             *                                       been called after the last call to the <tt>next</tt>
             *                                       method.
             */
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            /**
             * Returns the next element in the iteration.
             *
             * @return the next element in the iteration.
             * @throws java.util.NoSuchElementException
             *          iteration has no more elements.
             */
            @Override
            public IntegerValue next() {
                return new Int64Value(current++);

            }
        };
    }
}

