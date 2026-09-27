////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.str.TwineBuilder;
import net.sf.saxon.str.UnicodeString;

import java.math.BigInteger;

/**
 * This is a utility class that handles formatting of numbers as strings.
 * <p>The algorithm for converting a floating point number to a string is taken from Guy L. Steele and
 * Jon L. White, <i>How to Print Floating-Point Numbers Accurately</i>, ACM SIGPLAN 1990. It is algorithm
 * (FPP)<sup>2</sup> from that paper. There are three separate implementations of the algorithm:</p>
 * <ul>
 * <li>One using long arithmetic and generating non-exponential output representations</li>
 * <li>One using BigInteger arithmetic and generating non-exponential output representation</li>
 * <li>One using BigInteger arithmetic and generating exponential output representations</li>
 * </ul>
 * <p>The choice of method depends on the value of the number being formatted.</p>
 * <p>The module contains some residual code (mainly the routine for formatting integers) from the class
 * AppenderHelper by Jack Shirazi in the O'Reilly book <i>Java Performance Tuning</i>. The floating point routines
 * in that module were found to be unsuitable, since they used floating point arithmetic which introduces
 * rounding errors.</p>
 * <p>There are several reasons for doing this conversion within Saxon, rather than leaving it all to Java.
 * Firstly, there are differences in the required output format, notably the absence of ".0" when formatting
 * whole numbers, and the different rules for the range of numbers where exponential notation is used.
 * Secondly, there are bugs in some Java implementations, for example JDK outputs 0.001 as 0.0010, and
 * IKVM/GNU gets things very wrong sometimes. Finally, this implementation is faster for "everyday" numbers,
 * though it is slower for more extreme numbers. It would probably be reasonable to hand over formatting
 * to the Java platform (at least when running the Sun JDK) for exponents outside the range -7 to +7.</p>
 */

public class FloatingPointConverter {

    public static FloatingPointConverter THE_INSTANCE = new FloatingPointConverter();

    private FloatingPointConverter() {
    }

    /**
     * char array holding the characters for the string "-Infinity".
     */
    private static final String NEGATIVE_INFINITY = "-INF";
    /**
     * char array holding the characters for the string "Infinity".
     */
    private static final String POSITIVE_INFINITY = "INF";
    /**
     * char array holding the characters for the string "NaN".
     */
    private static final String NaN = "NaN";

    private static final char[] charForDigit = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
    };

    public static final long DOUBLE_SIGN_MASK = 0x8000000000000000L;
    private static final long doubleExpMask = 0x7ff0000000000000L;
    private static final int doubleExpShift = 52;
    private static final int doubleExpBias = 1023;
    private static final long doubleFractMask = 0xfffffffffffffL;
    public static final int FLOAT_SIGN_MASK = 0x80000000;
    private static final int floatExpMask = 0x7f800000;
    private static final int floatExpShift = 23;
    private static final int floatExpBias = 127;
    private static final int floatFractMask = 0x7fffff;

    private static final BigInteger TEN = BigInteger.valueOf(10);
    private static final BigInteger NINE = BigInteger.valueOf(9);

    /**
     * Format an integer, appending the string representation of the integer to a string buffer
     *
     * @param tb the string buffer
     * @param i the integer to be formatted
     * @return the buffer to be used for subsequent operations
     */

    public static TwineBuilder appendInt(TwineBuilder tb, int i) {
        // TODO: this elaborate machinery is only being used to output the exponent of a floating point number,
        //  which never has more than 3 digits...
        if (i < 0) {
            if (i == Integer.MIN_VALUE) {
                //cannot make this positive due to integer overflow
                return tb.append("-2147483648");
            }
            tb = tb.append('-');
            i = -i;
        }
        int c;
        if (i < 10) {
            //one digit
            return tb.append(charForDigit[i]);
        } else if (i < 100) {
            //two digits
            return tb.append(charForDigit[i / 10])
                    .append(charForDigit[i % 10]);
        } else if (i < 1000) {
            //three digits
            return tb.append(charForDigit[i / 100])
                    .append(charForDigit[(c = i % 100) / 10])
                    .append(charForDigit[c % 10]);
        } else if (i < 10000) {
            //four digits
            return tb.append(charForDigit[i / 1000])
                    .append(charForDigit[(c = i % 1000) / 100])
                    .append(charForDigit[(c %= 100) / 10])
                    .append(charForDigit[c % 10]);
        } else if (i < 100000) {
            //five digits
            return tb.append(charForDigit[i / 10000])
                    .append(charForDigit[(c = i % 10000) / 1000])
                    .append(charForDigit[(c %= 1000) / 100])
                    .append(charForDigit[(c %= 100) / 10])
                    .append(charForDigit[c % 10]);
        } else if (i < 1000000) {
            //six digits
            return tb.append(charForDigit[i / 100000])
                    .append(charForDigit[(c = i % 100000) / 10000])
                    .append(charForDigit[(c %= 10000) / 1000])
                    .append(charForDigit[(c %= 1000) / 100])
                    .append(charForDigit[(c %= 100) / 10])
                    .append(charForDigit[c % 10]);
        } else if (i < 10000000) {
            //seven digits
            return tb.append(charForDigit[i / 1000000])
                    .append(charForDigit[(c = i % 1000000) / 100000])
                    .append(charForDigit[(c %= 100000) / 10000])
                    .append(charForDigit[(c %= 10000) / 1000])
                    .append(charForDigit[(c %= 1000) / 100])
                    .append(charForDigit[(c %= 100) / 10])
                    .append(charForDigit[c % 10]);
        } else if (i < 100000000) {
            //eight digits
            return tb.append(charForDigit[i / 10000000])
                    .append(charForDigit[(c = i % 10000000) / 1000000])
                    .append(charForDigit[(c %= 1000000) / 100000])
                    .append(charForDigit[(c %= 100000) / 10000])
                    .append(charForDigit[(c %= 10000) / 1000])
                    .append(charForDigit[(c %= 1000) / 100])
                    .append(charForDigit[(c %= 100) / 10])
                    .append(charForDigit[c % 10]);
        } else if (i < 1000000000) {
            //nine digits
            return tb.append(charForDigit[i / 100000000])
                    .append(charForDigit[(c = i % 100000000) / 10000000])
                    .append(charForDigit[(c %= 10000000) / 1000000])
                    .append(charForDigit[(c %= 1000000) / 100000])
                    .append(charForDigit[(c %= 100000) / 10000])
                    .append(charForDigit[(c %= 10000) / 1000])
                    .append(charForDigit[(c %= 1000) / 100])
                    .append(charForDigit[(c %= 100) / 10])
                    .append(charForDigit[c % 10]);
        } else {
            //ten digits
            return tb.append(charForDigit[i / 1000000000])
                    .append(charForDigit[(c = i % 1000000000) / 100000000])
                    .append(charForDigit[(c %= 100000000) / 10000000])
                    .append(charForDigit[(c %= 10000000) / 1000000])
                    .append(charForDigit[(c %= 1000000) / 100000])
                    .append(charForDigit[(c %= 100000) / 10000])
                    .append(charForDigit[(c %= 10000) / 1000])
                    .append(charForDigit[(c %= 1000) / 100])
                    .append(charForDigit[(c %= 100) / 10])
                    .append(charForDigit[c % 10]);
        }
    }

    /**
     * Implementation of the (FPP)2 algorithm from Steele and White, for doubles in the range
     * 0.01 to 1000000, and floats in the range 0.000001 to 1000000.
     * In this range (a) XPath requires that the output should not be in exponential
     * notation, and (b) the arithmetic can be handled using longs rather than BigIntegers
     *
     * @param tb the string buffer to which the formatted result is to be appended
     * @param e  the exponent of the floating point number
     * @param f  the fraction part of the floating point number, such that the "real" value of the
     *           number is f * 2^(e-p), with p&gt;=0 and 0 lt f lt 2^p
     * @param p  the precision
     * @return the buffer to be used for subsequent operations
     */

    private static TwineBuilder fppfpp(TwineBuilder tb, int e, long f, int p) {
        long R = f << Math.max(e - p, 0);
        long S = 1L << Math.max(0, -(e - p));
        long Mminus = 1L << Math.max(e - p, 0);
        long Mplus = Mminus;
        boolean initial = true;

        // simpleFixup

        if (f == 1L << (p - 1)) {
            Mplus = Mplus << 1;
            R = R << 1;
            S = S << 1;
        }
        int k = 0;
        while (R < (S + 9) / 10) {  // (S+9)/10 == ceiling(S/10)
            k--;
            R = R * 10;
            Mminus = Mminus * 10;
            Mplus = Mplus * 10;
        }
        while (2 * R + Mplus >= 2 * S) {
            S = S * 10;
            k++;
        }

        for (int z = k; z < 0; z++) {
            if (initial) {
                tb = tb.append('0').append('.');
            }
            initial = false;
            tb = tb.append('0');
        }

        // end simpleFixup

        //int H = k-1;

        boolean low;
        boolean high;
        int U;
        while (true) {
            k--;
            long R10 = R * 10;
            U = (int) (R10 / S);
            R = R10 - (U * S);    // = R*10 % S, but faster - saves a division
            Mminus = Mminus * 10;
            Mplus = Mplus * 10;
            low = 2 * R < Mminus;
            high = 2 * R > 2 * S - Mplus;
            if (low || high) break;
            if (k == -1) {
                if (initial) {
                    tb = tb.append('0');
                }
                tb = tb.append('.');
            }
            tb = tb.append(charForDigit[U]);
            initial = false;
        }
        if (high && (!low || 2 * R > S)) {
            U++;
        }
        if (k == -1) {
            if (initial) {
                tb = tb.append('0');
            }
            tb = tb.append('.');
        }
        tb = tb.append(charForDigit[U]);
        for (int z = 0; z < k; z++) {
            tb = tb.append('0');
        }
        return tb;
    }

    /**
     * Implementation of the (FPP)2 algorithm from Steele and White, for doubles in the range
     * 0.000001 to 0.01. In this range XPath requires that the output should not be in exponential
     * notation, but the scale factors are large enough to exceed the capacity of long arithmetic.
     *
     * @param tb the string buffer to which the formatted result is to be appended
     * @param e  the exponent of the floating point number
     * @param f  the fraction part of the floating point number, such that the "real" value of the
     *           number is f * 2^(e-p), with p&gt;=0 and 0 lt f lt 2^p
     * @param p  the precision
     * @return the buffer to be used for subsequent operations
     */

    private static TwineBuilder fppfppBig(TwineBuilder tb, int e, long f, int p) {
        //long R = f << Math.max(e-p, 0);
        BigInteger R = BigInteger.valueOf(f).shiftLeft(Math.max(e - p, 0));

        //long S = 1L << Math.max(0, -(e-p));
        BigInteger S = BigInteger.ONE.shiftLeft(Math.max(0, -(e - p)));

        //long Mminus = 1 << Math.max(e-p, 0);
        BigInteger Mminus = BigInteger.ONE.shiftLeft(Math.max(e - p, 0));

        //long Mplus = Mminus;
        BigInteger Mplus = Mminus;

        boolean initial = true;

        // simpleFixup

        if (f == 1L << (p - 1)) {
            Mplus = Mplus.shiftLeft(1);
            R = R.shiftLeft(1);
            S = S.shiftLeft(1);
        }
        int k = 0;
        while (R.compareTo(S.add(NINE).divide(TEN)) < 0) {  // (S+9)/10 == ceiling(S/10)
            k--;
            R = R.multiply(TEN);
            Mminus = Mminus.multiply(TEN);
            Mplus = Mplus.multiply(TEN);
        }
        while (R.shiftLeft(1).add(Mplus).compareTo(S.shiftLeft(1)) >= 0) {
            S = S.multiply(TEN);
            k++;
        }

        for (int z = k; z < 0; z++) {
            if (initial) {
                tb = tb.append('0').append('.');
            }
            initial = false;
            tb = tb.append('0');
        }

        // end simpleFixup

        //int H = k-1;

        boolean low;
        boolean high;
        int U;
        while (true) {
            k--;
            BigInteger R10 = R.multiply(TEN);
            U = R10.divide(S).intValue();
            R = R10.mod(S);
            Mminus = Mminus.multiply(TEN);
            Mplus = Mplus.multiply(TEN);
            BigInteger R2 = R.shiftLeft(1);
            low = R2.compareTo(Mminus) < 0;
            high = R2.compareTo(S.shiftLeft(1).subtract(Mplus)) > 0;
            if (low || high) break;
            if (k == -1) {
                if (initial) {
                    tb = tb.append('0');
                }
                tb = tb.append('.');
            }
            tb = tb.append(charForDigit[U]);
            initial = false;
        }
        if (high && (!low || R.shiftLeft(1).compareTo(S) > 0)) {
            U++;
        }
        if (k == -1) {
            if (initial) {
                tb = tb.append('0');
            }
            tb = tb.append('.');
        }
        tb = tb.append(charForDigit[U]);
        for (int z = 0; z < k; z++) {
            tb = tb.append('0');
        }
        return tb;
    }


    /**
     * Implementation of the (FPP)2 algorithm from Steele and White, for numbers outside the range
     * 0.000001 to 1000000. In this range XPath requires that the output should be in exponential
     * notation
     *
     * @param tb the string buffer to which the formatted result is to be appended
     * @param e  the exponent of the floating point number
     * @param f  the fraction part of the floating point number, such that the "real" value of the
     *           number is f * 2^(e-p), with p&gt;=0 and 0 lt f lt 2^p
     * @param p  the precision
     */

    private static TwineBuilder fppfppExponential(TwineBuilder tb, int e, long f, int p) {
        //long R = f << Math.max(e-p, 0);
        BigInteger R = BigInteger.valueOf(f).shiftLeft(Math.max(e - p, 0));

        //long S = 1L << Math.max(0, -(e-p));
        BigInteger S = BigInteger.ONE.shiftLeft(Math.max(0, -(e - p)));

        //long Mminus = 1 << Math.max(e-p, 0);
        BigInteger Mminus = BigInteger.ONE.shiftLeft(Math.max(e - p, 0));

        //long Mplus = Mminus;
        BigInteger Mplus = Mminus;

        boolean initial = true;
        boolean doneDot = false;

        // simpleFixup

        if (f == 1L << (p - 1)) {
            Mplus = Mplus.shiftLeft(1);
            R = R.shiftLeft(1);
            S = S.shiftLeft(1);
        }
        int k = 0;
        while (R.compareTo(S.add(NINE).divide(TEN)) < 0) {  // (S+9)/10 == ceiling(S/10)
            k--;
            R = R.multiply(TEN);
            Mminus = Mminus.multiply(TEN);
            Mplus = Mplus.multiply(TEN);
        }
        while (R.shiftLeft(1).add(Mplus).compareTo(S.shiftLeft(1)) >= 0) {
            S = S.multiply(TEN);
            k++;
        }

        // end simpleFixup

        int H = k - 1;

        boolean low;
        boolean high;
        int U;
        while (true) {
            k--;
            BigInteger R10 = R.multiply(TEN);
            U = R10.divide(S).intValue();
            R = R10.mod(S);
            Mminus = Mminus.multiply(TEN);
            Mplus = Mplus.multiply(TEN);
            BigInteger R2 = R.shiftLeft(1);
            low = R2.compareTo(Mminus) < 0;
            high = R2.compareTo(S.shiftLeft(1).subtract(Mplus)) > 0;
            if (low || high) break;

            tb = tb.append(charForDigit[U]);
            if (initial) {
                tb = tb.append('.');
                doneDot = true;
            }
            initial = false;
        }
        if (high && (!low || R.shiftLeft(1).compareTo(S) > 0)) {
            U++;
        }
        tb = tb.append(charForDigit[U]);

        if (!doneDot) {
            tb = tb.append(".0");
        }
        tb = tb.append('E');
        tb = appendInt(tb, H);
        return tb;

    }

    /**
     * Append a string representation of a double value to a string buffer
     *
     * @param d                the double to be formatted
     * @param useExponential   forces exponential notation if set (if not set, exponential notation
     *                         is used only for values outside the range 1e-6 to 1e+6)
     * @return the original string buffer, now containing the string representation of the supplied double
     */

    //@CSharpReplaceBody(code = "return net.sf.saxon.str.BMPString.of(d.ToString(System.Globalization.CultureInfo.InvariantCulture));") // for now...
    public static UnicodeString convertDouble(double d, boolean useExponential) {
        TwineBuilder tb = TwineBuilder.make(32);
        if (d == Double.NEGATIVE_INFINITY) {
            tb = tb.append(NEGATIVE_INFINITY);
        } else if (d == Double.POSITIVE_INFINITY) {
            tb = tb.append(POSITIVE_INFINITY);
        } else if (Double.isNaN(d)) {
            tb = tb.append(NaN);
        } else if (d == 0.0) {
            if ((Double.doubleToLongBits(d) & DOUBLE_SIGN_MASK) != 0) {
                tb = tb.append('-');
            }
            tb = tb.append('0');
            if (useExponential) {
                tb = tb.append(".0E0");
            }
        } else if (d == Double.MAX_VALUE) {
            tb = tb.append("1.7976931348623157E308");
        } else if (d == -Double.MAX_VALUE) {
            tb = tb.append("-1.7976931348623157E308");
        } else if (d == Double.MIN_VALUE) {
            tb = tb.append("4.9E-324");
        } else if (d == -Double.MIN_VALUE) {
            tb = tb.append("-4.9E-324");
        } else {
            if (d < 0) {
                tb = tb.append('-');
                d = -d;
            }
            long bits = Double.doubleToLongBits(d);
            long fraction = (1L << 52) | (bits & doubleFractMask);
            long rawExp = (bits & doubleExpMask) >> doubleExpShift;
            int exp = (int) rawExp - doubleExpBias;
            if (rawExp == 0) {
                // don't know how to handle this currently: hand it over to Java to deal with
                tb = tb.append(Double.toString(d));
                return tb.toUnicodeString();
            }
            if (useExponential) {
                tb = fppfppExponential(tb, exp, fraction, 52);
            } else {
                if (d <= 0.01) {
                    tb = fppfppBig(tb, exp, fraction, 52);
                } else {
                    tb = fppfpp(tb, exp, fraction, 52);
                }
            }
        }
        return tb.toUnicodeString();
    }

    /**
     * Append a string representation of a float value to a string buffer
     *
     * @param f                the float to be formatted
     * @param forceExponential forces exponential notation if set (if not set, exponential notation
     *                         is used only for values outside the range 1e-6 to 1e+6)
     * @return the string representation of the supplied float
     */

    /*@NotNull*/
    //@CSharpReplaceBody(code="return s.append(f.ToString(System.Globalization.CultureInfo.InvariantCulture)).toUnicodeString();")  // for now...
    public static UnicodeString appendFloat(float f, boolean forceExponential) {
        TwineBuilder tb = TwineBuilder.make(16);
        if (f == Float.NEGATIVE_INFINITY) {
            tb = tb.append(NEGATIVE_INFINITY);
        } else if (f == Float.POSITIVE_INFINITY) {
            tb = tb.append(POSITIVE_INFINITY);
        } else if (Float.isNaN(f)) {
            tb = tb.append(NaN);
        } else if (f == 0.0) {
            if ((Float.floatToIntBits(f) & FLOAT_SIGN_MASK) != 0) {
                tb = tb.append('-');
            }
            tb = tb.append('0');
        } else if (f == Float.MAX_VALUE) {
            tb = tb.append("3.4028235E38");
        } else if (f == -Float.MAX_VALUE) {
            tb = tb.append("-3.4028235E38");
        } else if (f == Float.MIN_VALUE) {
            tb = tb.append("1.4E-45");
        } else if (f == -Float.MIN_VALUE) {
            tb = tb.append("-1.4E-45");
        } else {
            if (f < 0) {
                tb = tb.append('-');
                f = -f;
            }
            int bits = Float.floatToIntBits(f);
            int fraction = (1 << 23) | (bits & floatFractMask);
            int rawExp = ((bits & floatExpMask) >> floatExpShift);
            int exp = rawExp - floatExpBias;
            int precision = 23;
            if (rawExp == 0) {
                // don't know how to handle this currently: hand it over to Java to deal with
                tb = tb.append(Float.toString(f));
                return tb.toUnicodeString();
            }
            if (forceExponential || (f >= 1000000 || f < 0.000001F)) {
                tb = fppfppExponential(tb, exp, fraction, precision);
            } else {
                tb = fppfpp(tb, exp, fraction, precision);
            }
        }
        return tb.toUnicodeString();
    }


//    public static void main(String[] args) {
//        if (args.length > 0 && args[0].equals("F")) {
//            if (args.length == 2) {
//                StringTokenizer tok = new StringTokenizer(args[1], ",");
//                while (tok.hasMoreElements()) {
//                    String input = tok.nextToken();
//                    float f = Float.parseFloat(input);
//                    StringBuilder sb = new StringBuilder(20);
//                    appendFloat(sb, f);
//                    System.err.println("input: " + input + " output: " + sb.toString() + " java: " + f);
//                }
//            } else {
//                Random gen = new Random();
//                for (int i=1; i<1000; i++) {
//                    int p=gen.nextInt(999*i*i);
//                    int q=gen.nextInt(999*i*i);
//                    String input = (p + "." + q);
//                    float f = Float.parseFloat(input);
//                    StringBuilder sb = new StringBuilder(20);
//                    appendFloat(sb, f);
//                    System.err.println("input: " + input + " output: " + sb.toString() + " java: " + f);
//                }
//            }
//        } else {
//            if (args.length == 2) {
//                StringTokenizer tok = new StringTokenizer(args[1], ",");
//                while (tok.hasMoreElements()) {
//                    String input = tok.nextToken();
//                    double f = Double.parseDouble(input);
//                    StringBuilder sb = new StringBuilder(20);
//                    appendDouble(sb, f);
//                    System.err.println("input: " + input + " output: " + sb.toString() + " java: " + f);
//                }
//            } else {
//                long start = System.currentTimeMillis();
//                Random gen = new Random();
//                for (int i=1; i<100000; i++) {
//                    //int p=gen.nextInt(999*i*i);
//                    int q=gen.nextInt(999*i);
//                    //String input = (p + "." + q);
//                    String input = "0.000" + q;
//                    double f = Double.parseDouble(input);
//                    StringBuilder sb = new StringBuilder(20);
//                    appendDouble(sb, f);
//                    //System.err.println("input: " + input + " output: " + sb.toString() + " java: " + f);
//                }
//                System.err.println("** elapsed time " + (System.currentTimeMillis() - start));
//            }
//        }
//    }


}


