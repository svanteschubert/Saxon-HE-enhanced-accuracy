// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;


/**
 * Builder class for constructing ordered maps, where the keys are all known in advance to have
 * a type annotation of xs:string (so we don't need to retain the type annotation). The
 * builder simply accumulates keys and values
 * until it is asked to construct and return a map with those keys and values. At that point it
 * checks for duplicate keys, and carries out various optimizations to work out what map implementation
 * class to use.
 */
public class StringMapBuilder {

    private final List<UnicodeString> keys;
    private final List<GroundedValue> values;
    private final int specVersion;

    /**
     * Callback function to combine two values (for duplicate keys) into one. If
     * null, duplicates are rejected as an error.
     */
    private MapFunctionSet.OnDuplicatesAction combiner;

    /**
     * XPath dynamic context for evaluating the combiner function. Needed if and only
     * if the combiner invokes a user-defined XDM function
     */

    private XPathContext context;

    public StringMapBuilder(int specVersion) {
        keys = new ArrayList<>();
        values = new ArrayList<>();
        this.specVersion = specVersion;
    }

    public StringMapBuilder(int size, int specVersion) {
        keys = new ArrayList<>(size);
        values = new ArrayList<>(size);
        this.specVersion = specVersion;
    }

    /**
     * Supply a combiner function for combining entries with duplicate keys. If no combiner
     * is set, duplicate keys result in a dynamic error.
     * @param combiner a function that will be called with two grounded values (representing
     *                 two values for the same key) and returns a single grounded value to
     *                 be used in the result map.
     */

    public void setCombiner(MapFunctionSet.OnDuplicatesAction combiner) {
        this.combiner = combiner;
    }

    /**
     * Supply a dynamic evaluation context for evaluating the combiner function. Needed if
     * and only if the combiner calls a user-defined XDM function
     * @param context the XPath evaluation context
     */

    public void setContext(XPathContext context) {
        this.context = context;
    }

    /**
     * Add a key-value pair
     *
     * @param key   the key
     * @param value the corresponding value
     */

    public void put(UnicodeString key, GroundedValue value) {
        keys.add(key);
        values.add(value);
    }

    /**
     * Get the constructed map. This method must only be called once, because the
     * lists of keys and values are used within the constructed map and are mutable.
     *
     * @return the constructed map
     * @throws XPathException if duplicate keys were found, and not detected earlier
     */

    public MapItem getCompletedMap() throws XPathException {
        if (keys.size() == 0) {
            return EmptyMap.getInstance(specVersion);
        } else if (keys.size() == 1) {
            return new SingleEntryMap(new StringValue(keys.get(0)), values.get(0), specVersion);
        }
        // quick scan for possible duplicates using a bloom filter
        boolean possibleDuplicates = hasPossibleDuplicates(keys);
        if (possibleDuplicates) {
            // If there are possible duplicates, then we build the index now, with a more thorough
            // check for duplicates as we build it.
            HashMap<AtomicMatchKey, Integer> index = new HashMap<>(keys.size());
            if (combiner == null) {
                UnicodeString[] keyArray = keys.toArray(new UnicodeString[0]);
                GroundedValue[] valueArray = values.toArray(new GroundedValue[0]);
                int ix = 0;
                for (int i = 0; i < keys.size(); i++) {
                    UnicodeString key = keyArray[i];
                    boolean isNew = AbstractFixedMap.putOffsetIfAbsent(index, key, ix);
                    if (!isNew) {
                        throw new XPathException("Duplicate key " + Err.wrap(key) + " in map", "FOJS0003");
                    }
                    ix++;
                }
                return new FixedMapWithStringKeys(keyArray, valueArray, specVersion, index);
            } else {
                ArrayList<UnicodeString> keys2 = new ArrayList<>();
                ArrayList<GroundedValue> values2 = new ArrayList<>();
                int offset = 0;
                for (int i = 0; i < keys.size(); i++) {
                    UnicodeString key = keys.get(i);
                    boolean isNew = AbstractFixedMap.putOffsetIfAbsent(index, key, offset);
                    if (isNew) {
                        offset++;
                        keys2.add(key);
                        values2.add(values.get(i));
                    } else {
                        int existing = AbstractFixedMap.getOffset(index, key);
                        GroundedValue newVal = combiner.combine(
                                values2.get(existing), values.get(i), context);
                        values2.set(existing, newVal);
                    }
                }
                UnicodeString[] keyArray = keys2.toArray(new UnicodeString[0]);
                GroundedValue[] valueArray = values2.toArray(new GroundedValue[0]);
                return new FixedMapWithStringKeys(keyArray, valueArray, specVersion, index);
            }
        } else {
            // if we are sure there are no duplicates, then we delay indexing the map entries
            // until a map lookup operation is performed. This is because many maps are built
            // and immediately discarded or serialized without ever being queried.

            return new FixedMapWithStringKeys(keys.toArray(new UnicodeString[0]),
                                              values.toArray(new GroundedValue[0]),
                                              specVersion, null);
        }


    }

    /**
     * Get the constructed map. This method must only be called once, because the
     * lists of keys and values are used within the constructed map and are mutable.
     * This version of the method does not check for duplicated keys, and must be used
     * only when the caller is 100% confident that there are no duplicates.
     *
     * @return the constructed map
     */

    public MapItem getCompletedMapConfidently() {
        if (keys.size() == 0) {
            return EmptyMap.getInstance(specVersion);
        } else if (keys.size() == 1) {
            return new SingleEntryMap(new StringValue(keys.get(0)), values.get(0), specVersion);
        }
        return new FixedMapWithStringKeys(keys.toArray(new UnicodeString[0]),
                                          values.toArray(new GroundedValue[0]),
                                          specVersion,
                                          null);
    }

    private static boolean hasPossibleDuplicates(List<UnicodeString> keys) {
        int filterSize = keys.size() < 50 ? 1024 : 65536;
        int mask = filterSize - 1;
        BitSet bloom = new BitSet(filterSize);
        for (UnicodeString key : keys) {
            long hash = key.longHashCode();
            int hash0 = (int) (hash >> 40 & mask);
            int hash1 = (int) (hash >> 20 & mask);
            int hash2 = (int) hash & mask;
            if (bloom.get(hash0) && bloom.get(hash1) && bloom.get(hash2)) {
                return true;
            }
            bloom.set(hash0);
            bloom.set(hash1);
            bloom.set(hash2);
        }
        return false;
    }

}

