////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.type.ValidationFailure;

import java.math.BigDecimal;

/**
 * NumericValue is an abstract superclass for IntegerValue, DecimalValue,
 * FloatValue, and DoubleValue
 */

public abstract class NumericValue extends AtomicValue
        implements Comparable<NumericValue>, AtomicMatchKey {

    /**
     * Get a numeric value by parsing a string; the type of numeric value depends
     * on the lexical form of the string, following the rules for XPath numeric
     * literals.
     *
     * @param in the input string
     * @return a NumericValue representing the value of the string. Returns Double.NaN if the
     *         value cannot be parsed as a string.
     */

    /*@NotNull*/
    public static NumericValue parseNumber(/*@NotNull*/ String in) {
        if (in.indexOf('e') >= 0 || in.indexOf('E') >= 0 || in.indexOf('.') >= 0) {
            ConversionResult v = BigDecimalValue.makeDecimalValue(in, true);
            if (v instanceof ValidationFailure) {
                return DoubleValue.NaN;
            } else {
                return (NumericValue) v;
            }
        } else {
            ConversionResult v = Int64Value.stringToInteger(in);
            if (v instanceof ValidationFailure) {
                return DoubleValue.NaN;
            } else {
                return (NumericValue) v;
            }
        }
    }

    /**
     * Get the numeric value as a double
     *
     * @return A double representing this numeric value; NaN if it cannot be
     *         converted
     */
    public abstract double getDoubleValue();

    /**
     * Get the numeric value converted to a float
     *
     * @return a float representing this numeric value; NaN if it cannot be converted
     */

    public abstract float getFloatValue();

    /**
     * Get the numeric value converted to a decimal
     *
     * @return a decimal representing this numeric value;
     * @throws ValidationException if the value cannot be converted, for example if it is NaN or infinite
     */

    public abstract BigDecimal getDecimalValue() throws ValidationException;

    /**
     * Get the effective boolean value of the value. This override of this method throws no exceptions.
     *
     * @return true, unless the value is boolean false, numeric zero, or
     * zero-length string
     */
    @Override
    public abstract boolean effectiveBooleanValue();

    /**
     * Test whether a value is an integer (an instance of a subtype of xs:integer)
     *
     * @param value the value being tested
     * @return true if the value is an instance of xs:integer or a type derived therefrom
     */

    public static boolean isInteger(AtomicValue value) {
        return value instanceof IntegerValue;
    }

    /**
     * Return the numeric value as a Java long.
     *
     * @return the numeric value as a Java long. This performs truncation
     *         towards zero.
     * @throws net.sf.saxon.trans.XPathException
     *          if the value cannot be converted
     */
    public abstract long longValue() throws XPathException;

    /**
     * Change the sign of the number
     *
     * @return a value, of the same type as the original, with its sign
     *         inverted
     */

    public abstract NumericValue negate();

    /**
     * Implement the XPath floor() function
     *
     * @return a value, of the same type as that supplied, rounded towards
     *         minus infinity
     */

    /*@NotNull*/
    public abstract NumericValue floor();

    /**
     * Implement the XPath ceiling() function
     *
     * @return a value, of the same type as that supplied, rounded towards
     *         plus infinity
     */

    /*@NotNull*/
    public abstract NumericValue ceiling();

    /**
     * Implement the XPath round() function
     * @param scale the number of decimal places required in the result (supply 0 for rounding to an integer)
     * @return a value, of the same type as that supplied, rounded towards the
     *         nearest whole number (0.5 rounded up)
     */

    public abstract NumericValue round(int scale);

    /**
     * Implement the XPath 2.0 round-half-to-even() function
     *
     * @param scale the decimal position for rounding: e.g. 2 rounds to a
     *              multiple of 0.01, while -2 rounds to a multiple of 100
     * @return a value, of the same type as the original, rounded towards the
     *         nearest multiple of 10**(-scale), with rounding towards the nearest
     *         even number if two values are equally near
     */

    public abstract NumericValue roundHalfToEven(int scale);

    
    /**
     * Implement the round-half-up() function
     *
     * @param scale the decimal position for rounding: e.g. 2 rounds to a
     *              multiple of 0.01, while -2 rounds to a multiple of 100
     * @return a value, of the same type as the original, rounded towards the
     *         nearest multiple of 10**(-scale), with rounding towards "nearest neighbor" 
     *         unless both neighbors are equidistant, in which case round up. 
     *         Note that this is the rounding mode commonly taught at school.
     */

    public abstract NumericValue roundHalfUp(int scale);    
    
    /**
     * Ask whether the value is negative, zero, or positive
     *
     * @return -1 if negative, 0 if zero (including negative zero) or NaN, +1 if positive
     */

    public abstract int signum();

    /**
     * Ask whether this value is negative zero
     * @return true if this value is float or double negative zero
     */

    public boolean isNegativeZero() {
        return false;
    }

    /**
     * Ask whether the value is a whole number, that is, whether it compares
     * equal to some integer
     *
     * @return true if the value is a whole number
     */

    public abstract boolean isWholeNumber();

    /**
     * Test whether a number is a possible subscript into a sequence, that is,
     * a whole number greater than zero and less than 2^31
     * @return the number as an int if it is a possible subscript, or -1 otherwise
     */

    public abstract int asSubscript();

    /**
     * Get the absolute value as defined by the XPath abs() function
     *
     * @return the absolute value
     * @since 9.2
     */

    public abstract NumericValue abs();

    /**
     * Get a Comparable value that implements the XPath ordering comparison semantics for this value.
     * Returns null if the value is not comparable according to XPath rules. The implementation
     * for all kinds of NumericValue returns the value itself.
     *
     * @param ordered  true if an ordered comparison is required. In this case the result is null if the
     *                 type is unordered; in other cases the returned value will be a Comparable.
     * @param collator the collation to be used when comparing strings
     * @param implicitTimezone  the implicit timezone in the dynamic context, used when comparing
     * dates/times with and without timezone
     * @return a collation key that implements the comparison semantics
     */

    /*@NotNull*/
    @Override
    public final AtomicMatchKey getXPathComparable(boolean ordered, StringCollator collator, int implicitTimezone) {
        return this;
    }

    /**
     * Compare the value to another numeric value
     *
     * @param other The other numeric value
     * @return -1 if this one is the lower, 0 if they are numerically equal,
     *         +1 if this one is the higher, or if either value is NaN. Where NaN values are
     *         involved, they should be handled by the caller before invoking this method.
     * @throws ClassCastException if the other value is not a NumericValue
     *                            (the parameter is declared as Object to satisfy the Comparable
     *                            interface)
     */

    // This is the default implementation. Subclasses of number avoid the conversion to double
    // when comparing with another number of the same type.
    @Override
    public int compareTo(/*@NotNull*/ NumericValue other) {
        double a = getDoubleValue();
        double b = other.getDoubleValue();
        // IntelliJ says this can be replaced with Double.compare(). But it can't. Double.compare()
        // treats positive and negative zero as not equal; we want them treated as equal. XSLT3 test case
        // boolean-014.  MHK 2020-02-17
        //noinspection UseCompareMethod
        if (a == b) {
            return 0;
        }
        if (a < b) {
            return -1;
        }
        return +1;
    }

    /**
     * Compare the value to a long
     *
     * @param other the value to be compared with
     * @return -1 if this is less, 0 if this is equal, +1 if this is greater or if this is NaN
     */

    public abstract int compareTo(long other);

    /**
     * The equals() function compares numeric equality among integers, decimals, floats, doubles, and
     * their subtypes
     *
     * @param other the value to be compared with this one
     * @return true if the two values are numerically equal
     */

    public final boolean equals(/*@NotNull*/ Object other) {
        return other instanceof NumericValue && compareTo((NumericValue)other) == 0;
    }

    /**
     * hashCode() must be the same for two values that are equal. One
     * way to ensure this is to convert the value to a double, and take the
     * hashCode of the double. But this is expensive in the common case where
     * we are comparing integers. So we adopt the rule: for values that are in
     * the range of a Java Integer, we use the int value as the hashcode. For
     * values outside that range, we convert to a double and take the hashCode of
     * the double. This method needs to have a compatible implementation in
     * each subclass.
     *
     * @return the hash code of the numeric value
     */

    public abstract int hashCode();

    /**
     * Produce a string representation of the value
     *
     * @return The result of casting the number to a string
     */

    public String toString() {
        return getStringValue();
    }

}

