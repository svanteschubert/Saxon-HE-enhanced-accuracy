////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.value.AtomicValue;

/**
 * Interface representing a sequence of atomic values. This is often used to represent the
 * typed value of a node. In most cases the typed value of a node is a single atomic value,
 * so the class AtomicValue implements this interface.
 * <p>An AtomicSequence is always represented as a GroundedValue: that is, the entire sequence
 * is in memory, making operations such as {@link #itemAt(int)} and {@link #getLength()} possible.</p>
 */

public interface AtomicSequence extends GroundedValue, Iterable<AtomicValue> {

    /**
     * Get the first item in the sequence
     * @return the first item in the sequence, or null if the sequence is empty
     */

    @Override
    AtomicValue head();

    /**
     * Make an iterator over the items in the sequence
     * @return an iterator over the items in the sequence
     */

    @Override
    AtomicIterator iterate();

    /**
     * Get the Nth item in the sequence, zero-based
     * @param n the index of the required item, with 0 representing the first item in the sequence
     * @return the Nth item in the sequence, or null if the index is out of range
     */

    @Override
    AtomicValue itemAt(int n);

    /**
     * Get the length of the sequence
     * @return the number of items in the sequence
     */

    @Override
    int getLength();

    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules.
     *
     * @return the canonical lexical representation if defined in XML Schema; otherwise, the result
     *         of casting to string according to the XPath 2.0 rules
     */

    CharSequence getCanonicalLexicalRepresentation();

    /**
     * Get a Comparable value that implements the XML Schema ordering comparison semantics for this value.
     * The default implementation is written to compare sequences of atomic values.
     * This method is overridden for AtomicValue and its subclasses.
     * <p>In the case of data types that are partially ordered, the returned Comparable extends the standard
     * semantics of the compareTo() method by returning the value {@link SequenceTool#INDETERMINATE_ORDERING} when there
     * is no defined order relationship between two given values.</p>
     *
     * @return a Comparable that follows XML Schema comparison rules
     */

    Comparable<?> getSchemaComparable();

    /**
     * Get a string representation of the sequence. The is the space-separated concatenation of the result of
     * casting each of the items in the sequence to xs:string
     * @return a whitespace-separated concatenation of the string values of the items making up the sequence,
     * as a CharSequence.
     */

    @Override
    CharSequence getStringValueCS();

    /**
     * Get a string representation of the sequence. The is the space-separated concatenation of the result of
     * casting each of the items in the sequence to xs:string
     *
     * @return a whitespace-separated concatenation of the string values of the items making up the sequence,
     * as a String.
     */

    @Override
    String getStringValue();

}

