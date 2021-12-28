////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Base64BinaryValue;

import java.text.CollationKey;

/**
 * A match key for comparing strings under a collation. Wraps a Java CollationKey obtained
 * from the collation.
 */
public class CollationMatchKey implements AtomicMatchKey, Comparable {

    private CollationKey key;

    public CollationMatchKey(CollationKey key) {
        this.key = key;
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof CollationMatchKey) {
            return key.compareTo(((CollationMatchKey) o).key);
        } else {
            throw new ClassCastException();
        }
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CollationMatchKey && key.equals(((CollationMatchKey) o).key);
    }

    /**
     * Get an atomic value that encapsulates this match key. Needed to support the collation-key() function.
     *
     * @return an atomic value that encapsulates this match key
     */
    @Override
    public AtomicValue asAtomic() {
        return new Base64BinaryValue(key.toByteArray());
    }
}
