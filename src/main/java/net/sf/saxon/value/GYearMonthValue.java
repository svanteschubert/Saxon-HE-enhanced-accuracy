////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.str.*;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;

import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of the xs:gYearMonth data type
 */

public class GYearMonthValue extends GDateValue {

    private static final Pattern regex =
            Pattern.compile("(-?[0-9]+-[0-9][0-9])(Z|[+-][0-9][0-9]:[0-9][0-9])?");

    /**
     * Constructs an instance of GYearMonthValue without any validation
     * of the input values.
     *
     * @param year  - number of a year in the Gregorian calendar
     * @param month - number of a month within the specified year
     * @param tzMinutes - number of minutes to adjust by for the timezone
     */

    public GYearMonthValue(int year, int month, int tzMinutes) {
        super(year, month, DEFAULT_DAY, tzMinutes, BuiltInAtomicType.G_YEAR_MONTH);
    }

    /**
     * Constructs an instance of GYearMonthValue without any validation
     * of the input values, with a type annotation
     *
     * @param year      - number of a year in the Gregorian calendar
     * @param month     - number of a month within the specified year
     * @param tzMinutes - number of minutes to adjust by for the timezone
     */

    public GYearMonthValue(int year, int month, int tzMinutes, AtomicMetadata type) {
        super(year, month, DEFAULT_DAY, tzMinutes, type);
    }

    protected EnumSet<AccessorFn.Component> getDefinedComponents() {
        return EnumSet.of(AccessorFn.Component.YEAR, AccessorFn.Component.MONTH);
    }

    /**
     * Parse a GYearMonth value supplied as a string
     * @param value the supplied string
     * @param rules conversion rules - indicate whether year zero is valid
     * @return either a GYearMonth value, or a {@link ValidationFailure}
     */

    public static ConversionResult makeGYearMonthValue(UnicodeString value, ConversionRules rules) {
        final UnicodeString trimmed = Whitespace.trim(value);
        Matcher m = regex.matcher(trimmed.toString());
        if (!m.matches()) {
            return new ValidationFailure("Cannot convert '" + value + "' to a gYearMonth");
        }
        String base = m.group(1);
        String tz = m.group(2);
        String date = base + "-01" + (tz == null ? "" : tz);
        ConversionResult result = DateValue.tryParseDate(date, rules.isAllowYearZero());
        if (result instanceof ValidationFailure) {
            return result;
        }
        DateValue dv = (DateValue) result;
        return new GYearMonthValue(dv.getYear(), dv.getMonth(), dv.getTimezoneInMinutes());
    }
    
    /**
     * Creates an instance of GYearMonthValue.  Includes validation
     * checks.  If a validation error is detected, an instance of
     * ValidationFailure will be returned instead.
     *
     * @param year - number of a year in the Gregorian calendar
     * @param month - number of a month within the specified year
     * @param timezoneInMinutes - number of minutes to adjust by for the timezone
     * @return an instance of GYearMonthValue or ValidationFailure
     */
    public static ConversionResult makeGYearMonthValue(int year, int month, int timezoneInMinutes) {
        if (!isValidDate(year, month, 1)) {
            return new ValidationFailure("Invalid gYearMonth value " + year + "-" + month);
        }
        if (!isValidTimezone(timezoneInMinutes)) {
            return new ValidationFailure("Invalid timezone offset " + timezoneInMinutes + " minutes");
        }
        return new GYearMonthValue(year, month, timezoneInMinutes);
    }

    /**
     * Make a copy of this date, time, or dateTime value, with specified type annotation
     *
     * @param metadata the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     * @return the copied value
     */

    @Override
    public AtomicValue withMetadata(AtomicMetadata metadata) {
        return new GYearMonthValue(year, month, getTimezoneInMinutes(), metadata);
    }

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone, int specVersion) throws NoDynamicContextException {
        return specVersion >= 40 ? new DateValue(year, month, (byte) 1)
                .adjustTimezone(hasTimezone() ? getTimezoneInMinutes() : implicitTimezone) : null;
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.G_YEAR_MONTH;
    }

    /*@NotNull*/
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
        tb = appendString(tb, yr, (yr > 9999 ? (yr + "").length() : 4)).append('-');
        tb = appendTwoDigits(tb, month);

        if (hasTimezone()) {
            tb = appendTimezone(tb);
        }

        return tb.toUnicodeString();

    }

    /**
     * Add a duration to this date/time value
     *
     * @param duration the duration to be added (which might be negative)
     * @return a new date/time value representing the result of adding the duration. The original
     *         object is not modified.
     * @throws net.sf.saxon.trans.XPathException if an error is detected
     *
     */

    @Override
    public CalendarValue add(DurationValue duration) throws XPathException {
        throw new XPathException("Cannot add a duration to an xs:gYearMonth", "XPTY0004").asTypeError();
    }

    /**
     * Return a new date, time, or dateTime with the same normalized value, but
     * in a different timezone
     *
     * @param tz the new timezone, in minutes
     * @return the date/time in the new timezone
     */

    @Override
    public CalendarValue adjustTimezone(int tz) {
        DateTimeValue dt = toDateTime().adjustTimezone(tz);
        return new GYearMonthValue(dt.getYear(), dt.getMonth(), dt.getTimezoneInMinutes());
    }
}

