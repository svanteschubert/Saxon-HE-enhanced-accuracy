////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.OperatorSymbol;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.value.*;

import java.math.BigDecimal;

/**
 * A specialist comparer that implements the rules for comparing an untypedAtomic value
 * (always the first operand) to a numeric value (always the second operand)
 */

public class UntypedNumericComparer implements AtomicComparer {

    private ConversionRules rules = ConversionRules.DEFAULT;

    private static final long[][] bounds = new long[][] {
            // Initialization syntax chosen to be compatible with C#
            new long[] {1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L},
            new long[] {1L, 1L, 10L, 100L, 1000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L, 100_000_000L, 1_000_000_000L, 10_000_000_000L},
            new long[] {1L, 2L, 20L, 200L, 2000L, 20_000L, 200_000L, 2_000_000L, 20_000_000L, 200_000_000L, 2_000_000_000L, 20_000_000_000L},
            new long[] {1L, 3L, 30L, 300L, 3000L, 30_000L, 300_000L, 3_000_000L, 30_000_000L, 300_000_000L, 3_000_000_000L, 30_000_000_000L},
            new long[] {1L, 4L, 40L, 400L, 4000L, 40_000L, 400_000L, 4_000_000L, 40_000_000L, 400_000_000L, 4_000_000_000L, 40_000_000_000L},
            new long[] {1L, 5L, 50L, 500L, 5000L, 50_000L, 500_000L, 5_000_000L, 50_000_000L, 500_000_000L, 5_000_000_000L, 50_000_000_000L},
            new long[] {1L, 6L, 60L, 600L, 6000L, 60_000L, 600_000L, 6_000_000L, 60_000_000L, 600_000_000L, 6_000_000_000L, 60_000_000_000L},
            new long[] {1L, 7L, 70L, 700L, 7000L, 70_000L, 700_000L, 7_000_000L, 70_000_000L, 700_000_000L, 7_000_000_000L, 70_000_000_000L},
            new long[] {1L, 8L, 80L, 800L, 8000L, 80_000L, 800_000L, 8_000_000L, 80_000_000L, 800_000_000L, 8_000_000_000L, 80_000_000_000L},
            new long[] {1L, 9L, 90L, 900L, 9000L, 90_000L, 900_000L, 9_000_000L, 90_000_000L, 900_000_000L, 9_000_000_000L, 90_000_000_000L},
            new long[] {1L, 10L, 100L, 1000L, 10_000L, 100_000L, 1000_000L, 10_000_000L, 100_000_000L, 1000_000_000L, 910000_000_000L, 100_000_000_000L}
    };

    /**
     * Get lower and upper bounds on the number represented by a string written as a simple decimal
     * @param cs the string to be examined
     * @return either an array of 3 long values: the first two give a lower and upper bound on the
     * number represented by the string; the third is 1L if the number is a simple sequence of digits,
     * otherwise 0L. Otherwise, null if this cannot be determined.
     */

    private static long[] getBounds(UnicodeString cs) {
        // Analyze the string to find the leading digit and the number of digits before the decimal point
        boolean simple = true;
        int wholePartLength = 0;
        int firstDigit = -1;
        int decimalPoints = 0;
        int sign = '?';
        for (int i = 0; i < cs.length(); i++) {
            int c = cs.codePointAt(i);
            if (c >= '0' && c <= '9') {
                if (firstDigit < 0) {
                    firstDigit = c - '0';
                }
                if (decimalPoints == 0) {
                    wholePartLength++;
                }
            } else if (c == '-') {
                if (sign != '?' || wholePartLength > 0 || decimalPoints > 0) {
                    simple = false;
                    break;
                }
                sign = c;
            } else if (c == '.') {
                if (decimalPoints > 0) {
                    simple = false;
                    break;
                }
                decimalPoints = 1;
            } else {
                simple = false;
                break;
            }
        }
        if (firstDigit < 0) {
            simple = false;
        }
        if (simple && wholePartLength > 0 && wholePartLength <= 10) {
            long lowerBound = bounds[firstDigit][wholePartLength];
            long upperBound = bounds[firstDigit + 1][wholePartLength];
            long simpleNumber = (decimalPoints == 0) ? 1L : 0L;
            if (sign == '-') {
                return new long[]{-upperBound, -lowerBound, simpleNumber};
            } else {
                return new long[]{lowerBound, upperBound, simpleNumber};
            }
        }
        return null;
    }

    /**
     * Optimized routine to compare an untyped atomic value with a numeric value.
     * This attempts to deliver a quick answer if the comparison is obviously false,
     * without performing the full string-to-double conversion
     * @param a0 the untypedAtomic comparand
     * @param a1 the numeric comparand
     * @param operator the comparison operator: a singleton operator such as Token.FEQ
     * @param rules the conversion rules
     * @param specVersion for example 40 for XPath 4.0
     * @return the result of the comparison
     * @throws XPathException if the first operand is not convertible to a double
     */

    public static boolean quickCompare(
            StringValue a0, NumericValue a1, OperatorSymbol operator, ConversionRules rules, int specVersion)
            throws XPathException {
        if (a1.isNaN()) {
            return operator == OperatorSymbol.FNE;
        }
        int comp = specVersion >= 40
                ? quickComparison40(a0, a1, rules)
                : quickComparison31(a0, a1, rules);
        return switch (operator) {
            case FEQ -> comp == 0;
            case FLE -> comp <= 0;
            case FLT -> comp < 0;
            case FGE -> comp >= 0;
            case FGT -> comp > 0;
            default -> comp != 0;
        };
    }

    /**
     * Optimized routine to compare an untyped atomic value with a numeric value.
     * This attempts to deliver a quick answer if the comparison if obviously false,
     * without performing the full string-to-double conversion
     *
     * @param a0       the untypedAtomic comparand
     * @param a1       the numeric comparand
     * @param rules    the conversion rules
     * @return the result of the comparison (negative if a0 lt a1, 0 if equal, positive if a0 gt a1)
     * @throws XPathException if the first operand is not convertible to a double
     */

    private static int quickComparison31(
            StringValue a0, NumericValue a1, ConversionRules rules)
            throws XPathException {
        double d1 = a1.getDoubleValue();
        UnicodeString cs = Whitespace.trim(a0.getUnicodeStringValue());

        // Analyze the string to find the leading digit and the number of digits before the decimal point
        long[] bounds = getBounds(cs);

        boolean plain = false;
        if (bounds != null) {
            plain = bounds[2] > 0;
            if (bounds[1] < d1) {
                return -1;
            }
            if (bounds[0] > d1) {
                return +1;
            }
        }

        // The quick check was inconclusive, so we now parse the number.
        // We use integer comparison if both sides are simple integers, or double comparison otherwise
        if (plain && a1 instanceof Int64Value) {
            long l0 = Long.parseLong(cs.toString());
            return Long.compare(l0, a1.longValue());
        } else {
            ConversionResult result;
            synchronized(a0) {
                result = BuiltInAtomicType.DOUBLE.getStringConverter(rules).convertString(cs);
            }
            AtomicValue av = result.asAtomic();
            return Double.compare(((DoubleValue)av).getDoubleValue(), d1);
        }

    }

    /**
     * Optimized routine to compare an untyped atomic value with a numeric value.
     * This attempts to deliver a quick answer if the comparison if obviously false,
     * without performing the full string-to-double conversion. This version of the
     * method implements the proposed XPath 4.0 semantics, which first attempt
     * conversion to the type of the other operand.
     *
     * @param a0    the untypedAtomic comparand
     * @param a1    the numeric comparand
     * @param rules the conversion rules
     * @return the result of the comparison (negative if a0 lt a1, 0 if equal, positive if a0 gt a1)
     * @throws XPathException if the first operand is not convertible to a double
     */

    private static int quickComparison40(
            StringValue a0, NumericValue a1, ConversionRules rules)
            throws XPathException {

        if (a1 instanceof DoubleValue || a1 instanceof FloatValue) {
            return quickComparison31(a0, a1, rules);
        }

        UnicodeString cs = Whitespace.trim(a0.getUnicodeStringValue());

        // Analyze the string to find the leading digit and the number of digits before the decimal point
        long[] bounds = getBounds(cs);

        // Compare the string as if by converting it to the primitive type of the other operand
        boolean plain;
        if (bounds != null) {
            plain = bounds[2] > 0;
            if (a1 instanceof IntegerValue) {
                long a1L = a1.longValue();
                if (bounds[1] < a1L) {
                    return -1;
                }
                if (bounds[0] > a1L) {
                    return +1;
                }
                if (plain) {
                    long a0L = Long.parseLong(cs.toString());
                    return Long.compare(a0L, a1L);
                }
            }

            BigDecimal a1D = a1.getDecimalValue();
            if (BigDecimal.valueOf(bounds[1]).compareTo(a1D) < 0) {
                return -1;
            }
            if (BigDecimal.valueOf(bounds[0]).compareTo(a1D) > 0) {
                return +1;
            }

        }

        // The quick check was inconclusive, so we now parse the number.

        ConversionResult result;
        synchronized(a0) {
            result = BuiltInAtomicType.DECIMAL.getStringConverter(rules).convertString(cs);
            if (result instanceof DecimalValue) {
                return ((DecimalValue) result).getDecimalValue().compareTo(a1.getDecimalValue());
            }
            // if it can't be converted to decimal, try double
            result = BuiltInAtomicType.DOUBLE.getStringConverter(rules).convertString(cs);
            DoubleValue av = (DoubleValue)result.asAtomic();
            return av.transitiveCompareTo(a1);
        }


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
     * @return &lt;0 if a&lt;b, 0 if a=b, &gt;0 if a&gt;b
     * @throws ClassCastException        if the objects are not comparable
     */
    @Override
    public int compareAtomicValues(AtomicValue a, AtomicValue b) {
        try {
            return quickComparison31((StringValue)a, (NumericValue)b, rules);
        } catch (XPathException e) {
            throw new ComparisonException(e);
        }
    }

    /**
     * Get the collation used by this AtomicComparer if any
     *
     * @return the collation used for comparing strings, or null if not applicable
     */
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
        rules = context.getConfiguration().getConversionRules();
        return this;
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
    public boolean comparesEqual(AtomicValue a, AtomicValue b) {
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
        return "QUNC";
    }
}
