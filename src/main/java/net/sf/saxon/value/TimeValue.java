////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.ValidationFailure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * A value of type xs:time
 */

public final class TimeValue extends CalendarValue implements Comparable {

    private byte hour;
    private byte minute;
    private byte second;
    private int nanosecond;

    private TimeValue() {
    }

    /**
     * Construct a time value given the hour, minute, second, and microsecond components.
     * This constructor performs no validation.
     *
     * @param hour        the hour value, 0-23
     * @param minute      the minutes value, 0-59
     * @param second      the seconds value, 0-59
     * @param microsecond the number of microseconds, 0-999999
     * @param tz          the timezone displacement in minutes from UTC. Supply the value
     *                    {@link CalendarValue#NO_TIMEZONE} if there is no timezone component.
     * @deprecated since 10.0: use the constructor {@link #TimeValue(byte, byte, byte, int, int, String)}
     * that accepts nanosecond precision
     */

    public TimeValue(byte hour, byte minute, byte second, int microsecond, int tz) {
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.nanosecond = microsecond * 1000;
        setTimezoneInMinutes(tz);
        typeLabel = BuiltInAtomicType.TIME;
    }

    /**
     * Private constructor to construct a time value given the hour, minute, second, and nanosecond components.
     * This constructor performs no validation.
     *
     * @param hour        the hour value, 0-23
     * @param minute      the minutes value, 0-59
     * @param second      the seconds value, 0-59
     * @param nanosecond  the number of microseconds, 0-999999
     * @param tz          the timezone displacement in minutes from UTC. Supply the value
     *                    {@link CalendarValue#NO_TIMEZONE} if there is no timezone component.
     * @param flag        used to disambiguate this constructor. Must be set to "".
     */

    public TimeValue(byte hour, byte minute, byte second, int nanosecond, int tz, String flag) {
        if (!flag.isEmpty()) {
            throw new IllegalArgumentException();
        }
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.nanosecond = nanosecond;
        setTimezoneInMinutes(tz);
        typeLabel = BuiltInAtomicType.TIME;
    }

    /**
     * Factory method to construct a time value given the hour, minute, second, and nanosecond components.
     * This constructor performs no validation.
     *
     * @param hour        the hour value, 0-23
     * @param minute      the minutes value, 0-59
     * @param second      the seconds value, 0-59
     * @param nanosecond  the number of nanoseconds, 0-999_999_999
     * @param tz          the timezone displacement in minutes from UTC. Supply the value
     *                    {@link CalendarValue#NO_TIMEZONE} if there is no timezone component.
     */

    public TimeValue makeTimeValue(byte hour, byte minute, byte second, int nanosecond, int tz) {
        return new TimeValue(hour, minute, second, nanosecond, tz, "");
    }


    /**
     * Constructor: create a time value given a Java calendar object
     *
     * @param calendar holds the date and time
     * @param tz       the timezone offset in minutes, or NO_TIMEZONE indicating that there is no timezone
     */

    public TimeValue(/*@NotNull*/ GregorianCalendar calendar, int tz) {
        hour = (byte) calendar.get(Calendar.HOUR_OF_DAY);
        minute = (byte) calendar.get(Calendar.MINUTE);
        second = (byte) calendar.get(Calendar.SECOND);
        nanosecond = calendar.get(Calendar.MILLISECOND) * 1_000_000;
        setTimezoneInMinutes(tz);
        typeLabel = BuiltInAtomicType.TIME;
    }

    /**
     * Static factory method: create a time value from a supplied string, in
     * ISO 8601 format
     *
     * @param s the time in the lexical format hh:mm:ss[.ffffff] followed optionally by
     *          timezone in the form [+-]hh:mm or Z
     * @return either a TimeValue corresponding to the xs:time, or a ValidationFailure
     *         if the supplied value was invalid
     */

    /*@NotNull*/
    public static ConversionResult makeTimeValue(CharSequence s) {
        // input must have format hh:mm:ss[.fff*][([+|-]hh:mm | Z)]
        TimeValue tv = new TimeValue();
        StringTokenizer tok = new StringTokenizer(Whitespace.trimWhitespace(s).toString(), "-:.+Z", true);
        if (!tok.hasMoreElements()) {
            return badTime("too short", s);
        }
        String part = (String) tok.nextElement();

        if (part.length() != 2) {
            return badTime("hour must be two digits", s);
        }
        int value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badTime("Non-numeric hour component", s);
        }
        tv.hour = (byte) value;
        if (tv.hour > 24) {
            return badTime("hour is out of range", s);
        }
        if (!tok.hasMoreElements()) {
            return badTime("too short", s);
        }
        if (!":".equals(tok.nextElement())) {
            return badTime("wrong delimiter after hour", s);
        }

        if (!tok.hasMoreElements()) {
            return badTime("too short", s);
        }
        part = (String) tok.nextElement();
        if (part.length() != 2) {
            return badTime("minute must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badTime("Non-numeric minute component", s);
        }
        tv.minute = (byte) value;
        if (tv.minute > 59) {
            return badTime("minute is out of range", s);
        }
        if (tv.hour == 24 && tv.minute != 0) {
            return badTime("If hour is 24, minute must be 00", s);
        }
        if (!tok.hasMoreElements()) {
            return badTime("too short", s);
        }
        if (!":".equals(tok.nextElement())) {
            return badTime("wrong delimiter after minute", s);
        }

        if (!tok.hasMoreElements()) {
            return badTime("too short", s);
        }
        part = (String) tok.nextElement();
        if (part.length() != 2) {
            return badTime("second must be two digits", s);
        }
        value = DurationValue.simpleInteger(part);
        if (value < 0) {
            return badTime("Non-numeric second component", s);
        }
        tv.second = (byte) value;
        if (tv.second > 59) {
            return badTime("second is out of range", s);
        }
        if (tv.hour == 24 && tv.second != 0) {
            return badTime("If hour is 24, second must be 00", s);
        }

        int tz = 0;
        boolean negativeTz = false;
        int state = 0;
        while (tok.hasMoreElements()) {
            if (state == 9) {
                return badTime("characters after the end", s);
            }
            String delim = (String) tok.nextElement();
            if (".".equals(delim)) {
                if (state != 0) {
                    return badTime("decimal separator occurs twice", s);
                }
                if (!tok.hasMoreElements()) {
                    return badTime("decimal point must be followed by digits", s);
                }
                part = (String) tok.nextElement();
                if (part.length() > 9 && part.matches("^[0-9]+$")) {
                    part = part.substring(0, 9);
                }
                value = DurationValue.simpleInteger(part);
                if (value < 0) {
                    return badTime("Non-numeric fractional seconds component", s);
                }
                double fractionalSeconds = Double.parseDouble('.' + part);
                tv.nanosecond = (int) Math.round(fractionalSeconds * 1_000_000_000);
                if (tv.hour == 24 && tv.nanosecond != 0) {
                    return badTime("If hour is 24, fractional seconds must be 0", s);
                }
                state = 1;
            } else if ("Z".equals(delim)) {
                if (state > 1) {
                    return badTime("Z cannot occur here", s);
                }
                tz = 0;
                state = 9;  // we've finished
                tv.setTimezoneInMinutes(0);
            } else if ("+".equals(delim) || "-".equals(delim)) {
                if (state > 1) {
                    return badTime(delim + " cannot occur here", s);
                }
                state = 2;
                if (!tok.hasMoreElements()) {
                    return badTime("missing timezone", s);
                }
                part = (String) tok.nextElement();
                if (part.length() != 2) {
                    return badTime("timezone hour must be two digits", s);
                }
                value = DurationValue.simpleInteger(part);
                if (value < 0) {
                    return badTime("Non-numeric timezone hour component", s);
                }
                tz = value * 60;
                if (tz > 14 * 60) {
                    return badTime("timezone hour is out of range", s);
                }
                if ("-".equals(delim)) {
                    negativeTz = true;
                }
            } else if (":".equals(delim)) {
                if (state != 2) {
                    return badTime("colon cannot occur here", s);
                }
                state = 9;
                part = (String) tok.nextElement();
                value = DurationValue.simpleInteger(part);
                if (value < 0) {
                    return badTime("Non-numeric timezone minute component", s);
                }
                int tzminute = value;
                if (part.length() != 2) {
                    return badTime("timezone minute must be two digits", s);
                }
                if (tzminute > 59) {
                    return badTime("timezone minute is out of range", s);
                }

                tz += tzminute;
                if (negativeTz) {
                    tz = -tz;
                }
                tv.setTimezoneInMinutes(tz);
            } else {
                return badTime("timezone format is incorrect", s);
            }
        }

        if (state == 2 || state == 3) {
            return badTime("timezone incomplete", s);
        }

        if (tv.hour == 24) {
            tv.hour = 0;
        }

        tv.typeLabel = BuiltInAtomicType.TIME;
        return tv;
    }

    /*@NotNull*/
    private static ValidationFailure badTime(String msg, CharSequence value) {
        ValidationFailure err = new ValidationFailure(
                "Invalid time " + Err.wrap(value, Err.VALUE) + " (" + msg + ")");
        err.setErrorCode("FORG0001");
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
        return BuiltInAtomicType.TIME;
    }

    /**
     * Get the hour component, 0-23
     *
     * @return the hour
     */

    public byte getHour() {
        return hour;
    }

    /**
     * Get the minute component, 0-59
     *
     * @return the minute
     */

    public byte getMinute() {
        return minute;
    }

    /**
     * Get the second component, 0-59
     *
     * @return the second
     */

    public byte getSecond() {
        return second;
    }

    /**
     * Get the microsecond component, 0-999_999
     *
     * @return the nanoseconds component divided by 1000, rounded towards zero
     */

    public int getMicrosecond() {
        return nanosecond / 1000;
    }

    /**
     * Get the nanosecond component, 0-999_999
     *
     * @return the nanoseconds
     */

    public int getNanosecond() {
        return nanosecond;
    }


    /**
     * Convert to string
     *
     * @return ISO 8601 representation, in the localized timezone
     *         (the timezone held within the value).
     */

    /*@NotNull*/
    @Override
    public CharSequence getPrimitiveStringValue() {

        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C16);

        appendTwoDigits(sb, hour);
        sb.cat(':');
        appendTwoDigits(sb, minute);
        sb.cat(':');
        appendTwoDigits(sb, second);
        if (nanosecond != 0) {
            sb.cat('.');
            int ms = nanosecond;
            int div = 100_000_000;
            while (ms > 0) {
                int d = ms / div;
                sb.cat((char) (d + '0'));
                ms = ms % div;
                div /= 10;
            }
        }

        if (hasTimezone()) {
            appendTimezone(sb);
        }

        return sb;

    }


    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules. For an xs:time it is the
     * time adjusted to UTC
     *
     * @return the canonical lexical representation if defined in XML Schema
     */

    @Override
    public CharSequence getCanonicalLexicalRepresentation() {
        if (hasTimezone() && getTimezoneInMinutes() != 0) {
            return adjustTimezone(0).getStringValueCS();
        } else {
            return getStringValueCS();
        }
    }


    /**
     * Convert to a DateTime value. The date components represent a reference date, as defined
     * in the spec for comparing times.
     */

    /*@NotNull*/
    @Override
    public DateTimeValue toDateTime() {
        return new DateTimeValue(1972, (byte) 12, (byte) 31, hour, minute, second, nanosecond, getTimezoneInMinutes());
    }

    /**
     * Get a Java Calendar object corresponding to this time, on a reference date
     */

    /*@NotNull*/
    @Override
    public GregorianCalendar getCalendar() {
        // create a calendar using the specified timezone
        int tz = hasTimezone() ? getTimezoneInMinutes()*60000 : 0;
        TimeZone zone = new SimpleTimeZone(tz, "LLL");
        GregorianCalendar calendar = new GregorianCalendar(zone);
        calendar.setLenient(false);
        if (tz < calendar.getMinimum(Calendar.ZONE_OFFSET) || tz > calendar.getMaximum(Calendar.ZONE_OFFSET)) {
            return adjustTimezone(0).getCalendar();
        }

        // use a reference date of 1972-12-31
        calendar.set(1972, Calendar.DECEMBER, 31, hour, minute, second);
        calendar.set(Calendar.MILLISECOND, nanosecond / 1_000_000);
        calendar.set(Calendar.ZONE_OFFSET, tz);
        calendar.set(Calendar.DST_OFFSET, 0);

        calendar.getTime();
        return calendar;
    }

    /**
     * Make a copy of this time value,
     * but with a different type label
     *
     * @param typeLabel the new type label. This must be a subtype of xs:time.
     */

    /*@NotNull*/
    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        TimeValue v = new TimeValue(hour, minute, second, nanosecond, getTimezoneInMinutes(), "");
        v.typeLabel = typeLabel;
        return v;
    }

    /**
     * Return a new time with the same normalized value, but
     * in a different timezone. This is called only for a TimeValue that has an explicit timezone
     *
     * @param timezone the new timezone offset, in minutes
     * @return the time in the new timezone. This will be a new TimeValue unless no change
     *         was required to the original value
     */

    /*@NotNull*/
    @Override
    public TimeValue adjustTimezone(int timezone) {
        DateTimeValue dt = toDateTime().adjustTimezone(timezone);
        return new TimeValue(dt.getHour(), dt.getMinute(), dt.getSecond(),
                dt.getNanosecond(), dt.getTimezoneInMinutes(), "");
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
                return new Int64Value(nanosecond / 1000);
            case NANOSECONDS:
                return new Int64Value(nanosecond);
            case TIMEZONE:
                if (hasTimezone()) {
                    return DayTimeDurationValue.fromMilliseconds(60000L * getTimezoneInMinutes());
                } else {
                    return null;
                }
            default:
                throw new IllegalArgumentException("Unknown component for time: " + component);
        }
    }

    /**
     * Compare the value to another dateTime value
     *
     * @param other The other dateTime value
     * @return negative value if this one is the earler, 0 if they are chronologically equal,
     *         positive value if this one is the later. For this purpose, dateTime values with an unknown
     *         timezone are considered to be UTC values (the Comparable interface requires
     *         a total ordering).
     * @throws ClassCastException if the other value is not a TimeValue (the parameter
     *                            is declared as Object to satisfy the Comparable interface)
     */

    @Override
    public int compareTo(Object other) {
        TimeValue otherTime = (TimeValue) other;
        if (getTimezoneInMinutes() == otherTime.getTimezoneInMinutes()) {
            if (hour != otherTime.hour) {
                return IntegerValue.signum(hour - otherTime.hour);
            } else if (minute != otherTime.minute) {
                return IntegerValue.signum(minute - otherTime.minute);
            } else if (second != otherTime.second) {
                return IntegerValue.signum(second - otherTime.second);
            } else if (nanosecond != otherTime.nanosecond) {
                return IntegerValue.signum(nanosecond - otherTime.nanosecond);
            } else {
                return 0;
            }
        } else {
            return toDateTime().compareTo(otherTime.toDateTime());
        }
    }

    /**
     * Compare the value to another dateTime value
     *
     * @param other   The other dateTime value
     * @param implicitTimezone The implicit timezone assumed for a value with no timezone
     * @return negative value if this one is the earler, 0 if they are chronologically equal,
     *         positive value if this one is the later. For this purpose, dateTime values with an unknown
     *         timezone are considered to be UTC values (the Comparable interface requires
     *         a total ordering).
     * @throws ClassCastException        if the other value is not a DateTimeValue (the parameter
     *                                   is declared as Object to satisfy the Comparable interface)
     * @throws NoDynamicContextException if the implicit timezone is required and is not available
     *                                   (because the function is called at compile time)
     */

    @Override
    public int compareTo(/*@NotNull*/ CalendarValue other, /*@NotNull*/ int implicitTimezone) throws NoDynamicContextException {
        if (!(other instanceof TimeValue)) {
            throw new ClassCastException("Time values are not comparable to " + other.getClass());
        }
        TimeValue otherTime = (TimeValue) other;
        if (getTimezoneInMinutes() == otherTime.getTimezoneInMinutes()) {
            // The values have the same time zone, or neither has a timezone
            return compareTo(other);
        } else {
            return toDateTime().compareTo(otherTime.toDateTime(), implicitTimezone);
        }
    }


    /*@NotNull*/
    @Override
    public Comparable getSchemaComparable() {
        return new TimeComparable();
    }

    private class TimeComparable implements Comparable {

        /*@NotNull*/
        public TimeValue asTimeValue() {
            return TimeValue.this;
        }

        @Override
        public int compareTo(/*@NotNull*/ Object o) {
            if (o instanceof TimeComparable) {
                DateTimeValue dt0 = asTimeValue().toDateTime();
                DateTimeValue dt1 = ((TimeComparable) o).asTimeValue().toDateTime();
                return dt0.getSchemaComparable().compareTo(dt1.getSchemaComparable());
            } else {
                return SequenceTool.INDETERMINATE_ORDERING;
            }
        }

        public boolean equals(/*@NotNull*/ Object o) {
            return compareTo(o) == 0;
        }

        public int hashCode() {
            return TimeValue.this.toDateTime().getSchemaComparable().hashCode();
        }
    }


    public boolean equals(Object other) {
        return other instanceof TimeValue && compareTo(other) == 0;
    }

    public int hashCode() {
        return DateTimeValue.hashCode(
                1951, (byte) 10, (byte) 11, hour, minute, second, nanosecond, getTimezoneInMinutes());
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
    public TimeValue add(/*@NotNull*/ DurationValue duration) throws XPathException {
        if (duration instanceof DayTimeDurationValue) {
            DateTimeValue dt = toDateTime().add(duration);
            return new TimeValue(dt.getHour(), dt.getMinute(), dt.getSecond(),
                    dt.getNanosecond(), getTimezoneInMinutes(), "");
        } else {
            XPathException err = new XPathException("Time+Duration arithmetic is supported only for xs:dayTimeDuration");
            err.setErrorCode("XPTY0004");
            err.setIsTypeError(true);
            throw err;
        }
    }

    /**
     * Determine the difference between two points in time, as a duration
     *
     * @param other   the other point in time
     * @param context XPath dynamic evaluation context
     * @return the duration as an xs:dayTimeDuration
     * @throws XPathException for example if one value is a date and the other is a time
     */

    @Override
    public DayTimeDurationValue subtract(/*@NotNull*/ CalendarValue other, XPathContext context) throws XPathException {
        if (!(other instanceof TimeValue)) {
            XPathException err = new XPathException("First operand of '-' is a time, but the second is not");
            err.setIsTypeError(true);
            throw err;
        }
        return super.subtract(other, context);
    }


}

