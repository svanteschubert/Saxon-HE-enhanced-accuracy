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
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A value of type xs:dayTimeDuration.
 * <p>Internally this is held as an integer number of seconds held in a positive long, a positive integer
 * number of microseconds in the range 0 to 999,999,999, and a boolean sign. Some of the constructor
 * and accessor methods cannot handle the full range of values.</p>
 */

public final class DayTimeDurationValue extends DurationValue
        implements Comparable<DayTimeDurationValue> {

    /**
     * Private constructor for internal use
     */

    private DayTimeDurationValue() {
        typeLabel = BuiltInAtomicType.DAY_TIME_DURATION;
    }

    /**
     * Factory method: create a duration value from a supplied string, in
     * ISO 8601 format {@code [-]PnDTnHnMnS}
     *
     * @param s the lexical representation of the xs:dayTimeDuration value
     * @return a {@code DayTimeDurationValue} if the format is correct, or a {@link ValidationFailure} if not
     */

    public static ConversionResult makeDayTimeDurationValue(CharSequence s) {
        ConversionResult d = DurationValue.makeDuration(s, false, true);
        if (d instanceof ValidationFailure) {
            return d;
        }
        DurationValue dv = (DurationValue) d;
        return Converter.DurationToDayTimeDuration.INSTANCE.convert(dv);
    }

    /**
     * Create a dayTimeDuration given the number of days, hours, minutes, and seconds. This
     * constructor performs limited validation. The components (apart from sign) must all be non-negative
     * integers; they need not be normalized (for example, 36 hours is acceptable)
     * <p>Note: for historic reasons this constructor only supports microsecond precision. For nanosecond
     * precision, use the constructor {@link #DayTimeDurationValue(int, int, int, long, int)}</p>
     *
     * @param sign         positive number for positive durations, negative for negative duratoins
     * @param days         number of days
     * @param hours        number of hours
     * @param minutes      number of minutes
     * @param seconds      number of seconds
     * @param microseconds number of microseconds
     * @throws IllegalArgumentException if the value is out of range; specifically, if the total
     *                                  number of seconds exceeds 2^63; or if any of the values is negative
     */

    public DayTimeDurationValue(int sign, int days, int hours, int minutes, long seconds, int microseconds)
            throws IllegalArgumentException {
        if (days < 0 || hours < 0 || minutes < 0 || seconds < 0 || microseconds < 0) {
            throw new IllegalArgumentException("Negative component value");
        }
        if ((double) days * (24 * 60 * 60) + (double) hours * (60 * 60) +
                (double) minutes * 60 + (double) seconds > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Duration seconds limit exceeded");
        }
        negative = sign < 0;
        months = 0;
        long h = (long) days * 24L + (long) hours;
        long m = h * 60L + (long) minutes;
        long s = m * 60L + seconds;
        if (microseconds > 1000000) {
            s += microseconds / 1000000;
            microseconds %= 1000000;
        }
        this.seconds = s;
        this.nanoseconds = microseconds*1000;
        if (s == 0 && microseconds == 0) {
            negative = false;
        }
        typeLabel = BuiltInAtomicType.DAY_TIME_DURATION;
    }

    /**
     * Create a dayTimeDuration given the number of days, hours, minutes, seconds, and nanoseconds. This
     * constructor performs limited validation. The components need not be normalized (for example,
     * 36 hours is acceptable)
     * <p>To construct a positive duration, all the component values should be positive integers (or zero).
     * To construct a negative duration, all the component values should be negative integers (or zero).</p>
     *
     * @param days         number of days
     * @param hours        number of hours
     * @param minutes      number of minutes
     * @param seconds      number of seconds
     * @param nanoseconds  number of nanoseconds
     * @throws IllegalArgumentException if the value is out of range; specifically, if the total
     *                                  number of seconds exceeds 2^63; or if some values are positive and
     *                                  others are negative
     */

    public DayTimeDurationValue(int days, int hours, int minutes, long seconds, int nanoseconds)
            throws IllegalArgumentException {
        boolean somePositive = days > 0 || hours > 0 || minutes > 0 || seconds > 0 || nanoseconds > 0;
        boolean someNegative = days < 0 || hours < 0 || minutes < 0 || seconds < 0 || nanoseconds < 0;
        if (somePositive && someNegative) {
            throw new IllegalArgumentException("Some component values are positive and others are negative");
        }
        if (someNegative) {
            negative = true;
            days = -days;
            hours = -hours;
            minutes = -minutes;
            seconds = -seconds;
            nanoseconds = -nanoseconds;
        }
        if ((double) days * (24 * 60 * 60) + (double) hours * (60 * 60) +
                (double) minutes * 60 + (double) seconds > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Duration seconds limit exceeded");
        }
        months = 0;
        long h = (long) days * 24L + (long) hours;
        long m = h * 60L + (long) minutes;
        long s = m * 60L + seconds;
        if (nanoseconds > 1_000_000_000) {
            s += nanoseconds / 1_000_000_000;
            nanoseconds %= 1_000_000_000;
        }
        this.seconds = s;
        this.nanoseconds = nanoseconds;
        typeLabel = BuiltInAtomicType.DAY_TIME_DURATION;
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param typeLabel the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    /*@NotNull*/
    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        DayTimeDurationValue v = DayTimeDurationValue.fromSeconds(getTotalSeconds());
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
        return BuiltInAtomicType.DAY_TIME_DURATION;
    }

    /**
     * Convert to string
     *
     * @return ISO 8601 representation.
     */

    @Override
    public CharSequence getPrimitiveStringValue() {

        FastStringBuffer sb = new FastStringBuffer(32);
        if (negative) {
            sb.cat('-');
        }

        int days = getDays();
        int hours = getHours();
        int minutes = getMinutes();
        int seconds = getSeconds();

        sb.cat('P');
        if (days != 0) {
            sb.append(days + "D");
        }
        if (days == 0 || hours != 0 || minutes != 0 || seconds != 0 || nanoseconds != 0) {
            sb.cat('T');
        }
        if (hours != 0) {
            sb.append(hours + "H");
        }
        if (minutes != 0) {
            sb.append(minutes + "M");
        }
        if (seconds != 0 || nanoseconds != 0 || (days == 0 && minutes == 0 && hours == 0)) {
            if (nanoseconds == 0) {
                sb.append(seconds + "S");
            } else {
                formatFractionalSeconds(sb, seconds, (seconds * 1_000_000_000L) + nanoseconds);
            }
        }
        return sb;
    }

    /**
     * Get the length of duration in seconds. Note this may involve loss of precision. For an exact
     * result, use {@link #getTotalSeconds()}
     * @return an approximation to the length of the duration in seconds, expressed as a double.
     */

    @Override
    public double getLengthInSeconds() {
        double a = seconds + ((double) nanoseconds / 1_000_000_000);
        // System.err.println("Duration length " + days + "/" + hours + "/" + minutes + "/" + seconds + " is " + a);
        return negative ? -a : a;
    }

    /**
     * Get the length of duration in microseconds, as a long
     *
     * @return the length in nanoseconds, divided by one thousand, rounded towards zero
     * @throws ArithmeticException if the number of microseconds is too high to be returned as a long.
     */

    public long getLengthInMicroseconds() {
        if (seconds > Long.MAX_VALUE/1_000_000L) {
            throw new ArithmeticException("Value is too large to be expressed in microseconds");
        }
        long a = seconds * 1_000_000L + (nanoseconds / 1000);
        return negative ? -a : a;
    }

    /**
     * Get the length of duration in microseconds, as a long
     *
     * @return the length in nanoseconds, divided by one thousand, rounded towards zero
     * @throws ArithmeticException if the number of nanoseconds is too high to be returned as a long.
     */

    public long getLengthInNanoseconds() {
        if (seconds > Long.MAX_VALUE / 1_000_000_000L) {
            throw new ArithmeticException("Value is too large to be expressed in nanoseconds");
        }
        long a = seconds * 1_000_000_000L + nanoseconds;
        return negative ? -a : a;
    }


    /**
     * Construct a duration value as a number of seconds.
     *
     * @param seconds the number of seconds in the duration. May be negative
     * @return the xs:dayTimeDuration value with the specified length
     * @throws ArithmeticException if the number of (whole) seconds exceeds 2^63
     */

    public static DayTimeDurationValue fromSeconds(BigDecimal seconds) {
        DayTimeDurationValue sdv = new DayTimeDurationValue();
        sdv.negative = seconds.signum() < 0;
        if (sdv.negative) {
            seconds = seconds.negate();
        }
        BigInteger wholeSeconds = seconds.toBigInteger();
        sdv.seconds = wholeSeconds.longValueExact(); // ArithmeticException if out of range
        BigDecimal fractionalPart = seconds.remainder(BigDecimal.ONE);
        BigDecimal nanoseconds = fractionalPart.multiply(BigDecimalValue.BIG_DECIMAL_ONE_BILLION);
        sdv.nanoseconds = nanoseconds.intValue();
        if (sdv.seconds == 0 && sdv.nanoseconds == 0) {
            // can happen with underflow (division by a very large number)
            sdv.negative = false;
        }
        return sdv;
    }



    /**
     * Construct a duration value as a number of milliseconds.
     *
     * @param milliseconds the number of milliseconds in the duration (may be negative)
     * @return the corresponding xs:dayTimeDuration value
     * @throws ValidationException if implementation-defined limits are exceeded, specifically
     *                             if the total number of seconds exceeds 2^63.
     */

    public static DayTimeDurationValue fromMilliseconds(long milliseconds) throws ValidationException {
        int sign = Long.signum(milliseconds);
        if (sign < 0) {
            milliseconds = -milliseconds;
        }
        try {
            return new DayTimeDurationValue(
                    sign, 0, 0, 0, milliseconds / 1000, (int) (milliseconds % 1000) * 1000);
        } catch (IllegalArgumentException err) {
            // limits exceeded
            throw new ValidationFailure("Duration exceeds limits").makeException();
        }
    }

    /**
     * Construct a duration value as a number of microseconds.
     *
     * @param microseconds the number of microseconds in the duration.
     * @return the xs:dayTimeDuration represented by the given number of microseconds
     */

    public static DayTimeDurationValue fromMicroseconds(long microseconds) throws IllegalArgumentException {
        int sign = Long.signum(microseconds);
        if (sign < 0) {
            microseconds = -microseconds;
        }
        return new DayTimeDurationValue(
                sign, 0, 0, 0, microseconds / 1_000_000, (int) (microseconds % 1_000_000));

    }

    /**
     * Construct a duration value as a number of nanoseconds.
     *
     * @param nanoseconds the number of nanoseconds in the duration.
     * @return the xs:dayTimeDuration represented by the given number of nanoseconds
     */

    public static DayTimeDurationValue fromNanoseconds(long nanoseconds) throws IllegalArgumentException {
        return new DayTimeDurationValue(
                    0, 0, 0, nanoseconds / 1_000_000_000L, (int) (nanoseconds % 1_000_000_000L));
    }

    /**
     * Factory method taking a Java 8 {@link java.time.Duration} object
     * @param duration a duration as a Java 8 {@code java.time.Duration}
     * @since 9.9
     */

    public static DayTimeDurationValue fromJavaDuration(java.time.Duration duration) {
        long seconds = duration.getSeconds();
        int nanoseconds = duration.getNano();
        boolean negative = false;
        if (seconds < 0) {
            return new DayTimeDurationValue(0, 0, 0, seconds, -1_000_000_000 + nanoseconds);
        } else {
            return new DayTimeDurationValue(0, 0, 0, seconds, nanoseconds);
        }
    }

    /**
     * Convert this value to a Java 8 {@link java.time.Duration} object
     * @return the duration expressed as a Java 8 {@code java.time.Duration}
     * @since 9.9
     */

    public java.time.Duration toJavaDuration() {
        if (negative) {
            return java.time.Duration.ofSeconds(-seconds, -nanoseconds);
        } else {
            return java.time.Duration.ofSeconds(seconds, nanoseconds);
        }
    }


    /**
     * Multiply a duration by an integer
     *
     * @param factor the number to multiply by
     * @return the result of the multiplication
     */

    @Override
    public DurationValue multiply(long factor) throws XPathException {
        // Fast path for simple cases
        if (Math.abs(factor) < 0x7fff_ffff && Math.abs(seconds) < 0x7fff_ffff) {
            return new DayTimeDurationValue(0, 0, 0,
                                            seconds * factor * (negative ? -1 : 1),
                                            (int)(nanoseconds * factor * (negative ? -1 : 1)));
        } else {
            return multiply(BigDecimal.valueOf(factor));
        }
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
            XPathException err = new XPathException("Cannot multiply a duration by NaN");
            err.setErrorCode("FOCA0005");
            throw err;
        }
        if (Double.isInfinite(n)) {
            XPathException err = new XPathException("Cannot multiply a duration by infinity");
            err.setErrorCode("FODT0002");
            throw err;
        }
        BigDecimal factor = BigDecimal.valueOf(n);
        return multiply(factor);
    }

    private DayTimeDurationValue multiply(BigDecimal factor) throws XPathException {
        BigDecimal secs = getTotalSeconds();
        BigDecimal product = secs.multiply(factor);
        try {
            return fromSeconds(product);
        } catch (IllegalArgumentException | ArithmeticException err) {
            if (err.getCause() instanceof XPathException) {
                throw (XPathException) err.getCause();
            } else {
                XPathException err2 = new XPathException("Overflow when multiplying a duration by a number", err);
                err2.setErrorCode("FODT0002");
                throw err2;
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
            XPathException err = new XPathException("Cannot divide a duration by NaN");
            err.setErrorCode("FOCA0005");
            throw err;
        }
        if (n == 0) {
            XPathException err = new XPathException("Cannot divide a duration by zero");
            err.setErrorCode("FODT0002");
            throw err;
        }
        BigDecimal secs = getTotalSeconds();
        BigDecimal product = secs.divide(BigDecimal.valueOf(n));
        try {
            return fromSeconds(product);
        } catch (IllegalArgumentException | ArithmeticException err) {
            if (err.getCause() instanceof XPathException) {
                throw (XPathException) err.getCause();
            } else {
                XPathException err2 = new XPathException("Overflow when dividing a duration by a number", err);
                err2.setErrorCode("FODT0002");
                throw err2;
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
                XPathException err = new XPathException("Divide by zero (durations)");
                err.setErrorCode("FOAR0001");
                throw err;
            }
            return new BigDecimalValue(v1.divide(v2, 20, RoundingMode.HALF_EVEN));
        } else {
            XPathException err = new XPathException("Cannot divide two durations of different type");
            err.setErrorCode("XPTY0004");
            throw err;
        }
    }

    /**
     * Add two dayTimeDurations
     */

    @Override
    public DurationValue add(DurationValue other) throws XPathException {
        if (other instanceof DayTimeDurationValue) {
            DayTimeDurationValue d2 = (DayTimeDurationValue)other;
            if (((seconds | d2.seconds) & 0xffff_ffff_0000_0000L) != 0) {
                // risk of complications, use BigDecimal arithmetic
                try {
                    BigDecimal v1 = getTotalSeconds();
                    BigDecimal v2 = other.getTotalSeconds();
                    return fromSeconds(v1.add(v2));
                } catch (IllegalArgumentException e) {
                    XPathException err = new XPathException("Overflow when adding two durations");
                    err.setErrorCode("FODT0002");
                    throw err;
                }
            } else {
                // fast path for common case: no risk of overflow
                return DayTimeDurationValue.fromNanoseconds(getLengthInNanoseconds() + d2.getLengthInNanoseconds());
            }
        } else {
            XPathException err = new XPathException("Cannot add two durations of different type");
            err.setErrorCode("XPTY0004");
            throw err;
        }
    }

    /**
     * Subtract two dayTime-durations
     */

    @Override
    public DurationValue subtract(DurationValue other) throws XPathException {
        if (other instanceof DayTimeDurationValue) {
            DayTimeDurationValue d2 = (DayTimeDurationValue) other;
            if (((seconds | d2.seconds) & 0xffff_ffff_0000_0000L) != 0) {
                // risk of complications, use BigDecimal arithmetic
                try {
                    BigDecimal v1 = getTotalSeconds();
                    BigDecimal v2 = other.getTotalSeconds();
                    return fromSeconds(v1.subtract(v2));
                } catch (IllegalArgumentException e) {
                    XPathException err = new XPathException("Overflow when subtracting two durations");
                    err.setErrorCode("FODT0002");
                    throw err;
                }
            } else {
                // fast path for common case: no risk of overflow
                return DayTimeDurationValue.fromNanoseconds(getLengthInNanoseconds() - d2.getLengthInNanoseconds());
            }
        } else {
            XPathException err = new XPathException("Cannot subtract two durations of different type");
            err.setErrorCode("XPTY0004");
            throw err;
        }
    }

    /**
     * Negate a duration (same as subtracting from zero, but it preserves the type of the original duration)
     *
     * @throws IllegalArgumentException in the extremely unlikely event that the duration is one that cannot
     *                                  be negated (because the limit for positive durations is one second
     *                                  off from the limit for negative durations)
     */

    @Override
    public DurationValue negate() throws IllegalArgumentException {
        DayTimeDurationValue d2 = new DayTimeDurationValue();
        d2.setTypeLabel(typeLabel);
        d2.seconds = seconds;
        d2.nanoseconds = nanoseconds;
        d2.negative = !negative;
        return d2;
    }

    /**
     * Compare the value to another duration value
     *
     * @param other The other dateTime value
     * @return negative value if this one is the smaller, 0 if they are equal,
     *         positive value if this one is the greater.
     * @throws NullPointerException if the other value is null
     */

    @Override
    public int compareTo(DayTimeDurationValue other) {
        Objects.requireNonNull(other);
        if (this.negative != other.negative) {
            return this.negative ? -1 : +1;
        } else if (this.seconds != other.seconds) {
            return Long.compare(this.seconds, other.seconds) * (this.negative ? -1 : +1);
        } else {
            return Integer.compare(this.nanoseconds, other.nanoseconds) * (this.negative ? -1 : +1);
        }
    }

    /**
     * Get a Comparable value that implements the XPath ordering comparison semantics for this value.
     * Returns null if the value is not comparable according to XPath rules. The default implementation
     * returns the value itself. This is modified for types such as
     * xs:duration which allow ordering comparisons in XML Schema, but not in XPath.
     *
     * @param ordered  true if an ordered comparable is needed
     * @param collator Collation used for string comparison
     * @param implicitTimezone  XPath dynamic context
     */

    @Override
    public AtomicMatchKey getXPathComparable(boolean ordered, StringCollator collator, int implicitTimezone) {
        return this;
    }


}

