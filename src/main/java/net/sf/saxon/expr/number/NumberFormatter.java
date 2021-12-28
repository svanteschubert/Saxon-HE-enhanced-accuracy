////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.number;

import net.sf.saxon.lib.Numberer;
import net.sf.saxon.regex.EmptyString;
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.regex.charclass.Categories;
import net.sf.saxon.tree.util.FastStringBuffer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

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

        formatTokens = new ArrayList<UnicodeString>(10);
        punctuationTokens = new ArrayList<UnicodeString>(10);

        UnicodeString uFormat = UnicodeString.makeUnicodeString(format);
        int len = uFormat.uLength();
        int i = 0;
        int t;
        boolean first = true;
        startsWithPunctuation = true;

        while (i < len) {
            int c = uFormat.uCharAt(i);
            t = i;
            while (isLetterOrDigit(c)) {
                i++;
                if (i == len) break;
                c = uFormat.uCharAt(i);
            }
            if (i > t) {
                UnicodeString tok = uFormat.uSubstring(t, i);
                formatTokens.add(tok);
                if (first) {
                    punctuationTokens.add(UnicodeString.makeUnicodeString("."));
                    startsWithPunctuation = false;
                    first = false;
                }
            }
            if (i == len) break;
            t = i;
            c = uFormat.uCharAt(i);
            while (!isLetterOrDigit(c)) {
                first = false;
                i++;
                if (i == len) break;
                c = uFormat.uCharAt(i);
            }
            if (i > t) {
                UnicodeString sep = uFormat.uSubstring(t, i);
                punctuationTokens.add(sep);
            }
        }

        if (formatTokens.isEmpty()) {
            formatTokens.add(UnicodeString.makeUnicodeString("1"));
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

    private static IntPredicate alphanumeric =
            Categories.getCategory("N").or(Categories.getCategory("L"));

    /**
     * Format a list of numbers.
     *
     * @param numbers the numbers to be formatted (a sequence of integer values; it may also contain
     *                preformatted strings as part of the error recovery fallback)
     * @return the formatted output string.
     */

    public CharSequence format(List numbers, int groupSize, String groupSeparator,
                               String letterValue, String ordinal, /*@NotNull*/ Numberer numberer) {

        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C16);
        int num = 0;
        int tok = 0;
        // output first punctuation token
        if (startsWithPunctuation) {
            sb.append(punctuationTokens.get(tok));
        }
        // output the list of numbers
        while (num < numbers.size()) {
            if (num > 0) {
                if (tok == 0 && startsWithPunctuation) {
                    // The first punctuation token isn't a separator if it appears before the first
                    // formatting token. Such a punctuation token is used only once, at the start.
                    sb.append(".");
                } else {
                    sb.append(punctuationTokens.get(tok));
                }
            }
            Object o = numbers.get(num++);
            String s;
            if (o instanceof Long) {
                long nr = (Long) o;
                RegularGroupFormatter rgf = new RegularGroupFormatter(groupSize, groupSeparator, EmptyString.THE_INSTANCE);
                s = numberer.format(nr, formatTokens.get(tok), rgf, letterValue, ordinal);
            } else if (o instanceof BigInteger) {
                // Saxon bug 2071; test case number-0111
                FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);
                fsb.append(o.toString());
                RegularGroupFormatter rgf = new RegularGroupFormatter(groupSize, groupSeparator, EmptyString.THE_INSTANCE);
                s = rgf.format(fsb);
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
            sb.append(punctuationTokens.get(punctuationTokens.size() - 1));
        }
        return sb.condense();
    }

    private String translateDigits(String in, UnicodeString picture) {
        if (picture.length() == 0) {
            return in;
        }
        int formchar = picture.uCharAt(0);
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
            FastStringBuffer sb = new FastStringBuffer(128);
            for (int i = 0; i < in.length(); i++) {
                char c = in.charAt(i);
                if (c >= '0' && c <= '9') {
                    sb.appendWideChar(digits[c - '0']);
                } else {
                    sb.cat(c);
                }
            }
            return sb.toString();
        } else {
            return in;
        }
    }


}

