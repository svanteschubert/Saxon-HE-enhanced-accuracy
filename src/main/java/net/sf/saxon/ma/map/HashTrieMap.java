////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.ma.trie.ImmutableHashTrieMap;
import net.sf.saxon.ma.trie.ImmutableMap;
import net.sf.saxon.ma.trie.Tuple2;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.Iterator;

/**
 * An immutable map. This implementation, which uses a hash trie, was introduced in Saxon 9.6
 */
public class HashTrieMap implements MapItem {

    // The underlying trie holds key-value pairs, but these do not correspond directly
    // to the key value pairs in the XDM map. Instead, the key in the trie is an AtomicMatchKey
    // based on the XDM key, which allows equality matching to differ from the Java-level
    // equals method (to take account of collations, etc). The value in the trie is
    // actually a tuple holding both the real key, and the value.

    private ImmutableMap<AtomicMatchKey, KeyValuePair> imap;

    // The following values are maintained incrementally when
    // entries are added to the map. They are not changed when entries are removed,
    // so the actual type may be narrower than these values suggest. The purpose of
    // keeping this information is to try and avoid dynamic checking of the map
    // contents against a required type wherever possible. We therefore have
    // a guarantee that all entries in the map conform to the types maintained here;
    // but there is no guarantee that they do not also conform to some more specific
    // type.

    // The set of UTypes of keys in the map
    private UType keyUType = UType.VOID;

    // The set of UTypes of items in the values in the map
    private UType valueUType = UType.VOID;

    // The atomic type of all keys, if they are homogeneous; otherwise null
    private AtomicType keyAtomicType = ErrorType.getInstance();

    // The item type of all items in values, if they are homogeneous; otherwise null
    private ItemType valueItemType = ErrorType.getInstance();

    // The "envelope" cardinality of values in the map (0:1, 0:0, 1:1, 0:many, 1:many)
    private int valueCardinality = 0;

    // The number of entries in the map; -1 if unknown
    private int entries;

    /**
     * Create an empty map
     */

    public HashTrieMap() {
        this.imap = ImmutableHashTrieMap.empty();
        this.entries = 0;
    }


    /**
     * Create a singleton map with a single key and value
     * @param key   the key value
     * @param value the associated value
     * @return a singleton map
     */

    public static HashTrieMap singleton(AtomicValue key, GroundedValue value) {
        return new HashTrieMap().addEntry(key, value);
    }

    /**
     * Create a map whose contents are a copy of an existing immutable map
     * @param imap the map to be copied
     */

    public HashTrieMap(ImmutableMap<AtomicMatchKey, KeyValuePair> imap) {
        this.imap = imap;
        entries = -1;
    }

    /**
     * Create a map whose entries are copies of the entries in an existing MapItem
     * @param map the existing map to be copied
     * @return the new map
     */

    public static HashTrieMap copy(MapItem map) {
        if (map instanceof HashTrieMap) {
            return (HashTrieMap) map;
        }
        HashTrieMap m2 = new HashTrieMap();
        for (KeyValuePair pair : map.keyValuePairs()) {
            m2 = m2.addEntry(pair.key, pair.value);
        }
        return m2;
    }

    /**
     * After adding an entry to the map, update the cached type information
     * @param key      the new key
     * @param val      the new associated value
     * @param wasEmpty true if the map was empty before adding these values
     */

    private void updateTypeInformation(AtomicValue key, Sequence val, boolean wasEmpty) {
        if (wasEmpty) {
            keyUType = key.getUType();
            valueUType = SequenceTool.getUType(val);
            keyAtomicType = key.getItemType();
            valueItemType = MapItem.getItemTypeOfSequence(val);
            valueCardinality = SequenceTool.getCardinality(val);
        } else {
            keyUType = keyUType.union(key.getUType());
            valueUType = valueUType.union(SequenceTool.getUType(val));
            valueCardinality = Cardinality.union(valueCardinality, SequenceTool.getCardinality(val));
            if (key.getItemType() != keyAtomicType) {
                keyAtomicType = null;
            }
            if (!MapItem.isKnownToConform(val, valueItemType)) {
                valueItemType = null;
            }
        }
    }

    /**
     * Get the size of the map
     */

    @Override
    public int size() {
        if (entries >= 0) {
            return entries;
        }
        int count = 0;
        //noinspection UnusedDeclaration
        for (KeyValuePair entry : keyValuePairs()) {
            count++;
        }
        return entries = count;
    }

    /**
     * Ask whether the map is empty
     *
     * @return true if and only if the size of the map is zero
     */
    @Override
    public boolean isEmpty() {
        return entries == 0 || !imap.iterator().hasNext();
    }

    /**
     * Ask whether the map conforms to a given map type
     *
     * @param requiredKeyType   the required keyType
     * @param requiredValueType the required valueType
     * @param th                the type hierarchy cache for the configuration
     * @return true if the map conforms to the required type
     */
    @Override
    public boolean conforms(AtomicType requiredKeyType, SequenceType requiredValueType, TypeHierarchy th) {
        if (isEmpty()) {
            return true;
        }
        if (keyAtomicType == requiredKeyType && valueItemType == requiredValueType.getPrimaryType() &&
                Cardinality.subsumes(requiredValueType.getCardinality(), valueCardinality)) {
            return true;
        }
        boolean needFullCheck = false;
        if (requiredKeyType != BuiltInAtomicType.ANY_ATOMIC) {
            ItemType upperBoundKeyType = keyUType.toItemType();
            Affinity rel = th.relationship(requiredKeyType, upperBoundKeyType);
            if (rel == Affinity.SAME_TYPE || rel == Affinity.SUBSUMES) {
                // The key type is matched
            } else if (rel == Affinity.DISJOINT) {
                return false;
            } else {
                needFullCheck = true;
            }
        }
        ItemType requiredValueItemType = requiredValueType.getPrimaryType();
        if (requiredValueItemType != BuiltInAtomicType.ANY_ATOMIC) {
            ItemType upperBoundValueType = valueUType.toItemType();
            Affinity rel = th.relationship(requiredValueItemType, upperBoundValueType);
            if (rel == Affinity.SAME_TYPE || rel == Affinity.SUBSUMES) {
                // The value type is matched
            } else if (rel == Affinity.DISJOINT) {
                return false;
            } else {
                needFullCheck = true;
            }
        }
        int requiredValueCard = requiredValueType.getCardinality();
        if (!Cardinality.subsumes(requiredValueCard, valueCardinality)) {
            needFullCheck = true;
        }

        if (needFullCheck) {
            // we need to test the entries individually
            AtomicIterator<?> keyIter = keys();
            AtomicValue key;
            while ((key = keyIter.next()) != null) {
                if (!requiredKeyType.matches(key, th)) {
                    return false;
                }
                Sequence val = get(key);
                try {
                    if (!requiredValueType.matches(val, th)) {
                        return false;
                    }
                } catch (XPathException e) {
                    throw new AssertionError(e); // cannot happen with a grounded sequence
                }
            }
        }

        return true;

    }

    /**
     * Get the type of the map. This method is used largely for diagnostics, to report
     * the type of a map when it differs from the required type. This method has the side-effect
     * of updating the internal type information held within the map.
     *
     * @param th The type hierarchy cache for the configuration
     * @return the type of this map
     */
    @Override
    public MapType getItemType(TypeHierarchy th) {
        AtomicType keyType = null;
        ItemType valueType = null;
        int valueCard = 0;
        // we need to test the entries individually
        AtomicIterator<?> keyIter = keys();
        AtomicValue key;
        while ((key = keyIter.next()) != null) {
            Sequence val = get(key);
            if (keyType == null) {
                keyType = key.getItemType();
                valueType = SequenceTool.getItemType(val, th);
                valueCard = SequenceTool.getCardinality(val);
            } else {
                keyType = (AtomicType) Type.getCommonSuperType(keyType, key.getItemType(), th);
                valueType = Type.getCommonSuperType(valueType, SequenceTool.getItemType(val, th), th);
                valueCard = Cardinality.union(valueCard, SequenceTool.getCardinality(val));
            }
        }
        if (keyType == null) {
            // implies the map is empty
            this.keyUType = UType.VOID;
            this.valueUType = UType.VOID;
            this.valueCardinality = 0;
            return MapType.ANY_MAP_TYPE;
        } else {
            this.keyUType = keyType.getUType();
            this.valueUType = valueType.getUType();
            this.valueCardinality = valueCard;
            return new MapType(keyType, SequenceType.makeSequenceType(valueType, valueCard));
        }
    }

    /**
     * Get the lowest common item type of the keys in the map
     *
     * @return the most specific type to which all the keys belong. If the map is
     * empty, return UType.VOID (the type with no instances)
     */
    @Override
    public UType getKeyUType() {
        return keyUType;
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
    public HashTrieMap addEntry(AtomicValue key, GroundedValue value) {
        AtomicMatchKey amk = makeKey(key);
        boolean isNew = imap.get(amk) == null;
        boolean empty = isEmpty();
        ImmutableMap<AtomicMatchKey, KeyValuePair> imap2 = imap.put(amk, new KeyValuePair(key, value));
        HashTrieMap t2 = new HashTrieMap(imap2);
        t2.valueCardinality = this.valueCardinality;
        t2.keyUType = keyUType;
        t2.valueUType = valueUType;
        t2.keyAtomicType = keyAtomicType;
        t2.valueItemType = valueItemType;
        t2.updateTypeInformation(key, value, empty);
        if (entries >= 0) {
            t2.entries = isNew ? entries + 1 : entries;
        }
        return t2;
    }

    /**
     * Add a new entry to this map. Since the map is supposed to be immutable, this method
     * must only be called while initially populating the map, and must not be called if
     * anyone else might already be using the map.
     *
     * @param key   the key of the new entry. Any existing entry with this key is replaced.
     * @param value the value associated with the new entry
     * @return true if an existing entry with the same key was replaced
     */

    public boolean initialPut(AtomicValue key, GroundedValue value) {
//        if (Instrumentation.ACTIVE) {
//            Instrumentation.count("initialPut");
//        }
        boolean empty = isEmpty();
        boolean exists = get(key) != null;
        imap = imap.put(makeKey(key), new KeyValuePair(key, value));
        updateTypeInformation(key, value, empty);
        entries = -1;
        return exists;
    }

    private AtomicMatchKey makeKey(AtomicValue key) {
        return key.asMapKey();
    }


    /**
     * Remove an entry from the map
     *
     * @param key the key of the entry to be removed
     * @return a new map in which the requested entry has been removed; or this map
     * unchanged if the specified key was not present
     */

    @Override
    public HashTrieMap remove(AtomicValue key) {
//        if (Instrumentation.ACTIVE) {
//            Instrumentation.count("remove");
//        }

        // This code used to assume that if the key wasn't in the
        // map, imap.remove() would return the original object
        // unchanged. But that was only true if the hash bucket
        // that would have contained the value was empty. That
        // won't be the case if other values happen to have been
        // assigned that bucket. So now we do an explicit check.
        // This is probably slower, but remove() is an uncommon
        // operation. And it gives the correct result!
        if (imap.get(makeKey(key)) == null) {
            // The key is not present; the map is unchanged
            return this;
        }

        ImmutableMap<AtomicMatchKey, KeyValuePair> m2 = imap.remove(makeKey(key));
        HashTrieMap result = new HashTrieMap(m2);
        result.keyUType = keyUType;
        result.valueUType = valueUType;
        result.valueCardinality = valueCardinality;
        result.entries = entries - 1;
        return result;
    }

    /**
     * Get an entry from the Map
     *
     * @param key the value of the key
     * @return the value associated with the given key, or null if the key is not present in the map
     */

    @Override
    public GroundedValue get(AtomicValue key) {
        KeyValuePair o = imap.get(makeKey(key));
        return o == null ? null : o.value;
    }

    /**
     * Get an key/value pair from the Map
     *
     * @param key the value of the key
     * @return the key-value-pair associated with the given key, or null if the key is not present in the map
     */

    public KeyValuePair getKeyValuePair(AtomicValue key) {
        return imap.get(makeKey(key));
    }

    /**
     * Get the set of all key values in the map
     *
     * @return an iterator over the keys, in undefined order
     */

    @Override
    public AtomicIterator<? extends AtomicValue> keys() {
        return new AtomicIterator<AtomicValue>() {

            Iterator<Tuple2<AtomicMatchKey, KeyValuePair>> base = imap.iterator();

            @Override
            public AtomicValue next() {
                if (base.hasNext()) {
                    return base.next()._2.key;
                } else {
                    return null;
                }
            }
        };

    }

    /**
     * Get the set of all key-value pairs in the map
     *
     * @return an iterable whose iterator delivers the key-value pairs
     */
    @Override
    public Iterable<KeyValuePair> keyValuePairs() {
        // For .NEU - don't use a lambda expression here
        return new Iterable<KeyValuePair>() {
            @Override
            public Iterator<KeyValuePair> iterator() {
                return new Iterator<KeyValuePair>() {
                    Iterator<Tuple2<AtomicMatchKey, KeyValuePair>> base = imap.iterator();

                    @Override
                    public boolean hasNext() {
                        return base.hasNext();
                    }

                    @Override
                    public KeyValuePair next() {
                        return base.next()._2;
                    }

                    @Override
                    public void remove() {
                        base.remove();
                    }
                };
            };
        };
    }


    public void diagnosticDump() {
        System.err.println("Map details:");
        for (Tuple2<AtomicMatchKey, KeyValuePair> entry : imap) {
            AtomicMatchKey k1 = entry._1;
            AtomicValue k2 = entry._2.key;
            Sequence v = entry._2.value;
            System.err.println(k1.getClass() + " " + k1 +
                                       " #:" + k1.hashCode() +
                                       " = (" + k2.getClass() + " " + k2 + " : " + v + ")");
        }
    }

//    public String toShortString() {
//        int size = size();
//        if (size == 0) {
//            return "map{}";
//        } else if (size > 5) {
//            return "map{(:size " + size + ":)}";
//        } else {
//            FastStringBuffer buff = new FastStringBuffer(256);
//            buff.append("map{");
//            Iterator<Tuple2<AtomicMatchKey, KeyValuePair>> iter = imap.iterator();
//            while (iter.hasNext()) {
//                Tuple2<AtomicMatchKey, KeyValuePair> entry = iter.next();
//                AtomicMatchKey k1 = entry._1;
//                AtomicValue k2 = entry._2.key;
//                Sequence v = entry._2.value;
//                buff.append(k2.toShortString());
//                buff.append(':');
//                buff.append(Err.depictSequence(v).toString().trim());
//                buff.append(", ");
//            }
//            if (size == 1) {
//                buff.append("}");
//            } else {
//                buff.setCharAt(buff.length() - 2, '}');
//            }
//            return buff.toString().trim();
//        }
//    }

    public String toString() {
        return MapItem.mapToString(this);
    }
}

// Copyright (c) 2018-2020 Saxonica Limited



