////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.str.TwineBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.StringTokenizer;

/**
 * A value of type xs:date. Note that a Date may include a TimeZone.
 */

public class DateValue extends GDateValue implements XPathComparable {

    /**
     * Constructor given a year, month, and day. Performs no validation.
     *
     * @param year  The year. Accepts year zero.
     * @param month The month, 1-12
     * @param day   The day, 1-31
     */

    public DateValue(int year, int month, int day) {
        super(year, month, day, NO_TIMEZONE, BuiltInAtomicType.DATE);
    }


    /**
     * Constructor given a year, month, and day, and timezone. Performs no validation.
     *
     * @param year  The year. Accepts year zero
     * @param month The month, 1-12
     * @param day   The day, 1-31
     * @param tz    the timezone displacement in minutes from UTC. Supply the value
     *              {@link CalendarValue#NO_TIMEZONE} if there is no timezone component.
     */

    public DateValue(int year, int month, int day, int tz) {
        super(year, month, day, tz, BuiltInAtomicType.DATE);
    }

    /**
     * Constructor given a year, month, and day, and timezone, and an AtomicType representing
     * a subtype of xs:date. Performs no validation.
     *
     * @param year  The year. Accepts year zero.
     * @param month The month, 1-12
     * @param day   The day 1-31
     * @param tz    the timezone displacement in minutes from UTC. Supply the value
     *              {@link CalendarValue#NO_TIMEZONE} if there is no timezone component.
     * @param type  the type. This must be a type derived from xs:date, and the value
     *              must conform to this type. The method does not check these conditions.
     */

    public DateValue(int year, int month, int day, int tz, AtomicMetadata type) {
        super(year, month, day, tz, type);
    }

    /**
     * Initialize the DateValue using a character string in the format yyyy-mm-dd and an optional time zone.
     * Input must have format [-]yyyy-mm-dd[([+|-]hh:mm | Z)]
     * @param s             the supplied string value
     * @param allowYearZero true if (as in XSD 1.1) there is a year zero, false if (as in XSD 1.0) there is not
     * @return either a DateValue if the string represents a valid date, or a {@link ValidationFailure}
     * otherwise.
     */

    public static ConversionResult tryParseDate(String s, boolean allowYearZero) {

        StringTokenizer tok = new StringTokenizer(Whitespace.trim(s), "-:+TZ", true);
        try {
            if (!tok.hasMoreTokens()) {
                return badDate("Too short", s);
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

            if (part.length() < 4) {
                return badDate("Year is less than four digits", s);
            }
            if (part.length() > 4 && part.charAt(0) == '0') {
                return badDate("When year exceeds 4 digits, leading zeroes are not allowed", s);
            }
            long value = DurationValue.simpleInteger(part);
            if (value < 0 || value > Integer.MAX_VALUE) {
                if (value == -1) {
                    return badDate("Non-numeric year component", s);
                } else {
                    return badDate("Year is outside the range that Saxon can handle", s, "FODT0001");
                }
            }
            int year = (int) value * era;
            if (year == 0 && !allowYearZero) {
                return badDate("Year zero is not allowed", s);
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
            int day = (int)value;
            if (day < 1 || day > 31) {
                return badDate("Day is out of range", s);
            }

            int tzOffset = NO_TIMEZONE;
            if (tok.hasMoreTokens()) {

                String delim = tok.nextToken();

                if ("T".equals(delim)) {
                    return badDate("Value includes time", s);
                } else if ("Z".equals(delim)) {
                    tzOffset = 0;
                    if (tok.hasMoreTokens()) {
                        return badDate("Continues after 'Z'", s);
                    }
                } else if (!(!"+".equals(delim) && !"-".equals(delim))) {
                    if (!tok.hasMoreTokens()) {
                        return badDate("Missing timezone", s);
                    }
                    part = tok.nextToken();
                    value = DurationValue.simpleInteger(part);
                    if (value < 0) {
                        return badDate("Non-numeric timezone hour component", s);
                    }
                    if (value > 14) {
                        return badDate("Timezone hour is out of range", s);
                    }
                    int tzhour = (int) value;
                    if (part.length() != 2) {
                        return badDate("Timezone hour must be two digits", s);
                    }

                    if (!tok.hasMoreTokens()) {
                        return badDate("No minutes in timezone", s);
                    }
                    if (!":".equals(tok.nextToken())) {
                        return badDate("Wrong delimiter after timezone hour", s);
                    }

                    if (!tok.hasMoreTokens()) {
                        return badDate("No minutes in timezone", s);
                    }
                    part = tok.nextToken();
                    value = DurationValue.simpleInteger(part);
                    if (value < 0) {
                        return badDate("Non-numeric timezone minute component", s);
                    }
                    if (value > 59) {
                        return badDate("Timezone minute is out of range", s);
                    }
                    int tzminute = (int) value;
                    if (part.length() != 2) {
                        return badDate("Timezone minute must be two digits", s);
                    }

                    if (tok.hasMoreTokens()) {
                        return badDate("Continues after timezone", s);
                    }

                    tzOffset = tzhour * 60 + tzminute;
                    if ("-".equals(delim)) {
                        tzOffset = -tzOffset;
                    }

                } else {
                    return badDate("Timezone format is incorrect", s);
                }
            }

            if (!isValidDate(year, month, day)) {
                return badDate("Non-existent date", s);
            }
            return new DateValue(year, month, day, tzOffset);

        } catch (NumberFormatException err) {
            return badDate("Non-numeric component", s);
        }

    }

    /**
     * Construct a DateValue, from its lexical representation
     * @param value the lexical representation of the date
     * @return the corresponding date
     * @throws ValidationException if the date is invalid
     */

    public static DateValue parseDate(String value) throws ValidationException {
        return (DateValue) tryParseDate(value, true).asAtomic();
    }


    private static ValidationFailure badDate(String msg, String value) {
        ValidationFailure err = new ValidationFailure(
                "Invalid date " + Err.wrap(value, Err.VALUE) + " (" + msg + ")");
        err.setErrorCode("FORG0001");
        return err;
    }


    private static ValidationFailure badDate(String msg, String value, String errorCode) {
        ValidationFailure err = new ValidationFailure(
                "Invalid date " + Err.wrap(value, Err.VALUE) + " (" + msg + ")");
        err.setErrorCode(errorCode);
        return err;
    }

    /**
     * Create a DateValue (with no timezone) from a Java {@link LocalDate} object
     * @param localDate the supplied local date
     * @since 10.0
     */

    public DateValue(LocalDate localDate) {
        this(localDate.getYear(), (byte)localDate.getMonthValue(), (byte)localDate.getDayOfMonth());
    }

    /**
     * Create a DateValue from a Java {@link GregorianCalendar} object
     *
     * @param calendar the absolute date/time value
     * @param tz       The timezone offset from GMT in minutes, positive or negative; or the special
     *                 value NO_TIMEZONE indicating that the value is not in a timezone
     */
    public DateValue(GregorianCalendar calendar, int tz) {
        super(  calendar.get(Calendar.YEAR) * calendar.get(GregorianCalendar.ERA) == GregorianCalendar.BC ? -1 : 1,
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DATE),
                tz,
                BuiltInAtomicType.DATE);
    }


    /**
     * Creates an instance of DateValue.  Includes validation
     * checks.  If a validation error is detected, an instance of
     * ValidationFailure will be returned instead.
     *
     * @param year - number of a year in the Gregorian calendar
     * @param month - number of a month within the specified year
     * @param day - number of a day within the specified month
     * @param timezoneInMinutes - number of minutes to adjust by for the timezone
     * @return an instance of GYearMonthValue or ValidationFailure
     */
    public static ConversionResult makeDateValue(int year, int month, int day, int timezoneInMinutes) {
        if (!isValidDate(year, month, day)) {
            return new ValidationFailure("Invalid date value " + year + "-" + month + "-" + day);
        }
        if (!isValidTimezone(timezoneInMinutes)) {
            return new ValidationFailure("Invalid timezone offset " + timezoneInMinutes + " minutes", "FODT0003");
        }
        return new DateValue(year, month, day, timezoneInMinutes);
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.DATE;
    }

    /**
     * Get the date that immediately follows a given date
     *
     * @param year  the year
     * @param month the month (1-12)
     * @param day   the day (1-31)
     * @return a new DateValue with no timezone information
     */

    public static DateValue tomorrow(int year, int month, int day) {
        if (DateValue.isValidDate(year, month, day + 1)) {
            return new DateValue(year, month, (byte) (day + 1));
        } else if (month < 12) {
            return new DateValue(year, (byte) (month + 1), (byte) 1);
        } else {
            return new DateValue(year + 1, (byte) 1, (byte) 1);
        }
    }

    /**
     * Get the date that immediately precedes a given date
     *
     * @param year  the year
     * @param month the month (1-12)
     * @param day   the day (1-31)
     * @return a new DateValue with no timezone information
     */

    public static DateValue yesterday(int year, int month, int day) {
        if (day > 1) {
            return new DateValue(year, month, (day - 1));
        } else if (month > 1) {
            if (month == 3 && isLeapYear(year)) {
                return new DateValue(year, 2, 29);
            } else {
                return new DateValue(year, (month - 1), daysPerMonth[month - 2]);
            }
        } else {
            return new DateValue(year - 1, 12, 31);
        }
    }

    /**
     * Convert to string
     *
     * @return ISO 8601 representation.
     */

    @Override
    public UnicodeString getPrimitiveStringValue() {

        TwineBuilder tb = TwineBuilder.make(16);
        int yr = year;
        if (year <= 0) {
            yr = -yr;
            if (yr != 0) {
                tb = tb.append('-');
            }
        }
        tb = appendString(tb, yr, yr > 9999 ? (yr + "").length() : 4).append('-');
        tb = appendTwoDigits(tb, month).append('-');
        tb = appendTwoDigits(tb, day);

        if (hasTimezone()) {
            tb = appendTimezone(tb);
        }

        return tb.toUnicodeString();

    }

    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules. For xs:date, the timezone is
     * adjusted to be in the range +12:00 to -11:59
     *
     * @return the canonical lexical representation if defined in XML Schema; otherwise, the result
     *         of casting to string according to the XPath 2.0 rules
     */

    @Override
    public UnicodeString getCanonicalLexicalRepresentation() {
        DateValue target = this;
        if (hasTimezone()) {
            if (getTimezoneInMinutes() > 12 * 60) {
                target = adjustTimezone(getTimezoneInMinutes() - 24 * 60);
            } else if (getTimezoneInMinutes() <= -12 * 60) {
                target = adjustTimezone(getTimezoneInMinutes() + 24 * 60);
            }
        }
        return target.getUnicodeStringValue();
    }

    /**
     * Make a copy of this date value, but with a new type label
     *
     * @param metadata the new type label: must be a subtype of xs:date
     * @return the new xs:date value
     */

    /*@NotNull*/
    @Override
    public AtomicValue withMetadata(AtomicMetadata metadata) {
        return new DateValue(year, month, day, tzMinutes, metadata);
    }

    /**
     * Return a new date with the same normalized value, but
     * in a different timezone. This is called only for a DateValue that has an explicit timezone
     *
     * @param timezone the new timezone offset, in minutes
     * @return the time in the new timezone. This will be a new TimeValue unless no change
     *         was required to the original value
     */

    @Override
    public DateValue adjustTimezone(int timezone) {
        DateTimeValue dt = toDateTime().adjustTimezone(timezone);
        return new DateValue(dt.getYear(), dt.getMonth(), dt.getDay(), dt.getTimezoneInMinutes());
    }

    /**
     * Add a duration to a date
     *
     * @param duration the duration to be added (may be negative)
     * @return the new date
     * @throws net.sf.saxon.trans.XPathException
     *          if the duration is an xs:duration, as distinct from
     *          a subclass thereof
     */

    @Override
    public DateValue add(DurationValue duration) throws XPathException {
        if (duration instanceof DayTimeDurationValue) {
            BigDecimal seconds = duration.getTotalSeconds();
            BigDecimal[] daysAndPartDays = seconds.divideAndRemainder(BigDecimalValue.SECONDS_PER_DAY);
            boolean partDay = daysAndPartDays[1].signum() != 0;
            long days = daysAndPartDays[0].longValue();
            if (Math.abs(days) >= Integer.MAX_VALUE) {
                throw new XPathException("Saxon limit: cannot add/subtract more than 2^31 days to a date");
            }
            boolean negative = duration.signum() < 0;
            int julian = getJulianDayNumber();
            DateValue d = dateFromJulianDayNumber(julian + (int)days);
            if (partDay) {
                if (negative) {
                    d = yesterday(d.year, d.month, d.day);
                }
            }
            return new DateValue(d.year, d.month, d.day, getTimezoneInMinutes());
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
            while (!isValidDate(y, m, d)) {
                d -= 1;
            }
            return new DateValue(y, m, d, getTimezoneInMinutes());
        } else {
            throw new XPathException("Date arithmetic is not available for xs:duration, only for its subtypes")
                    .asTypeError().withErrorCode("XPTY0004");
        }
    }

    /**
     * Determine the difference between two points in time, as a duration
     *
     * @param other   the other point in time
     * @param context the XPath dynamic context. May be set to null
     *                only if both values contain an explicit timezone, or if neither does so.
     * @return the duration as an xs:dayTimeDuration
     * @throws XPathException for example if one value is a date and the other is a time
     */

    @Override
    public DayTimeDurationValue subtract(/*@NotNull*/ CalendarValue other, /*@Nullable*/ XPathContext context) throws XPathException {
        if (!(other instanceof DateValue)) {
            throw new XPathException("First operand of '-' is a date, but the second is not")
                    .asTypeError().withErrorCode("XPTY0004");
        }
        return super.subtract(other, context);
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
     * Context-free comparison of two DateValue values. For this to work,
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
        if (v2 instanceof DateValue) {
            try {
                return compareTo((DateValue)v2, MISSING_TIMEZONE);
            } catch (Exception err) {
                throw new ClassCastException("Date comparison requires access to implicit timezone");
            }
        } else {
            throw new ClassCastException("Cannot compare xs:date to " + v2.toString());
        }
    }

    /**
     * Calculate the Julian day number at 00:00 on a given date. This algorithm is taken from
     * http://vsg.cape.com/~pbaum/date/jdalg.htm and
     * http://vsg.cape.com/~pbaum/date/jdalg2.htm
     * (adjusted to handle BC dates correctly)
     * <p>Note that this assumes dates in the proleptic Gregorian calendar</p>
     *
     * @param year  the year
     * @param month the month (1-12)
     * @param day   the day (1-31)
     * @return the Julian day number
     */

    public static int getJulianDayNumber(int year, int month, int day) {
        int z = year - (month < 3 ? 1 : 0);
        short f = monthData[month - 1];
        if (z >= 0) {
            return day + f + 365 * z + z / 4 - z / 100 + z / 400 + 1721118;
        } else {
            // for negative years, add 12000 years and then subtract the days!
            z += 12000;
            int j = day + f + 365 * z + z / 4 - z / 100 + z / 400 + 1721118;
            return j - (365 * 12000 + 12000 / 4 - 12000 / 100 + 12000 / 400);  // number of leap years in 12000 years
        }
    }

    /**
     * Calculate the Julian day number at 00:00 on this date.
     * <p>Note that this assumes dates in the proleptic Gregorian calendar</p>
     *
     * @return the Julian day number
     */

    public int getJulianDayNumber() {
        return getJulianDayNumber(year, month, day);
    }

    /**
     * Get the Gregorian date corresponding to a particular Julian day number. The algorithm
     * is taken from http://www.hermetic.ch/cal_stud/jdn.htm#comp
     *
     * @param julianDayNumber the Julian day number
     * @return a DateValue with no timezone information set
     */

    public static DateValue dateFromJulianDayNumber(long julianDayNumber) {
        if (julianDayNumber >= 0) {
            long L = julianDayNumber + 68569 + 1;    // +1 adjustment for days starting at noon
            long n = (4 * L) / 146097;
            L = L - (146097 * n + 3) / 4;
            long i = (4000 * (L + 1)) / 1461001;
            L = L - (1461 * i) / 4 + 31;
            long j = (80 * L) / 2447;
            long d = L - (2447 * j) / 80;
            L = j / 11;
            long m = j + 2 - (12 * L);
            long y = 100 * (n - 49) + i + L;
            return new DateValue((int)y, (int)m, (int)d, NO_TIMEZONE, BuiltInAtomicType.DATE);
        } else {
            // add 12000 years and subtract them again...
            DateValue dt = dateFromJulianDayNumber(julianDayNumber +
                                                           365 * 12000 + 12000 / 4 - 12000 / 100 + 12000 / 400);
            return new DateValue(dt.year - 12000, dt.month, dt.day, dt.tzMinutes);
        }
    }

    /**
     * Get the ordinal day number within the year (1 Jan = 1, 1 Feb = 32, etc)
     *
     * @param year  the year
     * @param month the month (1-12)
     * @param day   the day (1-31)
     * @return the ordinal day number within the year
     */

    public static int getDayWithinYear(int year, int month, int day) {
        int j = getJulianDayNumber(year, month, day);
        int k = getJulianDayNumber(year, 1, 1);
        return j - k + 1;
    }

    /**
     * Get the day of the week.  The days of the week are numbered from
     * 1 (Monday) to 7 (Sunday)
     *
     * @param year  the year
     * @param month the month (1-12)
     * @param day   the day (1-31)
     * @return the day of the week, 1=Monday .... 7=Sunday
     */

    public static int getDayOfWeek(int year, int month, int day) {
        int d = getJulianDayNumber(year, month, day);
        d -= 2378500;   // 1800-01-05 - any Monday would do
        while (d <= 0) {
            d += 70000000;  // any sufficiently high multiple of 7 would do
        }
        return (d - 1) % 7 + 1;
    }

    /**
     * Get the ISO week number for a given date.  The days of the week are numbered from
     * 1 (Monday) to 7 (Sunday), and week 1 in any calendar year is the week (from Monday to Sunday)
     * that includes the first Thursday of that year
     *
     * @param year  the year
     * @param month the month (1-12)
     * @param day   the day (1-31)
     * @return the ISO week number
     */

    @CSharpReplaceBody(code="return System.Globalization.ISOWeek.GetWeekOfYear(new System.DateTime(year, month, day));")
    public static int getWeekNumber(int year, int month, int day) {
        LocalDate date = LocalDate.of(year, month, day);
        return date.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    /**
     * Get the week number within a month. This is required for the XSLT format-date() function.
     * The days of the week are numbered from 1 (Monday) to 7 (Sunday), and week 1
     * in any calendar month is the week (from Monday to Sunday) that includes the first Thursday
     * of that month.
     * <p>See bug 21370 which clarified the specification. This caused a change to the Saxon
     * implementation such that the days before the start of week 1 go in the last week of the previous
     * month, not week zero.</p>
     *
     * @param year  the year
     * @param month the month (1-12)
     * @param day   the day (1-31)
     * @return the week number within a month
     */

    public static int getWeekNumberWithinMonth(int year, int month, int day) {
        int firstDay = getDayOfWeek(year, month, 1);
        if (firstDay > 4 && (firstDay + day) <= 8) {
            // days before week one are part of the last week of the previous month (4 or 5)
            DateValue lastDayPrevMonth = yesterday(year, (byte) month, (byte) 1);
            return getWeekNumberWithinMonth(lastDayPrevMonth.year, lastDayPrevMonth.month, lastDayPrevMonth.day);
        }
        int inc = firstDay < 5 ? 1 : 0;   // implements the First Thursday rule
        return ((day + firstDay - 2) / 7) + inc;
    }

    /**
     * Convert the value to a Java {@link LocalDate} value, dropping any timezone information
     * @return the corresponding Java {@link LocalDate}; any timezone information is simply discarded
     */

    public LocalDate toLocalDate() {
        return LocalDate.of(getYear(), getMonth(), getDay());
    }
}

