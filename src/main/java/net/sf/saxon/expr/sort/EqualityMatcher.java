////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

/**
 * Interface representing objects used to perform equality matching. This is used in preference to wrapping
 * the objects in a wrapper with its own equals() and hashCode() methods, to avoid the overhead of wrapper
 * objects in a large data structure
 * @param <T> the type of objects to be compared for equality
 */

public interface EqualityMatcher<T> {

    /**
     * Compare two objects for equality
     * @param a one object
     * @param b another object
     * @return true if the two objects are deemed equal
     */

    boolean equal(T a, T b);

    /**
     * Compute a hash code for an object
     * @param a an object
     * @return a hash code, which has the property that if two objects are equal, then they must have the
     * same hash code
     */

    int hash(T a);
}

