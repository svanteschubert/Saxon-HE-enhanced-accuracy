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
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.CalendarValue;
import net.sf.saxon.value.StringValue;

/**
 * An AtomicComparer used for comparing atomic values of arbitrary item types. It encapsulates
 * a Collator that is used when the values to be compared are strings. It also supports
 * a separate method for testing equality of items, which can be used for data types that
 * are not ordered.
 *
 */

public class GenericAtomicComparer implements AtomicComparer {

    private StringCollator collator;
    private transient XPathContext context;

    /**
     * Create an GenericAtomicComparer
     *
     * @param collator          the collation to be used
     * @param conversionContext a context, used when converting untyped atomic values to the target type.
     */

    public GenericAtomicComparer(/*@Nullable*/ StringCollator collator, XPathContext conversionContext) {
        this.collator = collator;
        if (collator == null) {
            this.collator = CodepointCollator.getInstance();
        }
        context = conversionContext;
    }

    /**
     * Factory method to make a GenericAtomicComparer for values of known types
     *
     * @param type0    primitive type of the first operand
     * @param type1    primitive type of the second operand
     * @param collator the collation to be used, if any. This is supplied as a SimpleCollation object
     *                 which encapsulated both the collation URI and the collation itself.
     * @param context  the dynamic context
     * @return a GenericAtomicComparer for values of known types
     */

    public static AtomicComparer makeAtomicComparer(
            BuiltInAtomicType type0, BuiltInAtomicType type1, StringCollator collator, XPathContext context) {
        int fp0 = type0.getFingerprint();
        int fp1 = type1.getFingerprint();
        if (fp0 == fp1) {
            switch (fp0) {
                case StandardNames.XS_DATE_TIME:
                case StandardNames.XS_DATE:
                case StandardNames.XS_TIME:
                case StandardNames.XS_G_DAY:
                case StandardNames.XS_G_MONTH:
                case StandardNames.XS_G_YEAR:
                case StandardNames.XS_G_MONTH_DAY:
                case StandardNames.XS_G_YEAR_MONTH:
                    return new CalendarValueComparer(context);

                case StandardNames.XS_BOOLEAN:
                case StandardNames.XS_DAY_TIME_DURATION:
                case StandardNames.XS_YEAR_MONTH_DURATION:
                    return ComparableAtomicValueComparer.getInstance();

                case StandardNames.XS_BASE64_BINARY:
                case StandardNames.XS_HEX_BINARY:
                    // These become fully comparable in XPath 3.1 but we allow it regardless of version
                    return ComparableAtomicValueComparer.getInstance();

                case StandardNames.XS_QNAME:
                case StandardNames.XS_NOTATION:
                    return EqualityComparer.getInstance();

            }
        }

        if (type0.isPrimitiveNumeric() && type1.isPrimitiveNumeric()) {
            return ComparableAtomicValueComparer.getInstance();
        }

        if ((fp0 == StandardNames.XS_STRING ||
                fp0 == StandardNames.XS_UNTYPED_ATOMIC ||
                fp0 == StandardNames.XS_ANY_URI) &&
                (fp1 == StandardNames.XS_STRING ||
                        fp1 == StandardNames.XS_UNTYPED_ATOMIC ||
                        fp1 == StandardNames.XS_ANY_URI)) {
            if (collator instanceof CodepointCollator) {
                return CodepointCollatingComparer.getInstance();
            } else {
                return new CollatingAtomicComparer(collator);
            }
        }
        return new GenericAtomicComparer(collator, context);
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
    public GenericAtomicComparer provideContext(XPathContext context) {
        return new GenericAtomicComparer(collator, context);
    }

    /**
     * Get the underlying string collator
     *
     * @return the string collator
     */

    public StringCollator getStringCollator() {
        return collator;
    }

    /**
     * Compare two AtomicValue objects according to the rules for their data type. UntypedAtomic
     * values are compared as if they were strings; if different semantics are wanted, the conversion
     * must be done by the caller.
     *
     * @param a the first object to be compared. It is intended that this should be an instance
     *          of AtomicValue, though this restriction is not enforced. If it is a StringValue, the
     *          collator is used to compare the values, otherwise the value must implement the java.util.Comparable
     *          interface.
     * @param b the second object to be compared. This must be comparable with the first object: for
     *          example, if one is a string, they must both be strings.
     * @return &lt;0 if a &lt; b, 0 if a = b, &gt;0 if a &gt; b
     * @throws ClassCastException        if the objects are not comparable
     * @throws NoDynamicContextException if this comparer required access to dynamic context information,
     *                                   notably the implicit timezone, and this information is not available. In general this happens if a
     *                                   context-dependent comparison is attempted at compile-time, and it signals the compiler to generate
     *                                   code that tries again at run-time.
     */

    @Override
    public int compareAtomicValues(AtomicValue a, AtomicValue b) throws NoDynamicContextException {

        // System.err.println("Comparing " + a.getClass() + "(" + a + ") with " + b.getClass() + "(" + b + ") using " + collator);

        if (a == null) {
            return b == null ? 0 : -1;
        } else if (b == null) {
            return +1;
        }

        if (a instanceof StringValue && b instanceof StringValue) {
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
                XPathException e = new XPathException("Objects are not comparable (" +
                        Type.displayTypeName(a) + ", " + Type.displayTypeName(b) + ')', "XPTY0004");
                throw new ComparisonException(e);
            } else {
                return ac.compareTo(bc);
            }
        }
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
    public boolean comparesEqual(AtomicValue a, AtomicValue b) throws NoDynamicContextException {
        // System.err.println("Comparing " + a.getClass() + ": " + a + " with " + b.getClass() + ": " + b);
        if (a instanceof StringValue && b instanceof StringValue) {
            return collator.comparesEqual(a.getStringValue(), b.getStringValue());
        } else if (a instanceof CalendarValue && b instanceof CalendarValue) {
            return ((CalendarValue) a).compareTo((CalendarValue) b, context.getImplicitTimezone()) == 0;
        } else {
            int implicitTimezone = context.getImplicitTimezone();
            Object ac = a.getXPathComparable(false, collator, implicitTimezone);
            Object bc = b.getXPathComparable(false, collator, implicitTimezone);
            return ac.equals(bc);
        }
    }

    public XPathContext getContext() {
        return context;
    }

    /**
     * Create a string representation of this AtomicComparer that can be saved in a compiled
     * package and used to reconstitute the AtomicComparer when the package is reloaded
     *
     * @return a string representation of the AtomicComparer
     */
    @Override
    public String save() {
        return "GAC|" + collator.getCollationURI();
    }

    @Override
    public int hashCode() {
        return collator.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        // In considering whether two GenericAtomicComparers are equal, we ignore the dynamic context, because this
        // is only ever used to test the implicit timezone, and in all reasonable scenarios, the implicit timezone
        // is global.
        return obj instanceof GenericAtomicComparer && collator.equals(((GenericAtomicComparer)obj).collator);
    }
}


