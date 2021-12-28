////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.functions.FormatNumber;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.ValidationFailure;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * This class represents the XPath built-in type xs:integer. It is used for all
 * subtypes of xs:integer, other than user-defined subtypes. There are two implementations
 * of IntegerValue: {@link Int64Value}, which accommodates values up to 2^63, and
 * {@link BigIntegerValue}, which accommodates unlimited-length integers.
 * @since 9.8: changed in 9.8 to make this class a subclass of the new abstract
 * class DecimalValue, to better reflect the XDM type hierarchy
 */

public abstract class IntegerValue extends DecimalValue {

    /**
     * Static data identifying the min and max values for each built-in subtype of xs:integer.
     * This is a sequence of triples, each holding the fingerprint of the type, the minimum
     * value, and the maximum value. The special value NO_LIMIT indicates that there is no
     * minimum (or no maximum) for this type. The special value MAX_UNSIGNED_LONG represents the
     * value 2^64-1
     */
    private static long NO_LIMIT = -9999;
    private static long MAX_UNSIGNED_LONG = -9998;

    /*@NotNull*/ private static long[] ranges = {
            // arrange so the most frequently used types are near the start
            StandardNames.XS_INTEGER, NO_LIMIT, NO_LIMIT,
            StandardNames.XS_LONG, Long.MIN_VALUE, Long.MAX_VALUE,
            StandardNames.XS_INT, Integer.MIN_VALUE, Integer.MAX_VALUE,
            StandardNames.XS_SHORT, Short.MIN_VALUE, Short.MAX_VALUE,
            StandardNames.XS_BYTE, Byte.MIN_VALUE, Byte.MAX_VALUE,
            StandardNames.XS_NON_NEGATIVE_INTEGER, 0, NO_LIMIT,
            StandardNames.XS_POSITIVE_INTEGER, 1, NO_LIMIT,
            StandardNames.XS_NON_POSITIVE_INTEGER, NO_LIMIT, 0,
            StandardNames.XS_NEGATIVE_INTEGER, NO_LIMIT, -1,
            StandardNames.XS_UNSIGNED_LONG, 0, MAX_UNSIGNED_LONG,
            StandardNames.XS_UNSIGNED_INT, 0, 4294967295L,
            StandardNames.XS_UNSIGNED_SHORT, 0, 65535,
            StandardNames.XS_UNSIGNED_BYTE, 0, 255};

    /**
     * Factory method: makes either an Int64Value or a BigIntegerValue depending on the value supplied
     *
     * @param value the supplied integer value
     * @return the value as a BigIntegerValue or Int64Value as appropriate
     */

    public static IntegerValue makeIntegerValue(/*@NotNull*/ BigInteger value) {
        if (value.compareTo(BigIntegerValue.MAX_LONG) > 0 || value.compareTo(BigIntegerValue.MIN_LONG) < 0) {
            return new BigIntegerValue(value);
        } else {
            return Int64Value.makeIntegerValue(value.longValue());
        }
    }

    /**
     * Convert a double to an integer
     *
     * @param value the double to be converted
     * @return the result of the conversion, or the validation failure if the input is NaN or infinity
     */

    public static ConversionResult makeIntegerValue(double value) {
        if (Double.isNaN(value)) {
            ValidationFailure err = new ValidationFailure("Cannot convert double NaN to an integer");
            err.setErrorCode("FOCA0002");
            return err;
        }
        if (Double.isInfinite(value)) {
            ValidationFailure err = new ValidationFailure("Cannot convert double INF to an integer");
            err.setErrorCode("FOCA0002");
            return err;
        }
        if (value > Long.MAX_VALUE || value < Long.MIN_VALUE) {
            if (value == Math.floor(value)) {
                return new BigIntegerValue(FormatNumber.adjustToDecimal(value, 2).toBigInteger());
            } else {
                return new BigIntegerValue(new BigDecimal(value).toBigInteger());
            }
        }
        return Int64Value.makeIntegerValue((long) value);
    }

    /**
     * Convert a double to an integer
     *
     * @param doubleValue the double to be converted
     * @return the result of the conversion, or the validation failure if the input is NaN or infinity
     */

    public static ConversionResult makeIntegerValue(DoubleValue doubleValue) {
        double value = doubleValue.getDoubleValue();
        return makeIntegerValue(value);
    }

    /**
     * This class allows subtypes of xs:integer to be held, as well as xs:integer values.
     * This method sets the required type label. Note that this method modifies the value in situ.
     *
     * @param type     the subtype of integer required
     * @param validate true if validation is required, false if the caller warrants that the value
     *                 is valid for the subtype
     * @return null if the operation succeeds, or a ValidationException if the value is out of range
     */

    /*@Nullable*/
    public abstract ValidationFailure convertToSubType(BuiltInAtomicType type, boolean validate);

    /**
     * This class allows subtypes of xs:integer to be held, as well as xs:integer values.
     * This method sets the required type label. Note that this method modifies the value in situ.
     *
     * @param type the subtype of integer required
     * @return null if the operation succeeds, or a ValidationException if the value is out of range
     */

    /*@Nullable*/
    public abstract ValidationFailure validateAgainstSubType(BuiltInAtomicType type);

    /**
     * Check that a value is in range for the specified subtype of xs:integer
     *
     * @param value the value to be checked
     * @param type  the required item type, a subtype of xs:integer
     * @return true if successful, false if value is out of range for the subtype
     */

    public static boolean checkRange(long value, /*@NotNull*/ BuiltInAtomicType type) {
        int fp = type.getFingerprint();
        for (int i = 0; i < ranges.length; i += 3) {
            if (ranges[i] == fp) {
                long min = ranges[i + 1];
                if (min != NO_LIMIT && value < min) {
                    return false;
                }
                long max = ranges[i + 2];
                return max == NO_LIMIT || max == MAX_UNSIGNED_LONG || value <= max;
            }
        }
        throw new IllegalArgumentException(
                "No range information found for integer subtype " + type.getDescription());
    }

    /**
     * Get the minInclusive facet for a built-in integer subtype
     *
     * @param type the built-in type, which must be derived from xs:integer
     * @return the value of the minInclusive facet if there is a lower limit, or null if not
     */

    public static IntegerValue getMinInclusive(BuiltInAtomicType type) {
        int fp = type.getFingerprint();
        for (int i = 0; i < ranges.length; i += 3) {
            if (ranges[i] == fp) {
                long min = ranges[i + 1];
                if (min == NO_LIMIT) {
                    return null;
                } else {
                    return Int64Value.makeIntegerValue(min);
                }
            }
        }
        return null;
    }

    /**
     * Get the maxInclusive facet for a built-in integer subtype
     *
     * @param type the built-in type, which must be derived from xs:integer
     * @return the value of the minInclusive facet if there is a lower limit, or null if not
     */

    public static IntegerValue getMaxInclusive(BuiltInAtomicType type) {
        int fp = type.getFingerprint();
        for (int i = 0; i < ranges.length; i += 3) {
            if (ranges[i] == fp) {
                long max = ranges[i + 2];
                if (max == NO_LIMIT) {
                    return null;
                } else if (max == MAX_UNSIGNED_LONG) {
                    return IntegerValue.makeIntegerValue(BigIntegerValue.MAX_UNSIGNED_LONG);
                } else {
                    return Int64Value.makeIntegerValue(max);
                }
            }
        }
        return null;
    }

    /**
     * Check that a BigInteger is within the required range for a given integer subtype.
     * This method is expensive, so it should not be used unless the BigInteger is outside the range of a long.
     *
     * @param big  the supplied BigInteger
     * @param type the derived type (a built-in restriction of xs:integer) to check the value against
     * @return true if the value is within the range for the derived type
     */

    public static boolean checkBigRange(BigInteger big, /*@NotNull*/ BuiltInAtomicType type) {

        for (int i = 0; i < ranges.length; i += 3) {
            if (ranges[i] == type.getFingerprint()) {
                long min = ranges[i + 1];
                if (min != NO_LIMIT && BigInteger.valueOf(min).compareTo(big) > 0) {
                    return false;
                }
                long max = ranges[i + 2];
                if (max == NO_LIMIT) {
                    return true;
                } else if (max == MAX_UNSIGNED_LONG) {
                    return BigIntegerValue.MAX_UNSIGNED_LONG.compareTo(big) >= 0;
                } else {
                    return BigInteger.valueOf(max).compareTo(big) >= 0;
                }
            }
        }
        throw new IllegalArgumentException(
                "No range information found for integer subtype " + type.getDescription());
    }

    /**
     * Static factory method to convert strings to integers.
     *
     * @param s CharSequence representing the string to be converted
     * @return either an Int64Value or a BigIntegerValue representing the value of the String, or
     *         a ValidationFailure encapsulating an Exception if the value cannot be converted.
     */

    public static ConversionResult stringToInteger(/*@NotNull*/ CharSequence s) {

        int len = s.length();
        int start = 0;
        int last = len - 1;
        while (start < len && s.charAt(start) <= 0x20) {
            start++;
        }
        while (last > start && s.charAt(last) <= 0x20) {
            last--;
        }
        if (start > last) {
            return new ValidationFailure("Cannot convert zero-length string to an integer");
        }
        if (last - start < 16) {
            // for short numbers, we do the conversion ourselves, to avoid throwing unnecessary exceptions
            boolean negative = false;
            long value = 0;
            int i = start;
            if (s.charAt(i) == '+') {
                i++;
            } else if (s.charAt(i) == '-') {
                negative = true;
                i++;
            }
            if (i > last) {
                return new ValidationFailure("Cannot convert string " + Err.wrap(s, Err.VALUE) +
                        " to integer: no digits after the sign");
            }
            while (i <= last) {
                char d = s.charAt(i++);
                if (d >= '0' && d <= '9') {
                    value = 10 * value + (d - '0');
                } else {
                    return new ValidationFailure("Cannot convert string " + Err.wrap(s, Err.VALUE) + " to an integer");
                }
            }
            return Int64Value.makeIntegerValue(negative ? -value : value);
        } else {
            // for longer numbers, rely on library routines
            try {
                CharSequence t = Whitespace.trimWhitespace(s);
                if (t.charAt(0) == '+') {
                    t = t.subSequence(1, t.length());
                }
                if (t.length() < 16) {
                    return new Int64Value(Long.parseLong(t.toString()));
                } else {
                    return new BigIntegerValue(new BigInteger(t.toString()));
                }
            } catch (NumberFormatException err) {
                return new ValidationFailure("Cannot convert string " + Err.wrap(s, Err.VALUE) + " to an integer");
            }
        }
    }

    /**
     * Determine whether a string is castable as an integer
     *
     * @param input the string to be tested
     * @return null if the string is castable to an integer, or a validation failure otherwise
     */

    /*@Nullable*/
    public static ValidationFailure castableAsInteger(CharSequence input) {
        CharSequence s = Whitespace.trimWhitespace(input);

        int last = s.length() - 1;
        if (last < 0) {
            return new ValidationFailure("Cannot convert empty string to an integer");
        }
        int i = 0;
        if (s.charAt(i) == '+' || s.charAt(i) == '-') {
            i++;
        }
        if (i > last) {
            return new ValidationFailure("Cannot convert string " + Err.wrap(s, Err.VALUE) +
                    " to integer: no digits after the sign");
        }
        while (i <= last) {
            char d = s.charAt(i++);
            if (d >= '0' && d <= '9') {
                // OK
            } else {
                return new ValidationFailure("Cannot convert string " + Err.wrap(s, Err.VALUE) + " to an integer: contains a character that is not a digit");
            }
        }
        return null;
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    /*@NotNull*/
    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.INTEGER;
    }

    /**
     * Get the numeric value converted to a decimal
     *
     * @return a decimal representing this numeric value;
     */

    @Override
    public abstract BigDecimal getDecimalValue();

    /**
     * Determine whether the value is a whole number, that is, whether it compares
     * equal to some integer
     *
     * @return always true for this implementation
     */

    @Override
    public boolean isWholeNumber() {
        return true;
    }

    /**
     * Add another integer
     *
     * @param other the other integer
     * @return the result of the addition
     */

    public abstract IntegerValue plus(IntegerValue other);

    /**
     * Subtract another integer
     *
     * @param other the other integer
     * @return the result of the subtraction
     */

    public abstract IntegerValue minus(IntegerValue other);

    /**
     * Multiply by another integer
     *
     * @param other the other integer
     * @return the result of the multiplication
     */

    public abstract IntegerValue times(IntegerValue other);

    /**
     * Divide by another integer
     *
     * @param other the other integer
     * @return the result of the division
     * @throws XPathException if the other integer is zero
     */

    public abstract NumericValue div(IntegerValue other) throws XPathException;

    /**
     * Divide by another integer, providing location information for any exception
     *
     * @param other the other integer
     * @param locator the location of the expression, for use in diagnostics
     * @return the result of the division
     * @throws XPathException if the other integer is zero
     */

    public NumericValue div(IntegerValue other, Location locator) throws XPathException {
        try {
            return div(other);
        } catch (XPathException err) {
            err.maybeSetLocation(locator);
            throw err;
        }
    }

    /**
     * Take modulo another integer
     *
     * @param other the other integer
     * @return the result of the modulo operation (the remainder)
     * @throws XPathException if the other integer is zero
     */

    public abstract IntegerValue mod(IntegerValue other) throws XPathException;

    /**
     * Take modulo another integer, providing location information for any exception
     *
     * @param other   the other integer
     * @param locator the location of the expression, for use in diagnostics
     * @return the result of the division
     * @throws XPathException if the other integer is zero
     */

    public IntegerValue mod(IntegerValue other, Location locator) throws XPathException {
        try {
            return mod(other);
        } catch (XPathException err) {
            err.maybeSetLocation(locator);
            throw err;
        }
    }

    /**
     * Integer divide by another integer
     *
     * @param other the other integer
     * @return the result of the integer division
     * @throws XPathException if the other integer is zero
     */

    public abstract IntegerValue idiv(IntegerValue other) throws XPathException;

    /**
     * Integer divide by another integer, providing location information for any exception
     *
     * @param other   the other integer
     * @param locator the location of the expression, for use in diagnostics
     * @return the result of the division
     * @throws XPathException if the other integer is zero
     */

    public IntegerValue idiv(IntegerValue other, Location locator) throws XPathException {
        try {
            return idiv(other);
        } catch (XPathException err) {
            err.maybeSetLocation(locator);
            throw err;
        }
    }

    /**
     * Get the value as a BigInteger
     *
     * @return the value, as a BigInteger
     */

    public abstract BigInteger asBigInteger();

    /**
     * Get the signum of an int
     *
     * @param i the int
     * @return -1 if the integer is negative, 0 if it is zero, +1 if it is positive
     */

    protected static int signum(int i) {
        return (i >> 31) | (-i >>> 31);
    }

    /**
     * Determine whether two atomic values are identical, as determined by XML Schema rules. This is a stronger
     * test than equality (even schema-equality); for example two dateTime values are not identical unless
     * they are in the same timezone.
     * <p>Note that even this check ignores the type annotation of the value. The integer 3 and the short 3
     * are considered identical, even though they are not fully interchangeable. "Identical" means the
     * same point in the value space, regardless of type annotation.</p>
     * <p>NaN is identical to itself.</p>
     *
     * @param v the other value to be compared with this one
     * @return true if the two values are identical, false otherwise.
     */

    @Override
    public boolean isIdentical(/*@NotNull*/ AtomicValue v) {
        return (v instanceof IntegerValue) && equals(v);
    }


}

