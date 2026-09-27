// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A {@code Shape} represents the common structure of a set of {@code ShapedMap} objects,
 * each being a map with the same set of keys but different values. A {@code Shape} is essentially
 * a mapping from keys to integer slot positions.} An instance of a {@code Shape} is represented
 * by a {@link ShapedMap}.
 */
public class Shape {

    private final UnicodeString[] keys;
    private final HashMap<UnicodeString, Integer> slotMap;

    public final static UnicodeString KEY = new Twine8("key");
    public final static UnicodeString VALUE = new Twine8("value");
    public final static Shape KEY_VALUE_PAIR = new Shape(KEY, VALUE);

    public Shape(UnicodeString... keys) {
        this.keys = keys;
        this.slotMap = new HashMap<>(keys.length);
        for (int i=0; i<keys.length; i++) {
            slotMap.put(keys[i], i);
        }
    }

    /**
     * Derive the structure of a Shape from a RecordTest.
     *
     * @param recordTypeDefinition - an instance of RecordTest, populated with a list of field names
     */
    public Shape(RecordType recordTypeDefinition) {
        this(unicodeStringArrayFromStringList((ArrayList<String>) recordTypeDefinition.getFieldNames()));
    }

    /**
     * Helper method for converting a list of Java strings to
     * an array of Saxon Unicode strings.
     *
     * @param stringList - an ArrayList of Java strings
     * @return the same list as an array of Saxon Unicode strings (order preserved)
     */
    private static Twine8[] unicodeStringArrayFromStringList(ArrayList<String> stringList) {
        ArrayList<Twine8> unicodeStringList = new ArrayList<Twine8>();
        stringList.forEach((fieldName) -> {
            unicodeStringList.add(new Twine8(fieldName));
        });
        Twine8[] unicodeStringArray = new Twine8[unicodeStringList.size()];
        return unicodeStringList.toArray(unicodeStringArray);
    }

    /**
     * Get the number of keys in the Shape
     * @return the number of distinct keys
     */
    public int size() {
        return keys.length;
    }

    /**
     * Get the integer slot number allocated to a particular key, if any
     * @param key the relevant key
     * @return the slot number used for the specified key, or -1 if the key is not recognised
     */
    public int getSlot(UnicodeString key) {
        return slotMap.getOrDefault(key, -1);
    }

    /**
     * Get the key associated with a particular slot number
     * @param slot the slot number
     * @return the associated key value
     */
    public UnicodeString getKey(int slot) {
        return keys[slot];
    }

    /**
     * Return an iterator over the keys in this Shape
     * @return an iterator over the keys, retaining the order in which they were originally supplied
     */
    public AtomicIterator iterateKeys() {
        return new KeyIterator(keys);
    }

    /**
     * Make an instance of this Shape
     */
    public ShapedMap make(GroundedValue... values) {
        return new ShapedMap(this, values);
    }

    /**
     * Inner class to iterate over the keys
     */
    private static class KeyIterator implements AtomicIterator {
        final private UnicodeString[] keys;
        private int position = 0;
        public KeyIterator(UnicodeString[] keys) {
            this.keys = keys;
        }

        /**
         * Get the next atomic value in the sequence. <BR>
         *
         * @return the next Item. If there are no more items, return null.
         */
        @Override
        public AtomicValue next() {
            return position >= keys.length ? null : new StringValue(keys[position++]);
        }
    }
}

