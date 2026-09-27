////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BinaryValue;
import net.sf.saxon.value.HexBinaryValue;

/**
 * A match key used when comparing binary values as defined in XPath 3.1,
 * which makes hexBinary and base64Binary non-comparable. This applies both
 * to comparison using "eq" and to comparison of keys in a map.
 */


public class BinaryMatchKey31 implements AtomicMatchKey {

    private final BinaryValue value;
    public BinaryMatchKey31(BinaryValue value) {
        this.value = value;
    }


    @Override
    public int hashCode() {
        return value.hashCode();
    }


    @Override
    public boolean equals(Object obj) {
        return obj instanceof BinaryMatchKey31
                && value.equals(((BinaryMatchKey31) obj).value)
                && value instanceof HexBinaryValue == ((BinaryMatchKey31) obj).value instanceof HexBinaryValue;
    }

    /**
     * Get an atomic value that encapsulates this match key. Needed to support the collation-key() function.
     *
     * @return an atomic value that encapsulates this match key. NB: this is NOT (necessarily) the atomic value
     * from which the {@code AtomicMatchKey} was derived.
     */
    @Override
    public AtomicValue asAtomic() {
        return value;
    }
}
