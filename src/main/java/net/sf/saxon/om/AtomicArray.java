////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A sequence of atomic values, implemented using an underlying arrayList.
 * <p>Often used for representing the typed value of a list-valued node.</p>
 *
 * @since 9.5
 */
public class AtomicArray implements AtomicSequence {

    private static final List<AtomicValue> emptyAtomicList = Collections.emptyList();
    public static AtomicArray EMPTY_ATOMIC_ARRAY = new AtomicArray(emptyAtomicList);

    private final List<AtomicValue> content;

    /**
     * Create an AtomicArray over a supplied arrayList of atomic values
     *
     * @param content the supplied arrayList. The caller warrants that the contents of this array will not change.
     */

    public AtomicArray(List<AtomicValue> content) {
        this.content = content;
    }

    /**
     * Create an AtomicArray supplying the contents as an iterator
     *
     * @param iter the iterator that supplies the atomic values (which must be positioned
     *             at the start of the sequence, and which will be consumed by the method).
     * @throws XPathException     if evaluation of the SequenceIterator fails
     * @throws ClassCastException if any of the items returned by the SequenceIterator is not atomic
     */

    public AtomicArray(SequenceIterator iter) throws XPathException {
        ArrayList<AtomicValue> list = new ArrayList<>(10);
        SequenceTool.supply(iter, (ItemConsumer<? super Item>) item -> list.add((AtomicValue) item));
        content = list;
    }

    @Override
    public AtomicValue head() {
        return content.isEmpty() ? null : content.get(0);
    }

    @Override
    public AtomicIterator iterate() {
        //noinspection Convert2Diamond
        return new ListIterator.OfAtomic<AtomicValue>(content);
    }

    /**
     * Get the n'th item in the sequence (base-zero addressing)
     *
     * @param n the index of the required item, the first item being zero
     * @return the n'th item if n is in range, or null otherwise
     */

    @Override
    public AtomicValue itemAt(int n) {
        if (n >= 0 && n < content.size()) {
            return content.get(n);
        } else {
            return null;
        }
    }

    /**
     * Get the length of the sequence
     *
     * @return the number of items in the sequence
     */

    @Override
    public int getLength() {
        return content.size();
    }

    /**
     * Get a subsequence of this sequence
     *
     * @param start  the index of the first item to be included in the result, counting from zero.
     *               A negative value is taken as zero. If the value is beyond the end of the sequence, an empty
     *               sequence is returned
     * @param length the number of items to be included in the result. Specify Integer.MAX_VALUE to
     *               get the subsequence up to the end of the base sequence. If the value is negative, an empty sequence
     *               is returned. If the value goes off the end of the sequence, the result returns items up to the end
     *               of the sequence
     * @return the required subsequence
     */

    @Override
    public AtomicArray subsequence(int start, int length) {
        if (start < 0) {
            start = 0;
        }
        if (start + length > content.size()) {
            length = content.size() - start;
        }
        return new AtomicArray(content.subList(start, start + length));
    }

    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules.
     *
     * @return the canonical lexical representation if defined in XML Schema; otherwise, the result
     * of casting to string according to the XPath 2.0 rules
     */

    @Override
    public UnicodeString getCanonicalLexicalRepresentation() {
        return getUnicodeStringValue();
    }

    /**
     * Get the value of the item as a UnicodeString. This is in some cases more efficient than
     * the version of the method that returns a String.
     * @return the string value, as a UnicodeString
     */

    @Override
    public UnicodeString getUnicodeStringValue() {
        UnicodeBuilder ub = new UnicodeBuilder();
        boolean first = true;
        for (AtomicValue av : content) {
            if (!first) {
                ub.append(' ');
            } else {
                first = false;
            }
            ub.accept(av.getUnicodeStringValue());
        }
        return ub.toUnicodeString();
    }

    /**
     * Get the value of the item as a UnicodeString. This is in some cases more efficient than
     * the version of the method that returns a String.
     *
     * @return the string value, as a UnicodeString
     */

    @Override
    public String getStringValue() {
        StringBuilder sb = new StringBuilder(64);
        boolean first = true;
        for (AtomicValue av : content) {
            if (!first) {
                sb.append(' ');
            } else {
                first = false;
            }
            sb.append(av.getStringValue());
        }
        return sb.toString();
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
     * Returns a Java iterator over the atomic sequence.
     *
     * @return an Iterator.
     */

    @Override
    public Iterator<AtomicValue> iterator() {
        return content.iterator();
    }
}

