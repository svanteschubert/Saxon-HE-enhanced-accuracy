////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.AscendingRangeIterator;
import net.sf.saxon.expr.DescendingRangeIterator;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;

import java.util.Iterator;

/**
 * This class represents a sequence of integers, for example 1 by 5 to 50.
 * The integers must be within the range of a Java long.
 */

public class IntegerRange implements AtomicSequence {

    public long start;
    public long step;
    public long end;  // the adjusted end, so it is actually the last number returned

    /**
     * Construct an integer range expression
     *
     * @param start the first integer in the sequence (inclusive)
     * @param step the step between consecutive integers in the sequence (non-zero, may be negative)
     * @param end   the last integer in the sequence (inclusive). Must be &gt;= start
     */

    public IntegerRange(long start, long step, long end) {
        if (step == 0) {
            throw new IllegalArgumentException("step = 0 in IntegerRange");
        }
        if (end != start && (end > start != step > 0)) {
            throw new IllegalArgumentException("end before start in IntegerRange");
        }
        if (Math.abs((end - start)/step) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Maximum length of sequence in Saxon is " + Integer.MAX_VALUE);
        }
        this.start = start;
        this.step = step;
        this.end = start + step * (end - start)/step;
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
     * Get the increment in the sequence
     *
     * @return the increment
     */

    public long getStep() {
        return step;
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
        // Written this way for C# conversion
        if (step > 0) {
            return new AscendingRangeIterator(start, step, end);
        } else {
            return new DescendingRangeIterator(start, -step, end);
        }
    }

    /**
     * Get the n'th item in the sequence (starting from 0). This is defined for all
     * Values, but its real benefits come for a sequence Value stored extensionally
     * (or for a MemoClosure, once all the values have been read)
     */

    /*@Nullable*/
    @Override
    public IntegerValue itemAt(int n) {
        if (n < 0 || n >= getLength()) {
            return null;
        }
        return Int64Value.makeIntegerValue(start + (n * step));
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
        long newStart = this.start + Math.max(start, 0);
        long newEnd = newStart + ((long)length * step) - 1L;
        if (newEnd > end) {
            newEnd = end;
        }
        if (newEnd >= newStart) {
            return new IntegerRange(newStart, step, newEnd);
        } else {
            return EmptySequence.getInstance();
        }
    }

    /**
     * Get the length of the sequence
     */

    @Override
    public int getLength() {
        return (int) ((end - start) / step) + 1;
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
    public UnicodeString getCanonicalLexicalRepresentation() {
        return getUnicodeStringValue();
    }

    @Override
    public UnicodeString getUnicodeStringValue() {
        try {
            return SequenceTool.getStringValue(this);
        } catch (XPathException err) {
            throw new AssertionError(err);
        }
    }

    @Override
    public String getStringValue() {
        try {
            return SequenceTool.stringify(this);
        } catch (XPathException err) {
            throw new AssertionError(err);
        }
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
        return "(" + start + (step == 1 ? "" : (" by " + step)) + " to " + end + ")";
    }

    /**
     * Return a Java iterator over the atomic sequence.
     * @return an Iterator.
     */

    @Override
    public Iterator<AtomicValue> iterator() {
        return new IntegerRangeIterator(this);
    }

    private static class IntegerRangeIterator implements Iterator<AtomicValue> {
        private IntegerRange range;
        private long current;

        public IntegerRangeIterator(IntegerRange range) {
            this.range = range;
            current = range.start;
        }

        /**
         * Returns <code>true</code> if the iteration has more elements. (In other
         * words, returns <code>true</code> if <code>next</code> would return an element
         * rather than throwing an exception.)
         *
         * @return <code>true</code> if the iterator has more elements.
         */
        @Override
        public boolean hasNext() {
            return current <= range.end;
        }

        /**
         * Removes from the underlying collection the last element returned by the
         * iterator (optional operation).  This method can be called only once per
         * call to <code>next</code>.  The behavior of an iterator is unspecified if
         * the underlying collection is modified while the iteration is in
         * progress in any way other than by calling this method.
         *
         * @throws UnsupportedOperationException if the <code>remove</code>
         *                                       operation is not supported by this Iterator.
         * @throws IllegalStateException         if the <code>next</code> method has not
         *                                       yet been called, or the <code>remove</code> method has already
         *                                       been called after the last call to the <code>next</code>
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
        public AtomicValue next() {
            return new Int64Value(current++);

        }
    }
}

