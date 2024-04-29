////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
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
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.regex.ARegexIterator;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.regex.charclass.Categories;
import net.sf.saxon.str.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntIterator;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Implement the format-date(), format-time(), and format-dateTime() functions
 * in XSLT 2.0 and XQuery 1.1.
 */

public class FormatDate extends SystemFunction implements Callable {

    static final String[] knownCalendars = {"AD", "AH", "AME", "AM", "AP", "AS", "BE", "CB", "CE", "CL", "CS", "EE", "FE", "ISO", "JE",
            "KE", "KY", "ME", "MS", "NS", "OS", "RS", "SE", "SH", "SS", "TE", "VE", "VS"};

    private final static UnicodeString STR_0 = BMPString.of("0");
    private final static UnicodeString STR_01 = BMPString.of("01");
    private final static UnicodeString STR_1 = BMPString.of("1");
    private final static UnicodeString STR_f = BMPString.of("f");
    private final static UnicodeString STR_F = BMPString.of("F");
    private final static UnicodeString STR_i = BMPString.of("i");
    private final static UnicodeString STR_I = BMPString.of("I");
    private final static UnicodeString STR_J = BMPString.of("J");
    private final static UnicodeString STR_M = BMPString.of("M");
    private final static UnicodeString STR_N = BMPString.of("N");
    private final static UnicodeString STR_Nn = BMPString.of("Nn");
    private final static UnicodeString STR_n = BMPString.of("n");
    private final static UnicodeString STR_P = BMPString.of("P");
    private final static UnicodeString STR_s = BMPString.of("s");
    private final static UnicodeString STR_Y = BMPString.of("Y");
    private final static UnicodeString STR_Z = BMPString.of("Z");

    private String adjustCalendar(String calendarVal, String result, XPathContext context) throws XPathException {
        StructuredQName cal;
        try {
            cal = StructuredQName.fromLexicalQName((calendarVal), false, true, getRetainedStaticContext());
        } catch (XPathException e) {
            throw new XPathException("Invalid calendar name. " + e.getMessage())
                    .withErrorCode("FOFD1340")
                    .withXPathContext(context);
        }

        if (cal.hasURI(NamespaceUri.NULL)) {
            String calLocal = cal.getLocalPart();
            if (calLocal.equals("AD") || calLocal.equals("ISO")) {
                // no action
            } else if (Arrays.binarySearch(knownCalendars, calLocal) >= 0) {
                result = "[Calendar: AD]" + result;
            } else {
                throw new XPathException("Unknown no-namespace calendar: " + calLocal)
                        .withErrorCode("FOFD1340")
                        .withXPathContext(context);
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

    private static String formatDate(CalendarValue value, String format, String language, String place, XPathContext context)
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
                BigDecimal seconds = value.toDateTime().secondsSinceEpoch();
                int milliOffset = tz.getOffset(seconds.longValue()*1000);
                value = value.adjustTimezone(milliOffset / 60000);
            }
        }

        Numberer numberer = config.makeNumberer(language, place);
        StringBuilder sb = new StringBuilder(64);
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
                sb.append(format.charAt(i));
                if (format.charAt(i) == ']') {
                    i++;
                    if (i == format.length() || format.charAt(i) != ']') {
                        throw new XPathException("Closing ']' in date picture must be written as ']]'")
                                .withErrorCode("FOFD1340")
                                .withXPathContext(context);
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
                sb.append('[');
                i++;
            } else {
                int close = i < format.length() ? format.indexOf("]", i) : -1;
                if (close == -1) {
                    throw new XPathException("Date format contains a '[' with no matching ']'")
                            .withErrorCode("FOFD1340")
                            .withXPathContext(context);
                }
                String componentFormat = format.substring(i, close);
                sb.append(formatComponent(value, Whitespace.removeAllWhitespace(componentFormat),
                                          numberer, place, context));
                i = close + 1;
            }
        }
        return sb.toString();
    }

    private static final ARegularExpression componentPattern =
            ARegularExpression.compile("([YMDdWwFHhmsfZzPCE])\\s*(.*)", "");

    private static UnicodeString formatComponent(CalendarValue value, String specifier,
                                                 Numberer numberer, String country, XPathContext context)
            throws XPathException {
        boolean ignoreDate = value instanceof TimeValue;
        boolean ignoreTime = value instanceof DateValue;
        DateTimeValue dtvalue = value.toDateTime();

        UnicodeString uSpecifier = StringView.of(specifier).tidy();
        ARegexIterator matcher = (ARegexIterator)componentPattern.analyze(uSpecifier);
        Item firstMatch = matcher.next();
        if (firstMatch == null || firstMatch.getUnicodeStringValue().length32() != uSpecifier.length32() || !matcher.isMatching()) {
            throw new XPathException("Unrecognized date/time component [" + specifier + ']')
                    .withErrorCode("FOFD1340")
                    .withXPathContext(context);
        }
        UnicodeString component = matcher.getRegexGroup(1);
        UnicodeString format = matcher.getRegexGroup(2);
        boolean defaultFormat = false;
        if (format.isEmpty() || format.codePointAt(0) == ',') {
            defaultFormat = true;
            switch (component.codePointAt(0)) {
                case 'F':
                    format = STR_Nn.concat(format);
                    break;
                case 'P':
                    format = STR_n.concat(format);
                    break;
                case 'C':
                case 'E':
                    format = STR_N.concat(format);
                    break;
                case 'm':
                case 's':
                    format = STR_01.concat(format);
                    break;
                case 'z':
                case 'Z':
                    //format = "00:00" + format;
                    break;
                default:
                    format = STR_1.concat(format);
                    break;
            }
        }

        switch (component.codePointAt(0)) {
            case 'Y':       // year
                if (ignoreDate) {
                    throw new XPathException("In format-time(): an xs:time value does not contain a year component")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
                } else {
                    int year = dtvalue.getYear();
                    if (year < 0) {
                        year = -year;
                    }
                    return formatNumber(component, year, format, defaultFormat, numberer, context);
                }
            case 'M':       // month
                if (ignoreDate) {
                    throw new XPathException("In format-time(): an xs:time value does not contain a month component")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
                } else {
                    int month = dtvalue.getMonth();
                    return formatNumber(component, month, format, defaultFormat, numberer, context);
                }
            case 'D':       // day in month
                if (ignoreDate) {
                    throw new XPathException("In format-time(): an xs:time value does not contain a day component")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
                } else {
                    int day = dtvalue.getDay();
                    return formatNumber(component, day, format, defaultFormat, numberer, context);
                }
            case 'd':       // day in year
                if (ignoreDate) {
                    throw new XPathException("In format-time(): an xs:time value does not contain a day component")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
                } else {
                    int day = DateValue.getDayWithinYear(dtvalue.getYear(), dtvalue.getMonth(), dtvalue.getDay());
                    return formatNumber(component, day, format, defaultFormat, numberer, context);
                }
            case 'W':       // week of year
                if (ignoreDate) {
                    throw new XPathException("In format-time(): cannot obtain the week number from an xs:time value")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
                } else {
                    int week = DateValue.getWeekNumber(dtvalue.getYear(), dtvalue.getMonth(), dtvalue.getDay());
                    return formatNumber(component, week, format, defaultFormat, numberer, context);
                }
            case 'w':       // week in month
                if (ignoreDate) {
                    throw new XPathException("In format-time(): cannot obtain the week number from an xs:time value")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
                } else {
                    int week = DateValue.getWeekNumberWithinMonth(dtvalue.getYear(), dtvalue.getMonth(), dtvalue.getDay());
                    return formatNumber(component, week, format, defaultFormat, numberer, context);
                }
            case 'H':       // hour in day
                if (ignoreTime) {
                    throw new XPathException("In format-date(): an xs:date value does not contain an hour component")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
                } else {
                    Int64Value hour = (Int64Value) value.getComponent(AccessorFn.Component.HOURS);
                    assert hour != null;
                    return formatNumber(component, (int) hour.longValue(), format, defaultFormat, numberer, context);
                }
            case 'h':       // hour in half-day (12 hour clock)
                if (ignoreTime) {
                    throw new XPathException("In format-date(): an xs:date value does not contain an hour component")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
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
                    throw new XPathException("In format-date(): an xs:date value does not contain a minutes component")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
                } else {
                    Int64Value minutes = (Int64Value) value.getComponent(AccessorFn.Component.MINUTES);
                    assert minutes != null;
                    return formatNumber(component, (int) minutes.longValue(), format, defaultFormat, numberer, context);
                }
            case 's':       // seconds
                if (ignoreTime) {
                    throw new XPathException("In format-date(): an xs:date value does not contain a seconds component")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
                } else {
                    IntegerValue seconds = (IntegerValue) value.getComponent(AccessorFn.Component.WHOLE_SECONDS);
                    assert seconds != null;
                    return formatNumber(component, (int) seconds.longValue(), format, defaultFormat, numberer, context);
                }
            case 'f':       // fractional seconds
                // ignore the format
                if (ignoreTime) {
                    throw new XPathException("In format-date(): an xs:date value does not contain a fractional seconds component")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
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
                    Optional<Boolean> b = NamedTimeZone.inSummerTime(baseDate, country);
                    if (b.isPresent() && b.get()) {
                        baseDate = new DateTimeValue(year, (byte) 7, (byte) 1, (byte) 0, (byte) 0, (byte) 0, 0, tzoffset, false);
                    }
                    dtv = DateTimeValue.makeDateTimeValue(baseDate.toDateValue(), (TimeValue)value);
                } else {
                    dtv = value.toDateTime();
                }
                return formatTimeZone(dtv, (char)component.codePointAt(0), format, country);

            case 'F':       // day of week
                if (ignoreDate) {
                    throw new XPathException("In format-time(): an xs:time value does not contain day-of-week component")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
                } else {
                    int day = DateValue.getDayOfWeek(dtvalue.getYear(), dtvalue.getMonth(), dtvalue.getDay());
                    return formatNumber(component, day, format, defaultFormat, numberer, context);
                }
            case 'P':       // am/pm marker
                if (ignoreTime) {
                    throw new XPathException("In format-date(): an xs:date value does not contain an am/pm component")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
                } else {
                    int minuteOfDay = dtvalue.getHour() * 60 + dtvalue.getMinute();
                    return formatNumber(component, minuteOfDay, format, defaultFormat, numberer, context);
                }
            case 'C':       // calendar
                return StringView.of(numberer.getCalendarName("AD")).tidy();
            case 'E':       // era
                if (ignoreDate) {
                    throw new XPathException("In format-time(): an xs:time value does not contain an AD/BC component")
                            .withErrorCode("FOFD1350")
                            .withXPathContext(context);
                } else {
                    int year = dtvalue.getYear();
                    return StringView.of(numberer.getEraName(year)).tidy();
                }
            default:
                throw new XPathException("Unknown format-date/time component specifier '" + format.substring(0, 1) + '\'')
                        .withErrorCode("FOFD1340")
                        .withXPathContext(context);
        }
    }

//    private static final Pattern formatPattern =
//            Pattern.compile("([^,]*)(,.*)?");           // Note, the group numbers are different from above

    private static final ARegularExpression widthPattern =
            ARegularExpression.compile(",(\\*|[0-9]+)(\\-(\\*|[0-9]+))?", "");

//    private static final Pattern alphanumericPattern =
//            Pattern.compile("([A-Za-z0-9]|\\p{L}|\\p{N})*");
//    // the first term is redundant, but GNU Classpath can't cope with the others...

    private static final ARegularExpression digitsPattern =
            ARegularExpression.compile("\\p{Nd}+", "");

    private static final ARegularExpression digitsOrOptionalDigitsPattern =
            ARegularExpression.compile("[#\\p{Nd}]+", "");


    private static final ARegularExpression fractionalDigitsPattern =
            ARegularExpression.compile("\\p{Nd}+#*", "");

    private static UnicodeString formatNumber(UnicodeString component, int value,
                                              UnicodeString format, boolean defaultFormat, Numberer numberer, XPathContext context)
            throws XPathException {
        int comma = (int)StringTool.lastIndexOf(format, ',');
        UnicodeString widths = EmptyUnicodeString.getInstance();
        if (comma >= 0) {
            widths = format.substring(comma);
            format = format.prefix(comma);
        }
        UnicodeString primary = format;
        String letterValue = null;
        String ordinal = null;
        int lastCP = StringTool.lastCodePoint(primary);
        if (lastCP == 't') {
            primary = primary.prefix(primary.length() - 1);
            letterValue = "traditional";
        } else if (lastCP == 'o') {
            primary = primary.prefix(primary.length() - 1);
            ordinal = numberer.getOrdinalSuffixForDateTime(component.toString());
        }

        int min = 1;
        int max = Integer.MAX_VALUE;

        if (digitsPattern.matches(primary)) {
            int primaryLen = primary.length32();
            if (primaryLen > 1) {
                // "A format token containing leading zeroes, such as 001, sets the minimum and maximum width..."
                // We interpret this literally: a format token of "1" does not set a maximum, because it would
                // cause the year 2006 to be formatted as "6".
                min = primaryLen;
                max = primaryLen;
            }
        }
        if (STR_Y.equals(component)) {
            min = max = 0;
            if (!widths.isEmpty()) {
                max = getWidths(widths)[1];
            } else if (digitsPattern.containsMatch(primary)) {
                IntIterator primaryIter = primary.codePoints();
                while (primaryIter.hasNext()) {
                    int c = primaryIter.next();
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
        if (primary.equals(STR_I) || primary.equals(STR_i)) {
            int[] range = getWidths(widths);
            min = range[0];
            //max = Integer.MAX_VALUE;

            String roman = numberer.format(value, primary, null, letterValue, "", ordinal);
            UnicodeBuilder s = new UnicodeBuilder(32);
            s.append(roman);
            int len = StringTool.getStringLength(roman);
            while (len < min) {
                s.append(' ');
                len++;
            }
            return s.toUnicodeString();
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
                if (StringTool.lastCodePoint(primary) == '1' && min != primary.length()) {
                    UnicodeBuilder sb = new UnicodeBuilder(min + 1);
                    for (int i = 1; i < min; i++) {
                        sb.append('0');
                    }
                    sb.append('1');
                    primary = sb.toUnicodeString();
                }
            }
        }

        if (STR_P.equals(component)) {
            // A.M./P.M. can only be formatted as a name
            if (!(STR_N.equals(primary) || STR_n.equals(primary) || STR_Nn.equals(primary))) {
                primary = STR_n;
            }
            if (max == Integer.MAX_VALUE) {
                // if no max specified, use 4. An explicit greater value allows use of "noon" and "midnight"
                max = 4;
            }
        } else if (STR_Y.equals(component)) {
            if (max < Integer.MAX_VALUE) {
                value = value % (int) Math.pow(10, max);
            }
        } else if (STR_f.equals(component)) {
            // value is supplied as integer number of microseconds
            // If there is no Unicode digit in the pattern, output is implementation defined, so do what comes easily
            if (!digitsPattern.containsMatch(primary)) {
                return formatNumber(component, value, STR_1, defaultFormat, numberer, context);
            }
            // if there are grouping separators, handle as a reverse integer as described in the 3.1 spec
            if (!digitsOrOptionalDigitsPattern.matches(primary)) {
                UnicodeString reverseFormat = reverse(format);
                UnicodeString reverseValue = reverse(BMPString.of("" + value));
                UnicodeString reverseResult = formatNumber(
                        STR_s, Integer.parseInt(reverseValue.toString()), reverseFormat, false, numberer, context);
                UnicodeString correctedResult = reverse(reverseResult);
                if (correctedResult.length() > max) {
                    correctedResult = correctedResult.prefix(max);
                }
                return correctedResult;
            }
            if (!fractionalDigitsPattern.matches(primary)) {
                throw new XPathException("Invalid picture for fractional seconds: " + primary, "FOFD1340");
            }
            UnicodeString str;
            if (value == 0) {
                str = STR_0;
            } else {
                str = BMPString.of(((1000000 + value) + "").substring(1));
                if (str.length() > max) {
                    // Spec bug 29749 says we should truncate rather than rounding
                    str = str.prefix(max);
                }
            }
            while (str.length() < min) {
                str = str.concat(STR_0);
            }
            if (str.length() > min)
            while (str.length() > min && str.codePointAt(str.length() - 1) == '0') {
                str = str.prefix(str.length() - 1);
            }
            // for non standard decimal digit family
            int zeroDigit = Alphanumeric.getDigitFamily(format.codePointAt(0));
            if (zeroDigit >= 0 && zeroDigit != '0') {
                int[] digits = new int[10];
                for (int z = 0; z <= 9; z++) {
                    digits[z] = zeroDigit + z;
                }
                long n = Long.parseLong(str.toString());
                int requiredLength = str.length32();
                str = StringView.tidy(AbstractNumberer.convertDigitSystem(n, digits, requiredLength));
            }
            return str;
        }

        if (STR_N.equals(primary) || STR_n.equals(primary) || STR_Nn.equals(primary)) {
            String s = "";
            if (STR_M.equals(component)) {
                s = numberer.monthName(value, min, max);
            } else if (STR_F.equals(component)) {
                s = numberer.dayName(value, min, max);
            } else if (STR_P.equals(component)) {
                s = numberer.halfDayName(value, min, max);
            } else {
                primary = STR_1;
            }
            if (STR_N.equals(primary)) {
                return StringView.tidy(s.toUpperCase());
            } else if (STR_n.equals(primary)) {
                return StringView.tidy(s.toLowerCase());
            } else {
                return StringView.tidy(s);
            }
        }

        // deal with grouping separators, decimal digit family, etc. for numeric values
        NumericGroupFormatter picGroupFormat;
        try {
            picGroupFormat = FormatInteger.getPicSeparators(primary, false);
        } catch (XPathException e) {
            throw e.replacingErrorCode("FODF1310", "FOFD1340");
        }
        UnicodeString adjustedPicture = picGroupFormat.getAdjustedPicture();

        String formattedStr = numberer.format(value, adjustedPicture, picGroupFormat, letterValue, "", ordinal);
        int formattedLen = StringTool.getStringLength(formattedStr);
        int digitZero;
        if (formattedLen < min) {
            digitZero = Alphanumeric.getDigitFamily(adjustedPicture.codePointAt(0));
            StringBuilder fsb = new StringBuilder(formattedStr);
            while (formattedLen < min) {
                StringTool.prependWideChar(fsb, digitZero);
                formattedLen = formattedLen + 1;
            }
            formattedStr = fsb.toString();
        }
        return StringView.tidy(formattedStr);
    }

    private static UnicodeString reverse(UnicodeString in) {
        UnicodeBuilder builder = new UnicodeBuilder(in.length32());
        for (long i = in.length() - 1; i >= 0; i--) {
            builder.append(in.codePointAt(i));
        }
        return builder.toUnicodeString();
    }

    private static int[] getWidths(UnicodeString widths) throws XPathException {
        try {
            int min = -1;
            int max = -1;

            if (!widths.isEmpty()) {
                RegexIterator widthIter = widthPattern.analyze(widths);
                StringValue firstMatch = widthIter.next();
                if (firstMatch != null && firstMatch.length() == widths.length() && widthIter.isMatching()) {
                    UnicodeString smin = widthIter.getRegexGroup(1);
                    if (smin == null || smin.isEmpty() || StringConstants.ASTERISK.equals(smin)) {
                        min = 1;
                    } else {
                        min = Integer.parseInt(smin.toString());
                    }
                    UnicodeString smax = widthIter.getRegexGroup(3);
                    if (smax == null || smax.isEmpty() || StringConstants.ASTERISK.equals(smax)) {
                        max = Integer.MAX_VALUE;
                    } else {
                        max = Integer.parseInt(smax.toString());
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

//            if (min > max) {
//                XPathException e = new XPathException("Minimum width in date/time picture exceeds maximum width");
//                e.setErrorCode("FOFD1340");
//                throw e;
//            }
            int[] result = new int[2];
            result[0] = min;
            result[1] = max;
            return result;
        } catch (NumberFormatException err) {
            throw new XPathException("Invalid integer used as width in date/time picture", "FOFD1340");
        }
    }

    private static UnicodeString formatTimeZone(DateTimeValue value, char component, UnicodeString format, String country) throws XPathException {
        int comma = (int)StringTool.lastIndexOf(format, ',');
        UnicodeString widthModifier = EmptyUnicodeString.getInstance();
        if (comma >= 0) {
            widthModifier = format.substring(comma);
            format = format.prefix(comma);
        }
        if (!value.hasTimezone()) {
            if (format.equals(STR_Z)) {
                // military "local time"
                return STR_J;
            } else {
                return EmptyUnicodeString.getInstance();
            }
        }
        if (format.isEmpty() && !widthModifier.isEmpty()) {
            int[] widths = getWidths(widthModifier);
            int min = widths[0];
            int max = widths[1];
            if (min <= 1) {
                format = BMPString.of(max >= 4 ? "0:00" : "0");
            } else if (min <= 4) {
                format = BMPString.of(max >= 5 ? "00:00" : "00");
            } else {
                format = BMPString.of("00:00");
            }
        }
        if (format.isEmpty()) {
            format = BMPString.of("00:00");
        }
        int tz = value.getTimezoneInMinutes();
        boolean useZforZero = StringTool.lastCodePoint(format) == 't';
        if (useZforZero && tz == 0) {
            return STR_Z;
        }
        if (useZforZero) {
            format = format.prefix(format.length() - 1);
        }
        int digits = 0;
        int separators = 0;
        int separatorChar = ':';
        int zeroDigit = -1;
        int[] expandedFormat = StringTool.expand(format);
        for (int ch : expandedFormat) {
            if (Character.getType(ch) == Character.DECIMAL_DIGIT_NUMBER) {
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

            return StringTool.fromCodePoints(buffer, used);
        } else if (format.equals(BMPString.of("Z"))) {
            // military timezone formatting
            int hour = tz / 60;
            int minute = tz % 60;
            if (hour < -12 || hour > 12 || minute != 0) {
                return formatTimeZone(value, 'Z', BMPString.of("00:00"), country);
            } else {
                return BMPString.of("" + "YXWVUTSRQPONZABCDEFGHIKLM".charAt(hour + 12));
            }
        } else if (format.codePointAt(0) == 'N' || format.codePointAt(0) == 'n') {
            return StringView.of(getNamedTimeZone(value, country, format)).tidy();
        } else {
            return formatTimeZone(value, 'Z', BMPString.of("00:00"), country);
        }

    }

    private static String getNamedTimeZone(DateTimeValue value, String country, UnicodeString format) throws XPathException {

        int min = 1;
        int comma = (int)format.indexOf(',');
        if (comma > 0) {
            UnicodeString widths = format.substring(comma);
            int[] range = getWidths(widths);
            min = range[0];
        }
        if (format.codePointAt(0) == 'N' || format.codePointAt(0) == 'n') {
            if (min <= 5) {
                String tzname = NamedTimeZone.getTimeZoneNameForDate(value, country);
                if (tzname == null) {
                    return formatTimeZone(value, 'Z', BMPString.of("Z00:00t"), country).toString();
                }
                if (format.codePointAt(0) == 'n') {
                    tzname = tzname.toLowerCase();
                }
                return tzname;
            } else {
                return NamedTimeZone.getOlsonTimeZoneName(value, country);
            }
        }
        UnicodeBuilder sbz = new UnicodeBuilder(16);
        value.appendTimezone(sbz);
        return sbz.toString();
    }


    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        CalendarValue value = (CalendarValue) arguments[0].head();
        if (value == null) {
            return EmptySequence.getInstance();
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

        String calendar = calendarVal == null ? null : calendarVal.getStringValue();
        String language = languageVal == null ? null : languageVal.getStringValue();
        String place = countryVal == null ? null : countryVal.getStringValue();
        if (place != null) {
            value = adjustTimezoneToPlace(value, place);
        }
        String result = formatDate(value, format, language, place, context);
        if (calendarVal != null) {
            result = adjustCalendar(calendar, result, context);
        }
        return new StringValue(result);
    }

    private CalendarValue adjustTimezoneToPlace(CalendarValue value, String place) {
        if (place.contains("/") && value.hasTimezone() && !(value instanceof TimeValue)) {
            ZoneId zone = NamedTimeZone.getNamedTimeZone(place);
            if (zone != null) {
                int offsetSeconds = zone.getRules().getOffset(value.toDateTime().toJavaInstant()).getTotalSeconds();
                return value.adjustTimezone(offsetSeconds / 60);
            }
        }
        return value;
    }

}

