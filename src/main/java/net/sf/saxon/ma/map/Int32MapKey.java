// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Int64Value;

/**
 * An Int32MapKey is the AtomicMatchKey used for all numeric values that are numerically equal
 * to some 32-bit signed integer, regardless of the primitive type of the actual numeric value.
 */

public class Int32MapKey implements AtomicMatchKey {

    private final int key;

    public Int32MapKey(int key) {
        this.key = key;
    }

    /**
     * Get an atomic value that encapsulates this match key. Needed to support the collation-key() function.
     *
     * @return an atomic value that encapsulates this match key. NB: this is NOT (necessarily) the atomic value
     * from which the {@code AtomicMatchKey} was derived.
     */
    @Override
    public AtomicValue asAtomic() {
        return new Int64Value(key);
    }

    /**
     * Indicates whether some other object is "equal to" this one. The algorithm for allocating map keys
     * is designed to ensure that a numeric value that is numerically equal to some 32-bit integer
     * is always represented by an instance of {@code Int32MapKey}, and therefore the keys can be
     * directly compared using an integer comparison.
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof Int32MapKey other && key == other.key;
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        return key;
    }
}

