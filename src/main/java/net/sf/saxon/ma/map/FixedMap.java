// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.value.AtomicValue;

import java.util.HashMap;

/**
 * A general-purpose XDM map implementation that is ordered, that holds any kind of key,
 * and that supports incremental modification only by copying the whole map to a different
 * structure.
 *
 * <p>The implementation is based on an array of keys, an array of corresponding values,
 * and a mutable Java {@code java.util.HashMap} (but encapsulated
 * so no mutation can take place after the map has been built) that maps atomic match keys
 * to integer positions in the array. If put or remove operations
 * are subsequently attempted, the map is copied to a different implementation structure
 * that supports functional modification.</p>
 *
 * <p>The underlying implementation uses an instance of {@code HashMap<AtomicMatchKey, KeyValuePair>}
 * For many data types this means that the key is held twice, once as the key, and once as part
 * of the value. The reason for this is that the <code>keys()</code> function needs to return
 * the original keys as supplied, complete with type annotation, which would not (always) have
 * the same equality semantics. The {@code AtomicMatchKey} is computed from the actual key
 * and in some cases is simply a wrapper with a different <code>equals()</code> method.</p>
 */

public class FixedMap extends AbstractFixedMap {

    private final AtomicValue[] keyArray;

    @Override
    protected AtomicValue getKey(int position) {
        return keyArray[position];
    }

    /**
     * Construct a {@code FixedMap} supplying the entries as an array
     * of keys and a corresponding array of value. The caller is responsible
     * for ensuring that there are no duplicate keys. The two arrays (of keys and values)
     * must have the same length. The content of the arrays must not be subsequently
     * modified.
     *
     * @param keys   an array of keys
     * @param values a corresponding array of values
     * @param index  a hash map indexing the values
     */

    protected FixedMap(AtomicValue[] keys, GroundedValue[] values, HashMap<AtomicMatchKey, Integer> index, int specVersion) {
        assert keys.length == values.length;
        this.keyArray = keys;
        this.values = values;
        this.index = index;
        setSpecVersion(specVersion);
    }




}


