////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.CompareToConstant;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Token;
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
    private final transient XPathContext context;

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
                case StandardNames.XS_INTEGER:
                case StandardNames.XS_DECIMAL:
                case StandardNames.XS_DOUBLE:
                case StandardNames.XS_FLOAT:
                case StandardNames.XS_DAY_TIME_DURATION:
                case StandardNames.XS_YEAR_MONTH_DURATION:
                case StandardNames.XS_BASE64_BINARY:
                case StandardNames.XS_HEX_BINARY:
                    return ContextFreeAtomicComparer.getInstance();

                case StandardNames.XS_QNAME:
                case StandardNames.XS_NOTATION:
                    return EqualityComparer.getInstance();

            }
        }

        if (type0.isPrimitiveNumeric() && type1.isPrimitiveNumeric()) {
            return ContextFreeAtomicComparer.getInstance();
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

    /**
     * An {@code AtomicComparisonFunction} compares two atomic values to return a result
     * of true or false. This means it is committed to a particular comparison operator (such
     * as equals or less than). The supplied atomic values must be non-null.
     */
    @FunctionalInterface
    public interface AtomicComparisonFunction {
        /**
         * Compare two atomic values
         * @param v0 the first atomic value: must not be null
         * @param v1 the second atomic value: must not be null
         * @param context the XPath evaluation context, in case the comparison is context-sensitive
         * @return the result of the comparison
         * @throws XPathException if the values are not comparable, or if the context does not
         * supply the information needed to compare them (such as implicit timezone or collation)
         */
        boolean compare(AtomicValue v0, AtomicValue v1, XPathContext context) throws XPathException;
    }

    /**
     * Get an atomic comparison function for two atomic values that implement the
     * {@link XPathComparable} interface
     * @param operator the operator to be used for the comparison, for example {@link Token#FEQ}
     * @return a function to perform the comparison
     */

    private static AtomicComparisonFunction getContextFreeComparisonFunction(int operator) {
        return (a, b, context) -> {
            int comp = ((XPathComparable) a).compareTo((XPathComparable) b);
            return CompareToConstant.interpretComparisonResult(operator, comp);
        };
    }

    /**
     * Get an atomic comparison function for two xs:float or xs:double values
     * @param operator the operator to be used for the comparison, for example {@link Token#FEQ}
     * @return a function to perform the comparison
     */

    private static AtomicComparisonFunction getFloatingPointComparisonFunction(int operator) {
        return (a, b, context) -> {
            if (a.isNaN() || b.isNaN()) {
                return operator == Token.FNE;
            }
            int comp = ((XPathComparable) a).compareTo((XPathComparable) b);
            return CompareToConstant.interpretComparisonResult(operator, comp);
        };
    }

    /**
     * Return the integer fingerprint of a type, after promoting the type fpr comparison
     * purposes. Numeric types are promoted to xs:double, while xs:anyURI and xs:untypedAtomic
     * are promoted to xs:string
     * @param type the input type
     * @param version the XPath language version
     * @return the fingerprint of the type after promotion
     */

    private static int applyPromotion(BuiltInAtomicType type, int version) {
        if (type.isPrimitiveNumeric()) {
            return StandardNames.XS_DOUBLE;
        }
        int fp = type.getFingerprint();
        if (fp == StandardNames.XS_UNTYPED_ATOMIC || fp == StandardNames.XS_ANY_URI) {
            return StandardNames.XS_STRING;
        } else if (fp == StandardNames.XS_HEX_BINARY && version >= 40) {
            return StandardNames.XS_BASE64_BINARY;
        } else {
            return fp;
        }
    }

    /**
     * Factory method to make a ComparisonFunction for values of known types
     *
     * @param type0    primitive type of the first operand
     * @param type1    primitive type of the second operand
     * @param collator the collation to be used, if any. This is supplied as a SimpleCollation object
     *                 which encapsulated both the collation URI and the collation itself.
     * @param operator  the comparison operator, fpr example {@link Token#FEQ}
     * @param allowRecursion flag to stop infinite recursion (the function recurses if the static types
     *                       are not known; it then relies on testing the run-time types)
     * @return a comparison function for two atomic values (neither of which may be null)
     */

    public static AtomicComparisonFunction makeAtomicComparisonFunction(
            BuiltInAtomicType type0, BuiltInAtomicType type1,
            StringCollator collator, int operator,
            boolean allowRecursion, int version) {

        int fp0 = applyPromotion(type0, version);
        int fp1 = applyPromotion(type1, version);

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
                    return (a, b, context) -> {
                        int comp = ((CalendarValue) a).compareTo((CalendarValue) b, context.getImplicitTimezone());
                        return CompareToConstant.interpretComparisonResult(operator, comp);
                    };

                case StandardNames.XS_DOUBLE:
                case StandardNames.XS_FLOAT:
                    return getFloatingPointComparisonFunction(operator);

                case StandardNames.XS_BOOLEAN:
                case StandardNames.XS_INTEGER:
                case StandardNames.XS_DECIMAL:
                case StandardNames.XS_DAY_TIME_DURATION:
                case StandardNames.XS_YEAR_MONTH_DURATION:
                case StandardNames.XS_BASE64_BINARY:
                case StandardNames.XS_HEX_BINARY:
                    return getContextFreeComparisonFunction(operator);

                case StandardNames.XS_QNAME:
                case StandardNames.XS_NOTATION:
                    switch (operator) {
                        case Token.FEQ:
                            return (a, b, context) -> a.equals(b);
                        case Token.FNE:
                            return (a, b, context) -> !a.equals(b);
                        default:
                            return (a, b, context) -> {
                                throw new XPathException(type0 + " values cannot be compared for ordering", "XPTY0004");
                            };
                    }

                case StandardNames.XS_STRING:
                    if (collator instanceof CodepointCollator && operator == Token.FEQ) {
                        return (a, b, context) -> a.equals(b);
                    }
                    if (collator instanceof CodepointCollator && operator == Token.FNE) {
                        return (a, b, context) -> !a.equals(b);
                    } else {
                        return (a, b, context) -> {
                            int comp = collator.compareStrings(a.getUnicodeStringValue(), b.getUnicodeStringValue());
                            return CompareToConstant.interpretComparisonResult(operator, comp);
                        };
                    }

            }
        }

        if (type0.isDurationType() && type1.isDurationType()) {
            // potentially different subtypes of xs:duration - only equality comparison allowed
            switch (operator) {
                case Token.FEQ:
                    return (a, b, context) -> a.equals(b);
                case Token.FNE:
                    return (a, b, context) -> !a.equals(b);
                default:
                    // fall through and try again using the run-time types
                    break;
            }
        }

        if (allowRecursion) {
            // Get a comparison function using the run-time types rather than the static types
            // We remember the function used the first time through, and reuse it if the types are the same
            final BuiltInAtomicType[] firstTimeTypes = new BuiltInAtomicType[2];
            final AtomicComparisonFunction[] firstTimeFunction = new AtomicComparisonFunction[1];
            return (a, b, context) -> {
                BuiltInAtomicType at = a.getPrimitiveType();
                BuiltInAtomicType bt = b.getPrimitiveType();
                synchronized(firstTimeFunction) {
                    if (firstTimeFunction[0] == null) {
                        AtomicComparisonFunction comparisonFunction =
                                makeAtomicComparisonFunction(at, bt, collator, operator, false, version);
                        firstTimeFunction[0] = comparisonFunction;
                        firstTimeTypes[0] = at;
                        firstTimeTypes[1] = bt;
                        return comparisonFunction.compare(a, b, context);
                    } else {
                        if (firstTimeTypes[0] == at && firstTimeTypes[1] == bt) {
                            return firstTimeFunction[0].compare(a, b, context);
                        } else {
                            AtomicComparisonFunction comparisonFunction =
                                    makeAtomicComparisonFunction(at, bt, collator, operator, false, version);
                            return comparisonFunction.compare(a, b, context);
                        }
                    }
                }
            };
        } else {
            return (a, b, context) -> {
                throw new XPathException("Values are not comparable (" +
                              Type.displayTypeName(a) + ", " + Type.displayTypeName(b) + ')', "XPTY0004", context);

            };
        }
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
            return collator.compareStrings(a.getUnicodeStringValue(), b.getUnicodeStringValue());
        } else {
            int implicitTimezone = context.getImplicitTimezone();
            XPathComparable ac = a.getXPathComparable(collator, implicitTimezone);
            XPathComparable bc = b.getXPathComparable(collator, implicitTimezone);
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
            return collator.comparesEqual(a.getUnicodeStringValue(), b.getUnicodeStringValue());
        } else if (a instanceof CalendarValue && b instanceof CalendarValue) {
            return ((CalendarValue) a).compareTo((CalendarValue) b, context.getImplicitTimezone()) == 0;
        } else {
            int implicitTimezone = context.getImplicitTimezone();
            AtomicMatchKey ac = a.getXPathMatchKey(collator, implicitTimezone);
            AtomicMatchKey bc = b.getXPathMatchKey(collator, implicitTimezone);
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


