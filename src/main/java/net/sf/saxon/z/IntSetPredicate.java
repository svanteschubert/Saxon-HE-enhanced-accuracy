////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;

import java.util.function.IntPredicate;

/**
 * An implementation of IntPredicate that tests whether a given integer is a member
 * of some IntSet
 */
public class IntSetPredicate implements IntPredicate {

    private IntSet set;

    public IntSetPredicate(IntSet set) {
        if (set == null) {
            throw new NullPointerException();
        }
        this.set = set;
    }

    /**
     * Ask whether a given value matches this predicate
     *
     * @param value the value to be tested
     * @return true if the value matches; false if it does not
     */
    @Override
    public boolean test(int value) {
        return set.contains(value);
    }

    /**
     * Returns a composed predicate that represents a short-circuiting logical
     * OR of this predicate and another.  When evaluating the composed
     * predicate, if this predicate is {@code true}, then the {@code other}
     * predicate is not evaluated.
     * <p>
     * <p>Any exceptions thrown during evaluation of either predicate are relayed
     * to the caller; if evaluation of this predicate throws an exception, the
     * {@code other} predicate will not be evaluated.
     *
     * @param other a predicate that will be logically-ORed with this
     *              predicate
     * @return a composed predicate that represents the short-circuiting logical
     * OR of this predicate and the {@code other} predicate
     * @throws NullPointerException if other is null
     */
    @Override
    public IntPredicate or(IntPredicate other) {
        if (other instanceof IntSetPredicate) {
            return new IntSetPredicate(set.union(((IntSetPredicate)other).set));
        } else {
            return IntPredicate.super.or(other);
        }
    }

    /**
     * Get the underlying IntSet
     *
     * @return the underlying IntSet
     */

    public IntSet getIntSet() {
        return set;
    }

    /**
     * Get string representation
     */

    public String toString() {
        return "in {" + set + "}";
    }

    /**
     * Convenience predicate that always matches
     */

    public final static IntPredicate ALWAYS_TRUE = i -> true;

    /**
     * Convenience predicate that never matches
     */

    public final static IntPredicate ALWAYS_FALSE = i -> false;

}
