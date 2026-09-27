////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.str.TwineBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.*;

/**
 * A value of type {@code xs:dateTime}. This contains integer fields year, month, day, hour, minute,
 * second, and nanosecond. All these fields except year must be non-negative. In the internal
 * representation, the sequence of years runs -2, -1, 0, +1, +2: that is, the year before year 1
 * is year 0.
 * <p>The value also contains a boolean flag <code>hasNoYearZero</code>. When this flag is set, accessor
 * methods that expose the year value subtract one if it is non-positive: that is, year 0 is displayed
 * as -1, year -1 as -2, and so on. Constructor methods, unless otherwise specified, do not set this
 * flag.</p>
 * <p>From Saxon 9.9, this class implements the Java 8 interface {@link TemporalAccessor} which enables
 * it to interoperate with Java 8 temporal classes such as {@link Instant} and {@link ZonedDateTime}.</p>
 */

@CSharpInjectMembers(code={""
        + "public static Saxon.Hej.value.DateTimeValue fromDateTime(System.DateTime dt) {"
        + "    Saxon.Hej.value.DateTimeValue dtv = new (dt.Year, dt.Month, dt.Day, dt.Hour, dt.Minute, makeSeconds(dt.Second, dt.Millisecond * 1000000), 0);"
        + "    return (Saxon.Hej.value.DateTimeValue)dtv.withMetadata(Saxon.Hej.type.BuiltInAtomicType.DATE_TIME_STAMP);"
        + "}"
        + " public static Saxon.Hej.value.DateTimeValue fromDateTimeOffset(System.DateTimeOffset dt) {"
        + "    Saxon.Hej.value.DateTimeValue dtv = new (dt.Year, dt.Month, dt.Day, dt.Hour, dt.Minute, makeSeconds(dt.Second, dt.Millisecond * 1000000), (int)dt.Offset.TotalMinutes);"
        + "    return (Saxon.Hej.value.DateTimeValue)dtv.withMetadata(Saxon.Hej.type.BuiltInAtomicType.DATE_TIME_STAMP);"
        + "}"
})

public final class DateTimeValue extends CalendarValue
        implements XPathComparable
        , TemporalAccessor
{

    private final int year;       // the year as written; allows year zero
    private final int month;     // the month as written, range 1-12
    private final int day;       // the day as written, range 1-31
    private final int hour;      // the hour as written (except for midnight), range 0-23
    private final int minute;    // the minutes as written, range 0-59
    private final BigDecimal seconds;    // the seconds as written, range 0-59.9999... (no leap seconds)

    /**
     * Create a DateTimeValue from its components
     * @param year the year; allows year zero
     * @param month the month (1 to 12)
     * @param day the day (1 to 31)
     * @param hour the hour (0 to 23)
     * @param minute the minute (0 to 59)
     * @param seconds the second ( to 59.999999...)
     * @param tzMinutes the timezone offset in minutes, or {@link #NO_TIMEZONE} if absent
     * @param typeLabel the type annotation
     */
    public DateTimeValue(int year, int month, int day, int hour, int minute, BigDecimal seconds, int tzMinutes, AtomicMetadata typeLabel) {
        super(typeLabel, tzMinutes);
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
        this.seconds = seconds;
    }

    /**
     * Create a DateTimeValue from its components, defaulting the type annotation to xs:dateTimeStamp if there
     * is a timezone, or xs:dateTime otherwise
     *
     * @param year      the year; allows year zero
     * @param month     the month (1 to 12)
     * @param day       the day (1 to 31)
     * @param hour      the hour (0 to 23)
     * @param minute    the minute (0 to 59)
     * @param seconds   the second ( to 59.999999...)
     * @param tzMinutes the timezone offset in minutes, or {@link #NO_TIMEZONE} if absent
     */

    public DateTimeValue(int year, int month, int day, int hour, int minute, BigDecimal seconds, int tzMinutes) {
        super(tzMinutes == NO_TIMEZONE ? BuiltInAtomicType.DATE_TIME : BuiltInAtomicType.DATE_TIME_STAMP, tzMinutes);
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
        this.seconds = seconds;
    }

    /**
     * Get the dateTime value representing the nominal
     * date/time of this transformation run. Two calls within the same
     * query or transformation will always return the same answer.
     *
     * @param context the XPath dynamic context. May be null, in which case
     *                the current date and time are taken directly from the system clock
     * @return the current xs:dateTime
     */

    /*@Nullable*/
    public static DateTimeValue getCurrentDateTime(XPathContext context) {
        Controller c;
        if (context == null || (c = context.getController()) == null) {
            // non-XSLT/XQuery environment
            // We also take this path when evaluating compile-time expressions that require an implicit timezone.
            return now();
        } else {
            return c.getCurrentDateTime();
        }
    }

    /**
     * Get the dateTime value representing the moment of invocation of this method,
     * in the default timezone set for the platform on which the application is running.
     * @return the current dateTime, in the local timezone for the platform.
     */

    @CSharpReplaceBody(code="return fromDateTimeOffset(System.DateTimeOffset.Now);")
    public static DateTimeValue now() {
        return DateTimeValue.fromZonedDateTime(ZonedDateTime.now());
    }

    /**
     * Constructor: create a dateTime value given a Java calendar object.
     * The {@code #hasNoYearZero} flag is set to {@code true}.
     *
     * @param calendar    holds the date and time
     * @param tzSpecified indicates whether the timezone is specified
     */

    public static DateTimeValue fromCalendar(Calendar calendar, boolean tzSpecified) {
        int era = calendar.get(GregorianCalendar.ERA);
        int year = calendar.get(Calendar.YEAR);
        if (era == GregorianCalendar.BC) {
            year = -year;
        }
        int month = (calendar.get(Calendar.MONTH) + 1);
        int day = calendar.get(Calendar.DATE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        BigDecimal seconds = makeSeconds(calendar.get(Calendar.SECOND), calendar.get(Calendar.MILLISECOND) * 1_000_000);
        int tzMinutes = NO_TIMEZONE;
        if (tzSpecified) {
            tzMinutes = (calendar.get(Calendar.ZONE_OFFSET) +
                    calendar.get(Calendar.DST_OFFSET)) / 60_000;
        }
        return new DateTimeValue(year, month, day, hour, minute, seconds, tzMinutes);
    }


    /**
     * Factory method: create a dateTime value given a Java Date object. The returned dateTime
     * value will always have a timezone, which will always be UTC.
     *
     * @param suppliedDate holds the date and time
     * @return the corresponding xs:dateTime value
     * @throws XPathException if a dynamic error occurs
     */

    /*@NotNull*/
    public static DateTimeValue fromJavaDate(/*@NotNull*/ Date suppliedDate) throws XPathException {
        long millis = suppliedDate.getTime();
        return EPOCH.add(DayTimeDurationValue.fromMilliseconds(millis));
    }

    /**
     * Factory method: create a dateTime value given a Java time, expressed in milliseconds since 1970. The returned dateTime
     * value will always have a timezone, which will always be UTC.
     *
     * @param time the time in milliseconds since the epoch
     * @return the corresponding xs:dateTime value
     * @throws XPathException if a dynamic error occurs
     */

    /*@NotNull*/
    public static DateTimeValue fromJavaTime(long time) throws XPathException {
        return EPOCH.add(DayTimeDurationValue.fromMilliseconds(time));
    }

    /**
     * Factory method: create a dateTime value given the components of a Java Instant. The java.time.Instant class
     * is new in JDK 8, and this method was introduced under older JDKs, so this method takes two arguments,
     * the seconds and nano-seconds values, which can be obtained from a Java Instant
     * using the methods getEpochSecond() and getNano() respectively
     *
     * @param seconds the time in seconds since the epoch
     * @param nano the additional nanoseconds
     * @return the corresponding xs:dateTime value, which will have timezone Z (UTC)
     * @throws XPathException if a dynamic error occurs (typically due to overflow)
     */

    /*@NotNull*/
    public static DateTimeValue fromJavaInstant(long seconds, int nano) throws XPathException {
        BigInteger totalNanoseconds = BigInteger.valueOf(seconds)
                .multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(nano));
        BigDecimal totalSeconds = new BigDecimal(totalNanoseconds, 9);
        return EPOCH.add(new DayTimeDurationValue(totalSeconds));
    }

    /**
     * Factory method: create a dateTime value given a Java {@link Instant}. The {@code java.time.Instant} class
     * is new in JDK 8.
     *
     * @param instant the point in time
     * @return the corresponding xs:dateTime value, which will have timezone Z (UTC)
     * @since 9.9
     */

    /*@NotNull*/
    public static DateTimeValue fromJavaInstant(Instant instant) {
        try {
            return fromJavaInstant(instant.getEpochSecond(), instant.getNano());
        } catch (XPathException e) {
            throw new AssertionError();
        }
    }

    /**
     * Factory method: create a dateTime value given a Java {@link ZonedDateTime}. The {@code java.time.ZonedDateTime} class
     * is new in JDK 8.
     *
     * @param zonedDateTime the supplied zonedDateTime value
     * @return the corresponding xs:dateTime value, which will have the same timezone offset as the supplied ZonedDateTime;
     * the actual (civil) timezone information is lost. The returned value will be an instance of the built-in
     * subtype {@code xs:dateTimeStamp}
     * @since 9.9. Changed in 10.0 to retain nanosecond precision.
     */

    /*@NotNull*/
    public static DateTimeValue fromZonedDateTime(ZonedDateTime zonedDateTime) {
        return fromOffsetDateTime(zonedDateTime.toOffsetDateTime());
    }

    /**
     * Factory method: create a dateTime value given a Java {@link OffsetDateTime}. The {@code java.time.OffsetDateTime} class
     * is new in JDK 8.
     *
     * @param offsetDateTime the supplied zonedDateTime value
     * @return the corresponding xs:dateTime value, which will have the same timezone offset as the supplied OffsetDateTime.
     * The returned value will be an instance of the built-in subtype {@code xs:dateTimeStamp}
     * @since 10.0.
     */

    /*@NotNull*/
    public static DateTimeValue fromOffsetDateTime(OffsetDateTime offsetDateTime) {
        LocalDateTime ldt = offsetDateTime.toLocalDateTime();
        ZoneOffset zo = offsetDateTime.getOffset();
        int tz = zo.getTotalSeconds() / 60;
        return new DateTimeValue(ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth(),
                                 ldt.getHour(), ldt.getMinute(),
                                 makeSeconds(ldt.getSecond(), ldt.getNano()),
                                 tz);
    }

    /**
     * Factory method: create a dateTime value given a Java {@link LocalDateTime}. The {@code java.time.LocalDateTime} class
     * is new in JDK 8.
     *
     * @param localDateTime the supplied localDateTime value
     * @return the corresponding xs:dateTime value, which will have no timezone.
     * @since 9.9. Changed in 10.0 to retain nanosecond precision.
     */

    /*@NotNull*/
    public static DateTimeValue fromLocalDateTime(LocalDateTime localDateTime) {
        return new DateTimeValue(localDateTime.getYear(), localDateTime.getMonthValue(), localDateTime.getDayOfMonth(),
                                 localDateTime.getHour(), localDateTime.getMinute(),
                                 makeSeconds(localDateTime.getSecond(), localDateTime.getNano()),
                                 NO_TIMEZONE);
    }

    /**
     * Make a decimal number of seconds from the integer number of whole seconds and the
     * integer number of nanoseconds
     *
     * @param wholeSeconds the number of whole seconds
     * @param nanoSeconds  the number of nanoseconds
     * @return the decimal number of seconds
     */

    public static BigDecimal makeSeconds(int wholeSeconds, int nanoSeconds) {
        return BigDecimal.valueOf(wholeSeconds).add(decimalShift(BigDecimal.valueOf(nanoSeconds), -9)).stripTrailingZeros();
    }

    @CSharpReplaceBody(code="return Singulink.Numerics.BigDecimal.ShiftDecimal(value, places);")
    private static BigDecimal decimalShift(BigDecimal value, int places) {
        return value.scaleByPowerOfTen(places);
    }

    /**
     * Split a decimal number of seconds into the number of whole seconds and the number of nanoseconds
     *
     * @param seconds the decimal number of second
     * @return an array of two integers containing first, the number of seconds, and second, the number
     * of nanoseconds
     */
    public static int[] splitSeconds(BigDecimal seconds) {
        int[] result = new int[2];
        BigDecimal[] dr = seconds.divideAndRemainder(BigDecimal.ONE);
        result[0] = dr[0].intValue();
        result[1] = decimalShift(dr[1], 9).intValue();
        return result;
    }

    /**
     * Format a decimal seconds value
     */

    public static TwineBuilder formatSeconds(BigDecimal seconds, TwineBuilder tb) {
        int[] split = splitSeconds(seconds);
        tb = appendTwoDigits(tb, split[0]);
        if (split[1] != 0) {
            tb = tb.append('.');
            int ms = split[1];
            int div = 100_000_000;
            while (ms > 0) {
                int d = ms / div;
                tb = tb.append((char) (d + '0'));
                ms = ms % div;
                div /= 10;
            }
        }
        return tb;
    }

    /**
     * Fixed date/time used by Java (and Unix) as the origin of the universe: 1970-01-01T00:00:00Z
     */

    public static final DateTimeValue EPOCH =
            new DateTimeValue(1970, 1, 1, 0, 0, BigDecimal.ZERO, 0);

    /**
     * Factory method: create a dateTime value given a date and a time.
     *
     * @param date the date
     * @param time the time
     * @return the dateTime with the given components. If either component is null, returns null. The returned
     * {@code DateTimeValue} will have the {@code #hasNoYearZero} property if and only if the supplied
     * date has this property.
     * @throws XPathException if the timezones are both present and inconsistent
     */

    /*@Nullable*/
    public static DateTimeValue makeDateTimeValue(DateValue date, TimeValue time) throws XPathException {
        if (date == null || time == null) {
            return null;
        }
        int tz1 = date.getTimezoneInMinutes();
        int tz2 = time.getTimezoneInMinutes();
        if (tz1 != NO_TIMEZONE && tz2 != NO_TIMEZONE && tz1 != tz2) {
            throw new XPathException("Supplied date and time are in different timezones", "FORG0008");
        }

        return new DateTimeValue(
                date.getYear(), date.getMonth(), date.getDay(),
                time.getHour(), time.getMinute(), time.getSecond(), Math.max(tz1, tz2));

    }

    /**
     * Factory method: create a dateTime value from a supplied string, in
     * ISO 8601 format.
     * <p>If the supplied {@link ConversionRules} object has {@link ConversionRules#isAllowYearZero()} returning
     * true, then (a) a year value of zero is allowed in the supplied string, and (b) the {@code hasNoYearZero}
     * property in the result is set to false. If {@link ConversionRules#isAllowYearZero()} returns false,
     * the (a) the year value in the supplied string must not be zero, (b) a year value of -1 in the supplied
     * string is interpreted as representing the year before year 1, and (c) the {@code hasNoYearZero} property
     * in the result is set to true.</p>
     *
     * @param s a string in the lexical space of xs:dateTime
     * @param rules the conversion rules to be used (determining whether year zero is allowed)
     * @return either a DateTimeValue representing the xs:dateTime supplied, or a ValidationFailure if
     *         the lexical value was invalid
     */

    /*@NotNull*/
    public static ConversionResult makeDateTimeValue(UnicodeString s, ConversionRules rules) {
        // input must have format [-]yyyy-mm-ddThh:mm:ss[.fff*][([+|-]hh:mm | Z)]
        StringTokenizer tok = new StringTokenizer(Whitespace.trim(s).toString(), "-:.+TZ", true);

        if (!tok.hasMoreTokens()) {
            return badDate("too short", s);
        }
        String part = tok.nextToken();
        int era = +1;
        if ("+".equals(part)) {
            return badDate("Date must not start with '+' sign", s);
        } else if ("-".equals(part)) {
            era = -1;
            if (!tok.hasMoreTokens()) {
                return badDate("No year after '-'", s);
            }
            part = tok.nextToken();
        }
        long value = DurationValue.simpleInteger(part);
        if (value < 0 || value > Integer.MAX_VALUE) {
            if (value == -1) {
                return badDate("Non-numeric year component", s);
            } else {
                return badDate("Year is outside the range that Saxon can handle", s, "FODT0001");
            }
        }
        int year = (int)value * era;
        if (part.length() < 4) {
            return badDate("Year is less than four digits", s);
        }
        if (part.length() > 4 && part.charAt(0) == '0') {
            return badDate("When year exceeds 4 digits, leading zeroes are not allowed", s);
        }
        if (year == 0 && !rules.isAllowYearZero()) {
            return badDate("Year zero is not allowed under XSD 1.0", s);
        }
        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        if (!"-".equals(tok.nextToken())) {
            return badDate("Wrong delimiter after year", s);
        }

        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        part = tok.nextToken();
        if (part.length() != 2) {
            return badDate("Month must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric month component", s);
        }
        int month = (int)value;
        if (month < 1 || month > 12) {
            return badDate("Month is out of range", s);
        }

        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        if (!"-".equals(tok.nextToken())) {
            return badDate("Wrong delimiter after month", s);
        }
        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        part = tok.nextToken();
        if (part.length() != 2) {
            return badDate("Day must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric day component", s);
        }
        int day = (int) value;
        if (day < 1 || day > 31) {
            return badDate("Day is out of range", s);
        }

        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        if (!"T".equals(tok.nextToken())) {
            return badDate("Wrong delimiter after day, expected 'T'", s);
        }

        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        part = tok.nextToken();
        if (part.length() != 2) {
            return badDate("Hours must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric hours component", s);
        }
        int hours = (int) value;
        if (hours > 24) {
            return badDate("Hours is out of range", s);
        }

        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        if (!":".equals(tok.nextToken())) {
            return badDate("Wrong delimiter after hours, expected ':'", s);
        }

        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        part = tok.nextToken();
        if (part.length() != 2) {
            return badDate("Minutes must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric minutes component", s);
        }
        int minutes = (int)value;
        if (minutes > 59) {
            return badDate("Minutes is out of range", s);
        }
        if (hours == 24 && minutes != 0) {
            return badDate("If hours is 24, minutes must be 00", s);
        }
        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        if (!":".equals(tok.nextToken())) {
            return badDate("Wrong delimiter after minutes", s);
        }

        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        part = tok.nextToken();
        if (part.length() != 2) {
            return badDate("Seconds must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric seconds component", s);
        }
        int wholeSeconds = (int) value;
        BigDecimal seconds = BigDecimal.valueOf(wholeSeconds);
        if (wholeSeconds > 59) {
            return badDate("Seconds is out of range", s);
        }
        if (hours == 24 && wholeSeconds != 0) {
            return badDate("If hours is 24, seconds must be 00", s);
        }

        int tz = NO_TIMEZONE;
        int tzHours = 0;
        boolean negativeTz = false;
        int state = 0;
        while (tok.hasMoreTokens()) {
            if (state == 9) {
                return badDate("Characters after the end", s);
            }
            String delim = tok.nextToken();
            if (".".equals(delim)) {
                if (state != 0) {
                    return badDate("Decimal separator occurs twice", s);
                }
                if (!tok.hasMoreTokens()) {
                    return badDate("Decimal point must be followed by digits", s);
                }
                part = tok.nextToken();
                if (!part.matches("^[0-9]+$")) {
                    return badDate("Non-numeric fractional seconds component", s);
                }
                BigDecimal fractionalSeconds = new BigDecimal("0." + part).stripTrailingZeros();
                if (hours == 24 && fractionalSeconds.signum() != 0) {
                    return badDate("If hours is 24, fractional seconds must be 0", s);
                }
                seconds = seconds.add(fractionalSeconds);
                state = 1;
            } else if ("Z".equals(delim)) {
                if (state > 1) {
                    return badDate("Z cannot occur here", s);
                }
                tz = 0;
                state = 9;  // we've finished
            } else if ("+".equals(delim) || "-".equals(delim)) {
                if (state > 1) {
                    return badDate(delim + " cannot occur here", s);
                }
                state = 2;
                if (!tok.hasMoreTokens()) {
                    return badDate("Missing timezone", s);
                }
                part = tok.nextToken();
                if (part.length() != 2) {
                    return badDate("Timezone hour must be two digits", s);
                }
                value = DurationValue.simpleInteger(part);
                if (value < 0) {
                    return badDate("Non-numeric timezone hour component", s);
                }
                if (value > 14) {
                    return badDate("Timezone is out of range (-14:00 to +14:00)", s);
                }
                tzHours = (int) value;

                if ("-".equals(delim)) {
                    negativeTz = true;
                }

            } else if (":".equals(delim)) {
                if (state != 2) {
                    return badDate("Misplaced ':'", s);
                }
                state = 9;
                part = tok.nextToken();
                value = DurationValue.simpleInteger(part);
                if (value < 0) {
                    return badDate("Non-numeric timezone minute component", s);
                }
                if (part.length() != 2) {
                    return badDate("Timezone minute must be two digits", s);
                }
                if (value > 59) {
                    return badDate("Timezone minute is out of range", s);
                }
                tz = tzHours * 60 + (int)value;
                if (tz > 14 * 60) {
                    return badDate("Timezone is out of range (-14:00 to +14:00)", s);
                }
                if (negativeTz) {
                    tz = -tz;
                }
            } else {
                return badDate("Timezone format is incorrect", s);
            }
        }

        if (state == 2 || state == 3) {
            return badDate("Timezone incomplete", s);
        }

        // Check that this is a valid calendar date
        if (!DateValue.isValidDate(year, month, day)) {
            return badDate("Non-existent date", s);
        }

        // Adjust midnight to 00:00:00 on the next day
        if (hours == 24) {
            DateValue t = DateValue.tomorrow(year, month, day);
            year = t.getYear();
            month = t.getMonth();
            day = t.getDay();
            hours = 0;
        }

        return new DateTimeValue(year, month, day, hours, minutes, seconds, tz);
    }

    /**
     * Creates an instance of DateTimeValue.  Includes validation
     * checks.  If a validation error is detected, an instance of
     * ValidationFailure will be returned instead.
     *
     * @param year - number of a year in the Gregorian calendar. Year zero is accepted.
     * @param month - number of a month within the specified year
     * @param day - number of a day within the specified month
     * @param hour - hour within the specified day
     * @param minute - minute within the hour specified
     * @param seconds - second within the minute specified
     * @param tzMinutes - number of minutes to adjust by for the timezone
     * @return - an instance of DateTimeValue or ValidationFailure
     */
    public static ConversionResult makeDateTimeValue(int year, int month, int day, int hour, int minute, BigDecimal seconds, int tzMinutes) {
        if (!GDateValue.isValidDate(year, month, day)) {
            return new ValidationFailure("Invalid date " + year + "-" + month + "-" + day);
        }
        if (!TimeValue.isValidTime(hour, minute, seconds)) {
            return new ValidationFailure("Invalid time " + hour + ":" + minute + ":" + seconds);
        }
        if (!GDateValue.isValidTimezone(tzMinutes)) {
            return new ValidationFailure("Invalid time zone " + tzMinutes, "FODT0003");
        }
        return new DateTimeValue(year, month, day, hour, minute, seconds, tzMinutes);
    }

    public static ConversionResult makeDateTimeValue(int year, int month, int day, int hour, int minute, BigDecimal seconds) {
        return DateTimeValue.makeDateTimeValue(year, month, day, hour, minute, seconds, CalendarValue.NO_TIMEZONE);
    }

    /**
     * Factory method: create a dateTime value from a supplied string, in ISO 8601 format, allowing
     * a year value of 0 to represent the year before year 1 (that is, following the XSD 1.1 rules).
     * <p>The {@code hasNoYearZero} property in the result is set to false.</p>
     *
     * @param s     a string in the lexical space of xs:dateTime
     * @return a DateTimeValue representing the xs:dateTime supplied, including a timezone offset if
     * present in the lexical representation
     * @throws DateTimeParseException if the format of the supplied string is invalid.
     * @since 9.9
     */

    public static DateTimeValue parse(UnicodeString s) throws DateTimeParseException {
        ConversionResult result = makeDateTimeValue(s, ConversionRules.DEFAULT);
        if (result instanceof ValidationFailure) {
            throw new DateTimeParseException(((ValidationFailure) result).getMessage(), s.toString(), 0);
        } else {
            return (DateTimeValue)result;
        }
    }

    private static ValidationFailure badDate(String msg, UnicodeString value) {
        ValidationFailure err = new ValidationFailure(
                "Invalid dateTime value " + Err.wrap(value, Err.VALUE) + " (" + msg + ")"
        );
        err.setErrorCode("FORG0001");
        return err;
    }

    private static ValidationFailure badDate(String msg, UnicodeString value, String errorCode) {
        ValidationFailure err = new ValidationFailure(
                "Invalid dateTime value " + Err.wrap(value, Err.VALUE) + " (" + msg + ")"
        );
        err.setErrorCode(errorCode);
        return err;
    }


    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    /*@NotNull*/
    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.DATE_TIME;
    }

    /**
     * Get the year component, in its internal form (which allows a year zero)
     *
     * @return the year component
     */

    public int getYear() {
        return year;
    }

    /**
     * Get the month component, 1-12
     *
     * @return the month component
     */

    public int getMonth() {
        return month;
    }

    /**
     * Get the day component, 1-31
     *
     * @return the day component
     */

    public int getDay() {
        return day;
    }

    /**
     * Get the hour component, 0-23
     *
     * @return the hour component (never 24, even if the input was specified as 24:00:00)
     */

    public int getHour() {
        return hour;
    }

    /**
     * Get the minute component, 0-59
     *
     * @return the minute component
     */

    public int getMinute() {
        return minute;
    }

    /**
     * Get the second component, 0-59
     *
     * @return the second component
     */

    public BigDecimal getSecond() {
        return seconds;
    }

    /**
     * Convert the value to an xs:dateTime, retaining all the components that are actually present, and
     * substituting conventional values for components that are missing. (This method does nothing in
     * the case of xs:dateTime, but is there to implement a method in the {@link CalendarValue} interface).
     *
     * @return the value as an xs:dateTime
     */

    /*@NotNull*/
    @Override
    public DateTimeValue toDateTime() {
        return this;
    }

    /**
     * Check that the value can be handled in SaxonJS
     *
     * @throws XPathException if it can't be handled in SaxonJS
     */

    @Override
    public void checkValidInJavascript() throws XPathException {
        if (year <= 0 || year > 9999) {
            throw new XPathException("Year out of range for SaxonJS", "FODT0001");
        }
    }

    /**
     * Normalize the date and time to be in timezone Z.
     *
     * @param implicitTimezone used to supply the implicit timezone, used when the value has
     *           no explicit timezone
     * @return in general, a new DateTimeValue in timezone Z, representing the same instant in time.
     *         Returns the original DateTimeValue if this is already in timezone Z.
     * @throws NoDynamicContextException if the implicit timezone is needed and is CalendarValue.MISSING_TIMEZONE
     * or CalendarValue.NO_TIMEZONE
     */

    /*@NotNull*/
    public DateTimeValue adjustToUTC(int implicitTimezone) throws NoDynamicContextException {
        if (hasTimezone()) {
            return adjustTimezone(0);
        } else {
            if (implicitTimezone == CalendarValue.MISSING_TIMEZONE || implicitTimezone == CalendarValue.NO_TIMEZONE) {
                throw new NoDynamicContextException("DateTime operation needs access to implicit timezone");
            }
            return new DateTimeValue(year, month, day, hour, minute, seconds, implicitTimezone).adjustTimezone(0);
        }
    }

    /**
     * Get the Julian instant: a decimal value whose integer part is the Julian day number
     * multiplied by the number of seconds per day,
     * and whose fractional part is the fraction of the second.
     * This method operates on the local time, ignoring the timezone. The caller should call normalize()
     * before calling this method to get a normalized time.
     *
     * @return the Julian instant corresponding to this xs:dateTime value
     */

    public BigDecimal toJulianInstant() {
        int julianDay = DateValue.getJulianDayNumber(year, month, day);
        long julianSecond = julianDay * 24L * 60L * 60L;
        julianSecond += ((hour * 60L + minute) * 60L);
        return BigDecimal.valueOf(julianSecond).add(seconds);
    }

    /**
     * Get the DateTimeValue corresponding to a given Julian instant
     *
     * @param instant the Julian instant: a decimal value whose integer part is the Julian day number
     *                multiplied by the number of seconds per day, and whose fractional part is the fraction of the second.
     * @return the xs:dateTime value corresponding to the Julian instant. This will always be in timezone Z.
     * @throws XPathException if the result is out of range
     */

    /*@NotNull*/
    public static DateTimeValue fromJulianInstant(BigDecimal instant, int tzMinutes) throws XPathException {
        BigInteger julianSecond = instant.toBigInteger();
        if (julianSecond.abs().compareTo(BigInteger.valueOf((long)Integer.MAX_VALUE * 24*60*60)) > 0) {
            throw new XPathException("xs:dateTime value out of range", "FODT0001");
        }
        BigDecimal nanoseconds = instant.subtract(new BigDecimal(julianSecond)).multiply(BigDecimalValue.ONE_BILLION);
        long js = julianSecond.longValue();
        long jd = js / (24L * 60L * 60L);
        DateValue date = DateValue.dateFromJulianDayNumber((int) jd);
        js = js % (24L * 60L * 60L);
        byte hour = (byte) (js / (60L * 60L));
        js = js % (60L * 60L);
        byte minute = (byte) (js / 60L);
        js = js % 60L;
        return new DateTimeValue(date.getYear(), date.getMonth(), date.getDay(),
                hour, minute, makeSeconds((int)js, nanoseconds.intValue()), tzMinutes, BuiltInAtomicType.DATE_TIME);

    }

    /**
     * Generate a value from the current date and time that can be used to seed a random number generator
     * @return a long derived arbitrarily from the current date and time
     */
    @CSharpReplaceBody(code="return minute*360 + getSecond().GetHashCode();")
    public long randomSeed() {
        return getCalendar().getTimeInMillis();
    }

    
    /**
     * Get a Java Calendar object representing the value of this DateTime. This will respect the timezone
     * if there is one (provided the timezone is within the range supported by the {@code GregorianCalendar}
     * class, which in practice means that it is not -14:00). If there is no timezone or if
     * the timezone is out of range, the result will be in GMT.
     *
     * @return a Java GregorianCalendar object representing the value of this xs:dateTime value.
     */

    /*@NotNull*/
    @Override
    public GregorianCalendar getCalendar() {
        int tz = hasTimezone() ? getTimezoneInMinutes() * 60000 : 0;
        TimeZone zone = new SimpleTimeZone(tz, "LLL");
        GregorianCalendar calendar = new GregorianCalendar(zone);
        if (tz < calendar.getMinimum(Calendar.ZONE_OFFSET) || tz > calendar.getMaximum(Calendar.ZONE_OFFSET)) {
            return adjustTimezone(0).getCalendar();
        }
        calendar.setGregorianChange(new Date(Long.MIN_VALUE));
        calendar.setLenient(false);
        int yr = year;
        if (year <= 0) {
            yr = -year;
            calendar.set(Calendar.ERA, GregorianCalendar.BC);
        }
        //noinspection MagicConstant
        int[] parts = splitSeconds(seconds);
        calendar.set(yr, month - 1, day, hour, minute, parts[0]);
        calendar.set(Calendar.MILLISECOND, parts[1] / 1_000_000);   // loses precision unavoidably
        calendar.set(Calendar.ZONE_OFFSET, tz);
        calendar.set(Calendar.DST_OFFSET, 0);
        return calendar;
    }

    /**
     * Get a Java 8 {@link Instant} corresponding to this date and time. The value will respect the time zone
     * offset if present, or will assume UTC otherwise.
     * @return an {@code Instant} representing this date and time
     */

    public Instant toJavaInstant() {
        return Instant.from(this);
    }

    /**
     * Get a Java 8 {@link ZonedDateTime} corresponding to this date and time. The value will respect the time zone
     * offset if present, or will assume UTC otherwise.
     * @return a {@code ZonedDateTime} representing this date and time, including its timezone if present, or
     * interpreted as a UTC date/time otherwise.
     * @since 9.9
     */

    public ZonedDateTime toZonedDateTime() {
        if (hasTimezone()) {
            return ZonedDateTime.from(this);
        } else {
            return ZonedDateTime.from(adjustToUTC(0));
        }
    }

    /**
     * Get a Java 8 {@link OffsetDateTime} corresponding to this date and time. The value will respect the time zone
     * offset if present, or will assume UTC otherwise.
     *
     * @return a {@code OffsetDateTime} representing this date and time, including its timezone if present, or
     * interpreted as a UTC date/time otherwise.
     * @since 9.9
     */

    public OffsetDateTime toOffsetDateTime() {
        if (hasTimezone()) {
            return OffsetDateTime.from(this);
        } else {
            return OffsetDateTime.from(adjustToUTC(0));
        }
    }

    /**
     * Get a Java 8 {@link LocalDateTime} corresponding to this date and time. The value will ignore any timezone
     * offset present in this value.
     * @return a {@code LocalDateTime} equivalent to this date and time, discarding any time zone offset if present.
     * @since 9.9
     */

    public LocalDateTime toLocalDateTime() {
        return LocalDateTime.from(this);
    }


    /**
     * Convert to string
     *
     * @return ISO 8601 representation. The value returned is the localized representation:
     *         that is it uses the timezone contained within the value itself. In the case
     *         of a year earlier than year 1, the value output is the internally-held year,
     *         unless the {@code hasNoYearZero} flag is set, in which case it is the
     *         internal year minus one.
     */

    /*@NotNull*/
    @Override
    public UnicodeString getPrimitiveStringValue() {

        TwineBuilder tb = TwineBuilder.make(32);
        int yr = year;
        if (year <= 0) {
            yr = -yr;
            if (yr != 0) {
                tb = tb.append('-');
            }
        }
        tb = appendString(tb, yr, yr > 9999 ? (yr + "").length() : 4)
                .append('-');
        tb = appendTwoDigits(tb, month).append('-');
        tb = appendTwoDigits(tb, day).append('T');
        tb = appendTwoDigits(tb, hour).append(':');
        tb = appendTwoDigits(tb, minute).append(':');

        tb = formatSeconds(seconds, tb);

        if (hasTimezone()) {
            tb = appendTimezone(tb);
        }

        return tb.toUnicodeString();

    }

    /**
     * Extract the Date part
     *
     * @return a DateValue representing the date part of the dateTime, retaining the timezone or its absence
     */

    /*@NotNull*/
    public DateValue toDateValue() {
        return new DateValue(year, month, day, getTimezoneInMinutes());
    }

    /**
     * Extract the Time part
     *
     * @return a TimeValue representing the date part of the dateTime, retaining the timezone or its absence
     */

    /*@NotNull*/
    public TimeValue toTimeValue() {
        return new TimeValue(hour, minute, seconds, getTimezoneInMinutes(), BuiltInAtomicType.TIME);
    }


    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules. For an xs:dateTime it is the
     * date/time adjusted to UTC.
     *
     * @return the canonical lexical representation as defined in XML Schema
     */

    @Override
    public UnicodeString getCanonicalLexicalRepresentation() {
        if (hasTimezone() && getTimezoneInMinutes() != 0) {
            return adjustTimezone(0).getUnicodeStringValue();
        } else {
            return this.getUnicodeStringValue();
        }
    }

    /**
     * Make a copy of this date, time, or dateTime value, but with a new type label
     *
     * @param metadata the type label to be attached to the new copy. It is the caller's responsibility
     *                  to ensure that the value actually conforms to the rules for this type.
     */

    /*@NotNull*/
    @Override
    public DateTimeValue withMetadata(AtomicMetadata metadata) {
        return new DateTimeValue(year, month, day, hour, minute, seconds, tzMinutes, metadata);
    }

    /**
     * Return a new dateTime with the same normalized value, but
     * in a different timezone.
     *
     * @param timezone the new timezone offset, in minutes
     * @return the date/time in the new timezone. This will be a new DateTimeValue unless no change
     *         was required to the original value
     */

    /*@NotNull*/
    @Override
    public DateTimeValue adjustTimezone(int timezone) {
        if (!hasTimezone() || timezone == NO_TIMEZONE) {
            return new DateTimeValue(year, month, day, hour, minute, seconds, timezone);
        }
        int oldtz = getTimezoneInMinutes();
        if (oldtz == timezone) {
            return this;
        }
        int tz = timezone - oldtz;
        int h = hour;
        int mi = minute;
        mi += tz;
        if (mi < 0 || mi > 59) {
            h += (int)Math.floor(mi / 60.0);
            mi = (mi + 60 * 24) % 60;
        }

        if (h >= 0 && h < 24) {
            return new DateTimeValue(year, month, day, h, mi, seconds, timezone);
        }

        // Following code is designed to handle the corner case of adjusting from -14:00 to +14:00 or
        // vice versa, which can cause a change of two days in the date
        DateTimeValue dt = this;
        while (h < 0) {
            h += 24;
            DateValue t = DateValue.yesterday(dt.getYear(), dt.getMonth(), dt.getDay());
            dt = new DateTimeValue(t.getYear(), t.getMonth(), t.getDay(),
                    h, mi, seconds, timezone, BuiltInAtomicType.DATE_TIME);
        }
        if (h > 23) {
            h -= 24;
            DateValue t = DateValue.tomorrow(year, month, day);
            dt = new DateTimeValue(t.getYear(), t.getMonth(), t.getDay(),
                                   h, mi, seconds,
                                   timezone, BuiltInAtomicType.DATE_TIME);
        }
        return dt;
    }

    /**
     * Add a duration to a dateTime
     *
     * @param duration the duration to be added (possibly negative)
     * @return the new date
     * @throws net.sf.saxon.trans.XPathException
     *          if the duration is an xs:duration, as distinct from
     *          a subclass thereof
     */

    /*@NotNull*/
    @Override
    public DateTimeValue add(DurationValue duration) throws XPathException {
        if (duration instanceof DayTimeDurationValue) {
            BigDecimal seconds = duration.getTotalSeconds();
            BigDecimal julian = toJulianInstant();
            julian = julian.add(seconds);
            return fromJulianInstant(julian, getTimezoneInMinutes());
        } else if (duration instanceof YearMonthDurationValue) {
            int months = ((YearMonthDurationValue) duration).getLengthInMonths();
            int m = (month - 1) + months;
            int y = year + m / 12;
            m = m % 12;
            if (m < 0) {
                m += 12;
                y -= 1;
            }
            m++;
            int d = day;
            while (!DateValue.isValidDate(y, m, d)) {
                d -= 1;
            }
            return new DateTimeValue(y, m, d,
                    hour, minute, seconds, getTimezoneInMinutes(), BuiltInAtomicType.DATE_TIME);
        } else {
            throw new XPathException("DateTime arithmetic is not supported on xs:duration, only on its subtypes")
                    .withErrorCode("XPTY0004").asTypeError();
        }
    }

    /**
     * Determine the difference between two points in time, as a duration
     *
     * @param other   the other point in time
     * @param context the XPath dynamic context
     * @return the duration as an xs:dayTimeDuration
     * @throws net.sf.saxon.trans.XPathException
     *          for example if one value is a date and the other is a time
     */

    @Override
    public DayTimeDurationValue subtract(/*@NotNull*/ CalendarValue other, XPathContext context) throws XPathException {
        if (!(other instanceof DateTimeValue)) {
            throw new XPathException("First operand of '-' is a dateTime, but the second is not")
                    .withErrorCode("XPTY0004").asTypeError();
        }
        return super.subtract(other, context);
    }

    public BigDecimal secondsSinceEpoch() {
        DateTimeValue dtv = adjustToUTC(0);
        BigDecimal d1 = dtv.toJulianInstant();
        BigDecimal d2 = EPOCH.toJulianInstant();
        return d1.subtract(d2);
    }

    /**
     * Get a component of the value. Returns null if the timezone component is
     * requested and is not present.
     * @param component identifies the required component
     */

    /*@Nullable*/
    @Override
    public AtomicValue getComponent(AccessorFn.Component component) throws XPathException {
        switch (component) {
            case YEAR:
                return Int64Value.makeIntegerValue(year);
            case MONTH:
                return Int64Value.makeIntegerValue(month);
            case DAY:
                return Int64Value.makeIntegerValue(day);
            case HOURS:
                return Int64Value.makeIntegerValue(hour);
            case MINUTES:
                return Int64Value.makeIntegerValue(minute);
            case SECONDS:
                return new BigDecimalValue(seconds);
            case WHOLE_SECONDS: //(internal use only)
                return Int64Value.makeIntegerValue(splitSeconds(seconds)[0]);
            case TIMEZONE:
                if (hasTimezone()) {
                    return DayTimeDurationValue.fromMilliseconds(60000L * getTimezoneInMinutes());
                } else {
                    return null;
                }
            default:
                throw new IllegalArgumentException("Unknown component for dateTime: " + component);
        }
    }


    @Override
    public boolean isSupported(TemporalField field) {
        if (field.equals(ChronoField.OFFSET_SECONDS)) {
            return getTimezoneInMinutes() != NO_TIMEZONE;
        } else if (field instanceof ChronoField) {
            return true;
        } else {
            return field.isSupportedBy(this);
        }
    }

    /**
     * Gets the value of the specified field as a {@code long}.
     * <p>This queries the date-time for the value of the specified field.
     * The returned value may be outside the valid range of values for the field.
     * If the date-time cannot return the value, because the field is unsupported or for
     * some other reason, an exception will be thrown.</p>
     *
     * <p>The specification requires some fields (for example {@link ChronoField#EPOCH_DAY} to
     * reflect the local time. The Saxon implementation does not have access to the local time,
     * so it adjusts to UTC instead.</p>
     *
     * @param field the field to get, not null
     * @return the value for the field
     * @throws DateTimeException                if a value for the field cannot be obtained
     * @throws UnsupportedTemporalTypeException if the field is not supported
     * @throws ArithmeticException              if numeric overflow occurs
     * <p>Note: Implementations must check and handle all fields defined in {@link ChronoField}.
     * If the field is supported, then the value of the field must be returned.
     * If unsupported, then an {@code UnsupportedTemporalTypeException} must be thrown. </p>
     * <p>If the field is not a {@code ChronoField}, then the result of this method
     * is obtained by invoking {@code TemporalField.getFrom(TemporalAccessor)}
     * passing {@code this} as the argument.</p>
     * <p>Implementations must ensure that no observable state is altered when this
     * read-only method is invoked.</p>
     */

    @Override
    public long getLong(TemporalField field) {
        if (field instanceof ChronoField) {
            int[] split = splitSeconds(seconds);
            switch ((ChronoField) field) {
                case NANO_OF_SECOND:
                    return splitSeconds(seconds)[1];
                case NANO_OF_DAY:
                    return (hour * 3600 + minute * 60 + split[0]) * 1_000_000_000L + split[1];
                case MICRO_OF_SECOND:
                    return splitSeconds(seconds)[1] / 1000;
                case MICRO_OF_DAY:
                    return (hour * 3600 + minute * 60 + split[0]) * 1_000_000L + (split[1] / 1000);
                case MILLI_OF_SECOND:
                    return splitSeconds(seconds)[1] / 1_000_000;
                case MILLI_OF_DAY:
                    return (hour * 3600 + minute * 60 + split[0])*1000L + split[1] / 1_000_000;
                case SECOND_OF_MINUTE:
                    return splitSeconds(seconds)[0];
                case SECOND_OF_DAY:
                    return hour * 3600 + minute*60 + split[0];
                case MINUTE_OF_HOUR:
                    return minute;
                case MINUTE_OF_DAY:
                    return hour * 60 + minute;
                case HOUR_OF_AMPM:
                    return hour%12;
                case CLOCK_HOUR_OF_AMPM:
                    return (hour+11)%12 + 1;
                case HOUR_OF_DAY:
                    return hour;
                case CLOCK_HOUR_OF_DAY:
                    return (hour+23)%24 + 1; 
                case AMPM_OF_DAY:
                    return hour/12; // specification is unclear about noon and midnight
                case DAY_OF_WEEK:
                    return DateValue.getDayOfWeek(year, month, day);
                case ALIGNED_DAY_OF_WEEK_IN_MONTH:
                    return (day - 1) % 7 + 1;
                case ALIGNED_DAY_OF_WEEK_IN_YEAR:
                    return (DateValue.getDayWithinYear(year, month, day) - 1) % 7 + 1;
                case DAY_OF_MONTH:
                    return day;
                case DAY_OF_YEAR:
                    return DateValue.getDayWithinYear(year, month, day);
                case EPOCH_DAY:
                    BigDecimal secs = secondsSinceEpoch();
                    long days = secondsSinceEpoch().longValue() / (24*60*60);
                    return secs.signum() < 0 ? days-1 : days;
                case ALIGNED_WEEK_OF_MONTH:
                    return (day - 1) / 7 + 1;
                case ALIGNED_WEEK_OF_YEAR:
                    return (DateValue.getDayWithinYear(year, month, day) - 1) / 7 + 1;
                case MONTH_OF_YEAR:
                    return month;
                case PROLEPTIC_MONTH:
                    return (long)year*12 + month - 1;
                case YEAR_OF_ERA:
                    return Math.abs(year) + (year<0 ? 1 : 0);
                case YEAR:
                    return year;
                case ERA:
                    return year<0 ? 0 : 1;
                case INSTANT_SECONDS:
                    return secondsSinceEpoch().setScale(0, RoundingMode.FLOOR).longValue();
                case OFFSET_SECONDS:
                    int tz = getTimezoneInMinutes();
                    if (tz == NO_TIMEZONE) {
                        throw new UnsupportedTemporalTypeException("xs:dateTime value has no timezone");
                    } else {
                        return (long)tz * 60;
                    }
                default:
                    throw new UnsupportedTemporalTypeException(field.toString());
            }
        } else {
            return field.getFrom(this);
        }
    }


    /**
     * Compare the value to another dateTime value, following the XPath comparison semantics
     *
     * @param other   The other dateTime value
     * @param implicitTimezone The implicit timezone to be used for a value with no timezone
     * @return negative value if this one is the earler, 0 if they are chronologically equal,
     *         positive value if this one is the later. For this purpose, dateTime values with an unknown
     *         timezone are considered to be values in the implicit timezone (the Comparable interface requires
     *         a total ordering).
     * @throws ClassCastException        if the other value is not a DateTimeValue (the parameter
     *                                   is declared as CalendarValue to satisfy the interface)
     * @throws NoDynamicContextException if the implicit timezone is needed and is not available
     */

    @Override
    public int compareTo(CalendarValue other, int implicitTimezone) throws NoDynamicContextException {
        if (!(other instanceof DateTimeValue v2)) {
            throw new ClassCastException("DateTime values are not comparable to " + other.getClass());
        }
        if (getTimezoneInMinutes() == v2.getTimezoneInMinutes()) {
            // both values are in the same timezone (explicitly or implicitly)
            if (year != v2.year) {
                return IntegerValue.signum(year - v2.year);
            }
            if (month != v2.month) {
                return IntegerValue.signum(month - v2.month);
            }
            if (day != v2.day) {
                return IntegerValue.signum(day - v2.day);
            }
            if (hour != v2.hour) {
                return IntegerValue.signum(hour - v2.hour);
            }
            if (minute != v2.minute) {
                return IntegerValue.signum(minute - v2.minute);
            }
            if (!seconds.equals(v2.seconds)) {
                return seconds.subtract(v2.seconds).signum();
            }
            return 0;
        }
        return adjustToUTC(implicitTimezone).compareTo(v2.adjustToUTC(implicitTimezone), implicitTimezone);
    }

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone, int specVersion) throws NoDynamicContextException {
        if (hasTimezone()) {
            return this;
        } else if (implicitTimezone == MISSING_TIMEZONE) {
            throw new NoDynamicContextException("Unknown implicit timezone");
        } else {
            return adjustTimezone(implicitTimezone);
        }
    }

    /**
     * Context-free comparison of two DateTimeValue values. For this to work,
     * the two values must either both have a timezone or both have none.
     *
     * @param v2 the other value
     * @return the result of the comparison: -1 if the first is earlier, 0 if they
     *         are equal, +1 if the first is later
     * @throws ClassCastException if the values are not comparable (which might be because
     *                            no timezone is available)
     */

    @Override
    public int compareTo(XPathComparable v2) {
        if (v2 instanceof DateTimeValue) {
            try {
                return compareTo((DateTimeValue)v2, MISSING_TIMEZONE);
            } catch (Exception err) {
                throw new ClassCastException("DateTime comparison requires access to implicit timezone");
            }
        } else {
            throw new ClassCastException("Cannot compare xs:dateTime with " + v2.toString());
        }
    }

    /*@NotNull*/
    public DateTimeComparable getSchemaComparable() {
        return new DateTimeComparable(this);
    }

    /**
     * DateTimeComparable is an object that implements the XML Schema rules for comparing date/time values
     */

    public static class DateTimeComparable implements Comparable<DateTimeComparable> {

        private final DateTimeValue value;
        public DateTimeComparable(DateTimeValue value) {
            this.value = value;
        }
        
        // Rules from XML Schema Part 2
        @Override
        public int compareTo(DateTimeComparable o) {
            DateTimeValue dt0 = value;
            DateTimeValue dt1 = o.value;
            if (dt0.hasTimezone()) {
                if (dt1.hasTimezone()) {
                    dt0 = dt0.adjustTimezone(0);
                    dt1 = dt1.adjustTimezone(0);
                    return dt0.compareTo(dt1);
                } else {
                    DateTimeValue dt1max = dt1.adjustTimezone(14 * 60);
                    if (dt0.compareTo(dt1max) < 0) {
                        return -1;
                    }
                    DateTimeValue dt1min = dt1.adjustTimezone(-14 * 60);
                    if (dt0.compareTo(dt1min) > 0) {
                        return +1;
                    }
                    return SequenceTool.INDETERMINATE_ORDERING;
                }
            } else {
                if (dt1.hasTimezone()) {
                    DateTimeValue dt0min = dt0.adjustTimezone(-14 * 60);
                    if (dt0min.compareTo(dt1) < 0) {
                        return -1;
                    }
                    DateTimeValue dt0max = dt0.adjustTimezone(14 * 60);
                    if (dt0max.compareTo(dt1) > 0) {
                        return +1;
                    }
                    return SequenceTool.INDETERMINATE_ORDERING;
                } else {
                    dt0 = dt0.adjustTimezone(0);
                    dt1 = dt1.adjustTimezone(0);
                    return dt0.compareTo(dt1);
                }
            }
        }

        public boolean equals(/*@NotNull*/ Object o) {
            return o instanceof DateTimeComparable &&
                    value.hasTimezone() == ((DateTimeComparable) o).value.hasTimezone() &&
                    compareTo((DateTimeComparable) o) == 0;
        }

        public int hashCode() {
            DateTimeValue dt0 = value.adjustTimezone(0);
            return (dt0.year << 20) ^ (dt0.month << 16) ^ (dt0.day << 11) ^
                    (dt0.hour << 7) ^ (dt0.minute << 2) ^ (dt0.seconds.hashCode());
        }
    }

    /**
     * Context-free comparison of two dateTime values
     *
     * @param o the other date time value
     * @return true if the two values represent the same instant in time. Return false if one value has
     * a timezone and the other does not (this is the result needed when using keys in a map, and also the
     * result needed for XSD comparisons for example in enumeration facets and identity constraints)
     */

    public boolean equals(Object o) {
        return o instanceof DateTimeValue && compareTo((DateTimeValue)o) == 0;
    }

    /**
     * Hash code for context-free comparison of date time values. Note that equality testing
     * and therefore hashCode() works only for values with a timezone
     *
     * @return a hash code
     */

    public int hashCode() {
        return computeHashCode(year, month, day, hour, minute, seconds, getTimezoneInMinutes());
    }

    public static int computeHashCode(int year, int month, int day, int hour, int minute, BigDecimal second, int tzMinutes) {
        int tz = tzMinutes == CalendarValue.NO_TIMEZONE ? 0 : -tzMinutes;
        int h = hour;
        int mi = minute;
        mi += tz;
        if (mi < 0 || mi > 59) {
            h += (int)Math.floor(mi / 60.0);
            mi = (mi + 60 * 24) % 60;
        }
        while (h < 0) {
            h += 24;
            DateValue t = DateValue.yesterday(year, month, day);
            year = t.getYear();
            month = t.getMonth();
            day = t.getDay();
        }
        while (h > 23) {
            h -= 24;
            DateValue t = DateValue.tomorrow(year, month, day);
            year = t.getYear();
            month = t.getMonth();
            day = t.getDay();
        }
        return (year << 4) ^ (month << 28) ^ (day << 23) ^ (h << 18) ^ (mi << 13) ^ second.hashCode();

    }

}

