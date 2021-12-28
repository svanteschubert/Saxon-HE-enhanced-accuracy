////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.*;

/**
 * A simple implementation of MapItem where the strings are keys, and modification is unlikely.
 * This implementation is used in a number of cases where it can be determined that it is suitable,
 * for example when parsing JSON input, or when creating a fixed map to use in an options argument.
 */

public class DictionaryMap implements MapItem {

    private HashMap<String, GroundedValue> hashMap;

    /**
     * Create an empty dictionary, to which entries can be added using {@link #initialPut(String, GroundedValue)},
     * provided this is done before the map is exposed to the outside world.
     */

    public DictionaryMap() {
        hashMap = new HashMap<>();
    }

    /**
     * During initial construction of the map, add a key-value pair
     */

    public void initialPut(String key, GroundedValue value) {
        hashMap.put(key, value);
    }

    /**
     * During initial construction of the map, append a value to a possibly existing key-value pair
     */

    public void initialAppend(String key, GroundedValue value) {
        GroundedValue existingValue = hashMap.get(key);
        if (existingValue == null) {
            initialPut(key, value);
        } else {
            hashMap.put(key, existingValue.concatenate(value));
        }
    }

    /**
     * Get an entry from the Map
     *
     * @param key the value of the key
     * @return the value associated with the given key, or null if the key is not present in the map.
     */
    @Override
    public GroundedValue get(AtomicValue key) {
        if (key instanceof StringValue) {
            return hashMap.get(key.getStringValue());
        } else {
            return null;
        }
    }

    /**
     * Get the size of the map
     *
     * @return the number of keys/entries present in this map
     */
    @Override
    public int size() {
        return hashMap.size();
    }

    /**
     * Ask whether the map is empty
     *
     * @return true if and only if the size of the map is zero
     */
    @Override
    public boolean isEmpty() {
        return hashMap.isEmpty();
    }

    /**
     * Get the set of all key values in the map.
     *
     * @return a set containing all the key values present in the map, in unpredictable order
     */
    @Override
    public AtomicIterator<StringValue> keys() {
        Iterator<String> base = hashMap.keySet().iterator();
        return () -> base.hasNext() ? new StringValue(base.next()) : null;
    }

    /**
     * Get the set of all key-value pairs in the map
     *
     * @return an iterable containing all the key-value pairs
     */
    @Override
    public Iterable<KeyValuePair> keyValuePairs() {
        List<KeyValuePair> pairs = new ArrayList<>();
        hashMap.forEach((k, v) -> pairs.add(new KeyValuePair(new StringValue(k), v)));
        return pairs;
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
        return toHashTrieMap().remove(key);
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
    public boolean conforms(AtomicType keyType, SequenceType valueType, TypeHierarchy th) {
        if (isEmpty()) {
            return true;
        }
        if (!(keyType == BuiltInAtomicType.STRING || keyType == BuiltInAtomicType.ANY_ATOMIC)) {
            return false;
        }
        if (valueType.equals(SequenceType.ANY_SEQUENCE)) {
            return true;
        }
        for (GroundedValue val : hashMap.values()) {
            try {
                if (!valueType.matches(val, th)) {
                    return false;
                }
            } catch (XPathException e) {
                throw new AssertionError(e); // cannot happen when value is grounded
            }
        }
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
        ItemType valueType = null;
        int valueCard = 0;
        // we need to test the entries individually
        AtomicIterator keyIter = keys();
        AtomicValue key;
        for (Map.Entry<String, GroundedValue> entry : hashMap.entrySet()) {
            GroundedValue val = entry.getValue();
            if (valueType == null) {
                valueType = SequenceTool.getItemType(val, th);
                valueCard = SequenceTool.getCardinality(val);
            } else {
                valueType = Type.getCommonSuperType(valueType, SequenceTool.getItemType(val, th), th);
                valueCard = Cardinality.union(valueCard, SequenceTool.getCardinality(val));
            }
        }
        if (valueType == null) {
            // empty map
            return MapType.EMPTY_MAP_TYPE;
        } else {
            return new MapType(BuiltInAtomicType.STRING, SequenceType.makeSequenceType(valueType, valueCard));
        }
    }

    /**
     * Get the lowest common item type of the keys in the map
     *
     * @return the most specific type to which all the keys belong. If the map is
     * empty, return UType.VOID
     */
    @Override
    public UType getKeyUType() {
        return hashMap.isEmpty() ? UType.VOID : UType.STRING;
    }

    /**
     * Convert to a HashTrieMap
     */

    private HashTrieMap toHashTrieMap() {
        //System.err.println("Dictionary rewrite!!!!");
        HashTrieMap target = new HashTrieMap();
        hashMap.forEach((k, v) -> target.initialPut(new StringValue(k), v));
        return target;
    }
}

