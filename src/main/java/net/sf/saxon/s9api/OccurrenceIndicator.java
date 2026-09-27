////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.value.Cardinality;

/**
 * Represents one of the possible occurrence indicators in a SequenceType. The four standard values are
 * ONE (no occurrence indicator), ZERO_OR_ONE (?), ZERO_OR_MORE (*), ONE_OR_MORE (+). In addition the
 * value ZERO is supported: this is used only in the type empty-sequence() which matches an empty sequence
 * and nothing else.
 */
public enum OccurrenceIndicator {
    /**
     * Occurrence indicator for a sequence type that only allows zero-length sequences,
     * that is the type <code>empty-sequence()</code>. May be used with an item type.
     */
    ZERO,
    /**
     * Occurrence indicator for a sequence that is either empty or that contains a single item,
     * corresponding to "?" in XPath SequenceType syntax.
     */
    ZERO_OR_ONE,
    /**
     * Occurrence indicator for a sequence that allows any number of items,
     * corresponding to "*" in XPath SequenceType syntax.
     */
    ZERO_OR_MORE,
    /**
     * Occurrence indicator for a sequence that allows exactly one item,
     * corresponding to the absence of an occurrence indicator in XPath SequenceType syntax.
     */
    ONE,
    /**
     * Occurrence indicator for a sequence that allows one or more items,
     * corresponding to "+" in XPath SequenceType syntax.
     */
    ONE_OR_MORE;



    /**
     * Get the {@link OccurrenceIndicator} value corresponding to an internal cardinality property
     * @param cardinality the internal cardinality property, for example {@link StaticProperty#ALLOWS_ONE_OR_MORE}.
     * @return the corresponding {@link OccurrenceIndicator}
     */

    public static OccurrenceIndicator getOccurrenceIndicator(int cardinality) {
        return Cardinality.getOccurrenceIndicatorForCardinality(cardinality);
    }


    /**
     * Ask whether one occurrence indicator subsumes another. Specifically,
     * <code>A.subsumes(B)</code> is true if every sequence that satisfies the occurrence
     * indicator B also satisfies the occurrence indicator A.
     *
     * @param other The other occurrence indicator
     * @return true if this occurrence indicator subsumes the other occurrence indicator
     * @since 9.1
     */

    public boolean subsumes(/*@NotNull*/ OccurrenceIndicator other) {
        return Cardinality.subsumes(Cardinality.staticPropertyFromOccurrenceIndicator(this), Cardinality.staticPropertyFromOccurrenceIndicator(other));
    }

    /**
     * Return a string representation of the occurrence indicator: one of "*", "+", "?", "0" (exactly zero)
     * or empty string (exactly one)
     * @return a string representation of the occurrence indicator
     * @since 9.5
     */

    @CSharpModifiers(code={"public", "override"})
    public String toString() {
        return switch (this) {
            case ZERO -> "0";
            case ZERO_OR_ONE -> "?";
            case ZERO_OR_MORE -> "*";
            case ONE -> "";
            case ONE_OR_MORE -> "+";
            default -> "!!!";
        };
    }


}

