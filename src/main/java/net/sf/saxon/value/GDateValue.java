////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicMetadata;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ValidationFailure;

import java.math.BigDecimal;
import java.util.*;

/**
 * Abstract superclass for the primitive types containing date components: xs:date, xs:gYear,
 * xs:gYearMonth, xs:gMonth, xs:gMonthDay, xs:gDay
 */
public abstract class GDateValue extends CalendarValue {
    protected final int year;         // includes a year zero
    protected final int month;
    protected final int day;

    // The spec suggests 1972-12-31 as the default date when components are missing, but that causes
    // problems with gYearMonth. There's no particular reason for the choice other than the fact that it
    // has a leap second; but leap seconds aren't supported anyway.
    protected final static int DEFAULT_YEAR = 1972;
    protected final static int DEFAULT_MONTH = 1;
    protected final static int DEFAULT_DAY = 1;

    /**
     * Constructor that performs no validation
     * @param year the year value, allows year zero
     * @param month range 1-12
     * @param day range 1-31
     * @param tzMinutes offset in minutes, or NO_TIMEZONE
     * @param typeLabel the type annotation
     */
    public GDateValue(int year, int month, int day, int tzMinutes, AtomicMetadata typeLabel) {
        super(typeLabel, tzMinutes);
        this.year = year;
        this.month = month;
        this.day = day;
    }

    protected static class MutableGDateValue {
        public int year;       // the year as written
        public byte month;     // the month as written, range 1-12
        public byte day;       // the day as written, range 1-31
        public int tzMinutes = NO_TIMEZONE;
        public AtomicType typeLabel = BuiltInAtomicType.DATE_TIME;
        public ValidationFailure error = null;

        public MutableGDateValue() {}
        public MutableGDateValue(int year, int month, int day, int tzMinutes, AtomicType typeLabel) {
            this.year = year;
            this.month = (byte)month;
            this.day = (byte)day;
            this.tzMinutes = tzMinutes;
            this.typeLabel = typeLabel;
        }
    }

    protected EnumSet<AccessorFn.Component> getDefinedComponents() {
        return EnumSet.of(AccessorFn.Component.YEAR, AccessorFn.Component.MONTH, AccessorFn.Component.DAY);
    }

    /**
     * Test whether a candidate date is actually a valid date in the proleptic Gregorian calendar
     */

    protected static byte[] daysPerMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    protected static final short[] monthData = {306, 337, 0, 31, 61, 92, 122, 153, 184, 214, 245, 275};

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

    public int getMonth() {
        return month;
    }

    /**
     * Get the day component of the date (in local form)
     *
     * @return the day component (1-31)
     */

    public int getDay() {
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
            yr = -year;
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
     * Test whether a timezone value (expressed as the number of minutes) is valid
     */

    public static boolean isValidTimezone(int timezoneInMinutes) {
        return timezoneInMinutes == NO_TIMEZONE || Math.abs(timezoneInMinutes) <= (14 * 60);
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
        if (o instanceof GDateValue gdv) {
            return getPrimitiveType() == gdv.getPrimitiveType() && toDateTime().equals(gdv.toDateTime());
        } else {
            return false;
        }
    }

    public int hashCode() {
        return DateTimeValue.computeHashCode(year, month, day, 12, 0, BigDecimal.ZERO, getTimezoneInMinutes());
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
        return new DateTimeValue(year, month, day, 0, 0, BigDecimal.ZERO, getTimezoneInMinutes());
    }


    /*@NotNull*/
    public GDateComparable getSchemaComparable() {
        return new GDateComparable(this);
    }

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone, int specVersion) throws NoDynamicContextException {
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
            case YEAR:
                if (!getDefinedComponents().contains(component)) {
                    return null;
                }
                return Int64Value.makeIntegerValue(year);
            case MONTH:
                if (!getDefinedComponents().contains(component)) {
                    return null;
                }
                return Int64Value.makeIntegerValue(month);
            case DAY:
                if (!getDefinedComponents().contains(component)) {
                    return null;
                }
                return Int64Value.makeIntegerValue(day);
            case TIMEZONE:
                if (hasTimezone()) {
                    return DayTimeDurationValue.fromMilliseconds(60000L * getTimezoneInMinutes());
                } else {
                    return null;
                }
            case HOURS:
            case MINUTES:
            case SECONDS:
            case WHOLE_SECONDS: // Internal use only
                return null;
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

