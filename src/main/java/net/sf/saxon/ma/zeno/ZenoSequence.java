////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.zeno;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.StringConstants;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.value.EmptySequence;

import java.util.Iterator;
import java.util.List;

/**
 * An immutable XDM sequence implemented as a ZenoChain.
 *
 * <p>This sequence implementation </p>
 */

public class ZenoSequence implements GroundedValue {

    private final ZenoChain<Item> chain;

    /**
     * Construct an empty ZenoSequence
     */

    public ZenoSequence() {
        chain = new ZenoChain<>();
    }

    /**
     * Construct a ZenoSequence from a list of items
     * @param items the items to be included
     * @return the new ZenoSequence
     */

    public static ZenoSequence fromList(List<Item> items) {
        ZenoChain<Item> chain = new ZenoChain<Item>().addAll(items);
        return new ZenoSequence(chain);
    }

    /**
     * Constructor to create a ZenoSequence that wraps a supplied ZenoChain
     * @param chain the supplied ZenoChain
     */

    public ZenoSequence(ZenoChain<Item> chain) {
        this.chain = chain;
    }

    /**
     * Get an iterator over the items in the sequence
     * @return an iterator over the items in the sequence
     */

    @Override
    public SequenceIterator iterate() {
        return new ZenoSequenceIterator(this);
    }

    /**
     * Get the item at a given position in the sequence
     * @param n the index of the required item, with zero (0) representing the first item in the sequence
     * @return the item at the given position, or null if the position is out of range
     */

    @Override
    public Item itemAt(int n) {
        try {
            return chain.get(n);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Get the first item in the sequence
     * @return the first item in the sequence, or null if the sequence is empty
     */

    @Override
    public Item head() {
        return chain.isEmpty() ? null : chain.get(0);
    }

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
    @Override
    public GroundedValue subsequence(int start, int length) {
        if (start < 0) {
            start = 0;
        }
        int size = chain.size();
        if (start >= size || length <= 0) {
            return EmptySequence.getInstance();
        }
        if ((long)start + (long)length > (long)size) {
            length = size - start;
        }
        if (length == 1) {
            return itemAt(start);
        } else {
            return new ZenoSequence(chain.subList(start, start + length));
        }
    }

    /**
     * Get the length of the sequence
     * @return the number of items in the sequence
     */

    @Override
    public int getLength() {
        return chain.size();
    }

    /**
     * Get a string representation of the sequence, consisting of the space-separated concatenation
     * of the string representation of the items in the sequence
     * @return a Unicode string containing the result of getting the string value of each item
     * in the sequence, space-separated
     * @throws XPathException if there is an item in the sequence with no string value, for
     * example a map
     */

    @Override
    public UnicodeString getUnicodeStringValue() throws XPathException {
        switch (getLength()) {
            case 0:
                return EmptyUnicodeString.getInstance();
            case 1:
                return this.head().getUnicodeStringValue();
            default:
                UnicodeBuilder builder = new UnicodeBuilder();
                UnicodeString separator = EmptyUnicodeString.getInstance();
                for (Item item : chain) {
                    builder.append(separator);
                    separator = StringConstants.SINGLE_SPACE;
                    builder.append(item.getStringValue());
                }
                return builder.toUnicodeString();
        }
    }

    /**
     * Get a string representation of the sequence, consisting of the space-separated concatenation
     * of the string representation of the items in the sequence
     *
     * @return a Unicode string containing the result of getting the string value of each item
     * in the sequence, space-separated
     * @throws XPathException if there is an item in the sequence with no string value, for
     *                        example a map
     */

    @Override
    public String getStringValue() throws XPathException {
        switch (getLength()) {
            case 0:
                return "";
            case 1:
                return this.head().getStringValue();
            default:
                StringBuilder builder = new StringBuilder();
                String separator = "";
                for (Item item : chain) {
                    builder.append(separator);
                    separator = " ";
                    builder.append(item.getStringValue());
                }
                return builder.toString();
        }
    }

    /**
     * Append a single item to the end of the sequence
     * @param item the item to be appended
     * @return a new sequence containing the additional item. The original sequence is unchanged.
     */

    public ZenoSequence append(Item item) {
        return new ZenoSequence(chain.add(item));
    }

    /**
     * Append a sequence of item to the end of the sequence
     *
     * @param items the items to be appended
     * @return a new sequence containing the additional items. The original sequence is unchanged.
     */

    public ZenoSequence appendSequence(GroundedValue items) {
        if (chain.isEmpty() && items instanceof ZenoSequence) {
            return (ZenoSequence)items;
        }
        switch (items.getLength()) {
            case 0:
                return this;
            case 1:
                Item item = items.head();
                return new ZenoSequence(chain.add(item));
            default:
                if (items instanceof ZenoSequence) {
                    return new ZenoSequence(chain.concat(((ZenoSequence)items).chain));
                } else {
                    return new ZenoSequence(chain.addAll(items.asIterable()));
                }
        }
    }

    /**
     * Create a ZenoSequence by concatenating a number of sequences
     * @param segments the sequences to be concatenated
     * @return the result of the concatenation
     */

    public static ZenoSequence join(List<GroundedValue> segments) {
        // Note: currently used only for testing
        ZenoChain<Item> list = new ZenoChain<>();
        for (GroundedValue val : segments) {
            if (val instanceof ZenoSequence) {
                list = list.concat(((ZenoSequence)val).chain);
            } else {
                for (Item item : val.asIterable()) {
                    list = list.add(item);
                }
            }
        }
        return new ZenoSequence(list);
    }

    /**
     * A SequenceIterator over a ZenoSequence
     */

    public static class ZenoSequenceIterator implements GroundedIterator, LastPositionFinder
            , LookaheadIterator
    {

        // This class is not a LookAheadIterator on C#, because the underlying C# enumerator has no side-effect-free
        // hasNext() operation.

        private final ZenoSequence sequence;
        private final Iterator<Item> chainIterator;
        private int position = 0;

        public ZenoSequenceIterator(ZenoSequence sequence) {
            this.sequence = sequence;
            this.chainIterator = sequence.chain.iterator();
        }

        @Override
        public Item next() {
            position++;
            return chainIterator.hasNext() ? chainIterator.next() : null;
        }

        @Override
        public boolean supportsGetLength() {
            return true;
        }

        @Override
        public int getLength() {
            return sequence.getLength();
        }

        @Override
        public boolean isActuallyGrounded() {
            return true;
        }

        @Override
        public GroundedValue getResidue() {
            return sequence.subsequence(position, Integer.MAX_VALUE);
        }

        @Override
        public GroundedValue materialize() {
            return sequence;
        }

        @Override
        @CSharpReplaceBody(code="return false;")
        public boolean supportsHasNext() {
            return true;
        }

        @Override
        @CSharpReplaceBody(code="throw new InvalidOperationException();")
        public boolean hasNext() {
            return chainIterator.hasNext();
        }
    }

}

