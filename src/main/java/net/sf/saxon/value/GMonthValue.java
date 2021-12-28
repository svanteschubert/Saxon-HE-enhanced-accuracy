////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.ValidationFailure;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of the xs:gMonth data type
 */

public class GMonthValue extends GDateValue {

    private static Pattern regex =
            //Pattern.compile("--([0-9][0-9])(--)?(Z|[+-][0-9][0-9]:[0-9][0-9])?");
            Pattern.compile("--([0-9][0-9])(Z|[+-][0-9][0-9]:[0-9][0-9])?");
    // The commented-out pattern tolerates the bogus format --MM-- which was wrongly permitted by the original schema spec

    private GMonthValue() {
    }

    public static ConversionResult makeGMonthValue(CharSequence value) {
        GMonthValue g = new GMonthValue();
        Matcher m = regex.matcher(Whitespace.trimWhitespace(value));
        if (!m.matches()) {
            return new ValidationFailure("Cannot convert '" + value + "' to a gMonth");
        }
        String base = m.group(1);
        String tz = m.group(2);
        String date = "2000-" + base + "-01" + (tz == null ? "" : tz);
        g.typeLabel = BuiltInAtomicType.G_MONTH;
        return setLexicalValue(g, date, true);
    }

    public GMonthValue(byte month, int tz) {
        this(month, tz, BuiltInAtomicType.G_MONTH);
    }

    public GMonthValue(byte month, int tz, AtomicType type) {
        this.year = 2000;
        this.month = month;
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
        GMonthValue v = new GMonthValue(month, getTimezoneInMinutes());
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
        return BuiltInAtomicType.G_MONTH;
    }

    /*@NotNull*/
    @Override
    public CharSequence getPrimitiveStringValue() {

        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C16);

        sb.append("--");
        appendTwoDigits(sb, month);

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
        XPathException err = new XPathException("Cannot add a duration to an xs:gMonth");
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
        return new GMonthValue(dt.getMonth(), dt.getTimezoneInMinutes());
    }
}

