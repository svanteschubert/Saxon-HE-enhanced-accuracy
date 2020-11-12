////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.value.Cardinality;

/**
 * Represents one of the possible occurrence indicators in a SequenceType. The four standard values are
 * ONE (no occurrence indicator), ZERO_OR_ONE (?), ZERO_OR_MORE (*), ONE_OR_MORE (+). In addition the
 * value ZERO is supported: this is used only in the type empty-sequence() which matches an empty sequence
 * and nothing else.
 */
public enum OccurrenceIndicator {
    ZERO, ZERO_OR_ONE, ZERO_OR_MORE, ONE, ONE_OR_MORE;

    protected int getCardinality() {
        switch (this) {
            case ZERO:
                return StaticProperty.EMPTY;
            case ZERO_OR_ONE:
                return StaticProperty.ALLOWS_ZERO_OR_ONE;
            case ZERO_OR_MORE:
                return StaticProperty.ALLOWS_ZERO_OR_MORE;
            case ONE:
                return StaticProperty.ALLOWS_ONE;
            case ONE_OR_MORE:
                return StaticProperty.ALLOWS_ONE_OR_MORE;
            default:
                return StaticProperty.EMPTY;
        }
    }

    protected static OccurrenceIndicator getOccurrenceIndicator(int cardinality) {
        switch (cardinality) {
            case StaticProperty.EMPTY:
                return ZERO;
            case StaticProperty.ALLOWS_ZERO_OR_ONE:
                return ZERO_OR_ONE;
            case StaticProperty.ALLOWS_ZERO_OR_MORE:
                return ZERO_OR_MORE;
            case StaticProperty.ALLOWS_ONE:
                return ONE;
            case StaticProperty.ALLOWS_ONE_OR_MORE:
                return ONE_OR_MORE;
            default:
                return ZERO_OR_MORE;
        }
    }

    /**
     * Ask whether this occurrence indicator permits an empty sequence.
     *
     * @return true if the occurrence indicator is one of {@link #ZERO}, {@link #ZERO_OR_ONE},
     *         or {@link #ZERO_OR_MORE}
     * @since 9.2
     */

    public boolean allowsZero() {
        return Cardinality.allowsZero(getCardinality());
    }

    /**
     * Ask whether this occurrence indicator permits a sequence containing more than one item.
     *
     * @return true if the occurrence indicator is one of {@link #ZERO_OR_MORE} or {@link #ONE_OR_MORE}
     * @since 9.2
     */

    public boolean allowsMany() {
        return Cardinality.allowsMany(getCardinality());
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
        return Cardinality.subsumes(getCardinality(), other.getCardinality());
    }

    /**
     * Return a string representation of the occurrence indicator: one of "*", "+", "?", "0" (exactly zero)
     * or empty string (exactly one)
     * @return a string representation of the occurrence indicator
     * @since 9.5
     */

    public String toString() {
        switch (this) {
            case ZERO:
                return "0";
            case ZERO_OR_ONE:
                return "?";
            case ZERO_OR_MORE:
                return "*";
            case ONE:
                return "";
            case ONE_OR_MORE:
                return "+";
            default:
                return "!!!";
        }
    }

}

