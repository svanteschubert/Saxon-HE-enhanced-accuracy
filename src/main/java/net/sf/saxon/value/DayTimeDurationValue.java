////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * A value of type xs:dayTimeDuration (or a subtype thereof).
 * <p>Internally this is held as an BigDecimal number of seconds. Some of the constructor
 * and accessor methods cannot handle the full range of values.</p>
 */

public final class DayTimeDurationValue extends DurationValue
        implements XPathComparable {


    public DayTimeDurationValue(BigDecimal seconds) {
        this(seconds, BuiltInAtomicType.DAY_TIME_DURATION);
    }
    public DayTimeDurationValue(BigDecimal seconds, AtomicType typeLabel) {
        super(0, seconds, typeLabel);
        //assert (typeLabel.getPrimitiveAtomicType() == BuiltInAtomicType.DAY_TIME_DURATION);
    }

    /**
     * Factory method: create a duration value from a supplied string, in
     * ISO 8601 format {@code [-]PnDTnHnMnS}
     *
     * @param s the lexical representation of the xs:dayTimeDuration value
     * @return a {@code DayTimeDurationValue} if the format is correct, or a {@link ValidationFailure} if not
     */

    public static ConversionResult makeDayTimeDurationValue(UnicodeString s) {
        ConversionResult d = DurationValue.makeDuration(s, false, true);
        if (d instanceof ValidationFailure) {
            return d;
        }
        DurationValue dv = (DurationValue) d;
        return Converter.DurationToDayTimeDuration.INSTANCE.convert(dv);
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
        return new DayTimeDurationValue(_seconds, metadata.getType());
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.DAY_TIME_DURATION;
    }

    /**
     * Convert to string
     *
     * @return ISO 8601 representation.
     */

    @Override
    public UnicodeString getPrimitiveStringValue() {

        UnicodeBuilder sb = new UnicodeBuilder(16);
        if (signum() < 0) {
            sb.append('-');
        }

        BigDecimal[] parts = getDaysHoursMinutesAndSeconds();
        BigDecimal days = parts[0].abs().stripTrailingZeros(),
                hours = parts[1].abs().stripTrailingZeros(),
                minutes = parts[2].abs().stripTrailingZeros(),
                seconds = parts[3].abs().stripTrailingZeros();
        sb.append('P');
        if (days.signum() != 0) {
            BigDecimalValue.decimalToUnicodeString(days, sb).append('D');
        }
        if (days.signum() == 0 || hours.signum() != 0 || minutes.signum() != 0 || seconds.signum() != 0) {
            sb.append('T');
        }
        if (hours.signum() != 0) {
            BigDecimalValue.decimalToUnicodeString(hours, sb).append('H');
        }
        if (minutes.signum() != 0) {
            BigDecimalValue.decimalToUnicodeString(minutes, sb).append('M');
        }
        if (parts[3].signum() != 0 || (days.signum() == 0 && minutes.signum() == 0 && hours.signum() == 0)) {
            BigDecimalValue.decimalToUnicodeString(seconds, sb).append('S');
        }
        return sb.toUnicodeString();
    }

    // TODO: this is temporary
    public String toString() {
        return "DayTimeDuration " + _seconds.toString() + "S";
    }

    /**
     * Get the length of duration in seconds. Note this may involve loss of precision. For an exact
     * result, use {@link #getTotalSeconds()}
     * @return an approximation to the length of the duration in seconds, expressed as a double. May be negative.
     */

    public double getLengthInSeconds() {
        return _seconds.doubleValue();
    }

    /**
     * Get the length of duration in microseconds, as a long
     *
     * @return the length in nanoseconds, divided by one thousand, rounded towards zero
     * @throws ArithmeticException if the number of microseconds is too high to be returned as a long.
     */

    public long getLengthInMicroseconds() {
        if (_seconds.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE/1_000_000)) > 0) {
            throw new ArithmeticException("Value is too large to be expressed in microseconds");
        }
        return _seconds.multiply(BigDecimalValue.ONE_MILLION).longValue();
    }

//    /**
//     * Get the length of duration in microseconds, as a long
//     *
//     * @return the length in nanoseconds, divided by one thousand, rounded towards zero
//     * @throws ArithmeticException if the number of nanoseconds is too high to be returned as a long.
//     */
//
//    public long getLengthInNanoseconds() {
//        if (_seconds > Long.MAX_VALUE / 1_000_000_000L) {
//            throw new ArithmeticException("Value is too large to be expressed in nanoseconds");
//        }
//        long a = _seconds * 1_000_000_000L + _nanoseconds;
//        return _negative ? -a : a;
//    }


    /**
     * Construct a duration value as a number of milliseconds.
     *
     * @param milliseconds the number of milliseconds in the duration (may be negative)
     * @return the corresponding xs:dayTimeDuration value
     */

    public static DayTimeDurationValue fromMilliseconds(long milliseconds) {
        BigDecimal seconds = new BigDecimal(BigInteger.valueOf(milliseconds), 3);
        return new DayTimeDurationValue(seconds);
    }

    /**
     * Construct a duration value as a number of nanoseconds.
     *
     * @param nanoseconds the number of nanoseconds in the duration.
     * @return the xs:dayTimeDuration represented by the given number of nanoseconds
     */

    public static DayTimeDurationValue fromNanoseconds(long nanoseconds) {
        BigDecimal seconds = new BigDecimal(BigInteger.valueOf(nanoseconds), 9);
        return new DayTimeDurationValue(seconds);
    }

    /**
     * Factory method taking a Java 8 {@link java.time.Duration} object
     * @param duration a duration as a Java 8 {@code java.time.Duration}
     * @return the new xs:dayTimeDuration
     * @since 9.9
     */

    public static DayTimeDurationValue fromJavaDuration(java.time.Duration duration) {
        long seconds = duration.getSeconds();
        int nanoseconds = duration.getNano();
        return new DayTimeDurationValue(BigDecimal.valueOf(seconds)
                                                .add(BigDecimal.valueOf(nanoseconds)
                                                             .divide(BigDecimalValue.ONE_BILLION, RoundingMode.DOWN)));
    }

    /**
     * Convert this value to a Java 8 {@link java.time.Duration} object
     * @return the duration expressed as a Java 8 {@code java.time.Duration}
     * @since 9.9
     */

    public java.time.Duration toJavaDuration() {
        BigDecimal[] parts = _seconds.divideAndRemainder(BigDecimal.ONE);
        return java.time.Duration.ofSeconds(parts[0].longValue(), parts[1].multiply(BigDecimalValue.ONE_BILLION).longValue());
    }

    /**
     * Multiply a duration by an integer
     *
     * @param factor the number to multiply by
     * @return the result of the multiplication
     */

    @Override
    public DurationValue multiply(long factor) throws XPathException {
        return new DayTimeDurationValue(_seconds.multiply(BigDecimal.valueOf(factor)));
    }


    /**
     * Multiply duration by a number. Follows the semantics of op:multiply-dayTimeDuration.
     * @param n the number to multiply by.
     * @throws XPathException if the operand is Infinite or NaN, or if the resulting duration
     * exceeds Saxon limits (2^63 seconds)
     */

    @Override
    public DayTimeDurationValue multiply(double n) throws XPathException {
        if (Double.isNaN(n)) {
            throw new XPathException("Cannot multiply a duration by NaN", "FOCA0005");
        }
        if (Double.isInfinite(n)) {
            throw new XPathException("Cannot multiply a duration by infinity", "FODT0002");
        }
        BigDecimal factor = BigDecimal.valueOf(n);
        return multiply(factor);
    }

    public DayTimeDurationValue multiply(BigDecimal factor) throws XPathException {
        BigDecimal secs = getTotalSeconds();
        BigDecimal product = secs.multiply(factor);
        try {
            return new DayTimeDurationValue(product, BuiltInAtomicType.DAY_TIME_DURATION);
        } catch (IllegalArgumentException | ArithmeticException err) {
            if (err.getCause() instanceof XPathException) {
                throw (XPathException) err.getCause();
            } else {
                throw new XPathException("Overflow when multiplying a duration by a number", err)
                        .withErrorCode("FODT0002");
            }
        }
    }

    /**
     * Divide duration by a number. Follows the semantics of op:divide-dayTimeDuration.
     *
     * @param n the number to divide by.
     * @throws XPathException if the operand is zero or NaN, or if the resulting duration
     *                        exceeds Saxon limits (2^63 seconds)
     */

    @Override
    public DurationValue divide(double n) throws XPathException {
        if (Double.isNaN(n)) {
            throw new XPathException("Cannot divide a duration by NaN", "FOCA0005");
        }
        if (n == 0) {
            throw new XPathException("Cannot divide a duration by zero", "FODT0002");
        }
        BigDecimal secs = getTotalSeconds();
        BigDecimal product = secs.divide(BigDecimal.valueOf(n));
        try {
            return new DayTimeDurationValue(product, BuiltInAtomicType.DAY_TIME_DURATION);
        } catch (IllegalArgumentException | ArithmeticException err) {
            if (err.getCause() instanceof XPathException) {
                throw (XPathException) err.getCause();
            } else {
                throw new XPathException("Overflow when dividing a duration by a number", err)
                        .withErrorCode("FODT0002");
            }
        }
    }

    /**
     * Find the ratio between two durations
     *
     * @param other the dividend
     * @return the ratio, as a decimal
     * @throws XPathException when dividing by zero, or when dividing two durations of different type
     */
    @Override
    public BigDecimalValue divide(DurationValue other) throws XPathException {
        if (other instanceof DayTimeDurationValue) {
            BigDecimal v1 = getTotalSeconds();
            BigDecimal v2 = other.getTotalSeconds();
            if (v2.signum() == 0) {
                throw new XPathException("Divide by zero (durations)", "FOAR0001");
            }
            return new BigDecimalValue(v1.divide(v2, 20, RoundingMode.HALF_EVEN));
        } else {
            throw new XPathException("Cannot divide two durations of different type", "XPTY0004");
        }
    }

    /**
     * Add two dayTimeDurations
     */

    @Override
    public DurationValue add(DurationValue other) throws XPathException {
        if (other instanceof DayTimeDurationValue) {
            DayTimeDurationValue d2 = (DayTimeDurationValue)other;
            return new DayTimeDurationValue(_seconds.add(d2._seconds));
        } else {
            throw new XPathException("Cannot add two durations of different type", "XPTY0004");
        }
    }

    /**
     * Subtract two dayTime-durations
     */

    @Override
    public DurationValue subtract(DurationValue other) throws XPathException {
        if (other instanceof DayTimeDurationValue) {
            DayTimeDurationValue d2 = (DayTimeDurationValue) other;
            return new DayTimeDurationValue(_seconds.subtract(d2._seconds));
        } else {
            throw new XPathException("Cannot subtract two durations of different type", "XPTY0004").asTypeError();
        }
    }

    /**
     * Negate a duration (same as subtracting from zero, but it preserves the type of the original duration)
     */

    @Override
    public DurationValue negate() throws IllegalArgumentException {
        return new DayTimeDurationValue(_seconds.negate());
    }

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone, int specVersion) {
        return specVersion >= 40
                ? new DurationValue(_months, _seconds,BuiltInAtomicType.DURATION)
                : this;
    }

//    /**
//     * Compare the value to another duration value
//     *
//     * @param other The other dateTime value
//     * @return negative value if this one is the smaller, 0 if they are equal,
//     *         positive value if this one is the greater.
//     * @throws ClassCastException if the other value is not a DayTimeDurationValue
//     */
//
//    @Override
//    public int compareTo(XPathComparable other) {
//        if (other instanceof DayTimeDurationValue) {
//            Objects.requireNonNull(other);
//            DayTimeDurationValue dtd = (DayTimeDurationValue)other;
//            return _seconds.compareTo(dtd._seconds);
//        } else {
//            throw new ClassCastException("Cannot compare xs:dayTimeDuration to " + other);
//        }
//    }

    /**
     * Get a Comparable value that implements the XPath ordering comparison semantics for this value.
     * Returns null if the value is not comparable according to XPath rules. The default implementation
     * returns the value itself. This is modified for types such as
     * xs:duration which allow ordering comparisons in XML Schema, but not in XPath.
     *
     * @param collator         Collation used for string comparison
     * @param implicitTimezone XPath dynamic context
     * @param specVersion
     */

    @Override
    public AtomicMatchKey getXPathMatchKey(StringCollator collator, int implicitTimezone, int specVersion) {
        return this;
    }


}

