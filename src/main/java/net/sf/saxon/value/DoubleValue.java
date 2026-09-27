////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.expr.sort.AtomicSortComparer;
import net.sf.saxon.expr.sort.DoubleSortComparer;
import net.sf.saxon.functions.Round;
import net.sf.saxon.ma.map.BigDecimalMapKey;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.transpile.CSharpReplaceException;
import net.sf.saxon.type.AtomicMetadata;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ValidationException;

import java.math.BigDecimal;

/**
 * A numeric (double precision floating point) value
 */

public final class DoubleValue extends NumericValue {

    public static final DoubleValue ZERO = new DoubleValue(0.0);
    public static final DoubleValue NEGATIVE_ZERO = new DoubleValue(-0.0);
    public static final DoubleValue ONE = new DoubleValue(1.0);
    public static final DoubleValue NaN = new DoubleValue(Double.NaN);

    private final double value;

    /**
     * Constructor supplying a double
     *
     * @param value the value of the NumericValue
     */

    public DoubleValue(double value) {
        super(BuiltInAtomicType.DOUBLE);
        this.value = value;
    }

    /**
     * Constructor supplying a double and an AtomicType, for creating
     * a value that belongs to a user-defined subtype of xs:double. It is
     * the caller's responsibility to ensure that the supplied value conforms
     * to the supplied type.
     *
     * @param value the value of the NumericValue
     * @param typeLabel  the type of the value. This must be a subtype of xs:double, and the
     *              value must conform to this type. The method does not check these conditions.
     */

    public DoubleValue(double value, AtomicMetadata typeLabel) {
        super(typeLabel);
        this.value = value;
    }

    /**
     * Static factory method (for convenience in compiled bytecode)
     *
     * @param value the value of the double
     * @return a new DoubleValue
     */

    public static DoubleValue makeDoubleValue(double value) {
        return new DoubleValue(value);
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param metadata the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    /*@NotNull*/
    @Override
    public AtomicValue withMetadata(AtomicMetadata metadata) {
        return new DoubleValue(value, metadata);
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.DOUBLE;
    }

    /**
     * Return this numeric value as a double
     *
     * @return the value as a double
     */

    @Override
    public double getDoubleValue() {
        return value;
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
     * @throws ValidationException
     *          if the value cannot be converted, for example if it is NaN or infinite
     */
    @Override
    @CSharpReplaceException(from = "java.lang.NumberFormatException", to = "System.ArgumentOutOfRangeException")
    public BigDecimal getDecimalValue() throws ValidationException {
        try {
            return BigDecimal.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(e);
        }
    }

    /**
     * Get the numeric value converted to a decimal. This method gets the exact value
     * as needed for map keys and fn:compare, to give transitive comparison semantics
     *
     * @return a decimal representing this numeric value;
     * @throws ValidationException if the value cannot be converted, for example if it is NaN or infinite
     */
    @Override
    @CSharpReplaceException(from="java.lang.NumberFormatException", to="System.ArgumentOutOfRangeException")
    public BigDecimal getExactDecimalValue() throws ValidationException {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(e);
        }
    }

    /**
     * Return the numeric value as a Java long.
     *
     * @return the numeric value as a Java long. This performs truncation
     *         towards zero.
     * @throws net.sf.saxon.trans.XPathException
     *          if the value cannot be converted
     */
    @Override
    public long longValue() throws XPathException {
        return (long) value;
    }

    /**
     * Get the hashCode. This must conform to the rules for other NumericValue hashcodes
     *
     * @see NumericValue#hashCode
     */

    public int hashCode() {
        if (isNaN()) {
            return AtomicSortComparer.COLLATION_KEY_NaN.hashCode();
        } else if (Double.isInfinite(value)) {
            return Double.valueOf(value).hashCode();
        } else if (isWholeNumber() && value > Integer.MIN_VALUE && value < Integer.MAX_VALUE) {
            return (int) value;
        } else {
            return Double.valueOf(value).hashCode();
        }
    }

    /**
     * Test whether the value is the double/float value NaN
     */

    @Override
    public boolean isNaN() {
        return Double.isNaN(value);
    }

    /**
     * Get the effective boolean value
     *
     * @return the effective boolean value (true unless the value is zero or NaN)
     */
    @Override
    public boolean effectiveBooleanValue() {
        return value != 0.0 && !Double.isNaN(value);
    }

    /**
     * Convert the double to a string according to the XPath 2.0 rules
     * @return the string value
     */
//    public String getStringValue() {
//        return doubleToString(value).toString(); //, Double.toString(value)).toString();
//    }

    /**
     * Convert the double to a string according to the XPath 2.0 rules
     *
     * @return the string value
     */
    @Override
    public UnicodeString getPrimitiveStringValue() {
        return doubleToString(value);
    }

    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules. For xs:double, the canonical
     * representation always uses exponential notation.
     * @return the value, represented as a string using exponential notation
     */

    @Override
    public UnicodeString getCanonicalLexicalRepresentation() {
        return FloatingPointConverter.convertDouble(value, true);
    }

    /**
     * Internal method used for conversion of a double to a string
     *
     * @param value the actual value
     * @return the value converted to a string, according to the XPath casting rules.
     */

    public static UnicodeString doubleToString(double value) {
        double d = Math.abs(value);
        return FloatingPointConverter.convertDouble(value, d != 0 && (d >= 1000000 || d < 0.000001));
    }


    /**
     * Negate the value
     */

    @Override
    public NumericValue negate() {
        return new DoubleValue(-value);
    }

    /**
     * Implement the XPath floor() function
     */

    @Override
    public NumericValue floor() {
        return new DoubleValue(Math.floor(value));
    }

    /**
     * Implement the XPath ceiling() function
     */

    @Override
    public NumericValue ceiling() {
        return new DoubleValue(Math.ceil(value));
    }

    /**
     * Implement the XPath round() function
     */

    @Override
    public NumericValue round(int scale) {
        if (Double.isNaN(value)) {
            return this;
        }
        if (Double.isInfinite(value)) {
            return this;
        }
        if (value == 0.0) {
            return this;    // handles the negative zero case
        }

        if (scale == 0 && value > Long.MIN_VALUE && value < Long.MAX_VALUE) {
            if (value >= -0.5 && value < 0.0) {
                return new DoubleValue(-0.0);
            }
            return new DoubleValue(Math.round(value));
        }

        return roundViaDecimal(scale, Round.RoundingRule.HALF_TO_CEILING);

    }

    /**
     * Implement the XPath round() and round-to-half-even() function
     */

    @Override
    public NumericValue round(int scale, Round.RoundingRule roundingRule) {
        if (value == 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return this;
        }

        // Handle some simple special cases
        
        if (scale == 0) {
            if (roundingRule == Round.RoundingRule.CEILING) {
                return ceiling();
            } else if (roundingRule == Round.RoundingRule.FLOOR) {
                return floor();
            } else if (roundingRule == Round.RoundingRule.HALF_TO_CEILING) {
                if (value > Long.MIN_VALUE && value < Long.MAX_VALUE) {
                    if (value >= -0.5 && value < 0.0) {
                        return new DoubleValue(-0.0);
                    }
                    return new DoubleValue(Math.round(value));
                }
            }
        }

        return roundViaDecimal(scale, roundingRule);
    }

    private DoubleValue roundViaDecimal(int scale, Round.RoundingRule roundingRule) {
        if (value == 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return this;
        }

        BigDecimalValue decimalValue = new BigDecimalValue(new BigDecimal(value));
        NumericValue rounded = decimalValue.round(scale, roundingRule);
        double result = rounded.getDoubleValue();
        if (result == 0.0e0) {
            // return negative zero if the original value was negative
            return value < 0 ? DoubleValue.NEGATIVE_ZERO : DoubleValue.ZERO;
        }
        return new DoubleValue(result);
    }

    /**
     * Determine whether the value is negative, zero, or positive
     *
     * @return -1 if negative, 0 if zero (including negative zero) or NaN, +1 if positive
     */

    @Override
    public int signum() {
        if (Double.isNaN(value)) {
            return 0;
        }
        return value > 0 ? 1 : value == 0 ? 0 : -1;
    }

    /**
     * Ask whether this value is negative zero
     *
     * @return true if this value is float or double negative zero
     */
    @Override
    @CSharpReplaceBody(code = "return value == 0.0 && double.IsNegativeInfinity(1.0 / value);") // Better solutions exist but have dependencies
    public boolean isNegativeZero() {
        return value == 0.0 && (Double.doubleToLongBits(value) & FloatingPointConverter.DOUBLE_SIGN_MASK) != 0;
    }

    /**
     * Determine whether the value is a whole number, that is, whether it compares
     * equal to some integer
     */

    @Override
    public boolean isWholeNumber() {
        return value == Math.floor(value) && !Double.isInfinite(value);
    }

    /**
     * Test whether a number is a possible subscript into a sequence, that is,
     * a whole number greater than zero and less than 2^31
     *
     * @return the number as an int if it is a possible subscript, or -1 otherwise
     */
    @Override
    public int asSubscript() {
        if (isWholeNumber() && value > 0 && value <= Integer.MAX_VALUE) {
            return (int)value;
        } else {
            return -1;
        }
    }

    /**
     * Get the absolute value as defined by the XPath abs() function
     *
     * @return the absolute value
     * @since 9.2
     */

    @Override
    public NumericValue abs() {
        if (value > 0.0) {
            return this;
        } else {
            return new DoubleValue(Math.abs(value));
        }
    }

    /**
     * Compare the value to a long.
     *
     * @param other the value to be compared with
     * @return -1 if this is less, 0 if this is equal, +1 if this is greater or if this is NaN
     */

    @Override
    public int compareTo(long other) {
        double otherDouble = (double) other;
        if (value == otherDouble) {
            return 0;
        }
        return value < otherDouble ? -1 : +1;
    }


    @Override
    public int transitiveCompareTo(NumericValue other) {
        if (other instanceof DoubleValue || other instanceof FloatValue) {
            double d2 = other.getDoubleValue();
            if (value == d2) {
                return 0; // handles the positive/negative zero case
            }
            return Double.compare(value, d2);
        } else {
            return -other.transitiveCompareTo(this);
        }
    }

    /**
     * Get a value whose {@code equals()} and {@code hashcode()} methods follows the "same key"
     * rules for comparing the keys of a map. For numeric values, this is done as follows:
     * <ul>
     *     <li>For NaN, return {@code AtomicSortComparer.COLLATION_KEY_NaN;}</li>
     *     <li>For +INF and -INF, call {@code java.lang.Double.hashcode()}</li>
     *     <li>For any value that is numerically equal to some 32-bit signed integer, return
     *         a {@link net.sf.saxon.ma.map.Int32MapKey}</li>
     *     <li>For any other value, return a {@link net.sf.saxon.ma.map.BigDecimalMapKey}</li>
     * </ul>
     *
     * @return a value with the property that the {@code equals()} and {@code hashcode()} methods follow the rules for comparing
     * keys in maps.
     */

    @Override
    public AtomicMatchKey asMapKey(int specVersion) {
        if (isNaN()) {
            return AtomicSortComparer.COLLATION_KEY_NaN;
        } else if (Double.isInfinite(value)) {
            return this;
        } else if (Math.floor(value) == value && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return new net.sf.saxon.ma.map.Int32MapKey((int)value);
        } else {
            return new BigDecimalMapKey(fromDouble(value));
        }
    }

    @CSharpReplaceBody(code = "return Saxon.Impl.Helpers.BigDecimalUtils.ExactValueOf(value);")
    private static BigDecimal fromDouble(double value) {
        return new BigDecimal(value);
    }

    /**
     * Determine whether two atomic values are identical, as determined by XML Schema rules. This is a stronger
     * test than equality (even schema-equality); for example two dateTime values are not identical unless
     * they are in the same timezone.
     * <p>Note that even this check ignores the type annotation of the value. The integer 3 and the short 3
     * are considered identical, even though they are not fully interchangeable. "Identical" means the
     * same point in the value space, regardless of type annotation.</p>
     * <p>NaN is identical to itself.</p>
     * <p>This method treats positive and negative zero as identical, even though they are not defined
     * as such in XSD 1.1. This is justified on the basis that in all cases where values are required
     * to be identical (for example, enumeration facets and keyref constraints), being equal is good enough.</p>
     *
     * @param v the other value to be compared with this one
     * @return true if the two values are identical, false otherwise.
     */

    @Override
    public boolean isIdentical(/*@NotNull*/ AtomicValue v) {
        return v instanceof DoubleValue && DoubleSortComparer.getInstance().comparesEqual(this, v);
    }

    /*
     * Diagnostic method: print the sign, exponent, and significand
     * @param d the double to be diagnosed
     */

//    public static void printInternalForm(double d) {
//        System.err.println("==== Double " + d + " ====");
//        long bits = Double.doubleToLongBits(d);
//        System.err.println("Internal form: " + Long.toHexString(bits));
//        if (bits == 0x7ff0000000000000L) {
//            System.err.println("+Infinity");
//        } else if (bits == 0xfff0000000000000L) {
//            System.err.println("-Infinity");
//        } else if (bits == 0x7ff8000000000000L) {
//            System.err.println("NaN");
//        } else {
//            int s = ((bits >> 63) == 0) ? 1 : -1;
//            int e = (int)((bits >> 52) & 0x7ffL);
//            long m = (e == 0) ?
//                             (bits & 0xfffffffffffffL) << 1 :
//                             (bits & 0xfffffffffffffL) | 0x10000000000000L;
//            int exponent = e-1075;
//            System.err.println("Sign: " + s);
//            System.err.println("Raw Exponent: " + e);
//            System.err.println("Exponent: " + exponent);
//            System.err.println("Significand: " + m);
//            BigDecimal dec = BigDecimal.valueOf(m);
//            if (exponent > 0) {
//                dec = dec.multiply(new BigDecimal(BigInteger.valueOf(2).pow(exponent)));
//            } else {
//                // Next line is sometimes failing, e.g. on -3.62e-5. Not investigated.
//                dec = dec.divide(new BigDecimal(BigInteger.valueOf(2).pow(-exponent)), BigDecimal.ROUND_HALF_EVEN);
//            }
//            System.err.println("Exact value: " + (s>0?"":"-") + dec);
//        }
//    }

//    public static DoubleValue fromInternalForm(String hex) {
//        return new DoubleValue(Double.longBitsToDouble(Long.parseLong(hex, 16)));
//
//    }


}

