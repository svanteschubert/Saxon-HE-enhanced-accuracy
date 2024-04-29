////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.iter.SingleAtomicIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;

/**
 * A key and a corresponding value to be held in a Map. A key-value pair also acts as a singleton
 * map in its own right.
 */

public class SingleEntryMap extends MapItem {
    public AtomicValue key;
    public GroundedValue value;

    public SingleEntryMap(AtomicValue key, GroundedValue value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Get the key
     * @return the key (of the single entry in this map)
     */

    public AtomicValue getKey() {
        return key;
    }

    /**
     * Get the value
     *
     * @return the value (of the single entry in this map)
     */

    public GroundedValue getValue() {
        return value;
    }

    /**
     * Get an entry from the Map
     *
     * @param key the value of the key
     * @return the value associated with the given key, or null if the key is not present in the map.
     */
    @Override
    public GroundedValue get(AtomicValue key) {
        return this.key.asMapKey().equals(key.asMapKey()) ? value : null;
    }

    /**
     * Get the size of the map
     *
     * @return the number of keys/entries present in this map
     */
    @Override
    public int size() {
        return 1;
    }

    /**
     * Ask whether the map is empty
     *
     * @return true if and only if the size of the map is zero
     */
    @Override
    public boolean isEmpty() {
        return false;
    }

    /**
     * Get the set of all key values in the map.
     *
     * @return a set containing all the key values present in the map, in unpredictable order
     */
    @Override
    public AtomicIterator keys() {
        return SingleAtomicIterator.makeIterator(key);
    }

    /**
     * Get the set of all key-value pairs in the map
     *
     * @return an iterable containing all the key-value pairs
     */
    @Override
    public Iterable<KeyValuePair> keyValuePairs() {
        List<KeyValuePair> list = new ArrayList<>(1);
        list.add(new KeyValuePair(key, value));
        return list;
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
    public MapItem addEntry(AtomicValue key, GroundedValue value) {
        return toHashTrieMap().addEntry(key, value);
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
        if (get(key) == null) {
            return this;
        } else {
            return new HashTrieMap();
        }
    }

    /**
     * Ask whether the map conforms to a given map type
     *
     * @param keyType   the required keyType
     * @param valueType the required valueType
     * @param th        the type hierarchy cache for the configuration
     * @return true if the map conforms to the required type
     */
    @Override
    public boolean conforms(PlainType keyType, SequenceType valueType, TypeHierarchy th) {
        return keyType.matches(key, th) && valueType.matches(value, th);
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
        return new MapType(key.getItemType(), SequenceType.makeSequenceType(
                SequenceTool.getItemType(value, th),
                SequenceTool.getCardinality(value)));
    }

    /**
     * Get the lowest common item type of the keys in the map
     *
     * @return the most specific type to which all the keys belong. If the map is
     * empty, return UType.VOID
     */
    @Override
    public UType getKeyUType() {
        return key.getUType();
    }

    /**
     * Convert to a HashTrieMap
     */

    private HashTrieMap toHashTrieMap() {
        HashTrieMap target = new HashTrieMap();
        target.initialPut(key, value);
        return target;
    }
}

// Copyright (c) 2010-2023 Saxonica Limited
