////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.DecimalSymbols;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of format-number() function. Note this has no dependency on number formatting in the JDK.
 */

public class FormatNumber extends SystemFunction implements Callable, StatefulSystemFunction {

    private StructuredQName decimalFormatName; // null for the default format
    private String picture;
    private DecimalSymbols decimalSymbols;
    private SubPicture[] subPictures;

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments. This handles
     * the case where the decimal format name is supplied as a literal (or defaulted), and the picture string is
     * also supplied as a literal.
     *
     * @param arguments the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws net.sf.saxon.trans.XPathException if an error is detected
     */
    @Override
    public Expression fixArguments(Expression... arguments) throws XPathException {

        if (arguments[1] instanceof Literal && (arguments.length == 2 || arguments[2] instanceof Literal)) {
            DecimalFormatManager dfm = getRetainedStaticContext().getDecimalFormatManager();
            assert dfm != null;
            picture = ((Literal) arguments[1]).getValue().getStringValue();
            if (arguments.length == 3 && !Literal.isEmptySequence(arguments[2])) {
                try {
                    String lexicalName = ((Literal) arguments[2]).getValue().getStringValue();
                    decimalFormatName = StructuredQName.fromLexicalQName(lexicalName, false,
                                                                         true, getRetainedStaticContext());
                } catch (XPathException e) {
                    XPathException err = new XPathException("Invalid decimal format name. " + e.getMessage());
                    err.setErrorCode("FODF1280");
                    throw err;
                }
            }
            if (decimalFormatName == null) {
                decimalSymbols = dfm.getDefaultDecimalFormat();
            } else {
                decimalSymbols = dfm.getNamedDecimalFormat(decimalFormatName);
                if (decimalSymbols == null) {
                    throw new XPathException(
                            "Decimal format " + decimalFormatName.getDisplayName() + " has not been defined", "FODF1280");
                }
            }
            subPictures = getSubPictures(picture, decimalSymbols);
        }
        return null;
    }

    /**
     * Analyze a picture string into two sub-pictures.
     *
     * @param picture the picture as written (possibly two subpictures separated by a semicolon)
     * @param dfs     the decimal format symbols
     * @return an array of two sub-pictures, the positive and the negative sub-pictures respectively.
     * If there is only one sub-picture, the second one is null.
     * @throws XPathException if the picture is invalid
     */

    private static SubPicture[] getSubPictures(String picture, DecimalSymbols dfs) throws XPathException {
        int[] picture4 = StringValue.expand(picture);
        SubPicture[] pics = new SubPicture[2];
        if (picture4.length == 0) {
            XPathException err = new XPathException("format-number() picture is zero-length");
            err.setErrorCode("FODF1310");
            throw err;
        }
        int sep = -1;
        for (int c = 0; c < picture4.length; c++) {
            if (picture4[c] == dfs.getPatternSeparator()) {
                if (c == 0) {
                    grumble("first subpicture is zero-length");
                } else if (sep >= 0) {
                    grumble("more than one pattern separator");
                } else if (sep == picture4.length - 1) {
                    grumble("second subpicture is zero-length");
                }
                sep = c;
            }
        }

        if (sep < 0) {
            pics[0] = new SubPicture(picture4, dfs);
            pics[1] = null;
        } else {
            int[] pic0 = new int[sep];
            System.arraycopy(picture4, 0, pic0, 0, sep);
            int[] pic1 = new int[picture4.length - sep - 1];
            System.arraycopy(picture4, sep + 1, pic1, 0, picture4.length - sep - 1);
            pics[0] = new SubPicture(pic0, dfs);
            pics[1] = new SubPicture(pic1, dfs);
        }
        return pics;
    }

    /**
     * Format a number, given the two subpictures and the decimal format symbols
     *
     * @param number      the number to be formatted
     * @param subPictures the negative and positive subPictures
     * @param dfs         the decimal format symbols to be used
     * @return the formatted number
     */

    private static CharSequence formatNumber(NumericValue number,
                                             SubPicture[] subPictures,
                                             DecimalSymbols dfs) {

        NumericValue absN = number;
        SubPicture pic;
        String minusSign = "";
        int signum = number.signum();
        if (signum == 0 && number.isNegativeZero()) {
            signum = -1;
        }
        if (signum < 0) {
            absN = number.negate();
            if (subPictures[1] == null) {
                pic = subPictures[0];
                minusSign = "" + unicodeChar(dfs.getMinusSign());
            } else {
                pic = subPictures[1];
            }
        } else {
            pic = subPictures[0];
        }

        return pic.format(absN, dfs, minusSign);
    }

    private static void grumble(String s) throws XPathException {
        throw new XPathException("format-number picture: " + s, "FODF1310");
    }

    /**
     * Convert a double to a BigDecimal. In general there will be several BigDecimal values that
     * are equal to the supplied value, and the one we want to choose is the one with fewest non-zero
     * digits. The algorithm used is rather pragmatic: look for a string of zeroes or nines, try rounding
     * the number down or up as appropriate, then convert the adjusted value to a double to see if it's
     * equal to the original: if not, use the original value unchanged.
     *
     * @param value     the double to be converted
     * @param precision 2 for a double, 1 for a float
     * @return the result of conversion to a double
     */

    public static BigDecimal adjustToDecimal(double value, int precision) {
        final String zeros = precision == 1 ? "00000" : "000000000";
        final String nines = precision == 1 ? "99999" : "999999999";
        BigDecimal initial = BigDecimal.valueOf(value);
        BigDecimal trial = null;
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C16);
        BigDecimalValue.decimalToString(initial, fsb);
        String s = fsb.toString();
        int start = s.charAt(0) == '-' ? 1 : 0;
        int p = s.indexOf(".");
        int i = s.lastIndexOf(zeros);
        if (i > 0) {
            if (p < 0 || i < p) {
                // we're in the integer part
                // try replacing all following digits with zeros and seeing if we get the same double back
                FastStringBuffer sb = new FastStringBuffer(s.length());
                sb.append(s.substring(0, i));
                for (int n = i; n < s.length(); n++) {
                    sb.cat(s.charAt(n) == '.' ? '.' : '0');
                }
                trial = new BigDecimal(sb.toString());
            } else {
                // we're in the fractional part
                // try truncating the number before the zeros and seeing if we get the same double back
                trial = new BigDecimal(s.substring(0, i));

            }
        } else {
            i = s.indexOf(nines);
            if (i >= 0) {
                if (i == start) {
                    // number starts with 99999... or -99999. Try rounding up to 100000.. or -100000...
                    FastStringBuffer sb = new FastStringBuffer(s.length() + 1);
                    if (start == 1) {
                        sb.cat('-');
                    }
                    sb.cat('1');
                    for (int n = start; n < s.length(); n++) {
                        sb.cat(s.charAt(n) == '.' ? '.' : '0');
                    }
                    trial = new BigDecimal(sb.toString());
                } else {
                    // try rounding up
                    while (i >= 0 && (s.charAt(i) == '9' || s.charAt(i) == '.')) {
                        i--;
                    }
                    if (i < 0 || s.charAt(i) == '-') {
                        return initial;     // can't happen: we've already handled numbers starting 99999..
                    } else if (p < 0 || i < p) {
                        // we're in the integer part
                        FastStringBuffer sb = new FastStringBuffer(s.length());
                        sb.append(s.substring(0, i));
                        sb.cat((char) ((int) s.charAt(i) + 1));
                        for (int n = i; n < s.length(); n++) {
                            sb.cat(s.charAt(n) == '.' ? '.' : '0');
                        }
                        trial = new BigDecimal(sb.toString());
                    } else {
                        // we're in the fractional part - can ignore following digits
                        String s2 = s.substring(0, i) + (char) ((int) s.charAt(i) + 1);
                        trial = new BigDecimal(s2);
                    }
                }
            }
        }
        if (trial != null && (precision == 1 ? trial.floatValue() == value : trial.doubleValue() == value)) {
            return trial;
        } else {
            return initial;
        }
    }


    /**
     * Inner class to represent one sub-picture (the negative or positive subpicture)
     */

    private static class SubPicture {

        int minWholePartSize = 0;
        int maxWholePartSize = 0;
        int minFractionPartSize = 0;
        int maxFractionPartSize = 0;
        int minExponentSize = 0;
        int scalingFactor = 0;
        boolean isPercent = false;
        boolean isPerMille = false;
        String prefix = "";
        String suffix = "";
        int[] wholePartGroupingPositions = null;
        int[] fractionalPartGroupingPositions = null;
        boolean regular;
        boolean is31 = false;

        public SubPicture(int[] pic, DecimalSymbols dfs) throws XPathException {

            is31 = true;

            final int percentSign = dfs.getPercent();
            final int perMilleSign = dfs.getPerMille();
            final int decimalSeparator = dfs.getDecimalSeparator();
            final int groupingSeparator = dfs.getGroupingSeparator();
            final int digitSign = dfs.getDigit();
            final int zeroDigit = dfs.getZeroDigit();
            final int exponentSeparator = dfs.getExponentSeparator();

            List<Integer> wholePartPositions = null;
            List<Integer> fractionalPartPositions = null;

            boolean foundDigit = false;
            boolean foundDecimalSeparator = false;
            boolean foundExponentSeparator = false;
            boolean foundExponentSeparator2 = false;
            for (int ch : pic) {
                if (ch == digitSign || ch == zeroDigit || isInDigitFamily(ch, zeroDigit)) {
                    foundDigit = true;
                    break;
                }
            }
            if (!foundDigit) {
                grumble("subpicture contains no digit or zero-digit sign");
            }

            int phase = 0;

            // phase = 0: passive characters at start
            // phase = 1: digit signs in whole part
            // phase = 2: zero-digit signs in whole part
            // phase = 3: zero-digit signs in fractional part
            // phase = 4: digit signs in fractional part
            // phase = 5: zero-digit signs in exponent part
            // phase = 6: passive characters at end

            for (int c : pic) {
                if (c == percentSign || c == perMilleSign) {
                    if (isPercent || isPerMille) {
                        grumble("Cannot have more than one percent or per-mille character in a sub-picture");
                    }
                    isPercent = c == percentSign;
                    isPerMille = c == perMilleSign;
                    switch (phase) {
                        case 0:
                            prefix += unicodeChar(c);
                            break;
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                            if (foundExponentSeparator) {
                                grumble("Cannot have exponent-separator as well as percent or per-mille character in a sub-picture");
                            }
                        case 6:
                            phase = 6;
                            suffix += unicodeChar(c);
                            break;
                    }
                } else if (c == digitSign) {
                    switch (phase) {
                        case 0:
                        case 1:
                            phase = 1;
                            maxWholePartSize++;
                            break;
                        case 2:
                            grumble("Digit sign must not appear after a zero-digit sign in the integer part of a sub-picture");
                            break;
                        case 3:
                        case 4:
                            phase = 4;
                            maxFractionPartSize++;
                            break;
                        case 5:
                            grumble("Digit sign must not appear in the exponent part of a sub-picture");
                            break;
                        case 6:
                            if (foundExponentSeparator2) {
                                grumble("There must only be one exponent separator in a sub-picture");
                            } else {
                                grumble("Passive character must not appear between active characters in a sub-picture");
                            }
                            break;
                    }
                } else if (c == zeroDigit || isInDigitFamily(c, zeroDigit)) {
                    switch (phase) {
                        case 0:
                        case 1:
                        case 2:
                            phase = 2;
                            minWholePartSize++;
                            maxWholePartSize++;
                            break;
                        case 3:
                            minFractionPartSize++;
                            maxFractionPartSize++;
                            break;
                        case 4:
                            grumble("Zero digit sign must not appear after a digit sign in the fractional part of a sub-picture");
                            break;
                        case 5:
                            minExponentSize++;
                            break;
                        case 6:
                            if (foundExponentSeparator2) {
                                grumble("There must only be one exponent separator in a sub-picture");
                            } else {
                                grumble("Passive character must not appear between active characters in a sub-picture");
                            }
                            break;
                    }
                } else if (c == decimalSeparator) {
                    if (foundDecimalSeparator) {
                        grumble("There must only be one decimal separator in a sub-picture");
                    }
                    switch (phase) {
                        case 0:
                        case 1:
                        case 2:
                            phase = 3;
                            foundDecimalSeparator = true;
                            break;
                        case 3:
                        case 4:
                        case 5:
                            if (foundExponentSeparator) {
                                grumble("Decimal separator must not appear in the exponent part of a sub-picture");
                            }
                            break;
                        case 6:
                            grumble("Decimal separator cannot come after a character in the suffix");
                            break;
                    }
                } else if (c == groupingSeparator) {
                    switch (phase) {
                        case 0:
                        case 1:
                        case 2:
                            if (wholePartPositions == null) {
                                wholePartPositions = new ArrayList<>(3);
                            }
                            if (wholePartPositions.contains(maxWholePartSize)) {
                                grumble("Sub-picture cannot contain adjacent grouping separators");
                            }
                            wholePartPositions.add(maxWholePartSize);
                            // note these are positions from a false offset, they will be corrected later
                            break;
                        case 3:
                        case 4:
                            if (maxFractionPartSize == 0) {
                                grumble("Grouping separator cannot be adjacent to decimal separator");
                            }
                            if (fractionalPartPositions == null) {
                                fractionalPartPositions = new ArrayList<Integer>(3);
                            }
                            if (fractionalPartPositions.contains(maxFractionPartSize)) {
                                grumble("Sub-picture cannot contain adjacent grouping separators");
                            }
                            fractionalPartPositions.add(maxFractionPartSize);
                            break;
                        case 5:
                            if (foundExponentSeparator) {
                                grumble("Grouping separator must not appear in the exponent part of a sub-picture");
                            }
                            break;
                        case 6:
                            grumble("Grouping separator found in suffix of sub-picture");
                            break;
                    }
                } else if (c == exponentSeparator) {
                    switch (phase) {
                        case 0:
                            prefix += unicodeChar(c);
                            break;
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                            phase = 5;
                            foundExponentSeparator = true;
                            break;
                        case 5:
                            if (foundExponentSeparator) {
                                foundExponentSeparator2 = true;
                                phase = 6;
                                suffix += unicodeChar(exponentSeparator);
                            }
                            break;
                        case 6:
                            suffix += unicodeChar(c);
                            break;
                    }
                } else {    // passive character found
                    switch (phase) {
                        case 0:
                            prefix += unicodeChar(c);
                            break;
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                            if (minExponentSize == 0 && foundExponentSeparator) {
                                phase = 6;
                                suffix += unicodeChar(exponentSeparator);
                                suffix += unicodeChar(c);
                                break;
                            }
                        case 6:
                            phase = 6;
                            suffix += unicodeChar(c);
                            break;
                    }
                }
            }

            /* 4.7.4 Rule 3 */
            scalingFactor = minWholePartSize;

            if (maxWholePartSize == 0 && maxFractionPartSize == 0) {
                grumble("Mantissa contains no digit or zero-digit sign");
            }

            /* 4.7.4 Rule 8 */
            if (minWholePartSize == 0 && maxFractionPartSize == 0) { //minWholePartSize == 0 && !foundDecimalSeparator
                if (minExponentSize != 0) {
                    minFractionPartSize = 1;
                    maxFractionPartSize = 1;
                } else {
                    minWholePartSize = 1;
                }
            }
            /* 4.7.4 Rule 9 */
            if (minExponentSize != 0 && minWholePartSize == 0 && maxWholePartSize != 0) {
                minWholePartSize = 1;
            }
            /* 4.7.4 Rule 10 */
            if (minWholePartSize == 0 && minFractionPartSize == 0) {
                minFractionPartSize = 1;
            }

            // System.err.println("minWholePartSize = " + minWholePartSize);
            // System.err.println("maxWholePartSize = " + maxWholePartSize);
            // System.err.println("minFractionPartSize = " + minFractionPartSize);
            // System.err.println("maxFractionPartSize = " + maxFractionPartSize);

            // Sort out the grouping positions

            if (wholePartPositions != null) {
                // convert to positions relative to the decimal separator
                int n = wholePartPositions.size();
                wholePartGroupingPositions = new int[n];
                for (int i = 0; i < n; i++) {
                    wholePartGroupingPositions[i] =
                            maxWholePartSize - wholePartPositions.get(n - i - 1);
                }
                if (n == 1) {
                    regular = wholePartGroupingPositions[0] * 2 >= maxWholePartSize;
                } else if (n > 1) {
                    regular = true;
                    int first = wholePartGroupingPositions[0];
                    for (int i = 1; i < n; i++) {
                        if (wholePartGroupingPositions[i] != (i + 1) * first) {
                            regular = false;
                            break;
                        }
                    }
                    if (regular && (maxWholePartSize - wholePartGroupingPositions[n - 1] > first)) {
                        regular = false;
                    }
                    if (regular) {
                        wholePartGroupingPositions = new int[1];
                        wholePartGroupingPositions[0] = first;
                    }
                }
                if (wholePartGroupingPositions[0] == 0) {
                    //grumble("Cannot have a grouping separator adjacent to the decimal separator");
                    grumble("Cannot have a grouping separator at the end of the integer part");
                }
            }

            if (fractionalPartPositions != null) {
                int n = fractionalPartPositions.size();
                fractionalPartGroupingPositions = new int[n];
                for (int i = 0; i < n; i++) {
                    fractionalPartGroupingPositions[i] = fractionalPartPositions.get(i);
                }
            }
        }

        /**
         * Format a number using this sub-picture
         *
         * @param value     the absolute value of the number to be formatted
         * @param dfs       the decimal format symbols to be used
         * @param minusSign the representation of a minus sign to be used
         * @return the formatted number
         */

        public CharSequence format(NumericValue value, DecimalSymbols dfs, String minusSign) {

            // System.err.println("Formatting " + value);

            if (value.isNaN()) {
                return dfs.getNaN();     // changed by W3C Bugzilla 2712
            }

            int multiplier = 1;
            if (isPercent) {
                multiplier = 100;
            } else if (isPerMille) {
                multiplier = 1000;
            }

            if (multiplier != 1) {
                try {
                    value = (NumericValue) ArithmeticExpression.compute(
                            value, Calculator.TIMES, new Int64Value(multiplier), null);
                } catch (XPathException e) {
                    value = new DoubleValue(Double.POSITIVE_INFINITY);
                }
            }

            if ((value instanceof DoubleValue || value instanceof FloatValue) &&
                    Double.isInfinite(value.getDoubleValue())) {
                return minusSign + prefix + dfs.getInfinity() + suffix;
            }


            FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C16);
            if (value instanceof DoubleValue || value instanceof FloatValue) {
                BigDecimal dec = adjustToDecimal(value.getDoubleValue(), 2);
                formatDecimal(dec, sb);


            } else if (value instanceof IntegerValue) {
                if (minExponentSize != 0) {
                    formatDecimal(((IntegerValue) value).getDecimalValue(), sb);
                } else {
                    formatInteger(value, sb);
                }

            } else if (value instanceof BigDecimalValue) {
                //noinspection RedundantCast
                formatDecimal(((BigDecimalValue) value).getDecimalValue(), sb);
            }

            // System.err.println("Justified number: " + sb.toString());

            // Map the digits and decimal point to use the selected characters

            int[] ib = StringValue.expand(sb);
            int ibused = ib.length;
            int point = sb.indexOf('.');
            if (point == -1) {
                point = sb.length();
            } else {
                ib[point] = dfs.getDecimalSeparator();

                // If there is no fractional part, delete the decimal point
                if (maxFractionPartSize == 0) {
                    ibused--;
                }
            }

            // Map the digits

            if (dfs.getZeroDigit() != '0') {
                int newZero = dfs.getZeroDigit();
                for (int i = 0; i < ibused; i++) {
                    int c = ib[i];
                    if (c >= '0' && c <= '9') {
                        ib[i] = c - '0' + newZero;
                    }
                }
            }

            // Map the exponent-separator

            if (dfs.getExponentSeparator() != 'e') {
                int expS = sb.indexOf('e');
                if (expS != -1) {
                    ib[expS] = dfs.getExponentSeparator();
                }
            }

            // Add the whole-part grouping separators

            if (wholePartGroupingPositions != null) {
                if (regular) {
                    // grouping separators are at regular positions
                    int g = wholePartGroupingPositions[0];
                    int p = point - g;
                    while (p > 0) {
                        ib = insert(ib, ibused++, dfs.getGroupingSeparator(), p);
                        //sb.insert(p, unicodeChar(dfs.groupingSeparator));
                        p -= g;
                    }
                } else {
                    // grouping separators are at irregular positions
                    for (int wholePartGroupingPosition : wholePartGroupingPositions) {
                        int p = point - wholePartGroupingPosition;
                        if (p > 0) {
                            ib = insert(ib, ibused++, dfs.getGroupingSeparator(), p);
                            //sb.insert(p, unicodeChar(dfs.groupingSeparator));
                        }
                    }
                }
            }

            // Add the fractional-part grouping separators

            if (fractionalPartGroupingPositions != null) {
                // grouping separators are at irregular positions.
                for (int i = 0; i < fractionalPartGroupingPositions.length; i++) {
                    int p = point + 1 + fractionalPartGroupingPositions[i] + i;
                    if (p < ibused) {
                        ib = insert(ib, ibused++, dfs.getGroupingSeparator(), p);
                        //sb.insert(p, dfs.groupingSeparator);
                    } else {
                        break;
                    }
                }
            }

            // System.err.println("Grouped number: " + sb.toString());

            //sb.insert(0, prefix);
            //sb.insert(0, minusSign);
            //sb.append(suffix);
            FastStringBuffer res = new FastStringBuffer(prefix.length() + minusSign.length() + suffix.length() + ibused);
            res.append(minusSign);
            res.append(prefix);
            for (int i = 0; i < ibused; i++) {
                res.appendWideChar(ib[i]);
            }
            res.append(suffix);
            return res;
        }


        /**
         * Format a number supplied as a decimal
         *
         * @param dval the decimal value
         * @param fsb  the FastStringBuffer to contain the result
         */
        private void formatDecimal(BigDecimal dval, FastStringBuffer fsb) {
            int exponent = 0;
            /*if (maxFractionPartSize == 0 && minWholePartSize == 0) {
                minWholePartSize = 1;
            }*/ // this bit of logic is now included in the SubPicture class
            if (minExponentSize == 0) {
                dval = dval.setScale(maxFractionPartSize, RoundingMode.HALF_EVEN);
            } else {
                exponent = dval.precision() - dval.scale() - scalingFactor;
                dval = dval.movePointLeft(exponent);
                dval = dval.setScale(maxFractionPartSize, RoundingMode.HALF_EVEN);
            }
            BigDecimalValue.decimalToString(dval, fsb);

            int point = fsb.indexOf('.');
            int intDigits;
            if (point >= 0) {
                int zz = maxFractionPartSize - minFractionPartSize;
                while (zz > 0) {
                    if (fsb.charAt(fsb.length() - 1) == '0') {
                        fsb.setLength(fsb.length() - 1);
                        zz--;
                    } else {
                        break;
                    }
                }
                intDigits = point;
                if (fsb.charAt(fsb.length() - 1) == '.') {
                    fsb.setLength(fsb.length() - 1);
                }
            } else {
                intDigits = fsb.length();
                if (minFractionPartSize > 0) {
                    fsb.cat('.');
                    for (int i = 0; i < minFractionPartSize; i++) {
                        fsb.cat('0');
                    }
                }
            }
            if (minWholePartSize == 0 && intDigits == 1 && fsb.charAt(0) == '0') {
                fsb.removeCharAt(0);
            } else {
                fsb.prependRepeated('0', minWholePartSize - intDigits);
            }
            if (minExponentSize != 0) {
                fsb.cat('e');
                IntegerValue exp = (IntegerValue) IntegerValue.makeIntegerValue(exponent);
                String expStr = exp.toString();
                char first = expStr.charAt(0);
                if (first == '-') {
                    fsb.cat('-');
                    expStr = expStr.substring(1);
                }
                int length = expStr.length();
                if (length < minExponentSize) {
                    int zz = minExponentSize - length;
                    for (int i = 0; i < zz; i++) {
                        fsb.cat('0');
                    }
                }
                fsb.append(expStr);
            }
        }

        /**
         * Format a number supplied as a integer
         *
         * @param value the integer value
         * @param fsb   the FastStringBuffer to contain the result
         */

        private void formatInteger(NumericValue value, FastStringBuffer fsb) {
            if (!(minWholePartSize == 0 && value.compareTo(0) == 0)) {
                fsb.cat(value.getStringValueCS());
                int leadingZeroes = minWholePartSize - fsb.length();
                fsb.prependRepeated('0', leadingZeroes);
            }
            if (minFractionPartSize != 0) {
                fsb.cat('.');
                for (int i = 0; i < minFractionPartSize; i++) {
                    fsb.cat('0');
                }
            }
        }


    }

    /**
     * Convert a Unicode character (possibly >65536) to a String, using a surrogate pair if necessary
     *
     * @param ch the Unicode codepoint value
     * @return a string representing the Unicode codepoint, either a string of one character or a surrogate pair
     */

    private static CharSequence unicodeChar(int ch) {
        if (ch < 65536) {
            return "" + (char) ch;
        } else {  // output a surrogate pair
            //To compute the numeric value of the character corresponding to a surrogate
            //pair, use this formula (all numbers are hex):
            //(FirstChar - D800) * 400 + (SecondChar - DC00) + 10000
            ch -= 65536;
            char[] sb = new char[2];
            sb[0] = (char) ((ch / 1024) + 55296);
            sb[1] = (char) ((ch % 1024) + 56320);
            return new CharSlice(sb, 0, 2);
        }
    }

    /**
     * Insert an integer into an array of integers. This may or may not modify the supplied array.
     *
     * @param array    the initial array
     * @param used     the number of items in the initial array that are used
     * @param value    the integer to be inserted
     * @param position the position of the new integer in the final array
     * @return the new array, with the new integer inserted
     */

    private static int[] insert(int[] array, int used, int value, int position) {
        if (used + 1 > array.length) {
            array = Arrays.copyOf(array, used + 10);
        }
        System.arraycopy(array, position, array, position + 1, used - position);
        array[position] = value;
        return array;
    }

    /**
     * Call the format-number function, supplying two or three arguments
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
     *                  call it only once; if the value is required more than once, it should first be converted
     *                  to a {@link net.sf.saxon.om.GroundedValue} by calling the utility methd
     *                  SequenceTool.toGroundedValue().</p>
     *                  <p>If the expected value is a single item, the item should be obtained by calling
     *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
     *                  {@link net.sf.saxon.om.Item} or {@link net.sf.saxon.value.AtomicValue}.</p>
     *                  <p>It is the caller's responsibility to perform any type conversions required
     *                  to convert arguments to the type expected by the callee. An exception is where
     *                  this Callable is explicitly an argument-converting wrapper around the original
     *                  Callable.</p>
     * @return the result of the function
     * @throws XPathException if any dynamic error occurs
     */

    @Override
    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        int numArgs = arguments.length;
        DecimalFormatManager dfm = getRetainedStaticContext().getDecimalFormatManager();
        DecimalSymbols dfs;

        AtomicValue av0 = (AtomicValue) arguments[0].head();
        if (av0 == null) {
            av0 = DoubleValue.NaN;
        }
        NumericValue number = (NumericValue) av0;

        if (picture != null) {
            // Decimal format and picture known statically
            CharSequence result = formatNumber(number, subPictures, decimalSymbols);
            return new StringValue(result);

        } else {

            if (numArgs == 2) {
                dfs = dfm.getDefaultDecimalFormat();
            } else {
                // the decimal-format name was given as a run-time expression
                Item arg2 = arguments[2].head();
                if (arg2 == null) {
                    dfs = dfm.getDefaultDecimalFormat();
                } else {
                    String lexicalName = arg2.getStringValue();
                    dfs = getNamedDecimalFormat(dfm, lexicalName);
                }
            }
            String format = arguments[1].head().getStringValue();
            SubPicture[] pics = getSubPictures(format, dfs);
            return new StringValue(formatNumber(number, pics, dfs));
        }
    }

    /**
     * Get a decimal format, given its lexical QName
     *
     * @param dfm         the decimal format manager
     * @param lexicalName the lexical QName (or EQName) of the decimal format
     * @return the decimal format
     * @throws XPathException if the lexical QName is invalid or if no decimal format is found.
     */

    protected DecimalSymbols getNamedDecimalFormat(DecimalFormatManager dfm, String lexicalName) throws XPathException {
        DecimalSymbols dfs;
        StructuredQName qName;
        try {
            qName = StructuredQName.fromLexicalQName(lexicalName, false,
                                                     true, getRetainedStaticContext());
        } catch (XPathException e) {
            XPathException err = new XPathException("Invalid decimal format name. " + e.getMessage());
            err.setErrorCode("FODF1280");
            throw err;
        }

        dfs = dfm.getNamedDecimalFormat(qName);
        if (dfs == null) {
            XPathException err = new XPathException("format-number function: decimal-format '" + lexicalName + "' is not defined");
            err.setErrorCode("FODF1280");
            throw err;
        }
        return dfs;
    }

    /**
     * Test whether a character is in the digit family identified by a particular zero digit
     *
     * @param ch        the character to be tested
     * @param zeroDigit a Unicode character with digit value zero
     * @return true if the supplied character is in this digit family
     */

    private static boolean isInDigitFamily(int ch, int zeroDigit) {
        return ch >= zeroDigit && ch < zeroDigit + 10;
    }

    /**
     * Format a double as required by the adaptive serialization method
     *
     * @param value the value to be formatted
     * @return the formatted value
     */

    public static String formatExponential(DoubleValue value) {
        try {
            DecimalSymbols dfs = new DecimalSymbols(HostLanguage.XSLT, 31);
            dfs.setInfinity("INF");
            SubPicture[] pics = getSubPictures("0.0##########################e0", dfs);
            return formatNumber(value, pics, dfs).toString();
        } catch (XPathException e) {
            return value.getStringValue();
        }
    }

    /**
     * Make a copy of this SystemFunction. This is required only for system functions such as regex
     * functions that maintain state on behalf of a particular caller.
     *
     * @return a copy of the system function able to contain its own copy of the state on behalf of
     * the caller.
     */
    @Override
    public FormatNumber copy() {
        FormatNumber copy = (FormatNumber) SystemFunction.makeFunction(getFunctionName().getLocalPart(), getRetainedStaticContext(), getArity());
        copy.decimalFormatName = decimalFormatName;
        copy.picture = picture;
        copy.decimalSymbols = decimalSymbols;
        copy.subPictures = subPictures;
        return copy;
    }

}

