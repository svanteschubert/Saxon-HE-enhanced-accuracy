////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

/**
 * A set of constants enumerating the possible relationships between one type and another
 */

public enum Affinity {

    /**
     * The two types are identical
     */
    SAME_TYPE,
    /**
     * The first type subsumes the second; all values that are instances of the second type are
     * also instances of the first. For example, xs:decimal subsumes xs:integer; node() subsumes element().
     */
    SUBSUMES,
    /**
     * The second type subsumes the first; all values that are instances of the first type are
     * also instances of the second. For example, xs:integer is subsumed by xs:decimal; element()
     * is subsumed by node()
     */
    SUBSUMED_BY,
    /**
     * The two types have no instances in common; if a value is an instance of one type, then it
     * is not an instance of the other. For example, xs:string and xs:boolean are disjoint, as
     * are element() and attribute()
     */
    DISJOINT,
    /**
     * The two types have intersecting value spaces; there are values that belong to both types,
     * but neither type subsumes the other. For example, union(A, B) and union(A, C) overlap.
     * As a special case, xs:string? and xs:boolean? overlap, because both types allow an empty
     * sequence.
     */
    OVERLAPS
}

