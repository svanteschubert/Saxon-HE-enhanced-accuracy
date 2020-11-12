////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.exslt;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.StringToDouble;
import net.sf.saxon.value.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * This class implements extension functions in the
 * http://exslt.org/math namespace. <p>
 */

public abstract class Math {

    /**
     * Get the maximum numeric value of the string-value of each of a set of nodes
     */

    public static double max(XPathContext context, SequenceIterator nsv) throws XPathException {
        double max = Double.NEGATIVE_INFINITY;
        StringToDouble converter = context.getConfiguration().getConversionRules().getStringToDoubleConverter();
        try {
            while (true) {
                Item it = nsv.next();
                if (it == null) break;
                double x = converter.stringToNumber(it.getStringValueCS());
                if (Double.isNaN(x)) return x;
                if (x > max) max = x;
            }
            return max;
        } catch (NumberFormatException err) {
            return Double.NaN;
        }
    }


    /**
     * Get the minimum numeric value of the string-value of each of a set of nodes
     */

    public static double min(XPathContext context, SequenceIterator nsv) throws XPathException {
        double min = Double.POSITIVE_INFINITY;
        StringToDouble converter = context.getConfiguration().getConversionRules().getStringToDoubleConverter();
        try {
            while (true) {
                Item it = nsv.next();
                if (it == null) break;
                double x = converter.stringToNumber(it.getStringValueCS());
                if (Double.isNaN(x)) return x;
                if (x < min) min = x;
            }
            return min;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }


    /**
     * Get the items with maximum numeric value of the string-value of each of a sequence of items.
     * The items are returned in the order of the original sequence.
     */

    public static Sequence highest(XPathContext context, SequenceIterator nsv) throws XPathException {
        double max = Double.NEGATIVE_INFINITY;
        StringToDouble converter = context.getConfiguration().getConversionRules().getStringToDoubleConverter();
        try {
            List<Item> highest = new ArrayList<>();
            while (true) {
                Item it = nsv.next();
                if (it == null) {
                    break;
                }
                double x = converter.stringToNumber(it.getStringValueCS());
                if (Double.isNaN(x)) {
                    return EmptySequence.getInstance();
                }
                if (x == max) {
                    highest.add(it);
                } else if (x > max) {
                    max = x;
                    highest.clear();
                    highest.add(it);
                }
            }
            return new SequenceExtent(highest);
        } catch (NumberFormatException e) {
            return EmptySequence.getInstance();
        }
    }


    /**
     * Get the items with minimum numeric value of the string-value of each of a sequence of items
     * The items are returned in the order of the original sequence.
     */

    public static Sequence lowest(XPathContext context, SequenceIterator nsv) throws XPathException {
        double min = Double.POSITIVE_INFINITY;
        StringToDouble converter = context.getConfiguration().getConversionRules().getStringToDoubleConverter();
        try {
            List<Item> lowest = new ArrayList<>();
            while (true) {
                Item it = nsv.next();
                if (it == null) {
                    break;
                }
                double x = converter.stringToNumber(it.getStringValueCS());
                if (Double.isNaN(x)) {
                    return EmptySequence.getInstance();
                }
                if (x == min) {
                    lowest.add(it);
                } else if (x < min) {
                    min = x;
                    lowest.clear();
                    lowest.add(it);
                }
            }
            return new SequenceExtent(lowest);
        } catch (NumberFormatException e) {
            return EmptySequence.getInstance();
        }
    }

    /**
     * Get the absolute value of a numeric value (SStL)
     */

    public static double abs(double x) {
        return java.lang.Math.abs(x);
    }

    /**
     * Get the square root of a numeric value (SStL)
     */

    public static double sqrt(double x) {
        return java.lang.Math.sqrt(x);
    }

    /**
     * Get the power of two double or float values, returning the result as a double
     */

//    public static double power (double x, double y) {   // (SStL)
//        return java.lang.Math.pow(x,y);
//    }

    /**
     * Get a number n raised to the power of another number e.
     * <p>If e is a non-negative integer, then the result will have the same type as n, except
     * that a float is always promoted to double.</p>
     * <p>If e is a negative integer, then the result will have the same type as n except that a float
     * is treated as a double and an integer is treated as a decimal.</p>
     * <p>If e is not an integer (or an xs:decimal representing an integer),
     * the result will be a double.</p>
     *
     * @param n the first argument.
     * @param e the second argument. M
     * @return the result of n^e
     * @throws XPathException if an arithmetic overflow is detected. However, there is no guarantee that
     *                        overflow will always be detected, it may (especially with double arithmetic) lead to wrong answers
     *                        being returned.
     */

    public static NumericValue power(NumericValue n, NumericValue e) throws XPathException {
        if (n instanceof DoubleValue || n instanceof FloatValue ||
                e instanceof DoubleValue || e instanceof FloatValue ||
                !e.isWholeNumber()) {
            return new DoubleValue(java.lang.Math.pow(n.getDoubleValue(), e.getDoubleValue()));
        } else if (e instanceof IntegerValue && n instanceof IntegerValue && e.signum() >= 0) {
            long le = e.longValue();
            if (le > Integer.MAX_VALUE) {
                throw new XPathException("exponent out of range");
            }
            return IntegerValue.makeIntegerValue(((IntegerValue) n).asBigInteger().pow((int) le));
        } else {
            BigDecimal nd = n.getDecimalValue();
            long le = e.longValue();
            if (le > Integer.MAX_VALUE || le < Integer.MIN_VALUE) {
                throw new XPathException("exponent out of range");
            }
            return new BigDecimalValue(nd.pow((int) le));
        }
    }

    /**
     * Get a named constant to a given precision  (SStL)
     */

    public static double constant(XPathContext context, /*@NotNull*/ String name, double precision) throws XPathException {
        //PI, E, SQRRT2, LN2, LN10, LOG2E, SQRT1_2

        String con = "";

        if (name.equals("PI")) {
            con = "3.1415926535897932384626433832795028841971693993751";
        } else if (name.equals("E")) {
            con = "2.71828182845904523536028747135266249775724709369996";
        } else if (name.equals("SQRRT2")) {
            con = "1.41421356237309504880168872420969807856967187537694";
        } else if (name.equals("LN2")) {
            con = "0.69314718055994530941723212145817656807550013436025";
        } else if (name.equals("LN10")) {
            con = "2.302585092994046";
        } else if (name.equals("LOG2E")) {
            con = "1.4426950408889633";
        } else if (name.equals("SQRT1_2")) {
            con = "0.7071067811865476";
        } else {
            XPathException e = new XPathException("Unknown math constant " + name);
            e.setXPathContext(context);
            throw e;
        }

        return Double.parseDouble(con.substring(0, ((int) precision) + 2));
    }

    /**
     * Get the logarithm of a numeric value (SStL)
     */

    public static double log(double x) {
        return java.lang.Math.log(x);
    }

    /**
     * Get a random numeric value (SStL)
     */

    public static double random() {
        return java.lang.Math.random();
    }

    /**
     * Get the sine of a numeric value (SStL)
     */

    public static double sin(double x) {
        return java.lang.Math.sin(x);
    }

    /**
     * Get the cosine of a numeric value (SStL)
     */

    public static double cos(double x) {
        return java.lang.Math.cos(x);
    }

    /**
     * Get the tangent of a numeric value  (SStL)
     */

    public static double tan(double x) {
        return java.lang.Math.tan(x);
    }

    /**
     * Get the arcsine of a numeric value  (SStL)
     */

    public static double asin(double x) {
        return java.lang.Math.asin(x);
    }

    /**
     * Get the arccosine of a numeric value  (SStL)
     */

    public static double acos(double x) {
        return java.lang.Math.acos(x);
    }

    /**
     * Get the arctangent of a numeric value  (SStL)
     */

    public static double atan(double x) {
        return java.lang.Math.atan(x);
    }

    /**
     * Converts rectangular coordinates to polar  (SStL)
     */

    public static double atan2(double x, double y) {
        return java.lang.Math.atan2(x, y);
    }

    /**
     * Get the exponential of a numeric value  (SStL)
     */

    public static double exp(double x) {
        return java.lang.Math.exp(x);
    }

}

