////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.number.*;
import net.sf.saxon.lib.Numberer;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.regex.charclass.Categories;
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.*;

import java.util.Arrays;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implement the format-date(), format-time(), and format-dateTime() functions
 * in XSLT 2.0 and XQuery 1.1.
 */

public class FormatDate extends SystemFunction implements Callable {

    static final String[] knownCalendars = {"AD", "AH", "AME", "AM", "AP", "AS", "BE", "CB", "CE", "CL", "CS", "EE", "FE", "ISO", "JE",
            "KE", "KY", "ME", "MS", "NS", "OS", "RS", "SE", "SH", "SS", "TE", "VE", "VS"};

    private CharSequence adjustCalendar(StringValue calendarVal, CharSequence result, XPathContext context) throws XPathException {
        StructuredQName cal;
        try {
            String c = calendarVal.getStringValue();
            cal = StructuredQName.fromLexicalQName(c, false, true, getRetainedStaticContext());
        } catch (XPathException e) {
            XPathException err = new XPathException("Invalid calendar name. " + e.getMessage());
            err.setErrorCode("FOFD1340");
            err.setXPathContext(context);
            throw err;
        }

        if (cal.hasURI("")) {
            String calLocal = cal.getLocalPart();
            if (calLocal.equals("AD") || calLocal.equals("ISO")) {
                // no action
            } else if (Arrays.binarySearch(knownCalendars, calLocal) >= 0) {
                result = "[Calendar: AD]" + result;
            } else {
                XPathException err = new XPathException("Unknown no-namespace calendar: " + calLocal);
                err.setErrorCode("FOFD1340");
                err.setXPathContext(context);
                throw err;
            }
        } else {
            result = "[Calendar: AD]" + result;
        }
        return result;
    }

    /**
     * This method analyzes the formatting picture and delegates the work of formatting
     * individual parts of the date.
     *
     * @param value    the value to be formatted
     * @param format   the supplied format picture
     * @param language the chosen language
     * @param place  the chosen country
     * @param context  the XPath dynamic evaluation context
     * @return the formatted date/time
     * @throws XPathException if a dynamic error occurs
     */

    private static CharSequence formatDate(CalendarValue value, String format, String language, String place, XPathContext context)
            throws XPathException {

        Configuration config = context.getConfiguration();

        boolean languageDefaulted = language == null;
        if (language == null) {
            language = config.getDefaultLanguage();
        }
        if (place == null) {
            place = config.getDefaultCountry();
        }

        // if the value has a timezone and the place is a timezone name, the value is adjusted to that timezone
        if (value.hasTimezone() && place.contains("/")) {
            TimeZone tz = TimeZone.getTimeZone(place);
            if (tz != null) {
                int milliOffset = tz.getOffset(value.toDateTime().getCalendar().getTime().getTime());
                value = value.adjustTimezone(milliOffset / 60000);
            }
        }

        Numberer numberer = config.makeNumberer(language, place);
        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C64);
        if (!languageDefaulted && numberer.getClass() == Numberer_en.class && !language.startsWith("en")) {
            // See bug #4582. We're not outputting the prefix in cases where ICU is used for numbering.
            // But the test on numberer.defaultedLocale() below may catch it...
            sb.append("[Language: en]");
        }
        if (numberer.defaultedLocale() != null) {
            sb.append("[Language: " + numberer.defaultedLocale().getLanguage() + "]");
        }


        int i = 0;
        while (true) {
            while (i < format.length() && format.charAt(i) != '[') {
                sb.cat(format.charAt(i));
                if (format.charAt(i) == ']') {
                    i++;
                    if (i == format.length() || format.charAt(i) != ']') {
                        XPathException e = new XPathException("Closing ']' in date picture must be written as ']]'");
                        e.setErrorCode("FOFD1340");
                        e.setXPathContext(context);
                        throw e;
                    }
                }
                i++;
            }
            if (i == format.length()) {
                break;
            }
            // look for '[['
            i++;
            if (i < format.length() && format.charAt(i) == '[') {
                sb.cat('[');
                i++;
            } else {
                int close = i < format.length() ? format.indexOf("]", i) : -1;
                if (close == -1) {
                    XPathException e = new XPathException("Date format contains a '[' with no matching ']'");
                    e.setErrorCode("FOFD1340");
                    e.setXPathContext(context);
                    throw e;
                }
                String componentFormat = format.substring(i, close);
                sb.cat(formatComponent(value, Whitespace.removeAllWhitespace(componentFormat),
                                       numberer, place, context));
                i = close + 1;
            }
        }
        return sb;
    }

    private static Pattern componentPattern =
            Pattern.compile("([YMDdWwFHhmsfZzPCE])\\s*(.*)");

    private static CharSequence formatComponent(CalendarValue value, CharSequence specifier,
                                                Numberer numberer, String country, XPathContext context)
            throws XPathException {
        boolean ignoreDate = value instanceof TimeValue;
        boolean ignoreTime = value instanceof DateValue;
        DateTimeValue dtvalue = value.toDateTime();

        Matcher matcher = componentPattern.matcher(specifier);
        if (!matcher.matches()) {
            XPathException error = new XPathException("Unrecognized date/time component [" + specifier + ']');
            error.setErrorCode("FOFD1340");
            error.setXPathContext(context);
            throw error;
        }
        String component = matcher.group(1);
        String format = matcher.group(2);
        if (format == null) {
            format = "";
        }
        boolean defaultFormat = false;
        if ("".equals(format) || format.startsWith(",")) {
            defaultFormat = true;
            switch (component.charAt(0)) {
                case 'F':
                    format = "Nn" + format;
                    break;
                case 'P':
                    format = 'n' + format;
                    break;
                case 'C':
                case 'E':
                    format = 'N' + format;
                    break;
                case 'm':
                case 's':
                    format = "01" + format;
                    break;
                case 'z':
                case 'Z':
                    //format = "00:00" + format;
                    break;
                default:
                    format = '1' + format;
            }
        }

        switch (component.charAt(0)) {
            case 'Y':       // year
                if (ignoreDate) {
                    XPathException error = new XPathException("In format-time(): an xs:time value does not contain a year component");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    int year = dtvalue.getYear();
                    if (year < 0) {
                        year = 0 - year;
                    }
                    return formatNumber(component, year, format, defaultFormat, numberer, context);
                }
            case 'M':       // month
                if (ignoreDate) {
                    XPathException error = new XPathException("In format-time(): an xs:time value does not contain a month component");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    int month = dtvalue.getMonth();
                    return formatNumber(component, month, format, defaultFormat, numberer, context);
                }
            case 'D':       // day in month
                if (ignoreDate) {
                    XPathException error = new XPathException("In format-time(): an xs:time value does not contain a day component");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    int day = dtvalue.getDay();
                    return formatNumber(component, day, format, defaultFormat, numberer, context);
                }
            case 'd':       // day in year
                if (ignoreDate) {
                    XPathException error = new XPathException("In format-time(): an xs:time value does not contain a day component");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    int day = DateValue.getDayWithinYear(dtvalue.getYear(), dtvalue.getMonth(), dtvalue.getDay());
                    return formatNumber(component, day, format, defaultFormat, numberer, context);
                }
            case 'W':       // week of year
                if (ignoreDate) {
                    XPathException error = new XPathException("In format-time(): cannot obtain the week number from an xs:time value");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    int week = DateValue.getWeekNumber(dtvalue.getYear(), dtvalue.getMonth(), dtvalue.getDay());
                    return formatNumber(component, week, format, defaultFormat, numberer, context);
                }
            case 'w':       // week in month
                if (ignoreDate) {
                    XPathException error = new XPathException("In format-time(): cannot obtain the week number from an xs:time value");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    int week = DateValue.getWeekNumberWithinMonth(dtvalue.getYear(), dtvalue.getMonth(), dtvalue.getDay());
                    return formatNumber(component, week, format, defaultFormat, numberer, context);
                }
            case 'H':       // hour in day
                if (ignoreTime) {
                    XPathException error = new XPathException("In format-date(): an xs:date value does not contain an hour component");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    Int64Value hour = (Int64Value) value.getComponent(AccessorFn.Component.HOURS);
                    assert hour != null;
                    return formatNumber(component, (int) hour.longValue(), format, defaultFormat, numberer, context);
                }
            case 'h':       // hour in half-day (12 hour clock)
                if (ignoreTime) {
                    XPathException error = new XPathException("In format-date(): an xs:date value does not contain an hour component");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    Int64Value hour = (Int64Value) value.getComponent(AccessorFn.Component.HOURS);
                    assert hour != null;
                    int hr = (int) hour.longValue();
                    if (hr > 12) {
                        hr = hr - 12;
                    }
                    if (hr == 0) {
                        hr = 12;
                    }
                    return formatNumber(component, hr, format, defaultFormat, numberer, context);
                }
            case 'm':       // minutes
                if (ignoreTime) {
                    XPathException error = new XPathException("In format-date(): an xs:date value does not contain a minutes component");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    Int64Value minutes = (Int64Value) value.getComponent(AccessorFn.Component.MINUTES);
                    assert minutes != null;
                    return formatNumber(component, (int) minutes.longValue(), format, defaultFormat, numberer, context);
                }
            case 's':       // seconds
                if (ignoreTime) {
                    XPathException error = new XPathException("In format-date(): an xs:date value does not contain a seconds component");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    IntegerValue seconds = (IntegerValue) value.getComponent(AccessorFn.Component.WHOLE_SECONDS);
                    assert seconds != null;
                    return formatNumber(component, (int) seconds.longValue(), format, defaultFormat, numberer, context);
                }
            case 'f':       // fractional seconds
                // ignore the format
                if (ignoreTime) {
                    XPathException error = new XPathException("In format-date(): an xs:date value does not contain a fractional seconds component");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    Int64Value micros = (Int64Value) value.getComponent(AccessorFn.Component.MICROSECONDS);
                    assert micros != null;
                    return formatNumber(component, (int)micros.longValue(), format, defaultFormat, numberer, context);
                }
            case 'z':
            case 'Z':
                DateTimeValue dtv;
                if (value instanceof TimeValue) {
                    // See bug 3761. We need to pad the time with a date. 1972-12-31 or 1970-01-01 won't do because
                    // timezones were different then (Alaska changed in 1983, for example). Today's date isn't ideal
                    // because it's better to choose a date that isn't in summer time. We'll choose the first of
                    // January in the current year, unless that's in summer time in the country in question, in which
                    // case we'll choose first of July.
                    DateTimeValue now = DateTimeValue.getCurrentDateTime(context);
                    int year = now.getYear();
                    int tzoffset = value.getTimezoneInMinutes();
                    DateTimeValue baseDate =
                            new DateTimeValue(year, (byte)1, (byte)1, (byte)0, (byte)0, (byte)0, 0, tzoffset, false);
                    Boolean b = NamedTimeZone.inSummerTime(baseDate, country);
                    if (b != null && b) {
                        baseDate = new DateTimeValue(year, (byte) 7, (byte) 1, (byte) 0, (byte) 0, (byte) 0, 0, tzoffset, false);
                    }
                    dtv = DateTimeValue.makeDateTimeValue(baseDate.toDateValue(), (TimeValue)value);
                } else {
                    dtv = value.toDateTime();
                }
                return formatTimeZone(dtv, component.charAt(0), format, country);

            case 'F':       // day of week
                if (ignoreDate) {
                    XPathException error = new XPathException("In format-time(): an xs:time value does not contain day-of-week component");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    int day = DateValue.getDayOfWeek(dtvalue.getYear(), dtvalue.getMonth(), dtvalue.getDay());
                    return formatNumber(component, day, format, defaultFormat, numberer, context);
                }
            case 'P':       // am/pm marker
                if (ignoreTime) {
                    XPathException error = new XPathException("In format-date(): an xs:date value does not contain an am/pm component");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    int minuteOfDay = dtvalue.getHour() * 60 + dtvalue.getMinute();
                    return formatNumber(component, minuteOfDay, format, defaultFormat, numberer, context);
                }
            case 'C':       // calendar
                return numberer.getCalendarName("AD");
            case 'E':       // era
                if (ignoreDate) {
                    XPathException error = new XPathException("In format-time(): an xs:time value does not contain an AD/BC component");
                    error.setErrorCode("FOFD1350");
                    error.setXPathContext(context);
                    throw error;
                } else {
                    int year = dtvalue.getYear();
                    return numberer.getEraName(year);
                }
            default:
                XPathException e = new XPathException("Unknown format-date/time component specifier '" + format.charAt(0) + '\'');
                e.setErrorCode("FOFD1340");
                e.setXPathContext(context);
                throw e;
        }
    }

    private static Pattern formatPattern =
            //Pattern.compile("([^ot,]*?)([ot]?)(,.*)?");   // GNU Classpath has problems with this one
            Pattern.compile("([^,]*)(,.*)?");           // Note, the group numbers are different from above

    private static Pattern widthPattern =
            Pattern.compile(",(\\*|[0-9]+)(\\-(\\*|[0-9]+))?");

    private static Pattern alphanumericPattern =
            Pattern.compile("([A-Za-z0-9]|\\p{L}|\\p{N})*");
    // the first term is redundant, but GNU Classpath can't cope with the others...

    private static Pattern digitsPattern =
            Pattern.compile("\\p{Nd}+");

    private static Pattern digitsOrOptionalDigitsPattern =
            Pattern.compile("[#\\p{Nd}]+");


    private static Pattern fractionalDigitsPattern =
            Pattern.compile("\\p{Nd}+#*");

    private static CharSequence formatNumber(String component, int value,
                                             String format, boolean defaultFormat, Numberer numberer, XPathContext context)
            throws XPathException {
        int comma = format.lastIndexOf(',');
        String widths = "";
        if (comma >= 0) {
            widths = format.substring(comma);
            format = format.substring(0, comma);
        }
        String primary = format;
        String modifier = null;
        if (primary.endsWith("t")) {
            primary = primary.substring(0, primary.length() - 1);
            modifier = "t";
        } else if (primary.endsWith("o")) {
            primary = primary.substring(0, primary.length() - 1);
            modifier = "o";
        }
        String letterValue = "t".equals(modifier) ? "traditional" : null;
        String ordinal = "o".equals(modifier) ? numberer.getOrdinalSuffixForDateTime(component) : null;

        int min = 1;
        int max = Integer.MAX_VALUE;

        if (digitsPattern.matcher(primary).matches()) {
            int len = StringValue.getStringLength(primary);
            if (len > 1) {
                // "A format token containing leading zeroes, such as 001, sets the minimum and maximum width..."
                // We interpret this literally: a format token of "1" does not set a maximum, because it would
                // cause the year 2006 to be formatted as "6".
                min = len;
                max = len;
            }
        }
        if ("Y".equals(component)) {
            min = max = 0;
            if (!widths.isEmpty()) {
                max = getWidths(widths)[1];
            } else if (digitsPattern.matcher(primary).find()) {
                UnicodeString uPrimary = UnicodeString.makeUnicodeString(primary);
                for (int i = 0; i < uPrimary.uLength(); i++) {
                    int c = uPrimary.uCharAt(i);
                    if (c == '#') {
                        max++;
                    } else if ((c >= '0' && c <= '9') || Categories.ESCAPE_d.test(c)) {
                        min++;
                        max++;
                    }
                }
            }
            if (max <= 1) {
                max = Integer.MAX_VALUE;
            }
            if (max < 4 || (max < Integer.MAX_VALUE && value > 9999)) {
                value = value % (int) Math.pow(10, max);
            }
        }
        if (primary.equals("I") || primary.equals("i")) {
            int[] range = getWidths(widths);
            min = range[0];
            //max = Integer.MAX_VALUE;

            String roman = numberer.format(value, UnicodeString.makeUnicodeString(primary), null, letterValue, ordinal);
            StringBuilder s = new StringBuilder(roman);
            int len = StringValue.getStringLength(roman);
            while (len < min) {
                s.append(' ');
                len++;
            }
            return s.toString();
        } else if (!widths.isEmpty()) {
            int[] range = getWidths(widths);
            min = Math.max(min, range[0]);
            if (max == Integer.MAX_VALUE) {
                max = range[1];
            } else {
                max = Math.max(max, range[1]);
            }
            if (defaultFormat) {
                // if format was defaulted, the explicit widths override the implicit format
                if (primary.endsWith("1") && min != primary.length()) {
                    FastStringBuffer sb = new FastStringBuffer(min + 1);
                    for (int i = 1; i < min; i++) {
                        sb.cat('0');
                    }
                    sb.cat('1');
                    primary = sb.toString();
                }
            }
        }

        if ("P".equals(component)) {
            // A.M./P.M. can only be formatted as a name
            if (!("N".equals(primary) || "n".equals(primary) || "Nn".equals(primary))) {
                primary = "n";
            }
            if (max == Integer.MAX_VALUE) {
                // if no max specified, use 4. An explicit greater value allows use of "noon" and "midnight"
                max = 4;
            }
        } else if ("Y".equals(component)) {
            if (max < Integer.MAX_VALUE) {
                value = value % (int) Math.pow(10, max);
            }
        } else if ("f".equals(component)) {
            // value is supplied as integer number of microseconds
            UnicodeString uFormat = UnicodeString.makeUnicodeString(format);
            // If there is no Unicode digit in the pattern, output is implementation defined, so do what comes easily
            if (!digitsPattern.matcher(primary).find()) {
                return formatNumber(component, value, "1", defaultFormat, numberer, context);
            }
            // if there are grouping separators, handle as a reverse integer as described in the 3.1 spec
            if (!digitsOrOptionalDigitsPattern.matcher(primary).matches()) {
                UnicodeString reverseFormat = reverse(uFormat);
                UnicodeString reverseValue = reverse(UnicodeString.makeUnicodeString("" + value));
                CharSequence reverseResult = formatNumber("s",
                                                          Integer.parseInt(reverseValue.toString()), reverseFormat.toString(), false, numberer, context);
                UnicodeString correctedResult = reverse(UnicodeString.makeUnicodeString(reverseResult));
                if (correctedResult.uLength() > max) {
                    correctedResult = correctedResult.uSubstring(0, max);
                }
                return correctedResult.toString();
            }
            if (!fractionalDigitsPattern.matcher(primary).matches()) {
                throw new XPathException("Invalid picture for fractional seconds: " + primary, "FOFD1340");
            }
            StringBuilder s;
            if (value == 0) {
                s = new StringBuilder("0");
            } else {
                s = new StringBuilder(((1000000 + value) + "").substring(1));
                if (s.length() > max) {
                    // Spec bug 29749 says we should truncate rather than rounding
                    s = new StringBuilder(s.substring(0, max));
                }
            }
            while (s.length() < min) {
                s.append('0');
            }
            while (s.length() > min && s.charAt(s.length() - 1) == '0') {
                s = new StringBuilder(s.substring(0, s.length() - 1));
            }
            // for non standard decimal digit family
            int zeroDigit = Alphanumeric.getDigitFamily(uFormat.uCharAt(0));
            if (zeroDigit >= 0 && zeroDigit != '0') {
                int[] digits = new int[10];
                for (int z = 0; z <= 9; z++) {
                    digits[z] = zeroDigit + z;
                }
                long n = Long.parseLong(s.toString());
                int requiredLength = s.length();
                s = new StringBuilder(AbstractNumberer.convertDigitSystem(n, digits, requiredLength).toString());
            }
            return s.toString();
        }

        if ("N".equals(primary) || "n".equals(primary) || "Nn".equals(primary)) {
            String s = "";
            if ("M".equals(component)) {
                s = numberer.monthName(value, min, max);
            } else if ("F".equals(component)) {
                s = numberer.dayName(value, min, max);
            } else if ("P".equals(component)) {
                s = numberer.halfDayName(value, min, max);
            } else {
                primary = "1";
            }
            if ("N".equals(primary)) {
                return s.toUpperCase();
            } else if ("n".equals(primary)) {
                return s.toLowerCase();
            } else {
                return s;
            }
        }

        // deal with grouping separators, decimal digit family, etc. for numeric values
        NumericGroupFormatter picGroupFormat;
        try {
            picGroupFormat = FormatInteger.getPicSeparators(primary);
        } catch (XPathException e) {
            if ("FODF1310".equals(e.getErrorCodeLocalPart())) {
                e.setErrorCode("FOFD1340");
            }
            throw e;
        }
        UnicodeString adjustedPicture = picGroupFormat.getAdjustedPicture();

        String s = numberer.format(value, adjustedPicture, picGroupFormat, letterValue, ordinal);
        int len = StringValue.getStringLength(s);
        int zeroDigit;
        if (len < min) {
            zeroDigit = Alphanumeric.getDigitFamily(adjustedPicture.uCharAt(0));
            FastStringBuffer fsb = new FastStringBuffer(s);
            while (len < min) {
                fsb.prependWideChar(zeroDigit);
                len = len + 1;
            }
            s = fsb.toString();
        }
        return s;
    }

    private static UnicodeString reverse(UnicodeString in) {
        int[] out = new int[in.uLength()];
        for (int i = in.uLength() - 1, j = 0; i >= 0; i--, j++) {
            out[j] = in.uCharAt(i);
        }
        return UnicodeString.makeUnicodeString(out);
    }

    private static int[] getWidths(String widths) throws XPathException {
        try {
            int min = -1;
            int max = -1;

            if (!"".equals(widths)) {
                Matcher widthMatcher = widthPattern.matcher(widths);
                if (widthMatcher.matches()) {
                    String smin = widthMatcher.group(1);
                    if (smin == null || "".equals(smin) || "*".equals(smin)) {
                        min = 1;
                    } else {
                        min = Integer.parseInt(smin);
                    }
                    String smax = widthMatcher.group(3);
                    if (smax == null || "".equals(smax) || "*".equals(smax)) {
                        max = Integer.MAX_VALUE;
                    } else {
                        max = Integer.parseInt(smax);
                    }
                    if (min < 1) {
                        throw new XPathException("Invalid min value in format picture " + Err.wrap(widths, Err.VALUE), "FOFD1340");
                    }
                    if (max < 1 || max < min) {
                        throw new XPathException("Invalid max value in format picture " + Err.wrap(widths, Err.VALUE), "FOFD1340");
                    }
                } else {
                    throw new XPathException("Unrecognized width specifier in format picture " + Err.wrap(widths, Err.VALUE), "FOFD1340");
                }
            }

            if (min > max) {
                XPathException e = new XPathException("Minimum width in date/time picture exceeds maximum width");
                e.setErrorCode("FOFD1340");
                throw e;
            }
            int[] result = new int[2];
            result[0] = min;
            result[1] = max;
            return result;
        } catch (NumberFormatException err) {
            XPathException e = new XPathException("Invalid integer used as width in date/time picture");
            e.setErrorCode("FOFD1340");
            throw e;
        }
    }

    private static String formatTimeZone(DateTimeValue value, char component, String format, String country) throws XPathException {
        int comma = format.lastIndexOf(',');
        String widthModifier = "";
        if (comma >= 0) {
            widthModifier = format.substring(comma);
            format = format.substring(0, comma);
        }
        if (!value.hasTimezone()) {
            if (format.equals("Z")) {
                return "J"; // military "local time"
            } else {
                return "";
            }
        }
        if (format.isEmpty() && !widthModifier.isEmpty()) {
            int[] widths = getWidths(widthModifier);
            int min = widths[0];
            int max = widths[1];
            if (min <= 1) {
                format = max >= 4 ? "0:00" : "0";
            } else if (min <= 4) {
                format = max >= 5 ? "00:00" : "00";
            } else {
                format = "00:00";
            }
        }
        if (format.isEmpty()) {
            format = "00:00";
        }
        int tz = value.getTimezoneInMinutes();
        boolean useZforZero = format.endsWith("t");
        if (useZforZero && tz == 0) {
            return "Z";
        }
        if (useZforZero) {
            format = format.substring(0, format.length() - 1);
        }
        int digits = 0;
        int separators = 0;
        int separatorChar = ':';
        int zeroDigit = -1;
        int[] expandedFormat = StringValue.expand(format);
        for (int ch : expandedFormat) {
            if (Character.isDigit(ch)) {
                digits++;
                if (zeroDigit < 0) {
                    zeroDigit = Alphanumeric.getDigitFamily(ch);
                }
            } else {
                separators++;
                separatorChar = ch;
            }
        }
        int[] buffer = new int[10];
        int used = 0;
        if (digits > 0) {
            // Numeric timezone formatting
            if (component == 'z') {
                buffer[0] = 'G';
                buffer[1] = 'M';
                buffer[2] = 'T';
                used = 3;
            }
            boolean negative = tz < 0;
            tz = java.lang.Math.abs(tz);
            buffer[used++] = negative ? '-' : '+';

            int hour = tz / 60;
            int minute = tz % 60;

            boolean includeMinutes = minute != 0 || digits >= 3 || separators > 0;
            boolean includeSep = (minute != 0 && digits <= 2) || (separators > 0 && (minute != 0 || digits >= 3));

            int hourDigits = digits <= 2 ? digits : digits - 2;

            if (hour > 9 || hourDigits >= 2) {
                buffer[used++] = zeroDigit + hour / 10;
            }
            buffer[used++] = (hour % 10) + zeroDigit;

            if (includeSep) {
                buffer[used++] = separatorChar;
            }
            if (includeMinutes) {
                buffer[used++] = minute / 10 + zeroDigit;
                buffer[used++] = minute % 10 + zeroDigit;
            }

            return StringValue.contract(buffer, used).toString();
        } else if (format.equals("Z")) {
            // military timezone formatting
            int hour = tz / 60;
            int minute = tz % 60;
            if (hour < -12 || hour > 12 || minute != 0) {
                return formatTimeZone(value, 'Z', "00:00", country);
            } else {
                return Character.toString("YXWVUTSRQPONZABCDEFGHIKLM".charAt(hour + 12));
            }
        } else if (format.charAt(0) == 'N' || format.charAt(0) == 'n') {
            return getNamedTimeZone(value, country, format);
        } else {
            return formatTimeZone(value, 'Z', "00:00", country);
        }

    }

    private static String getNamedTimeZone(DateTimeValue value, String country, String format) throws XPathException {

        int min = 1;
        int comma = format.indexOf(',');
        if (comma > 0) {
            String widths = format.substring(comma);
            int[] range = getWidths(widths);
            min = range[0];
        }
        if (format.charAt(0) == 'N' || format.charAt(0) == 'n') {
            if (min <= 5) {
                String tzname = NamedTimeZone.getTimeZoneNameForDate(value, country);
                if (format.charAt(0) == 'n') {
                    tzname = tzname.toLowerCase();
                }
                return tzname;
            } else {
                return NamedTimeZone.getOlsenTimeZoneName(value, country);
            }
        }
        FastStringBuffer sbz = new FastStringBuffer(8);
        value.appendTimezone(sbz);
        return sbz.toString();
    }


    @Override
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        CalendarValue value = (CalendarValue) arguments[0].head();
        if (value == null) {
            return ZeroOrOne.empty();
        }
        String format = arguments[1].head().getStringValue();

        StringValue calendarVal = null;
        StringValue countryVal = null;
        StringValue languageVal = null;
        if (getArity() > 2) {
            languageVal = (StringValue) arguments[2].head();
            calendarVal = (StringValue) arguments[3].head();
            countryVal = (StringValue) arguments[4].head();
        }

        String language = languageVal == null ? null : languageVal.getStringValue();
        String place = countryVal == null ? null : countryVal.getStringValue();
        if (place != null && place.contains("/") && value.hasTimezone() && !(value instanceof TimeValue)) {
            TimeZone zone = NamedTimeZone.getNamedTimeZone(place);
            if (zone != null) {
                int offset = zone.getOffset(value.toDateTime().getCalendar().getTimeInMillis());
                value = value.adjustTimezone(offset / 60000);
            }
        }
        CharSequence result = formatDate(value, format, language, place, context);
        if (calendarVal != null) {
            result = adjustCalendar(calendarVal, result, context);
        }
        return new ZeroOrOne(new StringValue(result));
    }

}

