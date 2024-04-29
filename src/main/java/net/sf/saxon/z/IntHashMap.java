////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;

import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.transpile.CSharpReplaceBody;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A hash table that maps int keys to Object values.
 *
 * @author Dave Hale, Landmark Graphics
 * @author Dominique Devienne
 * @author Michael Kay: retrofitted to JDK 1.4, added iterator(), modified to disallow null values
 *         Reverted to generics July 2008.
 */


@CSharpInjectMembers(code={""
        + "public class IntHashMapValueSet<V> : System.Collections.Generic.IEnumerable<V> {"
        + "    internal readonly IntHashMap<V> container;"
        + "    public IntHashMapValueSet(IntHashMap<V> container) {this.container = container;}"
        + "    public System.Collections.Generic.IEnumerator<V> GetEnumerator() {return container.valueIterator();}"
        + "    System.Collections.IEnumerator System.Collections.IEnumerable.GetEnumerator() {return GetEnumerator();}"
        + "}"})

public class IntHashMap<T> {

    /**
     * Initializes a map with a capacity of 8 and a load factor of 0,25.
     */
    public IntHashMap() {
        this(8, 0.25);
    }

    /**
     * Initializes a map with the given capacity and a load factor of 0,25.
     *
     * @param capacity the initial capacity.
     */
    public IntHashMap(int capacity) {
        this(capacity, 0.25);
    }

    /**
     * Constructs a new map with initial capacity, and load factor.
     * <p>The capacity is the number of keys that can be mapped without resizing
     * the arrays in which keys and values are stored. For efficiency, only
     * a fraction of the elements in those arrays are used. That fraction is
     * the specified load factor. The initial length of the arrays equals the
     * smallest power of two not less than the ratio capacity/factor. The
     * capacity of the map is increased, as necessary. The maximum number
     * of keys that can be mapped is 2^30.</p>
     *
     * @param capacity the initial capacity.
     * @param factor   the load factor.
     */
    public IntHashMap(int capacity, double factor) {
        _factor = factor;
        setCapacity(capacity);
    }

    /**
     * Clears the map.
     */
    public void clear() {
        _n = 0;
        for (int i = 0; i < _nmax; ++i) {
            _value[i] = nullValue();
        }
    }

    @CSharpReplaceBody(code="return default(T);")
    private T nullValue() {
        return null;
    }

    @CSharpReplaceBody(code = "return object.Equals(value, default(T));")
    private boolean isNull(T value) {
        return value == null;
    }

    /**
     * Gets the value for this key.
     *
     * @param key Key
     * @return the value, null if not found.
     */
    public T get(int key) {
        return _value[indexOf(key)];
    }

    /**
     * Gets the size of the map.
     *
     * @return the size (the number of entries in the map)
     */
    public int size() {
        return _n;
    }

    /**
     * Removes a key from the map.
     *
     * @param key Key to remove
     * @return true if the value was removed
     */
    public boolean remove(int key) {
        // Knuth, v. 3, 527, Algorithm R.
        int i = indexOf(key);
        //if (!_filled[i]) {
        if (_value[i] == null) {
            return false;
        }
        --_n;
        for (; ; ) {
            //_filled[i] = false;
            _value[i] = nullValue();
            int j = i;
            int r;
            do {
                i = (i - 1) & _mask;
                //if (!_filled[i]) {
                if (isNull(_value[i])) {
                    return true;
                }
                r = hash(_key[i]);
            } while ((i <= r && r < j) || (r < j && j < i) || (j < i && i <= r));
            _key[j] = _key[i];
            _value[j] = _value[i];
            //_filled[j] = _filled[i];
        }
    }

    /**
     * Adds a key-value pair to the map.
     *
     * @param key   Key
     * @param value Value
     * @return the value that was previously associated with the key, or null if there was no previous value
     */
    public T put(int key, /*@Nullable*/ T value) {
        if (isNull(value)) {
            throw new NullPointerException("IntHashMap does not allow null values");
        }
        int i = indexOf(key);
        T old = _value[i];
        if (!isNull(old)) {
            _value[i] = value;
        } else {
            _key[i] = key;
            _value[i] = value;
            grow();
        }
        return old;
    }


    ///////////////////////////////////////////////////////////////////////////
    // private

    private static final int NBIT = 30; // NMAX = 2^NBIT
    private static final int NMAX = 1 << NBIT; // maximum number of keys mapped
    private final double _factor; // 0.0 <= _factor <= 1.0
    private int _nmax; // 0 <= _nmax = 2^nbit <= 2^NBIT = NMAX
    private int _n; // 0 <= _n <= _nmax <= NMAX
    private int _nlo; // _nmax*_factor (_n<=_nlo, if possible)
    private int _nhi; //  NMAX*_factor (_n< _nhi, if possible)
    private int _shift; // _shift = 1 + NBIT - nbit (see function hash() below)
    private int _mask; // _mask = _nmax - 1
    private int[] _key; // array[_nmax] of keys
    //@SuppressWarnings(value = {"unchecked"})
    private T[] _value; // array[_nmax] of values
    //private boolean[] _filled; // _filled[i]==true iff _key[i] is mapped

    private int hash(int key) {
        // Knuth, v. 3, 509-510. Randomize the 31 low-order bits of c*key
        // and return the highest nbits (where nbits <= 30) bits of these.
        // The constant c = 1327217885 approximates 2^31 * (sqrt(5)-1)/2.
        return ((1327217885 * key) >> _shift) & _mask;
    }

    private int indexOf(int key) {
        int i = hash(key);
        //while (_filled[i]) {
        while (!isNull(_value[i])) {
            if (_key[i] == key) {
                return i;
            }
            i = (i - 1) & _mask;
        }
        return i;
    }

    private void grow() {
        ++_n;
        if (_n > NMAX) {
            throw new RuntimeException("number of keys mapped exceeds " + NMAX);
        }
        if (_nlo < _n && _n <= _nhi) {
            setCapacity(_n);
        }
    }

    private void setCapacity(int capacity) {
        if (capacity < _n) {
            capacity = _n;
        }
        double factor = (_factor < 0.01) ? 0.01 : (_factor > 0.99) ? 0.99 : _factor;
        int nbit, nmax;
        for (nbit = 1, nmax = 2; nmax * factor < capacity && nmax < NMAX; ++nbit, nmax *= 2) {
            // no-op
        }
        int nold = _nmax;
        if (nmax == nold) {
            return;
        }
        _nmax = nmax;
        _nlo = (int) (nmax * factor);
        _nhi = (int) (NMAX * factor);
        _shift = 1 + NBIT - nbit;
        _mask = nmax - 1;
        int[] key = _key;
        T[] value = _value;
        //boolean[] filled = _filled;
        _n = 0;
        _key = new int[nmax];
        // semantically equivalent to _value = new V[nmax]
        _value = makeValueArray(nmax);
        //_filled = new boolean[nmax];
        if (key != null) {
            for (int i = 0; i < nold; ++i) {
                //if (filled[i]) {
                if (!isNull(value[i])) {
                    put(key[i], value[i]);
                }
            }
        }
    }

    @CSharpReplaceBody(code="return new T[size];")
    private T[] makeValueArray(int size) {
        return (T[]) new Object[size];
    }

    /**
     * Get an iterator over the keys
     *
     * @return an iterator over the integer keys in the map
     */

    @CSharpReplaceBody(code="return new Saxon.Hej.z.IntHashMap<T>.IntHashMapKeyIterator<T>(this);")
    public IntIterator keyIterator() {
        return new IntHashMapKeyIterator<T>(this);
    }

    /**
     * Get an iterator over the values
     *
     * @return an iterator over the values in the map
     */

    @CSharpReplaceBody(code = "return new Saxon.Hej.z.IntHashMap<T>.IntHashMapValueIterator<T>(this);")
    public Iterator<T> valueIterator() {
        return new IntHashMapValueIterator<T>(this);
    }

    /**
     * Get the collection of values. (Despite the name, this is not a set: it may contain duplicates.)
     * @return the set of values as an iterable collection
     */

    @CSharpReplaceBody(code = "return new Saxon.Hej.z.IntHashMap<T>.IntHashMapValueSet<T>(this);")
    public Iterable<T> valueSet() {
        return this::valueIterator;
    }

    /**
     * Create a copy of the IntHashMap
     *
     * @return a copy of this map
     */

    public IntHashMap<T> copy() {
        IntHashMap<T> n = new IntHashMap<>(size());
        IntIterator it = keyIterator();
        while (it.hasNext()) {
            int k = it.next();
            n.put(k, get(k));
        }
        return n;
    }

//    /**
//     * Diagnostic display of contents
//     */
//
//    public void display(PrintStream ps) {
//        IntIterator iter = new IntHashMapKeyIterator<T>(this);
//        while (iter.hasNext()) {
//            int key = iter.next();
//            Object value = get(key);
//            ps.println(key + " -> " + value.toString());
//        }
//    }

    /**
     * Iterator over keys
     * @implNote implemented as a static inner class for ease of conversion to C#
     */
    private static class IntHashMapKeyIterator<V> implements IntIterator {

        private int i = 0;
        private final IntHashMap<V> map;

        public IntHashMapKeyIterator(IntHashMap<V> map) {
            this.map = map;
            i = 0;
        }

        @Override
        public boolean hasNext() {
            while (i < map._key.length) {
                if (!map.isNull(map._value[i])) {
                    return true;
                } else {
                    i++;
                }
            }
            return false;
        }

        @Override
        public int next() {
            return map._key[i++];
        }
    }

    /**
     * Iterator over values
     * @implNote implemented as a static inner class for ease of conversion to C#
     */
    private static class IntHashMapValueIterator<W> implements Iterator<W> {

        private int i = 0;
        private final IntHashMap<W> map;

        public IntHashMapValueIterator(IntHashMap<W> map) {
            this.map = map;
            i = 0;
        }

        @Override
        public boolean hasNext() {
            while (i < map._key.length) {
                if (!map.isNull(map._value[i])) {
                    return true;
                } else {
                    i++;
                }
            }
            return false;
        }

        @Override
        public W next() {
            W temp = map._value[i++];
            if (map.isNull(temp)) {
                throw new NoSuchElementException();
            }
            return temp;
        }

        /**
         * Removes from the underlying collection the last element returned by the
         * iterator (optional operation).
         *
         * @throws UnsupportedOperationException if the <code>remove</code>
         *                                       operation is not supported by this Iterator.
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }
    }

    /**
     * Get the set of integer keys present in this IntHashSet
     *
     * @return the set of integer keys present in this IntHashSet
     */

    @CSharpReplaceBody(code="return new Saxon.Hej.z.IntHashMap<T>.IntHashMapKeySet<T>(this);")
    public IntSet keySet() {
        return new IntHashMapKeySet<T>(this);
    }

    private static class IntHashMapKeySet<U> extends IntSet {
        private final IntHashMap<U> map;
        public IntHashMapKeySet(IntHashMap<U> map) {
            this.map = map;
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Immutable set");
        }

        @Override
        public IntSet copy() {
            IntHashSet s = new IntHashSet();
            IntIterator ii = iterator();
            while (ii.hasNext()) {
                s.add(ii.next());
            }
            return s;
        }

        @Override
        public IntSet mutableCopy() {
            return copy();
        }

        @Override
        public boolean isMutable() {
            return false;
        }

        @Override
        public int size() {
            return map._n;
        }

        @Override
        public boolean isEmpty() {
            return map._n == 0;
        }

        @Override
        public boolean contains(int key) {
            return map._value[map.indexOf(key)] != null;
        }

        @Override
        public boolean remove(int value) {
            throw new UnsupportedOperationException("Immutable set");
        }

        @Override
        public boolean add(int value) {
            throw new UnsupportedOperationException("Immutable set");
        }

        @Override
        @CSharpReplaceBody(code="return new Saxon.Hej.z.IntHashMap<U>.IntHashMapKeyIterator<U>(map);")
        public IntIterator iterator() {
            return new IntHashMapKeyIterator<U>(map);
        }

        @Override
        public IntSet union(IntSet other) {
            return copy().union(other);
        }

        @Override
        public IntSet intersect(IntSet other) {
            return copy().intersect(other);
        }

        @Override
        public IntSet except(IntSet other) {
            return copy().except(other);
        }

        @Override
        public boolean containsAll(IntSet other) {
            return copy().containsAll(other);
        }

        public String toString() {
            return IntHashSet.stringify(iterator());
        }
    }



}

