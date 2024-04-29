////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2012 Michael Froh.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.trie;

public final class TrieKVP<K, V> {
    public final K key;
    public final V value;

    public TrieKVP(final K v1, final V v2) {
        key = v1;
        value = v2;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TrieKVP tuple = (TrieKVP) o;

        if (key != null ? !key.equals(tuple.key) : tuple.key != null)
            return false;
        return value != null ? value.equals(tuple.value) : tuple.value == null;
    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "(" +
                key + ',' +
                value +
        ')';
    }

}
