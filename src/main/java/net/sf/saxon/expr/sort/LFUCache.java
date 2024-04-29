////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.transpile.CSharpTypeBounds;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A "Least Frequently Used" cache. When the cache grows too large, we discard
 * entries whose reference counters are below some threshold. This is cheaper
 * than an LRU (least recently used) algorithm, because access to the map
 * doesn't require any locking against concurrent activity.
 *
 * Unlike classic LFU algorithms where the cache size has a fixed upper bound,
 * we allow it to grow beyond the target size and then do a bulk deletion of
 * infrequently used entries. We delete all entries whose usage counter is below
 * some threshold, learning the best threshold to apply from experience of
 * previous reorganisations.
 *
 * There is no synchronization. The underlying map itself is thread-safe, but we
 * make no attempt to prevent two concurrent threads garbage collecting at the same
 * time: if this happens, no harm is caused, because it's only a cache and lost
 * entries will simply be recomputed. Equally, the incrementing of counters isn't
 * synchronized because they don't need to be exact.
 */
@CSharpTypeBounds(bounds={"V:class"})
public class LFUCache<K, V> {

    private final boolean concurrent;
    private int targetSize;
    private int retentionThreshold = 1;

    private Map<K, LFUCacheEntryWithCounter<V>> map;

    /**
     * Creates a new LCU cache, suitable for use only within a single thread.
     *
     * @param cacheSize the target number of entries to be kept in this cache.
     */
    public LFUCache(final int cacheSize) {
        this(cacheSize, false);
        targetSize = cacheSize;
    }

    /**
     * Creates a new LCU cache, with the option of making it thread-safe
     *
     * @param cacheSize  the maximum number of entries that will be kept in this cache.
     * @param concurrent set to true if concurrent access is required, so that the
     *                   underlying map will be thread-safe
     */
    public LFUCache(final int cacheSize, boolean concurrent) {
        this.concurrent = concurrent;
        targetSize = cacheSize;
        map = makeMap(cacheSize);
    }

    private Map<K, LFUCacheEntryWithCounter<V>> makeMap(int cacheSize) {
        if (concurrent) {
            return new ConcurrentHashMap<K, LFUCacheEntryWithCounter<V>>(cacheSize);
        } else {
            return new HashMap<K, LFUCacheEntryWithCounter<V>>(cacheSize);
        }
    }

    /**
     * Retrieves an entry from the cache.<br>
     * The usage count of the entry is incremented.
     *
     * @param key the key whose associated value is to be returned.
     * @return the value associated to this key, or null if no value with this key exists in the cache.
     */
    public V get(K key) {
        LFUCacheEntryWithCounter<V> entry = map.get(key);
        if (entry == null) {
            return null;
        } else {
            entry.counter++;
            return entry.value;
        }
    }

    /**
     * Ask whether a given key is present in the cache.<br>
     * The usage count of the entry is incremented if the entry exists.
     * @param key the key to be tested
     * @return true if the key is present
     */

    public boolean containsKey(K key) {
        LFUCacheEntryWithCounter<V> entry = map.get(key);
        if (entry == null) {
            return false;
        } else {
            entry.counter++;
            return true;
        }
    }

    /**
     * Adds an entry to this cache. It is assumed that the caller has
     * already checked that the cache doesn't already contain a suitable
     * entry, so we treat each entry as a new one.
     * If the cache is exceeds a threshold size of three times the target
     * size, it is rebuilt with infrequently used entries purged.
     *
     * @param key   the key with which the specified value is to be associated.
     * @param value a value to be associated with the specified key.
     */
    public void put(K key, V value) {
        map.put(key, new LFUCacheEntryWithCounter<V>(value));
        // Consider purging rarely-used entries
        if (map.size() > 3*targetSize) {
            rebuild();
        }
    }

    private void rebuild() {
        Map<K, LFUCacheEntryWithCounter<V>> m2 = makeMap(targetSize);
        int retained = 0;
        for (Map.Entry<K, LFUCacheEntryWithCounter<V>> entry : map.entrySet()) {
            if (entry.getValue().counter > retentionThreshold) {
                m2.put(entry.getKey(), new LFUCacheEntryWithCounter<V>(entry.getValue().value));
                retained++;
            }
        }
        // System.err.println("Rebuilt LCUCache; retained size = " + retained + " retention threshold = " + retentionThreshold);
        // Consider adjusting the threshold for next time
        if (retained > 1.5*targetSize) {
            // We retained too many entries, try to do better next time
            retentionThreshold++;
        } else if (retentionThreshold > 0 && retained < targetSize) {
            // We discarded too many entries, try to do better next time
            retentionThreshold--;
        }
        // Replace the map. Note this update isn't thread-safe; it doesn't matter if we lose it, or if some
        // other thread is doing the same thing concurrently.
        map = m2;
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
