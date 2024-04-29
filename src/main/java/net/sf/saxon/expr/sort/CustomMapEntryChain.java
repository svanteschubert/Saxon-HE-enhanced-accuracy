////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

/**
 * A one-way linked chain of entries in a {@link CustomMap}
 *
 * @param <K> The type of the key held in each link of the chain (although the keys
 *           for all the entries in a chain are equal, they are not necessarily
 *           indistinguishable)
 * @param <V> The type of the value held in each link of the chain
 * @implNote This is a separate top-level class (rather than an inner class) for ease of
 * conversion to C#. Making it a separate class avoids the problem of the full class
 * name requiring two type parameters.
 */
class CustomMapEntryChain<K, V> {
    public K key;
    public V value;
    public CustomMapEntryChain<K, V> next;

    public CustomMapEntryChain(K key, V value) {
        this.key = key;
        this.value = value;
        this.next = null;
    }
}

