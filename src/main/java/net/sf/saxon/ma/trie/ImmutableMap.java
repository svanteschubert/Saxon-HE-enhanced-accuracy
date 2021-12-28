////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2012 Michael Froh.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.trie;

import java.util.Iterator;

// Original author: Michael Froh (published on Github). Released under MPL 2.0
// by Saxonica Limited with permission from the author


public interface ImmutableMap<K, V> extends Iterable<Tuple2<K, V>>{
    /**
     * Add a new entry to the map. If an entry already exists with the
     * given key, the returned map will contain this entry, but not the
     * existing entry.
     *
     * @param key   the key to use to retrieve this item
     * @param value the value stored for this item
     * @return a new map with this item added
     */
    ImmutableMap<K, V> put(K key, V value);

    /**
     * Remove an entry from the map. If no entry exists with the given
     * key, the returned map will have identical contents to the original
     * map (and may, in fact, be the original map itself).
     *
     * @param key the key for the entry to remove
     * @return a new map with the entry with the given key removed (or
     *         a map with the original contents if no entry was found
     *         for the given key).
     */
    ImmutableMap<K, V> remove(K key);

    /**
     * Retrieve a stored value from the map based on the key for the
     * associated entry. If no entry exists with the given key, we
     * return None.
     *
     * @param key the key for the entry to retrieve
     * @return Some(value) if an entry exists with the given key, or
     *         None if no entry with the given key was found.
     */
    V get(K key);

    /**
     * Iterate over the entries in the map
     * @return an iterator over the key-value pairs held in the map
     */

    @Override
    Iterator<Tuple2<K, V>> iterator();
}
