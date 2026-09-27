////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.functions.Round;
import net.sf.saxon.ma.map.BigDecimalMapKey;
import net.sf.saxon.str.BMPString;
import net.sf.saxon.str.StringConstants;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
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

    private final BigDecimal value;

    // cached property holding the equivalent double. (Note: breaks immutability...)
    private double doubleValue = Double.NaN; // meaning unknown

    public final static BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1000);
    public static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
    public static final BigDecimal ONE_BILLION = BigDecimal.valueOf(1_000_000_000);

    public static final BigDecimalValue ZERO = new BigDecimalValue(BigDecimal.ZERO);
    public static final BigDecimalValue ONE = new BigDecimalValue(BigDecimal.ONE);
    public static final BigDecimalValue TWO = new BigDecimalValue(BigDecimal.valueOf(2));
    public static final BigDecimal MIN_INT = BigDecimal.valueOf(Integer.MIN_VALUE);
    public static final BigDecimal MAX_INT = BigDecimal.valueOf(Integer.MAX_VALUE);


    public final static BigDecimal SIXTY = BigDecimal.valueOf(60);

    public final static BigDecimal HOURS_PER_DAY = BigDecimal.valueOf(24);
    public final static BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf(24 * 60 * 60);
    public final static BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(60 * 60);
    public final static BigDecimal SECONDS_PER_MINUTE = SIXTY;

    /**
     * Constructor supplying a BigDecimal
     *
     * @param value the value of the DecimalValue
     */

    public BigDecimalValue(BigDecimal value) {
        super(BuiltInAtomicType.DECIMAL);
        this.value = value.stripTrailingZeros();
    }

    /**
     * Constructor supplying a BigDecimal and a type label
     *
     * @param value the value of the DecimalValue
     * @param typeLabel the type label, which must be a subtype of DECIMAL
     */

    public BigDecimalValue(BigDecimal value, AtomicMetadata typeLabel) {
        super(typeLabel);
        this.value = value.stripTrailingZeros();
    }

    /**
     * Internal constructor bypassing the `stripTrailingZeros` when it is known to be unnecessary
     *
     * @param value     the value of the DecimalValue
     * @param typeLabel the type label, which must be a subtype of DECIMAL
     * @param alreadyScaled should be set to true (but the value is ignored)
     */

    private BigDecimalValue(BigDecimal value, AtomicMetadata typeLabel, boolean alreadyScaled) {
        super(typeLabel);
        this.value = value;
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

    public static ConversionResult makeDecimalValue(String in, boolean validate) {

        try {
            return parse(in);
        } catch (NumberFormatException err) {
            ValidationFailure e = new ValidationFailure(
                    "Cannot convert string " + Err.wrap(in, Err.VALUE) +
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

    public static NumericValue parse(CharSequence in) throws NumberFormatException {
        StringBuilder digits = new StringBuilder(in.length());
        int scale = 0;
        int state = 0;
        // 0 - in initial whitespace; 1 - after sign
        // 3 - after decimal point; 5 - in final whitespace
        boolean foundDigit = false;
        boolean foundDot = false;
        int len = in.length();
        for (int i = 0; i < len; i++) {
            char c = in.charAt(i);
            switch (c) {
                case ' ', '\t', '\r', '\n' -> {
                    if (state != 0) {
                        state = 5;
                    }
                }
                case '+' -> {
                    if (state != 0) {
                        throw new NumberFormatException("unexpected sign");
                    }
                    state = 1;
                }
                case '-' -> {
                    if (state != 0) {
                        throw new NumberFormatException("unexpected sign");
                    }
                    state = 1;
                    digits.append(c);
                }
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    if (state == 0) {
                        state = 1;
                    } else if (state >= 3) {
                        scale++;
                    }
                    if (state == 5) {
                        throw new NumberFormatException("contains embedded whitespace");
                    }
                    digits.append(c);
                    foundDigit = true;
                }
                case '.' -> {
                    if (state == 5) {
                        throw new NumberFormatException("contains embedded whitespace");
                    }
                    if (state >= 3) {
                        throw new NumberFormatException("more than one decimal point");
                    }
                    foundDot = true;
                    state = 3;
                }
                default -> throw new NumberFormatException("invalid character '" + c + "'");
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
        if (digits.length() == 0 || (digits.length() == 1 && digits.charAt(0) == '-')) {
            return BigDecimalValue.ZERO;
        }
        if (!foundDot && digits.length() < 16) {
            // For simple constructs like xs:decimal('2'), return an Int64Value. Altova MapForce generates this a lot.
            return new Int64Value(Long.parseLong(digits.toString()));
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

    public static boolean castableAsDecimal(String in) {
        String trimmed = Whitespace.trim(in);
        return decimalPattern.matcher(trimmed).matches();
    }

    /**
     * Constructor supplying a double
     *
     * @param in the value of the DecimalValue
     * @throws ValidationException if the supplied value cannot be converted, typically because it is INF or NaN.
     */

    public BigDecimalValue(double in) throws ValidationException {
        super(BuiltInAtomicType.DECIMAL);
        try {
            value = new BigDecimal(in);
            // Note, this gives a different result from BigDecimal.valueOf(in) - it retains more precision.
            //d.stripTrailingZeros();
        } catch (Exception err) {
            // Must be a special value such as NaN or infinity
            ValidationFailure e = new ValidationFailure(
                    "Cannot convert double " + Err.wrap(in + "", Err.VALUE) + " to decimal");
            e.setErrorCode("FOCA0002");
            throw e.makeException();
        }
    }

    /**
     * Constructor supplying a long integer
     *
     * @param in the value of the DecimalValue
     */

    public BigDecimalValue(long in) {
        super(BuiltInAtomicType.DECIMAL);
        value = BigDecimal.valueOf(in);
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param metadata the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    @Override
    public AtomicValue withMetadata(AtomicMetadata metadata) {
        if (metadata.getType().getPrimitiveItemType() == BuiltInAtomicType.INTEGER) {
            return IntegerValue.makeIntegerValue(value.toBigInteger()).withMetadata(metadata);
        } else {
            return new BigDecimalValue(value, metadata, true);
        }
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
        if (Double.isNaN(doubleValue)) {
            doubleValue = value.doubleValue();
        }
        return doubleValue;
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
        BigDecimal round = value.stripTrailingZeros();
        if (isWholeNumber()) {
            long longVal;
            try {
                longVal = round.longValue();
            } catch (Exception e) {
                // This path is for C#, where converting BigDecimal to long gives an OverflowException if out of range
                longVal = Long.MAX_VALUE;
            }
            if (longVal > Integer.MIN_VALUE && longVal < Integer.MAX_VALUE) {
                return (int) longVal;
            }
        }
        return Double.valueOf(getDoubleValue()).hashCode();
    }

    @Override
    public boolean effectiveBooleanValue() {
        return value.signum() != 0;
    }

    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules. For xs:decimal, the canonical
     * representation always contains a decimal point.
     * @return the canonical lexical representation
     */

    @Override
    public UnicodeString getCanonicalLexicalRepresentation() {
        UnicodeString s = this.getUnicodeStringValue().tidy();
        if (s.indexOf('.') < 0) {
            s = s.concat(StringConstants.POINT_ZERO);
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
    public UnicodeString getPrimitiveStringValue() {
        return BMPString.of(decimalToString(value, new StringBuilder(16)).toString());
    }

    /**
     * Convert a decimal value to a string, using the XPath rules for formatting
     *
     * @param value the decimal value to be converted
     * @param fsb   the StringBuilder to which the value is to be appended
     * @return the supplied StringBuilder, suitably populated
     */

    @CSharpReplaceBody(code="return fsb.append(value.ToString(\"G\", System.Globalization.CultureInfo.InvariantCulture));")
    public static StringBuilder decimalToString(BigDecimal value, StringBuilder fsb) {
        // Can't use BigDecimal#toString() under JDK 1.5 because this produces values like "1E-5".
        // Can't use BigDecimal#toPlainString() because it retains trailing zeroes to represent the scale
        int scale = value.scale();
        if (scale == 0) {
            fsb.append(value);
            return fsb;
        } else if (scale < 0) {
            String s = value.abs().unscaledValue().toString();
            if (s.equals("0")) {
                fsb.append('0');
                return fsb;
            }
            //StringBuilder sb = new StringBuilder(s.length() + (-scale) + 2);
            if (value.signum() < 0) {
                fsb.append('-');
            }
            fsb.append(s);
            fsb.append("0".repeat(Math.max(0, -scale)));
            return fsb;
        } else {
            String s = value.abs().unscaledValue().toString();
            if (s.equals("0")) {
                fsb.append('0');
                return fsb;
            }
            int len = s.length();
            //StringBuilder sb = new StringBuilder(len+1);
            if (value.signum() < 0) {
                fsb.append('-');
            }
            if (scale >= len) {
                fsb.append("0.");
                fsb.append("0".repeat(scale - len));
                fsb.append(s);
            } else {
                fsb.append(s, 0, len - scale);
                fsb.append('.');
                fsb.append(s.substring(len - scale));
            }
            return fsb;
        }
    }

    @CSharpReplaceBody(code = "return fsb.append(value.ToString(\"G\", System.Globalization.CultureInfo.InvariantCulture));")
    public static UnicodeBuilder decimalToUnicodeString(BigDecimal value, UnicodeBuilder fsb) {
        // Can't use BigDecimal#toString() under JDK 1.5 because this produces values like "1E-5".
        // Can't use BigDecimal#toPlainString() because it retains trailing zeroes to represent the scale
        int scale = value.scale();
        if (scale == 0) {
            fsb.append(value.toString());
            return fsb;
        } else if (scale < 0) {
            String s = value.abs().unscaledValue().toString();
            if (s.equals("0")) {
                fsb.append('0');
                return fsb;
            }
            //StringBuilder sb = new StringBuilder(s.length() + (-scale) + 2);
            if (value.signum() < 0) {
                fsb.append('-');
            }
            fsb.append(s);
            for (int i = 0; i < -scale; i++) {
                fsb.append('0');
            }
            return fsb;
        } else {
            String s = value.abs().unscaledValue().toString();
            if (s.equals("0")) {
                fsb.append('0');
                return fsb;
            }
            int len = s.length();
            //StringBuilder sb = new StringBuilder(len+1);
            if (value.signum() < 0) {
                fsb.append('-');
            }
            if (scale >= len) {
                fsb.append("0.");
                for (int i = len; i < scale; i++) {
                    fsb.append('0');
                }
                fsb.append(s);
            } else {
                fsb.append(s.substring(0, len - scale));
                fsb.append('.');
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
    @CSharpReplaceBody(code="return new BigDecimalValue(Singulink.Numerics.BigDecimal.Floor(value));")
    public NumericValue floor() {
        return new BigDecimalValue(value.setScale(0, RoundingMode.FLOOR));
    }

    /**
     * Implement the XPath ceiling() function
     */

    @Override
    @CSharpReplaceBody(code = "return new BigDecimalValue(Singulink.Numerics.BigDecimal.Ceiling(value));")
    public NumericValue ceiling() {
        return new BigDecimalValue(value.setScale(0, RoundingMode.CEILING));
    }

    /**
     * Implement the XPath round() function
     */

    @Override
    @CSharpReplaceBody(code = "return new BigDecimalValue(Saxon.Impl.Helpers.BigDecimalUtils.Round(value, scale));")
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

        return switch (value.signum()) {
            case -1 -> new BigDecimalValue(value.setScale(scale, RoundingMode.HALF_DOWN));
            case 0 -> this;
            case +1 -> new BigDecimalValue(value.setScale(scale, RoundingMode.HALF_UP));
            default ->
                // can't happen
                    this;
        };

    }

    /**
     * Implement the XPath round-half-to-even() and round#3 functions
     */

    @Override
    @CSharpReplaceBody(code = "return new BigDecimalValue(Saxon.Impl.Helpers.BigDecimalUtils.Round(value, scale, roundingRule));")
    public NumericValue round(int scale, Round.RoundingRule roundingRule) {
        if (scale >= value.scale()) {
            return this;
        }

        BigDecimal scaledValue;
        switch (roundingRule) {
            case FLOOR:
                scaledValue = value.setScale(scale, java.math.RoundingMode.FLOOR);
                break;
            case CEILING:
                scaledValue = value.setScale(scale, java.math.RoundingMode.CEILING);
                break;
            case AWAY_FROM_ZERO:
                scaledValue = value.setScale(scale, java.math.RoundingMode.UP);
                break;
            case TOWARD_ZERO:
                scaledValue = value.setScale(scale, java.math.RoundingMode.DOWN);
                break;
            case HALF_TO_FLOOR:
                if (signum() >= 0) {
                    scaledValue = value.setScale(scale, java.math.RoundingMode.HALF_DOWN);
                } else {
                    scaledValue = value.setScale(scale, java.math.RoundingMode.HALF_UP);
                }
                break;
            case HALF_TO_CEILING:
            default:
                if (signum() >= 0) {
                    scaledValue = value.setScale(scale, java.math.RoundingMode.HALF_UP);
                } else {
                    scaledValue = value.setScale(scale, java.math.RoundingMode.HALF_DOWN);
                }
                break;
            case HALF_TOWARD_ZERO:
                scaledValue = value.setScale(scale, java.math.RoundingMode.HALF_DOWN);
                break;
            case HALF_AWAY_FROM_ZERO:
                scaledValue = value.setScale(scale, java.math.RoundingMode.HALF_UP);
                break;
            case HALF_TO_EVEN:
                scaledValue = value.setScale(scale, RoundingMode.HALF_EVEN);
                break;
        }
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
    @CSharpReplaceBody(code = "return value.DecimalPlaces == 0;")
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
    public int compareTo(XPathComparable other) {
        if (other instanceof NumericValue) {
            if (NumericValue.isInteger(((NumericValue)other))) {
                // deliberately triggers a ClassCastException if other value is the wrong type
                try {
                    return value.compareTo(((NumericValue)other).getDecimalValue());
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
        } else {
            throw new ClassCastException("Cannot compare xs:decimal to " + other.toString());
        }
    }

    @Override
    public int transitiveCompareTo(NumericValue other) {
        if (other instanceof DecimalValue) {
            try {
                return value.compareTo(other.getDecimalValue());
            } catch (ValidationException e) {
                throw new UncheckedXPathException(e);
            }
        } else {
            double dbl = other.getDoubleValue();
            if (Double.isInfinite(dbl)) {
                return Double.compare(0.0e0, dbl);
            }
            BigDecimal exactDecimal = new BigDecimal(dbl);
            return value.compareTo(exactDecimal);
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
        if (isWholeNumber() && value.compareTo(MIN_INT) >= 0 && value.compareTo(MAX_INT) <= 0) {
            try {
                return new net.sf.saxon.ma.map.Int32MapKey((int)longValue());
            } catch (XPathException e) {
                throw new AssertionError(e);
            }
        }
        return new BigDecimalMapKey(value);
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

