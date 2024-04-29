////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.functions.Reverse;
import net.sf.saxon.om.*;
import net.sf.saxon.str.*;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.tree.iter.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


/**
 * A sequence value implemented extensionally. That is, this class represents a sequence
 * by allocating memory to each item in the sequence.
 */

public abstract class SequenceExtent implements GroundedValue {

    /**
     * Construct a sequence containing all the remaining items in a SequenceIterator.
     *
     * @param iter The supplied sequence of items. The returned sequence will contain all
     *             items delivered by repeated calls on next() on this iterator, and the
     *             iterator will be consumed by calling the method.
     * @return a sequence of the remaining items
     * @throws UncheckedXPathException if reading the items using the
     *                                           SequenceIterator raises an error
     */

    public static SequenceExtent.Of<Item> from(SequenceIterator iter) {
        List<Item> list = new ArrayList<>(!SequenceTool.supportsGetLength(iter)
                                                      ? 20
                                                      : ((LastPositionFinder) iter).getLength());
        for (Item item; (item = iter.next()) != null; ) {
            list.add(item);
        }
        return new SequenceExtent.Of<>(list);
    }


    /**
     * Factory method to make a GroundedValue holding the remaining contents of any SequenceIterator,
     * that is, the contents that have not yet been read
     *
     * @param iter a Sequence iterator that may or may not be consumed to deliver the items in the sequence.
     *             The iterator need not be positioned at the start.
     * @return a GroundedValue holding the items delivered by the SequenceIterator. If the
     * sequence is empty the result will be an instance of {@link EmptySequence}. If it is of length
     * one, the result will be an {@link Item}. In all other cases, it will be an instance of
     * {@link SequenceExtent}.
     * @throws UncheckedXPathException if an error occurs processing the values from
     *                                           the iterator.
     */

    public static GroundedValue makeResidue(SequenceIterator iter) {
        if (iter instanceof GroundedIterator && ((GroundedIterator)iter).isActuallyGrounded()) {
            return ((GroundedIterator) iter).getResidue();
        }
        SequenceExtent extent = from(iter);
        return extent.reduce();
    }

    /**
     * Factory method to make a Value holding the contents of any List of items
     *
     * @param input a List containing the items in the sequence. The caller guarantees that
     *              the list will not be subsequently modified.
     * @param <T> the type of items in the list
     * @return a grounded sequence holding the items in the list. If the
     * sequence is empty the result will be an instance of {@link EmptySequence}. If it is of length
     * one, the result will be an {@link Item}. In all other cases, it will be an instance of
     * {@link SequenceExtent}.
     */

    public static <T extends Item> GroundedValue makeSequenceExtent(/*@NotNull*/ List<T> input) {
        int len = input.size();
        if (len == 0) {
            return EmptySequence.getInstance();
        } else if (len == 1) {
            return input.get(0);
        } else {
            return new Of<>(input);
        }
    }

    public abstract SequenceIterator reverseIterate();


    public static class Of<T extends Item> extends SequenceExtent implements Iterable<T> {

        private List<T> items;

        /**
         * Construct a SequenceExtent from a List. The members of the list must all
         * be Items. The caller warrants that the list is effectively immutable.
         *
         * @param list the list of items to be included in the sequence
         */

        public Of(List<T> list) {
            this.items = list;
        }

        /**
         * Construct an sequence from an array of items. Note, the array of items is used as is,
         * which means the caller must not subsequently change its contents.
         *
         * @param items the array of items to be included in the sequence
         */

        public Of(T[] items) {
            this(Arrays.asList(items));
        }

        /**
         * Construct a SequenceExtent as a view of another SequenceExtent
         *
         * @param ext    The existing SequenceExtent
         * @param start  zero-based offset of the first item in the existing SequenceExtent
         *               that is to be included in the new SequenceExtent
         * @param length The number of items in the new SequenceExtent
         */

        public Of(Of<T> ext, int start, int length) {
            items = ext.items.subList(start, start+length);
        }

        /**
         * Return an iterator over this sequence.
         *
         * @return the required SequenceIterator, positioned at the start of the
         * sequence
         */

        /*@NotNull*/
        @Override
        @CSharpModifiers(code={"public", "override"})
        public ListIterator.Of<T> iterate() {
            return new ListIterator.Of<T>(items);
        }

        /**
         * Return an enumeration of this sequence in reverse order (used for reverse axes)
         *
         * @return an AxisIterator that processes the items in reverse order
         */

        /*@NotNull*/
        public SequenceIterator reverseIterate() {
            return Reverse.reverseIterator(items);
        }

        /**
         * Get the effective boolean value
         */

        @Override
        public boolean effectiveBooleanValue() throws XPathException {
            int len = getLength();
            if (len == 0) {
                return false;
            } else {
                Item first = items.get(0);
                if (first instanceof NodeInfo) {
                    return true;
                } else if (len == 1 && first instanceof AtomicValue) {
                    return first.effectiveBooleanValue();
                } else {
                    // this will fail - reuse the error messages
                    return ExpressionTool.effectiveBooleanValue(iterate());
                }
            }
        }

        /**
         * Get the n'th item in the value, counting from zero (0)
         *
         * @param n the index of the required item, with zero (0) representing the first item in the sequence
         * @return the n'th item if it exists, or null if the requested position is out of range
         */

        @Override
        @CSharpModifiers(code = {"public", "override"})
        public Item itemAt(int n) {
            if (n >= 0 && n < items.size()) {
                return items.get(n);
            } else {
                return null;
            }
        }

        @Override
        @CSharpModifiers(code = {"public", "override"})
        public Item head() {
            if (items.isEmpty()) {
                return null;
            } else {
                return items.get(0);
            }
        }

        @Override
        @CSharpModifiers(code = {"public", "override"})
        public int getLength() {
            return items.size();
        }

        @Override
        @CSharpModifiers(code = {"public", "override"})
        public UnicodeString getUnicodeStringValue() throws XPathException {
            switch (getLength()) {
                case 0:
                    return EmptyUnicodeString.getInstance();
                case 1:
                    return this.head().getUnicodeStringValue();
                default:
                    UnicodeBuilder builder = new UnicodeBuilder();
                    UnicodeString separator = EmptyUnicodeString.getInstance();
                    for (T item : items) {
                        builder.append(separator);
                        separator = StringConstants.SINGLE_SPACE;
                        builder.append(item.getStringValue());
                    }
                    return builder.toUnicodeString();
            }
        }

        @Override
        @CSharpModifiers(code = {"public", "override"})
        public String getStringValue() throws XPathException {
            switch (getLength()) {
                case 0:
                    return "";
                case 1:
                    return this.head().getStringValue();
                default:
                    StringBuilder builder = new StringBuilder();
                    String separator = "";
                    for (T item : items) {
                        builder.append(separator);
                        separator = " ";
                        builder.append(item.getStringValue());
                    }
                    return builder.toString();
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
        @CSharpModifiers(code = {"public", "override"})
        public GroundedValue subsequence(int start, int length) {
            if (start < 0) {
                start = 0;
            }
            int size = getLength();
            if (start > size) {
                return EmptySequence.getInstance();
            }
            int limit =  ((long)start + (long)length > size) ? size : start + length;
            return new SequenceExtent.Of<T>(items.subList(start, limit)).reduce();
        }

        /*@NotNull*/
        @CSharpModifiers(code = {"public", "override"})
        public String toString() {
            StringBuilder fsb = new StringBuilder(64);
            fsb.append('(');
            for (int i = 0; i < getLength(); i++) {
                fsb.append(i == 0 ? "" : ", ");
                fsb.append(items.get(i).toString());
            }
            fsb.append(')');
            return fsb.toString();
        }

        /**
         * Reduce the sequence to its simplest form. If the value is an empty sequence, the result will be
         * EmptySequence.getInstance(). If the value is a single atomic value, the result will be an instance
         * of AtomicValue. If the value is a single item of any other kind, the result will be an instance
         * of One. Otherwise, the result will typically be unchanged.
         *
         * @return the simplified sequence
         */
        @Override
        @CSharpModifiers(code = {"public", "override"})
        public GroundedValue reduce() {
            int len = getLength();
            if (len == 0) {
                return EmptySequence.getInstance();
            } else if (len == 1) {
                return this.itemAt(0);
            } else {
                return this;
            }
        }


        /**
         * Get an iterator (a Java {@link java.util.Iterator}) over the items in this sequence.
         *
         * @return an iterator over the items in this sequence.
         */

        //@Override
        @CSharpModifiers(code = {"public"})
        public Iterator<T> iterator() {
            return items.iterator();
        }
    }
}

