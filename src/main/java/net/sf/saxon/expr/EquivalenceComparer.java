////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.CalendarValue;
import net.sf.saxon.value.StringValue;

/**
 * A comparer that compares atomic values for equality, with the properties:
 * - non-comparable types compare false
 * - NaN compares equal to NaN
 */
public class EquivalenceComparer extends GenericAtomicComparer {

    protected EquivalenceComparer(StringCollator collator, XPathContext context) {
        super(collator, context);
    }

    @Override
    public EquivalenceComparer provideContext(XPathContext context) {
        return new EquivalenceComparer(getStringCollator(), context);
    }

    /**
     * Compare two AtomicValue objects for equality according to the rules for their data type. UntypedAtomic
     * values are compared as if they were strings; if different semantics are wanted, the conversion
     * must be done by the caller.
     *
     * @param a the first object to be compared. If it is a StringValue, the
     *          collator is used to compare the values, otherwise the value must implement the equals() method.
     * @param b the second object to be compared. This must be comparable with the first object: for
     *          example, if one is a string, they must both be strings.
     * @return true if the values are equal, false if not
     * @throws ClassCastException if the objects are not comparable
     */

    @Override
    public boolean comparesEqual (AtomicValue a, AtomicValue b) throws NoDynamicContextException {
        // System.err.println("Comparing " + a.getClass() + ": " + a + " with " + b.getClass() + ": " + b);
        if (a instanceof StringValue && b instanceof StringValue) {
            return getStringCollator().comparesEqual(a.getStringValue(), b.getStringValue());
        } else if (a instanceof CalendarValue && b instanceof CalendarValue) {
            return ((CalendarValue) a).compareTo((CalendarValue) b, getContext().getImplicitTimezone()) == 0;
        } else if (a.isNaN() && b.isNaN()) {
            return true;
        } else {
            int implicitTimezone = getContext().getImplicitTimezone();
            Object ac = a.getXPathComparable(false, getStringCollator(), implicitTimezone);
            Object bc = b.getXPathComparable(false, getStringCollator(), implicitTimezone);
            return ac.equals(bc);
        }
    }



//    /**
//     * Compare two values that are known to be non-comparable. In the base class this method
//     * throws a ClassCastException. In this subclass it is overridden to return
//     * {@link net.sf.saxon.om.SequenceTool#INDETERMINATE_ORDERING}
//     */
//
//    @Override
//    protected int compareNonComparables(AtomicValue a, AtomicValue b) {
//        return SequenceTool.INDETERMINATE_ORDERING;
//    }

    /**
     * Create a string representation of this AtomicComparer that can be saved in a compiled
     * package and used to reconstitute the AtomicComparer when the package is reloaded
     *
     * @return a string representation of the AtomicComparer
     */
    @Override
    public String save() {
        return "EQUIV|" + super.save();
    }
}

// Copyright (c) 2010-2020 Saxonica Limited



