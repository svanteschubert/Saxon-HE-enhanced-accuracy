// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A variant of the {@link FixedMap} map implementation optimized for the case where
 * the keys are all instances of {@code xs:string}. This saves space because it avoids
 * the need (a) to wrap the {@link UnicodeString} values in a {@link StringValue}, and (b)
 * to store a type annotation (as this is always {@code xs:string}
 */
public class FixedMapWithStringKeys extends AbstractFixedMap {

    private final UnicodeString[] keyArray;

    @Override
    protected AtomicValue getKey(int position) {
        return new StringValue(keyArray[position]);
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

    protected FixedMapWithStringKeys(
            UnicodeString[] keys, GroundedValue[] values, int specVersion,
            HashMap<AtomicMatchKey, Integer> index) {
        assert keys.length == values.length;
        this.keyArray = keys;
        this.values = values;
        this.index = index;
        setSpecVersion(specVersion);
    }

    public static FixedMapWithStringKeys fromJavaMap(Map<UnicodeString, GroundedValue> map) {
        List<UnicodeString> keys = new ArrayList<>(map.size());
        List<GroundedValue> values = new ArrayList<>(map.size());
        for (Map.Entry<UnicodeString, GroundedValue> entry : map.entrySet()) {
            keys.add(entry.getKey());
            values.add(entry.getValue());
        }
        return new FixedMapWithStringKeys(keys.toArray(new UnicodeString[0]),
                                          values.toArray(new GroundedValue[0]),
                                          40,
                                          null);
    }

    /**
     * Get an entry from the map, supplying a {@link UnicodeString} as the key
     *
     * @param key the key
     * @return the relevant entry if present, or null if absent.
     */

    @Override
    public GroundedValue getU(UnicodeString key) {
        ensureIndexed();
        Integer offset = index.get(key);
        if (offset == null) {
            return null;
        }
        return values[offset];
    }

}


