// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.value.NumericValue;

public class TransitiveNumericComparable implements XPathComparable {

    private final NumericValue value;

    public TransitiveNumericComparable(NumericValue value) {
        this.value = value;
    }

    /**
     * Compares this object with the specified object for order.  Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.
     */

    @Override
    public int compareTo(XPathComparable o) {
        if (o instanceof TransitiveNumericComparable) {
            return value.transitiveCompareTo(((TransitiveNumericComparable)o).value);
        }
        throw new ClassCastException("Cannot compare numeric and non-numeric values");
    }
}

