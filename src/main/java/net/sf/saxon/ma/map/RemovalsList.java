// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.ma.trie.ImmutableHashTrieMap;
import net.sf.saxon.transpile.CSharpReplaceBody;

public class RemovalsList {

    private final ImmutableHashTrieMap<Integer, Integer> removals;
    private final int numberOfItems;

    public RemovalsList() {
        removals = emptyMap();
        numberOfItems = 0;
    }

    private RemovalsList(ImmutableHashTrieMap<Integer, Integer> removals, int numberOfItems) {
        this.removals = removals;
        this.numberOfItems = numberOfItems;
    }

    @CSharpReplaceBody(code="return System.Collections.Immutable.ImmutableDictionary<int, int>.Empty;")
    private static ImmutableHashTrieMap<Integer, Integer> emptyMap() {
        return ImmutableHashTrieMap.empty();
    }

    public static RemovalsList empty = new RemovalsList();

    public RemovalsList add(Integer value) {
        return new RemovalsList(removals.put(value, value), numberOfItems + 1);
    }

    @CSharpReplaceBody(code="return removals.ContainsKey(value);")
    public boolean contains(Integer value) {
        return removals.get(value) != null;
    }

    public int size(){
        return numberOfItems;
    }

    /**
     * Remove an entry from the removals list
     * @param value the entry to be removed from the removals list
     * @return the removals list, with the supplied value reinstated
     */
    public RemovalsList remove(Integer value) {
        if (contains(value)) {
            return new RemovalsList(removals.remove(value), numberOfItems - 1);
        } else {
            return this;
        }
    }



}
