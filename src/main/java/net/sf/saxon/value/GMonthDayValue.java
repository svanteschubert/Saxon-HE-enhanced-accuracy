////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.str.BMPString;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.ValidationFailure;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of the xs:gYear data type
 */

public class GMonthDayValue extends GDateValue {

    private static final Pattern regex =
            Pattern.compile("--([0-9][0-9]-[0-9][0-9])(Z|[+-][0-9][0-9]:[0-9][0-9])?");

    private GMonthDayValue(MutableGDateValue m) {
        super(m);
    }

    public static ConversionResult makeGMonthDayValue(UnicodeString value) {
        final UnicodeString trimmed = Whitespace.trim(value);
        Matcher m = regex.matcher(trimmed.toString());
        if (!m.matches()) {
            return new ValidationFailure("Cannot convert '" + value + "' to a gMonthDay");
        }
        MutableGDateValue g = new MutableGDateValue();
        String base = m.group(1);
        String tz = m.group(2);
        String date = "2000-" + base + (tz == null ? "" : tz);
        g.typeLabel = BuiltInAtomicType.G_MONTH_DAY;
        setLexicalValue(g, BMPString.of(date), true);
        return g.error == null ? new GMonthDayValue(g) : g.error;
    }

    public GMonthDayValue(byte month, byte day, int tz) {
        this(month, day, tz, BuiltInAtomicType.G_MONTH_DAY);
    }

    public GMonthDayValue(byte month, byte day, int tz, AtomicType type) {
        this(new MutableGDateValue(2000, month, day, false, tz, type));
    }

    /**
     * Make a copy of this date, time, or dateTime value
     *
     * @param typeLabel the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     * @return the copied value
     */

    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        MutableGDateValue m = makeMutableCopy();
        m.typeLabel = typeLabel;
        return new GMonthDayValue(m);
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.G_MONTH_DAY;
    }

    /*@NotNull*/
    @Override
    public UnicodeString getPrimitiveStringValue() {

        UnicodeBuilder sb = new UnicodeBuilder(16);

        sb.appendLatin("--");
        appendTwoDigits(sb, month);
        sb.append('-');
        appendTwoDigits(sb, day);

        if (hasTimezone()) {
            appendTimezone(sb);
        }

        return sb.toUnicodeString();

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
        throw new XPathException("Cannot add a duration to an xs:gMonthDay", "XPTY0004").asTypeError();
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
        return new GMonthDayValue(dt.getMonth(), dt.getDay(), dt.getTimezoneInMinutes());
    }
}

