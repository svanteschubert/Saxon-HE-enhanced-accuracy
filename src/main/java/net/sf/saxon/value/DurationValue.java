////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
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
import net.sf.saxon.str.Latin1;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceException;
import net.sf.saxon.type.*;

import java.math.BigDecimal;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * A value of type xs:duration. A duration is represented as an integer number of months (limited to 32 bits)
 * and a BigDecimal number of seconds (no limits). Both (if non-zero) must have the same sign.
 */

public class DurationValue extends AtomicValue implements XPathComparable, AtomicMatchKey {


    protected final int _months;
    protected final BigDecimal _seconds;

    private final static Pattern unsignedDecimal = Pattern.compile("[0-9]+(\\.[0-9]+)");
    private final static Pattern unsignedInteger = Pattern.compile("[0-9]+");

    /**
     * Construct an xs:duration from the number of months and seconds
     * @param months the integer number of months.
     * @param seconds the decimal number of seconds. Must be negative or zero if months is negative; must
     *                be positive or zero if months is positive.
     * @param typeLabel allows a subtype of xs:duration to be constructed - but not xs:dayTimeDuration
     *                  or xs:yearMonthDuration, as these have separate subclasses.
     * @throws IllegalArgumentException if the months and seconds have conflicting sign
     */

    public DurationValue(int months, BigDecimal seconds, AtomicType typeLabel) {
        super(typeLabel);
        //assert(typeLabel.getPrimitiveAtomicType() == BuiltInAtomicType.DURATION);
        if (Integer.signum(months) * seconds.signum() == -1) {
            throw new IllegalArgumentException("The months and seconds components of a duration cannot have different sign");
        }
        this._months = months;
        this._seconds = seconds;
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
    @CSharpReplaceException(from="java.lang.NumberFormatException", to="System.FormatException")
    protected static ConversionResult makeDuration(UnicodeString s, boolean allowYM, boolean allowDT) {
        //long years = 0, months = 0, days = 0, hours = 0, minutes = 0;
        BigDecimal seconds = BigDecimal.ZERO;
        int months = 0;
        boolean negative = false;
        StringTokenizer tok = new StringTokenizer(Whitespace.trim(s).toString(), "-+PYMDTHS", true);
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
            if (!tok.hasMoreTokens()) {
                return badDuration("missing unit letter at end", s);
            }
            char delim = tok.nextToken().charAt(0);
            if (!unsignedInteger.matcher(part).matches()) {
                if (delim != 'S') {
                    return badDuration("components other than 'S' must be unsigned integers", s);
                }
                if (!unsignedDecimal.matcher(part).matches()) {
                    return badDuration("invalid decimal format for 'S' component", s);
                }
            }
            BigDecimal partValue;
            try {
                partValue = new BigDecimal(part).stripTrailingZeros();
            } catch (NumberFormatException err) {
                // Should not happen, we have already tested
                return badDuration("non-numeric '" + delim + "' component", s);
            }

            switch (delim) {
                case 'Y':
                    if (state > 0) {
                        return badDuration("Y is out of sequence", s);
                    }
                    if (!allowYM) {
                        return badDuration("Year component is not allowed in dayTimeDuration", s);
                    }
                    try {
                        months += Math.multiplyExact(partValue.intValueExact(), 12);
                    } catch (ArithmeticException e) {
                        return badDuration("Year component exceeds Saxon limits", s, "FODT0002");
                    }
                    state = 1;
                    components++;
                    break;
                case 'M':
                    if (state == 4 || state == 5) {
                        if (!allowDT) {
                            return badDuration("Minute component is not allowed in yearMonthDuration", s);
                        }
                        seconds = seconds.add(partValue.multiply(BigDecimalValue.SIXTY));
                        state = 6;
                        components++;
                        break;
                    } else if (state == 0 || state == 1) {
                        if (!allowYM) {
                            return badDuration("Month component is not allowed in dayTimeDuration", s);
                        }
                        try {
                            months = Math.addExact(months, partValue.intValueExact());
                        } catch (ArithmeticException e) {

                            return badDuration("Month component exceeds Saxon limits", s, "FODT0002");
                        }
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
                    seconds = seconds.add(partValue.multiply(BigDecimalValue.SECONDS_PER_DAY));
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
                    seconds = seconds.add(partValue.multiply(BigDecimalValue.SECONDS_PER_HOUR));
                    state = 5;
                    components++;
                    break;
                case 'S':
                    if (state < 4 || state > 7) {
                        return badDuration("S is out of sequence", s);
                    }
                    if (!allowDT) {
                        return badDuration("Seconds component is not allowed in yearMonthDuration", s);
                    }
                    seconds = seconds.add(partValue);
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
            months = -months;
            seconds = seconds.negate();
        }

        return new DurationValue (months, seconds, BuiltInAtomicType.DURATION);
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
     * @return the integer. Return -1 if the string is not a sequence of digits, or -2 if it exceeds 2^63
     */

    protected static long simpleInteger(String s) {
        long result = 0;
        int len = s.length();
        if (len == 0) {
            return -1;
        }
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                try {
                    result = Math.addExact(Math.multiplyExact(result, 10L), (c - '0'));
                } catch (ArithmeticException e) {
                    return -2;
                }
            } else {
                return -1;
            }
        }
        return result;
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param metadata the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type. This method can't be used to create
     *                  an instance of xs:dayTimeDuration or xs:yearMonthDuration.
     */

    @Override
    public AtomicValue withMetadata(AtomicMetadata metadata) {
        BuiltInAtomicType req = metadata.getType().getPrimitiveAtomicType();
        if (req == BuiltInAtomicType.DAY_TIME_DURATION) {
            return new DayTimeDurationValue(_seconds);
        } else if (req == BuiltInAtomicType.YEAR_MONTH_DURATION) {
            return new YearMonthDurationValue(_months, BuiltInAtomicType.YEAR_MONTH_DURATION);
        } else {
            return new DurationValue(_months, _seconds, metadata.getType());
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
        return Integer.signum(Integer.signum(_months) + _seconds.signum());
    }

    /**
     * Get the year component
     *
     * @return the number of years in the normalized duration; always positive
     */

    public int getYears() {
        return Math.abs(_months / 12);
    }

    /**
     * Get the months component
     *
     * @return the number of months in the normalized duration; always positive, in the range 0-11
     */

    public int getMonths() {
        return Math.abs(_months % 12);
    }

    /**
     * Get the days component
     *
     * @return the number of days in the normalized duration; always positive
     */

    public long getDays() {
        return Math.abs(getDaysHoursMinutesAndSeconds()[0].longValue());
    }

    /**
     * Get the days, hours, minute, and seconds components, in normalized form, as
     * an array of four decimal values (the first three will always be integral). All four
     * values will have the same sign (negative for a negative duration, positive
     * if positive).
     */
    public BigDecimal[] getDaysHoursMinutesAndSeconds() {
        BigDecimal[] result = new BigDecimal[4];
        BigDecimal[] D_HMS = _seconds.divideAndRemainder(BigDecimalValue.SECONDS_PER_DAY);
        result[0] = D_HMS[0];
        BigDecimal[] H_MS = D_HMS[1].divideAndRemainder(BigDecimalValue.SECONDS_PER_HOUR);
        result[1] = H_MS[0];
        BigDecimal[] M_S = H_MS[1].divideAndRemainder(BigDecimalValue.SECONDS_PER_MINUTE);
        result[2] = M_S[0];
        result[3] = M_S[1];
        return result;
    }

    /**
     * Get the hours component
     *
     * @return the number of hours in the normalized duration; always positive, in the range 0-23
     */

    public int getHours() {
        return (int)Math.abs(getDaysHoursMinutesAndSeconds()[1].longValue());
    }

    /**
     * Get the minutes component
     *
     * @return the number of minutes in the normalized duration; always positive, in the range 0-59
     */

    public int getMinutes() {
        return (int)Math.abs(getDaysHoursMinutesAndSeconds()[2].longValue());
    }

    /**
     * Get the seconds component
     *
     * @return the number of seconds (including fractional seconds) in the normalized duration; always positive, in the range 0-59
     */

    public BigDecimal getSeconds() {
        return getDaysHoursMinutesAndSeconds()[3].abs();
    }

    /**
     * Get the total number of months (ignoring the days/hours/minutes/seconds)
     *
     * @return the total number of months, that is <code>getYears()*12 + getMonths()</code>, as a positive
     *         or negative number according as the duration is positive or negative
     */

    public int getTotalMonths() {
        return _months;
    }

    /**
     * Get the total number of seconds (ignoring the years/months)
     *
     * @return the total number of seconds (across the days, hours, minutes,
     *         and seconds components), as a positive
     *         or negative number according as the duration is positive or negative,
     *         with the fractional part indicating parts of a second
     */

    public BigDecimal getTotalSeconds() {
        return _seconds;
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

        if (_months == 0 && _seconds.signum() == 0) {
            return U_ZERO_DURATION;
        }

        UnicodeBuilder sb = new UnicodeBuilder(16);
        if (signum() < 0) {
            sb.append('-');
        }
        int years = getYears();
        int months = getMonths();

        BigDecimal[] dhms = getDaysHoursMinutesAndSeconds();
        long days = dhms[0].abs().longValueExact();
        int hours = dhms[1].abs().intValueExact();
        int minutes = dhms[2].abs().intValueExact();
        BigDecimal seconds = dhms[3].abs();

        sb.append("P");
        if (years != 0) {
            sb.append(UnicodeString.fromLong(years)).append('Y');
        }
        if (months != 0) {
            sb.append(UnicodeString.fromLong(months)).append('M');
        }
        if (days != 0) {
            sb.append(UnicodeString.fromLong(days)).append('D');
        }
        if (hours != 0 || minutes != 0 || seconds.signum() != 0) {
            sb.append('T');
        }
        if (hours != 0) {
            sb.append(UnicodeString.fromLong(hours)).append('H');
        }
        if (minutes != 0) {
            sb.append(UnicodeString.fromLong(minutes)).append('M');
        }
        if (seconds.signum() != 0) {
            BigDecimalValue.decimalToUnicodeString(seconds.stripTrailingZeros(), sb);
            sb.append('S');
        }

        return sb.toUnicodeString();

    }

    private final static UnicodeString U_ZERO_DURATION = Latin1.of("PT0S");

    /**
     * Get a component of the normalized value
     * @param component the required component. Component values are negative if the duration is negative
     */

    @Override
    public AtomicValue getComponent(AccessorFn.Component component) {
        boolean negative = signum() < 0;
        switch (component) {
            case YEAR:
                return Int64Value.makeIntegerValue(negative ? -getYears() : getYears());
            case MONTH:
                return Int64Value.makeIntegerValue(negative ? -getMonths() : getMonths());
            case DAY:
                return Int64Value.makeIntegerValue(negative ? -getDays() : getDays());
            case HOURS:
                return Int64Value.makeIntegerValue(negative ? -getHours() : getHours());
            case MINUTES:
                return Int64Value.makeIntegerValue(negative ? -getMinutes() : getMinutes());
            case SECONDS:
                return new BigDecimalValue(negative ? getSeconds().negate() : getSeconds());
            case WHOLE_SECONDS: {
                BigDecimal decimalSeconds = negative ? getSeconds().negate() : getSeconds();
                return Int64Value.makeIntegerValue(decimalSeconds.longValue());
            }
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
     *
     * @param collator         collation used for comparing string values
     * @param implicitTimezone the XPath dynamic evaluation context, used in cases where the comparison is context
     * @param specVersion      Durations (as distinct from subtypes thereof) are comparable only if version is 4.0
     *                         or greater.
     */

    /*@Nullable*/
    @Override
    public AtomicMatchKey getXPathMatchKey(StringCollator collator, int implicitTimezone, int specVersion) {
        return this;
    }

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone, int specVersion) throws NoDynamicContextException {
        return specVersion >= 40 ? this : null;
    }

    @Override
    public int compareTo(XPathComparable o) {
        if (o instanceof DurationValue d2) {
            int mDiff = Integer.compare(this._months, d2.getTotalMonths());
            return mDiff == 0 ? this._seconds.compareTo(d2.getTotalSeconds()) : mDiff;
        } else {
            throw new ClassCastException();
        }
    }

    /**
     * Test if the two durations are of equal length.
     *
     * @throws ClassCastException if the other value is not an xs:duration or subtype thereof
     */

    public boolean equals(Object other) {
        if (other instanceof DurationValue d2) {
            return this._months == d2._months &&
                    this._seconds.compareTo(d2._seconds) == 0;
        } else {
            return false;
        }
    }

    public int hashCode() {
        return _months ^ getTotalSeconds().hashCode();
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
        return new DurationValue(-_months, _seconds.negate(), getItemType());
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
        return new DurationComparable(_months, _seconds);
    }

    /**
     * DurationValueComparable is a Comparable value that acts as a surrogate for a Duration,
     * having ordering rules that implement the XML Schema specification.
     */

    public static class DurationComparable implements Comparable<DurationComparable> {

        private final int months;
        private final BigDecimal seconds;


        public DurationComparable(int m, BigDecimal s) {
            months = m;
            seconds = s;
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
                return seconds.compareTo(other.seconds);
            } else {
                // The months figure varies, but the seconds figure might tip things over if it's high
                // enough. We make the assumption, however, that the nanoseconds won't affect things.
                double oneDay = 24e0 * 60e0 * 60e0;
                double min0 = monthsToDaysMinimum(months) * oneDay + seconds.doubleValue();
                double max0 = monthsToDaysMaximum(months) * oneDay + seconds.doubleValue();
                double min1 = monthsToDaysMinimum(other.months) * oneDay + other.seconds.doubleValue();
                double max1 = monthsToDaysMaximum(other.months) * oneDay + other.seconds.doubleValue();
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
            return months ^ seconds.hashCode();
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

