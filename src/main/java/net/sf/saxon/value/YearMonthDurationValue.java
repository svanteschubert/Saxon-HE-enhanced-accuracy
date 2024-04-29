////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.ValidationFailure;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A value of type xs:yearMonthDuration (or a subtype thereof).
 * <p>The state retained by this class is essentially a signed 32-bit integer representing the number
 * of months: that is, {@code year*12 + month}; plus a type label allowing subtypes of {@code xs:yearMonthDuration}
 * to be represented.</p>
 */

public final class YearMonthDurationValue extends DurationValue implements XPathComparable, ContextFreeAtomicValue {

    public YearMonthDurationValue(int months, AtomicType typeLabel) {
        super(0, months, 0, 0, 0, 0, 0, typeLabel);
    }

    /**
     * Static factory: create a year-month duration value from a supplied string, in
     * ISO 8601 format [+|-]PnYnM
     *
     * @param s a string in the lexical space of xs:yearMonthDuration.
     * @return either a YearMonthDurationValue, or a ValidationFailure if the string was
     *         not in the lexical space of xs:yearMonthDuration.
     */

    public static ConversionResult makeYearMonthDurationValue(UnicodeString s) {
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
        return new YearMonthDurationValue(getLengthInMonths(), typeLabel);
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
    public UnicodeString getPrimitiveStringValue() {

        // The canonical representation has months in the range 0-11

        int y = getYears();
        int m = getMonths();

        UnicodeBuilder sb = new UnicodeBuilder(16);
        if (_negative) {
            sb.append('-');
        }
        sb.append('P');
        if (y != 0) {
            sb.append(y + "Y");
        }
        if (m != 0 || y == 0) {
            sb.append(m + "M");
        }
        return sb.toUnicodeString();

    }

    /**
     * Get the number of months in the duration
     *
     * @return the number of months in the duration
     */

    public int getLengthInMonths() {
        return _months * (_negative ? -1 : +1);
    }

    /**
     * Construct a duration value as a number of months.
     *
     * @param months the number of months (may be negative)
     * @return the corresponding xs:yearMonthDuration value
     */

    public static YearMonthDurationValue fromMonths(int months) {
        return new YearMonthDurationValue(months, BuiltInAtomicType.YEAR_MONTH_DURATION);
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
        if (Math.abs(factor) < 30_000 && Math.abs(_months) < 30_000) {
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
            throw new XPathException("Cannot multiply a duration by NaN", "FOCA0005");
        }
        double m = getLengthInMonths();
        double product = n * m;
        if (Double.isInfinite(product) || product > Integer.MAX_VALUE || product < Integer.MIN_VALUE) {
            throw new XPathException("Overflow when multiplying a duration by a number", "FODT0002");
        }
        // following code is needed to get the correct rounding on both Java and C#
        return fromMonths((int)new DoubleValue(product).round(0).longValue());
    }

    /**
     * Multiply duration by a decimal.
     */

    @Override
    public YearMonthDurationValue multiply(BigDecimal n) throws XPathException {
        int m = getLengthInMonths();
        BigDecimal product = n.multiply(BigDecimal.valueOf(m));
        if (product.abs().compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new XPathException("Overflow when multiplying a duration by a number", "FODT0002");
        }
        // following code is needed to get the correct rounding on both Java and C#
        return fromMonths((int) new BigDecimalValue(product).round(0).longValue());
    }


    /**
     * Divide duration by a number.
     */

    @Override
    public DurationValue divide(double n) throws XPathException {
        if (Double.isNaN(n)) {
            throw new XPathException("Cannot divide a duration by NaN", "FOCA0005");
        }
        double m = getLengthInMonths();
        double product = m / n;
        if (Double.isInfinite(product) || product > Integer.MAX_VALUE || product < Integer.MIN_VALUE) {
            throw new XPathException("Overflow when dividing a duration by a number", "FODT0002");
        }
        // following code is needed to get the correct rounding on both Java and C#
        return fromMonths((int) new DoubleValue(product).round(0).longValue());
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
                throw new XPathException("Divide by zero (durations)", "FOAR0001");
            }
            return new BigDecimalValue(divideBigDecimal(v1, v2));
        } else {
            throw new XPathException("Cannot divide two durations of different type", "XPTY0004");
        }
    }

    @CSharpReplaceBody(code="return Singulink.Numerics.BigDecimal.Divide(v1, v2, 20, Singulink.Numerics.RoundingMode.MidpointToEven);")
    private BigDecimal divideBigDecimal(BigDecimal v1, BigDecimal v2) {
        return v1.divide(v2, 20, RoundingMode.HALF_EVEN);
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
            throw new XPathException("Cannot add two durations of different type", "XPTY0004").asTypeError();
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
            throw new XPathException("Cannot subtract two durations of different type", "XPTY0004").asTypeError();
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
    public int compareTo(XPathComparable other) {
        if (other instanceof YearMonthDurationValue) {
            return Integer.compare(getLengthInMonths(), ((YearMonthDurationValue) other).getLengthInMonths());
        } else {
            throw new ClassCastException("Cannot compare xs:yearMonthDuration with " + other);
        }
    }

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone) {
        return this;
    }

    @Override
    public XPathComparable getXPathComparable() {
        return this;
    }

    /**
     * Get a Comparable value that implements the XPath ordering comparison semantics for this value.
     * Returns null if the value is not comparable according to XPath rules. The default implementation
     * returns the value itself. This is modified for types such as
     * xs:duration which allow ordering comparisons in XML Schema, but not in XPath.
     *  @param collator for comparing strings - not used
     * @param implicitTimezone implicit timezone in the dynamic context - not used
     */

    @Override
    public AtomicMatchKey getXPathMatchKey(StringCollator collator, int implicitTimezone) {
        return this;
    }


}

