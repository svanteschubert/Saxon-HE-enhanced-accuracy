////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.ValidationFailure;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of the xs:gYear data type
 */

public class GYearValue extends GDateValue {

    private static Pattern regex =
            Pattern.compile("(-?[0-9]+)(Z|[+-][0-9][0-9]:[0-9][0-9])?");

    private GYearValue() {
    }

    public static ConversionResult makeGYearValue(CharSequence value, ConversionRules rules) {
        GYearValue g = new GYearValue();
        Matcher m = regex.matcher(Whitespace.trimWhitespace(value));
        if (!m.matches()) {
            return new ValidationFailure("Cannot convert '" + value + "' to a gYear");
        }
        String base = m.group(1);
        String tz = m.group(2);
        String date = base + "-01-01" + (tz == null ? "" : tz);
        g.typeLabel = BuiltInAtomicType.G_YEAR;
        return setLexicalValue(g, date, rules.isAllowYearZero());
    }

    public GYearValue(int year, int tz, boolean xsd10) {
        this(year, tz, BuiltInAtomicType.G_YEAR);
        this.hasNoYearZero = xsd10;
    }

    public GYearValue(int year, int tz, AtomicType type) {
        this.year = year;
        this.month = 1;
        this.day = 1;
        setTimezoneInMinutes(tz);
        this.typeLabel = type;
    }

    /**
     * Make a copy of this date, time, or dateTime value
     *
     * @param typeLabel
     */

    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        GYearValue v = new GYearValue(year, getTimezoneInMinutes(), true);
        v.typeLabel = typeLabel;
        return v;
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.G_YEAR;
    }

    /*@NotNull*/
    @Override
    public CharSequence getPrimitiveStringValue() {

        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C16);
        int yr = year;
        if (year <= 0) {
            yr = -yr + (hasNoYearZero ? 1 : 0);           // no year zero in lexical space for XSD 1.0
            if (yr != 0) {
                sb.cat('-');
            }
        }
        appendString(sb, yr, (yr > 9999 ? (yr + "").length() : 4));

        if (hasTimezone()) {
            appendTimezone(sb);
        }

        return sb;

    }

    /**
     * Add a duration to this date/time value
     *
     * @param duration the duration to be added (which might be negative)
     * @return a new date/time value representing the result of adding the duration. The original
     *         object is not modified.
     * @throws net.sf.saxon.trans.XPathException
     *
     */

    @Override
    public CalendarValue add(DurationValue duration) throws XPathException {
        XPathException err = new XPathException("Cannot add a duration to an xs:gYear");
        err.setErrorCode("XPTY0004");
        throw err;
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
        DateTimeValue dt = (DateTimeValue) toDateTime().adjustTimezone(tz);
        return new GYearValue(dt.getYear(), dt.getTimezoneInMinutes(), hasNoYearZero);
    }
}

