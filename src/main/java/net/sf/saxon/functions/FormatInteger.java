////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.number.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.lib.Numberer;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.regex.charclass.Categories;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntSet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class FormatInteger extends SystemFunction implements StatefulSystemFunction {

    private static final RegularExpression badDecimalHashPattern
            = ARegularExpression.compile("(([\\dXx]+|\\w+)#+.*)|(#+[^\\dXx]+)", "");
    private static final RegularExpression modifierPattern
            = ARegularExpression.compile("([co](\\(.*\\))?)?[at]?", "");
    private static final RegularExpression decimalDigitPattern
            = ARegularExpression.compile("^((\\p{Nd}|#|[^\\p{N}\\p{L}])+?)$", "");
    private static final RegularExpression nonDecimalDigitPattern
            = ARegularExpression.compile("^(([Xx#]|[^\\p{N}\\p{L}])+?)$", "");

    public static final String preface = "In the picture string for format-integer, ";

    private Function<IntegerValue, String> formatter = null;

    @Override
    public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {
        boolean opt = true;
        if (!(arguments[1] instanceof Literal)) {
            opt = false;
        }
        if (arguments.length == 3 && !(arguments[2] instanceof Literal)) {
            opt = false;
        }
        if (!opt) {
            return super.makeOptimizedFunctionCall(visitor, contextInfo, arguments);
        }
        Configuration config = visitor.getConfiguration();

        String language = arguments.length == 3
                ? ((StringLiteral)arguments[2]).getGroundedValue().getStringValue()
                : config.getDefaultLanguage();
        Numberer numb = config.makeNumberer(language, null);

        boolean allow40 = visitor.getStaticContext().getPackageData().getHostLanguageVersion() >= 40;
        formatter = makeFormatter(numb, ((StringLiteral)arguments[1]).getGroundedValue().getStringValue(), allow40);
        return super.makeOptimizedFunctionCall(visitor, contextInfo, arguments);
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        return formatInteger(
                (IntegerValue) arguments[0].head(),
                (StringValue) arguments[1].head(),
                arguments.length == 2 ? null : (StringValue) arguments[2].head(),
                context);
    }

    private StringValue formatInteger(IntegerValue num, StringValue picture, /*@Nullable*/ StringValue language, XPathContext context) throws XPathException {
        Configuration config = context.getConfiguration();
        boolean allow40 = getRetainedStaticContext().getPackageData().getHostLanguageVersion() >= 40;

        if (num == null) {
            return StringValue.EMPTY_STRING;
        }

        Function<IntegerValue, String> localFormatter = formatter;

        if (localFormatter == null) {
            String languageVal;
            if (language != null) {
                languageVal = language.getStringValue();
            } else {
                //default language
                languageVal = config.getDefaultLanguage();
            }
            Numberer numb = config.makeNumberer(languageVal, null);
            localFormatter = makeFormatter(numb, picture.getStringValue(), allow40);
        }


        try {
            return new StringValue(localFormatter.apply(num));
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
        }

    }

    private Function<IntegerValue, String> makeFormatter(Numberer numb, String pic, boolean allow40) throws XPathException {
        if (pic.isEmpty()) {
            throw new XPathException(preface + "the picture cannot be empty", "FODF1310");
        }

        boolean hasExplicitRadix = false;
        int radix = 10;
        if (allow40 && pic.matches("^([2-9]|[12][0-9]|3[0-6])\\^.*[xX].*$")) {
            int hat = pic.indexOf('^');
            radix = Integer.parseInt(pic.substring(0, hat));
            hasExplicitRadix = true;
            pic = pic.substring(hat + 1);
        }

        String primaryToken;
        String modifier;
        String parenthetical;

        int lastSemicolon = pic.lastIndexOf(';');
        if (lastSemicolon >= 0) {
            primaryToken = pic.substring(0, lastSemicolon);
            if (primaryToken.isEmpty()) {
                throw new XPathException(preface + "the primary format token cannot be empty", "FODF1310");
            }
            modifier = lastSemicolon < pic.length() - 1 ? pic.substring(lastSemicolon + 1) : "";
            if (!modifierPattern.matches(StringView.tidy(modifier))) {
                throw new XPathException(preface + "the modifier is invalid", "FODF1310");
            }
        } else {
            primaryToken = pic;
            modifier = "";
        }

        boolean cardinal = modifier.startsWith("c");
        boolean ordinal = modifier.startsWith("o");
        //boolean traditional = modifier.endsWith("t");
        boolean alphabetic = modifier.endsWith("a");

        int leftParen = modifier.indexOf('(');
        int rightParen = modifier.lastIndexOf(')');

        parenthetical = leftParen < 0 ? "" : modifier.substring(leftParen + 1, rightParen);

        String letterValue = alphabetic ? "alphabetic" : "traditional";
        String ordinalValue = ordinal ? "".equals(parenthetical) ? "yes" : parenthetical : "";
        String cardinalValue = cardinal ? "".equals(parenthetical) ? "yes" : parenthetical : "";

        UnicodeString primary = StringView.tidy(primaryToken);
        Categories.Category isDecimalDigit = Categories.getCategory("Nd");
        boolean isDecimalDigitPattern = false;
        if (hasExplicitRadix) {
            if (!nonDecimalDigitPattern.matches(primary)) {
                throw new XPathException(
                        preface + "the primary format token with radix " + radix + " does not " +
                                "meet the rules for a non-decimal digit pattern", "FODF1310");
            }
            letterValue = (primary.indexOf('X') >= 0 ? "X" : "x") + radix;
        } else {
            IntIterator iter = primary.codePoints();
            while (iter.hasNext()) {
                if (isDecimalDigit.test(iter.next())) {
                    isDecimalDigitPattern = true;
                    break;
                }
            }
            if (isDecimalDigitPattern && !decimalDigitPattern.matches(primary)) {
                throw new XPathException(
                        preface + "the primary format token contains a decimal digit but does not " +
                                "meet the rules for a decimal digit pattern", "FODF1310");
            }
        }
        if (isDecimalDigitPattern || hasExplicitRadix) {
            NumericGroupFormatter picGroupFormat = getPicSeparators(primary, hasExplicitRadix);
            UnicodeString adjustedPicture = picGroupFormat.getAdjustedPicture();
            String finalLetterValue = letterValue;
            return num -> {
                try {
                    String s = numb.format(num.abs().longValue(),
                                           adjustedPicture, picGroupFormat, finalLetterValue, "", ordinalValue);
                    return num.signum() < 0 ? ("-" + s) : s;
                } catch (XPathException e) {
                    throw new UncheckedXPathException(e);
                }
            };
        } else {
            UnicodeString token = StringView.tidy(primaryToken);
            String finalLetterValue = letterValue;
            return num -> {
                try {
                    String s = numb.format(num.abs().longValue(),
                                           token, null, finalLetterValue, cardinalValue, ordinalValue);
                    return num.signum() < 0 ? ("-" + s) : s;
                } catch (XPathException e) {
                    throw new UncheckedXPathException(e);
                }
            };
        }
    }

    /**
     * Get the picture separators and filter
     * them out of the picture. Has side effect of creating a simplified picture, which
     * it makes available as the getAdjustedPicture() property of the returned NumericGroupFormatter.
     *
     * @param picExpanded the formatting picture, after stripping off any modifiers
     * @param hasExplicitRadix true if digits are indicated by X or x rather than a decimal digit
     * @return a NumericGroupFormatter that implements the formatting defined in the picture
     * @throws net.sf.saxon.trans.XPathException
     *          if the picture is invalid
     */
    public static NumericGroupFormatter getPicSeparators(UnicodeString picExpanded, boolean hasExplicitRadix) throws XPathException {

        IntSet groupingPositions = new IntHashSet(5);
        List<Integer> separatorList = new ArrayList<>();
        int groupingPosition = 0; // number of digits to the right of a grouping separator
        int firstGroupingPos = 0; // number of digits to the right of the first grouping separator
        int lastGroupingPos = 0;
        boolean regularCheck = true;
        int zeroDigit = -1;

        if (badDecimalHashPattern.matches(picExpanded)) {
            throw new XPathException(preface + "the picture is not valid (it uses '#' where disallowed)", "FODF1310");
        }

        for (long i = picExpanded.length() - 1; i >= 0; i--) {

            final int codePoint = picExpanded.codePointAt(i);
            switch (Character.getType(codePoint)) {

                case Character.DECIMAL_DIGIT_NUMBER:

                    if (zeroDigit == -1) {
                        zeroDigit = Alphanumeric.getDigitFamily(codePoint);
                    } else {
                        if (zeroDigit != Alphanumeric.getDigitFamily(codePoint)) {
                            throw new XPathException(
                                    preface + "the picture mixes digits from different digit families", "FODF1310");
                        }
                    }
                    groupingPosition++;
                    break;

                case Character.UPPERCASE_LETTER:
                case Character.LOWERCASE_LETTER:
                    if (!hasExplicitRadix) {
                        break;
                    }
                    if (codePoint == 'x' || codePoint == 'X') {
                        if (zeroDigit == -1) {
                            zeroDigit = codePoint;
                        } else if (zeroDigit != codePoint) {
                            throw new XPathException(
                                    preface + "the picture mixes upper-case and lower-case non-decimal digits", "FODF1310");

                        }
                    } else {
                        throw new XPathException(
                                preface + "non-decimal digits must be indicated by 'x' or 'X'", "FODF1310");

                    }
                    groupingPosition++;
                    break;
                case Character.LETTER_NUMBER:
                case Character.OTHER_NUMBER:
                case Character.MODIFIER_LETTER:
                case Character.OTHER_LETTER:
                    break;

                default:
                    if (i == picExpanded.length() - 1) {
                        throw new XPathException(preface + "the picture cannot end with a separator", "FODF1310");
                    }
                    if (codePoint == '#') {
                        groupingPosition++;
                        if (i != 0) {
                            switch (Character.getType(picExpanded.codePointAt(i - 1))) {
                                case Character.DECIMAL_DIGIT_NUMBER:
                                case Character.LETTER_NUMBER:
                                case Character.OTHER_NUMBER:
                                case Character.UPPERCASE_LETTER:
                                case Character.LOWERCASE_LETTER:
                                case Character.MODIFIER_LETTER:
                                case Character.OTHER_LETTER:
                                    throw new XPathException(
                                            preface + "the picture cannot contain alphanumeric character(s) before character '#'", "FODF1310");
                            }
                        }
//
                        break;
                    } else {
                        boolean added = groupingPositions.add(groupingPosition);
                        if (!added) {
                            throw new XPathException(preface + "the picture contains consecutive separators", "FODF1310");
                        }
                        separatorList.add(codePoint);

                        if (groupingPositions.size() == 1) {
                            firstGroupingPos = groupingPosition;
                        } else {

                            if (groupingPosition != firstGroupingPos * groupingPositions.size()) {
                                regularCheck = false;
                            }
                            if (separatorList.get(0) != codePoint) {
                                regularCheck = false;
                            }

                        }

                        if (i == 0) {
                            throw new XPathException(preface + "the picture cannot begin with a separator", "FODF1310");
                        }
                        lastGroupingPos = groupingPosition;
                    }
                    break;
            }
        }
        if (regularCheck && groupingPositions.size() >= 1) {
            if (picExpanded.length() - lastGroupingPos - groupingPositions.size() > firstGroupingPos) {
                regularCheck = false;
            }
        }

        UnicodeString adjustedPic = extractSeparators(picExpanded, groupingPositions);
        if (groupingPositions.isEmpty()) {
            return new RegularGroupFormatter(0, "", adjustedPic);
        }

        if (regularCheck) {
            if (separatorList.isEmpty()) {
                return new RegularGroupFormatter(0, "", adjustedPic);
            } else {
                StringBuilder sb = new StringBuilder(4);
                sb.appendCodePoint(separatorList.get(0));
                return new RegularGroupFormatter(firstGroupingPos, sb.toString(), adjustedPic);
            }
        } else {
            return new IrregularGroupFormatter(groupingPositions, separatorList, adjustedPic);
        }
    }

    /**
     * Return a string in which characters at given positions have been removed
     *
     * @param arr              the string to be filtered
     * @param excludePositions the positions to be filtered out from the string, counted as positions from the end
     * @return the items from the original array that are not filtered out
     */
    private static UnicodeString extractSeparators(UnicodeString arr, IntSet excludePositions) {
        // TODO: this doesn't do what the documentation says: it ignores the supplied positions entirely
        UnicodeBuilder ub = new UnicodeBuilder(arr.length32());
        IntIterator iter = arr.codePoints();
        while (iter.hasNext()) {
            int c = iter.next();
            if (NumberFormatter.isLetterOrDigit(c)) {
                ub.append(c);
            }
        }
        return ub.toUnicodeString();
    }

    @Override
    public SystemFunction copy() {
        FormatInteger fi2 = (FormatInteger) SystemFunction.makeFunction(getFunctionName().getLocalPart(), getRetainedStaticContext(), getArity());
        fi2.formatter = formatter;
        return fi2;
    }
}

// Copyright (c) 2010-2023 Saxonica Limited
