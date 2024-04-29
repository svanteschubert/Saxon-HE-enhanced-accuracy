////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.str.BMPString;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.ValidationFailure;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.StringTokenizer;

/**
 * A value of type xs:duration
 */

public class DurationValue extends AtomicValue implements AtomicMatchKey {

    protected final boolean _negative;
    protected final int _months;
    protected final long _seconds;
    protected final int _nanoseconds;


    /**
     * Constructor for xs:duration taking the components of the duration. There is no requirement
     * that the values are normalized, for example it is acceptable to specify months=18. The values of
     * the individual components must all be non-negative.
     * <p>Note: For historic reasons this constructor only supports microsecond precision. To get nanosecond
     * precision, use the constructor {@link DurationValue#DurationValue(int, int, int, int, int, long, int, AtomicType)}.</p>
     *
     * @param positive     true if the duration is positive, false if negative. For a negative duration
     *                     the components are all supplied as positive integers (or zero).
     * @param years        the number of years
     * @param months       the number of months
     * @param days         the number of days
     * @param hours        the number of hours
     * @param minutes      the number of minutes
     * @param seconds      the number of seconds
     * @param microseconds the number of microseconds
     * @throws IllegalArgumentException if the size of the duration exceeds implementation-defined
     *                                  limits: specifically, if the total number of months exceeds 2^31, or if the total number
     *                                  of seconds exceeds 2^63.
     */

    public DurationValue(boolean positive, int years, int months, int days,
                         int hours, int minutes, long seconds, int microseconds)
            throws IllegalArgumentException {
        this(positive, years, months, days, hours, minutes, seconds, microseconds, BuiltInAtomicType.DURATION);
    }

    /**
     * Constructor for xs:duration taking the components of the duration, plus a user-specified
     * type which must be a subtype of xs:duration. There is no requirement
     * that the values are normalized, for example it is acceptable to specify months=18. The values of
     * the individual components must all be non-negative.
     * <p>Note: for historic reasons this constructor was written to expect microseconds rather than nanoseconds.
     * To supply nanoseconds, use the alternative constructor
     * {@link DurationValue#DurationValue(int, int, int, int, int, long, int, AtomicType)}.</p>
     *
     * @param positive     true if the duration is positive, false if negative. For a negative duration
     *                     the components are all supplied as positive integers (or zero).
     * @param years        the number of years
     * @param months       the number of months
     * @param days         the number of days
     * @param hours        the number of hours
     * @param minutes      the number of minutes
     * @param seconds      the number of seconds (long to allow copying)
     * @param microseconds the number of microseconds
     * @param typeLabel    the user-defined subtype of xs:duration. Note that this constructor cannot
     *                     be used to create an instance of xs:dayTimeDuration or xs:yearMonthDuration.
     * @throws IllegalArgumentException if the size of the duration exceeds implementation-defined
     *                                  limits: specifically, if the total number of months exceeds 2^31, or if the total number
     *                                  of seconds exceeds 2^63.
     */

    public DurationValue(boolean positive, int years, int months, int days,
                         int hours, int minutes, long seconds, int microseconds, AtomicType typeLabel) {
        super(typeLabel);
        if (years < 0 || months < 0 || days < 0 || hours < 0 || minutes < 0 || seconds < 0 || microseconds < 0) {
            throw new IllegalArgumentException("Negative component value");
        }
        if ((double) years * 12 + (double) months > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Duration months limit exceeded");
        }
        if ((double) days * (24 * 60 * 60) + (double) hours * (60 * 60) +
                (double) minutes * 60 + (double) seconds > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Duration seconds limit exceeded");
        }
        this._months = years * 12 + months;
        long h = days * 24L + hours;
        long m = h * 60L + minutes;
        this._seconds = m * 60L + seconds;
        this._nanoseconds = microseconds * 1000;
        this._negative = isNegativeDuration(!positive);
    }

    /**
     * Constructor for xs:duration taking the components of the duration, plus a user-specified
     * type which must be a subtype of xs:duration. There is no requirement
     * that the values are normalized, for example it is acceptable to specify months=18. The values of
     * the individual components must all be non-negative.
     * <p>If the duration is positive, all the components must be supplied as positive (or zero) integers.
     * If the duration is negative, all the components must be supplied as negative (or zero) integers.</p>
     *
     * @param years        the number of years
     * @param months       the number of months
     * @param days         the number of days
     * @param hours        the number of hours
     * @param minutes      the number of minutes
     * @param seconds      the number of seconds (long to allow copying)
     * @param nanoseconds  the number of nanoseconds
     * @param typeLabel    the user-defined subtype of xs:duration. Note that this constructor cannot
     *                     be used to create an instance of xs:dayTimeDuration or xs:yearMonthDuration.
     * @throws IllegalArgumentException if the size of the duration exceeds implementation-defined
     *                                  limits: specifically, if the total number of months exceeds 2^31, or if the total number
     *                                  of seconds exceeds 2^63.
     */

    public DurationValue(int years, int months, int days,
                         int hours, int minutes, long seconds, int nanoseconds, AtomicType typeLabel) {
        super(typeLabel);
        boolean somePositive = years > 0 || months > 0 || days > 0 || hours > 0 || minutes > 0 || seconds > 0 || nanoseconds > 0;
        boolean someNegative = years < 0 || months < 0 || days < 0 || hours < 0 || minutes < 0 || seconds < 0 || nanoseconds < 0;
        if (somePositive && someNegative) {
            throw new IllegalArgumentException("Some component values are positive and some negative");
        }
        if (someNegative) {
            years = -years;
            months = -months;
            days = -days;
            hours = -hours;
            minutes = -minutes;
            seconds = -seconds;
            nanoseconds = -nanoseconds;
        }
        if ((double) years * 12 + (double) months > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Duration months limit exceeded");
        }
        if ((double) days * (24 * 60 * 60) + (double) hours * (60 * 60) +
                (double) minutes * 60 + (double) seconds > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Duration seconds limit exceeded");
        }
        this._months = years * 12 + months;
        long h = days * 24L + hours;
        long m = h * 60L + minutes;
        this._seconds = m * 60L + seconds;
        this._nanoseconds = nanoseconds;
        this._negative = someNegative;
    }

    protected static void formatFractionalSeconds(UnicodeBuilder sb, int seconds, long nanosecs) {
        String mss = nanosecs + "";
        if (seconds == 0) {
            mss = "0000000000" + mss;
            mss = mss.substring(mss.length() - 10);
        }
        sb.append(mss.substring(0, mss.length() - 9));
        sb.append('.');
        int lastSigDigit = mss.length() - 1;
        while (mss.charAt(lastSigDigit) == '0') {
            lastSigDigit--;
        }
        sb.append(mss.substring(mss.length() - 9, lastSigDigit + 1));
        sb.append('S');
    }


    /**
     * Ensure that a zero duration is considered positive
     */

    protected boolean isNegativeDuration(boolean nonPositive) {
        if (_months == 0 && _seconds == 0L && _nanoseconds == 0) {
            return false;
        } else {
            return nonPositive;
        }
    }

    /**
     * Static factory method: create a duration value from a supplied string, in
     * ISO 8601 format [-]PnYnMnDTnHnMnS
     *
     * @param s a string in the lexical space of xs:duration
     * @return the constructed xs:duration value, or a {@link ValidationFailure} if the
     *         supplied string is lexically invalid.
     */

    /*@NotNull*/
    public static ConversionResult makeDuration(UnicodeString s) {
        return makeDuration(s, true, true);
    }

    /*@NotNull*/
    protected static ConversionResult makeDuration(UnicodeString s, boolean allowYM, boolean allowDT) {
        int years = 0, months = 0, days = 0, hours = 0, minutes = 0, seconds = 0, nanoseconds = 0;
        boolean negative = false;
        StringTokenizer tok = new StringTokenizer(Whitespace.trim(s).toString(), "-+.PYMDTHS", true);
        int components = 0;
        if (!tok.hasMoreTokens()) {
            return badDuration("empty string", s);
        }
        String part = tok.nextToken();
        if ("+".equals(part)) {
            return badDuration("+ sign not allowed in a duration", s);
        } else if ("-".equals(part)) {
            negative = true;
            if (tok.hasMoreTokens()) {
                part = tok.nextToken();
            } else {
                return badDuration("'-' on its own is not a valid duration", s);
            }
        }
        if (!"P".equals(part)) {
            return badDuration("missing 'P'", s);
        }
        int state = 0;
        while (tok.hasMoreTokens()) {
            part = tok.nextToken();
            if ("T".equals(part)) {
                state = 4;
                if (!tok.hasMoreTokens()) {
                    return badDuration("T must be followed by time components", s);
                }
                part = tok.nextToken();
            }
            int value = simpleInteger(part);
            if (value < 0) {
                if (value == -2) {
                    return badDuration("component of duration exceeds Saxon limits", s, "FODT0002");
                } else {
                    return badDuration("invalid or non-numeric component", s);
                }
            }
            if (!tok.hasMoreTokens()) {
                return badDuration("missing unit letter at end", s);
            }
            char delim = tok.nextToken().charAt(0);
            switch (delim) {
                case 'Y':
                    if (state > 0) {
                        return badDuration("Y is out of sequence", s);
                    }
                    if (!allowYM) {
                        return badDuration("Year component is not allowed in dayTimeDuration", s);
                    }
                    years = value;
                    state = 1;
                    components++;
                    break;
                case 'M':
                    if (state == 4 || state == 5) {
                        if (!allowDT) {
                            return badDuration("Minute component is not allowed in yearMonthDuration", s);
                        }
                        minutes = value;
                        state = 6;
                        components++;
                        break;
                    } else if (state == 0 || state == 1) {
                        if (!allowYM) {
                            return badDuration("Month component is not allowed in dayTimeDuration", s);
                        }
                        months = value;
                        state = 2;
                        components++;
                        break;
                    } else {
                        return badDuration("M is out of sequence", s);
                    }
                case 'D':
                    if (state > 2) {
                        return badDuration("D is out of sequence", s);
                    }
                    if (!allowDT) {
                        return badDuration("Day component is not allowed in yearMonthDuration", s);
                    }
                    days = value;
                    state = 3;
                    components++;
                    break;
                case 'H':
                    if (state != 4) {
                        return badDuration("H is out of sequence", s);
                    }
                    if (!allowDT) {
                        return badDuration("Hour component is not allowed in yearMonthDuration", s);
                    }
                    hours = value;
                    state = 5;
                    components++;
                    break;
                case '.':
                    if (state < 4 || state > 6) {
                        return badDuration("misplaced decimal point", s);
                    }
                    seconds = value;
                    state = 7;
                    break;
                case 'S':
                    if (state < 4 || state > 7) {
                        return badDuration("S is out of sequence", s);
                    }
                    if (!allowDT) {
                        return badDuration("Seconds component is not allowed in yearMonthDuration", s);
                    }
                    if (state == 7) {
                        StringBuilder frac = new StringBuilder(part);
                        while (frac.length() < 9) {
                            frac.append("0");
                        }
                        part = frac.toString();
                        if (part.length() > 9) {
                            part = part.substring(0, 9);
                        }
                        value = simpleInteger(part);
                        if (value < 0) {
                            return badDuration("non-numeric fractional seconds", s);
                        }
                        nanoseconds = value;
                    } else {
                        seconds = value;
                    }
                    state = 8;
                    components++;
                    break;
                default:
                    return badDuration("misplaced " + delim, s);
            }
        }

        if (components == 0) {
            return badDuration("Duration specifies no components", s);
        }

        if (negative) {
            years = -years;
            months = -months;
            days = -days;
            hours = -hours;
            minutes = -minutes;
            seconds = -seconds;
            nanoseconds = -nanoseconds;
        }

        try {
            return new DurationValue(
                    years, months, days, hours, minutes, seconds, nanoseconds, BuiltInAtomicType.DURATION);
        } catch (IllegalArgumentException err) {
            // catch values that exceed limits
            return new ValidationFailure(err.getMessage());
        }
    }

    protected static ValidationFailure badDuration(String msg, UnicodeString s) {
        ValidationFailure err = new ValidationFailure("Invalid duration value '" + s + "' (" + msg + ')');
        err.setErrorCode("FORG0001");
        return err;
    }

    protected static ValidationFailure badDuration(String msg, UnicodeString s, String errorCode) {
        ValidationFailure err = new ValidationFailure("Invalid duration value '" + s + "' (" + msg + ')');
        err.setErrorCode(errorCode);
        return err;
    }

    /**
     * Parse a simple unsigned integer
     *
     * @param s the string containing the sequence of digits. No sign or whitespace is allowed.
     * @return the integer. Return -1 if the string is not a sequence of digits, or -2 if it exceeds 2^31
     */

    protected static int simpleInteger(/*@NotNull*/ String s) {
        long result = 0;
        int len = s.length();
        if (len == 0) {
            return -1;
        }
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                result = result * 10 + (c - '0');
                if (result > Integer.MAX_VALUE) {
                    return -2;
                }
            } else {
                return -1;
            }
        }
        return (int) result;
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param typeLabel the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        if (_negative) {
            return new DurationValue(0, -_months, 0, 0, 0, -_seconds, -_nanoseconds, typeLabel);
        } else {
            return new DurationValue(0, _months, 0, 0, 0, _seconds, _nanoseconds, typeLabel);
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
        return BuiltInAtomicType.DURATION;
    }

    /**
     * Return the signum of the value
     *
     * @return -1 if the duration is negative, zero if it is zero-length, +1 if it is positive
     */

    public int signum() {
        if (_negative) {
            return -1;
        }
        if (_months == 0 && _seconds == 0L && _nanoseconds == 0) {
            return 0;
        }
        return +1;
    }

    /**
     * Get the year component
     *
     * @return the number of years in the normalized duration; always positive
     */

    public int getYears() {
        return _months / 12;
    }

    /**
     * Get the months component
     *
     * @return the number of months in the normalized duration; always positive, in the range 0-11
     */

    public int getMonths() {
        return _months % 12;
    }

    /**
     * Get the days component
     *
     * @return the number of days in the normalized duration; always positive
     */

    public int getDays() {
//        System.err.println("seconds = " + seconds);
//        System.err.println("minutes = " + seconds / 60L);
//        System.err.println("hours = " + seconds / (60L*60L));
//        System.err.println("days = " + seconds / (24L*60L*60L));
//        System.err.println("days (int) = " + (int)(seconds / (24L*60L*60L)));
        return (int) (_seconds / (24L * 60L * 60L));
    }

    /**
     * Get the hours component
     *
     * @return the number of hours in the normalized duration; always positive, in the range 0-23
     */

    public int getHours() {
        return (int) (_seconds % (24L * 60L * 60L) / (60L * 60L));
    }

    /**
     * Get the minutes component
     *
     * @return the number of minutes in the normalized duration; always positive, in the range 0-59
     */

    public int getMinutes() {
        return (int) (_seconds % (60L * 60L) / 60L);
    }

    /**
     * Get the seconds component
     *
     * @return the number of whole seconds in the normalized duration; always positive, in the range 0-59
     */

    public int getSeconds() {
        return (int) (_seconds % 60L);
    }

    /**
     * Get the microseconds component
     *
     * @return the number of nanoseconds in the normalized duration, divided by one thousand and rounded down;
     * always positive, in the range 0-999999
     */

    public int getMicroseconds() {
        return _nanoseconds / 1000;
    }

    /**
     * Get the nanoseconds component
     *
     * @return the number of nanoseconds in the normalized duration; always positive, in the range 0-999,999,999
     */

    public int getNanoseconds() {
        return _nanoseconds;
    }

    /**
     * Get the total number of months (ignoring the days/hours/minutes/seconds)
     *
     * @return the total number of months, that is (getYears()*12) + getMonths(), as a positive
     *         or negative number according as the duration is positive or negative
     */

    public int getTotalMonths() {
        return _negative ? -_months : _months;
    }

    /**
     * Get the total number of seconds (ignoring the years/months)
     *
     * @return the total number of seconds, as a positive
     *         or negative number according as the duration is positive or negative,
     *         with the fractional part indicating parts of a second to nanosecond precision
     */

    public BigDecimal getTotalSeconds() {
        BigDecimal dec = BigDecimal.valueOf(_negative ? -_seconds : _seconds);
        if (_nanoseconds != 0) {
            dec = dec.add(new BigDecimal(BigInteger.valueOf(_negative ? -_nanoseconds : _nanoseconds), 9));
        }
        return dec;
    }

    /**
     * Convert to string
     *
     * @return ISO 8601 representation.
     */

    @Override
    public UnicodeString getPrimitiveStringValue() {

        // Note, Schema does not define a canonical representation. We omit all zero components, unless
        // the duration is zero-length, in which case we output PT0S.

        if (_months == 0 && _seconds == 0L && _nanoseconds == 0) {
            return BMPString.of("PT0S");
        }

        UnicodeBuilder sb = new UnicodeBuilder(16);
        if (_negative) {
            sb.append('-');
        }
        int years = getYears();
        int months = getMonths();
        int days = getDays();
        int hours = getHours();
        int minutes = getMinutes();
        int seconds = getSeconds();

        sb.append("P");
        if (years != 0) {
            sb.append(years + "Y");
        }
        if (months != 0) {
            sb.append(months + "M");
        }
        if (days != 0) {
            sb.append(days + "D");
        }
        if (hours != 0 || minutes != 0 || seconds != 0 || _nanoseconds != 0) {
            sb.append("T");
        }
        if (hours != 0) {
            sb.append(hours + "H");
        }
        if (minutes != 0) {
            sb.append(minutes + "M");
        }
        if (seconds != 0 || _nanoseconds != 0) {
            if (seconds != 0 && _nanoseconds == 0) {
                sb.append(seconds + "S");
            } else {
                formatFractionalSeconds(sb, seconds, (seconds * 1_000_000_000L) + _nanoseconds);
            }
        }

        return sb.toUnicodeString();

    }

    /**
     * Get length of duration in seconds, assuming an average length of month. (Note, this defines a total
     * ordering on durations which is different from the partial order defined in XML Schema; XPath 2.0
     * currently avoids defining an ordering at all. But the ordering here is consistent with the ordering
     * of the two duration subtypes in XPath 2.0.)
     *
     * @return the duration in seconds, as a double
     */

    public double getLengthInSeconds() {
        double a = _months * (365.242199 / 12.0) * 24 * 60 * 60 + _seconds + ((double) _nanoseconds / 1_000_000_000);
        return _negative ? -a : a;
    }

    /**
     * Get a component of the normalized value
     * @param component the required component
     */

    @Override
    public AtomicValue getComponent(AccessorFn.Component component) {
        switch (component) {
            case YEAR:
                return Int64Value.makeIntegerValue(_negative ? -getYears() : getYears());
            case MONTH:
                return Int64Value.makeIntegerValue(_negative ? -getMonths() : getMonths());
            case DAY:
                return Int64Value.makeIntegerValue(_negative ? -getDays() : getDays());
            case HOURS:
                return Int64Value.makeIntegerValue(_negative ? -getHours() : getHours());
            case MINUTES:
                return Int64Value.makeIntegerValue(_negative ? -getMinutes() : getMinutes());
            case SECONDS:
                StringBuilder sb = new StringBuilder(16);
                String ms = "000000000" + _nanoseconds;
                ms = ms.substring(ms.length() - 9);
                sb.append(_negative ? "-" : "").append(getSeconds()).append('.').append(ms);
                return BigDecimalValue.parse(sb.toString());
            case WHOLE_SECONDS:
                return Int64Value.makeIntegerValue(_negative ? -_seconds : _seconds);
            case MICROSECONDS:
                return new Int64Value((_negative ? -_nanoseconds : _nanoseconds) / 1000);
            case NANOSECONDS:
                return new Int64Value(_negative ? -_nanoseconds : _nanoseconds);
            default:
                throw new IllegalArgumentException("Unknown component for duration: " + component);
        }
    }


    /**
     * Get an object value that implements the XPath equality and ordering comparison semantics for this value.
     * If the ordered parameter is set to true, the result will be a Comparable and will support a compareTo()
     * method with the semantics of the XPath lt/gt operator, provided that the other operand is also obtained
     * using the getXPathComparable() method. In all cases the result will support equals() and hashCode() methods
     * that support the semantics of the XPath eq operator, again provided that the other operand is also obtained
     * using the getXPathComparable() method. A context argument is supplied for use in cases where the comparison
     * semantics are context-sensitive, for example where they depend on the implicit timezone or the default
     * collation.
     *  @param collator collation used for comparing string values
     * @param implicitTimezone  the XPath dynamic evaluation context, used in cases where the comparison is context
     */

    /*@Nullable*/
    @Override
    public AtomicMatchKey getXPathMatchKey(StringCollator collator, int implicitTimezone) {
        return this;
    }

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone) throws NoDynamicContextException {
        return null;
    }

    /**
     * Test if the two durations are of equal length.
     *
     * @throws ClassCastException if the other value is not an xs:duration or subtype thereof
     */

    public boolean equals(Object other) {
        if (other instanceof DurationValue) {
            DurationValue d1 = this;
            DurationValue d2 = (DurationValue) other;

            return d1._negative == d2._negative &&
                    d1._months == d2._months &&
                    d1._seconds == d2._seconds &&
                    d1._nanoseconds == d2._nanoseconds;
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Double.valueOf(getLengthInSeconds()).hashCode();
    }

    /**
     * Add two durations
     *
     * @param other the duration to be added to this one
     * @return the sum of the two durations
     * @throws XPathException if an error is detected
     */

    public DurationValue add(DurationValue other) throws XPathException {
        throw new XPathException("Only subtypes of xs:duration can be added", "XPTY0004").asTypeError();
    }

    /**
     * Subtract two durations
     *
     * @param other the duration to be subtracted from this one
     * @return the difference of the two durations
     * @throws XPathException if an error is detected
     */

    public DurationValue subtract(DurationValue other) throws XPathException {
        throw new XPathException("Only subtypes of xs:duration can be subtracted")
                .withErrorCode("XPTY0004")
                .asTypeError();
    }

    /**
     * Negate a duration (same as subtracting from zero, but it preserves the type of the original duration)
     *
     * @return the original duration with its sign reversed, retaining its type
     */

    public DurationValue negate() {
        if (_negative) {
            return new DurationValue(0, _months, 0, 0, 0, _seconds, _nanoseconds, typeLabel);
        } else {
            return new DurationValue(0, -_months, 0, 0, 0, -_seconds, -_nanoseconds, typeLabel);
        }
    }

    /**
     * Multiply a duration by an integer
     *
     * @param factor the number to multiply by
     * @return the result of the multiplication
     * @throws XPathException if an error is detected
     */

    public DurationValue multiply(long factor) throws XPathException {
        return multiply((double)factor);
    }


    /**
     * Multiply a duration by a double
     *
     * @param factor the number to multiply by
     * @return the result of the multiplication
     * @throws XPathException if an error is detected
     */

    public DurationValue multiply(double factor) throws XPathException {
        throw new XPathException("Only subtypes of xs:duration can be multiplied by a number", "XPTY0004").asTypeError();
    }

    /**
     * Multiply a duration by a decimal
     *
     * @param factor the number to multiply by
     * @return the result of the multiplication
     * @throws XPathException if an error is detected
     */

    public DurationValue multiply(BigDecimal factor) throws XPathException {
        throw new XPathException("Only subtypes of xs:duration can be multiplied by a number", "XPTY0004").asTypeError();
    }

    /**
     * Divide a duration by a number
     *
     * @param factor the number to divide by
     * @return the result of the division
     * @throws XPathException if an error is detected
     */

    public DurationValue divide(double factor) throws XPathException {
        throw new XPathException("Only subtypes of xs:duration can be divided by a number", "XPTY0004").asTypeError();
    }

    /**
     * Divide a duration by a another duration
     *
     * @param other the duration to divide by
     * @return the result of the division
     * @throws XPathException if an error is detected
     */

    public BigDecimalValue divide(DurationValue other) throws XPathException {
        throw new XPathException("Only subtypes of xs:duration can be divided by another duration", "XPTY0004").asTypeError();
    }

    /**
     * Get a Comparable value that implements the XML Schema ordering comparison semantics for this value.
     * This implementation handles the ordering rules for durations in XML Schema.
     * It is overridden for the two subtypes DayTimeDuration and YearMonthDuration.
     *
     * @return a suitable Comparable
     */

    /*@NotNull*/
    public DurationComparable getSchemaComparable() {
        int m = this._months;
        long s = this._seconds;
        int n = this._nanoseconds;
        if (this._negative) {
            s = -s;
            m = -m;
            n = -n;
        }
        return new DurationComparable(m, s, n);
    }

    /**
     * DurationValueComparable is a Comparable value that acts as a surrogate for a Duration,
     * having ordering rules that implement the XML Schema specification.
     */

    public static class DurationComparable implements Comparable<DurationComparable> {

        private final int months;
        private final long seconds;
        private final int nanoseconds;

        public DurationComparable(int m, long s, int nanos) {
            months = m;
            seconds = s;
            nanoseconds = nanos;
        }

        /**
         * Compare two durations according to the XML Schema rules.
         *
         * @param other the other duration
         * @return -1 if this duration is smaller; 0 if they are equal; +1 if this duration is greater;
         *         {@link net.sf.saxon.om.SequenceTool#INDETERMINATE_ORDERING} if there is no defined order
         */

        @Override
        public int compareTo(DurationComparable other) {
            if (months == other.months) {
                if (seconds == other.seconds) {
                    return Integer.compare(nanoseconds, other.nanoseconds);
                } else {
                    return Long.compare(seconds, other.seconds);
                }
            } else {
                // The months figure varies, but the seconds figure might tip things over if it's high
                // enough. We make the assumption, however, that the nanoseconds won't affect things.
                double oneDay = 24e0 * 60e0 * 60e0;
                double min0 = monthsToDaysMinimum(months) * oneDay + seconds;
                double max0 = monthsToDaysMaximum(months) * oneDay + seconds;
                double min1 = monthsToDaysMinimum(other.months) * oneDay + other.seconds;
                double max1 = monthsToDaysMaximum(other.months) * oneDay + other.seconds;
                if (max0 < min1) {
                    return -1;
                } else if (min0 > max1) {
                    return +1;
                } else {
                    //noinspection ComparatorMethodParameterNotUsed
                    return SequenceTool.INDETERMINATE_ORDERING;
                }
            }
        }

        public boolean equals(Object o) {
            return o instanceof DurationComparable && compareTo((DurationComparable)o) == 0;
        }

        public int hashCode() {
            return months ^ (int) seconds;
        }

        private int monthsToDaysMinimum(int months) {
            if (months < 0) {
                return -monthsToDaysMaximum(-months);
            }
            if (months < 12) {
                int[] shortest = {0, 28, 59, 89, 120, 150, 181, 212, 242, 273, 303, 334};
                return shortest[months];
            } else {
                int years = months / 12;
                int remainingMonths = months % 12;
                // the -1 is to allow for the fact that we might miss a leap day if we time the start badly
                int yearDays = years * 365 + (years % 4) - (years % 100) + (years % 400) - 1;
                return yearDays + monthsToDaysMinimum(remainingMonths);
            }
        }

        private int monthsToDaysMaximum(int months) {
            if (months < 0) {
                return -monthsToDaysMinimum(-months);
            }
            if (months < 12) {
                int[] longest = {0, 31, 62, 92, 123, 153, 184, 215, 245, 276, 306, 337};
                return longest[months];
            } else {
                int years = months / 12;
                int remainingMonths = months % 12;
                // the +1 is to allow for the fact that we might miss a leap day if we time the start badly
                int yearDays = years * 365 + (years % 4) - (years % 100) + (years % 400) + 1;
                return yearDays + monthsToDaysMaximum(remainingMonths);
            }
        }
    }

}

