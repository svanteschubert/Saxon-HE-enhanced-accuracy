////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.value.*;

/**
 * A specialist comparer that implements the rules for comparing an untypedAtomic value
 * (always the first operand) to a numeric value (always the second operand)
 */

public class UntypedNumericComparer implements AtomicComparer {

    private ConversionRules rules = ConversionRules.DEFAULT;

    private static double[][] bounds = {
            {1, 0e0, 0e1, 0e2, 0e3, 0e4, 0e5, 0e6, 0e7, 0e8, 0e9, 0e10},
            {1, 1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10},
            {1, 2e0, 2e1, 2e2, 2e3, 2e4, 2e5, 2e6, 2e7, 2e8, 2e9, 2e10},
            {1, 3e0, 3e1, 3e2, 3e3, 3e4, 3e5, 3e6, 3e7, 3e8, 3e9, 3e10},
            {1, 4e0, 4e1, 4e2, 4e3, 4e4, 4e5, 4e6, 4e7, 4e8, 4e9, 4e10},
            {1, 5e0, 5e1, 5e2, 5e3, 5e4, 5e5, 5e6, 5e7, 5e8, 5e9, 5e10},
            {1, 6e0, 6e1, 6e2, 6e3, 6e4, 6e5, 6e6, 6e7, 6e8, 6e9, 6e10},
            {1, 7e0, 7e1, 7e2, 7e3, 7e4, 7e5, 7e6, 7e7, 7e8, 7e9, 7e10},
            {1, 8e0, 8e1, 8e2, 8e3, 8e4, 8e5, 8e6, 8e7, 8e8, 8e9, 8e10},
            {1, 9e0, 9e1, 9e2, 9e3, 9e4, 9e5, 9e6, 9e7, 9e8, 9e9, 9e10},
            {1, 10e0, 10e1, 10e2, 10e3, 10e4, 10e5, 10e6, 10e7, 10e8, 10e9, 10e10}
    };

    /**
     * Optimized routine to compare an untyped atomic value with a numeric value.
     * This attempts to deliver a quick answer if the comparison is obviously false,
     * without performing the full string-to-double conversion
     * @param a0 the untypedAtomic comparand
     * @param a1 the numeric comparand
     * @param operator the comparison operator: a singleton operator such as Token.FEQ
     * @param rules the conversion rules
     * @return the result of the comparison
     * @throws XPathException if the first operand is not convertible to a double
     */

    public static boolean quickCompare(
            UntypedAtomicValue a0, NumericValue a1, int operator, ConversionRules rules)
            throws XPathException {
        int comp = quickComparison(a0, a1, rules);
        switch (operator) {
            case Token.FEQ:
                return comp == 0;
            case Token.FLE:
                return comp <= 0;
            case Token.FLT:
                return comp < 0;
            case Token.FGE:
                return comp >= 0;
            case Token.FGT:
                return comp > 0;
            case Token.FNE:
            default:
                return comp != 0;
        }
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

    private static int quickComparison(
            UntypedAtomicValue a0, NumericValue a1, ConversionRules rules)
            throws XPathException {
        double d1 = a1.getDoubleValue();

        CharSequence cs = Whitespace.trimWhitespace(a0.getStringValueCS());

        boolean simple = true;
        int wholePartLength = 0;
        int firstDigit = -1;
        int decimalPoints = 0;
        char sign = '?';
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
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
            double lowerBound = bounds[firstDigit][wholePartLength];
            double upperBound = bounds[firstDigit + 1][wholePartLength];
            if (sign == '-') {
                double temp = lowerBound;
                lowerBound = -upperBound;
                upperBound = -temp;
            }
            if (upperBound < d1) {
                return -1;
            }
            if (lowerBound > d1) {
                return +1;
            }

        }
        // The quick check was inconclusive, so we now parse the number.
        // We use integer comparison if both sides are simple integers, or double comparison otherwise
        if (simple && decimalPoints == 0 && wholePartLength <= 15 && a1 instanceof Int64Value) {
            long l0 = Long.parseLong(cs.toString());
            return Long.compare(l0, a1.longValue());
        } else {
            ConversionResult result;
            synchronized(a0) {
                result = BuiltInAtomicType.DOUBLE.getStringConverter(rules).convertString(a0.getPrimitiveStringValue());
            }
            AtomicValue av = result.asAtomic();
            return Double.compare(((DoubleValue)av).getDoubleValue(), d1);
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
            return quickComparison((UntypedAtomicValue)a, (NumericValue)b, rules);
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
