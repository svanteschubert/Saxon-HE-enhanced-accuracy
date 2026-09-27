// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;

import java.util.Iterator;
import java.util.function.Supplier;

/**
 * LazyString implements GroundedValue so it can be used as an entry in a map; however,
 * despite the name, it is actually computed on demand. The value is always a single String.
 * The reason it implements GroundedValue but not Item is that there's a tendency for code
 * to assume that <code>item instanceof AtomicValue</code> will always be true if <code>item</code>
 * is atomic.
 *
 * <p>The class is currently used for the stack trace property in the map available in a catch block.
 * This computed lazily because it's potentially a large string and expensive to construct, and it's likely
 * that most catch blocks aren't interested in it.</p>
 */
public class LazyString implements GroundedValue {

    private final Supplier<String> supplier;
    private StringValue value;

    public LazyString(Supplier<String> supplier) {
        this.supplier = supplier;
    }

    private synchronized void build() {
        if (value == null) {
            String str = supplier.get();
            value = new StringValue(str);
        }
    }

    /**
     * Get an iterator over all the items in the sequence. This differs from the superclass method
     * in not allowing an exception, either during this method call, or in the subsequent processing
     * of the returned iterator.
     *
     * @return an iterator (meaning a Saxon {@link SequenceIterator} rather than a Java
     * {@link Iterator}) over all the items in this Sequence.
     */
    @Override
    public SequenceIterator iterate() {
        build();
        return SingletonIterator.makeIterator(value);
    }

    /**
     * Get the n'th item in the value, counting from zero (0)
     *
     * @param n the index of the required item, with zero (0) representing the first item in the sequence
     * @return the n'th item if it exists, or null if the requested position is out of range
     */
    @Override
    public Item itemAt(int n) {
        if (n == 0) {
            build();
            return value;
        } else {
            return null;
        }
    }

    /**
     * Get the first item of the sequence. This differs from the parent interface in not allowing an exception
     *
     * @return the first item of the sequence, or null if the sequence is empty
     */
    @Override
    public Item head() {
        build();
        return value;
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
        if (start <= 0 && (start + length) > 0) {
            build();
            return value;
        } else {
            return EmptySequence.INSTANCE;
        }
    }

    /**
     * Get the size of the value (the number of items in the value, considered as a sequence)
     *
     * @return the number of items in the sequence. Note that for a single item, including a map or array,
     * the result is always 1 (one).
     */
    @Override
    public int getLength() {
        return 1;
    }

    /**
     * Get the string value of this sequence, as an instance of {@link UnicodeString}.
     * The string value of an item is the result of applying the XPath string()
     * function. The string value of a sequence is the space-separated result of applying the string-join() function
     * using a single space as the separator
     *
     * <p>The result of this method is always equivalent to the result of the {@link #getStringValue()} method.
     * Use this method in preference either (a) if you need to use the value in a context where a {@link UnicodeString}
     * is required, or (b) if the underlying value is held as a {@code UnicodeString}, or in a form that is readily
     * converted to a {@code UnicodeString}. This is typically the case (i) when the value is a text or element node
     * in a TinyTree, and (ii) when the value is a {@code StringItem}: that is, an atomic value of type
     * {@code xs:string}.</p>
     *
     * @return the string value of the sequence.
     * @throws XPathException if the sequence contains items that have no string value (for example, function items)
     */
    @Override
    public UnicodeString getUnicodeStringValue() throws XPathException {
        build();
        return value.getUnicodeStringValue();
    }

    /**
     * Get the string value of this sequence, as an instance of {@link String}.
     * The string value of an item is the result of applying the XPath string()
     * function. The string value of a sequence is the space-separated result of applying the string-join() function
     * using a single space as the separator.
     *
     * <p>The result of this method is always equivalent to the result of the {@link #getUnicodeStringValue()} method.
     * Use this method in preference either (a) if you need to use the value in a context where a {@link String}
     * is required, or (b) if the underlying value is held as a {@code String}, or in a form that is readily
     * converted to a {@code String}. This is typically the case (i) when the value is an attribute node
     * in a TinyTree, or (ii) any kind of node in a third-party tree model such as DOM.</p>
     *
     * @return the string value of the sequence.
     * @throws XPathException if the sequence contains items that have no string value (for example, function items)
     */
    @Override
    public String getStringValue() throws XPathException {
        build();
        return value.getStringValue();
    }
}

