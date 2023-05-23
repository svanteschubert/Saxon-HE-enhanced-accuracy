////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * An implementation class for decimal values other than integers
 * @since 9.8. This class was previously named "DecimalValue". In 9.8 a new DecimalValue
 * class is introduced, to more faithfully reflect the XDM type hierarchy, so that every
 * instance of xs:decimal is now implemented as an instance of DecimalValue.
 */

public final class BigDecimalValue extends DecimalValue {

    public static final int DIVIDE_PRECISION = 18;

    private BigDecimal value;
    private Double doubleValue;

    public static final BigDecimal BIG_DECIMAL_ONE_MILLION = BigDecimal.valueOf(1_000_000);
    public static final BigDecimal BIG_DECIMAL_ONE_BILLION = BigDecimal.valueOf(1_000_000_000);

    public static final BigDecimalValue ZERO = new BigDecimalValue(BigDecimal.valueOf(0));
    public static final BigDecimalValue ONE = new BigDecimalValue(BigDecimal.valueOf(1));
    public static final BigDecimalValue TWO = new BigDecimalValue(BigDecimal.valueOf(2));
    public static final BigDecimalValue THREE = new BigDecimalValue(BigDecimal.valueOf(3));
    public static final BigDecimal MAX_INT = BigDecimal.valueOf(Integer.MAX_VALUE);

    /**
     * Constructor supplying a BigDecimal
     *
     * @param value the value of the DecimalValue
     */

    public BigDecimalValue(BigDecimal value) {
        this.value = value.stripTrailingZeros();
        typeLabel = BuiltInAtomicType.DECIMAL;
    }

    private static final Pattern decimalPattern = Pattern.compile("(\\-|\\+)?((\\.[0-9]+)|([0-9]+(\\.[0-9]*)?))");

    /**
     * Factory method to construct a DecimalValue from a string
     *
     * @param in       the value of the DecimalValue
     * @param validate true if validation is required; false if the caller knows that the value is valid
     * @return the required DecimalValue if the input is valid, or a ValidationFailure encapsulating the error
     *         message if not.
     */

    public static ConversionResult makeDecimalValue(CharSequence in, boolean validate) {

        try {
            return parse(in);
        } catch (NumberFormatException err) {
            ValidationFailure e = new ValidationFailure(
                    "Cannot convert string " + Err.wrap(Whitespace.trim(in), Err.VALUE) +
                            " to xs:decimal: " + err.getMessage());
            e.setErrorCode("FORG0001");
            return e;
        }
    }

    /**
     * Factory method to construct a DecimalValue from a string, throwing an unchecked exception
     * if the value is invalid
     *
     * @param in       the lexical representation of the DecimalValue
     * @return the required DecimalValue
     * @throws NumberFormatException if the value is invalid
     */

    public static BigDecimalValue parse(CharSequence in) throws NumberFormatException {
        FastStringBuffer digits = new FastStringBuffer(in.length());
        int scale = 0;
        int state = 0;
        // 0 - in initial whitespace; 1 - after sign
        // 3 - after decimal point; 5 - in final whitespace
        boolean foundDigit = false;
        int len = in.length();
        for (int i = 0; i < len; i++) {
            char c = in.charAt(i);
            switch (c) {
                case ' ':
                case '\t':
                case '\r':
                case '\n':
                    if (state != 0) {
                        state = 5;
                    }
                    break;
                case '+':
                    if (state != 0) {
                        throw new NumberFormatException("unexpected sign");
                    }
                    state = 1;
                    break;
                case '-':
                    if (state != 0) {
                        throw new NumberFormatException("unexpected sign");
                    }
                    state = 1;
                    digits.cat(c);
                    break;
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    if (state == 0) {
                        state = 1;
                    } else if (state >= 3) {
                        scale++;
                    }
                    if (state == 5) {
                        throw new NumberFormatException("contains embedded whitespace");
                    }
                    digits.cat(c);
                    foundDigit = true;
                    break;
                case '.':
                    if (state == 5) {
                        throw new NumberFormatException("contains embedded whitespace");
                    }
                    if (state >= 3) {
                        throw new NumberFormatException("more than one decimal point");
                    }
                    state = 3;
                    break;
                default:
                    throw new NumberFormatException("invalid character '" + c + "'");
            }

        }

        if (!foundDigit) {
            throw new NumberFormatException("no digits in value");
        }

        // remove insignificant trailing zeroes
        while (scale > 0) {
            if (digits.charAt(digits.length() - 1) == '0') {
                digits.setLength(digits.length() - 1);
                scale--;
            } else {
                break;
            }
        }
        if (digits.isEmpty() || (digits.length() == 1 && digits.charAt(0) == '-')) {
            return BigDecimalValue.ZERO;
        }
        BigInteger bigInt = new BigInteger(digits.toString());
        BigDecimal bigDec = new BigDecimal(bigInt, scale);
        return new BigDecimalValue(bigDec);
    }

    /**
     * Test whether a string is castable to a decimal value
     *
     * @param in the string to be tested
     * @return true if the string has the correct format for a decimal
     */

    public static boolean castableAsDecimal(CharSequence in) {
        CharSequence trimmed = Whitespace.trimWhitespace(in);
        return decimalPattern.matcher(trimmed).matches();
    }

    /**
     * Constructor supplying a double
     *
     * @param in the value of the DecimalValue
     * @throws ValidationException if the supplied value cannot be converted, typically because it is INF or NaN.
     */

    public BigDecimalValue(double in) throws ValidationException {
        try {
            BigDecimal d = new BigDecimal(in);
            value = d.stripTrailingZeros();
        } catch (NumberFormatException err) {
            // Must be a special value such as NaN or infinity
            ValidationFailure e = new ValidationFailure(
                    "Cannot convert double " + Err.wrap(in + "", Err.VALUE) + " to decimal");
            e.setErrorCode("FOCA0002");
            throw e.makeException();
        }
        typeLabel = BuiltInAtomicType.DECIMAL;
    }

    /**
     * Constructor supplying a long integer
     *
     * @param in the value of the DecimalValue
     */

    public BigDecimalValue(long in) {
        value = BigDecimal.valueOf(in);
        typeLabel = BuiltInAtomicType.DECIMAL;
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param typeLabel the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        BigDecimalValue v = new BigDecimalValue(value);
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
        return BuiltInAtomicType.DECIMAL;
    }

    /**
     * Get the numeric value as a double
     *
     * @return A double representing this numeric value; NaN if it cannot be
     *         converted
     */
    @Override
    public double getDoubleValue() {
        if (doubleValue == null) {
            double d = value.doubleValue();
            doubleValue = d;
            return d;
        } else {
            return doubleValue;
        }
    }

    /**
     * Get the numeric value converted to a float
     *
     * @return a float representing this numeric value; NaN if it cannot be converted
     */
    @Override
    public float getFloatValue() {
        return (float) value.doubleValue();
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
        return (long) value.doubleValue();
    }

    /**
     * Get the value
     */

    @Override
    public BigDecimal getDecimalValue() {
        return value;
    }

    /**
     * Get the hashCode. This must conform to the rules for other NumericValue hashcodes
     *
     * @see NumericValue#hashCode
     */

    public int hashCode() {
        BigDecimal round = value.setScale(0, RoundingMode.DOWN);
        long value = round.longValue();
        if (value > Integer.MIN_VALUE && value < Integer.MAX_VALUE) {
            return (int) value;
        } else {
            return Double.valueOf(getDoubleValue()).hashCode();
        }
    }

    @Override
    public boolean effectiveBooleanValue() {
        return value.signum() != 0;
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

//    public CharSequence getStringValueCS() {
//        return decimalToString(value, new FastStringBuffer(20));
//    }

    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules. For xs:decimal, the canonical
     * representation always contains a decimal point.
     */

    @Override
    public CharSequence getCanonicalLexicalRepresentation() {
        String s = getStringValue();
        if (s.indexOf('.') < 0) {
            s += ".0";
        }
        return s;
    }

    /**
     * Get the value as a String
     *
     * @return a String representation of the value
     */

    /*@NotNull*/
    @Override
    public CharSequence getPrimitiveStringValue() {
        return decimalToString(value, new FastStringBuffer(FastStringBuffer.C16));
    }

    /**
     * Convert a decimal value to a string, using the XPath rules for formatting
     *
     * @param value the decimal value to be converted
     * @param fsb   the FastStringBuffer to which the value is to be appended
     * @return the supplied FastStringBuffer, suitably populated
     */

    public static FastStringBuffer decimalToString(BigDecimal value, FastStringBuffer fsb) {
        // Can't use BigDecimal#toString() under JDK 1.5 because this produces values like "1E-5".
        // Can't use BigDecimal#toPlainString() because it retains trailing zeroes to represent the scale
        int scale = value.scale();
        if (scale == 0) {
            fsb.append(value.toString());
            return fsb;
        } else if (scale < 0) {
            String s = value.abs().unscaledValue().toString();
            if (s.equals("0")) {
                fsb.cat('0');
                return fsb;
            }
            //FastStringBuffer sb = new FastStringBuffer(s.length() + (-scale) + 2);
            if (value.signum() < 0) {
                fsb.cat('-');
            }
            fsb.append(s);
            for (int i = 0; i < -scale; i++) {
                fsb.cat('0');
            }
            return fsb;
        } else {
            String s = value.abs().unscaledValue().toString();
            if (s.equals("0")) {
                fsb.cat('0');
                return fsb;
            }
            int len = s.length();
            //FastStringBuffer sb = new FastStringBuffer(len+1);
            if (value.signum() < 0) {
                fsb.cat('-');
            }
            if (scale >= len) {
                fsb.append("0.");
                for (int i = len; i < scale; i++) {
                    fsb.cat('0');
                }
                fsb.append(s);
            } else {
                fsb.append(s.substring(0, len - scale));
                fsb.cat('.');
                fsb.append(s.substring(len - scale));
            }
            return fsb;
        }
    }

    /**
     * Negate the value
     */

    @Override
    public NumericValue negate() {
        return new BigDecimalValue(value.negate());
    }

    /**
     * Implement the XPath floor() function
     */

    @Override
    public NumericValue floor() {
        return new BigDecimalValue(value.setScale(0, RoundingMode.FLOOR));
    }

    /**
     * Implement the XPath ceiling() function
     */

    @Override
    public NumericValue ceiling() {
        return new BigDecimalValue(value.setScale(0, RoundingMode.CEILING));
    }

    /**
     * Implement the XPath round() function
     */

    @Override
    public NumericValue round(int scale) {
        // The XPath rules say that we should round to the nearest integer, with .5 rounding towards
        // positive infinity. Unfortunately this is not one of the rounding modes that the Java BigDecimal
        // class supports, so we need different rules depending on the value.

        // If the value is positive, we use ROUND_HALF_UP; if it is negative, we use ROUND_HALF_DOWN (here "UP"
        // means "away from zero")

        if (scale >= value.scale()) {
            // no-op - see bug #4027
            return this;
        }

        switch (value.signum()) {
            case -1:
                return new BigDecimalValue(value.setScale(scale, RoundingMode.HALF_DOWN));
            case 0:
                return this;
            case +1:
                return new BigDecimalValue(value.setScale(scale, RoundingMode.HALF_UP));
            default:
                // can't happen
                return this;
        }

    }

    /**
     * Implement the XPath round-half-to-even() function
     */

    @Override
    public NumericValue roundHalfToEven(int scale) {
        if (scale >= value.scale()) {
            return this;
        }
        BigDecimal scaledValue = value.setScale(scale, RoundingMode.HALF_EVEN);
        return new BigDecimalValue(scaledValue.stripTrailingZeros());
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
     * Determine whether the value is a whole number, that is, whether it compares
     * equal to some integer
     */

    @Override
    public boolean isWholeNumber() {
        return value.scale() == 0 ||
                value.compareTo(value.setScale(0, RoundingMode.DOWN)) == 0;
    }

    /**
     * Test whether a number is a possible subscript into a sequence, that is,
     * a whole number greater than zero and less than 2^31
     *
     * @return the number as an int if it is a possible subscript, or -1 otherwise
     */
    @Override
    public int asSubscript() {
        if (isWholeNumber() && value.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(MAX_INT) <= 0) {
            try {
                return (int)longValue();
            } catch (XPathException e) {
                return -1;
            }
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
        if (value.signum() > 0) {
            return this;
        } else {
            return new BigDecimalValue(value.negate());
        }
    }

    /**
     * Compare the value to another numeric value
     */

    @Override
    public int compareTo(NumericValue other) {
        if (NumericValue.isInteger(other)) {
            // deliberately triggers a ClassCastException if other value is the wrong type
            try {
                return value.compareTo(other.getDecimalValue());
            } catch (XPathException err) {
                throw new AssertionError("Conversion of integer to decimal should never fail");
            }
        } else if (other instanceof BigDecimalValue) {
            return value.compareTo(((BigDecimalValue) other).value);
        } else if (other instanceof FloatValue) {
            return -other.compareTo(this);
        } else {
            return super.compareTo(other);
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
        return value.compareTo(BigDecimal.valueOf(other));
    }

    /**
     * Get a Comparable value that implements the XML Schema ordering comparison semantics for this value.
     * Returns null if the value is not comparable according to XML Schema rules. The default implementation
     * returns the value itself if it is comparable, or null otherwise. This is modified for types such as
     * xs:duration which allow ordering comparisons in XML Schema, but not in XPath.
     * <p>In the case of data types that are partially ordered, the returned Comparable extends the standard
     * semantics of the compareTo() method by returning the value {@link net.sf.saxon.om.SequenceTool#INDETERMINATE_ORDERING} when there
     * is no defined order relationship between two given values.</p>
     */

    /**
     * Get an object that implements XML Schema comparison semantics
     */

    @Override
    public Comparable getSchemaComparable() {
        return new DecimalComparable(this);
    }

    /**
     * A Comparable that performs comparison of a DecimalValue either with another
     * DecimalValue or with some other representation of an XPath numeric value
     */

    protected static class DecimalComparable implements Comparable {

        protected BigDecimalValue value;

        public DecimalComparable(BigDecimalValue value) {
            this.value = value;
        }

        public BigDecimal asBigDecimal() {
            return value.getDecimalValue();
        }

        @Override
        public int compareTo(Object o) {
            if (o instanceof DecimalComparable) {
                return asBigDecimal().compareTo(((DecimalComparable) o).asBigDecimal());
            } else if (o instanceof Int64Value.Int64Comparable) {
                return asBigDecimal().compareTo(BigDecimal.valueOf(((Int64Value.Int64Comparable) o).asLong()));
            } else if (o instanceof BigIntegerValue.BigIntegerComparable) {
                return asBigDecimal().compareTo(new BigDecimal(((BigIntegerValue.BigIntegerComparable) o).asBigInteger()));
            } else {
                return SequenceTool.INDETERMINATE_ORDERING;
            }
        }

        public boolean equals(Object o) {
            return compareTo(o) == 0;
        }

        public int hashCode() {
            // Must align with hashCodes for other subtypes of xs:decimal
            if (value.isWholeNumber()) {
                IntegerValue iv = Converter.DecimalToInteger.INSTANCE.convert(value);
                return iv.getSchemaComparable().hashCode();

            }
            return value.hashCode();
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
        return (v instanceof DecimalValue) && equals(v);
    }

}

