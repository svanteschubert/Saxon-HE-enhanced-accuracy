////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An LRU cache, based on <code>LinkedHashMap</code>.
 * Synthesized and simplified from various published examples of the genre.
 * The methods are not synchronized.
 */
public class LRUCache<K, V> {

    private Map<K, V> map;

    /**
     * Creates a new LRU cache.
     *
     * @param cacheSize the maximum number of entries that will be kept in this cache.
     */
    public LRUCache(final int cacheSize) {
        this(cacheSize, false);
    }

    /**
     * Creates a new LRU cache, with the option of making it thread-safe
     *
     * @param cacheSize  the maximum number of entries that will be kept in this cache.
     * @param concurrent set to true if concurrent access is required, so that access will
     *                   be synchronized
     */
    public LRUCache(final int cacheSize, boolean concurrent) {
        map = new LinkedHashMap<K, V>(cacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return cacheSize < super.size();
            }
        };
        if (concurrent) {
            map = Collections.synchronizedMap(map);
        }
    }

    /**
     * Retrieves an entry from the cache.<br>
     * The retrieved entry becomes the most recently used entry.
     *
     * @param key the key whose associated value is to be returned.
     * @return the value associated to this key, or null if no value with this key exists in the cache.
     */
    public V get(K key) {
        return map.get(key);
    }

    /**
     * Adds an entry to this cache.
     * If the cache is full, the LRU (least recently used) entry is dropped.
     *
     * @param key   the key with which the specified value is to be associated.
     * @param value a value to be associated with the specified key.
     */
    public void put(K key, V value) {
        map.put(key, value);
    }

    /**
     * Clear the cache
     */
    public void clear() {
        map.clear();
    }

    /**
     * Get the number of entries in the cache
     *
     * @return the number of entries
     */

    public int size() {
        return map.size();
    }


}
