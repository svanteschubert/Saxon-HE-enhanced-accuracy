////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.trie;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

// Original author: Michael Froh (published on Github). Released under MPL 2.0
// by Saxonica Limited with permission from the author

public abstract class ImmutableHashTrieMap<K, V>
        implements ImmutableMap<K,V>, Iterable<TrieKVP<K, V>>  {

    private static final ImmutableHashTrieMap EMPTY_NODE = new EmptyHashNode();

    public static <K, V> ImmutableHashTrieMap<K, V> empty() {
        return EMPTY_NODE;
    }

    private static <K> int getBucket(int shift, K key) {
        return key.hashCode() >> shift & MASK;
    }


    @Override
    public ImmutableHashTrieMap<K, V> put(K key, V value) {
        return put(0, key, value);
    }


    @Override
    public ImmutableHashTrieMap<K, V> remove(K key) {
        return remove(0, key);
    }

    @Override
    public V get(K key) {
        return get(0, key);
    }

    /*
    * In the methods below, "shift" denotes how far (in bits) through the
    * hash code we are currently looking. At each level, we add "bits",
    * defined below, to shift.
    */

    private static final int BITS = 5;
    private static final int FANOUT = 1 << BITS;
    private static final int MASK = FANOUT - 1;

    abstract ImmutableHashTrieMap<K, V> put(int shift, K key,
                                            V value);

    abstract ImmutableHashTrieMap<K, V> remove(int shift, K key);

    abstract V get(int shift, K key);

    abstract boolean isArrayNode();

    /**
     * Implementation for an empty map
     * @param <K> the key type
     * @param <V> the value type
     */

    private static class EmptyHashNode<K, V>
            extends ImmutableHashTrieMap<K, V> {
        @Override
        public ImmutableHashTrieMap<K, V> put(final int shift, final K key,
                                       final V value) {
            return new EntryHashNode<>(key, value);
        }

        @Override
        public ImmutableHashTrieMap<K, V> remove(final int shift,
                                          final K key) {
            return this;
        }

        @Override
        public boolean isArrayNode() {
            return false;
        }

        @Override
        public V get(final int shift, final K key) {
            return null;
        }

        @Override
        public Iterator<TrieKVP<K, V>> iterator() {
            return Collections.emptyIterator();
        }
    }

    /**
     * Implementation for a single-entry map
     * @param <K> the key type
     * @param <V> the value type
     */

    private static class EntryHashNode<K, V>
            extends ImmutableHashTrieMap<K, V> {
        private final K key;
        private final V value;

        private EntryHashNode(final K key,
                              final V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public ImmutableHashTrieMap<K, V> put(final int shift, final K key,
                                       final V value) {
            if (this.key.equals(key)) {
                // Overwriting this entry
                return new EntryHashNode<>(key, value);
            } else if (this.key.hashCode() == key.hashCode()) {
                // This is a collision. Return a new ListHashNode.
                return new ListHashNode<>(new TrieKVP<>(this.key, this.value),
                                          new TrieKVP<>(key, value));
            }
            // Split this node into an ArrayHashNode with this and the new value
            // as entries.
            return newArrayHashNode(shift, this.key.hashCode(), this,
                    key.hashCode(), new EntryHashNode<K, V>(key, value));
        }

        @Override
        public ImmutableHashTrieMap<K, V> remove(final int shift,
                                          final K key) {
            if (this.key.equals(key)) {
                return empty();
            }
            return this;
        }

        @Override
        public boolean isArrayNode() {
            return false;
        }

        @Override
        public V get(final int shift, final K key) {
            if (this.key.equals(key)) {
                return value;
            }
            return null;
        }

        @Override
        public Iterator<TrieKVP<K, V>> iterator() {
            return Collections.singleton(new TrieKVP<>(key, value)).iterator();
        }
    }

    /**
     * Implementation for a set of entries where the keys of all entries share the same hash code
     * @param <K> the key type
     * @param <V> the value type
     */

    private static class ListHashNode<K, V> extends ImmutableHashTrieMap<K, V> {
        private final ImmutableList<TrieKVP<K, V>> entries;

        public ListHashNode(TrieKVP<K, V> entry1,
                            TrieKVP<K, V> entry2) {
            // These entries must collide
            assert entry1.key.hashCode() == entry2.key.hashCode();
            ImmutableList<TrieKVP<K, V>> newList = ImmutableList.empty();
            entries = newList.prepend(entry1).prepend(entry2);
        }

        private ListHashNode(final ImmutableList<TrieKVP<K, V>> entries) {
            // Size should be at least 2
            assert !entries.isEmpty();
            assert !entries.tail().isEmpty();
            this.entries = entries;
        }

        @Override
        public ImmutableHashTrieMap<K, V> put(final int shift, final K key,
                                       final V value) {
            if (entries.head().key.hashCode() != key.hashCode()) {
                return newArrayHashNode(shift,
                        entries.head().key.hashCode(),
                        this,
                        key.hashCode(),
                                        new EntryHashNode<>(
                                                key, value));
            }
            ImmutableList<TrieKVP<K, V>> newList = ImmutableList.empty();
            boolean found = false;
            for (TrieKVP<K, V> entry : entries) {
                if (entry.key.equals(key)) {
                    // Node replacement
                    newList = newList.prepend(new TrieKVP<>(key, value));
                    found = true;
                } else {
                    newList = newList.prepend(entry);
                }
            }
            if (!found) {
                // Adding a new entry
                newList = newList.prepend(new TrieKVP<>(key, value));
            }
            return new ListHashNode<>(newList);
        }

        @Override
        public ImmutableHashTrieMap<K, V> remove(final int shift,
                                          final K key) {
            ImmutableList<TrieKVP<K, V>> newList = ImmutableList.empty();
            int size = 0;
            for (TrieKVP<K, V> entry : entries) {
                if (!entry.key.equals(key)) {
                    newList = newList.prepend(entry);
                    size++;
                }
            }
            if (size == 1) {
                TrieKVP<K, V> entry = newList.head();
                return new EntryHashNode<>(entry.key, entry.value);
            }
            return new ListHashNode<>(newList);
        }

        @Override
        public boolean isArrayNode() {
            return false;
        }

        @Override
        public V get(final int shift, final K key) {
            for (TrieKVP<K, V> entry : entries) {
                if (entry.key.equals(key)) {
                    return entry.value;
                }
            }
            return null;
        }

        @Override
        public Iterator<TrieKVP<K, V>> iterator() {
            return new Iterator<TrieKVP<K, V>>() {
                private ImmutableList<TrieKVP<K, V>> curList =
                        ListHashNode.this.entries;

                @Override
                public boolean hasNext() {
                    return !curList.isEmpty();
                }

                @Override
                public TrieKVP<K, V> next() {
                    TrieKVP<K, V> retVal = curList.head();
                    curList = curList.tail();
                    return retVal;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    /**
     * Create a new node combining two existing nodes
     * @param shift the shift to be applied to the hash code at this level in the tree
     * @param hash1 the hash code of the first node
     * @param subNode1 the first node
     * @param hash2 the hash code of the second node
     * @param subNode2 the second node
     * @param <K> the key type
     * @param <V> the value type
     * @return the new node
     */

    private static <K, V> ImmutableHashTrieMap<K, V>
        newArrayHashNode(int shift,
                         int hash1,
                         ImmutableHashTrieMap<K, V> subNode1,
                         int hash2,
                         ImmutableHashTrieMap<K, V> subNode2) {
        int curShift = shift;
        int h1 = hash1 >> shift & MASK;
        int h2 = hash2 >> shift & MASK;
        List<Integer> buckets = new LinkedList<>();
        while (h1 == h2) {
            buckets.add(0, h1);
            curShift += BITS;
            h1 = hash1 >> curShift & MASK;
            h2 = hash2 >> curShift & MASK;
        }
        ImmutableHashTrieMap<K, V> newNode =
                new BranchedArrayHashNode<K,V>(h1, subNode1, h2, subNode2);
        for (Integer bucket : buckets) {
            newNode = new SingletonArrayHashNode<K,V>(bucket, newNode);
        }
        return newNode;

    }

    private static abstract class ArrayHashNode<K,V>
            extends ImmutableHashTrieMap<K,V> {
        @Override
        boolean isArrayNode() {
            return true;
        }
    }

    private static class BranchedArrayHashNode<K, V>
            extends ArrayHashNode<K, V> {
        private final ImmutableHashTrieMap<K, V>[] subnodes;
        private final int size;

        public BranchedArrayHashNode(int h1,
                             ImmutableHashTrieMap<K, V> subNode1,
                             int h2,
                             ImmutableHashTrieMap<K, V> subNode2) {
            assert h1 != h2;
            size = 2;
            subnodes = new ImmutableHashTrieMap[FANOUT];
            for (int i = 0; i < FANOUT; i++) {
                if (i == h1) {
                    subnodes[i] = subNode1;
                } else if (i == h2) {
                    subnodes[i] = subNode2;
                } else {
                    subnodes[i] = EMPTY_NODE;
                }
            }
        }

        public BranchedArrayHashNode(int size,
                             final ImmutableHashTrieMap<K, V>[] subnodes) {
            assert subnodes.length == FANOUT;
            this.size = size;
            this.subnodes = subnodes;
        }

        @Override
        public ImmutableHashTrieMap<K, V> put(final int shift, final K key,
                                       final V value) {
            final int bucket = getBucket(shift, key);
            ImmutableHashTrieMap<K, V>[] newNodes = new ImmutableHashTrieMap[FANOUT];
            System.arraycopy(subnodes, 0, newNodes, 0, FANOUT);

            final int newSize =
                    newNodes[bucket] == EMPTY_NODE ? size + 1 : size;
            newNodes[bucket] = newNodes[bucket].put(shift + BITS,
                    key, value);
            return new BranchedArrayHashNode<K, V>(newSize, newNodes);
        }

        @Override
        public ImmutableHashTrieMap<K, V> remove(final int shift,
                                          final K key) {
            final int bucket = getBucket(shift, key);
            if (subnodes[bucket] == EMPTY_NODE) {
                return this;
            }
            ImmutableHashTrieMap<K, V>[] newNodes = new ImmutableHashTrieMap[FANOUT];
            System.arraycopy(subnodes, 0, newNodes, 0, FANOUT);
            newNodes[bucket] = newNodes[bucket].remove(shift + BITS,
                    key);
            final int newSize =
                    newNodes[bucket] == EMPTY_NODE ? size - 1 : size;
            if (newSize == 1) {
                int orphanedBucket = -1;
                for (int i = 0; i < FANOUT; i++) {
                    if (newNodes[i] != EMPTY_NODE) {
                        orphanedBucket = i;
                        break;
                    }
                }
                ImmutableHashTrieMap<K, V> orphanedEntry =
                        subnodes[orphanedBucket];
                if (orphanedEntry.isArrayNode()) {
                    return new SingletonArrayHashNode<>(orphanedBucket, orphanedEntry);
                }
                return orphanedEntry;
            }
            return new BranchedArrayHashNode<>(newSize, newNodes);
        }

        @Override
        public V get(final int shift, final K key) {
            final int bucket = getBucket(shift, key);
            return subnodes[bucket].get(shift + BITS, key);
        }

        @Override
        public Iterator<TrieKVP<K, V>> iterator() {
            return new Iterator<TrieKVP<K, V>>() {
                private int bucket = 0;
                private Iterator<TrieKVP<K, V>> childIterator =
                        subnodes[0].iterator();

                @Override
                public boolean hasNext() {
                    if (childIterator.hasNext()) {
                        return true;
                    }
                    bucket++;
                    while (bucket < FANOUT) {
                        childIterator = subnodes[bucket].iterator();
                        if (childIterator.hasNext()) {
                            return true;
                        }
                        bucket++;
                    }
                    return false;
                }

                @Override
                public TrieKVP<K, V> next() {
                    return childIterator.next();
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    private static class SingletonArrayHashNode<K, V> extends
            ArrayHashNode<K, V> {
        private final int bucket;
        private final ImmutableHashTrieMap<K, V> subnode;

        private SingletonArrayHashNode(final int bucket,
                                       final ImmutableHashTrieMap<K, V> subnode) {
            assert subnode instanceof ArrayHashNode;
            this.bucket = bucket;
            this.subnode = subnode;
        }

        @Override
        ImmutableHashTrieMap<K, V> put(final int shift, final K key,
                                       final V value) {
            final int bucket = getBucket(shift, key);
            if (bucket == this.bucket) {
                return new SingletonArrayHashNode<>(bucket,
                                                    subnode.put(shift + BITS, key, value));
            }
            return new BranchedArrayHashNode<>(this.bucket, subnode,
                                               bucket, new EntryHashNode<>(key, value));
        }

        @Override
        ImmutableHashTrieMap<K, V> remove(final int shift, final K key) {
            final int bucket = getBucket(shift, key);
            if (bucket == this.bucket) {
                ImmutableHashTrieMap<K, V> newNode =
                        subnode.remove(shift + BITS, key);
                if (!newNode.isArrayNode()) {
                    return newNode;
                }
                return new SingletonArrayHashNode<>(bucket, newNode);
            }
            return this;
        }

        @Override
        V get(final int shift, final K key) {
            final int bucket = getBucket(shift, key);
            if (bucket == this.bucket) {
                return subnode.get(shift + BITS, key);
            }
            return null;
        }

        @Override
        public Iterator<TrieKVP<K, V>> iterator() {
            return subnode.iterator();
        }
    }

}
