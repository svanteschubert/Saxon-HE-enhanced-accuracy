////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.transpile.CSharpTypeBounds;
import net.sf.saxon.z.IntHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * A map implementation using a custom equality matcher, to avoid having to wrap
 * each entry in a wrapper object to achieve customised equality testing.
 *
 * <p>Note: this doesn't implement {@code Map<K, V>} to save the effort of implementing
 * methods like size() and iterator() that aren't required for our use cases. These
 * methods could easily be added without changing the basic design.
 * </p>
 */

@CSharpTypeBounds(bounds = {"V:class"})
public class CustomMap<K, V>  {

    private final IntHashMap<CustomMapEntryChain<K, V>> buckets;
    private final EqualityMatcher<K> equalityMatcher;

    public CustomMap(EqualityMatcher<K> matcher) {
        buckets = new IntHashMap<>();
        equalityMatcher = matcher;
    }

    /**
     * Add an entry to the map
     * @param key the new key
     * @param value the value to be added.
     * @return the previous value for the key if there was one, or null otherwise
     */
    public V put(K key, V value) {
        int h = equalityMatcher.hash(key);
        CustomMapEntryChain<K, V> bucket = buckets.get(h);
        if (bucket == null) {
            CustomMapEntryChain<K, V> entry = new CustomMapEntryChain<>(key, value);
            buckets.put(h, entry);
            return null;
        } else {
            while (true) {
                if (equalityMatcher.equal(bucket.key, key)) {
                    V existing = bucket.value;
                    bucket.value = value;
                    return existing;
                } else if (bucket.next == null) {
                    @SuppressWarnings("UnnecessaryLocalVariable")
                    CustomMapEntryChain<K, V> entry = new CustomMapEntryChain<>(key, value);
                    bucket.next = entry;
                    return null;
                } else {
                    bucket = bucket.next;
                }
            }
        }
    }

    /**
     * Get the value associated with a given key, or null if absemt
     * @param key   the new key
     * @return the value for the key if there is one, or null otherwise
     */
    public V get(K key) {
        int h = equalityMatcher.hash(key);
        CustomMapEntryChain<K, V> bucket = buckets.get(h);
        if (bucket == null) {
            return null;
        } else {
            while (true) {
                if (equalityMatcher.equal(bucket.key, key)) {
                    return bucket.value;
                } else if (bucket.next == null) {
                    return null;
                } else {
                    bucket = bucket.next;
                }
            }
        }
    }

    /**
     * Ask if a key exists in the set of keys
     * @param key the requested key
     * @return true if it exists
     */

    public boolean containsKey(K key) {
        int h = equalityMatcher.hash(key);
        CustomMapEntryChain<K, V> bucket = buckets.get(h);
        if (bucket == null) {
            return false;
        } else {
            while (true) {
                if (equalityMatcher.equal(bucket.key, key)) {
                    return true;
                } else if (bucket.next == null) {
                    return false;
                } else {
                    bucket = bucket.next;
                }
            }
        }
    }

    /**
     * List all the keys.
     * @return a list of all the keys
     */
    public List<K> keys() {
        List<K> result = new ArrayList<>();
        for (CustomMapEntryChain<K, V> entry : buckets.valueSet()) {
            CustomMapEntryChain<K, V> e = entry;
            do {
                result.add(e.key);
                e = e.next;
            } while (e != null);
        }
        return result;
    }

    /**
     * List all the values.
     * @return a list of all the values
     */
    public List<V> values() {
        List<V> result = new ArrayList<>();
        for (CustomMapEntryChain<K, V> entry : buckets.valueSet()) {
            CustomMapEntryChain<K, V> e = entry;
            do {
                result.add(e.value);
                e = e.next;
            } while (e != null);
        }
        return result;
    }




}

