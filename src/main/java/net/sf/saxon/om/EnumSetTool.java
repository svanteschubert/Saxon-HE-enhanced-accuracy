////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.om;

import java.util.EnumSet;

/**
 * A couple of utility methods for handling EnumSet objects.
 */

public class EnumSetTool {

    /**
     * Return a new EnumSet as the intersection of two supplied EnumSets. Neither is modified.
     * @param a the first EnumSet
     * @param b the second EnumSet
     * @param <P> the Enum class common to both arguments
     * @return the combined EnumSet
     */

    public static <P extends Enum<P>> EnumSet<P> intersect(EnumSet<P> a, EnumSet<P> b) {
        EnumSet<P> e = EnumSet.copyOf(a);
        e.retainAll(b);
        return e;
    }

    /**
     * Return a new EnumSet as the union of two supplied EnumSets. Neither is modified
     *
     * @param a   the first EnumSet
     * @param b   the second EnumSet
     * @param <P> the Enum class common to both arguments
     * @return the combined EnumSet
     */

    public static <P extends Enum<P>> EnumSet<P> union(EnumSet<P> a, EnumSet<P> b) {
        EnumSet<P> e = EnumSet.copyOf(a);
        e.addAll(b);
        return e;
    }
}

