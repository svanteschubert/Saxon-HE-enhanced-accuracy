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
import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.functions.Round;
import net.sf.saxon.ma.map.BigDecimalMapKey;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.transpile.CSharpReplaceException;
import net.sf.saxon.type.AtomicMetadata;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Converter;
import net.sf.saxon.type.ValidationException;

import java.math.BigDecimal;

/**
 * A numeric (single precision floating point) value
 */

public final class FloatValue extends NumericValue {

    public static final FloatValue ZERO = new FloatValue((float) 0.0);
    public static final FloatValue NEGATIVE_ZERO = new FloatValue((float) -0.0);
    public static final FloatValue ONE = new FloatValue((float) 1.0);
    public static final FloatValue NaN = new FloatValue(Float.NaN);

    private final float value;

    /**
     * Constructor supplying a float
     *
     * @param value the value of the float
     */

    public FloatValue(float value) {
        super(BuiltInAtomicType.FLOAT);
        this.value = value;
    }

    /**
     * Static factory method (for convenience in compiled bytecode)
     *
     * @param value the value of the float
     * @return the FloatValue
     */

    public static FloatValue makeFloatValue(float value) {
        return new FloatValue(value);
    }

    /**
     * Constructor supplying a float and an AtomicType, for creating
     * a value that belongs to a user-defined subtype of xs:float. It is
     * the caller's responsibility to ensure that the supplied value conforms
     * to the supplied type.
     *
     * @param value the value of the NumericValue
     * @param typeLabel  the type of the value. This must be a subtype of xs:float, and the
     *              value must conform to this type. The method does not check these conditions.
     */

    public FloatValue(float value, AtomicMetadata typeLabel) {
        super(typeLabel);
        this.value = value;
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param metadata the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    @Override
    public AtomicValue withMetadata(AtomicMetadata metadata) {
        return new FloatValue(value, metadata);
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.FLOAT;
    }

    /**
     * Get the value
     */

    @Override
    public float getFloatValue() {
        return value;
    }

    @Override
    public double getDoubleValue() {
        return value;
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
    @CSharpReplaceException(from = "java.lang.NumberFormatException", to = "System.ArgumentOutOfRangeException")
    public BigDecimal getExactDecimalValue() throws ValidationException {
        try {
            //noinspection UnpredictableBigDecimalConstructorCall
            return new BigDecimal((double)value);
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

    @Override
    public int hashCode() {
        if (isNaN()) {
            return AtomicSortComparer.COLLATION_KEY_NaN.hashCode();
        } else if (Float.isInfinite(value)) {
            return Double.valueOf(getDoubleValue()).hashCode();
        } else if (isWholeNumber() && value > Integer.MIN_VALUE && value < Integer.MAX_VALUE) {
            return (int) value;
        } else {
            return Double.valueOf(getDoubleValue()).hashCode();
        }
    }

    /**
     * Test whether the value is the double/float value NaN
     */

    @Override
    public boolean isNaN() {
        return Float.isNaN(value);
    }

    /**
     * Get the effective boolean value
     *
     * @return true unless the value is zero or NaN
     */
    @Override
    public boolean effectiveBooleanValue() {
        return (value != 0.0 && !Float.isNaN(value));
    }


    /**
     * Get the value as a String
     * @return a String representation of the value
     */

//    public String getStringValue() {
//       return getStringValueCS().toString();
//    }

    /**
     * Get the value as a String
     *
     * @return a String representation of the value
     */

    /*@NotNull*/
    @Override
    public UnicodeString getPrimitiveStringValue() {
        return floatToString(value);
    }

    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules. For xs:float, the canonical
     * representation always uses exponential notation.
     * @return the value, represented as a string in exponential notation
     */

    @Override
    public UnicodeString getCanonicalLexicalRepresentation() {
        return FloatingPointConverter.appendFloat(value, true);
    }

    /**
     * Internal method used for conversion of a float to a string
     *
     * @param value the actual value
     * @return the value converted to a string, according to the XPath casting rules.
     */

    public static UnicodeString floatToString(float value) {
        return FloatingPointConverter.appendFloat(value, false);
    }

    /**
     * Negate the value
     */

    @Override
    public NumericValue negate() {
        return new FloatValue(-value);
    }

    /**
     * Implement the XPath floor() function
     */

    @Override
    public NumericValue floor() {
        return new FloatValue((float) Math.floor(value));
    }

    /**
     * Implement the XPath ceiling() function
     */

    @Override
    public NumericValue ceiling() {
        return new FloatValue((float) Math.ceil(value));
    }

    /**
     * Implement the XPath round() function
     */

    @Override
    public NumericValue round(int scale) {
        if (Float.isNaN(value)) {
            return this;
        }
        if (Float.isInfinite(value)) {
            return this;
        }
        if (value == 0.0) {
            return this;    // handles the negative zero case
        }

        if (scale == 0 && value > Integer.MIN_VALUE && value < Integer.MAX_VALUE) {
            if (value >= -0.5 && value < 0.0) {
                return new FloatValue(-0.0f);
            }
            return new FloatValue((float) Math.round(value));
        }
        DoubleValue d = new DoubleValue(getDoubleValue());
        d = (DoubleValue) d.round(scale);
        return new FloatValue(d.getFloatValue());
    }

    /**
     * Implement the XPath round-to-half-even() function
     */

    @Override
    public NumericValue round(int scale, Round.RoundingRule roundingRule) {
        DoubleValue d = new DoubleValue(getDoubleValue());
        d = (DoubleValue) d.round(scale, roundingRule);
        return new FloatValue(d.getFloatValue());
    }

    /**
     * Determine whether the value is negative, zero, or positive
     *
     * @return -1 if negative, 0 if zero (including negative zero), +1 if positive, NaN if NaN
     */

    @Override
    public int signum() {
        if (Float.isNaN(value)) {
            return 0;
        }
        return compareTo(0);
    }

    /**
     * Ask whether this value is negative zero
     *
     * @return true if this value is float or double negative zero
     */
    @Override
    @CSharpReplaceBody(code="return value == 0.0f && float.IsNegativeInfinity(1.0f / value);")  // Better solutions exist but have dependencies
    public boolean isNegativeZero() {
        return value == 0.0 && (Float.floatToIntBits(value) & FloatingPointConverter.FLOAT_SIGN_MASK) != 0;
    }


    /**
     * Determine whether the value is a whole number, that is, whether it compares
     * equal to some integer
     */

    @Override
    public boolean isWholeNumber() {
        return value == Math.floor(value) && !Float.isInfinite(value);
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
            return (int) value;
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
            return new FloatValue(Math.abs(value));
        }
    }

    @Override
    public int compareTo(XPathComparable other) {
        if (other instanceof NumericValue) {
            if (other instanceof FloatValue) {
                float otherFloat = ((FloatValue) other).value;
                // Do not rewrite as Float.compare() - see IntelliJ bug IDEA-196419
                if (value == otherFloat) {
                    return 0;
                } else if (value < otherFloat) {
                    return -1;
                } else {
                    return +1;
                }
            }
            if (other instanceof DoubleValue) {
                return super.compareTo(other);
            }
            return compareTo(Converter.NumericToFloat.INSTANCE.convert((NumericValue)other));
        } else {
            throw new ClassCastException("Cannot compare xs:float to " + other);
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
        float otherFloat = (float) other;
        if (value == otherFloat) {
            return 0;
        }
        return value < otherFloat ? -1 : +1;
    }

    @Override
    public int transitiveCompareTo(NumericValue other) {
        if (other instanceof FloatValue fv2) {
            float f1 = getFloatValue();
            float f2 = fv2.getFloatValue();
            if (f1 == f2) {
                return 0; // handles positive/negative zero comparison
            }
            return Float.compare(f1, f2);
        } else if (other instanceof DoubleValue) {
            double d1 = getDoubleValue();
            double d2 = other.getDoubleValue();
            if (d1 == d2) {
                return 0; // handles positive/negative zero comparison
            }
            return Double.compare(d1, d2);
        } else if (other instanceof Int64Value) {
            try {
                return compareTo(other.longValue());
            } catch (XPathException e) {
                throw new UncheckedXPathException(e);
            }
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
        if (Float.isNaN(value)) {
            return AtomicSortComparer.COLLATION_KEY_NaN;
        } else if (Float.isInfinite(value)) {
            return this;
        } else if (Math.floor(value) == value && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return new net.sf.saxon.ma.map.Int32MapKey((int) value);
        } else {
            return new BigDecimalMapKey(fromFloat(value));
        }
    }

    @CSharpReplaceBody(code="return Saxon.Impl.Helpers.BigDecimalUtils.ExactValueOf(value);")
    private static BigDecimal fromFloat(float value) {
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
     *
     * @param v the other value to be compared with this one
     * @return true if the two values are identical, false otherwise.
     */

    @Override
    public boolean isIdentical(/*@NotNull*/ AtomicValue v) {
        return v instanceof FloatValue && DoubleSortComparer.getInstance().comparesEqual(this, v);
    }

    @Override
    public AtomicValue asAtomic() {
        return this;
    }
}

