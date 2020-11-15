////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.expr.sort.AtomicSortComparer;
import net.sf.saxon.expr.sort.DoubleSortComparer;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AtomicType;
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

    private float value;

    /**
     * Constructor supplying a float
     *
     * @param value the value of the float
     */

    public FloatValue(float value) {
        this.value = value;
        typeLabel = BuiltInAtomicType.FLOAT;
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
     * @param type  the type of the value. This must be a subtype of xs:float, and the
     *              value must conform to this type. The method does not check these conditions.
     */

    public FloatValue(float value, AtomicType type) {
        this.value = value;
        typeLabel = type;
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param typeLabel the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        FloatValue v = new FloatValue(value);
        v.typeLabel = typeLabel;
        return v;
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
        return (double) value;
    }

    /**
     * Get the numeric value converted to a decimal
     *
     * @return a decimal representing this numeric value;
     * @throws ValidationException
     *          if the value cannot be converted, for example if it is NaN or infinite
     */
    @Override
    public BigDecimal getDecimalValue() throws ValidationException {
        return new BigDecimal((double) value);
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
        if (value > Integer.MIN_VALUE && value < Integer.MAX_VALUE) {
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
    public CharSequence getPrimitiveStringValue() {
        return floatToString(value);
    }

    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules. For xs:float, the canonical
     * representation always uses exponential notation.
     */

    @Override
    public CharSequence getCanonicalLexicalRepresentation() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C16);
        return FloatingPointConverter.appendFloat(fsb, value, true);
    }

    /**
     * Internal method used for conversion of a float to a string
     *
     * @param value the actual value
     * @return the value converted to a string, according to the XPath casting rules.
     */

    public static CharSequence floatToString(float value) {
        return FloatingPointConverter.appendFloat(new FastStringBuffer(FastStringBuffer.C16), value, false);
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
        value = d.getFloatValue();
        return this;
    }

    /**
     * Implement the XPath round-to-half-even() function
     */

    @Override
    public NumericValue roundHalfToEven(int scale) {
        DoubleValue d = new DoubleValue(getDoubleValue());
        d = (DoubleValue) d.roundHalfToEven(scale);
        value = d.getFloatValue();
        return this;
    }
    
    
    /**
     * Implement the round-half-up() function
     */

    @Override
    public NumericValue roundHalfUp(int scale) {
        DoubleValue d = new DoubleValue(getDoubleValue());
        value = d.getFloatValue();
        return this;
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
    public int compareTo(NumericValue other) {
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
        return compareTo(Converter.NumericToFloat.INSTANCE.convert(other));
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

    /**
     * Get an object that implements XML Schema comparison semantics
     */

    @Override
    public Comparable getSchemaComparable() {
        // Convert negative to positive zero because Float.compareTo() does the wrong thing
        // Note that for NaN, we return NaN, and rely on the user of the Comparable to use it in a way
        // that ensures NaN != NaN.
        return value == 0.0f ? 0.0f : value;
    }

    /**
     * Get a value whose equals() method follows the "same key" rules for comparing the keys of a map.
     *
     * @return a value with the property that the equals() and hashCode() methods follow the rules for comparing
     * keys in maps.
     */

    @Override
    public AtomicMatchKey asMapKey() {
        if (isNaN()) {
            return AtomicSortComparer.COLLATION_KEY_NaN;
        } else if (Double.isInfinite(value)) {
            return new DoubleValue(value);
        } else {
            try {
                return new BigDecimalValue(value);
            } catch (ValidationException e) {
                // We have already ruled out the values that fail (NaN and INF)
                throw new AssertionError(e);
            }
        }
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
        return v instanceof FloatValue && DoubleSortComparer.getInstance().comparesEqual(this, (FloatValue) v);
    }

    @Override
    public FloatValue asAtomic() {
        return this;
    }
}

