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
import net.sf.saxon.str.TwineBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicMetadata;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.ValidationFailure;

import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of the xs:gDay data type
 */

public class GDayValue extends GDateValue {

    private static final Pattern regex =
            Pattern.compile("---([0-9][0-9])(Z|[+-][0-9][0-9]:[0-9][0-9])?");

    /**
     * Constructs an instance of GDayValue without any validation
     * of the input values.
     *
     * @param day - number of a day within an arbitrary month
     * @param tz  - number of minutes to adjust by for the timezone
     */

    public GDayValue(int day, int tz) {
        super(DEFAULT_YEAR, DEFAULT_MONTH, day, tz, BuiltInAtomicType.G_DAY);
    }

    /**
     * Constructs an instance of GDayValue without any validation
     * of the input values.
     *
     * @param day - number of a day within an arbitrary month
     * @param tz  - number of minutes to adjust by for the timezone
     * @param type - the type annotation to be used
     */

    public GDayValue(int day, int tz, AtomicMetadata type) {
        super(DEFAULT_YEAR, DEFAULT_MONTH, day, tz, type);
    }

    protected EnumSet<AccessorFn.Component> getDefinedComponents() {
        return EnumSet.of(AccessorFn.Component.DAY);
    }

    /**
     * Parse a GDay value from its lexical representation
     * @param value the lexical representation of the xs:gDay value
     * @return either a {@link GDayValue}, or a validation error
     */
    public static ConversionResult makeGDayValue(UnicodeString value) {
        final UnicodeString trimmed = Whitespace.trim(value);
        Matcher m;
        m = regex.matcher(trimmed.toString());
        if (!m.matches()) {
            return new ValidationFailure("Cannot convert '" + value + "' to a gDay");
        }
        String base = m.group(1);
        String tz = m.group(2);
        String date = "1972-01-" + base + (tz == null ? "" : tz);
        ConversionResult result = DateValue.tryParseDate(date, true);
        if (result instanceof ValidationFailure) {
            return result;
        }
        DateValue dv = (DateValue) result;
        return new GDayValue(dv.getDay(), dv.getTimezoneInMinutes());
    }

    /**
     * Create an instance of GDayValue, with validation
     * checks.  If a validation error is detected, an instance of
     * ValidationFailure will be returned instead.
     *
     * @param day - number of a day within an arbitrary month
     * @param timezoneInMinutes - number of minutes to adjust by for the timezone
     * @return - an instance of GDayValue
     */
    public static ConversionResult makeGDayValue(int day, int timezoneInMinutes) {
        if (!isValidDate(1972, 1, day)) {
            return new ValidationFailure("Invalid gDay value " + day);
        }
        if (!isValidTimezone(timezoneInMinutes)) {
            return new ValidationFailure("Invalid timezone offset " + timezoneInMinutes + " minutes", "FODT003");
        }
        return new GDayValue(day, timezoneInMinutes);
    }

    /**
     * Make a copy of this value with a different type annotation
     * @param metadata the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     * @return the copied value
     */

    @Override
    public AtomicValue withMetadata(AtomicMetadata metadata) {
        return new GDayValue(getDay(), getTimezoneInMinutes(), metadata);
    }


    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone, int specVersion) throws NoDynamicContextException {
        return specVersion >= 40 ? new DateValue(DEFAULT_YEAR, DEFAULT_MONTH, day)
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
        return BuiltInAtomicType.G_DAY;
    }

    /*@NotNull*/
    @Override
    public UnicodeString getPrimitiveStringValue() {

        TwineBuilder tb = TwineBuilder.make(16);

        tb = tb.append('-').append('-').append('-');
        tb = appendTwoDigits(tb, day);

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
        throw new XPathException("Cannot add a duration to an xs:gDay", "XPTY0004").asTypeError();
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
        return new GDayValue(dt.getDay(), dt.getTimezoneInMinutes());
    }
}

