////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.NumericValue;

/**
 * An AtomicComparer used for sorting values that are known to be instances of xs:decimal (including xs:integer),
 * It also supports a separate method for getting a collation key to test equality of items
 */

public class DecimalSortComparer implements AtomicComparer {

    private static final DecimalSortComparer THE_INSTANCE = new DecimalSortComparer();

    public static DecimalSortComparer getDecimalSortComparerInstance() {
        return THE_INSTANCE;
    }

    private DecimalSortComparer() {
    }

    /*@Nullable*/
    @Override
    public StringCollator getCollator() {
        return null;
    }

    /**
     * Supply the dynamic context in case this is needed for the comparison
     *
     * @param context the dynamic evaluation context
     * @return either the original AtomicComparer, or a new AtomicComparer in which the context
     * is known. The original AtomicComparer is not modified
     */

    @Override
    public AtomicComparer provideContext(XPathContext context) {
        return this;
    }

    /**
     * Compare two AtomicValue objects according to the rules for their data type. UntypedAtomic
     * values are compared as if they were strings; if different semantics are wanted, the conversion
     * must be done by the caller.
     *
     * @param a the first object to be compared. This must be an AtomicValue and it must implement
     *          Comparable with context-free XPath comparison semantics
     * @param b the second object to be compared. This must be an AtomicValue and it must implement
     *          Comparable with context-free XPath comparison semantics
     * @return &lt;0 if a&lt;b, 0 if a=b, &gt;0 if a&gt;b
     * @throws ClassCastException if the objects are not comparable
     */

    @Override
    public int compareAtomicValues(AtomicValue a, AtomicValue b) {
        if (a == null) {
            return (b == null ? 0 : -1);
        } else if (b == null) {
            return +1;
        }
        return ((NumericValue)a).compareTo((NumericValue)b);
    }

    /**
     * Compare two AtomicValue objects for equality according to the rules for their data type. UntypedAtomic
     * values are compared by converting to the type of the other operand.
     *
     * @param a the first object to be compared. This must be an AtomicValue and it must implement
     *          equals() with context-free XPath comparison semantics
     * @param b the second object to be compared. This must be an AtomicValue and it must implement
     *          equals() with context-free XPath comparison semantics
     * @return true if the values are equal, false if not
     * @throws ClassCastException if the objects are not comparable
     */

    @Override
    public boolean comparesEqual(AtomicValue a, AtomicValue b) {
        return a.equals(b);
    }

    /**
     * Create a string representation of this AtomicComparer that can be saved in a compiled
     * package and used to reconstitute the AtomicComparer when the package is reloaded
     *
     * @return a string representation of the AtomicComparer
     */
    @Override
    public String save() {
        return "DecSC";
    }
}

