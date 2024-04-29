////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.z.IntHashMap;

/**
 * A mutable set implementation using a custom equality matcher, to avoid having to wrap
 * each entry in a wrapper object to achieve customised equality testing.
 *
 * <p>Note: this doesn't implement {@code java.util.Set<T>}. This is to save the effort of implementing
 * methods like size() and iterator() that aren't required for our use cases. These
 * methods could easily be added without changing the basic design.
 * </p>
 *
 * @param <T> The type of the entries in the set
 */

public class CustomSet<T>  {

    private final IntHashMap<CustomSetEntryChain<T>> buckets;
    private final EqualityMatcher<T> equalityMatcher;

    /**
     * Create an empty set using the supplied equality matcher
     * @param matcher the function used to compare items for equality
     */
    public CustomSet(EqualityMatcher<T> matcher) {
        buckets = new IntHashMap<>();
        equalityMatcher = matcher;
    }

    /**
     * Add a value to the set
     * @param value the value to be added.
     * @return true if the value is newly added, that is, if it was not already present
     */
    public boolean add(T value) {
        int h = equalityMatcher.hash(value);
        CustomSetEntryChain<T> bucket = buckets.get(h);
        if (bucket == null) {
            CustomSetEntryChain<T> entry = new CustomSetEntryChain<>(value);
            buckets.put(h, entry);
            return true;
        } else {
            while (true) {
                if (equalityMatcher.equal(bucket.value, value)) {
                    return false;
                } else if (bucket.next == null) {
                    @SuppressWarnings("UnnecessaryLocalVariable")
                    CustomSetEntryChain<T> entry = new CustomSetEntryChain<>(value);
                    bucket.next = entry;
                    return true;
                } else {
                    bucket = bucket.next;
                }
            }
        }
    }

    /**
     * Ask if a value exists in the set
     * @param value the requested value
     * @return true if it exists
     */

    public boolean contains(T value) {
        int h = equalityMatcher.hash(value);
        CustomSetEntryChain<T> bucket = buckets.get(h);
        if (bucket == null) {
            return false;
        } else {
            while (true) {
                if (equalityMatcher.equal(bucket.value, value)) {
                    return true;
                } else if (bucket.next == null) {
                    return false;
                } else {
                    bucket = bucket.next;
                }
            }
        }
    }




}

