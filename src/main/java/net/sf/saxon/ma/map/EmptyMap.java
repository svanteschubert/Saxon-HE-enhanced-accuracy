////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.om.AtomicArray;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.PlainType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;

import java.util.Collections;

/**
 * A map with no entries.
 *
 * <p>There are two empty maps, a 3.1 version and a 4.0 version. This is because maps constructed by adding entries
 * inherit the version of the original. The difference is that in a 3.1 map, hexBinary and base64Binary values are
 * always distinct, whereas in a 4.0 map, they may be duplicates.</p>
 */

public class EmptyMap extends MapItem {

    public final static EmptyMap INSTANCE_31 = new EmptyMap(31);
    public final static EmptyMap INSTANCE_40 = new EmptyMap(40);

    public static EmptyMap getInstance(int version) {
        return version >= 40 ? INSTANCE_40 : INSTANCE_31;
    }

    private EmptyMap(int specVersion) {
        setSpecVersion(specVersion);
    }

    /**
     * Get an entry from the Map
     *
     * @param key the value of the key
     * @return the value associated with the given key, or null if the key is not present in the map.
     */
    @Override
    public GroundedValue get(AtomicValue key) {
        return null;
    }

    /**
     * Get the size of the map
     *
     * @return the number of keys/entries present in this map
     */
    @Override
    public int size() {
        return 0;
    }

    /**
     * Ask whether the map is empty
     *
     * @return true if and only if the size of the map is zero
     */
    @Override
    public boolean isEmpty() {
        return true;
    }

    /**
     * Get the set of all key values in the map.
     *
     * @return a set containing all the key values present in the map, in unpredictable order
     */
    @Override
    public AtomicIterator keys() {
        return AtomicArray.EMPTY_ATOMIC_ARRAY.iterate();
    }

    /**
     * Get the set of all key-value pairs in the map
     *
     * @return an iterable containing all the key-value pairs
     */
    @Override
    public Iterable<KeyValuePair> keyValuePairs() {
        return Collections.emptySet();
    }

    /**
     * Create a new map containing the existing entries in the map plus an additional entry,
     * without modifying the original. If there is already an entry with the specified key,
     * this entry is replaced by the new entry.
     *
     * @param key   the key of the new entry
     * @param value the value associated with the new entry
     * @return the new map containing the additional entry
     */
    @Override
    public MapItem put(AtomicValue key, GroundedValue value) {
        return new SingleEntryMap(key, value, getSpecVersion());
    }

    /**
     * Remove an entry from the map
     *
     * @param key the key of the entry to be removed
     * @return a new map in which the requested entry has been removed; or this map
     * unchanged if the specified key was not present
     */
    @Override
    public MapItem remove(AtomicValue key) {
        return this;
    }

    /**
     * Ask whether the map conforms to a given map type
     *
     * @param keyType   the required keyType
     * @param valueType the required valueType
     * @return true if the map conforms to the required type
     */
    @Override
    public boolean conforms(PlainType keyType, SequenceType valueType) {
        return true;
    }

    /**
     * Get the type of the map. This method is used largely for diagnostics, to report
     * the type of a map when it differs from the required type.
     *
     * @param th the type hierarchy cache
     * @return the type of this map
     */
    @Override
    public ItemType getItemType(TypeHierarchy th) {
        return MapType.EMPTY_MAP_TYPE;
    }


    public String toString() {
        return "map{}";
    }
}

// Copyright (c) 2024-2026 Saxonica Limited
