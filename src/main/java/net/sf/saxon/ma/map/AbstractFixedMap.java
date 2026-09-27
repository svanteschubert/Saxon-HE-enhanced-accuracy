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
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.value.AtomicValue;

import java.util.*;

/**
 * A general-purpose XDM map implementation that is ordered, that holds any kind of key,
 * and that supports incremental modification only by copying the whole map to a different
 * structure. Any duplicates added during map construction follow "use-last" semantics -
 * the new value replaces the old. This structure is therefore unsuited to cases where
 * duplicates need custom handling.
 *
 * <p>The implementation is based on an array of keys, an array of corresponding values,
 * and a mutable Java {@code java.util.HashMap} (but encapsulated
 * so no mutation can take place after the map has been built) that maps atomic match keys
 * to integer positions in the array. If put or remove operations
 * are subsequently attempted, the map is copied to a different implementation structure
 * that supports functional modification.</p>
 *
 * <p>The underlying implementation uses an instance of {@code HashMap<AtomicMatchKey, KeyValuePair>}
 * For many data types this means that the key is held twice, once as the key, and once as part
 * of the value. The reason for this is that the <code>keys()</code> function needs to return
 * the original keys as supplied, complete with type annotation, which would not (always) have
 * the same equality semantics. The {@code AtomicMatchKey} is computed from the actual key
 * and in some cases is simply a wrapper with a different <code>equals()</code> method.</p>
 */

public abstract class AbstractFixedMap extends MapWithTypeCache implements Iterable<KeyValuePair> {

    protected GroundedValue[] values;
    protected HashMap<AtomicMatchKey, Integer> index;

    private static class FixedMapBuilder extends GeneralMapBuilder {

        public FixedMapBuilder(int specVersion) {
            super(specVersion);
        }

        @Override
        public MapItem getCompletedMap() throws XPathException {
            if (keys.isEmpty()) {
                return EmptyMap.getInstance(specVersion);
            } else if (keys.size() == 1) {
                return new SingleEntryMap(keys.get(0), values.get(0), specVersion);
            }
            AtomicValue[] keyArray = keys.toArray(new AtomicValue[0]);
            GroundedValue[] valueArray = values.toArray(new GroundedValue[0]);
            HashMap<AtomicMatchKey, Integer> index = new HashMap<>(keyArray.length);
            for (int i = 0; i < keyArray.length; i++) {
                boolean isNew = putOffsetIfAbsent(index, keyArray[i].asMapKey(specVersion), i);
                if (!isNew) {
                    throw new XPathException("Duplicate key " + Err.depict(keyArray[i]) + " in map", "FOJS0003");
                }
            }
            return new FixedMap(keyArray, valueArray, index, specVersion);
        }

        /**
         * Get the constructed map. This method must only be called once, because the
         * lists of keys and values are used within the constructed map and are mutable.
         *
         * @param context The dynamic context: needed only when calling a combiner function
         *                to combine duplicate entries
         * @return the constructed map
         * @throws XPathException if duplicate keys were found, and not detected earlier
         */

        public MapItem getCompletedMap(XPathContext context) throws XPathException {
            if (keys.isEmpty()) {
                return EmptyMap.getInstance(specVersion);
            } else if (keys.size() == 1) {
                return new SingleEntryMap(keys.get(0), values.get(0), specVersion);
            }
            // quick scan for possible duplicates using a bloom filter
            boolean possibleDuplicates = hasPossibleDuplicates(keys, specVersion);
            if (possibleDuplicates) {
                // If there are possible duplicates, then we build the map now, with a more thorough
                // check for duplicates as we build it.
                List<AtomicValue> keyList = new ArrayList<>(keys.size());
                List<GroundedValue> valueList = new ArrayList<>(keys.size());

                HashMap<AtomicMatchKey, Integer> index = new HashMap<>(keys.size());
                int serial = 0;
                for (int i = 0; i < keys.size(); i++) {
                    AtomicMatchKey key = keys.get(i).asMapKey(specVersion);
                    boolean isNew = putOffsetIfAbsent(index, key, i);
                    if (isNew) {
                        index.put(key, serial++);
                        keyList.add(keys.get(i));
                        valueList.add(values.get(i));
                    } else if (combiner == null) {
                        throw new XPathException("Duplicate key " + Err.depict(keys.get(i)) + " in map");
                    } else {
                        GroundedValue newVal;
                        int offset = -1;
                        try {
                            offset = getOffset(index, key);
                            newVal = combiner.combine(valueList.get(offset), values.get(i), context);
                        } catch (UncheckedXPathException e) {
                            throw new XPathException("Duplicate key " + Err.depict(keys.get(i))
                                                             + " in map. First value: "
                                                             + Err.depictSequence(valueList.get(offset)) + "; second value: "
                                                             + Err.depictSequence(values.get(i)), e).withErrorCode(e.getXPathException().getErrorCodeQName());
                        }
                        // In 4.0 we have the option of using the old or the new key. But 3.1 requires the new
                        // key, so we use that.
                        keyList.set(offset, keys.get(i));
                        valueList.set(offset, newVal);
                    }
                }

                AtomicValue[] keyArray = keyList.toArray(new AtomicValue[0]);
                GroundedValue[] valueArray = valueList.toArray(new GroundedValue[0]);

                return new FixedMap(keyArray, valueArray, index, specVersion);

            } else {
                return getCompletedMapConfidently();
            }
        }


        /**
         * Get the constructed map, knowing that there are no duplicate keys.
         *
         * @return the constructed map
         */

        @Override
        public MapItem getCompletedMapConfidently() {
            AtomicValue[] keyArray = keys.toArray(new AtomicValue[0]);
            GroundedValue[] valueArray = values.toArray(new GroundedValue[0]);
            HashMap<AtomicMatchKey, Integer> index = new HashMap<>(keyArray.length);
            for (int i = 0; i < keyArray.length; i++) {
                index.put(keyArray[i].asMapKey(specVersion), i);
            }
            return new FixedMap(keyArray, valueArray, index, specVersion);
        }
    }

    /**
     * Factory method to get a MapBuilder which can be used to construct an instance
     * of a {@link AbstractFixedMap}
     *
     * @return a class for building fixed maps
     */
    public static GeneralMapBuilder getBuilder(int specVersion) {
        return new AbstractFixedMap.FixedMapBuilder(specVersion);
    }

    /**
     * Get an entry from the Map
     *
     * @param key the value of the key
     * @return the value associated with the given key, or null if the key is not present in the map.
     */
    @Override
    public GroundedValue get(AtomicValue key) {
        ensureIndexed();
        int offset = getOffset(index, key.asMapKey(getSpecVersion()));
        return offset < 0 ? null : values[offset];
    }

    /**
     * Get the offset in the index of a given key, or -1 if the key is not present.
     * Done this way for C# transpilation (maps with primitive int values work differently...)
     * @param index the index of offsets
     * @param key the wanted key
     * @return the offset in the index, or -1 if absent
     */

    @CSharpReplaceBody(code="return index.TryGetValue(key, out var result) ? result : -1;")
    public static int getOffset(HashMap<AtomicMatchKey, Integer> index, AtomicMatchKey key) {
        Integer offset = index.get(key);
        return offset == null ? -1 : offset;
    }

    /**
     * Put an entry in the index if the key is currently absent
     * @param index the index of offsets
     * @param key the new key
     * @param value the new value
     * @return true if an entry was added, false if the key was already present
     */
    public static boolean putOffsetIfAbsent(HashMap<AtomicMatchKey, Integer> index, AtomicMatchKey key, int value) {
        if (!index.containsKey(key)) {
            index.put(key, value);
            return true;
        }
        return false;
    }

    /**
     * Get the offset in the index of a given key, or -1 if the key is not present.
     *
     * @param key   the wanted key
     * @return the offset in the index, or -1 if absent
     */

    public int getPosition(AtomicValue key) {
        ensureIndexed();
        return getOffset(index, key.asMapKey(getSpecVersion()));
    }

    /**
     * Ensure that an index exists, creating it if necessary. In many cases an index
     * is only created on the first call on {@link #get(AtomicValue)}. This is because
     * in scenarios such as JSON transformation there may be many maps generated during
     * JSON parsing that are never referenced, or that are copied directly to the serialized
     * output without ever being accessed by key.
     */
    protected synchronized void ensureIndexed() {
        if (index == null) {
            int specVersion = getSpecVersion();
            index = new HashMap<>(size());
            for (int i = 0; i < size(); i++) {
                index.put(getKey(i).asMapKey(specVersion), i);
            }
        }
    }

    /**
     * Get the size of the map
     *
     * @return the number of keys/entries present in this map
     */
    @Override
    public int size() {
        return values.length;
    }

    /**
     * Get the key at a given offset. This method is provided for use by subclasses,
     * which can store the actual key in different ways.
     * @param position the offset (sibling position within the ordered map) of the required key
     * @return the key at the specified position
     */

    protected abstract AtomicValue getKey(int position);

    /**
     * Get the value at a given offset.
     *
     * @param position the offset (sibling position within the ordered map) of the required key
     * @return the key at the specified position
     */

    protected GroundedValue getValue(int position) {
        return values[position];
    }

    /**
     * Get the content of the map as an iterable collection of key value pairs
     * @return an iterable collection of key value pairs
     */

    @Override
    public Iterable<KeyValuePair> keyValuePairs() {
        return this;
    }

    /**
     * Return an iterator of key-value pairs.
     * @return an Iterator of key-value pairs.
     */
    @Override
    public Iterator<KeyValuePair> iterator() {
        return new KeyValuePairIterator(this);
    }

    private static class KeyValuePairIterator implements Iterator<KeyValuePair> {

        private int offset = 0;
        private final AbstractFixedMap map;

        public KeyValuePairIterator(AbstractFixedMap map) {
            this.map = map;
        }

        /**
         * Returns {@code true} if the iteration has more elements.
         * (In other words, returns {@code true} if {@link #next} would
         * return an element rather than throwing an exception.)
         *
         * @return {@code true} if the iteration has more elements
         */
        @Override
        public boolean hasNext() {
            return offset < map.values.length;
        }

        /**
         * Returns the next element in the iteration.
         *
         * @return the next element in the iteration
         * @throws NoSuchElementException if the iteration has no more elements
         */
        @Override
        public KeyValuePair next() {
            if (offset >= map.values.length) {
                throw new NoSuchElementException();
            }
            KeyValuePair kvp = new KeyValuePair(map.getKey(offset), map.values[offset]);
            offset++;
            return kvp;
        }
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
        DeltaMap newMap = new DeltaMap(this);
        newMap.setKnownType(knownKeyType, knownValueType);
        return newMap.put(key, value);
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
        if (get(key) != null) {
            DeltaMap newMap = new DeltaMap(this);
            newMap.setKnownType(knownKeyType, knownValueType);
            return newMap.remove(key);
        } else {
            return this;
        }
    }

    /**
     * Returns a string representation of the object.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (KeyValuePair kvp : this) {
            if (i++ != 0) {
                sb.append(", ");
            }
            sb.append(Err.depict(kvp.key()))
                    .append(": ")
                    .append(Err.depictSequence(kvp.value()));
            if (i > 7) {
                sb.append(" ...");
                break;
            }
        }
        sb.append("}");
        return sb.toString();
    }
}

