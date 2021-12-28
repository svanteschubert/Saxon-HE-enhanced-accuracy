////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.ma.map.HashTrieMap;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.tree.jiter.MappingJavaIterator;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.StringValue;

import java.util.*;

/**
 * A map in the XDM data model. A map is a list of zero or more entries, each of which
 * is a pair comprising a key (which is an atomic value) and a value (which is an arbitrary value).
 * The map itself is an XDM item.
 * <p>An XdmMap is immutable.</p>
 * <p>As originally issued in Saxon 9.8, this class implemented the {@link java.util.Map} interface.
 * It no longer does so, because it was found that the methods {@link #size()}, {@link #put(XdmAtomicValue, XdmValue)},
 * and {@link #remove} did not adhere to the semantic contract of that interface. Instead, it is now
 * possible to obtain a view of this object as an immutable Java {@link java.util.Map} by calling the
 * method {@link #asImmutableMap()}. See bug 3824.</p>
 *
 * @since 9.8
 */

public class XdmMap extends XdmFunctionItem {

    /**
     * Create an empty XdmMap
     */

    public XdmMap() {
        setValue(new HashTrieMap());
    }

    /**
     * Create an XdmMap whose underlying value is a MapItem
     * @param map the MapItem to be encapsulated
     */

    public XdmMap(MapItem map) {
        setValue(map);
    }

    /**
     * Create an XdmMap supplying the entries in the form of a Java Map,
     * where the keys and values in the Java Map are XDM values
     *
     * @param map a Java map whose entries are the (key, value) pairs
     * @since 9.8
     */

    public XdmMap(Map<? extends XdmAtomicValue, ? extends XdmValue> map) {
        HashTrieMap val = new HashTrieMap();
        for (Map.Entry<? extends XdmAtomicValue, ? extends XdmValue> entry : map.entrySet()) {
            val.initialPut(entry.getKey().getUnderlyingValue(), entry.getValue().getUnderlyingValue());
        }
        setValue(val);
    }

    /**
     * Get the underlying implementation object representing the value. This method allows
     * access to lower-level Saxon functionality, including classes and methods that offer
     * no guarantee of stability across releases.
     *
     * @return the underlying implementation object representing the value
     * @since 9.8 (previously inherited from XdmValue which returns a Sequence)
     */
    @Override
    public MapItem getUnderlyingValue() {
        return (MapItem)super.getUnderlyingValue();
    }

    /**
     * Get the number of entries in the map
     *
     * @return the number of entries in the map. (Note that the {@link #size()} method returns 1 (one),
     * because an XDM map is an item.)
     */

    public int mapSize() {
        return getUnderlyingValue().size();
    }

    /**
     * Create a new map containing an additional (key, value) pair.
     * If there is an existing entry with the same key, it is removed
     * @return a new map containing the additional entry. The original map is unchanged.
     */

    public XdmMap put(XdmAtomicValue key, XdmValue value) {
        XdmMap map2 = new XdmMap();
        map2.setValue(getUnderlyingValue().addEntry(key.getUnderlyingValue(), value.getUnderlyingValue()));
        return map2;
    }

    /**
     * Create a new map in which the entry for a given key has been removed.
     * If there is no entry with the same key, the new map has the same content as the old (it may or may not
     * be the same Java object)
     *
     * @return a map without the specified entry. The original map is unchanged.
     */

    public XdmMap remove(XdmAtomicValue key) {
        XdmMap map2 = new XdmMap();
        map2.setValue(getUnderlyingValue().remove(key.getUnderlyingValue()));
        return map2;
    }

    /**
     * Get the keys present in the map in the form of an unordered set.
     * @return an unordered set of the keys present in this map, in arbitrary order.
     */
    public Set<XdmAtomicValue> keySet() {
        return new AbstractSet<XdmAtomicValue>() {
            @Override
            public Iterator<XdmAtomicValue> iterator() {
                return new MappingJavaIterator<>(
                        getUnderlyingValue().keyValuePairs().iterator(),
                        kvp -> (XdmAtomicValue) XdmValue.wrap(kvp.key));
            }

            @Override
            public int size() {
                return getUnderlyingValue().size();
            }

            @Override
            public boolean contains(Object o) {
                // Intentionally throws ClassCastException if o is not an XdmAtomicValue
                return getUnderlyingValue().get(((XdmAtomicValue)o).getUnderlyingValue()) != null;
            }
        };
    }

    /**
     * Return this map as an immutable instance of {@link java.util.Map}
     * @return an immutable instance of {@link java.util.Map} backed by this map.
     * Methods such as {@link #remove} and {@link #put} applied to the result will
     * always throw {@code UnsupportedOperationException}.
     */

    public Map<XdmAtomicValue, XdmValue> asImmutableMap() {
        // See bug 3824
        XdmMap base = this;
        return new AbstractMap<XdmAtomicValue, XdmValue>() {
            @Override
            public Set<Entry<XdmAtomicValue, XdmValue>> entrySet() {
                return base.entrySet();
            }

            @Override
            public int size() {
                return base.mapSize();
            }

            @Override
            public boolean isEmpty() {
                return base.isEmpty();
            }

            @Override
            public boolean containsKey(Object key) {
                return key instanceof XdmAtomicValue && base.containsKey((XdmAtomicValue) key);
            }

            @Override
            public XdmValue get(Object key) {
                return key instanceof XdmAtomicValue ? base.get((XdmAtomicValue)key) : null;
            }

            @Override
            public XdmValue put(XdmAtomicValue key, XdmValue value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public XdmValue remove(Object key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void putAll(Map<? extends XdmAtomicValue, ? extends XdmValue> m) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<XdmAtomicValue> keySet() {
                return base.keySet();
            }

            @Override
            public Collection<XdmValue> values() {
                return base.values();
            }
        };
    }

    /**
     * Return a mutable Java Map containing the same entries as this map.
     *
     * @return a mutable Map from atomic values to (sequence) values, containing a copy of the
     * entries in this map. Changes to the returned map have no effect on the original
     * {@code XdmMap}.
     * @since 9.6.
     */

    @Override
    public Map<XdmAtomicValue, XdmValue> asMap() {
        return new HashMap<>(asImmutableMap());
    }

    /**
     * Removes all of the mappings from this map (optional operation).
     * The map will be empty after this call returns.
     *
     * @throws UnsupportedOperationException if the <tt>clear</tt> operation
     *                                       is not supported by this map
     */
    public void clear() {
        throw new UnsupportedOperationException("XdmMap is immutable");
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings
     */
    @Override
    public boolean isEmpty() {
        return getUnderlyingValue().isEmpty();
    }

    /**
     * Returns <tt>true</tt> if this map contains a mapping for the specified
     * key.  More formally, returns <tt>true</tt> if and only if
     * this map contains a mapping for a key <tt>k</tt> such that
     * <tt>(key==null ? k==null : key.equals(k))</tt>.  (There can be
     * at most one such mapping.)
     *
     * @param key key whose presence in this map is to be tested
     * @return <tt>true</tt> if this map contains a mapping for the specified key
     * @since 9.8. Changed the method signature in 9.9.1.1 to match the implementation: see bug 3969.
     */
    public boolean containsKey(XdmAtomicValue key) {
        return getUnderlyingValue().get(key.getUnderlyingValue()) != null;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned.
     * @return the value to which the specified key is mapped, or
     * {@code null} if this map contains no mapping for the key
     * @throws ClassCastException   if the supplied key cannot be converted to an XdmAtomicValue
     * @throws NullPointerException if the supplied key is null
     */
    public XdmValue get(XdmAtomicValue key) {
        if (key == null) {
            throw new NullPointerException();
        }
        GroundedValue v = getUnderlyingValue().get(key.getUnderlyingValue());
        return v == null ? null : XdmValue.wrap(v);
    }

    /**
     * Returns the value to which the specified string-valued key is mapped,
     * or {@code null} if this map contains no mapping for the key. This is a convenience
     * method to save the trouble of converting the String to an <code>XdmAtomicValue</code>.
     *
     * @param key the key whose associated value is to be returned. This is treated
     *            as an instance of <code>xs:string</code> (which will also match
     *            entries whose key is <code>xs:untypedAtomic</code> or <code>xs:anyURI</code>)
     * @return the value to which the specified key is mapped, or
     * {@code null} if this map contains no mapping for the key
     * @throws NullPointerException if the supplied key is null
     */
    public XdmValue get(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
        GroundedValue v = getUnderlyingValue().get(new StringValue(key));
        return v == null ? null : XdmValue.wrap(v);
    }

    /**
     * Returns the value to which the specified integer-valued key is mapped,
     * or {@code null} if this map contains no mapping for the key. This is a convenience
     * method to save the trouble of converting the integer to an <code>XdmAtomicValue</code>.
     *
     * @param key the key whose associated value is to be returned. This is treated
     *            as an instance of <code>xs:integer</code> (which will also match
     *            entries whose key belongs to another numeric type)
     * @return the value to which the specified key is mapped, or
     * {@code null} if this map contains no mapping for the key
     * @throws NullPointerException if the supplied key is null
     */
    public XdmValue get(long key) {
        GroundedValue v = getUnderlyingValue().get(new Int64Value(key));
        return v == null ? null : XdmValue.wrap(v);
    }

    /**
     * Returns the value to which the specified double-valued key is mapped,
     * or {@code null} if this map contains no mapping for the key. This is a convenience
     * method to save the trouble of converting the double to an <code>XdmAtomicValue</code>.
     *
     * @param key the key whose associated value is to be returned. This is treated
     *            as an instance of <code>xs:double</code> (which will also match
     *            entries whose key belongs to another numeric type)
     * @return the value to which the specified key is mapped, or
     * {@code null} if this map contains no mapping for the key
     * @throws NullPointerException if the supplied key is null
     */

    public XdmValue get(double key) {
        GroundedValue v = getUnderlyingValue().get(new DoubleValue(key));
        return v == null ? null : XdmValue.wrap(v);
    }

    /**
     * Returns a {@link Collection} containing the values found in this map,
     * that is, the value parts of the key-value pairs.
     *
     * @return a collection containing the values found in this map. The result
     * may contain duplicates, and the ordering of the collection is unpredictable.
     */
    public Collection<XdmValue> values() {
        List<XdmValue> result = new ArrayList<>();
        for (KeyValuePair keyValuePair : getUnderlyingValue().keyValuePairs()) {
            result.add(XdmValue.wrap(keyValuePair.value));
        }
        return result;
    }

    /**
     * Returns a {@link Set} of the key-value pairs contained in this map.
     *
     * @return a set of the mappings contained in this map
     */
    public Set<Map.Entry<XdmAtomicValue, XdmValue>> entrySet() {
        Set<Map.Entry<XdmAtomicValue, XdmValue>> result = new HashSet<>();
        for (KeyValuePair keyValuePair : getUnderlyingValue().keyValuePairs()) {
            result.add(new XdmMapEntry(keyValuePair));
        }
        return result;
    }

    /**
     * Static factory method to construct an XDM map by converting each entry
     * in a supplied Java map. The keys in the Java map must be convertible
     * to XDM atomic values using the {@link XdmAtomicValue#makeAtomicValue(Object)}
     * method. The associated values must be convertible to XDM sequences
     * using the {@link XdmValue#makeValue(Object)} method.
     * @param input the supplied map
     * @return the resulting XdmMap
     * @throws IllegalArgumentException if any value in the input map cannot be converted
     * to a corresponding XDM value.
     */

    public static XdmMap makeMap(Map input) throws IllegalArgumentException {
        HashTrieMap result = new HashTrieMap();
        for (Object entry : input.entrySet()) {
            Object key = ((Map.Entry)entry).getKey();
            Object value = ((Map.Entry)entry).getValue();
            XdmAtomicValue xKey = XdmAtomicValue.makeAtomicValue(key);
            XdmValue xValue = XdmValue.makeValue(value);
            result.initialPut(xKey.getUnderlyingValue(), xValue.getUnderlyingValue());
        }
        return new XdmMap(result);
    }

    private static class XdmMapEntry implements Map.Entry<XdmAtomicValue, XdmValue> {

        KeyValuePair pair;

        public XdmMapEntry(KeyValuePair pair) {
            this.pair = pair;
        }
        /**
         * Returns the key corresponding to this entry.
         *
         * @return the key corresponding to this entry
         * @throws IllegalStateException implementations may, but are not
         *                               required to, throw this exception if the entry has been
         *                               removed from the backing map.
         */
        @Override
        public XdmAtomicValue getKey() {
            return (XdmAtomicValue)XdmValue.wrap(pair.key);
        }

        /**
         * Returns the value corresponding to this entry.  If the mapping
         * has been removed from the backing map (by the iterator's
         * <tt>remove</tt> operation), the results of this call are undefined.
         *
         * @return the value corresponding to this entry
         * @throws IllegalStateException implementations may, but are not
         *                               required to, throw this exception if the entry has been
         *                               removed from the backing map.
         */
        @Override
        public XdmValue getValue() {
            return XdmValue.wrap(pair.value);
        }

        /**
         * Replaces the value corresponding to this entry with the specified
         * value (optional operation).  (Writes through to the map.)  The
         * behavior of this call is undefined if the mapping has already been
         * removed from the map (by the iterator's <tt>remove</tt> operation).
         *
         * @param value new value to be stored in this entry
         * @return old value corresponding to the entry
         * @throws UnsupportedOperationException if the <tt>put</tt> operation
         *                                       is not supported by the backing map
         * @throws ClassCastException            if the class of the specified value
         *                                       prevents it from being stored in the backing map
         * @throws NullPointerException          if the backing map does not permit
         *                                       null values, and the specified value is null
         * @throws IllegalArgumentException      if some property of this value
         *                                       prevents it from being stored in the backing map
         * @throws IllegalStateException         implementations may, but are not
         *                                       required to, throw this exception if the entry has been
         *                                       removed from the backing map.
         */
        @Override
        public XdmValue setValue(XdmValue value) {
            throw new UnsupportedOperationException();
        }
    }
}

