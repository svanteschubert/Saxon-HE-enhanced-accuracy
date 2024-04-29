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
import net.sf.saxon.str.StringConstants;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ValidationFailure;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * An integer value: note this is a subtype of decimal in XML Schema, not a primitive type.
 * This class supports integer values in the range permitted by a Java "long",
 * and also supports the built-in subtypes of xs:integer.
 */

public final class Int64Value extends IntegerValue {

    /**
     * IntegerValue representing the value -1
     */
    public static final Int64Value MINUS_ONE = new Int64Value(-1);
    /**
     * IntegerValue representing the value zero
     */
    public static final Int64Value ZERO = new Int64Value(0);
    /**
     * IntegerValue representing the value +1
     */
    public static final Int64Value PLUS_ONE = new Int64Value(+1);
    /**
     * IntegerValue representing the maximum value for a long
     */
    public static final Int64Value MAX_LONG = new Int64Value(Long.MAX_VALUE);
    /**
     * IntegerValue representing the minimum value for a long
     */
    public static final Int64Value MIN_LONG = new Int64Value(Long.MIN_VALUE);
    private final long value;

    /**
     * Array of small integer values
     */
    private static final Int64Value[] SMALL_INTEGERS = {
            new Int64Value(0),
            new Int64Value(1),
            new Int64Value(2),
            new Int64Value(3),
            new Int64Value(4),
            new Int64Value(5),
            new Int64Value(6),
            new Int64Value(7),
            new Int64Value(8),
            new Int64Value(9),
            new Int64Value(10),
            new Int64Value(11),
            new Int64Value(12),
            new Int64Value(13),
            new Int64Value(14),
            new Int64Value(15),
            new Int64Value(16),
            new Int64Value(17),
            new Int64Value(18),
            new Int64Value(19),
            new Int64Value(20)
    };

    /**
     * Constructor supplying a long
     *
     * @param value the value of the IntegerValue
     */

    public Int64Value(long value) {
        super(BuiltInAtomicType.INTEGER);
        this.value = value;
    }

    /**
     * Constructor supplying a long, with a specific type annotation
     * @param value the value of the IntegerValue
     * @param typeLabel the type annotation (trusted to be correct)
     */

    public Int64Value(long value, AtomicType typeLabel) {
        super(typeLabel);
        this.value = value;
    }

    /**
     * Constructor for a subtype, supplying a long and a type label.
     *
     * @param val   The supplied value, as an integer
     * @param typeLabel  the required item type, a subtype of xs:integer
     * @param check Set to true if the method is required to check that the value is in range;
     *              false if the caller can guarantee that the value has already been checked.
     * @throws XPathException if the supplied value is out of range for the
     *                        target type
     */

    public Int64Value(long val, /*@NotNull*/ BuiltInAtomicType typeLabel, boolean check) throws XPathException {
        super(typeLabel);
        value = val;
        if (check && !checkRange(value, typeLabel)) {
            throw new XPathException("Integer value " + val +
                    " is out of range for the requested type " + typeLabel.getDescription())
                    .withErrorCode("XPTY0004").asTypeError();
        }
    }

    /**
     * Factory method: allows Int64Value objects to be reused. Note that
     * a value obtained using this method must not be modified to set a type label, because
     * the value is in general shared.
     *
     * @param value the integer value
     * @return an Int64Value with this integer value
     */

    public static Int64Value makeIntegerValue(long value) {
        if (value <= 20 && value >= 0) {
            return SMALL_INTEGERS[(int) value];
        } else {
            return new Int64Value(value);
        }
    }

    /**
     * Factory method to create a derived value, with no checking of the value against the
     * derived type
     *
     * @param val  the integer value
     * @param type the subtype of xs:integer
     * @return the constructed value
     */

    /*@NotNull*/
    public static Int64Value makeDerived(long val, AtomicType type) {
        return new Int64Value(val, type);
    }

    /**
     * Factory method returning the integer -1, 0, or +1 according as the argument
     * is negative, zero, or positive
     *
     * @param val the value to be tested
     * @return the Int64Value representing -1, 0, or +1
     */

    public static Int64Value signum(long val) {
        if (val == 0) {
            return ZERO;
        } else {
            return val < 0 ? MINUS_ONE : PLUS_ONE;
        }
    }

    /**
     * Test whether a number is a possible subscript into a sequence, that is,
     * a whole number greater than zero and less than 2^31
     *
     * @return the number as an int if it is a possible subscript, or -1 otherwise
     */
    @Override
    public int asSubscript() {
        if (value > 0 && value <= Integer.MAX_VALUE) {
            return (int)value;
        } else {
            return -1;
        }
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
            return new Int64Value(value, typeLabel);
        } else {
            return new BigDecimalValue(value).copyAsSubType(typeLabel);
        }
    }


    /**
     * This class allows subtypes of xs:integer to be held, as well as xs:integer values.
     * This method sets the required type label. Note that this method modifies the value in situ.
     *
     * @param type the subtype of integer required
     * @return null if the operation succeeds, or a ValidationException if the value is out of range
     */

    /*@Nullable*/
    @Override
    public ValidationFailure validateAgainstSubType(/*@NotNull*/ BuiltInAtomicType type) {
        if (checkRange(value, type)) {
            return null;
        } else {
            ValidationFailure err = new ValidationFailure("Value " + value +
                    " cannot be converted to integer subtype " + type.getDescription());
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
        if (value > Integer.MIN_VALUE && value < Integer.MAX_VALUE) {
            return (int) value;
        } else {
            return Double.valueOf(getDoubleValue()).hashCode();
        }
    }

    /**
     * Get the value
     *
     * @return the value of the xs:integer, as a Java long
     */

    @Override
    public long longValue() {
        return value;
    }

    /**
     * Return the effective boolean value of this integer
     *
     * @return false if the integer is zero, otherwise true
     */
    @Override
    public boolean effectiveBooleanValue() {
        return value != 0;
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
            if (other instanceof Int64Value) {
                return Long.compare(value, ((Int64Value) other).value);
            } else if (other instanceof BigIntegerValue) {
                return BigInteger.valueOf(value).compareTo(((BigIntegerValue) other).asBigInteger());
            } else if (other instanceof BigDecimalValue) {
                return BigDecimal.valueOf(value).compareTo(((BigDecimalValue)other).getDecimalValue());
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
        return Long.compare(value, other);
    }

    /**
     * Get the value as a String
     *
     * @return a String representation of the value
     */

    @Override
    public UnicodeString getPrimitiveStringValue() {
        // Copied from Long.toString(), but generating single-byte characters
        if (value == Long.MIN_VALUE) {
            return StringConstants.MIN_LONG;
        }
        int size = (value < 0) ? stringSize(-value) + 1 : stringSize(value);
        byte[] buf = new byte[size];
        getDigits(value, size, buf);
        return new Twine8(buf);
        //return BMPString.of(Long.toString(value));
    }

    private static void getDigits(long i, int index, byte[] buf) {
        // Derived from Long.getChars()
        long q;
        int r;
        int charPos = index;
        byte sign = 0;

        if (i < 0) {
            sign = (byte)'-';
            i = -i;
        }

        // Get 2 digits/iteration using longs until quotient fits into an int
        while (i > Integer.MAX_VALUE) {
            q = i / 100;
            // really: r = i - (q * 100);
            r = (int) (i - ((q << 6) + (q << 5) + (q << 2)));
            i = q;
            buf[--charPos] = DIGIT_ONES[r];
            buf[--charPos] = DIGIT_TENS[r];
        }

        // Get 2 digits/iteration using ints
        int q2;
        int i2 = (int) i;
        while (i2 >= 65536) {
            q2 = i2 / 100;
            // really: r = i2 - (q * 100);
            r = i2 - ((q2 << 6) + (q2 << 5) + (q2 << 2));
            i2 = q2;
            buf[--charPos] = DIGIT_ONES[r];
            buf[--charPos] = DIGIT_TENS[r];
        }

        // Fall thru to fast mode for smaller numbers
        // assert(i2 <= 65536, i2);
        do {
            q2 = (i2 * 52429) >>> (16 + 3);
            r = i2 - ((q2 << 3) + (q2 << 1));  // r = i2-(q2*10) ...
            buf[--charPos] = DIGITS[r];
            i2 = q2;
        } while (i2 != 0);

        if (sign != 0) {
            buf[--charPos] = sign;
        }
    }


    private final static byte[] DIGITS = StringConstants.bytes("0123456789");

    private final static byte[] DIGIT_TENS = StringConstants.bytes
            (   "0000000000" +
                "1111111111" +
                "2222222222" +
                "3333333333" +
                "4444444444" +
                "5555555555" +
                "6666666666" +
                "7777777777" +
                "8888888888" +
                "9999999999" );

    private final static byte[] DIGIT_ONES = StringConstants.bytes
            (   "0123456789" +
                "0123456789" +
                "0123456789" +
                "0123456789" +
                "0123456789" +
                "0123456789" +
                "0123456789" +
                "0123456789" +
                "0123456789" +
                "0123456789" );


    // Requires positive x
    private static int stringSize(long x) {
        for (int w=0; w<18; w++) {
            if (x < powersOfTen[w]) {
                return w+1;
            }
        }
        return 19;
    }

    private static final long[] powersOfTen = new long[] {
                10L, 100L, 1000L,
                10000L, 100000L, 1_000_000L,
                10_000_000L, 100_000_000L, 1_000_000_000L,
                10_000_000_000L, 100_000_000_000L, 1_000_000_000_000L,
                10_000_000_000_000L, 100_000_000_000_000L, 1_000_000_000_000_000L,
                10_000_000_000_000_000L, 100_000_000_000_000_000L, 1_000_000_000_000_000_000L};

/**
     * Get the numeric value as a double
     *
     * @return A double representing this numeric value; NaN if it cannot be
     *         converted
     */
    @Override
    public double getDoubleValue() {
        return (double) value;
    }

    /**
     * Get the numeric value converted to a float
     *
     * @return a float representing this numeric value; NaN if it cannot be converted
     */

    @Override
    public float getFloatValue() {
        return (float) value;
    }

    /**
     * Get the numeric value converted to a decimal
     *
     * @return a decimal representing this numeric value;
     */

    @Override
    public BigDecimal getDecimalValue() {
        return BigDecimal.valueOf(value);
    }

    /**
     * Negate the value
     *
     * @return the result of inverting the sign of the value
     */

    @Override
    public NumericValue negate() {
        if (value == Long.MIN_VALUE) {
            return BigIntegerValue.makeIntegerValue(BigInteger.valueOf(value)).negate();
        } else {
            return new Int64Value(-value);
        }
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
     * @param scale the scale (for example scale=2 rounds to 2 decimal places, scale=-2
     *              rounds to a multiple of 100); default value is zero which rounds to an integer
     * @return the integer value, unchanged
     */

    @Override
    public NumericValue round(int scale) {
        if (scale >= 0 || value == 0) {
            return this;
        } else {
            if (scale < -15) {
                return new BigIntegerValue(value).round(scale);
            }

            long absolute = Math.abs(value);
            long factor = 1;
            for (long i = 1; i <= -scale; i++) {
                factor *= 10;
            }
            long modulus = absolute % factor;
            long rval = absolute - modulus;
            long d = modulus * 2;

            if (value > 0) {
                if (d >= factor) {
                    rval += factor;
                }
            } else {
                if (d > factor) {
                    rval += factor;
                }
                rval = -rval;
            }
            return new Int64Value(rval);
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
            if (scale < -15) {
                return new BigIntegerValue(value).roundHalfToEven(scale);
            }
            long absolute = Math.abs(value);
            long factor = 1;
            for (long i = 1; i <= -scale; i++) {
                factor *= 10;
            }
            long modulus = absolute % factor;
            long rval = absolute - modulus;
            long d = modulus * 2;
            if (d > factor) {
                rval += factor;
            } else if (d < factor) {
                // no-op
            } else {
                // round to even
                if (rval % (2 * factor) == 0) {
                    // no-op
                } else {
                    rval += factor;
                }
            }
            if (value < 0) {
                rval = -rval;
            }
            return new Int64Value(rval);
        }
    }

    /**
     * Determine whether the value is negative, zero, or positive
     *
     * @return -1 if negative, 0 if zero, +1 if positive, NaN if NaN
     */

    @Override
    public int signum() {
        if (value > 0) return +1;
        if (value == 0) return 0;
        return -1;
    }

    /**
     * Get the absolute value as defined by the XPath abs() function
     *
     * @return the absolute value
     */

    @Override
    public NumericValue abs() {
        if (value > 0) {
            return this;
        } else if (value == Long.MIN_VALUE) {
            return new BigIntegerValue(new BigInteger("9223372036854775808"));
        } else {
            return makeIntegerValue(-value);
        }
    }

    /**
     * Add another integer
     */

    @Override
    public IntegerValue plus(/*@NotNull*/ IntegerValue other) {
        // if either of the values is large, we use BigInteger arithmetic to be on the safe side
        if (other instanceof Int64Value) {
            long topa = (value >> 60) & 0xf;
            if (topa != 0 && topa != 0xf) {
                return new BigIntegerValue(value).plus(new BigIntegerValue(((Int64Value) other).value));
            }
            long topb = (((Int64Value) other).value >> 60) & 0xf;
            if (topb != 0 && topb != 0xf) {
                return new BigIntegerValue(value).plus(new BigIntegerValue(((Int64Value) other).value));
            }
            return makeIntegerValue(value + ((Int64Value) other).value);
        } else {
            return new BigIntegerValue(value).plus(other);
        }
    }

    /**
     * Subtract another integer
     */

    @Override
    public IntegerValue minus(/*@NotNull*/ IntegerValue other) {
        // if either of the values is large, we use BigInteger arithmetic to be on the safe side
        if (other instanceof Int64Value) {
            long topa = (value >> 60) & 0xf;
            if (topa != 0 && topa != 0xf) {
                return new BigIntegerValue(value).minus(new BigIntegerValue(((Int64Value) other).value));
            }
            long topb = (((Int64Value) other).value >> 60) & 0xf;
            if (topb != 0 && topb != 0xf) {
                return new BigIntegerValue(value).minus(new BigIntegerValue(((Int64Value) other).value));
            }
            return makeIntegerValue(value - ((Int64Value) other).value);
        } else {
            return new BigIntegerValue(value).minus(other);
        }
    }

    /**
     * Multiply by another integer
     */

    @Override
    public IntegerValue times(/*@NotNull*/ IntegerValue other) {
        // if either of the values is large, we use BigInteger arithmetic to be on the safe side
        if (other instanceof Int64Value) {
            if (isLong() || ((Int64Value) other).isLong()) {
                return new BigIntegerValue(value).times(new BigIntegerValue(((Int64Value) other).value));
            } else {
                return makeIntegerValue(value * ((Int64Value) other).value);
            }
        } else {
            return new BigIntegerValue(value).times(other);
        }
    }

    /**
     * Divide by another integer
     */

    @Override
    public NumericValue div(/*@NotNull*/ IntegerValue other) throws XPathException {
        // if either of the values is large, we use BigInteger arithmetic to be on the safe side
        if (other instanceof Int64Value) {
            long quotient = ((Int64Value) other).value;
            if (quotient == 0) {
                throw new XPathException("Integer division by zero", "FOAR0001");
            }
            if (isLong() || ((Int64Value) other).isLong()) {
                return new BigIntegerValue(value).div(new BigIntegerValue(quotient));
            }

            // the result of dividing two integers is a decimal; but if
            // one divides exactly by the other, we implement it as an integer
            if (value % quotient == 0) {
                return makeIntegerValue(value / quotient);
            } else {
                return Calculator.decimalDivide(new BigDecimalValue(value), new BigDecimalValue(quotient));
            }
        } else {
            return new BigIntegerValue(value).div(other);
        }
    }

    /**
     * Take modulo another integer
     */

    @Override
    public IntegerValue mod(/*@NotNull*/ IntegerValue other) throws XPathException {
        // if either of the values is large, we use BigInteger arithmetic to be on the safe side
        if (other instanceof Int64Value) {
            long quotient = ((Int64Value) other).value;
            if (quotient == 0) {
                throw new XPathException("Integer modulo zero", "FOAR0001");
            }
            if (isLong() || ((Int64Value) other).isLong()) {
                return new BigIntegerValue(value).mod(new BigIntegerValue(((Int64Value) other).value));
            } else {
                return makeIntegerValue(value % quotient);
            }
        } else {
            return new BigIntegerValue(value).mod(other);
        }
    }

    /**
     * Integer divide by another integer
     */

    @Override
    public IntegerValue idiv(IntegerValue other) throws XPathException {
        // if either of the values is large, we use BigInteger arithmetic to be on the safe side
        if (other.signum() == 0) {
            throw new XPathException("Integer division by zero", "FOAR0001");
        }
        if (other instanceof Int64Value) {
            if (isLong() || ((Int64Value) other).isLong()) {
                return new BigIntegerValue(value).idiv(new BigIntegerValue(((Int64Value) other).value));
            }
            return makeIntegerValue(value / ((Int64Value) other).value);
        } else {
            return new BigIntegerValue(value).idiv(other);
        }
    }

    /**
     * Test whether this value needs a long to hold it. Specifically, whether
     * the absolute value is > 2^31.
     * @return true if the value is too big to fit in a 32-bit int
     */

    private boolean isLong() {
        long top = value >> 31;
        return top != 0;
    }

    /**
     * Get the value as a BigInteger
     */

    @Override
    public BigInteger asBigInteger() {
        return BigInteger.valueOf(value);
    }

}

