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
import net.sf.saxon.tree.util.FastStringBuffer;

import java.util.Locale;

/**
 * Class AbstractNumberer is a base implementation of Numberer that provides language-independent
 * default numbering
 * This supports the xsl:number element.
 * Methods and data are declared as protected, and static is avoided, to allow easy subclassing.
 *
 */

public abstract class AbstractNumberer implements Numberer {

    private String country;
    private String language;

    public static final int UPPER_CASE = 0;
    public static final int LOWER_CASE = 1;
    public static final int TITLE_CASE = 2;


    /**
     * Whether this numberer has had its locale defaulted, i.e. it's not using the language requested
     * This can be used for situations, such as in fn:format-date(), where indication of use of defaults is required.
     * Override for subclasses where this can happen
     * @return the locale used, null if it wasn't defaulted
     */
    @Override
    public Locale defaultedLocale() {
        return null;
    }

    /**
     * Set the country used by this numberer (currently used only for names of timezones)
     */

    @Override
    public void setCountry(String country) {
        this.country = country;
    }

    /**
     * Set the language used by this numberer. Useful because it can potentially handle variants of English
     * (and subclasses can handle other languages)
     * @param language the requested language. Note that "en-x-hyphen" is recognized as a request to hyphenate
     *            numbers in the range 21-99.
     */

    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Get the language used by this numberer
     */

    public String getLanguage() {
        return language;
    }

    /**
     * Get the country used by this numberer.
     *
     * @return the country used by this numberer, or null if no country has been set
     */

    @Override
    public String getCountry() {
        return country;
    }

    /**
     * Format a number into a string. This method is provided for backwards compatibility. It merely
     * calls the other format method after constructing a RegularGroupFormatter. The method is final;
     * localization subclasses should implement the method
     * {@link net.sf.saxon.lib.Numberer#format(long, UnicodeString, NumericGroupFormatter, String, String)} rather than this method.
     *
     * @param number         The number to be formatted
     * @param picture        The format token. This is a single component of the format attribute
     *                       of xsl:number, e.g. "1", "01", "i", or "a"
     * @param groupSize      number of digits per group (0 implies no grouping)
     * @param groupSeparator string to appear between groups of digits
     * @param letterValue    The letter-value specified to xsl:number: "alphabetic" or
     *                       "traditional". Can also be an empty string or null.
     * @param ordinal        The value of the ordinal attribute specified to xsl:number
     *                       The value "yes" indicates that ordinal numbers should be used; "" or null indicates
     *                       that cardinal numbers
     * @return the formatted number. Note that no errors are reported; if the request
     *         is invalid, the number is formatted as if the string() function were used.
     */

    @Override
    public final String format(long number,
                               UnicodeString picture,
                               int groupSize,
                               String groupSeparator,
                               String letterValue,
                               String ordinal) {

        return format(number, picture,
                new RegularGroupFormatter(groupSize, groupSeparator, EmptyString.THE_INSTANCE),
                letterValue, ordinal);
    }

    /**
     * Format a number into a string
     *
     * @param number            The number to be formatted
     * @param picture           The format token. This is a single component of the format attribute
     *                          of xsl:number, e.g. "1", "01", "i", or "a"
     * @param numGroupFormatter object contains separators to appear between groups of digits
     * @param letterValue       The letter-value specified to xsl:number: "alphabetic" or
     *                          "traditional". Can also be an empty string or null.
     * @param ordinal           The value of the ordinal attribute specified to xsl:number
     *                          The value "yes" indicates that ordinal numbers should be used; "" or null indicates
     *                          cardinal numbers. A value such as "-er" indicates ordinal with a particular gender.
     * @return the formatted number. Note that no errors are reported; if the request
     *         is invalid, the number is formatted as if the string() function were used.
     */
    @Override
    public String format(long number,
                         /*@Nullable*/ UnicodeString picture,
                         NumericGroupFormatter numGroupFormatter,
                         String letterValue,
                         String ordinal) {


        if (number < 0) {
            return "" + number;
        }
        if (picture == null || picture.uLength() == 0) {
            return "" + number;
        }

        int pictureLength = picture.uLength();
        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C16);
        int formchar = picture.uCharAt(0);

        FastStringBuffer fsb = new FastStringBuffer(2);

        switch (formchar) {

            case '0':
            case '1':
                sb.append(toRadical(number, westernDigits, pictureLength, numGroupFormatter));
                if (ordinal != null && !ordinal.isEmpty()) {
                    sb.append(ordinalSuffix(ordinal, number));
                }
                break;

            case 'A':
                if (number == 0) {
                    return "0";
                }
                return toAlphaSequence(number, latinUpper);

            case 'a':
                if (number == 0) {
                    return "0";
                }
                return toAlphaSequence(number, latinLower);

            case 'w':
            case 'W':
                int wordCase;
                if (picture.uLength() == 1) {
                    if (formchar == 'W') {
                        wordCase = UPPER_CASE;
                    } else /*if (formchar == 'w')*/ {
                        wordCase = LOWER_CASE;
                    }
                } else {
                    // includes cases like "ww" or "Wz". The action here is conformant, but it's not clear what's best
                    wordCase = TITLE_CASE;
                }
                if (ordinal != null && !ordinal.isEmpty()) {
                    return toOrdinalWords(ordinal, number, wordCase);
                } else {
                    return toWords(number, wordCase);
                }

            case 'i':
                if (number == 0) {
                    return "0";
                }
                if (letterValue == null || letterValue.isEmpty() ||
                    letterValue.equals("traditional")) {
                    return toRoman(number);
                } else {
                    alphaDefault(number, 'i', sb);
                }
                break;

            case 'I':
                if (number == 0) {
                    return "0";
                }
                if (letterValue == null || letterValue.isEmpty() ||
                    letterValue.equals("traditional")) {
                    return toRoman(number).toUpperCase();
                } else {
                    alphaDefault(number, 'I', sb);
                }
                break;

            case '\u2460':
                // circled digits
                if (number == 0) {
                    return "" + (char) 0x24EA;
                }
                if (number > 20 && number <= 35) {
                    return "" + (char) (0x3251 + number - 21);
                }
                if (number > 35 && number <= 50) {
                    return "" + (char) (0x32B1 + number - 36);
                }
                if (number > 50) {
                    return "" + number;
                }
                return "" + (char) (0x2460 + number - 1);

            case '\u2474':
                // parenthesized digits
                if (number == 0 || number > 20) {
                    return "" + number;
                }
                return "" + (char) (0x2474 + number - 1);

            case '\u2488':
                // digit full stop
                if (number == 0) {
                    return "" + (char) 0xD83C + (char) 0xDD00;
                }
                if (number > 20) {
                    return "" + number;
                }
                return "" + (char) (0x2488 + number - 1);

            case '\u2776':
                // dingbat negative circled digits
                if (number == 0) {
                    return "" + (char) 0x24FF;
                }
                if (number > 10 && number <= 20) {
                    return "" + (char) (0x24EB + number - 11);
                }
                if (number > 20) {
                    return "" + number;
                }
                return "" + (char) (0x2776 + number - 1);

            case '\u2780':
                // double circled sans-serif digits
                if (number == 0) {
                    return "" + (char) 0xD83C + (char) 0xDD0B;
                }
                if (number > 10) {
                    return "" + number;
                }
                return "" + (char) (0x2780 + number - 1);

            case '\u24F5':
                // double circled digits
                if (number == 0 || number > 10) {
                    return "" + number;
                }
                return "" + (char) (0x24F5 + number - 1);

            case '\u278A':
                // dingbat negative circled sans-serif digits
                if (number == 0) {
                    return "" + (char) 0xD83C + (char) 0xDD0C;
                }
                if (number > 10) {
                    return "" + number;
                }
                return "" + (char) (0x278A + number - 1);

            case '\u3220':
                // parenthesized ideograph
                if (number == 0 || number > 10) {
                    return "" + number;
                }
                return "" + (char) (0x3220 + number - 1);

            case '\u3280':
                // circled ideograph
                if (number == 0 || number > 10) {
                    return "" + number;
                }
                return "" + (char) (0x3280 + number - 1);

            case 65799: //'\uD800\uDD07': \uD807\uDD27 hex 10107
                // aegean number
                if (number == 0 || number > 10) {
                    return "" + number;
                }
                fsb.appendWideChar(65799 + (int) number - 1);
                return fsb.toString();

            case 69216:
                // rumi digit
                if (number == 0 || number > 10) {
                    return "" + number;
                }
                fsb.appendWideChar(69216 + (int) number - 1);
                return fsb.toString();

            case 69714:
                // brahmi digit
                if (number == 0 || number > 10) {
                    return "" + number;
                }
                fsb.appendWideChar(69714 + (int) number - 1);
                return fsb.toString();

            case 119648:
                // counting rod unit digit
                if (number == 0 || number >= 10) {
                    return "" + number;
                }
                fsb.appendWideChar(119648 + (int) number - 1);
                return fsb.toString();

            case 127234:
                // digit one comma
                if (number == 0) {
                    fsb.appendWideChar(127233);
                    return fsb.toString();
                }
                if (number >= 10) {
                    return "" + number;
                }
                fsb.appendWideChar(127234 + (int) number - 1);
                return fsb.toString();

            case '\u0391':
                if (number == 0) {
                    return "0";
                }
                return toAlphaSequence(number, greekUpper);

            case '\u03b1':
                if (number == 0) {
                    return "0";
                }
                return toAlphaSequence(number, greekLower);

            case '\u0410':
                if (number == 0) {
                    return "0";
                }
                return toAlphaSequence(number, cyrillicUpper);

            case '\u0430':
                if (number == 0) {
                    return "0";
                }
                return toAlphaSequence(number, cyrillicLower);

            case '\u05d0':
                if (number == 0) {
                    return "0";
                }
                return toAlphaSequence(number, hebrew);

            case '\u3042':
                if (number == 0) {
                    return "0";
                }
                return toAlphaSequence(number, hiraganaA);

            case '\u30a2':
                if (number == 0) {
                    return "0";
                }
                return toAlphaSequence(number, katakanaA);

            case '\u3044':
                if (number == 0) {
                    return "0";
                }
                return toAlphaSequence(number, hiraganaI);

            case '\u30a4':
                if (number == 0) {
                    return "0";
                }
                return toAlphaSequence(number, katakanaI);

            case '\u4e00':
                return toJapanese(number);

            default:

                int digitValue = Alphanumeric.getDigitValue(formchar);
                if (digitValue >= 0) {

                    int zero = formchar - digitValue;
                    int[] digits = new int[10];
                    for (int z = 0; z <= 9; z++) {
                        digits[z] = zero + z;
                    }

                    return toRadical(number, digits, pictureLength, numGroupFormatter);

                } else {
                    if (formchar < '\u1100' && Character.isLetter((char) formchar) && number > 0) {
                        alphaDefault(number, (char) formchar, sb);
                    } else {
                        // fallback to western numbering
                        sb.append(toRadical(number, westernDigits, pictureLength, numGroupFormatter));
                        if (ordinal != null && !ordinal.isEmpty()) {
                            sb.append(ordinalSuffix(ordinal, number));
                        }
                        //return toRadical(number, westernDigits, pictureLength, numGroupFormatter);
                    }
                    break;

                }
        }

        return sb.toString();
    }

    /**
     * Construct the ordinal suffix for a number, for example "st", "nd", "rd". The default
     * (language-neutral) implementation returns a zero-length string
     *
     * @param ordinalParam the value of the ordinal attribute (used in non-English
     *                     language implementations)
     * @param number       the number being formatted
     * @return the ordinal suffix to be appended to the formatted number
     */

    protected String ordinalSuffix(String ordinalParam, long number) {
        return "";
    }

    protected static final int[] westernDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

    protected static final String latinUpper =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    protected static final String latinLower =
            "abcdefghijklmnopqrstuvwxyz";

    protected static final String greekUpper =
            "\u0391\u0392\u0393\u0394\u0395\u0396\u0397\u0398\u0399\u039a" +
                    "\u039b\u039c\u039d\u039e\u039f\u03a0\u03a1\u03a2\u03a3\u03a4" +
                    "\u03a5\u03a6\u03a7\u03a8\u03a9";

    protected static final String greekLower =
            "\u03b1\u03b2\u03b3\u03b4\u03b5\u03b6\u03b7\u03b8\u03b9\u03ba" +
                    "\u03bb\u03bc\u03bd\u03be\u03bf\u03c0\u03c1\u03c2\u03c3\u03c4" +
                    "\u03c5\u03c6\u03c7\u03c8\u03c9";

    // Cyrillic information from Dmitry Kirsanov [dmitry@kirsanov.com]
    // (based on his personal knowledge of Russian texts, not any authoritative source)

    protected static final String cyrillicUpper =
            "\u0410\u0411\u0412\u0413\u0414\u0415\u0416\u0417\u0418" +
                    "\u041a\u041b\u041c\u041d\u041e\u041f\u0420\u0421\u0421\u0423" +
                    "\u0424\u0425\u0426\u0427\u0428\u0429\u042b\u042d\u042e\u042f";

    protected static final String cyrillicLower =
            "\u0430\u0431\u0432\u0433\u0434\u0435\u0436\u0437\u0438" +
                    "\u043a\u043b\u043c\u043d\u043e\u043f\u0440\u0441\u0441\u0443" +
                    "\u0444\u0445\u0446\u0447\u0448\u0449\u044b\u044d\u044e\u044f";

    protected static final String hebrew =
            "\u05d0\u05d1\u05d2\u05d3\u05d4\u05d5\u05d6\u05d7\u05d8\u05d9\u05db\u05dc" +
                    "\u05de\u05e0\u05e1\u05e2\u05e4\u05e6\u05e7\u05e8\u05e9\u05ea";


    // The following Japanese sequences were supplied by
    // MURAKAMI Shinyu [murakami@nadita.com]

    protected static final String hiraganaA =
            "\u3042\u3044\u3046\u3048\u304a\u304b\u304d\u304f\u3051\u3053" +
                    "\u3055\u3057\u3059\u305b\u305d\u305f\u3061\u3064\u3066\u3068" +
                    "\u306a\u306b\u306c\u306d\u306e\u306f\u3072\u3075\u3078\u307b" +
                    "\u307e\u307f\u3080\u3081\u3082\u3084\u3086\u3088\u3089\u308a" +
                    "\u308b\u308c\u308d\u308f\u3092\u3093";

    protected static final String katakanaA =

            "\u30a2\u30a4\u30a6\u30a8\u30aa\u30ab\u30ad\u30af\u30b1\u30b3" +
                    "\u30b5\u30b7\u30b9\u30bb\u30bd\u30bf\u30c1\u30c4\u30c6\u30c8" +
                    "\u30ca\u30cb\u30cc\u30cd\u30ce\u30cf\u30d2\u30d5\u30d8\u30db" +
                    "\u30de\u30df\u30e0\u30e1\u30e2\u30e4\u30e6\u30e8\u30e9\u30ea" +
                    "\u30eb\u30ec\u30ed\u30ef\u30f2\u30f3";

    protected static final String hiraganaI =

            "\u3044\u308d\u306f\u306b\u307b\u3078\u3068\u3061\u308a\u306c" +
                    "\u308b\u3092\u308f\u304b\u3088\u305f\u308c\u305d\u3064\u306d" +
                    "\u306a\u3089\u3080\u3046\u3090\u306e\u304a\u304f\u3084\u307e" +
                    "\u3051\u3075\u3053\u3048\u3066\u3042\u3055\u304d\u3086\u3081" +
                    "\u307f\u3057\u3091\u3072\u3082\u305b\u3059";

    protected static final String katakanaI =

            "\u30a4\u30ed\u30cf\u30cb\u30db\u30d8\u30c8\u30c1\u30ea\u30cc" +
                    "\u30eb\u30f2\u30ef\u30ab\u30e8\u30bf\u30ec\u30bd\u30c4\u30cd" +
                    "\u30ca\u30e9\u30e0\u30a6\u30f0\u30ce\u30aa\u30af\u30e4\u30de" +
                    "\u30b1\u30d5\u30b3\u30a8\u30c6\u30a2\u30b5\u30ad\u30e6\u30e1" +
                    "\u30df\u30b7\u30f1\u30d2\u30e2\u30bb\u30b9";


    /**
     * Default processing with an alphabetic format token: use the contiguous
     * range of Unicode letters starting with that token.
     *
     * @param number   the number to be formatted
     * @param formchar the format character, for example 'A' for the numbering sequence A,B,C
     * @param sb       buffer to hold the result of the formatting
     */

    protected void alphaDefault(long number, char formchar, FastStringBuffer sb) {
        int min = (int) formchar;
        int max = (int) formchar;
        // use the contiguous range of letters starting with the specified one
        while (Character.isLetterOrDigit((char) (max + 1))) {
            max++;
        }
        sb.append(toAlpha(number, min, max));
    }

    /**
     * Format the number as an alphabetic label using the alphabet consisting
     * of consecutive Unicode characters from min to max
     *
     * @param number the number to be formatted
     * @param min    the start of the Unicode codepoint range
     * @param max    the end of the Unicode codepoint range
     * @return the formatted number
     */

    protected String toAlpha(long number, int min, int max) {
        if (number <= 0) {
            return "" + number;
        }
        int range = max - min + 1;
        char last = (char) (((number - 1) % range) + min);
        if (number > range) {
            return toAlpha((number - 1) / range, min, max) + last;
        } else {
            return "" + last;
        }
    }

    /**
     * Convert the number into an alphabetic label using a given alphabet.
     * For example, if the alphabet is "xyz" the sequence is x, y, z, xx, xy, xz, ....
     *
     * @param number   the number to be formatted
     * @param alphabet a string containing the characters to be used, for example "abc...xyz"
     * @return the formatted number
     */

    protected String toAlphaSequence(long number, String alphabet) {
        if (number <= 0) {
            return "" + number;
        }
        int range = alphabet.length();
        char last = alphabet.charAt((int) ((number - 1) % range));
        if (number > range) {
            return toAlphaSequence((number - 1) / range, alphabet) + last;
        } else {
            return "" + last;
        }
    }

    /**
     * Convert the number into a decimal or other representation using the given set of
     * digits.
     * For example, if the digits are "01" the sequence is 1, 10, 11, 100, 101, 110, 111, ...
     * More commonly, the digits will be "0123456789", giving the usual decimal numbering.
     *
     * @param number            the number to be formatted
     * @param digits            the codepoints to be used for the digits
     * @param pictureLength     the length of the picture that is significant: for example "3" if the
     *                          picture is "001"
     * @param numGroupFormatter an object that encapsulates the rules for inserting grouping separators
     * @return the formatted number
     */

    private String toRadical(long number, int[] digits, int pictureLength,
                             NumericGroupFormatter numGroupFormatter) {

        FastStringBuffer temp = convertDigitSystem(number, digits, pictureLength);

        if (numGroupFormatter == null) {
            return temp.toString();
        }

        return numGroupFormatter.format(temp);
    }

    /**
     * Convert a number to use a given set of digits, to a required length.
     * For example, if the digits are "01" the sequence is 1, 10, 11, 100, 101, 110, 111, ...
     * More commonly, the digits will be "0123456789", giving the usual decimal numbering.
     *
     * @param number            the number to be formatted
     * @param digits            the codepoints to be used for the digits
     * @param requiredLength    the length of the picture that is significant: for example "3" if the
     *                          picture is "001"
     * @return the converted number
     */

    public static FastStringBuffer convertDigitSystem(long number, int[] digits, int requiredLength) {
        FastStringBuffer temp = new FastStringBuffer(FastStringBuffer.C16);
        int base = digits.length;
        FastStringBuffer s = new FastStringBuffer(FastStringBuffer.C16);
        long n = number;
        int count = 0;
        while (n > 0) {
            int digit = digits[(int) (n % base)];
            s.prependWideChar(digit);
            count++;
            n = n / base;
        }

        for (int i = 0; i < (requiredLength - count); i++) {
            temp.appendWideChar(digits[0]);
        }
        temp.append(s);
        return temp;
    }

    /**
     * Generate a Roman numeral (in lower case)
     *
     * @param n the number to be formatted
     * @return the Roman numeral representation of the number in lower case
     */

    public static String toRoman(long n) {
        if (n <= 0 || n > 9999) {
            return "" + n;
        }
        return romanThousands[(int) n / 1000] +
                romanHundreds[((int) n / 100) % 10] +
                romanTens[((int) n / 10) % 10] +
                romanUnits[(int) n % 10];
    }

    // Roman numbers beyond 4000 use overlining and other conventions which we won't
    // attempt to reproduce. We'll go high enough to handle present-day Gregorian years.

    private static String[] romanThousands =
            {"", "m", "mm", "mmm", "mmmm", "mmmmm", "mmmmmm", "mmmmmmm", "mmmmmmmm", "mmmmmmmmm"};
    private static String[] romanHundreds =
            {"", "c", "cc", "ccc", "cd", "d", "dc", "dcc", "dccc", "cm"};
    private static String[] romanTens =
            {"", "x", "xx", "xxx", "xl", "l", "lx", "lxx", "lxxx", "xc"};
    private static String[] romanUnits =
            {"", "i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix"};


    /**
     * Format the number in Japanese.
     *
     * @param number the number to be formatted: formatted in Western decimal style unless in the range 1 to 9999
     * @return the Japanese Kanji representation of the number if in the range 1-9999
     */

    public String toJapanese(long number) {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C16);
        if (number == 0) {
            fsb.appendWideChar(0x3007);
        } else if (number <= 9999) {
            toJapanese((int) number, fsb, false);
        } else {
            fsb.append("" + number);
        }
        return fsb.toString();
    }

    /**
     * Format the number in Japanese.
     *
     * @param nr        the number to be formatted: must be in the range 1 to 9999 (or 0 on a recursive call)
     * @param fsb       buffer to receive the formatted number as the Japanese Kanji representation of the number in lower case
     * @param isInitial true except on a recursive call
     */

    private static void toJapanese(int nr, FastStringBuffer fsb, boolean isInitial) {
        if (nr == 0) {
            // no action (not used at top level)
        } else if (nr <= 9) {
            if (!(nr == 1 && isInitial)) {
                fsb.appendWideChar(kanjiDigits[nr]);
            }
        } else if (nr == 10) {
            fsb.appendWideChar(0x5341);
        } else if (nr <= 99) {
            toJapanese(nr / 10, fsb, true);
            fsb.appendWideChar(0x5341);
            toJapanese(nr % 10, fsb, false);
        } else if (nr <= 999) {
            toJapanese(nr / 100, fsb, true);
            fsb.appendWideChar(0x767e);
            toJapanese(nr % 100, fsb, false);
        } else if (nr <= 9999) {
            toJapanese(nr / 1000, fsb, true);
            fsb.appendWideChar(0x5343);
            toJapanese(nr % 1000, fsb, false);
        }

    }

    private static final int[] kanjiDigits =
            {0x3007, 0x4e00, 0x4e8c, 0x4e09, 0x56db, 0x4e94, 0x516d, 0x4e03, 0x516b, 0x4e5d};

    /**
     * Show the number as words in title case. (We choose title case because
     * the result can then be converted algorithmically to lower case or upper case).
     *
     * @param number the number to be formatted
     * @return the number formatted as English words
     */

    public abstract String toWords(long number);

    /**
     * Format a number as English words with specified case options
     *
     * @param number   the number to be formatted
     * @param wordCase the required case for example {@link #UPPER_CASE},
     *                 {@link #LOWER_CASE}, {@link #TITLE_CASE}
     * @return the formatted number
     */

    public String toWords(long number, int wordCase) {
        String s;
        if (number == 0) {
            s = "Zero";
        } else {
            s = toWords(number);
        }
        switch (wordCase) {
            case UPPER_CASE:
                return s.toUpperCase();
            case LOWER_CASE:
                return s.toLowerCase();
            default:
                return s;
        }
    }

    /**
     * Show an ordinal number as English words in a requested case (for example, Twentyfirst)
     *
     * @param ordinalParam the value of the "ordinal" attribute as supplied by the user
     * @param number       the number to be formatted
     * @param wordCase     the required case for example {@link #UPPER_CASE},
     *                     {@link #LOWER_CASE}, {@link #TITLE_CASE}
     * @return the formatted number
     */

    public abstract String toOrdinalWords(String ordinalParam, long number, int wordCase);

    /**
     * Get a month name or abbreviation
     *
     * @param month    The month number (1=January, 12=December)
     * @param minWidth The minimum number of characters
     * @param maxWidth The maximum number of characters
     */

    @Override
    public abstract String monthName(int month, int minWidth, int maxWidth);

    /**
     * Get a day name or abbreviation
     *
     * @param day      The day of the week (1=Monday, 7=Sunday)
     * @param minWidth The minimum number of characters
     * @param maxWidth The maximum number of characters
     */

    @Override
    public abstract String dayName(int day, int minWidth, int maxWidth);

    /**
     * Get an am/pm indicator. Default implementation works for English, on the basis that some
     * other languages might like to copy this (most non-English countries don't actually use the
     * 12-hour clock, so it's irrelevant).
     *
     * @param minutes  the minutes within the day
     * @param minWidth minimum width of output
     * @param maxWidth maximum width of output
     * @return the AM or PM indicator
     */

    @Override
    public String halfDayName(int minutes, int minWidth, int maxWidth) {
        String s;
        if (minutes == 0 && maxWidth >= 8 && "gb".equals(country)) {
            s = "Midnight";
        } else if (minutes < 12 * 60) {
            switch (maxWidth) {
                case 1:
                    s = "A";
                    break;
                case 2:
                case 3:
                    s = "Am";
                    break;
                default:
                    s = "A.M.";
            }
        } else if (minutes == 12 * 60 && maxWidth >= 8 && "gb".equals(country)) {
            s = "Noon";
        } else {
            switch (maxWidth) {
                case 1:
                    s = "P";
                    break;
                case 2:
                case 3:
                    s = "Pm";
                    break;
                default:
                    s = "P.M.";
            }
        }
        return s;
    }

    /**
     * Get an ordinal suffix for a particular component of a date/time.
     *
     * @param component the component specifier from a format-dateTime picture, for
     *                  example "M" for the month or "D" for the day.
     * @return a string that is acceptable in the ordinal attribute of xsl:number
     *         to achieve the required ordinal representation. For example, "-e" for the day component
     *         in German, to have the day represented as "dritte August".
     */

    @Override
    public String getOrdinalSuffixForDateTime(String component) {
        return "yes";
    }

    /**
     * Get the name for an era (e.g. "BC" or "AD")
     *
     * @param year the proleptic gregorian year, using "0" for the year before 1AD
     */

    @Override
    public String getEraName(int year) {
        return year > 0 ? "AD" : "BC";
    }

    /**
     * Get the name of a calendar
     *
     * @param code The code representing the calendar as in the XSLT 2.0 spec, e.g. AD for the Gregorian calendar
     */

    @Override
    public String getCalendarName(String code) {
        if (code.equals("AD")) {
            return "Gregorian";
        } else {
            return code;
        }
    }


}

