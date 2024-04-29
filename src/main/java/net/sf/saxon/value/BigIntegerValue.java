////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.Calculator;
import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.str.BMPString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ValidationFailure;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * An integer value: note this is a subtype of decimal in XML Schema, not a primitive type.
 * The abstract class IntegerValue is used to represent any xs:integer value; this implementation
 * is used for values that do not fit comfortably in a Java long; including the built-in subtype xs:unsignedLong
 */

public final class BigIntegerValue extends IntegerValue {

    private final BigInteger value;

    private static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger MIN_INT = BigInteger.valueOf(Integer.MIN_VALUE);
    public static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    public static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    /*@NotNull*/ public static final BigInteger MAX_UNSIGNED_LONG = new BigInteger("18446744073709551615");
    /*@NotNull*/ public static final BigIntegerValue ZERO = new BigIntegerValue(BigInteger.ZERO);

    /**
     * Construct an xs:integer value from a Java BigInteger
     *
     * @param value the supplied BigInteger
     */

    public BigIntegerValue(BigInteger value) {
        super(BuiltInAtomicType.INTEGER);
        this.value = value;
    }

    /**
     * Construct an xs:integer value from a Java BigInteger, supplying a type label.
     * It is the caller's responsibility to ensure that the supplied value conforms
     * with the rules for the specified type.
     *
     * @param value     the value of the integer
     * @param typeLabel the type, which must represent a type derived from xs:integer
     */

    public BigIntegerValue(BigInteger value, AtomicType typeLabel) {
        super(typeLabel);
        this.value = value;
    }

    /**
     * Construct an xs:integer value from a Java long. Note: normally, if the value fits in a long,
     * then an Int64Value should be used. This constructor is largely for internal use, when operations
     * are required that require two integers to use the same implementation class to be used.
     *
     * @param value the supplied Java long
     */

    public BigIntegerValue(long value) {
        super(BuiltInAtomicType.INTEGER);
        this.value = BigInteger.valueOf(value);
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param typeLabel the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    /*@NotNull*/
    @Override
    public AtomicValue copyAsSubType(/*@NotNull*/ AtomicType typeLabel) {
        if (typeLabel.getPrimitiveType() == StandardNames.XS_INTEGER) {
            return new BigIntegerValue(value, typeLabel);
        } else {
            return new BigDecimalValue(new BigDecimal(value), typeLabel);
        }

    }


    /**
     * This class allows subtypes of xs:integer to be held, as well as xs:integer values.
     * This method checks that the value is valid against the rules for a given subtype.
     *
     * @param type the subtype of integer required
     * @return null if the operation succeeds, or a ValidationException if the value is out of range
     */

    /*@Nullable*/
    @Override
    public ValidationFailure validateAgainstSubType(/*@NotNull*/ BuiltInAtomicType type) {
        if (IntegerValue.checkBigRange(value, type)) {
            return null;
        } else {
            ValidationFailure err = new ValidationFailure(
                    "Integer value is out of range for subtype " + type.getDisplayName());
            err.setErrorCode("FORG0001");
            return err;
        }
    }

    /**
     * Get the hashCode. This must conform to the rules for other NumericValue hashcodes
     *
     * @see NumericValue#hashCode
     */

    public int hashCode() {
        if (value.compareTo(MIN_INT) >= 0 && value.compareTo(MAX_INT) <= 0) {
            return value.intValue();
        } else {
            return Double.valueOf(getDoubleValue()).hashCode();
        }
    }

    /**
     * Get the value as a long
     *
     * @return the value of the xs:integer, as a Java long
     */

    @Override
    public long longValue() {
        return value.longValue();
    }

    /**
     * Get the value as a BigInteger
     *
     * @return the value of the xs:integer as a Java BigInteger
     */

    @Override
    public BigInteger asBigInteger() {
        return value;
    }

    /**
     * Test whether the value is within the range that can be held in a 64-bit signed integer
     *
     * @return true if the value is within range for a long
     */

    public boolean isWithinLongRange() {
        return value.compareTo(MIN_LONG) >= 0 && value.compareTo(MAX_LONG) <= 0;
    }

    /**
     * Convert the value to a BigDecimal
     *
     * @return the resulting BigDecimal
     */

    /*@NotNull*/
    public BigDecimal asDecimal() {
        return new BigDecimal(value);
    }

    /**
     * Return the effective boolean value of this integer
     *
     * @return false if the integer is zero, otherwise true
     */
    @Override
    public boolean effectiveBooleanValue() {
        return value.compareTo(BigInteger.ZERO) != 0;
    }

    /**
     * Compare the value to another numeric value
     *
     * @param other the numeric value to be compared to this value
     * @return -1 if this value is less than the other, 0 if they are equal,
     *         +1 if this value is greater
     */

    @Override
    public int compareTo(XPathComparable other) {
        if (other instanceof NumericValue) {
            if (other instanceof BigIntegerValue) {
                return value.compareTo(((BigIntegerValue) other).value);
            } else if (other instanceof Int64Value) {
                return value.compareTo(BigInteger.valueOf(((Int64Value) other).longValue()));
            } else if (other instanceof BigDecimalValue) {
                return asDecimal().compareTo(((BigDecimalValue) other).getDecimalValue());
            } else {
                return super.compareTo(other);
            }
        } else {
            throw new ClassCastException("Cannot compare xs:integer to " + other);
        }
    }

    /**
     * Compare the value to a long
     *
     * @param other the value to be compared with
     * @return -1 if this is less, 0 if this is equal, +1 if this is greater or if this is NaN
     */

    @Override
    public int compareTo(long other) {
        if (other == 0) {
            return value.signum();
        }
        return value.compareTo(BigInteger.valueOf(other));
    }


    /**
     * Get the value as a String
     *
     * @return a String representation of the value
     */

    @Override
    public UnicodeString getPrimitiveStringValue() {
        return BMPString.of(value.toString());
    }

    /**
     * Get the numeric value as a double
     *
     * @return A double representing this numeric value; NaN if it cannot be
     *         converted
     */
    @Override
    public double getDoubleValue() {
        return value.doubleValue();
    }

    /**
     * Get the numeric value converted to a decimal
     *
     * @return a decimal representing this numeric value;
     */

    /*@NotNull*/
    @Override
    public BigDecimal getDecimalValue() {
        return new BigDecimal(value);
    }

    /**
     * Get the numeric value converted to a float
     *
     * @return a float representing this numeric value; NaN if it cannot be converted
     */
    @Override
    public float getFloatValue() {
        return (float) getDoubleValue();
    }

    /**
     * Negate the value
     *
     * @return the result of inverting the sign of the value
     */

    /*@NotNull*/
    @Override
    public NumericValue negate() {
        return new BigIntegerValue(value.negate());
    }

    /**
     * Implement the XPath floor() function
     *
     * @return the integer value, unchanged
     */

    /*@NotNull*/
    @Override
    public NumericValue floor() {
        return this;
    }

    /**
     * Implement the XPath ceiling() function
     *
     * @return the integer value, unchanged
     */

    /*@NotNull*/
    @Override
    public NumericValue ceiling() {
        return this;
    }

    /**
     * Implement the XPath round() function
     *
     * @return the integer value, unchanged
     */

    @Override
    public NumericValue round(int scale) {
        if (scale >= 0) {
            return this;
        } else {
            BigInteger factor = BigInteger.valueOf(10).pow(-scale);
            BigInteger[] pair = value.divideAndRemainder(factor);
            int up = pair[1].compareTo(factor.divide(BigInteger.valueOf(2)));
            if (up >= 0) {
                // remainder is > .5
                pair[0] = pair[0].add(BigInteger.valueOf(1));
            }
            return makeIntegerValue(pair[0].multiply(factor));
        }
    }

    /**
     * Implement the XPath round-to-half-even() function
     *
     * @param scale number of digits required after the decimal point; the
     *              value -2 (for example) means round to a multiple of 100
     * @return if the scale is &gt;=0, return this value unchanged. Otherwise
     *         round it to a multiple of 10**-scale
     */

    @Override
    public NumericValue roundHalfToEven(int scale) {
        if (scale >= 0) {
            return this;
        } else {
            BigInteger factor = BigInteger.valueOf(10).pow(-scale);
            BigInteger[] pair = value.divideAndRemainder(factor);
            int up = pair[1].compareTo(factor.divide(BigInteger.valueOf(2)));
            if (up > 0) {
                // remainder is > .5
                pair[0] = pair[0].add(BigInteger.valueOf(1));
            } else if (up == 0) {
                // remainder == .5
                if (pair[0].mod(BigInteger.valueOf(2)).signum() != 0) {
                    // last digit of quotient is odd: make it even
                    pair[0] = pair[0].add(BigInteger.valueOf(1));
                }
            }
            return makeIntegerValue(pair[0].multiply(factor));
        }
    }

    /**
     * Determine whether the value is negative, zero, or positive
     *
     * @return -1 if negative, 0 if zero, +1 if positive, NaN if NaN
     */

    @Override
    public int signum() {
        return value.signum();
    }

    /**
     * Get the absolute value as defined by the XPath abs() function
     *
     * @return the absolute value
     */

    /*@NotNull*/
    @Override
    public NumericValue abs() {
        if (value.signum() >= 0) {
            return this;
        } else {
            return new BigIntegerValue(value.abs());
        }
    }

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
     * Test whether a number is a possible subscript into a sequence, that is,
     * a whole number greater than zero and less than 2^31
     *
     * @return the number as an int if it is a possible subscript, or -1 otherwise
     */
    @Override
    public int asSubscript() {
        if (value.compareTo(BigInteger.ZERO) > 0 && value.compareTo(MAX_INT) <= 0) {
            return (int)longValue();
        } else {
            return -1;
        }
    }

    /**
     * Add another integer
     */

    @Override
    public IntegerValue plus(/*@NotNull*/ IntegerValue other) {
        if (other instanceof BigIntegerValue) {
            return makeIntegerValue(value.add(((BigIntegerValue) other).value));
        } else {
            //noinspection RedundantCast
            return makeIntegerValue(value.add(BigInteger.valueOf(((Int64Value) other).longValue())));
        }
    }

    /**
     * Subtract another integer
     */

    @Override
    public IntegerValue minus(/*@NotNull*/ IntegerValue other) {
        if (other instanceof BigIntegerValue) {
            return makeIntegerValue(value.subtract(((BigIntegerValue) other).value));
        } else {
            //noinspection RedundantCast
            return makeIntegerValue(value.subtract(BigInteger.valueOf(((Int64Value) other).longValue())));
        }
    }

    /**
     * Multiply by another integer
     */

    @Override
    public IntegerValue times(/*@NotNull*/ IntegerValue other) {
        if (other instanceof BigIntegerValue) {
            return makeIntegerValue(value.multiply(((BigIntegerValue) other).value));
        } else {
            //noinspection RedundantCast
            return makeIntegerValue(value.multiply(BigInteger.valueOf(((Int64Value) other).longValue())));
        }
    }

    /**
     * Divide by another integer
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the other integer is zero
     */

    @Override
    public NumericValue div(/*@NotNull*/ IntegerValue other) throws XPathException {
        BigInteger oi;
        if (other instanceof BigIntegerValue) {
            oi = ((BigIntegerValue) other).value;
        } else {
            oi = BigInteger.valueOf(other.longValue());
        }
        BigDecimalValue a = new BigDecimalValue(new BigDecimal(value));
        BigDecimalValue b = new BigDecimalValue(new BigDecimal(oi));
        return Calculator.decimalDivide(a, b);
    }

    /**
     * Take modulo another integer
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the other integer is zero
     */

    @Override
    public IntegerValue mod(/*@NotNull*/ IntegerValue other) throws XPathException {
        if (other.signum() == 0) {
            throw new XPathException("Integer modulo zero", "FOAR0001");
        }
        if (other instanceof BigIntegerValue) {
            return makeIntegerValue(value.remainder(((BigIntegerValue) other).value));
        } else {
            return makeIntegerValue(value.remainder(BigInteger.valueOf(other.longValue())));
        }
    }

    /**
     * Integer divide by another integer
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the other integer is zero
     */

    @Override
    public IntegerValue idiv(/*@NotNull*/ IntegerValue other) throws XPathException {
        if (other.signum() == 0) {
            throw new XPathException("Integer division by zero", "FOAR0001");
        }
        BigInteger oi;
        if (other instanceof BigIntegerValue) {
            oi = ((BigIntegerValue) other).value;
        } else {
            oi = BigInteger.valueOf(other.longValue());
        }
        return makeIntegerValue(value.divide(oi));
    }

    /**
     * Reduce a value to its simplest form.
     */

    /*@NotNull*/
    @Override
    public IntegerValue reduce() {
        if (compareTo(Long.MAX_VALUE) < 0 && compareTo(Long.MIN_VALUE) > 0) {
            return new Int64Value(longValue(), typeLabel);
        }
        return this;
    }




}

