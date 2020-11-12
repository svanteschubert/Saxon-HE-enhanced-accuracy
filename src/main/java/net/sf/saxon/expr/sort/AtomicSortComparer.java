////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.UntypedAtomicValue;

/**
 * An AtomicComparer used for comparing atomic values of arbitrary item types. It encapsulates
 * a collator that is used when the values to be compared are strings. It also supports
 * a separate method for testing equality of items, which can be used for data types that
 * are not ordered.
 * <p>The AtomicSortComparer is identical to the GenericAtomicComparer except for its handling
 * of NaN: it treats NaN values as lower than any other value, and as equal to each other.</p>
 *
 */

public class AtomicSortComparer implements AtomicComparer {

    private StringCollator collator;
    private transient XPathContext context;
    private int itemType;

    /**
     * Factory method to get an atomic comparer suitable for sorting or for grouping (operations in which
     * NaN is considered equal to NaN)
     *
     * @param collator Collating comparer to be used when comparing strings. This argument may be null
     *                 if the itemType excludes the possibility of comparing strings. If the method is called at compile
     *                 time, this should be a SimpleCollation so that it can be cloned at run-time.
     * @param itemType the primitive item type of the values to be compared
     * @param context  Dynamic context (may be an EarlyEvaluationContext)
     * @return a suitable AtomicComparer
     */

    public static AtomicComparer makeSortComparer(StringCollator collator, int itemType, XPathContext context) {
        switch (itemType) {
            case StandardNames.XS_STRING:
            case StandardNames.XS_UNTYPED_ATOMIC:
            case StandardNames.XS_ANY_URI:
                if (collator instanceof CodepointCollator) {
                    return CodepointCollatingComparer.getInstance();
                } else {
                    return new CollatingAtomicComparer(collator);
                }
            case StandardNames.XS_INTEGER:
            case StandardNames.XS_DECIMAL:
                return DecimalSortComparer.getDecimalSortComparerInstance();
            case StandardNames.XS_DOUBLE:
            case StandardNames.XS_FLOAT:
            case StandardNames.XS_NUMERIC:
                return DoubleSortComparer.getInstance();
            case StandardNames.XS_DATE_TIME:
            case StandardNames.XS_DATE:
            case StandardNames.XS_TIME:
                return new CalendarValueComparer(context);
            default:
                // use the general-purpose comparer that handles all types
                return new AtomicSortComparer(collator, itemType, context);
        }

    }

    protected AtomicSortComparer(/*@Nullable*/ StringCollator collator, int itemType, XPathContext context) {
        this.collator = collator;
        if (collator == null) {
            this.collator = CodepointCollator.getInstance();
        }
        this.context = context;
        this.itemType = itemType;
    }

    @Override
    public StringCollator getCollator() {
        return collator;
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
        return new AtomicSortComparer(collator, itemType, context);
    }

    /**
     * Get the underlying StringCollator
     *
     * @return the underlying collator
     */

    public StringCollator getStringCollator() {
        return collator;
    }

    /**
     * Get the requested item type
     *
     * @return the item type
     */

    public int getItemType() {
        return itemType;
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
    public int compareAtomicValues(AtomicValue a, AtomicValue b) throws NoDynamicContextException {

        if (a == null) {
            if (b == null) {
                return 0;
            } else {
                return -1;
            }
        } else if (b == null) {
            return +1;
        }

        // System.err.println("Comparing " + a.getClass() + "(" + a + ") with " + b.getClass() + "(" + b + ") using " + collator);

        // Delete the following five lines to fix bug 3450
        //if (a instanceof UntypedAtomicValue) {
        //    return ((UntypedAtomicValue) a).compareTo(b, collator, context);
        //} else if (b instanceof UntypedAtomicValue) {
        //    return -((UntypedAtomicValue) b).compareTo(a, collator, context);
        //} else
        // End of fix for 3450

        if (a.isNaN()) {
            return b.isNaN() ? 0 : -1;
        } else if (b.isNaN()) {
            return +1;
        } else if (a instanceof StringValue && b instanceof StringValue) {
            if (collator instanceof CodepointCollator) {
                return CodepointCollator.compareCS(a.getStringValueCS(), b.getStringValueCS());
            } else {
                return collator.compareStrings(a.getStringValue(), b.getStringValue());
            }
        } else {
            int implicitTimezone = context.getImplicitTimezone();
            Comparable ac = (Comparable) a.getXPathComparable(true, collator, implicitTimezone);
            Comparable bc = (Comparable) b.getXPathComparable(true, collator, implicitTimezone);
            if (ac == null || bc == null) {
                return compareNonComparables(a, b);
            } else {
                try {
                    return ac.compareTo(bc);
                } catch (ClassCastException e) {
                    String message = "Cannot compare " + a.getPrimitiveType().getDisplayName() +
                            " with " + b.getPrimitiveType().getDisplayName();
                    // Direct users to bug 3450 which explains a 2017 bug fix that may cause previously
                    // working applications to fail
                    if (a instanceof UntypedAtomicValue || b instanceof UntypedAtomicValue) {
                        message += ". Further information: see http://saxonica.plan.io/issues/3450";
                    }
                    throw new ClassCastException(message);
                }
            }
        }
    }

    /**
     * Compare two values that are known to be non-comparable. In the base class this method
     * throws a ClassCastException. In a subclass it is overridden to return
     * {@link net.sf.saxon.om.SequenceTool#INDETERMINATE_ORDERING}
     * @param a the first value to be compared
     * @param b the second value to be compared
     * @return the result of the comparison
     * @throws ClassCastException if the two values are not comparable
     */

    protected int compareNonComparables(AtomicValue a, AtomicValue b) {
        XPathException err = new XPathException("Values are not comparable (" +
                                                        Type.displayTypeName(a) + ", " + Type.displayTypeName(b) + ')',
                                                "XPTY0004");
        throw new ComparisonException(err);
    }

    /**
     * Compare two AtomicValue objects for equality according to the rules for their data type. UntypedAtomic
     * values are compared by converting to the type of the other operand.
     *
     * @param a the first object to be compared. It is intended that this should be an instance
     *          of AtomicValue, though this restriction is not enforced. If it is a StringValue, the
     *          collator is used to compare the values, otherwise the value must implement the equals() method.
     * @param b the second object to be compared. This must be comparable with the first object: for
     *          example, if one is a string, they must both be strings.
     * @return true if the values are equal, false if not
     * @throws ClassCastException if the objects are not comparable
     */

    @Override
    public boolean comparesEqual(AtomicValue a, AtomicValue b) throws NoDynamicContextException {
        return compareAtomicValues(a, b) == 0;
    }

    public static AtomicMatchKey COLLATION_KEY_NaN = new AtomicMatchKey() {
        @Override
        public AtomicValue asAtomic() {
        // The logic here is to choose a value that compares equal to itself but not equal to any other
        // number. We use StructuredQName because it has a simple equals() method.

        return new QNameValue("saxon", "http://saxon.sf.net/collation-key", "NaN");
        }
    };

    /**
     * Create a string representation of this AtomicComparer that can be saved in a compiled
     * package and used to reconstitute the AtomicComparer when the package is reloaded
     *
     * @return a string representation of the AtomicComparer
     */
    @Override
    public String save() {
        return "AtSC|" + itemType + "|" + getCollator().getCollationURI();
    }
}

