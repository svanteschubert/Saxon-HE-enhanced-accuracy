////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.ValidationFailure;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A value of type xs:yearMonthDuration.
 * <p>The state retained by this class is essentially a signed 32-bit integer representing the number
 * of months: that is, {@code year*12 + month}; plus a type label allowing subtypes of {@code xs:yearMonthDuration}
 * to be represented.</p>
 */

public final class YearMonthDurationValue extends DurationValue implements Comparable<YearMonthDurationValue> {

    /**
     * Private constructor for internal use
     */

    private YearMonthDurationValue() {
        typeLabel = BuiltInAtomicType.YEAR_MONTH_DURATION;
    }

    /**
     * Static factory: create a year-month duration value from a supplied string, in
     * ISO 8601 format [+|-]PnYnM
     *
     * @param s a string in the lexical space of xs:yearMonthDuration.
     * @return either a YearMonthDurationValue, or a ValidationFailure if the string was
     *         not in the lexical space of xs:yearMonthDuration.
     */

    public static ConversionResult makeYearMonthDurationValue(CharSequence s) {
        ConversionResult d = DurationValue.makeDuration(s, true, false);
        if (d instanceof ValidationFailure) {
            return d;
        }
        DurationValue dv = (DurationValue) d;
        return YearMonthDurationValue.fromMonths((dv.getYears() * 12 + dv.getMonths()) * dv.signum());
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param typeLabel the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    /*@NotNull*/
    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        YearMonthDurationValue v = YearMonthDurationValue.fromMonths(getLengthInMonths());
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
        return BuiltInAtomicType.YEAR_MONTH_DURATION;
    }

    /**
     * Convert to string
     *
     * @return ISO 8601 representation.
     */

    @Override
    public CharSequence getPrimitiveStringValue() {

        // The canonical representation has months in the range 0-11

        int y = getYears();
        int m = getMonths();

        FastStringBuffer sb = new FastStringBuffer(32);
        if (negative) {
            sb.cat('-');
        }
        sb.cat('P');
        if (y != 0) {
            sb.append(y + "Y");
        }
        if (m != 0 || y == 0) {
            sb.append(m + "M");
        }
        return sb;

    }

    /**
     * Get the number of months in the duration
     *
     * @return the number of months in the duration
     */

    public int getLengthInMonths() {
        return months * (negative ? -1 : +1);
    }

    /**
     * Construct a duration value as a number of months.
     *
     * @param months the number of months (may be negative)
     * @return the corresponding xs:yearMonthDuration value
     */

    public static YearMonthDurationValue fromMonths(int months) {
        YearMonthDurationValue mdv = new YearMonthDurationValue();
        mdv.negative = months < 0;
        mdv.months = months < 0 ? -months : months;
        mdv.seconds = 0;
        mdv.nanoseconds = 0;
        return mdv;
    }

    /**
     * Multiply a duration by an integer
     *
     * @param factor the number to multiply by
     * @return the result of the multiplication
     */

    @Override
    public YearMonthDurationValue multiply(long factor) throws XPathException {
        // Fast path for simple cases
        if (Math.abs(factor) < 30_000 && Math.abs(months) < 30_000) {
            return YearMonthDurationValue.fromMonths((int)factor * getLengthInMonths());
        } else {
            return multiply((double)factor);
        }
    }

    /**
     * Multiply duration by a number.
     */

    @Override
    public YearMonthDurationValue multiply(double n) throws XPathException {
        if (Double.isNaN(n)) {
            XPathException err = new XPathException("Cannot multiply a duration by NaN");
            err.setErrorCode("FOCA0005");
            throw err;
        }
        double m = (double) getLengthInMonths();
        double product = n * m;
        if (Double.isInfinite(product) || product > Integer.MAX_VALUE || product < Integer.MIN_VALUE) {
            XPathException err = new XPathException("Overflow when multiplying a duration by a number");
            err.setErrorCode("FODT0002");
            throw err;
        }
        return fromMonths((int) Math.round(product));
    }


    /**
     * Divide duration by a number.
     */

    @Override
    public DurationValue divide(double n) throws XPathException {
        if (Double.isNaN(n)) {
            XPathException err = new XPathException("Cannot divide a duration by NaN");
            err.setErrorCode("FOCA0005");
            throw err;
        }
        double m = (double) getLengthInMonths();
        double product = m / n;
        if (Double.isInfinite(product) || product > Integer.MAX_VALUE || product < Integer.MIN_VALUE) {
            XPathException err = new XPathException("Overflow when dividing a duration by a number");
            err.setErrorCode("FODT0002");
            throw err;
        }
        return fromMonths((int) Math.round(product));
    }

    /**
     * Find the ratio between two durations
     *
     * @param other the dividend
     * @return the ratio, as a decimal
     * @throws XPathException if an error occurs, for example division by zero or dividing durations of different type
     */

    @Override
    public BigDecimalValue divide(DurationValue other) throws XPathException {
        if (other instanceof YearMonthDurationValue) {
            BigDecimal v1 = BigDecimal.valueOf(getLengthInMonths());
            BigDecimal v2 = BigDecimal.valueOf(((YearMonthDurationValue) other).getLengthInMonths());
            if (v2.signum() == 0) {
                XPathException err = new XPathException("Divide by zero (durations)");
                err.setErrorCode("FOAR0001");
                throw err;
            }
            return new BigDecimalValue(v1.divide(v2, 20, RoundingMode.HALF_EVEN));
        } else {
            XPathException err = new XPathException("Cannot divide two durations of different type");
            err.setErrorCode("XPTY0004");
            throw err;
        }
    }

    /**
     * Add two year-month-durations
     */

    @Override
    public DurationValue add(DurationValue other) throws XPathException {
        if (other instanceof YearMonthDurationValue) {
            return fromMonths(getLengthInMonths() +
                    ((YearMonthDurationValue) other).getLengthInMonths());
        } else {
            XPathException err = new XPathException("Cannot add two durations of different type");
            err.setErrorCode("XPTY0004");
            throw err;
        }
    }

    /**
     * Subtract two year-month-durations
     */

    @Override
    public DurationValue subtract(DurationValue other) throws XPathException {
        if (other instanceof YearMonthDurationValue) {
            return fromMonths(getLengthInMonths() -
                    ((YearMonthDurationValue) other).getLengthInMonths());
        } else {
            XPathException err = new XPathException("Cannot subtract two durations of different type");
            err.setErrorCode("XPTY0004");
            throw err;
        }
    }

    /**
     * Negate a duration (same as subtracting from zero, but it preserves the type of the original duration)
     */

    @Override
    public DurationValue negate() {
        return fromMonths(-getLengthInMonths());
    }

    /**
     * Compare the value to another duration value
     *
     * @param other The other dateTime value
     * @return negative value if this one is the earler, 0 if they are chronologically equal,
     *         positive value if this one is the later. For this purpose, dateTime values with an unknown
     *         timezone are considered to be UTC values (the Comparable interface requires
     *         a total ordering).
     * @throws ClassCastException if the other value is not a DateTimeValue (the parameter
     *                            is declared as Object to satisfy the Comparable interface)
     */

    @Override
    public int compareTo(YearMonthDurationValue other) {
        return Integer.compare(getLengthInMonths(), other.getLengthInMonths());
    }

    /**
     * Get a Comparable value that implements the XPath ordering comparison semantics for this value.
     * Returns null if the value is not comparable according to XPath rules. The default implementation
     * returns the value itself. This is modified for types such as
     * xs:duration which allow ordering comparisons in XML Schema, but not in XPath.
     *
     * @param ordered true if ordered comparisons need to be supported
     * @param collator for comparing strings - not used
     * @param implicitTimezone implicit timezone in the dynamic context - not used
     */

    @Override
    public AtomicMatchKey getXPathComparable(boolean ordered, StringCollator collator, int implicitTimezone) {
        return this;
    }


}

