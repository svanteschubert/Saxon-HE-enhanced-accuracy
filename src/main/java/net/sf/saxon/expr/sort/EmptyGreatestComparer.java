////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.value.AtomicValue;

/**
 * A Comparer that modifies a base comparer by sorting empty key values and NaN values last (greatest),
 * as opposed to the default which sorts them first.
 */

public class EmptyGreatestComparer implements AtomicComparer {

    private AtomicComparer baseComparer;

    /**
     * Create an EmptyGreatestComparer
     *
     * @param baseComparer the comparer used to compare non-empty values (which typically sorts empty
     *                     as least)
     */

    public EmptyGreatestComparer(AtomicComparer baseComparer) {
        this.baseComparer = baseComparer;
    }

    /**
     * Get the underlying comparer (which compares empty least)
     *
     * @return the base comparer
     */

    public AtomicComparer getBaseComparer() {
        return baseComparer;
    }

    @Override
    public StringCollator getCollator() {
        return baseComparer.getCollator();
    }

    /**
     * Supply the dynamic context in case this is needed for the comparison
     *
     * @param context the dynamic evaluation context
     * @return either the original AtomicComparer, or a new AtomicComparer in which the context
     *         is known. The original AtomicComparer is not modified
     */

    @Override
    public AtomicComparer provideContext(XPathContext context) {
        AtomicComparer newBase = baseComparer.provideContext(context);
        if (newBase != baseComparer) {
            return new EmptyGreatestComparer(newBase);
        } else {
            return this;
        }
    }

    /**
     * Compare two AtomicValue objects according to the rules for their data type. UntypedAtomic
     * values are compared as if they were strings; if different semantics are wanted, the conversion
     * must be done by the caller.
     *
     * @param a the first object to be compared. It is intended that this should normally be an instance
     *          of AtomicValue, though this restriction is not enforced. If it is a StringValue, the
     *          collator is used to compare the values, otherwise the value must implement the java.util.Comparable
     *          interface.
     * @param b the second object to be compared. This must be comparable with the first object: for
     *          example, if one is a string, they must both be strings.
     * @return &lt;0 if a&lt;b, 0 if a=b, &gt;0 if a&gt;b
     * @throws ClassCastException if the objects are not comparable
     */

    @Override
    public int compareAtomicValues(/*@Nullable*/ AtomicValue a, AtomicValue b) throws NoDynamicContextException {
        if (a == null) {
            if (b == null) {
                return 0;
            } else {
                return +1;
            }
        } else if (b == null) {
            return -1;
        }

        if (a.isNaN()) {
            return b.isNaN() ? 0 : +1;
        } else if (b.isNaN()) {
            return -1;
        }

        return baseComparer.compareAtomicValues(a, b);
    }

    /**
     * Compare two AtomicValue objects for equality according to the rules for their data type. UntypedAtomic
     * values are compared by converting to the type of the other operand.
     *
     * @param a the first object to be compared.
     * @param b the second object to be compared.
     * @return true if the values are equal, false if not
     * @throws ClassCastException if the objects are not comparable
     */

    @Override
    public boolean comparesEqual(AtomicValue a, AtomicValue b) throws NoDynamicContextException {
        return (a == null && b == null) || baseComparer.comparesEqual(a, b);
    }

    /**
     * Create a string representation of this AtomicComparer that can be saved in a compiled
     * package and used to reconstitute the AtomicComparer when the package is reloaded
     *
     * @return a string representation of the AtomicComparer
     */
    @Override
    public String save() {
        return "EG|" + baseComparer.save();
    }
}

