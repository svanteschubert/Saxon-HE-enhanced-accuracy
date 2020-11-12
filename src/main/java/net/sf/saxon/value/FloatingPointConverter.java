////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.tree.util.FastStringBuffer;

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
    private static final char[] NEGATIVE_INFINITY = {'-', 'I', 'N', 'F'};
    /**
     * char array holding the characters for the string "Infinity".
     */
    private static final char[] POSITIVE_INFINITY = {'I', 'N', 'F'};
    /**
     * char array holding the characters for the string "NaN".
     */
    private static final char[] NaN = {'N', 'a', 'N'};

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
     * @param s the string buffer
     * @param i the integer to be formatted
     * @return the supplied string buffer, containing the appended integer
     */

    public static FastStringBuffer appendInt(FastStringBuffer s, int i) {
        if (i < 0) {
            if (i == Integer.MIN_VALUE) {
                //cannot make this positive due to integer overflow
                s.append("-2147483648");
                return s;
            }
            s.cat('-');
            i = -i;
        }
        int c;
        if (i < 10) {
            //one digit
            s.cat(charForDigit[i]);
            return s;
        } else if (i < 100) {
            //two digits
            s.cat(charForDigit[i / 10]);
            s.cat(charForDigit[i % 10]);
            return s;
        } else if (i < 1000) {
            //three digits
            s.cat(charForDigit[i / 100]);
            s.cat(charForDigit[(c = i % 100) / 10]);
            s.cat(charForDigit[c % 10]);
            return s;
        } else if (i < 10000) {
            //four digits
            s.cat(charForDigit[i / 1000]);
            s.cat(charForDigit[(c = i % 1000) / 100]);
            s.cat(charForDigit[(c %= 100) / 10]);
            s.cat(charForDigit[c % 10]);
            return s;
        } else if (i < 100000) {
            //five digits
            s.cat(charForDigit[i / 10000]);
            s.cat(charForDigit[(c = i % 10000) / 1000]);
            s.cat(charForDigit[(c %= 1000) / 100]);
            s.cat(charForDigit[(c %= 100) / 10]);
            s.cat(charForDigit[c % 10]);
            return s;
        } else if (i < 1000000) {
            //six digits
            s.cat(charForDigit[i / 100000]);
            s.cat(charForDigit[(c = i % 100000) / 10000]);
            s.cat(charForDigit[(c %= 10000) / 1000]);
            s.cat(charForDigit[(c %= 1000) / 100]);
            s.cat(charForDigit[(c %= 100) / 10]);
            s.cat(charForDigit[c % 10]);
            return s;
        } else if (i < 10000000) {
            //seven digits
            s.cat(charForDigit[i / 1000000]);
            s.cat(charForDigit[(c = i % 1000000) / 100000]);
            s.cat(charForDigit[(c %= 100000) / 10000]);
            s.cat(charForDigit[(c %= 10000) / 1000]);
            s.cat(charForDigit[(c %= 1000) / 100]);
            s.cat(charForDigit[(c %= 100) / 10]);
            s.cat(charForDigit[c % 10]);
            return s;
        } else if (i < 100000000) {
            //eight digits
            s.cat(charForDigit[i / 10000000]);
            s.cat(charForDigit[(c = i % 10000000) / 1000000]);
            s.cat(charForDigit[(c %= 1000000) / 100000]);
            s.cat(charForDigit[(c %= 100000) / 10000]);
            s.cat(charForDigit[(c %= 10000) / 1000]);
            s.cat(charForDigit[(c %= 1000) / 100]);
            s.cat(charForDigit[(c %= 100) / 10]);
            s.cat(charForDigit[c % 10]);
            return s;
        } else if (i < 1000000000) {
            //nine digits
            s.cat(charForDigit[i / 100000000]);
            s.cat(charForDigit[(c = i % 100000000) / 10000000]);
            s.cat(charForDigit[(c %= 10000000) / 1000000]);
            s.cat(charForDigit[(c %= 1000000) / 100000]);
            s.cat(charForDigit[(c %= 100000) / 10000]);
            s.cat(charForDigit[(c %= 10000) / 1000]);
            s.cat(charForDigit[(c %= 1000) / 100]);
            s.cat(charForDigit[(c %= 100) / 10]);
            s.cat(charForDigit[c % 10]);
            return s;
        } else {
            //ten digits
            s.cat(charForDigit[i / 1000000000]);
            s.cat(charForDigit[(c = i % 1000000000) / 100000000]);
            s.cat(charForDigit[(c %= 100000000) / 10000000]);
            s.cat(charForDigit[(c %= 10000000) / 1000000]);
            s.cat(charForDigit[(c %= 1000000) / 100000]);
            s.cat(charForDigit[(c %= 100000) / 10000]);
            s.cat(charForDigit[(c %= 10000) / 1000]);
            s.cat(charForDigit[(c %= 1000) / 100]);
            s.cat(charForDigit[(c %= 100) / 10]);
            s.cat(charForDigit[c % 10]);
            return s;
        }
    }

    /**
     * Implementation of the (FPP)2 algorithm from Steele and White, for doubles in the range
     * 0.01 to 1000000, and floats in the range 0.000001 to 1000000.
     * In this range (a) XPath requires that the output should not be in exponential
     * notation, and (b) the arithmetic can be handled using longs rather than BigIntegers
     *
     * @param sb the string buffer to which the formatted result is to be appended
     * @param e  the exponent of the floating point number
     * @param f  the fraction part of the floating point number, such that the "real" value of the
     *           number is f * 2^(e-p), with p&gt;=0 and 0 lt f lt 2^p
     * @param p  the precision
     */

    private static void fppfpp(FastStringBuffer sb, int e, long f, int p) {
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
                sb.append("0.");
            }
            initial = false;
            sb.cat('0');
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
                    sb.cat('0');
                }
                sb.cat('.');
            }
            sb.cat(charForDigit[U]);
            initial = false;
        }
        if (high && (!low || 2 * R > S)) {
            U++;
        }
        if (k == -1) {
            if (initial) {
                sb.cat('0');
            }
            sb.cat('.');
        }
        sb.cat(charForDigit[U]);
        for (int z = 0; z < k; z++) {
            sb.cat('0');
        }
    }

    /**
     * Implementation of the (FPP)2 algorithm from Steele and White, for doubles in the range
     * 0.000001 to 0.01. In this range XPath requires that the output should not be in exponential
     * notation, but the scale factors are large enough to exceed the capacity of long arithmetic.
     *
     * @param sb the string buffer to which the formatted result is to be appended
     * @param e  the exponent of the floating point number
     * @param f  the fraction part of the floating point number, such that the "real" value of the
     *           number is f * 2^(e-p), with p&gt;=0 and 0 lt f lt 2^p
     * @param p  the precision
     */

    private static void fppfppBig(FastStringBuffer sb, int e, long f, int p) {
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
                sb.append("0.");
            }
            initial = false;
            sb.cat('0');
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
                    sb.cat('0');
                }
                sb.cat('.');
            }
            sb.cat(charForDigit[U]);
            initial = false;
        }
        if (high && (!low || R.shiftLeft(1).compareTo(S) > 0)) {
            U++;
        }
        if (k == -1) {
            if (initial) {
                sb.cat('0');
            }
            sb.cat('.');
        }
        sb.cat(charForDigit[U]);
        for (int z = 0; z < k; z++) {
            sb.cat('0');
        }
    }


    /**
     * Implementation of the (FPP)2 algorithm from Steele and White, for numbers outside the range
     * 0.000001 to 1000000. In this range XPath requires that the output should be in exponential
     * notation
     *
     * @param sb the string buffer to which the formatted result is to be appended
     * @param e  the exponent of the floating point number
     * @param f  the fraction part of the floating point number, such that the "real" value of the
     *           number is f * 2^(e-p), with p&gt;=0 and 0 lt f lt 2^p
     * @param p  the precision
     */

    private static void fppfppExponential(FastStringBuffer sb, int e, long f, int p) {
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

            sb.cat(charForDigit[U]);
            if (initial) {
                sb.cat('.');
                doneDot = true;
            }
            initial = false;
        }
        if (high && (!low || R.shiftLeft(1).compareTo(S) > 0)) {
            U++;
        }
        sb.cat(charForDigit[U]);

        if (!doneDot) {
            sb.append(".0");
        }
        sb.cat('E');
        appendInt(sb, H);

    }

    /**
     * Append a string representation of a double value to a string buffer
     *
     * @param s the string buffer to which the result will be appended
     * @param d the double to be formatted
     * @return the original string buffer, now containing the string representation of the supplied double
     */

    public static FastStringBuffer appendDouble(FastStringBuffer s, double d, boolean forceExponential) {
        if (d == Double.NEGATIVE_INFINITY) {
            s.append(NEGATIVE_INFINITY);
        } else if (d == Double.POSITIVE_INFINITY) {
            s.append(POSITIVE_INFINITY);
        } else if (d != d) {
            s.append(NaN);
        } else if (d == 0.0) {
            if ((Double.doubleToLongBits(d) & DOUBLE_SIGN_MASK) != 0) {
                s.cat('-');
            }
            s.cat('0');
            if (forceExponential) {
                s.append(".0E0");
            }
        } else if (d == Double.MAX_VALUE) {
            s.append("1.7976931348623157E308");
        } else if (d == -Double.MAX_VALUE) {
            s.append("-1.7976931348623157E308");
        } else if (d == Double.MIN_VALUE) {
            s.append("4.9E-324");
        } else if (d == -Double.MIN_VALUE) {
            s.append("-4.9E-324");
        } else {
            if (d < 0) {
                s.cat('-');
                d = -d;
            }
            long bits = Double.doubleToLongBits(d);
            long fraction = (1L << 52) | (bits & doubleFractMask);
            long rawExp = (bits & doubleExpMask) >> doubleExpShift;
            int exp = (int) rawExp - doubleExpBias;
            if (rawExp == 0) {
                // don't know how to handle this currently: hand it over to Java to deal with
                s.append(Double.toString(d));
                return s;
            }
            if (forceExponential || (d >= 1000000 || d < 0.000001)) {
                fppfppExponential(s, exp, fraction, 52);
            } else {
                if (d <= 0.01) {
                    fppfppBig(s, exp, fraction, 52);
                } else {
                    fppfpp(s, exp, fraction, 52);
                }
            }

            // test code
//            try {
//                if (Double.parseDouble(s.toString()) != value) {
//                    System.err.println("*** Round-trip failed: input " + value +
//                            '(' + Double.doubleToLongBits(value) + ')' +
//                            " != output " + s.toString() +
//                            '(' + Double.doubleToLongBits(Double.parseDouble(s.toString())) + ')' );
//                }
//            } catch (NumberFormatException e) {
//                System.err.println("*** Bad float " + s.toString() + " for input " + value);
//            }
        }
        return s;
    }

    /**
     * Append a string representation of a float value to a string buffer
     *
     * @param s                the string buffer to which the result will be appended
     * @param f                the float to be formatted
     * @param forceExponential forces exponential notation if set (if not set, exponential notation
     *                         is used only for values outside the range 1e-6 to 1e+6)
     * @return the original string buffer, now containing the string representation of the supplied float
     */

    /*@NotNull*/
    public static FastStringBuffer appendFloat(FastStringBuffer s, float f, boolean forceExponential) {
        if (f == Float.NEGATIVE_INFINITY) {
            s.append(NEGATIVE_INFINITY);
        } else if (f == Float.POSITIVE_INFINITY) {
            s.append(POSITIVE_INFINITY);
        } else if (f != f) {
            s.append(NaN);
        } else if (f == 0.0) {
            if ((Float.floatToIntBits(f) & FLOAT_SIGN_MASK) != 0) {
                s.cat('-');
            }
            s.cat('0');
        } else if (f == Float.MAX_VALUE) {
            s.append("3.4028235E38");
        } else if (f == -Float.MAX_VALUE) {
            s.append("-3.4028235E38");
        } else if (f == Float.MIN_VALUE) {
            s.append("1.4E-45");
        } else if (f == -Float.MIN_VALUE) {
            s.append("-1.4E-45");
        } else {
            if (f < 0) {
                s.cat('-');
                f = -f;
            }
            int bits = Float.floatToIntBits(f);
            int fraction = (1 << 23) | (bits & floatFractMask);
            int rawExp = ((bits & floatExpMask) >> floatExpShift);
            int exp = rawExp - floatExpBias;
            int precision = 23;
            if (rawExp == 0) {
                // don't know how to handle this currently: hand it over to Java to deal with
                s.append(Float.toString(f));
                return s;
            }
            if (forceExponential || (f >= 1000000 || f < 0.000001F)) {
                fppfppExponential(s, exp, fraction, precision);
            } else {
                fppfpp(s, exp, fraction, precision);
            }
        }
        return s;
    }


//    public static void main(String[] args) {
//        if (args.length > 0 && args[0].equals("F")) {
//            if (args.length == 2) {
//                StringTokenizer tok = new StringTokenizer(args[1], ",");
//                while (tok.hasMoreElements()) {
//                    String input = tok.nextToken();
//                    float f = Float.parseFloat(input);
//                    FastStringBuffer sb = new FastStringBuffer(20);
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
//                    FastStringBuffer sb = new FastStringBuffer(20);
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
//                    FastStringBuffer sb = new FastStringBuffer(20);
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
//                    FastStringBuffer sb = new FastStringBuffer(20);
//                    appendDouble(sb, f);
//                    //System.err.println("input: " + input + " output: " + sb.toString() + " java: " + f);
//                }
//                System.err.println("** elapsed time " + (System.currentTimeMillis() - start));
//            }
//        }
//    }


}


