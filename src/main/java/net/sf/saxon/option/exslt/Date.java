////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.exslt;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.Converter;
import net.sf.saxon.type.ValidationFailure;
import net.sf.saxon.value.*;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This class implements extension functions in the
 * http://exslt.org/dates-and-times namespace.
 */

public final class Date {

    /**
     * Private constructor to disallow instantiation
     */

    private Date() {
    }

    /**
     * The date:date-time function returns the current date and time as a date/time string.
     * <p>The date/time string that's returned must be a string in the format defined as the
     * lexical representation of xs:dateTime in [3.2.7 dateTime] of [XML Schema Part 2: Datatypes].</p>
     * <p>The date/time format is basically CCYY-MM-DDThh:mm:ss+hh:mm.</p>
     * <p>The date/time string format must include a time zone, either a Z to indicate
     * Coordinated Universal Time or a + or - followed by the difference between the
     * difference from UTC represented as hh:mm.</p>
     *
     * @param context the XPath dynamic context
     * @return the current date and time as a date/time string
     * @throws XPathException if the context does not allow a date and time to be obtained
     */

    public static String dateTime(XPathContext context) throws XPathException {
        return context.getCurrentDateTime().getStringValue();
    }

    /**
     * The date:date function returns the date specified in the date/time string given
     * as the argument. If no argument is given, then the current local date/time, as
     * returned by date:date-time is used as a default argument.
     * <p>The date/time string that's returned must be a string in the format defined as the
     * lexical representation of xs:dateTime in
     * <a href="http://www.w3.org/TR/xmlschema-2/#dateTime">[3.2.7 dateTime]</a> of
     * <a href="http://www.w3.org/TR/xmlschema-2/">[XML Schema Part 2: Datatypes]</a>.</p>
     * <p>If the argument is not in either of these formats, date:date returns an empty string ('').</p>
     * <p>The date/time format is basically CCYY-MM-DDThh:mm:ss, although implementers should consult
     * <a href="http://www.w3.org/TR/xmlschema-2/">[XML Schema Part 2: Datatypes]</a> and
     * <a href="http://www.iso.ch/markete/8601.pdf">[ISO 8601]</a> for details.</p>
     * <p>The date is returned as a string with a lexical representation as defined for xs:date in
     * [3.2.9 date] of [XML Schema Part 2: Datatypes]. The date format is basically CCYY-MM-DD,
     * although implementers should consult [XML Schema Part 2: Datatypes] and [ISO 8601] for details.
     * If no argument is given or the argument date/time specifies a time zone, then the date string
     * format must include a time zone, either a Z to indicate Coordinated Universal Time or a + or -
     * followed by the difference between the difference from UTC represented as hh:mm. If an argument
     * is specified and it does not specify a time zone, then the date string format must not include
     * a time zone.</p>
     *
     * @param context    the XPath dynamic context
     * @param datetimeIn the date and time as a date/time string
     * @return the date part of the dateTime supplied, or "" if invalid
     */

    public static String date(XPathContext context, String datetimeIn) {
        ConversionRules rules = context.getConfiguration().getConversionRules();
        datetimeIn = nn(datetimeIn);
        if (datetimeIn.indexOf('T') >= 0) {
            ConversionResult cr = DateTimeValue.makeDateTimeValue(datetimeIn, rules);
            if (cr instanceof ValidationFailure) {
                return "";
            } else {
                return ((DateTimeValue) cr).toDateValue().getStringValue();
            }
        } else {
            ConversionResult cr = DateValue.makeDateValue(datetimeIn, rules);
            if (cr instanceof ValidationFailure) {
                return "";
            } else {
                return ((AtomicValue) cr).getStringValue();
            }
        }
    }

    /**
     * The date:date function returns the current date.
     *
     * @param context the XPath dynamic context
     * @return the current date as a string
     * @throws XPathException if the context does not allow the date and time to be established
     */

    public static String date(XPathContext context) throws XPathException {
        return date(context, dateTime(context));
    }

    /**
     * The date:time function returns the time specified in the date/time string given as the
     * argument.
     *
     * @param context  the XPath dynamic context
     * @param dateTime must start with [+|-]CCYY-MM-DDThh:mm:ss
     * @return the time part of the string, or "" if invalid
     */

    public static String time(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        if (dateTime.indexOf('T') >= 0) {
            ConversionResult cr = DateTimeValue.makeDateTimeValue(dateTime, context.getConfiguration().getConversionRules());
            if (cr instanceof ValidationFailure) {
                return "";
            } else {
                return ((DateTimeValue) cr).toTimeValue().getStringValue();
            }
        } else {
            ConversionResult cr = TimeValue.makeTimeValue(dateTime);
            if (cr instanceof ValidationFailure) {
                return "";
            } else {
                return ((AtomicValue) cr).getStringValue();
            }
        }
    }

    /**
     * The date:time function returns the current time.
     *
     * @param context the XPath dynamic context
     * @return the current time as a string
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static String time(XPathContext context) throws XPathException {
        return time(context, dateTime(context));
    }

    /**
     * The date:year function returns the year of a date as a number. If no
     * argument is given, then the current local date/time, as returned by
     * date:date-time is used as a default argument.
     * <p>The date/time string specified as the first argument must be a right-truncated
     * string in the format defined as the lexical representation of xs:dateTime in one
     * of the formats defined in
     * <a href="http://www.w3.org/TR/xmlschema-2/">[XML Schema Part 2: Datatypes]</a>.</p>
     * <p>The permitted formats are as follows:</p>
     * <ul>
     * <li>xs:dateTime (CCYY-MM-DDThh:mm:ss)</li>
     * <li>xs:date (CCYY-MM-DD)</li>
     * <li>xs:gYearMonth (CCYY-MM)</li>
     * <li>xs:gYear (CCYY)</li>
     * </ul>
     * <p>If the date/time string is not in one of these formats, then NaN is returned.</p>
     * <p>Note: although not specifically permitted in the EXSLT specification, the Saxon
     * implementation also allows the input value to contain a timezone</p>
     *
     * @param context    the XPath dynamic context
     * @param datetimeIn the supplied date/time in ISO format
     * @return the year component of the supplied date time, or NaN if invalid
     */
    public static double year(XPathContext context, String datetimeIn) {
        datetimeIn = nn(datetimeIn);
        try {
            ConversionResult cr = CalendarValue.makeCalendarValue(datetimeIn, context.getConfiguration().getConversionRules());
            if (cr instanceof ValidationFailure) {
                return Double.NaN;
            }
            if (cr instanceof GMonthValue || cr instanceof GMonthDayValue ||
                    cr instanceof GDayValue || cr instanceof TimeValue) {
                return Double.NaN;
            }
            AtomicValue year = ((CalendarValue) cr).getComponent(AccessorFn.Component.YEAR);
            return ((NumericValue) year).getDoubleValue();
        } catch (XPathException e) {
            return Double.NaN;
        }
    }

    /**
     * The date:year function returns the current year.
     *
     * @param context the XPath dynamic context
     * @return the current year as a double
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static double year(XPathContext context) throws XPathException {
        return year(context, dateTime(context));
    }

    /**
     * Return true if the year specified in the date/time string
     * given as the argument is a leap year.
     *
     * @param context  the XPath dynamic context
     * @param dateTime a dateTime as a string
     * @return true if the year is a leap year (false if not, or if input is invalid)
     */

    public static boolean leapYear(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        double year = year(context, dateTime);
        if (Double.isNaN(year)) {
            return false;
        }
        int y = (int) year;
        return (y % 4 == 0) && !((y % 100 == 0) && !(y % 400 == 0));
    }

    /**
     * Returns true if the current year is a leap year
     *
     * @param context the XPath dynamic context
     * @return true if the current year is a leap year (false if not, or if input is invalid)
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static boolean leapYear(XPathContext context) throws XPathException {
        return leapYear(context, dateTime(context));
    }

    /**
     * Return the month number from a date.
     * The date must start with either "CCYY-MM" or "--MM"
     *
     * @param context  the XPath dynamic context
     * @param dateTime a dateTime as a string
     * @return the month extracted from the dateTime
     */

    public static double monthInYear(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        try {
            ConversionResult cr = CalendarValue.makeCalendarValue(dateTime, context.getConfiguration().getConversionRules());
            if (cr instanceof ValidationFailure) {
                return Double.NaN;
            }
            if (cr instanceof GYearValue ||
                    cr instanceof GDayValue || cr instanceof TimeValue) {
                return Double.NaN;
            }
            AtomicValue month = ((CalendarValue) cr).getComponent(AccessorFn.Component.MONTH);
            return ((NumericValue) month).getDoubleValue();
        } catch (XPathException e) {
            return Double.NaN;
        }
    }

    /**
     * Return the month number from the current date.
     *
     * @param context the XPath dynamic context
     * @return the current month number
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static double monthInYear(XPathContext context) throws XPathException {
        return monthInYear(context, dateTime(context));
    }

    /**
     * Return the month name from a date.
     * The date must start with either "CCYY-MM" or "--MM"
     *
     * @param context the XPath dynamic context
     * @param date    the date/time as a string
     * @return the English month name, for example "January", "February"
     */

    public static String monthName(XPathContext context, String date) {
        date = nn(date);
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        double m = monthInYear(context, date);
        if (Double.isNaN(m)) {
            return "";
        }
        return months[(int) m - 1];
    }

    /**
     * Return the month name from the current date.
     *
     * @param context the XPath dynamic context
     * @return the English month name, for example "January", "February"
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static String monthName(XPathContext context) throws XPathException {
        return monthName(context, dateTime(context));
    }

    /**
     * Return the month abbreviation from a date.
     *
     * @param context the XPath dynamic context
     * @param date    The date must start with either "CCYY-MM" or "--MM"
     * @return the English month abbreviation, for example "Jan", "Feb"
     */

    public static String monthAbbreviation(XPathContext context, String date) {
        date = nn(date);
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        double m = monthInYear(context, date);
        if (Double.isNaN(m)) {
            return "";
        }
        return months[(int) m - 1];
    }

    /**
     * Return the month abbreviation from the current date.
     *
     * @param context the XPath dynamic context
     * @return the English month abbreviation, for example "Jan", "Feb"
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static String monthAbbreviation(XPathContext context) throws XPathException {
        return monthAbbreviation(context, dateTime(context));
    }

    /**
     * Return the ISO week number of a specified date within the year
     * (Note, this returns the ISO week number: the result in EXSLT is underspecified)
     *
     * @param context  the XPath dynamic context
     * @param dateTime the current date starting CCYY-MM-DD
     * @return the ISO week number
     */

    public static double weekInYear(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        int dayInYear = (int) dayInYear(context, dateTime);
        String firstJan = dateTime.substring(0, 4) + "-01-01";
        int jan1day = ((int) dayInWeek(context, firstJan) + 5) % 7;
        int daysInFirstWeek = jan1day == 0 ? 0 : 7 - jan1day;

        int rawWeek = (dayInYear - daysInFirstWeek + 6) / 7;

        if (daysInFirstWeek >= 4) {
            return rawWeek + 1;
        } else {
            if (rawWeek > 0) {
                return rawWeek;
            } else {
                // week number should be 52 or 53: same as 31 Dec in previous year
                int lastYear = Integer.parseInt(dateTime.substring(0, 4)) - 1;
                String dec31 = lastYear + "-12-31";
                // assumes year > 999
                return weekInYear(context, dec31);
            }
        }
    }

    /**
     * Return the ISO week number of the current date
     * (Note, this returns the ISO week number: the result in EXSLT is underspecified)
     *
     * @param context the XPath dynamic context
     * @return the ISO week number
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static double weekInYear(XPathContext context) throws XPathException {
        return weekInYear(context, dateTime(context));
    }

    /**
     * Return the week number of a specified date within the month
     * (Note, this function is underspecified in EXSLT)
     *
     * @param context  the XPath dynamic context
     * @param dateTime the date starting CCYY-MM-DD
     * @return the week number within the month
     */

    public static double weekInMonth(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        return (double) (int) ((dayInMonth(context, dateTime) - 1) / 7 + 1);
    }

    /**
     * Return the ISO week number of the current date within the month
     *
     * @param context the XPath dynamic context
     * @return the week number within the month
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static double weekInMonth(XPathContext context) throws XPathException {
        return weekInMonth(context, dateTime(context));
    }

    /**
     * Return the day number of a specified date within the year
     *
     * @param context  the XPath dynamic context
     * @param dateTime the date starting with CCYY-MM-DD
     * @return the day number within the year, as a double
     */

    public static double dayInYear(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        int month = (int) monthInYear(context, dateTime);
        int day = (int) dayInMonth(context, dateTime);
        int[] prev = {0,
                31,
                31 + 28,
                31 + 28 + 31,
                31 + 28 + 31 + 30,
                31 + 28 + 31 + 30 + 31,
                31 + 28 + 31 + 30 + 31 + 30,
                31 + 28 + 31 + 30 + 31 + 30 + 31,
                31 + 28 + 31 + 30 + 31 + 30 + 31 + 31,
                31 + 28 + 31 + 30 + 31 + 30 + 31 + 31 + 30,
                31 + 28 + 31 + 30 + 31 + 30 + 31 + 31 + 30 + 31,
                31 + 28 + 31 + 30 + 31 + 30 + 31 + 31 + 30 + 31 + 30,
                31 + 28 + 31 + 30 + 31 + 30 + 31 + 31 + 30 + 31 + 30 + 31};
        int leap = month > 2 && leapYear(context, dateTime) ? 1 : 0;
        return prev[month - 1] + leap + day;
    }

    /**
     * Return the day number of the current date within the year
     *
     * @param context the XPath dynamic context
     * @return the day number within the year, as a double
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static double dayInYear(XPathContext context) throws XPathException {
        return dayInYear(context, dateTime(context));
    }

    /**
     * Return the day number of a specified date within the month
     *
     * @param context  the XPath dynamic context
     * @param dateTime must start with CCYY-MM-DD, or --MM-DD, or ---DD
     * @return the day number within the month, as a double
     */

    public static double dayInMonth(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        try {
            ConversionResult cr = CalendarValue.makeCalendarValue(dateTime, context.getConfiguration().getConversionRules());
            if (cr instanceof ValidationFailure) {
                return Double.NaN;
            }
            if (cr instanceof GYearValue || cr instanceof GYearMonthValue ||
                    cr instanceof GMonthValue || cr instanceof TimeValue) {
                return Double.NaN;
            }
            AtomicValue day = ((CalendarValue) cr).getComponent(AccessorFn.Component.DAY);
            return ((NumericValue) day).getDoubleValue();
        } catch (XPathException e) {
            return Double.NaN;
        }
    }

    /**
     * Return the day number of the current date within the month
     *
     * @param context the XPath dynamic context
     * @return the current day number, as a double
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static double dayInMonth(XPathContext context) throws XPathException {
        return dayInMonth(context, dateTime(context));
    }

    /**
     * Return the day-of-the-week in a month of a date as a number
     * (for example 3 for the 3rd Tuesday in May).
     *
     * @param context  the XPath dynamic context
     * @param dateTime must start with CCYY-MM-DD
     * @return the the day-of-the-week in a month of a date as a number
     *         (for example 3 for the 3rd Tuesday in May).
     */

    public static double dayOfWeekInMonth(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        double dd = dayInMonth(context, dateTime);
        if (Double.isNaN(dd)) {
            return dd;
        }
        return ((int) dd - 1) / 7 + 1;
    }

    /**
     * Return the day-of-the-week in a month of the current date as a number
     * (for example 3 for the 3rd Tuesday in May).
     *
     * @param context the XPath dynamic context
     * @return the the day-of-the-week in a month of the current date as a number
     *         (for example 3 for the 3rd Tuesday in May).
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static double dayOfWeekInMonth(XPathContext context) throws XPathException {
        return dayOfWeekInMonth(context, dateTime(context));
    }

    /**
     * Return the day of the week given in a date as a number.
     * The numbering of days of the week starts at 1 for Sunday, 2 for Monday
     * and so on up to 7 for Saturday.
     *
     * @param context  the XPath dynamic context
     * @param dateTime must start with CCYY-MM-DD
     * @return the day of the week as a number
     */

    public static double dayInWeek(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        double yy = year(context, dateTime);
        double mm = monthInYear(context, dateTime);
        double dd = dayInMonth(context, dateTime);
        if (Double.isNaN(yy) || Double.isNaN(mm) || Double.isNaN(dd)) {
            return Double.NaN;
        }
        GregorianCalendar calDate =
                new GregorianCalendar(
                        (int) yy,
                        (int) mm - 1,
                        (int) dd);
        calDate.setFirstDayOfWeek(Calendar.SUNDAY);
        return calDate.get(Calendar.DAY_OF_WEEK);
    }

    /**
     * Return the day of the week in the current date as a number.
     * The numbering of days of the week starts at 1 for Sunday, 2 for Monday
     * and so on up to 7 for Saturday.
     *
     * @param context the XPath dynamic context
     * @return the day of the week as a number
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static double dayInWeek(XPathContext context) throws XPathException {
        return dayInWeek(context, dateTime(context));
    }

    /**
     * Return the day of the week given in a date as an English day name:
     * one of 'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday' or 'Friday'.
     *
     * @param context  the XPath dynamic context
     * @param dateTime must start with CCYY-MM-DD
     * @return the English name of the day of the week
     */

    public static String dayName(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
                "Saturday"};
        double d = dayInWeek(context, dateTime);
        if (Double.isNaN(d)) {
            return "";
        }
        return days[(int) d - 1];
    }

    /**
     * Return the day of the week given in the current date as an English day name:
     * one of 'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday' or 'Friday'.
     *
     * @param context the XPath dynamic context
     * @return the English name of the day of the week
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static String dayName(XPathContext context) throws XPathException {
        return dayName(context, dateTime(context));
    }

    /**
     * Return the day of the week given in a date as an English day abbreviation:
     * one of 'Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', or 'Sat'.
     *
     * @param context  the XPath dynamic context
     * @param dateTime must start with CCYY-MM-DD
     * @return the English day abbreviation
     */

    public static String dayAbbreviation(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        double d = dayInWeek(context, dateTime);
        if (Double.isNaN(d)) {
            return "";
        }
        return days[(int) d - 1];
    }

    /**
     * Return the day of the week given in the current date as an English day abbreviation:
     * one of 'Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', or 'Sat'.
     *
     * @param context the XPath dynamic context
     * @return the English day abbreviation
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static String dayAbbreviation(XPathContext context) throws XPathException {
        return dayAbbreviation(context, dateTime(context));
    }

    /**
     * Return the hour of the day in the specified date or date/time
     *
     * @param context  the XPath dynamic context
     * @param dateTime must start with CCYY-MM-DDThh:mm:ss or hh:mm:ss
     * @return the hour
     */

    public static double hourInDay(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        try {
            ConversionResult cr = CalendarValue.makeCalendarValue(dateTime, context.getConfiguration().getConversionRules());
            if (cr instanceof ValidationFailure) {
                return Double.NaN;
            }
            if (!(cr instanceof DateTimeValue || cr instanceof TimeValue)) {
                return Double.NaN;
            }
            AtomicValue hour = ((CalendarValue) cr).getComponent(AccessorFn.Component.HOURS);
            return ((NumericValue) hour).getDoubleValue();
        } catch (XPathException e) {
            return Double.NaN;
        }
    }

    /**
     * Return the current hour of the day
     *
     * @param context the XPath dynamic context
     * @return the hour
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static double hourInDay(XPathContext context) throws XPathException {
        return hourInDay(context, dateTime(context));
    }

    /**
     * Return the minute of the hour in the specified date or date/time
     *
     * @param context  the XPath dynamic context
     * @param dateTime must start with CCYY-MM-DDThh:mm:ss or hh:mm:ss
     * @return the minute
     */

    public static double minuteInHour(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        try {
            ConversionResult cr = CalendarValue.makeCalendarValue(dateTime, context.getConfiguration().getConversionRules());
            if (cr instanceof ValidationFailure) {
                return Double.NaN;
            }
            if (!(cr instanceof DateTimeValue || cr instanceof TimeValue)) {
                return Double.NaN;
            }
            AtomicValue minute = ((CalendarValue) cr).getComponent(AccessorFn.Component.MINUTES);
            return ((NumericValue) minute).getDoubleValue();
        } catch (XPathException e) {
            return Double.NaN;
        }
    }

    /**
     * Return the current minute of the hour
     *
     * @param context the XPath dynamic context
     * @return the minute
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static double minuteInHour(XPathContext context) throws XPathException {
        return minuteInHour(context, dateTime(context));
    }

    /**
     * Return the second of the minute in the specified date or date/time
     *
     * @param context  the XPath dynamic context
     * @param dateTime must start with CCYY-MM-DDThh:mm:ss or hh:mm:ss
     * @return the second
     */

    public static double secondInMinute(XPathContext context, String dateTime) {
        dateTime = nn(dateTime);
        try {
            ConversionResult cr = CalendarValue.makeCalendarValue(dateTime, context.getConfiguration().getConversionRules());
            if (cr instanceof ValidationFailure) {
                return Double.NaN;
            }
            if (!(cr instanceof DateTimeValue || cr instanceof TimeValue)) {
                return Double.NaN;
            }
            AtomicValue second = ((CalendarValue) cr).getComponent(AccessorFn.Component.SECONDS);
            return ((NumericValue) second).getDoubleValue();
        } catch (XPathException e) {
            return Double.NaN;
        }
    }

    /**
     * Return the current second of the minute
     *
     * @param context the XPath dynamic context
     * @return the second
     * @throws XPathException if the context does not allow a date/time to be established
     */

    public static double secondInMinute(XPathContext context) throws XPathException {
        return secondInMinute(context, dateTime(context));
    }

    /**
     * The date:add function returns the date/time resulting from adding a duration to a date/time.
     * The first argument must be right-truncated date/time strings in one of the formats defined in
     * [XML Schema Part 2: Datatypes].
     * <p>The permitted formats are as follows:</p>
     * <ul>
     * <li>xs:dateTime (CCYY-MM-DDThh:mm:ss)</li>
     * <li>xs:date (CCYY-MM-DD)</li>
     * <li>xs:gYearMonth (CCYY-MM)</li>
     * <li>xs:gYear (CCYY)</li>
     * </ul>
     * <p>The second argument is a string in the format defined for xs:duration in [3.2.6 duration] of
     * [XML Schema Part 2: Datatypes].</p>
     * <p>The return value is a right-truncated date/time strings in one of the formats defined in
     * [XML Schema Part 2: Datatypes] and listed above.
     * This value is calculated using the algorithm described in
     * [Appendix E Adding durations to dateTimes] of [XML Schema Part 2: Datatypes].</p>
     *
     * @param context    the XPath dynamic context
     * @param datetimeIn the supplied date/time in ISO date/time format
     * @param durationIn the supplied duration in ISO duration format
     * @return the result of the addition, as a string representing a dateTimeValue
     * @throws XPathException if the supplied date or duration is invalid
     */
    public static String add(XPathContext context, String datetimeIn, String durationIn) throws XPathException {
        datetimeIn = nn(datetimeIn);
        durationIn = nn(durationIn);
        ConversionResult cr0 = CalendarValue.makeCalendarValue(datetimeIn, context.getConfiguration().getConversionRules());
        if (cr0 instanceof ValidationFailure) {
            return "";
        }
        CalendarValue cv0 = (CalendarValue) cr0;
        if (specificity(cv0) < 0) {
            return "";
        }
        DateTimeValue v0 = cv0.toDateTime();
        ConversionResult cr1 = DurationValue.makeDuration(durationIn);
        if (cr1 instanceof ValidationFailure) {
            return "";
        }
        DurationValue v1 = (DurationValue) cr1;
        YearMonthDurationValue v1m = Converter.DurationToYearMonthDuration.INSTANCE.convert(v1);
        DayTimeDurationValue v1s = Converter.DurationToDayTimeDuration.INSTANCE.convert(v1);
        DateTimeValue sum = v0.add(v1m).add(v1s);
        return Converter.convert(sum, cv0.getPrimitiveType(), context.getConfiguration().getConversionRules()).getStringValue();
    }

    /**
     * The date:sum function adds a set of durations together.
     * <p>The string values of the nodes in the node set passed as an argument
     * are interpreted as durations and added together as if using the date:add-duration
     * function. The string values of the nodes in the node set passed as the argument
     * must be in the format defined for xs:duration in [3.2.6 duration] of
     * [XML Schema Part 2: Datatypes]. If any of the string values of these nodes
     * are not in this format, or if the node set is empty, the function returns an
     * empty string (''). The result is a string in the format defined for xs:duration
     * in [3.2.6 duration] of [XML Schema Part 2: Datatypes].</p>
     * <p>The durations can be summed by summing the numbers given for each of the components in the durations.</p>
     *
     *
     * @param durations a sequence which when atomized and converted to strings using the string()
     *                  function yields a sequence of strings representing the input durations in ISO format
     * @return a string representation of the total duration, or "" if any input is invalid or if the
     *         input is an empty sequence
     * @throws XPathException if the input sequence cannot be evaluated
     */

    public static String sum(SequenceIterator durations) throws XPathException {
        DurationValue tot = (DurationValue) DurationValue.makeDuration("PT0S");
        while (true) {
            Item it = durations.next();
            if (it == null) {
                break;
            }
            ConversionResult cr = DurationValue.makeDuration(it.getStringValueCS());
            if (cr instanceof ValidationFailure) {
                return "";
            }
            tot = addDurationValues(tot, (DurationValue) cr);
            if (tot == null) {
                return "";
            }
        }
        return tot.getStringValue();
    }

    /**
     * The date:add-duration function returns the duration resulting from adding two durations together.
     * <p>Both arguments are strings in the format defined for xs:duration in [3.2.6 duration] of
     * [XML Schema Part 2: Datatypes]. If either argument is not in this format, the function returns
     * an empty string (''). The return value is a string in the format defined for xs:duration in
     * [3.2.6 duration] of [XML Schema Part 2: Datatypes]. The durations can usually be added by
     * summing the numbers given for each of the components in the durations. However, if the durations
     * are differently signed, then this sometimes results in durations that are impossible to express
     * in this syntax (e.g. 'P1M' + '-P1D'). In these cases, the function returns an empty string ('').</p>
     *
     *
     * @param duration0 the first operand, a string in the format of an ISO duration
     * @param duration1 the second operand, a string in the format of an ISO duration
     * @return a string representation of the total duration, or "" if the input is invalid or the
     *         total is inexpressible
     */

    public String addDuration(String duration0, String duration1) {
        duration0 = nn(duration0);
        duration1 = nn(duration1);
        ConversionResult dv0 = DurationValue.makeDuration(duration0);
        ConversionResult dv1 = DurationValue.makeDuration(duration1);
        if (dv0 instanceof ValidationFailure || dv1 instanceof ValidationFailure) {
            return "";
        }
        DurationValue result = addDurationValues((DurationValue) dv0, (DurationValue) dv1);
        return result == null ? "" : result.getStringValue();
    }

    /**
     * Add two duration values, according to the rules for the add-duration() and sum() functions
     *
     *
     * @param dv0     the first duration
     * @param dv1     the second duration
     * @return the sum of the two durations, or null if they are not summable (e.g. P1Y + -P1D)
     */

    /*@Nullable*/
    private static DurationValue addDurationValues(DurationValue dv0, DurationValue dv1) {
        YearMonthDurationValue dv0m = Converter.DurationToYearMonthDuration.INSTANCE.convert(dv0);
        DayTimeDurationValue dv0s = Converter.DurationToDayTimeDuration.INSTANCE.convert(dv0);
        YearMonthDurationValue dv1m = Converter.DurationToYearMonthDuration.INSTANCE.convert(dv1);
        DayTimeDurationValue dv1s = Converter.DurationToDayTimeDuration.INSTANCE.convert(dv1);
        int months = dv0m.getLengthInMonths() + dv1m.getLengthInMonths();
        long micros = dv0s.getLengthInMicroseconds() + dv1s.getLengthInMicroseconds();
        if (Integer.signum(months) * Long.signum(micros) < 0) {
            return null;
        }
        boolean positive = months >= 0 && micros >= 0;
        if (!positive) {
            months = -months;
            micros = -micros;
        }
        return new DurationValue(positive, 0, months,
                0, 0, 0, (int) (micros / 1000000), (int) (micros % 1000000), BuiltInAtomicType.DURATION);
    }

    /**
     * The date:difference function returns the duration between the first date and the second date.
     * <p>If the first date occurs before the second date, then the result is a positive duration;
     * if it occurs after the second date, the result is a negative duration.</p>
     * <p>The two dates must both be right-truncated date/time strings in one of the formats defined in
     * [XML Schema Part 2: Datatypes].</p>
     * <p>The date/time with the most specific format (i.e. the least truncation) is converted into the
     * same format as the date with the most specific format (i.e. the most truncation).</p>
     * <p>The permitted formats are as follows, from most specific to least specific:</p>
     * <ul>
     * <li>xs:dateTime (CCYY-MM-DDThh:mm:ss)</li>
     * <li>xs:date (CCYY-MM-DD)</li>
     * <li>xs:gYearMonth (CCYY-MM)</li>
     * <li>xs:gYear (CCYY)</li>
     * </ul>
     * <p>If either of the arguments is not in one of these formats, date:difference
     * returns the empty string ('').</p>
     * <p>The difference between the date/times is returned as a string in the format defined for
     * xs:duration in [3.2.6 duration] of [XML Schema Part 2: Datatypes].</p>
     * <p>If the date/time string with the least specific format is in either xs:gYearMonth or xs:gYear format,
     * then the number of days, hours, minutes and seconds in the duration string must be equal to zero.
     * (The format of the string will be PnYnM.)
     * The number of months specified in the duration must be less than 12.
     * Otherwise, the number of years and months in the duration string must be equal to zero.
     * (The format of the string will be PnDTnHnMnS.)
     * The number of seconds specified in the duration string must be less than 60;
     * the number of minutes must be less than 60; the number of hours must be less than 24.</p>
     *
     * @param context     the XPath dynamic context
     * @param dateLeftIn  the first operand, a string in the format of an ISO dateTime, date, yearMonth, or year
     * @param dateRightIn the second operand, a string in the format of an ISO dateTime, date, yearMonth, or year
     * @return a string representation of the difference as an ISO duration, or "" if the input is invalid or the
     *         difference is inexpressible
     */

    public static String difference(XPathContext context, String dateLeftIn, String dateRightIn) {
        try {
            dateLeftIn = nn(dateLeftIn);
            dateRightIn = nn(dateRightIn);
            final ConversionRules rules = context.getConfiguration().getConversionRules();
            ConversionResult op0 = CalendarValue.makeCalendarValue(dateLeftIn, rules);
            ConversionResult op1 = CalendarValue.makeCalendarValue(dateRightIn, rules);
            if (op0 instanceof ValidationFailure || op1 instanceof ValidationFailure) {
                return "";
            }
            CalendarValue v0 = (CalendarValue) op0;
            CalendarValue v1 = (CalendarValue) op1;
            int s0 = specificity(v0);
            int s1 = specificity(v1);
            if (s0 < 0 || s1 < 0) {
                return "";
            }
            if (s0 < s1) {
                v1 = (CalendarValue) Converter.convert(v1, v0.getPrimitiveType(), rules);
            } else if (s1 < s0) {
                v0 = (CalendarValue) Converter.convert(v0, v1.getPrimitiveType(), rules);
            }
            if (v0 instanceof GYearValue) {
                int y0 = ((GYearValue) v0).getYear();
                int y1 = ((GYearValue) v1).getYear();
                return YearMonthDurationValue.fromMonths(12 * (y1 - y0)).getStringValue();
            } else if (v0 instanceof GYearMonthValue) {
                int y0 = ((GYearMonthValue) v0).getYear();
                int y1 = ((GYearMonthValue) v1).getYear();
                int m0 = ((GYearMonthValue) v0).getMonth();
                int m1 = ((GYearMonthValue) v1).getMonth();
                return YearMonthDurationValue.fromMonths(12 * (y1 - y0) + (m1 - m0)).getStringValue();
            } else {
                DateTimeValue dt0 = v0.toDateTime();
                DateTimeValue dt1 = v1.toDateTime();
                return dt1.subtract(dt0, context).getStringValue();
            }
        } catch (XPathException e) {
            return "";
        }
    }

    /**
     * Rank the right-truncated calendar types in order of specificity
     *
     * @param val a value of a given calendar type
     * @return the specificity of the type: 0 for least specific, 4 for most specific, -1 if this
     *         is not a right-truncated type
     */

    private static int specificity(CalendarValue val) {
        if (val instanceof GYearValue) {
            return 0;
        } else if (val instanceof GYearMonthValue) {
            return 1;
        } else if (val instanceof DateValue) {
            return 2;
        } else if (val instanceof DateTimeValue) {
            return 3;
        } else {
            return -1;
        }
    }

    /**
     * The date:duration function returns a duration string representing the number of seconds
     * specified by the argument string. If no argument is given, then the result of calling
     * date:seconds without any arguments is used as a default argument. The duration is returned
     * as a string in the format defined for xs:duration in [3.2.6 duration] of
     * [XML Schema Part 2: Datatypes]. The number of years and months in the duration string
     * must be equal to zero. (The format of the string will be PnDTnHnMnS.)
     * The number of seconds specified in the duration string must be less than 60;
     * the number of minutes must be less than 60; the number of hours must be less than 24.
     * If the argument is Infinity, -Infinity or NaN, then date:duration returns an empty string ('').
     *
     * @param seconds the input number of seconds
     * @return the duration as a string in ISO format
     */

    public static String duration(double seconds) {
        DayTimeDurationValue v = DayTimeDurationValue.fromSeconds(new BigDecimal(seconds));
        return v.getStringValue();
    }

    /**
     * Return the number of seconds since 1 Jan 1970
     *
     * @param context the XPath dynamic context
     * @return the number of seconds since 1 Jan 1970 (the "epoch" according to Java and Unix)
     * @throws XPathException if the context does not allow a date/time to be established
     */
    public static double seconds(XPathContext context) throws XPathException {
        DateTimeValue now = context.getCurrentDateTime();
        DurationValue diff = now.subtract(DateTimeValue.EPOCH, context);
        return diff.getLengthInSeconds();
    }

    /**
     * The date:seconds function returns the number of seconds specified by the argument string.
     * If no argument is given, then the current local date/time, as returned by date:date-time
     * is used as a default argument.
     * <p>The argument string may be in one of the following formats:</p>
     * <ol>
     * <li>A right-truncated date/time string in one of the formats defined in
     * <a href="http://www.w3.org/TR/xmlschema-2/">[XML Schema Part 2: Datatypes]</a>.
     * In these cases, the difference between the date/time string and 1970-01-01T00:00:00Z
     * is calculated as with date:difference and the result is converted to seconds with
     * date:seconds.
     * The legal formats are as follows:
     * <ul>
     * <li>xs:dateTime (CCYY-MM-DDThh:mm:ss)</li>
     * <li>xs:date (CCYY-MM-DD)</li>
     * <li>xs:gYearMonth (CCYY-MM)</li>
     * <li>xs:gYear (CCYY)</li>
     * </ul>
     * </li>
     * <li>A duration specified in days, hours, minutes and seconds in the format defined for
     * xs:duration in
     * <a href="http://www.w3.org/TR/xmlschema-2/#duration">[3.2.6 duration]</a>
     * of
     * <a href="http://www.w3.org/TR/xmlschema-2/">[XML Schema Part 2: Datatypes]</a>.
     * The number of years and months in the duration string must both be equal to zero:
     * either P0Y0M120D or P120D are permitted, but P3M is not.
     * If the argument to date:seconds is defined as a duration, the number returned is the
     * result of converting the duration to seconds by assuming that
     * 1 day = 24 hours, 1 hour = 60 minutes and 1 minute = 60 seconds.
     * The permitted duration format is basically PnDTnHnMnS, although implementers should
     * consult
     * <a href="http://www.w3.org/TR/xmlschema-2/">[XML Schema Part 2: Datatypes]</a>
     * and
     * <a href="http://www.iso.ch/markete/8601.pdf">[ISO 8601]</a>
     * for details.
     * </li>
     * </ol>
     * <p>If the argument is not in any of these formats, date:seconds returns NaN.</p>
     *
     * @param context    the XPath dynamic context
     * @param datetimeIn the input dateTime, date, yearMonth, or year as an ISO string
     * @return the number of seconds since 1 Jan 1970 (the "epoch" according to Java and Unix)
     */
    public static double seconds(XPathContext context, String datetimeIn) {
        try {
            datetimeIn = nn(datetimeIn);
            ConversionResult cr = CalendarValue.makeCalendarValue(datetimeIn, context.getConfiguration().getConversionRules());
            if (cr instanceof DateTimeValue || cr instanceof DateValue ||
                    cr instanceof GYearValue || cr instanceof GYearMonthValue) {
                DateTimeValue dateTime = ((CalendarValue) cr).toDateTime();
                DayTimeDurationValue diff = dateTime.subtract(DateTimeValue.EPOCH, context);
                return diff.getLengthInSeconds();
            }
            cr = DurationValue.makeDuration(datetimeIn);
            if (cr instanceof DurationValue) {
                DurationValue duration = (DurationValue) cr;
                if (duration.getYears() != 0 || duration.getMonths() != 0) {
                    return Double.NaN;
                } else {
                    return duration.getLengthInSeconds();
                }
            } else {
                return Double.NaN;
            }
        } catch (XPathException e) {
            return Double.NaN;
        }
    }

    /**
     * Treat null as zero-length string
     *
     * @param in input string
     * @return zero-length string if input is null, else the input string unchanged.
     */

    private static String nn(String in) {
        return in == null ? "" : in;
    }
}

