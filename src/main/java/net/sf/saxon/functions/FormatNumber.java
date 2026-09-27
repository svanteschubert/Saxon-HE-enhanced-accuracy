////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.str.*;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.DecimalSymbols;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntIterator;

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
    private UnicodeString picture;
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

        if (arguments[1] instanceof Literal &&
                (arguments.length == 2 ||
                         arguments[2] instanceof StringLiteral ||
                        Literal.isEmptySequence(arguments[2]))) {
            DecimalFormatManager dfm = getRetainedStaticContext().getDecimalFormatManager();
            assert dfm != null;
            picture = ((StringLiteral) arguments[1]).getUnicodeString();
            if (arguments.length == 3) {
                if ((arguments[2] instanceof StringLiteral)) {
                    try {
                        String lexicalName = ((StringLiteral) arguments[2]).stringify();
                        decimalFormatName = StructuredQName.fromLexicalQName(lexicalName, false,
                                                                             StructuredQName.QUPL, getRetainedStaticContext());
                    } catch (XPathException e) {
                        throw new XPathException("Invalid decimal format name. " + e.getMessage(), "FODF1280");
                    }
                } else if (Literal.isEmptySequence(arguments[2])) {
                    decimalFormatName = null;
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

    private static SubPicture[] getSubPictures(UnicodeString picture, DecimalSymbols dfs) throws XPathException {
        SubPicture[] pics = new SubPicture[2];
        if (picture.length() == 0) {
            throw new XPathException("format-number() picture is zero-length", "FODF1310");
        }
        int patternSeparator = dfs.getMarker(DecimalSymbols.Property.PATTERN_SEPARATOR);
        long split = picture.indexOf(patternSeparator);
        if (split < 0) {
            pics[0] = makeSubPicture(picture, dfs);
            pics[1] = null;
        } else {
            UnicodeString p0 = picture.substring(0, split);
            if (p0.length() == 0) {
                grumble("first sub-picture is zero-length");
            }
            pics[0] = makeSubPicture(p0, dfs);
            UnicodeString p1 = picture.substring(split+1);

            if (p1.length() == 0) {
                grumble("second sub-picture is zero-length");
            }
            if (p1.indexOf(patternSeparator) >= 0) {
                grumble("more than one pattern separator");
            }
            pics[1] = makeSubPicture(p1, dfs);
        }

        return pics;
    }

    @CSharpReplaceBody(code="return new Saxon.Impl.Overrides.FormatNumberSubPicture(details, dfs);")
    protected static SubPicture makeSubPicture(UnicodeString details, DecimalSymbols dfs) throws XPathException {
        return new SubPicture(details, dfs);
    }

    /**
     * Format a number, given the two subpictures and the decimal format symbols
     *
     * @param number      the number to be formatted
     * @param subPictures the negative and positive subPictures
     * @param dfs         the decimal format symbols to be used
     * @return the formatted number
     */

    private static UnicodeString formatNumber(NumericValue number,
                                              SubPicture[] subPictures,
                                              DecimalSymbols dfs) {

        NumericValue absN = number;
        SubPicture pic;
        UnicodeString minusSign = EmptyUnicodeString.getInstance();
        int signum = number.signum();
        if (signum == 0 && number.isNegativeZero()) {
            signum = -1;
        }
        if (signum < 0) {
            absN = number.negate();
            if (subPictures[1] == null) {
                pic = subPictures[0];
                minusSign = dfs.getProperty(DecimalSymbols.Property.MINUS_SIGN);
            } else {
                pic = subPictures[1];
            }
        } else {
            pic = subPictures[0];
        }

        return pic.format(absN, dfs, minusSign);
    }

    private static String stringFromCodepoint(int codepoint) {
        StringBuilder sb = new StringBuilder();
        sb.appendCodePoint(codepoint);
        return sb.toString();
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

    @CSharpReplaceBody(code="return Singulink.Numerics.BigDecimal.FromDouble(value, Singulink.Numerics.FloatConversion.ParseString);") // keep it simple for now...
    public static BigDecimal adjustToDecimal(double value, int precision) {
        final String zeros = precision == 1 ? "00000" : "000000000";
        final String nines = precision == 1 ? "99999" : "999999999";
        BigDecimal initial = BigDecimal.valueOf(value);
        BigDecimal trial = null;
        StringBuilder fsb = new StringBuilder(16);
        BigDecimalValue.decimalToString(initial, fsb);
        String s = fsb.toString();
        int start = s.charAt(0) == '-' ? 1 : 0;
        int p = s.indexOf(".");
        int i = s.lastIndexOf(zeros);
        if (i > 0) {
            if (p < 0 || i < p) {
                // we're in the integer part
                // try replacing all following digits with zeros and seeing if we get the same double back
                StringBuilder sb = new StringBuilder(s.length());
                sb.append(s.substring(0, i));
                for (int n = i; n < s.length(); n++) {
                    sb.append(s.charAt(n) == '.' ? '.' : '0');
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
                    StringBuilder sb = new StringBuilder(s.length() + 1);
                    if (start == 1) {
                        sb.append('-');
                    }
                    sb.append('1');
                    for (int n = start; n < s.length(); n++) {
                        sb.append(s.charAt(n) == '.' ? '.' : '0');
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
                        StringBuilder sb = new StringBuilder(s.length());
                        sb.append(s.substring(0, i));
                        sb.append((char) ((int) s.charAt(i) + 1));
                        for (int n = i; n < s.length(); n++) {
                            sb.append(s.charAt(n) == '.' ? '.' : '0');
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

    public static class SubPicture {  // public so it can be overridden for C#

        protected int minWholePartSize = 0;
        protected int maxWholePartSize = 0;
        protected int minFractionPartSize = 0;
        protected int maxFractionPartSize = 0;
        protected int minExponentSize = 0;
        protected int scalingFactor = 0;
        protected boolean isPercent = false;
        protected boolean isPerMille = false;
        protected UnicodeString prefix = EmptyUnicodeString.getInstance();
        protected UnicodeString suffix = EmptyUnicodeString.getInstance();
        protected int[] wholePartGroupingPositions = null;
        protected int[] fractionalPartGroupingPositions = null;
        protected boolean regular;
        protected boolean is31 = false;

        public SubPicture(UnicodeString pic, DecimalSymbols dfs) throws XPathException {

            is31 = true;

            final int percentMarker = dfs.getMarker(DecimalSymbols.Property.PERCENT_MARKER);
            final int perMilleMarker = dfs.getMarker(DecimalSymbols.Property.PER_MILLE_MARKER);
            final int decimalMarker = dfs.getMarker(DecimalSymbols.Property.DECIMAL_SEPARATOR_MARKER);
            final int groupingMarker = dfs.getMarker(DecimalSymbols.Property.GROUPING_SEPARATOR_MARKER);
            final int digitSign = dfs.getMarker(DecimalSymbols.Property.DIGIT);
            final int zeroDigit = dfs.getMarker(DecimalSymbols.Property.ZERO_DIGIT);
            final int exponentSeparator = dfs.getMarker(DecimalSymbols.Property.EXPONENT_SEPARATOR_MARKER);

            TwineBuilder prefixBuilder = TwineBuilder.make(8);
            TwineBuilder suffixBuilder = TwineBuilder.make(8);

            List<Integer> wholePartPositions = null;
            List<Integer> fractionalPartPositions = null;

            boolean foundDigit = false;
            boolean foundDecimalMarker = false;
            boolean foundExponentMarker = false;
            boolean foundExponentMarker2 = false;
            IntIterator iter = pic.codePoints();
            while (iter.hasNext()) {
                int ch = iter.next();
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

            iter = pic.codePoints();
            while (iter.hasNext()) {
                int c = iter.next();
                if (c == percentMarker || c == perMilleMarker) {
                    if (isPercent || isPerMille) {
                        grumble("Cannot have more than one percent or per-mille character in a sub-picture");
                    }
                    isPercent = c == percentMarker;
                    isPerMille = c == perMilleMarker;
                    switch (phase) {
                        case 0:
                            prefixBuilder = prefixBuilder.append(isPercent
                                                         ? dfs.getProperty(DecimalSymbols.Property.PERCENT_RENDITION)
                                                         : dfs.getProperty(DecimalSymbols.Property.PER_MILLE_RENDITION));
                            break;
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                            if (foundExponentMarker) {
                                grumble("Cannot have exponent-separator as well as percent or per-mille character in a sub-picture");
                            }
                            CSharp.emitCode("goto case 6;");
                        case 6:
                            phase = 6;
                            suffixBuilder = suffixBuilder.append(isPercent
                                                         ? dfs.getProperty(DecimalSymbols.Property.PERCENT_RENDITION)
                                                         : dfs.getProperty(DecimalSymbols.Property.PER_MILLE_RENDITION));
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
                            if (foundExponentMarker2) {
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
                            if (foundExponentMarker2) {
                                grumble("There must only be one exponent separator in a sub-picture");
                            } else {
                                grumble("Passive character must not appear between active characters in a sub-picture");
                            }
                            break;
                    }
                } else if (c == decimalMarker) {
                    if (foundDecimalMarker) {
                        grumble("There must only be one decimal separator in a sub-picture");
                    }
                    switch (phase) {
                        case 0:
                        case 1:
                        case 2:
                            phase = 3;
                            foundDecimalMarker = true;
                            break;
                        case 3:
                        case 4:
                        case 5:
                            if (foundExponentMarker) {
                                grumble("Decimal separator must not appear in the exponent part of a sub-picture");
                            }
                            break;
                        case 6:
                            grumble("Decimal separator cannot come after a character in the suffix");
                            break;
                    }
                } else if (c == groupingMarker) {
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
                                fractionalPartPositions = new ArrayList<>(3);
                            }
                            if (fractionalPartPositions.contains(maxFractionPartSize)) {
                                grumble("Sub-picture cannot contain adjacent grouping separators");
                            }
                            fractionalPartPositions.add(maxFractionPartSize);
                            break;
                        case 5:
                            if (foundExponentMarker) {
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
                            prefixBuilder = prefixBuilder.append(c);
                            break;
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                            phase = 5;
                            foundExponentMarker = true;
                            break;
                        case 5:
                            if (foundExponentMarker) {
                                foundExponentMarker2 = true;
                                phase = 6;
                                suffixBuilder = suffixBuilder.append(exponentSeparator);
                            }
                            break;
                        case 6:
                            suffixBuilder = suffixBuilder.append(c);
                            break;
                    }
                } else {    // passive character found
                    switch (phase) {
                        case 0:
                            prefixBuilder = prefixBuilder.append(c);
                            break;
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                            if (minExponentSize == 0 && foundExponentMarker) {
                                phase = 6;
                                suffixBuilder = suffixBuilder.append(exponentSeparator).append(c);
                                break;
                            }
                            CSharp.emitCode("goto case 6;");
                        case 6:
                            phase = 6;
                            suffixBuilder = suffixBuilder.append(c);
                            break;
                    }
                }
            }

            prefix = prefixBuilder.toUnicodeString();
            suffix = suffixBuilder.toUnicodeString();

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

        public UnicodeString format(NumericValue value, DecimalSymbols dfs, UnicodeString minusSign) {

            // System.err.println("Formatting " + value);

            if (value.isNaN()) {
                return dfs.getProperty(DecimalSymbols.Property.NAN);     // changed by W3C Bugzilla 2712
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
                return minusSign
                        .concat(prefix)
                        .concat(dfs.getProperty(DecimalSymbols.Property.INFINITY))
                        .concat(suffix);
            }


            StringBuilder sb = new StringBuilder(16);
            if (value instanceof DoubleValue || value instanceof FloatValue) {
                BigDecimal dec = adjustToDecimal(value.getDoubleValue(), 2);
                formatDecimal(dec, sb);


            } else if (value instanceof IntegerValue) {
                if (minExponentSize != 0) {
                    formatDecimal(((IntegerValue) value).getDecimalValue(), sb);
                } else {
                    formatInteger(value, sb);
                }

            } else if (value instanceof DecimalValue) {
                formatDecimal(((DecimalValue) value).getDecimalValue(), sb);
            }

            // System.err.println("Justified number: " + sb.toString());

            // Map the digits and decimal point to use the selected characters

            String raw = sb.toString();
            ZenoString ib = ZenoString.of(StringView.of(raw));
            int ibused = ib.length32();
            int point = raw.indexOf('.');
            if (point == -1) {
                point = raw.length();
            } else {
                // If there is no fractional part, delete the decimal point
                if (maxFractionPartSize == 0) {
                    ibused--;
                }
                // Otherwise render the decimal point
                if (!dfs.hasDefaultValue(DecimalSymbols.Property.DECIMAL_SEPARATOR_RENDITION)) {
                    ib = ib.replace(point, dfs.getProperty(DecimalSymbols.Property.DECIMAL_SEPARATOR_RENDITION));
                }
            }

            // Map the digits

            int zeroDigit = dfs.getMarker(DecimalSymbols.Property.ZERO_DIGIT);
            if (zeroDigit != '0') {
                int[] ib2 = StringTool.expand(ib);
                for (int i = 0; i < ibused; i++) {
                    int c = ib2[i];
                    if (c >= '0' && c <= '9') {
                        ib2[i] = c - '0' + zeroDigit;
                    }
                }
                ib = ZenoString.of(StringTool.fromCodePoints(ib2, ib2.length));
            }

            // Map the exponent-separator

            if (!dfs.hasDefaultValue(DecimalSymbols.Property.EXPONENT_SEPARATOR_RENDITION)) {
                int expS = (int)ib.indexOf('e');
                if (expS != -1) {
                    ib = ib.replace(expS, dfs.getProperty(DecimalSymbols.Property.EXPONENT_SEPARATOR_RENDITION));
                }
            }

            // Add the whole-part grouping separators

            if (wholePartGroupingPositions != null) {
                UnicodeString separator = dfs.getProperty(DecimalSymbols.Property.GROUPING_SEPARATOR_RENDITION);
                if (regular) {
                    // grouping separators are at regular positions
                    int g = wholePartGroupingPositions[0];
                    int p = point - g;
                    while (p > 0) {
                        ib = ib.insert(p, separator);
                        p -= g;
                    }
                } else {
                    // grouping separators are at irregular positions
                    for (int wholePartGroupingPosition : wholePartGroupingPositions) {
                        int p = point - wholePartGroupingPosition;
                        if (p > 0) {
                            ib = ib.insert(p, separator);
                        }
                    }
                }
            }

            // Add the fractional-part grouping separators
            // TODO: the decimal point might be more than one character...

            if (fractionalPartGroupingPositions != null) {
                // grouping separators are at irregular positions.
                UnicodeString separator = dfs.getProperty(DecimalSymbols.Property.GROUPING_SEPARATOR_RENDITION);
                for (int i = 0; i < fractionalPartGroupingPositions.length; i++) {
                    int p = point + 1 + fractionalPartGroupingPositions[i] + i;
                    if (p < ib.length32()) {
                        ib = ib.insert(p, separator);
                    } else {
                        break;
                    }
                }
            }

            return minusSign
                    .concat(prefix)
                    .concat(ib)
                    .concat(suffix);
        }


        /**
         * Format a number supplied as a decimal
         *
         * @param dval the decimal value
         * @param fsb  the StringBuilder to contain the result
         */
        @CSharpModifiers(code={"protected", "virtual"})
        private void formatDecimal(BigDecimal dval, StringBuilder fsb) {
            //NOTE: C# has its own version of this code in an overriding subclass
            int exponent = 0;
            if (minExponentSize == 0) {
                dval = dval.setScale(maxFractionPartSize, RoundingMode.HALF_EVEN);
            } else if (dval.signum() != 0) {
                exponent = dval.precision() - dval.scale() - scalingFactor;
                dval = dval.movePointLeft(exponent);
                dval = dval.setScale(maxFractionPartSize, RoundingMode.HALF_EVEN);
            }
            BigDecimalValue.decimalToString(dval, fsb);

            int point = fsb.indexOf(".");
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
                    fsb.append('.');
                    for (int i = 0; i < minFractionPartSize; i++) {
                        fsb.append('0');
                    }
                }
            }

            if (minWholePartSize == 0 && intDigits == 1 && fsb.charAt(0) == '0') {
                fsb.deleteCharAt(0);
            } else if (minWholePartSize > intDigits) {
                StringTool.prependRepeated(fsb, '0', minWholePartSize - intDigits);
            }
            if (minExponentSize != 0) {
                fsb.append('e');
                IntegerValue exp = (IntegerValue) IntegerValue.fromDouble(exponent);
                String expStr = exp.getUnicodeStringValue().toString();
                char first = expStr.charAt(0);
                if (first == '-') {
                    fsb.append('-');
                    expStr = expStr.substring(1);
                }
                int length = expStr.length();
                if (length < minExponentSize) {
                    int zz = minExponentSize - length;
                    for (int i = 0; i < zz; i++) {
                        fsb.append('0');
                    }
                }
                fsb.append(expStr);
            }
        }

        /**
         * Format a number supplied as a integer
         *
         * @param value the integer value
         * @param sb   the StringBuilder to contain the result
         */

        private void formatInteger(NumericValue value, StringBuilder sb) {
            if (!(minWholePartSize == 0 && value.compareTo(0) == 0)) {
                sb.append(value.getUnicodeStringValue());
                int leadingZeroes = minWholePartSize - sb.length();
                if (leadingZeroes > 0) {
                    StringTool.prependRepeated(sb, '0', leadingZeroes);
                }
            }
            if (minFractionPartSize != 0) {
                sb.append('.');
                for (int i = 0; i < minFractionPartSize; i++) {
                    sb.append('0');
                }
            }
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
            UnicodeString result = formatNumber(number, subPictures, decimalSymbols);
            return new StringValue(result);

        } else {

            if (numArgs == 2) {
                dfs = dfm.getDefaultDecimalFormat();
            } else {
                // the decimal-format name was given as a run-time expression
                Item arg2 = arguments[2].head();
                if (arg2 == null) {
                    dfs = dfm.getDefaultDecimalFormat();
                } else if (arg2 instanceof MapItem) {
                    dfs = processOptions(dfm, (MapItem)arg2);
                } else {
                    String lexicalName = arg2.getUnicodeStringValue().toString();
                    dfs = getNamedDecimalFormat(dfm, lexicalName);
                }
            }
            UnicodeString format = arguments[1].head().getUnicodeStringValue();
            SubPicture[] pics = getSubPictures(format, dfs);
            return new StringValue(formatNumber(number, pics, dfs));
        }
    }

    /**
     * XPath 4.0: process options map supplied in 3rd argument
     * @param options the options argument
     * @return the corresponding DecimalFormatSymbols object
     */

    public DecimalSymbols processOptions(DecimalFormatManager dfm, MapItem options) throws XPathException {
        // TODO: use the option parameter machinery, e.g. to ensure coercions are applied
        DecimalSymbols baseFormat = null;
        GroundedValue format = options.get(new StringValue("format-name"));
        if (format != null) {
            if (format instanceof StringValue) {
                baseFormat = getNamedDecimalFormat(dfm, format.getStringValue());
            } else if (format instanceof QNameValue) {
                baseFormat = getNamedDecimalFormat(dfm,((QNameValue)format).getStructuredQName());
            }
        }
        if (baseFormat == null) {
            baseFormat = dfm.getDefaultDecimalFormat();
        }
        baseFormat = new DecimalSymbols(baseFormat);
        for (KeyValuePair kvp : options.keyValuePairs()) {
            String key = kvp.key().getStringValue();
            if (DecimalSymbols.isValidPropertyName(key)) {
                GroundedValue val = Atomizer.atomize(kvp.value());
                UnicodeString propValue = val.getUnicodeStringValue();
                baseFormat.setProperty(key, propValue);
            }
        }
        try {
            baseFormat.checkConsistency(null);
        } catch (XPathException e) {
            throw e.withErrorCode("FODF1290");
        }
        return baseFormat;
    }

    private String firstChar(GroundedValue value) throws XPathException {
        return value.getStringValue();
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
                                                     StructuredQName.QUPL, getRetainedStaticContext());
        } catch (XPathException e) {
            throw new XPathException("Invalid decimal format name. " + e.getMessage(), "FODF1280");
        }

        return getNamedDecimalFormat(dfm, qName);
    }

    protected DecimalSymbols getNamedDecimalFormat(DecimalFormatManager dfm, StructuredQName qName) throws XPathException {
        DecimalSymbols dfs = dfm.getNamedDecimalFormat(qName);
        if (dfs == null) {
            throw new XPathException("format-number function: decimal-format '" + qName + "' is not defined", "FODF1280");
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

    public static UnicodeString formatExponential(DoubleValue value) {
        try {
            DecimalSymbols dfs = new DecimalSymbols(HostLanguage.XSLT, 31);
            dfs.setProperty("infinity", new Twine8("INF"));
            SubPicture[] pics = getSubPictures(new Twine8("0.0##########################e0"), dfs);
            return formatNumber(value, pics, dfs);
        } catch (XPathException e) {
            return value.getUnicodeStringValue();
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

    /**
     * Get a function to format a double as a string using a supplied picture
     * @param picture the supplied picture
     * @return a function that converts a double to a string
     * @throws XPathException if the picture is invalid
     */
    public static java.util.function.Function<Double, UnicodeString> getFormatter(UnicodeString picture) throws XPathException {
        DecimalSymbols symbols = new DecimalSymbols(HostLanguage.XSLT, 30);
        SubPicture[] subPictures = getSubPictures(picture, symbols);
        return dbl -> formatNumber(new DoubleValue(dbl), subPictures, symbols);
    }


}

