////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ValidationFailure;

import java.util.*;

/**
 * Abstract superclass for the primitive types containing date components: xs:date, xs:gYear,
 * xs:gYearMonth, xs:gMonth, xs:gMonthDay, xs:gDay
 */
public abstract class GDateValue extends CalendarValue {
    protected final int year;         // unlike the lexical representation, includes a year zero
    protected final byte month;
    protected final byte day;
    protected final boolean hasNoYearZero;

    public GDateValue(int year, byte month, byte day, boolean hasNoYearZero, int tzMinutes, AtomicType typeLabel) {
        super(typeLabel, tzMinutes);
        this.year = year;
        this.month = month;
        this.day = day;
        this.hasNoYearZero = hasNoYearZero;
    }

    protected static class MutableGDateValue {
        public int year;       // the year as written, +1 for BC years
        public byte month;     // the month as written, range 1-12
        public byte day;       // the day as written, range 1-31
        public boolean hasNoYearZero;  // true if XSD 1.0 rules apply for negative years
        public int tzMinutes = NO_TIMEZONE;
        public AtomicType typeLabel = BuiltInAtomicType.DATE_TIME;
        public ValidationFailure error = null;

        public MutableGDateValue() {}
        public MutableGDateValue(int year, int month, int day, boolean hasNoYearZero, int tzMinutes, AtomicType typeLabel) {
            this.year = year;
            this.month = (byte)month;
            this.day = (byte)day;
            this.hasNoYearZero = hasNoYearZero;
            this.tzMinutes = tzMinutes;
            this.typeLabel = typeLabel;
        }
    }

    protected MutableGDateValue makeMutableCopy() {
        MutableGDateValue m = new MutableGDateValue();
        m.year = year;
        m.month = month;
        m.day = day;
        m.hasNoYearZero = hasNoYearZero;
        m.tzMinutes = getTimezoneInMinutes();
        m.typeLabel = typeLabel;
        return m;
    }

    protected GDateValue(MutableGDateValue m) {
        super(m.typeLabel, m.tzMinutes);
        this.year = m.year;
        this.month = m.month;
        this.day = m.day;
        this.hasNoYearZero = m.hasNoYearZero;
    }

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
     * @param m     the "raw" MutableGDateValue to be populated: this is modified in-situ. If the string is
     *              invalid, the error field of the MutableGDateValue will be set.
     * @param s     the supplied string value
     * @param allowYearZero true if (as in XSD 1.1) there is a year zero, false if (as in XSD 1.0) there is not
     */

    /*@NotNull*/
    protected static void setLexicalValue(MutableGDateValue m, UnicodeString s, boolean allowYearZero) {
        m.hasNoYearZero = !allowYearZero;
        StringTokenizer tok = new StringTokenizer(Whitespace.trim(s).toString(), "-:+TZ", true);
        try {
            if (!tok.hasMoreTokens()) {
                m.error = badDate("Too short", s);
                return;
            }
            String part = tok.nextToken();
            int era = +1;
            if ("+".equals(part)) {
                m.error = badDate("Date must not start with '+' sign", s);
                return;
            } else if ("-".equals(part)) {
                era = -1;
                if (!tok.hasMoreTokens()) {
                    m.error = badDate("No year after '-'", s);
                    return;
                }
                part = (String) tok.nextToken();
            }

            if (part.length() < 4) {
                m.error = badDate("Year is less than four digits", s);
                return;
            }
            if (part.length() > 4 && part.charAt(0) == '0') {
                m.error = badDate("When year exceeds 4 digits, leading zeroes are not allowed", s);
                return;
            }
            int value = DurationValue.simpleInteger(part);
            if (value < 0) {
                if (value == -1) {
                    m.error = badDate("Non-numeric year component", s);
                } else {
                    m.error = badDate("Year is outside the range that Saxon can handle", s, "FODT0001");
                }
                return;
            }
            m.year = value * era;
            if (m.year == 0 && !allowYearZero) {
                m.error = badDate("Year zero is not allowed", s);
                return;
            }
            if (era < 0 && !allowYearZero) {
                m.year++;      // if year zero not allowed, -0001 is the year before +0001, represented as 0 internally.
            }
            if (!tok.hasMoreTokens()) {
                m.error = badDate("Too short", s);
                return;
            }
            if (!"-".equals(tok.nextToken())) {
                m.error = badDate("Wrong delimiter after year", s);
                return;
            }

            if (!tok.hasMoreTokens()) {
                m.error = badDate("Too short", s);
                return;
            }
            part = tok.nextToken();
            if (part.length() != 2) {
                m.error = badDate("Month must be two digits", s);
                return;
            }
            value = DurationValue.simpleInteger(part);
            if (value < 0) {
                m.error = badDate("Non-numeric month component", s);
                return;
            }
            m.month = (byte) value;
            if (m.month < 1 || m.month > 12) {
                m.error = badDate("Month is out of range", s);
                return;
            }
            if (!tok.hasMoreTokens()) {
                m.error = badDate("Too short", s);
                return;
            }
            if (!"-".equals(tok.nextToken())) {
                m.error = badDate("Wrong delimiter after month", s);
                return;
            }

            if (!tok.hasMoreTokens()) {
                m.error = badDate("Too short", s);
                return;
            }
            part = (String) tok.nextToken();
            if (part.length() != 2) {
                m.error = badDate("Day must be two digits", s);
                return;
            }
            value = DurationValue.simpleInteger(part);
            if (value < 0) {
                m.error = badDate("Non-numeric day component", s);
                return;
            }
            m.day = (byte) value;
            if (m.day < 1 || m.day > 31) {
                m.error = badDate("Day is out of range", s);
                return;
            }

            int tzOffset;
            if (tok.hasMoreTokens()) {

                String delim = tok.nextToken();

                if ("T".equals(delim)) {
                    m.error = badDate("Value includes time", s);
                    return;
                } else if ("Z".equals(delim)) {
                    tzOffset = 0;
                    if (tok.hasMoreTokens()) {
                        m.error = badDate("Continues after 'Z'", s);
                        return;
                    }
                    m.tzMinutes = tzOffset;

                } else if (!(!"+".equals(delim) && !"-".equals(delim))) {
                    if (!tok.hasMoreTokens()) {
                        m.error = badDate("Missing timezone", s);
                        return;
                    }
                    part = (String) tok.nextToken();
                    value = DurationValue.simpleInteger(part);
                    if (value < 0) {
                        m.error = badDate("Non-numeric timezone hour component", s);
                        return;
                    }
                    int tzhour = value;
                    if (part.length() != 2) {
                        m.error = badDate("Timezone hour must be two digits", s);
                        return;
                    }
                    if (tzhour > 14) {
                        m.error = badDate("Timezone hour is out of range", s);
                        return;
                    }
                    if (!tok.hasMoreTokens()) {
                        m.error = badDate("No minutes in timezone", s);
                        return;
                    }
                    if (!":".equals(tok.nextToken())) {
                        m.error = badDate("Wrong delimiter after timezone hour", s);
                        return;
                    }

                    if (!tok.hasMoreTokens()) {
                        m.error = badDate("No minutes in timezone", s);
                        return;
                    }
                    part = (String) tok.nextToken();
                    value = DurationValue.simpleInteger(part);
                    if (value < 0) {
                        m.error = badDate("Non-numeric timezone minute component", s);
                        return;
                    }
                    int tzminute = value;
                    if (part.length() != 2) {
                        m.error = badDate("Timezone minute must be two digits", s);
                        return;
                    }
                    if (tzminute > 59) {
                        m.error = badDate("Timezone minute is out of range", s);
                        return;
                    }
                    if (tok.hasMoreTokens()) {
                        m.error = badDate("Continues after timezone", s);
                        return;
                    }

                    tzOffset = tzhour * 60 + tzminute;
                    if ("-".equals(delim)) {
                        tzOffset = -tzOffset;
                    }
                    m.tzMinutes = tzOffset;

                } else {
                    m.error = badDate("Timezone format is incorrect", s);
                    return;
                }
            }

            if (!isValidDate(m.year, m.month, m.day)) {
                m.error = badDate("Non-existent date", s);
            }

        } catch (NumberFormatException err) {
            m.error = badDate("Non-numeric component", s);
        }
    }

    private static ValidationFailure badDate(String msg, UnicodeString value) {
        ValidationFailure err = new ValidationFailure(
                "Invalid date " + Err.wrap(value, Err.VALUE) + " (" + msg + ")");
        err.setErrorCode("FORG0001");
        return err;
    }

    private static ValidationFailure badDate(String msg, UnicodeString value, String errorCode) {
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
        return DateTimeValue.computeHashCode(year, month, day, (byte) 12, (byte) 0, (byte) 0, 0, getTimezoneInMinutes());
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
    public GDateComparable getSchemaComparable() {
        return new GDateComparable(this);
    }

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone) throws NoDynamicContextException {
        return null;
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

    public static class GDateComparable implements Comparable<GDateComparable> {

        private final GDateValue value;

        public GDateComparable(GDateValue value) {
            this.value = value;
        }


        /*@NotNull*/
        public GDateValue asGDateValue() {
            return value;
        }

        @Override
        public int compareTo(GDateComparable o) {
            if (asGDateValue().getPrimitiveType() != o.asGDateValue().getPrimitiveType()) {
                return SequenceTool.INDETERMINATE_ORDERING;
            }
            DateTimeValue dt0 = value.toDateTime();
            DateTimeValue dt1 = o.value.toDateTime();
            return dt0.getSchemaComparable().compareTo(dt1.getSchemaComparable());
        }

        public boolean equals(/*@NotNull*/ Object o) {
            return o instanceof GDateComparable && compareTo((GDateComparable)o) == 0;
        }

        public int hashCode() {
            return value.toDateTime().getSchemaComparable().hashCode();
        }
    }

}

