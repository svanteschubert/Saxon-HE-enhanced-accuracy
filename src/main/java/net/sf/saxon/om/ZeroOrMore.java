////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.functions.Reverse;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceExtent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * A sequence value implemented extensionally. That is, this class represents a sequence
 * by allocating memory to each item in the sequence.
 */

public class ZeroOrMore<T extends Item> implements GroundedValue, Iterable<T> {

    private final List<T> value;

    /**
     * Construct a SequenceExtent from a List. The members of the list must all
     * be Items
     *
     * @param list the list of items to be included in the sequence
     */

    public ZeroOrMore(List<T> list) {
        this.value = list;
    }

    protected List<T> getValue() {
        return value;
    }

    public static <T extends Item> ZeroOrMore<T> fromSequenceIterator(SequenceIterator iter) throws XPathException {
        List<T> list = new ArrayList<>();
        for (Item item; (item = iter.next()) != null; ) {
            list.add((T)item);
        }
        return new ZeroOrMore<>(list);
    }

    @Override
    public UnicodeString getUnicodeStringValue() throws XPathException {
        return SequenceTool.getStringValue(this);
    }

    @Override
    public String getStringValue() throws XPathException {
        return SequenceTool.stringify(this);
    }

    /**
     * Get the first item in the sequence.
     *
     * @return the first item in the sequence if there is one, or null if the sequence
     *         is empty
     */
    @Override
    public T head() {
        return itemAt(0);
    }

    /**
     * Get the number of items in the sequence
     *
     * @return the number of items in the sequence
     */

    @Override
    public int getLength() {
        return value.size();
    }

    /**
     * Determine the cardinality
     *
     * @return the cardinality of the sequence, using the constants defined in
     *         net.sf.saxon.value.Cardinality
     * @see Cardinality
     */

    public int getCardinality() {
        switch (value.size()) {
            case 0:
                return StaticProperty.EMPTY;
            case 1:
                return StaticProperty.EXACTLY_ONE;
            default:
                return StaticProperty.ALLOWS_ONE_OR_MORE;
        }
    }

    /**
     * Get the n'th item in the sequence (starting with 0 as the first item)
     *
     * @param n the position of the required item
     * @return the n'th item in the sequence, or null if the position is out of range
     */

    /*@Nullable*/
    @Override
    public T itemAt(int n) {
        if (n < 0 || n >= getLength()) {
            return null;
        } else {
            return value.get(n);
        }
    }

    /**
     * Return an iterator over this sequence.
     *
     * @return the required SequenceIterator, positioned at the start of the
     *         sequence
     */

    /*@NotNull*/
    @Override
    public ListIterator.Of<T> iterate() {
        return new ListIterator.Of<T>(value);
    }

    /**
     * Return an enumeration of this sequence in reverse order (used for reverse axes)
     *
     * @return an AxisIterator that processes the items in reverse order
     */

    /*@NotNull*/
    public SequenceIterator reverseIterate() {
        return Reverse.reverseIterator(value);
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
            Item first = value.get(0);
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
        if (start < 0) {
            start = 0;
        }
        if (start > value.size()) {
            return EmptySequence.getInstance();
        }
        return new SequenceExtent.Of<T>(value.subList(start, start+length)).reduce();
    }

    /*@NotNull*/
    public String toString() {
        StringBuilder fsb = new StringBuilder(64);
        for (int i = 0; i < value.size(); i++) {
            fsb.append(i == 0 ? "(" : ", ");
            fsb.append(value.get(i).toString());
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
    public GroundedValue reduce() {
        int len = getLength();
        if (len == 0) {
            return EmptySequence.getInstance();
        } else if (len == 1) {
            return itemAt(0);
        } else {
            return this;
        }
    }

    /**
     * Get an iterator (a Java {@link Iterator}) over the items in this sequence.
     * @return an iterator over the items in this sequence.
     */

    //@Override
    public Iterator<T> iterator() {
        return value.iterator();
    }
}

