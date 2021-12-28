////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.ValidationFailure;

import java.util.*;

/**
 * Abstract superclass for the primitive types containing date components: xs:date, xs:gYear,
 * xs:gYearMonth, xs:gMonth, xs:gMonthDay, xs:gDay
 */
public abstract class GDateValue extends CalendarValue {
    protected int year;         // unlike the lexical representation, includes a year zero
    protected byte month;
    protected byte day;
    protected boolean hasNoYearZero;
    /**
     * Test whether a candidate date is actually a valid date in the proleptic Gregorian calendar
     */

    /*@NotNull*/ protected static byte[] daysPerMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    /*@NotNull*/ protected static final short[] monthData = {306, 337, 0, 31, 61, 92, 122, 153, 184, 214, 245, 275};

    /**
     * Get the year component of the date (in local form)
     *
     * @return the year component, as represented internally (allowing a year zero)
     */

    public int getYear() {
        return year;
    }

    /**
     * Get the month component of the date (in local form)
     *
     * @return the month component (1-12)
     */

    public byte getMonth() {
        return month;
    }

    /**
     * Get the day component of the date (in local form)
     *
     * @return the day component (1-31)
     */

    public byte getDay() {
        return day;
    }

    /*@NotNull*/
    @Override
    public GregorianCalendar getCalendar() {

        int tz = hasTimezone() ? getTimezoneInMinutes() * 60000 : 0;
        TimeZone zone = new SimpleTimeZone(tz, "LLL");
        GregorianCalendar calendar = new GregorianCalendar(zone);
        calendar.setGregorianChange(new Date(Long.MIN_VALUE));
        if (tz < calendar.getMinimum(Calendar.ZONE_OFFSET) || tz > calendar.getMaximum(Calendar.ZONE_OFFSET)) {
            return adjustTimezone(0).getCalendar();
        }
        calendar.clear();
        calendar.setLenient(false);
        int yr = year;
        if (year <= 0) {
            yr = hasNoYearZero ? 1 - year : 0 - year;
            calendar.set(Calendar.ERA, GregorianCalendar.BC);
        }
        //noinspection MagicConstant
        calendar.set(yr, month - 1, day);
        calendar.set(Calendar.ZONE_OFFSET, tz);
        calendar.set(Calendar.DST_OFFSET, 0);
        calendar.getTime();
        return calendar;
    }

    /**
     * Initialize the DateValue using a character string in the format yyyy-mm-dd and an optional time zone.
     * Input must have format [-]yyyy-mm-dd[([+|-]hh:mm | Z)]
     *
     * @param d     the "raw" DateValue to be populated
     * @param s     the supplied string value
     * @param allowYearZero true if (as in XSD 1.1) there is a year zero, false if (as in XSD 1.0) there is not
     * @return either the supplied GDateValue, with its data initialized; or a ValidationFailure
     */

    /*@NotNull*/
    protected static ConversionResult setLexicalValue(GDateValue d, CharSequence s, boolean allowYearZero) {
        d.hasNoYearZero = !allowYearZero;
        StringTokenizer tok = new StringTokenizer(Whitespace.trimWhitespace(s).toString(), "-:+TZ", true);
        try {
            if (!tok.hasMoreElements()) {
                return badDate("Too short", s);
            }
            String part = (String) tok.nextElement();
            int era = +1;
            if ("+".equals(part)) {
                return badDate("Date must not start with '+' sign", s);
            } else if ("-".equals(part)) {
                era = -1;
                if (!tok.hasMoreElements()) {
                    return badDate("No year after '-'", s);
                }
                part = (String) tok.nextElement();
            }

            if (part.length() < 4) {
                return badDate("Year is less than four digits", s);
            }
            if (part.length() > 4 && part.charAt(0) == '0') {
                return badDate("When year exceeds 4 digits, leading zeroes are not allowed", s);
            }
            int value = DurationValue.simpleInteger(part);
            if (value < 0) {
                if (value == -1) {
                    return badDate("Non-numeric year component", s);
                } else {
                    return badDate("Year is outside the range that Saxon can handle", s, "FODT0001");
                }
            }
            d.year = value * era;
            if (d.year == 0 && !allowYearZero) {
                return badDate("Year zero is not allowed", s);
            }
            if (era < 0 && !allowYearZero) {
                d.year++;      // if year zero not allowed, -0001 is the year before +0001, represented as 0 internally.
            }
            if (!tok.hasMoreElements()) {
                return badDate("Too short", s);
            }
            if (!"-".equals(tok.nextElement())) {
                return badDate("Wrong delimiter after year", s);
            }

            if (!tok.hasMoreElements()) {
                return badDate("Too short", s);
            }
            part = (String) tok.nextElement();
            if (part.length() != 2) {
                return badDate("Month must be two digits", s);
            }
            value = DurationValue.simpleInteger(part);
            if (value < 0) {
                return badDate("Non-numeric month component", s);
            }
            d.month = (byte) value;
            if (d.month < 1 || d.month > 12) {
                return badDate("Month is out of range", s);
            }
            if (!tok.hasMoreElements()) {
                return badDate("Too short", s);
            }
            if (!"-".equals(tok.nextElement())) {
                return badDate("Wrong delimiter after month", s);
            }

            if (!tok.hasMoreElements()) {
                return badDate("Too short", s);
            }
            part = (String) tok.nextElement();
            if (part.length() != 2) {
                return badDate("Day must be two digits", s);
            }
            value = DurationValue.simpleInteger(part);
            if (value < 0) {
                return badDate("Non-numeric day component", s);
            }
            d.day = (byte) value;
            if (d.day < 1 || d.day > 31) {
                return badDate("Day is out of range", s);
            }

            int tzOffset;
            if (tok.hasMoreElements()) {

                String delim = (String) tok.nextElement();

                if ("T".equals(delim)) {
                    return badDate("Value includes time", s);
                } else if ("Z".equals(delim)) {
                    tzOffset = 0;
                    if (tok.hasMoreElements()) {
                        return badDate("Continues after 'Z'", s);
                    }
                    d.setTimezoneInMinutes(tzOffset);

                } else if (!(!"+".equals(delim) && !"-".equals(delim))) {
                    if (!tok.hasMoreElements()) {
                        return badDate("Missing timezone", s);
                    }
                    part = (String) tok.nextElement();
                    value = DurationValue.simpleInteger(part);
                    if (value < 0) {
                        return badDate("Non-numeric timezone hour component", s);
                    }
                    int tzhour = value;
                    if (part.length() != 2) {
                        return badDate("Timezone hour must be two digits", s);
                    }
                    if (tzhour > 14) {
                        return badDate("Timezone hour is out of range", s);
                    }
                    if (!tok.hasMoreElements()) {
                        return badDate("No minutes in timezone", s);
                    }
                    if (!":".equals(tok.nextElement())) {
                        return badDate("Wrong delimiter after timezone hour", s);
                    }

                    if (!tok.hasMoreElements()) {
                        return badDate("No minutes in timezone", s);
                    }
                    part = (String) tok.nextElement();
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
                    if (tok.hasMoreElements()) {
                        return badDate("Continues after timezone", s);
                    }

                    tzOffset = tzhour * 60 + tzminute;
                    if ("-".equals(delim)) {
                        tzOffset = -tzOffset;
                    }
                    d.setTimezoneInMinutes(tzOffset);

                } else {
                    return badDate("Timezone format is incorrect", s);
                }
            }

            if (!isValidDate(d.year, d.month, d.day)) {
                return badDate("Non-existent date", s);
            }

        } catch (NumberFormatException err) {
            return badDate("Non-numeric component", s);
        }
        return d;
    }

    private static ValidationFailure badDate(String msg, CharSequence value) {
        ValidationFailure err = new ValidationFailure(
                "Invalid date " + Err.wrap(value, Err.VALUE) + " (" + msg + ")");
        err.setErrorCode("FORG0001");
        return err;
    }

    private static ValidationFailure badDate(String msg, CharSequence value, String errorCode) {
        ValidationFailure err = new ValidationFailure(
                "Invalid date " + Err.wrap(value, Err.VALUE) + " (" + msg + ")");
        err.setErrorCode(errorCode);
        return err;
    }


    /**
     * Determine whether a given date is valid
     *
     * @param year  the year (permitting year zero)
     * @param month the month (1-12)
     * @param day   the day (1-31)
     * @return true if this is a valid date
     */

    public static boolean isValidDate(int year, int month, int day) {
        return month > 0 && month <= 12 && day > 0 && day <= daysPerMonth[month - 1]
                || month == 2 && day == 29 && isLeapYear(year);
    }

    /**
     * Test whether a year is a leap year
     *
     * @param year the year (permitting year zero)
     * @return true if the supplied year is a leap year
     */

    public static boolean isLeapYear(int year) {
        return (year % 4 == 0) && !(year % 100 == 0 && !(year % 400 == 0));
    }

    /**
     * Check that the value can be handled in Saxon-JS
     *
     * @throws XPathException if it can't be handled in Saxon-JS
     */

    @Override
    public void checkValidInJavascript() throws XPathException {
        if (year <= 0 || year > 9999) {
            throw new XPathException("Year out of range for Saxon-JS", "FODT0001");
        }
    }

    /**
     * The equals() methods on atomic values is defined to follow the semantics of eq when applied
     * to two atomic values. When the other operand is not an atomic value, the result is undefined
     * (may be false, may be an exception). When the other operand is an atomic value that cannot be
     * compared with this one, the method returns false.
     * <p>The hashCode() method is consistent with equals().</p>
     * <p>This implementation performs a context-free comparison: it fails with ClassCastException
     * if one value has a timezone and the other does not.</p>
     *
     * @param o the other value
     * @return true if the other operand is an atomic value and the two values are equal as defined
     *         by the XPath eq operator
     * @throws ClassCastException if the values are not comparable
     */

    public boolean equals(Object o) {
        if (o instanceof GDateValue) {
            GDateValue gdv = (GDateValue) o;
            return getPrimitiveType() == gdv.getPrimitiveType() && toDateTime().equals(gdv.toDateTime());
        } else {
            return false;
        }
    }

    public int hashCode() {
        return DateTimeValue.hashCode(year, month, day, (byte) 12, (byte) 0, (byte) 0, 0, getTimezoneInMinutes());
    }

    /**
     * Compare this value to another value of the same type, using the supplied context object
     * to get the implicit timezone if required. This method implements the XPath comparison semantics.
     *
     * @param other   the value to be compared
     * @param implicitTimezone the implicit timezone to be used for a value with no timezone
     * @return -1 if this value is less, 0 if equal, +1 if greater
     */

    @Override
    public int compareTo(/*@NotNull*/ CalendarValue other, int implicitTimezone) throws NoDynamicContextException {
        if (getPrimitiveType() != other.getPrimitiveType()) {
            throw new ClassCastException("Cannot compare dates of different types");
            // covers, for example, comparing a gYear to a gYearMonth
        }
        GDateValue v2 = (GDateValue) other;
        if (getTimezoneInMinutes() == other.getTimezoneInMinutes()) {
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
            return 0;
        }
        return toDateTime().compareTo(other.toDateTime(), implicitTimezone);
    }

    /**
     * Convert to DateTime.
     *
     * @return the starting instant of the GDateValue (with the same timezone)
     */

    /*@NotNull*/
    @Override
    public DateTimeValue toDateTime() {
        return new DateTimeValue(year, month, day, (byte) 0, (byte) 0, (byte) 0, 0, getTimezoneInMinutes(), hasNoYearZero);
    }


    /*@NotNull*/
    @Override
    public Comparable getSchemaComparable() {
        return new GDateComparable();
    }

    /**
     * Get a component of the value. Returns null if the timezone component is
     * requested and is not present.
     * @param component the required component
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
            case TIMEZONE:
                if (hasTimezone()) {
                    return DayTimeDurationValue.fromMilliseconds(60000L * getTimezoneInMinutes());
                } else {
                    return null;
                }
            default:
                throw new IllegalArgumentException("Unknown component for date: " + component);
        }
    }

    private class GDateComparable implements Comparable {

        /*@NotNull*/
        public GDateValue asGDateValue() {
            return GDateValue.this;
        }

        @Override
        public int compareTo(/*@NotNull*/ Object o) {
            if (o instanceof GDateComparable) {
                if (asGDateValue().getPrimitiveType() != ((GDateComparable) o).asGDateValue().getPrimitiveType()) {
                    return SequenceTool.INDETERMINATE_ORDERING;
                }
                DateTimeValue dt0 = GDateValue.this.toDateTime();
                DateTimeValue dt1 = ((GDateComparable) o).asGDateValue().toDateTime();
                return dt0.getSchemaComparable().compareTo(dt1.getSchemaComparable());
            } else {
                return SequenceTool.INDETERMINATE_ORDERING;
            }
        }

        public boolean equals(/*@NotNull*/ Object o) {
            return compareTo(o) == 0;
        }

        public int hashCode() {
            return GDateValue.this.toDateTime().getSchemaComparable().hashCode();
        }
    }

}

