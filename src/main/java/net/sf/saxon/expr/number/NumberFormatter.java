////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.number;

import net.sf.saxon.lib.Numberer;
import net.sf.saxon.regex.charclass.Categories;
import net.sf.saxon.str.*;
import net.sf.saxon.z.IntPredicateProxy;
import net.sf.saxon.z.IntUnionPredicate;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Class NumberFormatter defines a method to format a ArrayList of integers as a character
 * string according to a supplied format specification.
 *
 */

public class NumberFormatter {

    private ArrayList<UnicodeString> formatTokens;
    private ArrayList<UnicodeString> punctuationTokens;
    private boolean startsWithPunctuation;

    /**
     * Tokenize the format pattern.
     *
     * @param format the format specification. Contains one of the following values:<ul>
     *               <li>"1": conventional decimal numbering</li>
     *               <li>"a": sequence a, b, c, ... aa, ab, ac, ...</li>
     *               <li>"A": sequence A, B, C, ... AA, AB, AC, ...</li>
     *               <li>"i": sequence i, ii, iii, iv, v ...</li>
     *               <li>"I": sequence I, II, III, IV, V, ...</li>
     *               </ul>
     *               This symbol may be preceded and followed by punctuation (any other characters) which is
     *               copied to the output string.
     */

    public void prepare(String format) {

        // Tokenize the format string into alternating alphanumeric and non-alphanumeric tokens

        if (format.isEmpty()) {
            format = "1";
        }

        formatTokens = new ArrayList<>(10);
        punctuationTokens = new ArrayList<>(10);

        UnicodeString uFormat = StringView.tidy(format);
        int len = uFormat.length32();
        int i = 0;
        int t;
        boolean first = true;
        startsWithPunctuation = true;

        while (i < len) {
            int c = uFormat.codePointAt(i);
            t = i;
            while (isLetterOrDigit(c)) {
                i++;
                if (i == len) break;
                c = uFormat.codePointAt(i);
            }
            if (i > t) {
                UnicodeString tok = uFormat.substring(t, i);
                formatTokens.add(tok);
                if (first) {
                    punctuationTokens.add(BMPString.of("."));
                    startsWithPunctuation = false;
                    first = false;
                }
            }
            if (i == len) break;
            t = i;
            c = uFormat.codePointAt(i);
            while (!isLetterOrDigit(c)) {
                first = false;
                i++;
                if (i == len) break;
                c = uFormat.codePointAt(i);
            }
            if (i > t) {
                UnicodeString sep = uFormat.substring(t, i);
                punctuationTokens.add(sep);
            }
        }

        if (formatTokens.isEmpty()) {
            formatTokens.add(BMPString.of("1"));
            if (punctuationTokens.size() == 1) {
                punctuationTokens.add(punctuationTokens.get(0));
            }
        }

    }

    /**
     * Determine whether a (possibly non-BMP) character is a letter or digit.
     *
     * @param c the codepoint of the character to be tested
     * @return true if this is a number or letter as defined in the XSLT rules for xsl:number pictures.
     */

    public static boolean isLetterOrDigit(int c) {
        if (c <= 0x7F) {
            // Fast path for ASCII characters
            return (c >= 0x30 && c <= 0x39) || (c >= 0x41 && c <= 0x5A) || (c >= 0x61 && c <= 0x7A);
        } else {
            return alphanumeric.test(c);
        }
    }

    private static final IntPredicateProxy alphanumeric =
            IntUnionPredicate.makeUnion(Categories.getCategory("N"), (Categories.getCategory("L")));

    /**
     * Format a list of numbers.
     *
     * @param numbers the numbers to be formatted (a sequence of integer values; it may also contain
     *                preformatted strings as part of the error recovery fallback)
     * @param groupSize the grouping-size, as in xsl:number
     * @param groupSeparator the grouping-separator, as in xsl:number
     * @param letterValue the letter-value, as in xsl:number
     * @param ordinal the ordinal attribute as in xsl:number
     * @param numberer the Numberer to be used for localization
     * @return the formatted output string.
     */

    public UnicodeString format(List<Object> numbers, int groupSize, String groupSeparator,
                                String letterValue, String ordinal, /*@NotNull*/ Numberer numberer) {

        UnicodeBuilder sb = new UnicodeBuilder(32);
        int num = 0;
        int tok = 0;
        // output first punctuation token
        if (startsWithPunctuation) {
            sb.accept(punctuationTokens.get(tok));
        }
        // output the list of numbers
        while (num < numbers.size()) {
            if (num > 0) {
                if (tok == 0 && startsWithPunctuation) {
                    // The first punctuation token isn't a separator if it appears before the first
                    // formatting token. Such a punctuation token is used only once, at the start.
                    sb.append(".");
                } else {
                    sb.accept(punctuationTokens.get(tok));
                }
            }
            Object o = numbers.get(num++);
            String s;
            if (o instanceof Long) {
                long nr = (Long) o;
                RegularGroupFormatter rgf = new RegularGroupFormatter(groupSize, groupSeparator, EmptyUnicodeString.getInstance());
                s = numberer.format(nr, formatTokens.get(tok), rgf, letterValue, "", ordinal);
            } else if (o instanceof BigInteger) {
                // Saxon bug 2071; test case number-0111
                RegularGroupFormatter rgf = new RegularGroupFormatter(groupSize, groupSeparator, EmptyUnicodeString.getInstance());
                s = rgf.format(o.toString());
                s = translateDigits(s, formatTokens.get(tok));
            } else {
                // Not sure this can happen
                s = o.toString();
            }
            sb.append(s);
            tok++;
            if (tok == formatTokens.size()) {
                tok--;
            }
        }
        // output the final punctuation token
        if (punctuationTokens.size() > formatTokens.size()) {
            sb.accept(punctuationTokens.get(punctuationTokens.size() - 1));
        }
        return sb.toUnicodeString();
    }

    private String translateDigits(String in, UnicodeString picture) {
        if (picture.length() == 0) {
            return in;
        }
        int formchar = picture.codePointAt(0);
        int digitValue = Alphanumeric.getDigitValue(formchar);
        if (digitValue >= 0) {
            int zero = formchar - digitValue;
            if (zero == (int) '0') {
                return in;
            }
            int[] digits = new int[10];
            for (int z = 0; z <= 9; z++) {
                digits[z] = zero + z;
            }
            StringBuilder sb = new StringBuilder(128);
            for (int i = 0; i < in.length(); i++) {
                char c = in.charAt(i);
                if (c >= '0' && c <= '9') {
                    sb.appendCodePoint(digits[c - '0']);
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        } else {
            return in;
        }
    }


}

