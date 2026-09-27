// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.transpile.CSharpInnerClass;
import net.sf.saxon.tree.jiter.ConcatenatingIterator;
import net.sf.saxon.value.AtomicValue;

import java.util.Iterator;

/**
 * A {@code DeltaMap} consists of a fixed part and a variable part. The fixed part represents
 * the result of building a map "in bulk", for example with a map:merge or map:build call,
 * or as the output of JSON parsing. The variable part represents the result of subsequent
 * map:put and map:remove operations.
 *
 * <p>The thinking here is that {@code map:put} and {@code map:remove} operations are
 * fairly rare, so most maps will be implemented as instances of {@link AbstractFixedMap}.
 * If a {@code map:put} or {@code map:remove} then occurs, the result will be a {@code DeltaMap}
 * that contains the original {@link AbstractFixedMap}, plus details of subsequent changes.</p>
 *
 * <p>The variable part, representing the differences, actually includes three data structures:</p>
 *
 * <ul>
 *     <li>A list of removals: entries that have been removed from the fixed map</li>
 *     <li>A list of replacements: entries where a new value is now associated with a key
 *     that was present in the fixed map</li>
 *     <li>A list of additions: new key-value pairs, themselves represented as a map</li>
 * </ul>
 */

public class DeltaMap extends MapWithTypeCache implements Iterable<KeyValuePair> {

    private final AbstractFixedMap fixedMap;
    private final MapItem additionalEntries;
    private final ReplacementsList replacements;
    private final RemovalsList removals;

    /**
     * Construct a {@link DeltaMap} with a supplied fixed part, and an empty variable part
     * @param fixedMap the supplied fixed part
     */

    public DeltaMap(AbstractFixedMap fixedMap) {
        int version = fixedMap.getSpecVersion();
        setSpecVersion(version);
        this.fixedMap = fixedMap;
        this.additionalEntries = EmptyMap.getInstance(version);
        this.removals = RemovalsList.empty;
        this.replacements = ReplacementsList.empty;
    }

    private DeltaMap(AbstractFixedMap fixedMap, RemovalsList removals,
                    ReplacementsList replacements, MapItem additionalEntries) {
        int version = fixedMap.getSpecVersion();
        setSpecVersion(version);
        this.fixedMap = fixedMap;
        this.additionalEntries = additionalEntries;
        this.removals = removals;
        this.replacements = replacements;
    }

    /**
     * Get the value with a given key. This searches first, the additional entries,
     * then the fixed entries, but taking into account any removals and replacements
     * @param key     the value of the key
     * @return the associated value, or null.
     */
    @Override
    public GroundedValue get(AtomicValue key) {
        GroundedValue value = additionalEntries.get(key);
        if (value != null) {
            return value;
        }
        int offset = fixedMap.getPosition(key);
        if (offset >= 0) {
            GroundedValue replacement = replacements.get(offset);
            if (replacement != null) {
                return replacement;
            }
            if (removals.contains(offset)) {
                return null;
            }
            return fixedMap.getValue(offset);
        }
        return null;
    }

    @Override
    public int size() {
        return fixedMap.size() - removals.size() + additionalEntries.size();
    }

    @Override
    public Iterable<KeyValuePair> keyValuePairs() {
        return this;
    }

    /**
     * Implement {@code Iterable<KeyValuePair>}
     * @return an iterator over the key-value pairs
     */
    @CSharpInnerClass(outer = true, extra = {"Saxon.Ejavax.xml.transform.Source source", "Saxon.Hej.Configuration config"})
    public Iterator<KeyValuePair> iterator() {
//        Iterator<Integer> base = integerRange(0, fixedMap.size());
//        Function<Integer, KeyValuePair> filter = index -> {
//            AtomicValue key = fixedMap.getKey(index);
//            GroundedValue replacement = replacements.get(index);
//            if (replacement != null) {
//                return new KeyValuePair(key, replacement);
//            }
//            if (removals.contains(index)) {
//                return null;
//            }
//            return new KeyValuePair(key, fixedMap.getValue(index));
//        };
//        Iterator<KeyValuePair> first = new MappingJavaIterator<>(base, filter);
        Iterator<KeyValuePair> first = new FixedMapIterator(this);
        return new ConcatenatingIterator<>(first, () -> additionalEntries.keyValuePairs().iterator());
    }

    @Override
    public MapItem put(AtomicValue key, GroundedValue value) {
        int offset = fixedMap.getPosition(key);
        if (offset >= 0) {
            ReplacementsList replacements2 = replacements.put(offset, value);
            RemovalsList removals2 = removals.remove(offset);
            return new DeltaMap(fixedMap, removals2, replacements2, additionalEntries);
        } else {
            MapItem additional2 = additionalEntries.put(key, value);
            return new DeltaMap(fixedMap, removals, replacements, additional2);
        }
    }

    @Override
    public MapItem remove(AtomicValue key) {
        if (additionalEntries.get(key) != null) {
            MapItem additional2 = additionalEntries.remove(key);
            return new DeltaMap(fixedMap, removals, replacements, additional2);
        }
        int offset = fixedMap.getPosition(key);
        if (offset >= 0) {
            ReplacementsList replacements2 = replacements.remove(offset);
            RemovalsList removals2 = removals.add(offset);
            return new DeltaMap(fixedMap, removals2, replacements2, additionalEntries);
        } else {
            return this;
        }
    }


    public static class FixedMapIterator implements Iterator<KeyValuePair> {

        int pos = 0;
        KeyValuePair nextPair;
        final DeltaMap deltaMap;
        final ReplacementsList replacements;
        final RemovalsList removals;
        final AbstractFixedMap fixedMap;

        public FixedMapIterator(DeltaMap deltaMap) {
            this.deltaMap = deltaMap;
            this.replacements = deltaMap.replacements;
            this.removals = deltaMap.removals;
            this.fixedMap = deltaMap.fixedMap;
            advance();
        }

        private void advance() {
            while (true) {
                int p = pos++;
                if (p >= fixedMap.size()) {
                    nextPair = null;
                    return;
                }
                AtomicValue key = fixedMap.getKey(p);
                GroundedValue replacement = replacements.get(p);
                if (replacement != null) {
                    nextPair = new KeyValuePair(key, replacement);
                    return;
                } else if (removals.contains(p)) {
                    continue;
                } else {
                    nextPair = new KeyValuePair(key, fixedMap.getValue(p));
                    return;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return nextPair != null;
        }

        @Override
        public KeyValuePair next() {
            KeyValuePair result = nextPair;
            advance();
            return result;
        }
    }

}

