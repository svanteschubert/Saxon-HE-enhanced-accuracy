////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
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
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.ValidationFailure;

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
        + "    Saxon.Hej.value.DateTimeValue dtv = new (dt.Year, (byte)dt.Month, (byte)dt.Day, (byte)dt.Hour, (byte)dt.Minute, (byte)dt.Second, dt.Millisecond * 1000000, 0);"
        + "    return (Saxon.Hej.value.DateTimeValue)dtv.copyAsSubType(Saxon.Hej.type.BuiltInAtomicType.DATE_TIME_STAMP);"
        + "}"
        + " public static Saxon.Hej.value.DateTimeValue fromDateTimeOffset(System.DateTimeOffset dt) {"
        + "    Saxon.Hej.value.DateTimeValue dtv = new (dt.Year, (byte)dt.Month, (byte)dt.Day, (byte)dt.Hour, (byte)dt.Minute, (byte)dt.Second, dt.Millisecond * 1000000, (int)dt.Offset.TotalMinutes);"
        + "    return (Saxon.Hej.value.DateTimeValue)dtv.copyAsSubType(Saxon.Hej.type.BuiltInAtomicType.DATE_TIME_STAMP);"
        + "}"
})

public final class DateTimeValue extends CalendarValue
        implements XPathComparable
        , TemporalAccessor
{

    private final int year;       // the year as written, +1 for BC years
    private final byte month;     // the month as written, range 1-12
    private final byte day;       // the day as written, range 1-31
    private final byte hour;      // the hour as written (except for midnight), range 0-23
    private final byte minute;    // the minutes as written, range 0-59
    private final byte second;    // the seconds as written, range 0-59 (no leap seconds)
    private final int nanosecond; // the number of nanoseconds within the current second
    private final boolean hasNoYearZero;  // true if XSD 1.0 rules apply for negative years

    public DateTimeValue(int year, byte month, byte day, byte hour, byte minute, byte second,
                         int nanosecond, boolean hasNoYearZero, int tzMinutes, AtomicType typeLabel) {
        super(typeLabel, tzMinutes);
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.nanosecond = nanosecond;
        this.hasNoYearZero = hasNoYearZero;
    }

    @CSharpModifiers(code={"private"})
    protected static class MutableDateTimeValue {
        public int year;       // the year as written, +1 for BC years
        public byte month;     // the month as written, range 1-12
        public byte day;       // the day as written, range 1-31
        public byte hour;      // the hour as written (except for midnight), range 0-23
        public byte minute;    // the minutes as written, range 0-59
        public byte second;    // the seconds as written, range 0-59 (no leap seconds)
        public int nanosecond; // the number of nanoseconds within the current second
        public boolean hasNoYearZero;  // true if XSD 1.0 rules apply for negative years
        public int tzMinutes = NO_TIMEZONE;
        public AtomicType typeLabel = BuiltInAtomicType.DATE_TIME;
    }

    private MutableDateTimeValue makeMutableCopy() {
        MutableDateTimeValue m = new MutableDateTimeValue();
        m.year = year;
        m.month = month;
        m.day = day;
        m.hour = hour;
        m.minute = minute;
        m.second = second;
        m.nanosecond = nanosecond;
        m.hasNoYearZero = hasNoYearZero;
        m.tzMinutes = getTimezoneInMinutes();
        m.typeLabel = typeLabel;
        return m;
    }

    private static DateTimeValue fromMutableCopy(MutableDateTimeValue m) {
        return new DateTimeValue(m.year, m.month, m.day, m.hour, m.minute, m.second,
                                 m.nanosecond, m.hasNoYearZero, m.tzMinutes, m.typeLabel);
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
    public static DateTimeValue getCurrentDateTime(/*@Nullable*/ XPathContext context) {
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

    public static DateTimeValue fromCalendar(/*@NotNull*/ Calendar calendar, boolean tzSpecified) {
        MutableDateTimeValue m = new MutableDateTimeValue();
        int era = calendar.get(GregorianCalendar.ERA);
        m.year = calendar.get(Calendar.YEAR);
        if (era == GregorianCalendar.BC) {
            m.year = 1 - m.year;
        }
        m.month = (byte) (calendar.get(Calendar.MONTH) + 1);
        m.day = (byte) calendar.get(Calendar.DATE);
        m.hour = (byte) calendar.get(Calendar.HOUR_OF_DAY);
        m.minute = (byte) calendar.get(Calendar.MINUTE);
        m.second = (byte) calendar.get(Calendar.SECOND);
        m.nanosecond = calendar.get(Calendar.MILLISECOND) * 1_000_000;
        if (tzSpecified) {
            m.tzMinutes = (calendar.get(Calendar.ZONE_OFFSET) +
                    calendar.get(Calendar.DST_OFFSET)) / 60_000;
        }
        m.typeLabel = BuiltInAtomicType.DATE_TIME;
        m.hasNoYearZero = true;
        return fromMutableCopy(m);
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
        return EPOCH
                .add(DayTimeDurationValue.fromSeconds(BigDecimal.valueOf(seconds))
                             .add(DayTimeDurationValue.fromNanoseconds(nano)));
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
        return new DateTimeValue(ldt.getYear(), (byte) ldt.getMonthValue(), (byte) ldt.getDayOfMonth(),
                                              (byte) ldt.getHour(), (byte) ldt.getMinute(), (byte) ldt.getSecond(),
                                              ldt.getNano(), false, tz, BuiltInAtomicType.DATE_TIME_STAMP);
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
        return new DateTimeValue(localDateTime.getYear(), (byte) localDateTime.getMonthValue(), (byte) localDateTime.getDayOfMonth(),
                                 (byte) localDateTime.getHour(), (byte) localDateTime.getMinute(), (byte) localDateTime.getSecond(),
                                 localDateTime.getNano(), false, NO_TIMEZONE, BuiltInAtomicType.DATE_TIME);
    }

    /**
     * Fixed date/time used by Java (and Unix) as the origin of the universe: 1970-01-01T00:00:00Z
     */

    /*@NotNull*/ public static final DateTimeValue EPOCH =
            new DateTimeValue(1970, (byte) 1, (byte) 1, (byte) 0, (byte) 0, (byte) 0, 0, 0, true);

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
    public static DateTimeValue makeDateTimeValue(/*@Nullable*/ DateValue date, /*@Nullable*/ TimeValue time) throws XPathException {
        if (date == null || time == null) {
            return null;
        }
        int tz1 = date.getTimezoneInMinutes();
        int tz2 = time.getTimezoneInMinutes();
        if (tz1 != NO_TIMEZONE && tz2 != NO_TIMEZONE && tz1 != tz2) {
            throw new XPathException("Supplied date and time are in different timezones", "FORG0008");
        }

        MutableDateTimeValue v = date.toDateTime().makeMutableCopy();
        v.hour = time.getHour();
        v.minute = time.getMinute();
        v.second = time.getSecond();
        v.nanosecond = time.getNanosecond();
        v.tzMinutes = Math.max(tz1, tz2);
        v.typeLabel = BuiltInAtomicType.DATE_TIME;
        v.hasNoYearZero = date.hasNoYearZero;
        return fromMutableCopy(v);
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
     * @param s     a string in the lexical space of xs:dateTime
     * @param rules the conversion rules to be used (determining whether year zero is allowed)
     * @return either a DateTimeValue representing the xs:dateTime supplied, or a ValidationFailure if
     *         the lexical value was invalid
     */

    /*@NotNull*/
    public static ConversionResult makeDateTimeValue(UnicodeString s, /*@NotNull*/ ConversionRules rules) {
        // input must have format [-]yyyy-mm-ddThh:mm:ss[.fff*][([+|-]hh:mm | Z)]
        MutableDateTimeValue dt = new MutableDateTimeValue();
        dt.hasNoYearZero = !rules.isAllowYearZero();
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
        int value = DurationValue.simpleInteger(part);
        if (value < 0) {
            if (value == -1) {
                return badDate("Non-numeric year component", s);
            } else {
                return badDate("Year is outside the range that Saxon can handle", s, "FODT0001");
            }
        }
        dt.year = value * era;
        if (part.length() < 4) {
            return badDate("Year is less than four digits", s);
        }
        if (part.length() > 4 && part.charAt(0) == '0') {
            return badDate("When year exceeds 4 digits, leading zeroes are not allowed", s);
        }
        if (dt.year == 0 && !rules.isAllowYearZero()) {
            return badDate("Year zero is not allowed", s);
        }
        if (era < 0 && !rules.isAllowYearZero()) {
            dt.year++;     // if year zero not allowed, -0001 is the year before +0001, represented as 0 internally.
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
        dt.month = (byte) value;
        if (dt.month < 1 || dt.month > 12) {
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
        part = (String) tok.nextToken();
        if (part.length() != 2) {
            return badDate("Day must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric day component", s);
        }
        dt.day = (byte) value;
        if (dt.day < 1 || dt.day > 31) {
            return badDate("Day is out of range", s);
        }

        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        if (!"T".equals(tok.nextToken())) {
            return badDate("Wrong delimiter after day", s);
        }

        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        part = tok.nextToken();
        if (part.length() != 2) {
            return badDate("Hour must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric hour component", s);
        }
        dt.hour = (byte) value;
        if (dt.hour > 24) {
            return badDate("Hour is out of range", s);
        }

        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        if (!":".equals(tok.nextToken())) {
            return badDate("Wrong delimiter after hour", s);
        }

        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        part = tok.nextToken();
        if (part.length() != 2) {
            return badDate("Minute must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric minute component", s);
        }
        dt.minute = (byte) value;
        if (dt.minute > 59) {
            return badDate("Minute is out of range", s);
        }
        if (dt.hour == 24 && dt.minute != 0) {
            return badDate("If hour is 24, minute must be 00", s);
        }
        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        if (!":".equals(tok.nextToken())) {
            return badDate("Wrong delimiter after minute", s);
        }

        if (!tok.hasMoreTokens()) {
            return badDate("Too short", s);
        }
        part = tok.nextToken();
        if (part.length() != 2) {
            return badDate("Second must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badDate("Non-numeric second component", s);
        }
        dt.second = (byte) value;

        if (dt.second > 59) {
            return badDate("Second is out of range", s);
        }
        if (dt.hour == 24 && dt.second != 0) {
            return badDate("If hour is 24, second must be 00", s);
        }

        int tz = 0;
        boolean negativeTz = false;
        int state = 0;
        while (tok.hasMoreTokens()) {
            if (state == 9) {
                return badDate("Characters after the end", s);
            }
            String delim = (String) tok.nextToken();
            if (".".equals(delim)) {
                if (state != 0) {
                    return badDate("Decimal separator occurs twice", s);
                }
                if (!tok.hasMoreTokens()) {
                    return badDate("Decimal point must be followed by digits", s);
                }
                part = tok.nextToken();
                if (part.length() > 9 && part.matches("^[0-9]+$")) {
                    part = part.substring(0, 9);
                }
                value = DurationValue.simpleInteger(part);
                if (value < 0) {
                    return badDate("Non-numeric fractional seconds component", s);
                }
                double fractionalSeconds = Double.parseDouble('.' + part);
                int nanoSeconds = (int) Math.round(fractionalSeconds * 1_000_000_000);
                if (nanoSeconds == 1_000_000_000) {
                    nanoSeconds--; // truncate fractional seconds to .999_999_999 if nanoseconds rounds to 1_000_000_000
                }
                dt.nanosecond = nanoSeconds;

                if (dt.hour == 24 && dt.nanosecond != 0) {
                    return badDate("If hour is 24, fractional seconds must be 0", s);
                }
                state = 1;
            } else if ("Z".equals(delim)) {
                if (state > 1) {
                    return badDate("Z cannot occur here", s);
                }
                tz = 0;
                state = 9;  // we've finished
                dt.tzMinutes = 0;
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
                tz = value;
                if (tz > 14) {
                    return badDate("Timezone is out of range (-14:00 to +14:00)", s);
                }
                tz *= 60;

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
                int tzminute = value;
                if (part.length() != 2) {
                    return badDate("Timezone minute must be two digits", s);
                }
                if (tzminute > 59) {
                    return badDate("Timezone minute is out of range", s);
                }
                if (Math.abs(tz) == 14 * 60 && tzminute != 0) {
                    return badDate("Timezone is out of range (-14:00 to +14:00)", s);
                }
                tz += tzminute;
                if (negativeTz) {
                    tz = -tz;
                }
                dt.tzMinutes = tz;
            } else {
                return badDate("Timezone format is incorrect", s);
            }
        }

        if (state == 2 || state == 3) {
            return badDate("Timezone incomplete", s);
        }

        boolean midnight = false;
        if (dt.hour == 24) {
            dt.hour = 0;
            midnight = true;
        }

        // Check that this is a valid calendar date
        if (!DateValue.isValidDate(dt.year, dt.month, dt.day)) {
            return badDate("Non-existent date", s);
        }

        // Adjust midnight to 00:00:00 on the next day
        if (midnight) {
            DateValue t = DateValue.tomorrow(dt.year, dt.month, dt.day);
            dt.year = t.getYear();
            dt.month = t.getMonth();
            dt.day = t.getDay();
        }

        dt.typeLabel = BuiltInAtomicType.DATE_TIME;
        return fromMutableCopy(dt);
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
                "Invalid dateTime value " + Err.wrap(value, Err.VALUE) + " (" + msg + ")");
        err.setErrorCode("FORG0001");
        return err;
    }

    private static ValidationFailure badDate(String msg, UnicodeString value, String errorCode) {
        ValidationFailure err = new ValidationFailure(
                "Invalid dateTime value " + Err.wrap(value, Err.VALUE) + " (" + msg + ")");
        err.setErrorCode(errorCode);
        return err;
    }

    /**
     * Constructor: construct a DateTimeValue from its components.
     * This constructor performs no validation.
     * <p>Note: this constructor accepts the fractional seconds value
     * to nanosecond precision. It creates a DateTimeValue that follows XSD 1.1 conventions:
     * that is, there is a year zero. This can be changed by setting the {@code hasNoYearZero} property.</p>
     *
     * @param year        The year (the year before year 1 is year 0)
     * @param month       The month, 1-12
     * @param day         The day 1-31
     * @param hour        the hour value, 0-23
     * @param minute      the minutes value, 0-59
     * @param second      the seconds value, 0-59
     * @param nanosecond  the number of nanoseconds, 0-999_999_999
     * @param tz          the timezone displacement in minutes from UTC. Supply the value
     *                    {@link CalendarValue#NO_TIMEZONE} if there is no timezone component.
     */

    public DateTimeValue(int year, byte month, byte day,
                         byte hour, byte minute, byte second, int nanosecond, int tz) {
        this(year, month, day, hour, minute, second, nanosecond, false, tz, BuiltInAtomicType.DATE_TIME);
    }

    /**
     * Constructor: construct a DateTimeValue from its components.
     * This constructor performs no validation.
     * <p>Note: for historic reasons, this constructor accepts the fractional seconds value
     * only to microsecond precision. To get nanosecond precision, use the 8-argument constructor
     * and set the XSD 1.0 option separately</p>
     *
     * @param year        The year as held internally (so the year before year 1 is year 0)
     * @param month       The month, 1-12
     * @param day         The day 1-31
     * @param hour        the hour value, 0-23
     * @param minute      the minutes value, 0-59
     * @param second      the seconds value, 0-59
     * @param microsecond the number of microseconds, 0-999999
     * @param tz          the timezone displacement in minutes from UTC. Supply the value
     *                    {@link CalendarValue#NO_TIMEZONE} if there is no timezone component.
     * @param hasNoYearZero  true if the dateTime value should behave under XSD 1.0 rules, that is,
     *                    negative dates assume there is no year zero. Note that regardless of this
     *                    setting, the year argument is set on the basis that the year before +1 is
     *                    supplied as zero; but if the hasNoYearZero flag is set, this value will be displayed
     *                    with a year of -1, and {@link #getYear() will return -1}
     */

    public DateTimeValue(int year, byte month, byte day,
                         byte hour, byte minute, byte second, int microsecond, int tz, boolean hasNoYearZero) {
        this(year, month, day, hour, minute, second, microsecond * 1000, hasNoYearZero, tz, BuiltInAtomicType.DATE_TIME);
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

    public byte getMonth() {
        return month;
    }

    /**
     * Get the day component, 1-31
     *
     * @return the day component
     */

    public byte getDay() {
        return day;
    }

    /**
     * Get the hour component, 0-23
     *
     * @return the hour component (never 24, even if the input was specified as 24:00:00)
     */

    public byte getHour() {
        return hour;
    }

    /**
     * Get the minute component, 0-59
     *
     * @return the minute component
     */

    public byte getMinute() {
        return minute;
    }

    /**
     * Get the second component, 0-59
     *
     * @return the second component
     */

    public byte getSecond() {
        return second;
    }

    /**
     * Get the microsecond component, 0-999999
     *
     * @return the nanosecond component divided by 1000, rounded towards zero
     */

    public int getMicrosecond() {
        return nanosecond / 1000;
    }

    /**
     * Get the nanosecond component, 0-999999
     *
     * @return the nanosecond component
     */

    public int getNanosecond() {
        return nanosecond;
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
     * Ask whether this value uses the XSD 1.0 rules (which don't allow year zero) or the XSD 1.1 rules (which do).
     *
     * @return true if the value uses the XSD 1.0 rules
     */

    public boolean isXsd10Rules() {
        return hasNoYearZero;
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
            MutableDateTimeValue m = makeMutableCopy();
            m.tzMinutes = implicitTimezone;
            return fromMutableCopy(m).adjustTimezone(0);
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
        julianSecond += ((hour * 60L + minute) * 60L) + second;
        BigDecimal j = BigDecimal.valueOf(julianSecond);
        if (nanosecond == 0) {
            return j;
        } else {
            return j.add(BigDecimal.valueOf(nanosecond).divide(BigDecimalValue.BIG_DECIMAL_ONE_BILLION, 9, RoundingMode.HALF_EVEN));
        }
    }

    /**
     * Get the DateTimeValue corresponding to a given Julian instant
     *
     * @param instant the Julian instant: a decimal value whose integer part is the Julian day number
     *                multiplied by the number of seconds per day, and whose fractional part is the fraction of the second.
     * @return the xs:dateTime value corresponding to the Julian instant. This will always be in timezone Z.
     */

    /*@NotNull*/
    public static DateTimeValue fromJulianInstant(/*@NotNull*/ BigDecimal instant) {
        BigInteger julianSecond = instant.toBigInteger();
        BigDecimal nanoseconds = instant.subtract(new BigDecimal(julianSecond)).multiply(BigDecimalValue.BIG_DECIMAL_ONE_BILLION);
        long js = julianSecond.longValue();
        long jd = js / (24L * 60L * 60L);
        DateValue date = DateValue.dateFromJulianDayNumber((int) jd);
        js = js % (24L * 60L * 60L);
        byte hour = (byte) (js / (60L * 60L));
        js = js % (60L * 60L);
        byte minute = (byte) (js / 60L);
        js = js % 60L;
        return new DateTimeValue(date.getYear(), date.getMonth(), date.getDay(),
                hour, minute, (byte) js, nanoseconds.intValue(), 0);
    }

    /**
     * Generate a value from the current date and time that can be used to seed a random number generator
     * @return a long derived arbitrarily from the current date and time
     */
    @CSharpReplaceBody(code="return ((((long)minute)*360 + ((long)second))*60) * 1000000000L + (long)nanosecond;")
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
            yr = hasNoYearZero ? 1 - year : -year;
            calendar.set(Calendar.ERA, GregorianCalendar.BC);
        }
        //noinspection MagicConstant
        calendar.set(yr, month - 1, day, hour, minute, second);
        calendar.set(Calendar.MILLISECOND, nanosecond / 1_000_000);   // loses precision unavoidably
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
            try {
                return ZonedDateTime.from(adjustToUTC(0));
            } catch (NoDynamicContextException e) {
                throw new AssertionError(e);
            }
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
            try {
                return OffsetDateTime.from(adjustToUTC(0));
            } catch (NoDynamicContextException e) {
                throw new AssertionError(e);
            }
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

        UnicodeBuilder sb = new UnicodeBuilder(32);
        int yr = year;
        if (year <= 0) {
            yr = -yr + (hasNoYearZero ? 1 : 0);    // no year zero in lexical space for XSD 1.0
            if (yr != 0) {
                sb.append('-');
            }
        }
        appendString(sb, yr, yr > 9999 ? (yr + "").length() : 4);
        sb.append('-');
        appendTwoDigits(sb, month);
        sb.append('-');
        appendTwoDigits(sb, day);
        sb.append('T');
        appendTwoDigits(sb, hour);
        sb.append(':');
        appendTwoDigits(sb, minute);
        sb.append(':');
        appendTwoDigits(sb, second);
        if (nanosecond != 0) {
            sb.append('.');
            int ms = nanosecond;
            int div = 100_000_000;
            while (ms > 0) {
                int d = ms / div;
                sb.append((char) (d + '0'));
                ms = ms % div;
                div /= 10;
            }
        }

        if (hasTimezone()) {
            appendTimezone(sb);
        }

        return sb.toUnicodeString();

    }

    /**
     * Extract the Date part
     *
     * @return a DateValue representing the date part of the dateTime, retaining the timezone or its absence
     */

    /*@NotNull*/
    public DateValue toDateValue() {
        return new DateValue(year, month, day, getTimezoneInMinutes(), hasNoYearZero);
    }

    /**
     * Extract the Time part
     *
     * @return a TimeValue representing the date part of the dateTime, retaining the timezone or its absence
     */

    /*@NotNull*/
    public TimeValue toTimeValue() {
        return new TimeValue(hour, minute, second, nanosecond, getTimezoneInMinutes(), BuiltInAtomicType.TIME);
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
     * @param typeLabel the type label to be attached to the new copy. It is the caller's responsibility
     *                  to ensure that the value actually conforms to the rules for this type.
     */

    /*@NotNull*/
    @Override
    public DateTimeValue copyAsSubType(AtomicType typeLabel) {
        MutableDateTimeValue v = makeMutableCopy();
        v.typeLabel = typeLabel;
        return fromMutableCopy(v);
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
            MutableDateTimeValue m = makeMutableCopy();
            m.tzMinutes = timezone;
            return fromMutableCopy(m);
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
            return new DateTimeValue(year, month, day,
                                                 (byte) h, (byte) mi, second, nanosecond,
                                                 hasNoYearZero, timezone, BuiltInAtomicType.DATE_TIME);
        }

        // Following code is designed to handle the corner case of adjusting from -14:00 to +14:00 or
        // vice versa, which can cause a change of two days in the date
        DateTimeValue dt = this;
        while (h < 0) {
            h += 24;
            DateValue t = DateValue.yesterday(dt.getYear(), dt.getMonth(), dt.getDay());
            dt = new DateTimeValue(t.getYear(), t.getMonth(), t.getDay(),
                    (byte) h, (byte) mi, second, nanosecond,
                                   hasNoYearZero, timezone, BuiltInAtomicType.DATE_TIME);
        }
        if (h > 23) {
            h -= 24;
            DateValue t = DateValue.tomorrow(year, month, day);
            dt = new DateTimeValue(t.getYear(), t.getMonth(), t.getDay(),
                                   (byte) h, (byte) mi, second, nanosecond,
                                   hasNoYearZero, timezone, BuiltInAtomicType.DATE_TIME);
        }
        return dt;
    }

    /**
     * Add a duration to a dateTime
     *
     * @param duration the duration to be added (may be negative)
     * @return the new date
     * @throws net.sf.saxon.trans.XPathException
     *          if the duration is an xs:duration, as distinct from
     *          a subclass thereof
     */

    /*@NotNull*/
    @Override
    public DateTimeValue add(/*@NotNull*/ DurationValue duration) throws XPathException {
        if (duration instanceof DayTimeDurationValue) {
            BigDecimal seconds = duration.getTotalSeconds();
            BigDecimal julian = toJulianInstant();
            julian = julian.add(seconds);
            MutableDateTimeValue dt = fromJulianInstant(julian).makeMutableCopy();
            dt.tzMinutes = getTimezoneInMinutes();
            dt.hasNoYearZero = this.hasNoYearZero;
            return fromMutableCopy(dt);
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
            return new DateTimeValue(y, (byte) m, (byte) d,
                    hour, minute, second, nanosecond, hasNoYearZero, getTimezoneInMinutes(), BuiltInAtomicType.DATE_TIME);
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
        try {
            DateTimeValue dtv = adjustToUTC(0);
            BigDecimal d1 = dtv.toJulianInstant();
            BigDecimal d2 = EPOCH.toJulianInstant();
            return d1.subtract(d2);
        } catch (NoDynamicContextException e) {
            throw new AssertionError(e);
        }
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
            case YEAR_ALLOWING_ZERO:
                return Int64Value.makeIntegerValue(year);
            case YEAR:
                return Int64Value.makeIntegerValue(year > 0 || !hasNoYearZero ? year : year - 1);
            case MONTH:
                return Int64Value.makeIntegerValue(month);
            case DAY:
                return Int64Value.makeIntegerValue(day);
            case HOURS:
                return Int64Value.makeIntegerValue(hour);
            case MINUTES:
                return Int64Value.makeIntegerValue(minute);
            case SECONDS:
                BigDecimal d = BigDecimal.valueOf(nanosecond);
                d = d.divide(BigDecimalValue.BIG_DECIMAL_ONE_BILLION, 6, RoundingMode.HALF_UP);
                d = d.add(BigDecimal.valueOf(second));
                return new BigDecimalValue(d);
            case WHOLE_SECONDS: //(internal use only)
                return Int64Value.makeIntegerValue(second);
            case MICROSECONDS:
                // internal use only
                return new Int64Value(nanosecond / 1000);
            case NANOSECONDS:
                // internal use only
                return new Int64Value(nanosecond);
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
            switch ((ChronoField) field) {
                case NANO_OF_SECOND:
                    return nanosecond;
                case NANO_OF_DAY:
                    return (hour * 3600 + minute * 60 + second) * 1_000_000_000L + nanosecond;
                case MICRO_OF_SECOND:
                    return nanosecond / 1000;
                case MICRO_OF_DAY:
                    return (hour * 3600 + minute * 60 + second) * 1_000_000L + (nanosecond / 1000);
                case MILLI_OF_SECOND:
                    return nanosecond / 1_000_000;
                case MILLI_OF_DAY:
                    return (hour * 3600 + minute * 60 + second)*1000L + nanosecond / 1_000_000;
                case SECOND_OF_MINUTE:
                    return second;
                case SECOND_OF_DAY:
                    return hour*3600 + minute*60 + second;
                case MINUTE_OF_HOUR:
                    return minute;
                case MINUTE_OF_DAY:
                    return hour*60 + minute;
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
                    return secondsSinceEpoch().setScale(0, BigDecimal.ROUND_FLOOR).longValue();
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
    public int compareTo(/*@NotNull*/ CalendarValue other, int implicitTimezone) throws NoDynamicContextException {
        if (!(other instanceof DateTimeValue)) {
            throw new ClassCastException("DateTime values are not comparable to " + other.getClass());
        }
        DateTimeValue v2 = (DateTimeValue) other;
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
            if (second != v2.second) {
                return IntegerValue.signum(second - v2.second);
            }
            if (nanosecond != v2.nanosecond) {
                return IntegerValue.signum(nanosecond - v2.nanosecond);
            }
            return 0;
        }
        return adjustToUTC(implicitTimezone).compareTo(v2.adjustToUTC(implicitTimezone), implicitTimezone);
    }

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone) throws NoDynamicContextException {
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
                    (dt0.hour << 7) ^ (dt0.minute << 2) ^ (dt0.second * 1_000_000_000 + dt0.nanosecond);
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
        return computeHashCode(year, month, day, hour, minute, second, nanosecond, getTimezoneInMinutes());
    }

    public static int computeHashCode(int year, byte month, byte day, byte hour, byte minute, byte second, int nanosecond, int tzMinutes) {
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
        return (year << 4) ^ (month << 28) ^ (day << 23) ^ (h << 18) ^ (mi << 13) ^ second ^ nanosecond;

    }

}

