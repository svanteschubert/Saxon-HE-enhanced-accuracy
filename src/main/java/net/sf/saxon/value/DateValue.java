////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * A value of type Date. Note that a Date may include a TimeZone.
 */

public class DateValue extends GDateValue implements Comparable {

    /**
     * Private constructor of a skeletal DateValue
     */

    private DateValue() {
    }

    /**
     * Constructor given a year, month, and day. Performs no validation.
     *
     * @param year  The year as held internally (note that the year before 1AD is supplied as 0,
     *              but will be displayed on output as -0001)
     * @param month The month, 1-12
     * @param day   The day, 1-31
     */

    public DateValue(int year, byte month, byte day) {
        this.hasNoYearZero = true;
        this.year = year;
        this.month = month;
        this.day = day;
        typeLabel = BuiltInAtomicType.DATE;
    }

    /**
     * Constructor given a year, month, day and xsd10. Performs no validation.
     *
     * @param year  The year as held internally (note that the year before 1AD is supplied as 0)
     * @param month The month, 1-12
     * @param day   The day, 1-31
     * @param xsd10 Schema version flag. If set to true, the year before 1AD is displayed as -0001;
     *              if false, it is displayed as 0000; but in both cases, it is held internally as zero. The flag
     *              makes no difference to dates later than 1AD.
     */

    public DateValue(int year, byte month, byte day, boolean xsd10) {
        this.hasNoYearZero = xsd10;
        this.year = year;
        this.month = month;
        this.day = day;
        typeLabel = BuiltInAtomicType.DATE;
    }

    /**
     * Constructor given a year, month, and day, and timezone. Performs no validation.
     *
     * @param year  The year as held internally (note that the year before 1AD is 0)
     * @param month The month, 1-12
     * @param day   The day, 1-31
     * @param tz    the timezone displacement in minutes from UTC. Supply the value
     *              {@link CalendarValue#NO_TIMEZONE} if there is no timezone component.
     * @param xsd10 Schema version flag. If set to true, the year before 1AD is displayed as -0001;
     *              if false, it is displayed as 0000; but in both cases, it is held internally as zero. The flag
     *              makes no difference to dates later than 1AD.
     */

    public DateValue(int year, byte month, byte day, int tz, boolean xsd10) {
        // Method is called by generated Java code.
        this.hasNoYearZero = xsd10;
        this.year = year;
        this.month = month;
        this.day = day;
        setTimezoneInMinutes(tz);
        typeLabel = BuiltInAtomicType.DATE;
    }

    /**
     * Constructor given a year, month, and day, and timezone, and an AtomicType representing
     * a subtype of xs:date. Performs no validation.
     *
     * @param year  The year as held internally (note that the year before 1AD is 0)
     * @param month The month, 1-12
     * @param day   The day 1-31
     * @param tz    the timezone displacement in minutes from UTC. Supply the value
     *              {@link CalendarValue#NO_TIMEZONE} if there is no timezone component.
     * @param type  the type. This must be a type derived from xs:date, and the value
     *              must conform to this type. The method does not check these conditions.
     */

    public DateValue(int year, byte month, byte day, int tz, AtomicType type) {
        this.year = year;
        this.month = month;
        this.day = day;
        setTimezoneInMinutes(tz);
        typeLabel = type;
    }

    /**
     * Constructor: create a date value from a supplied string, in
     * ISO 8601 format
     *
     * @param s the lexical form of the date value. Currently this assumes XSD 1.0
     *          conventions for BC years (that is, no year zero), but this may change at some time.
     * @throws ValidationException if the supplied string is not a valid date
     */
    public DateValue(CharSequence s) throws ValidationException {
        this(s, ConversionRules.DEFAULT);
    }

    /**
     * Constructor: create a date value from a supplied string, in
     * ISO 8601 format
     *
     * @param s     the lexical form of the date value
     * @param rules the conversion rules (determining whether year zero is allowed)
     * @throws ValidationException if the supplied string is not a valid date
     */
    public DateValue(CharSequence s, ConversionRules rules) throws ValidationException {
        setLexicalValue(this, s, rules.isAllowYearZero()).asAtomic();
        typeLabel = BuiltInAtomicType.DATE;
    }

    /**
     * Create a DateValue (with no timezone) from a Java LocalDate object
     * @param localDate the supplied local date
     * @since 10.0
     */

    public DateValue(LocalDate localDate) {
        this(localDate.getYear(), (byte)localDate.getMonthValue(), (byte)localDate.getDayOfMonth());
    }

    /**
     * Create a DateValue from a Java GregorianCalendar object
     *
     * @param calendar the absolute date/time value
     * @param tz       The timezone offset from GMT in minutes, positive or negative; or the special
     *                 value NO_TIMEZONE indicating that the value is not in a timezone
     */
    public DateValue(GregorianCalendar calendar, int tz) {
        // Note: this constructor is not used by Saxon itself, but might be used by applications
        int era = calendar.get(GregorianCalendar.ERA);
        year = calendar.get(Calendar.YEAR);
        if (era == GregorianCalendar.BC) {
            year = 1 - year;
        }
        month = (byte) (calendar.get(Calendar.MONTH) + 1);
        day = (byte) calendar.get(Calendar.DATE);
        setTimezoneInMinutes(tz);
        typeLabel = BuiltInAtomicType.DATE;
    }

    /**
     * Static factory method: construct a DateValue from a string in the lexical form
     * of a date, returning a ValidationFailure if the supplied string is invalid
     *
     * @param in    the lexical form of the date
     * @param rules  conversion rules to apply (affects handling of year 0)
     * @return either a DateValue or a ValidationFailure
     */

    public static ConversionResult makeDateValue(CharSequence in, ConversionRules rules) {
        DateValue d = new DateValue();
        d.typeLabel = BuiltInAtomicType.DATE;
        return setLexicalValue(d, in, rules.isAllowYearZero());
    }

    /**
     * Factory method: create an xs:date value from a supplied string, in ISO 8601 format, allowing
     * a year value of 0 to represent the year before year 1 (that is, following the XSD 1.1 rules).
     * <p>The {@code hasNoYearZero} property in the result is set to false.</p>
     *
     * @param s a string in the lexical space of xs:date
     * @return a DateValue representing the xs:date supplied, including a timezone offset if
     * present in the lexical representation
     * @throws DateTimeParseException if the format of the supplied string is invalid.
     * @since 9.9
     */

    public static DateValue parse(CharSequence s) throws DateTimeParseException {
        ConversionResult result = makeDateValue(s, ConversionRules.DEFAULT);
        if (result instanceof ValidationFailure) {
            throw new DateTimeParseException(((ValidationFailure) result).getMessage(), s, 0);
        } else {
            return (DateValue) result;
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

    public static DateValue tomorrow(int year, byte month, byte day) {
        if (DateValue.isValidDate(year, month, day + 1)) {
            return new DateValue(year, month, (byte) (day + 1), true);
        } else if (month < 12) {
            return new DateValue(year, (byte) (month + 1), (byte) 1, true);
        } else {
            return new DateValue(year + 1, (byte) 1, (byte) 1, true);
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

    public static DateValue yesterday(int year, byte month, byte day) {
        if (day > 1) {
            return new DateValue(year, month, (byte) (day - 1), true);
        } else if (month > 1) {
            if (month == 3 && isLeapYear(year)) {
                return new DateValue(year, (byte) 2, (byte) 29, true);
            } else {
                return new DateValue(year, (byte) (month - 1), daysPerMonth[month - 2], true);
            }
        } else {
            return new DateValue(year - 1, (byte) 12, (byte) 31, true);
        }
    }

    /**
     * Convert to string
     *
     * @return ISO 8601 representation.
     */

    @Override
    public CharSequence getPrimitiveStringValue() {

        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C16);
        int yr = year;
        if (year <= 0) {
            yr = -yr + (hasNoYearZero ? 1 : 0);           // no year zero in lexical space for XSD 1.0
            if (yr != 0) {
                sb.cat('-');
            }
        }
        appendString(sb, yr, yr > 9999 ? (yr + "").length() : 4);
        sb.cat('-');
        appendTwoDigits(sb, month);
        sb.cat('-');
        appendTwoDigits(sb, day);

        if (hasTimezone()) {
            appendTimezone(sb);
        }

        return sb;

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
    public CharSequence getCanonicalLexicalRepresentation() {
        DateValue target = this;
        if (hasTimezone()) {
            if (getTimezoneInMinutes() > 12 * 60) {
                target = adjustTimezone(getTimezoneInMinutes() - 24 * 60);
            } else if (getTimezoneInMinutes() <= -12 * 60) {
                target = adjustTimezone(getTimezoneInMinutes() + 24 * 60);
            }
        }
        return target.getStringValueCS();
    }

    /**
     * Make a copy of this date value, but with a new type label
     *
     * @param typeLabel the new type label: must be a subtype of xs:date
     * @return the new xs:date value
     */

    /*@NotNull*/
    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        DateValue v = new DateValue(year, month, day, getTimezoneInMinutes(), hasNoYearZero);
        v.typeLabel = typeLabel;
        return v;
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
        return new DateValue(dt.getYear(), dt.getMonth(), dt.getDay(), dt.getTimezoneInMinutes(), hasNoYearZero);
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
            long microseconds = ((DayTimeDurationValue) duration).getLengthInMicroseconds();
            boolean negative = microseconds < 0;
            microseconds = Math.abs(microseconds);
            int days = (int) Math.floor((double) microseconds / (1000000L * 60L * 60L * 24L));
            boolean partDay = (microseconds % (1000000L * 60L * 60L * 24L)) > 0;
            int julian = getJulianDayNumber();
            DateValue d = dateFromJulianDayNumber(julian + (negative ? -days : days));
            if (partDay) {
                if (negative) {
                    d = yesterday(d.year, d.month, d.day);
                }
            }
            d.setTimezoneInMinutes(getTimezoneInMinutes());
            d.hasNoYearZero = this.hasNoYearZero;
            return d;
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
            return new DateValue(y, (byte) m, (byte) d, getTimezoneInMinutes(), hasNoYearZero);
        } else {
            XPathException err = new XPathException("Date arithmetic is not available for xs:duration, only for its subtypes");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            throw err;
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
            XPathException err = new XPathException("First operand of '-' is a date, but the second is not");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            throw err;
        }
        return super.subtract(other, context);
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
    public int compareTo(Object v2) {
        try {
            return compareTo((DateValue) v2, MISSING_TIMEZONE);
        } catch (Exception err) {
            throw new ClassCastException("Date comparison requires access to implicit timezone");
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

    public static DateValue dateFromJulianDayNumber(int julianDayNumber) {
        if (julianDayNumber >= 0) {
            int L = julianDayNumber + 68569 + 1;    // +1 adjustment for days starting at noon
            int n = (4 * L) / 146097;
            L = L - (146097 * n + 3) / 4;
            int i = (4000 * (L + 1)) / 1461001;
            L = L - (1461 * i) / 4 + 31;
            int j = (80 * L) / 2447;
            int d = L - (2447 * j) / 80;
            L = j / 11;
            int m = j + 2 - (12 * L);
            int y = 100 * (n - 49) + i + L;
            return new DateValue(y, (byte) m, (byte) d, true);
        } else {
            // add 12000 years and subtract them again...
            DateValue dt = dateFromJulianDayNumber(julianDayNumber +
                                                           365 * 12000 + 12000 / 4 - 12000 / 100 + 12000 / 400);
            dt.year -= 12000;
            return dt;
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

