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
import net.sf.saxon.value.StringValue;

/**
 * A Comparer used for comparing sort keys when data-type="text". The items to be
 * compared are converted to strings, and the strings are then compared using an
 * underlying collator
 *
 * @author Michael H. Kay
 */

public class TextComparer implements AtomicComparer {

    private AtomicComparer baseComparer;

    public TextComparer(AtomicComparer baseComparer) {
        this.baseComparer = baseComparer;
    }

    /**
     * Get the underlying comparer (which doesn't do conversion to string)
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
            return new TextComparer(newBase);
        } else {
            return this;
        }
    }


    /**
     * Compare two Items by converting them to strings and comparing the string values.
     *
     * @param a the first Item to be compared.
     * @param b the second Item to be compared.
     * @return &lt;0 if a&lt;b, 0 if a=b, &gt;0 if a&gt;b
     * @throws ClassCastException if the objects are not Items, or are items that cannot be convered
     *                            to strings (e.g. QNames)
     */

    @Override
    public int compareAtomicValues(AtomicValue a, AtomicValue b) throws ClassCastException, NoDynamicContextException {
        return baseComparer.compareAtomicValues(toStringValue(a), toStringValue(b));
    }

    private StringValue toStringValue(AtomicValue a) {
        if (a instanceof StringValue) {
            return (StringValue) a;
        } else {
            return new StringValue(a == null ? "" : a.getStringValue());
        }
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
    public boolean comparesEqual(AtomicValue a, /*@NotNull*/ AtomicValue b) throws NoDynamicContextException {
        return compareAtomicValues(a, b) == 0;
    }

    /**
     * Create a string representation of this AtomicComparer that can be saved in a compiled
     * package and used to reconstitute the AtomicComparer when the package is reloaded
     *
     * @return a string representation of the AtomicComparer
     */
    @Override
    public String save() {
        return "TEXT|" + baseComparer.save();
    }
}

