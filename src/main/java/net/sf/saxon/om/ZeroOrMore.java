////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * A value that is a sequence containing one or more items. The main use is in declarations of reflexive extension
 * functions, where declaring an argument of type &lt;ZeroOrMore&lt;IntegerValue&gt;&gt; triggers automatic type
 * checking in the same way as for a native XSLT/XQuery function declaring the type as xs:integer*.
 */

public class ZeroOrMore<T extends Item> implements GroundedValue, Iterable<T> {

    private List<T> content;

    /**
     * Create a sequence containing zero or one items
     *
     * @param content The content of the sequence
     */

    public ZeroOrMore(T[] content) {
        this.content = Arrays.asList(content);
    }

    public ZeroOrMore(List<T> content) {
        this.content = content;
    }

    public ZeroOrMore(SequenceIterator iter) throws XPathException {
        content = new ArrayList<>();
        iter.forEachOrFail(item -> content.add((T)item));
    }

    @Override
    public T head() {
        return content.isEmpty() ? null : content.get(0);
    }

    @Override
    public ListIterator<T> iterate() {
        return new ListIterator<>(content);
    }

    @Override
    public Iterator<T> iterator() {
        return content.iterator();
    }

    /**
     * Get the n'th item in the value, counting from 0
     *
     * @param n the index of the required item, with 0 representing the first item in the sequence
     * @return the n'th item if it exists, or null otherwise
     */
    @Override
    public T itemAt(int n) {
        if (n >= 0 && n < content.size()) {
            return content.get(n);
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
     * @return the required subsequence.
     */
    @Override
    public ZeroOrMore<T> subsequence(int start, int length) {
        if (start < 0) {
            start = 0;
        }
        if (start + length > content.size()) {
            length = content.size() - start;
        }
        return new ZeroOrMore<>(content.subList(start, start+ length));
    }

    /**
     * Get the size of the value (the number of items)
     *
     * @return the number of items in the sequence
     */
    @Override
    public int getLength() {
        return content.size();
    }

    /**
     * Get the effective boolean value of this sequence
     *
     * @return the effective boolean value
     * @throws net.sf.saxon.trans.XPathException
     *          if the sequence has no effective boolean value (for example a sequence of two integers)
     */
    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        return ExpressionTool.effectiveBooleanValue(iterate());
    }

    /**
     * Get the string value of this sequence. The string value of an item is the result of applying the string()
     * function. The string value of a sequence is the space-separated result of applying the string-join() function
     * using a single space as the separator
     *
     * @return the string value of the sequence.
     * @throws net.sf.saxon.trans.XPathException
     *          if the sequence contains items that have no string value (for example, function items)
     */
    @Override
    public String getStringValue() throws XPathException {
        return SequenceTool.getStringValue(this);
    }

    /**
     * Get the string value of this sequence. The string value of an item is the result of applying the string()
     * function. The string value of a sequence is the space-separated result of applying the string-join() function
     * using a single space as the separator
     *
     * @return the string value of the sequence.
     * @throws net.sf.saxon.trans.XPathException
     *          if the sequence contains items that have no string value (for example, function items)
     */
    @Override
    public CharSequence getStringValueCS() throws XPathException {
        return SequenceTool.getStringValue(this);
    }

    /**
     * Reduce the sequence to its simplest form. If the value is an empty sequence, the result will be
     * EmptySequence.getInstance(). If the value is a single atomic value, the result will be an instance
     * of {@link AtomicValue}. If the value is a single item of any other kind, the result will be an instance
     * of {@link One}. Otherwise, the result will typically be unchanged.
     *
     * @return the simplified sequence
     */
    @Override
    public GroundedValue reduce() {
        if (content.isEmpty()) {
            return EmptySequence.getInstance();
        } else if (content.size() == 1) {
            T first = content.get(0);
            if (first instanceof AtomicValue) {
                return first;
            } else {
                return new One<>(head());
            }
        }
        return this;
    }
}
