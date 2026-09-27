// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * A {@code FlexibleShapedMap} is an implementation of XDM maps optimised for the case
 * where many maps have the same set of string-valued keys. This is a variant of
 * {@link ShapedMap} which extends the capability by allowing entries to be
 * optional (these are represented by nulls in the values array)
 *
 * <p>The keys() method delivers the keys in a consistent order.</p>
 *
 * <p>The class implements the method {@link #getWithPlan(AtomicValue, Stack)} so that a
 * lookup expression such as <code>$record?field</code> using a statically-known lookup
 * key is able to use the slot number established on a previous call of the same
 * expression, avoiding the cost of a hash lookup.</p>
 *
 * <p>An entry in the map may be absent; this is indicated by the value in the relevant
 * slot being a Java null.</p>
 */

public class SparseShapedMap extends ShapedMap {

    /**
     * Create an instance of a {@code ShapedMap} whose structure is defined
     * by the supplied {@link Shape}
     *
     * @param shape  the shape of the map, identifying the keys that are present
     * @param values the values to appear in the map, corresponding one-to-one
     *               with the keys that are defined in the {@link Shape}. A value
     *               may be null to indicate that the entry is absent.
     */
    public SparseShapedMap(Shape shape, GroundedValue... values) {
        super(shape, values);
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
            return getU(key.getUnicodeStringValue());
        }
        return null;
    }

    /**
     * Get the size of the map
     *
     * @return the number of keys/entries present in this map
     */
    @Override
    public int size() {
        int i = 0;
        for (GroundedValue v : values) {
            if (v != null) {
                i++;
            }
        }
        return i;
    }

    /**
     * Get the set of all key values in the map.
     *
     * @return a set containing all the key values present in the map. Normally the order
     * is unpredictable, but for a {@code ShapedMap} the order is defined by the {@link Shape}.
     */
    @Override
    public AtomicIterator keys() {
        List<AtomicValue> list = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                list.add(new StringValue(shape.getKey(i)));
            }
        }
        return new ListIterator.OfAtomic<AtomicValue>(list);
    }

    /**
     * Get the set of all key-value pairs in the map
     *
     * @return an iterable containing all the key-value pairs
     */
    @Override
    public Iterable<KeyValuePair> keyValuePairs() {
        List<KeyValuePair> list = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                list.add(new KeyValuePair(new StringValue(shape.getKey(i)), values[i]));
            }
        }
        return list;
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
        for (GroundedValue val : values) {
            if (val != null) {
                if (valueType == null) {
                    valueType = SequenceTool.getItemType(val, th);
                    valueCard = SequenceTool.getCardinality(val);
                } else {
                    valueType = Type.getCommonSuperType(valueType, SequenceTool.getItemType(val, th), th);
                    valueCard = Cardinality.union(valueCard, SequenceTool.getCardinality(val));
                }
            }
        }
        if (valueType == null) {
            // empty map
            return MapType.EMPTY_MAP_TYPE;
        } else {
            return new MapType(BuiltInAtomicType.STRING, SequenceType.makeSequenceType(valueType, valueCard));
        }
    }


}

