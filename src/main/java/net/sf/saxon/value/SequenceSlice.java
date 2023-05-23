////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.util.FastStringBuffer;

import java.util.Iterator;
import java.util.List;


/**
 * A sequence value implemented extensionally. That is, this class represents a sequence
 * by allocating memory to each item in the sequence.
 */

public class SequenceSlice implements GroundedValue {
    private List<? extends Item> value;
    private int offset;
    private int length;

    /**
     * Construct an sequence from a slice of a list of items. Note, the list of items is used as is,
     * which means the caller must not subsequently change its contents.
     *
     * @param value the list of items
     * @param offset the zero-based position of the first item to be included in the sequence
     * @param length the number of items to be included in the sequence; if this exceeds the number
     *               of items available for inclusion, include all items up to the end of the sequence
     * @throws IndexOutOfBoundsException if offset &lt; 0 or length &lt; 0 or offset + length &lt; value.size()
     */

    public SequenceSlice(List<? extends Item> value, int offset, int length) {
        this.value = value;
        this.offset = offset;
        if (offset < 0 || length < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (length > value.size() || offset + length > value.size()) { // carefully written to handle length=Integer.MAX_VALUE
            this.length = value.size() - offset;
        } else {
            this.length = length;
        }
    }

    @Override
    public String getStringValue() throws XPathException {
        return SequenceTool.getStringValue(this);
    }

    @Override
    public CharSequence getStringValueCS() throws XPathException {
        return SequenceTool.getStringValue(this);
    }

    /**
     * Get the first item in the sequence.
     *
     * @return the first item in the sequence if there is one, or null if the sequence
     *         is empty
     */
    @Override
    public Item head() {
        return itemAt(0);
    }

    /**
     * Get the number of items in the sequence
     *
     * @return the number of items in the sequence
     */

    @Override
    public int getLength() {
        return length;
    }

    /**
     * Determine the cardinality
     *
     * @return the cardinality of the sequence, using the constants defined in
     *         net.sf.saxon.value.Cardinality
     * @see Cardinality
     */

    public int getCardinality() {
        switch (getLength()) {
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
    public Item itemAt(int n) {
        if (n < 0 || n >= getLength()) {
            return null;
        } else {
            return value.get(n + offset);
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
    public ListIterator<? extends Item> iterate() {
        return new ListIterator<>(value.subList(offset, offset+length));
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
        int newStart = start+offset;
        if (newStart > value.size()) {
            return EmptySequence.getInstance();
        }
        if (length < 0) {
            return EmptySequence.getInstance();
        }
        int newLength = Integer.min(length, this.length);
        if (newStart + newLength > value.size()) {
            newLength = value.size() - newStart;
        }
        switch (newLength) {
            case 0:
                return EmptySequence.getInstance();
            case 1:
                return value.get(newStart);
            default:
                return new SequenceSlice(value, newStart, newLength);
        }
    }

    /*@NotNull*/
    public String toString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);
        for (int i = 0; i < getLength(); i++) {
            fsb.append(i == 0 ? "(" : ", ");
            fsb.append(itemAt(i).toString());
        }
        fsb.cat(')');
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
     * Get the contents of this value in the form of a Java {@link Iterable},
     * so that it can be used in a for-each expression
     * @return an Iterable containing the same sequence of items
     */

    @Override
    public Iterable<? extends Item> asIterable() {
        return value.subList(offset, offset+length);
    }

    /**
     * Get an iterator (a Java {@link Iterator}) over the items in this sequence.
     * @return an iterator over the items in this sequence.
     */

    public Iterator<? extends Item> iterator() {
        return value.subList(offset, offset + length).iterator();
    }
}

