////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.local;


/*
 Turkish numberer implementation.
 by Ferhat SAVCI (ferhat dot savci at gmail dot com)
 */

/*
 Email from author to Michael Kay dated 2014-02-21:
 Please find the Java code attached. The file is encoded in UTF-8, and you will need to preserve that
 in order to keep the Turkish letters in string literals in the code correct.
 I agree to release it under the Mozilla Public License or under any other recognized open
 source license that Saxon might adopt in the future.
 */


import net.sf.saxon.expr.number.AbstractNumberer;

import java.util.Locale;

/**
 *
 * @author Ferhat
 */
public class Numberer_tr extends AbstractNumberer {

    private final String[] birler = {"sıfır", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz"};
    private final String[] onlar = {"", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan"};
    private final String[] ten2the3rds = {"kentilyon", "katrilyon", "trilyon", "milyar", "milyon", "bin"};

    private final Locale turkish = new Locale("tr");

    public String formatWord(String word, int formatIdx) {
        switch (formatIdx) {
            case UPPER_CASE:
                return word.toUpperCase(turkish);
            case TITLE_CASE:
                return word.substring(0, 1).toUpperCase(turkish) + word.substring(1);
        }

        return word.toLowerCase(turkish);
    }

    public String numberer(long number, int format) {
        if (number < 10) {
            return birler[(int) number];
        }

        StringBuffer sb = new StringBuffer(200);
        int grup = 0;

        for (long bolen = 1000000000000000000L; bolen > 100; bolen /= 1000) {
            int bolum = (int) (number / bolen);
            number = number - bolum * bolen;
            if (bolum > 0) {
                int yuz = bolum / 100;
                int on = bolum / 10 % 10;
                int bir = bolum % 10;
                if (yuz != 0) {
                    if (yuz != 1) {
                        sb.append(formatWord(birler[yuz], format));
                        sb.append(' ');
                    }
                    sb.append(formatWord("yüz", format));
                    sb.append(' ');
                }
                if (on != 0) {
                    sb.append(formatWord(onlar[on], format));
                    sb.append(' ');
                }
                if (bir != 0) {
                    if (bolen > 1000 || bir > 1 || yuz != 0 || on != 0) {
                        sb.append(formatWord(birler[bir], format));
                        sb.append(' ');
                    }
                }
                sb.append(formatWord(ten2the3rds[grup], format));
                sb.append(' ');
            }

            grup++;
        }

        if (number > 0) {
            int yuz = (int) (number / 100);
            int on = (int) (number / 10 % 10);
            int bir = (int) (number % 10);
            if (yuz != 0) {
                if (yuz != 1) {
                    sb.append(formatWord(birler[yuz], format));
                    sb.append(' ');
                }
                sb.append(formatWord("yüz", format));
                sb.append(' ');
            }
            if (on != 0) {
                sb.append(formatWord(onlar[on], format));
                sb.append(' ');
            }
            if (bir != 0) {
                sb.append(formatWord(birler[bir], format));
                sb.append(' ');
            }
        }

        return sb.toString().trim();
    }

    @Override
    public String toWords(long l) {
        return numberer(l, LOWER_CASE);
    }

    @Override
    public String toWords(long l, int format) {
        return numberer(l, format);
    }

    @Override
    public String toOrdinalWords(String string, long l, int format) {
        String number = numberer(l, format);

        String[] lookFor = {"sıfır", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan", "yüz", "kentilyon", "katrilyon", "trilyon", "milyar", "milyon", "bin"};
        String[] replaceWith = {"sıfırıncı", "birinci", "ikinci", "üçüncü", "dördüncü", "beşinci", "altıncı", "yedinci", "sekizinci", "dokuzuncu", "onuncu", "yirminci", "otuzuncu", "kırkıncı", "ellinci", "altmışıncı", "yetmişinci", "sekseninci", "doksanıncı", "yüzüncü", "kentilyonuncu", "katrilyonuncu", "trilyonuncu", "milyarıncı", "milyonuncu", "bininci"};

        int idx = number.lastIndexOf(' ');
        if (idx < 0) { // single word
            idx = 0;
        }

        String lastWord = number.substring(idx + 1);
        int found = -1;
        for (int w = 0; w < lookFor.length; w++) {
            if (lookFor[w].toLowerCase(turkish).equals(lastWord.toLowerCase(turkish))) {
                found = w;
                break;
            }
        }

        if (found == -1) { // WTF?
            return number;
        }

        return number.substring(0, idx + 1) + formatWord(replaceWith[found], format);
    }

    @Override
    public String monthName(int ay, int min, int max) {
        String[] aylar = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs",
            "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};

        if (ay < 1 || ay > 12) {
            return "";
        }

        StringBuffer ayAdi = new StringBuffer(abbreviate(aylar[ay - 1], max));
        while (ayAdi.length() < min) {
            ayAdi.append(' ');
        }

        return ayAdi.toString();
    }

    @Override
    public String dayName(int gun, int min, int max) {
        String[] gunler = {"Pazartesi", "Salı", "Çarşamba", "Perşembe",
            "Cuma", "Cumartesi", "Pazar"};

        if (gun < 1 || gun > 7) {
            return "";
        }

        StringBuffer gunAdi = new StringBuffer(abbreviate(gunler[gun - 1], max));
        while (gunAdi.length() < min) {
            gunAdi.append(' ');
        }

        return gunAdi.toString();
    }

    private String abbreviate(String string, int max) {
        if (string.length() <= max) {
            return string;
        }

        // first try: camelBump the string -- no length to max comparison
        string = camelBump(string);
        if (string.length() <= max) {
            return string;
        }

        // second try: remove vowels (except capitalized vowels -- they are the "bump"s) till length <= max
        string = removeVowels(string, max);
        if (string.length() <= max) {
            return string;
        }

        // third try: remove consonants (except capitalized consonants -- they are the "bump"s) till length <= max
        string = removeConsonants(string, max);
        if (string.length() <= max) {
            return string;
        }

        // last ditch: remaining chars are capitalized, chop string after position=max
        return string.substring(0, max);
    }

    public String camelBump(String string) {
        // CamelBump the words first thing
        char[] chars = string.trim().toCharArray();
        // Capitalize every word and delete spaces
        char[] camelBump = new char[chars.length];
        int n = 0;
        boolean precedingSpace = true; // First character should be capitalized
        for (int c = 0; c < chars.length; c++) {
            if (Character.isWhitespace(chars[c])) {
                precedingSpace = true;
                continue;
            }
            if (precedingSpace) {
                String toUp = new String(new char[]{chars[c]});
                toUp = toUp.toUpperCase(turkish);
                camelBump[n++] = toUp.charAt(0);
            } else {
                camelBump[n++] = chars[c];
            }

            precedingSpace = false;
        }

        string = new String(camelBump, 0, n);

        return string;
    }

    private String removeVowels(String string, int max) {
        int diff = string.length() - max;
        String toRemove = "aeıioöuü";
        StringBuffer sb = new StringBuffer();
        string = reverse(string);
        for (int c = 0; c < string.length(); c++) {
            if (diff <= 0) {
                sb.append(string.charAt(c));
                continue;
            }
            if (toRemove.indexOf(string.charAt(c)) == -1) {
                sb.append(string.charAt(c));
            } else {
                diff--;
            }
        }

        return reverse(sb.toString());
    }

    private String removeConsonants(String string, int max) {
        int diff = string.length() - max;
        String toRemove = "bcçdfgğhjklmnprsştvyz";
        StringBuffer sb = new StringBuffer();
        string = reverse(string);
        for (int c = 0; c < string.length(); c++) {
            if (diff <= 0) {
                sb.append(string.charAt(c));
                continue;
            }
            if (toRemove.indexOf(string.charAt(c)) == -1) {
                sb.append(string.charAt(c));
            } else {
                diff--;
            }
        }

        return reverse(sb.toString());
    }

    private String reverse(String toString) {
        char[] rev = new char[toString.length()];
        for (int c = 0; c < rev.length; c++) {
            rev[rev.length - (c + 1)] = toString.charAt(c);
        }

        return new String(rev);
    }

    public static void main(String[] args) {
        // Test cases
        Numberer_tr tn = new Numberer_tr();

        System.out.println("Running some tests...");

        System.out.println(Long.MAX_VALUE + " (Long.MAX_VALUE) in words: '" + tn.toWords(Long.MAX_VALUE, TITLE_CASE) + "'");
        System.out.println(4425408 + " in words: '" + tn.toWords(4425408, TITLE_CASE) + "'");
        System.out.println(5323156550L + " in words: '" + tn.toWords(5323156550L, TITLE_CASE) + "'");
        System.out.println(10101010101L + " in words: '" + tn.toWords(10101010101L, TITLE_CASE) + "'");
        System.out.println(101010101010L + " in words: '" + tn.toWords(101010101010L, TITLE_CASE) + "'");
        System.out.println(111111111111L + " in words: '" + tn.toWords(111111111111L, TITLE_CASE) + "'");
        System.out.println(156156L + " in words: '" + tn.toWords(156156L, TITLE_CASE) + "'");

        System.out.println("");

        System.out.println(Long.MAX_VALUE + " (Long.MAX_VALUE) in ordinal words: '" + tn.toOrdinalWords("", Long.MAX_VALUE, TITLE_CASE) + "'");
        System.out.println(4425408 + " in ordinal words: '" + tn.toOrdinalWords("", 4425408, TITLE_CASE) + "'");
        System.out.println(5323156550L + " in ordinal words: '" + tn.toOrdinalWords("", 5323156550L, TITLE_CASE) + "'");
        System.out.println(10101010101L + " in ordinal words: '" + tn.toOrdinalWords("", 10101010101L, TITLE_CASE) + "'");
        System.out.println(101010101010L + " in ordinal words: '" + tn.toOrdinalWords("", 101010101010L, TITLE_CASE) + "'");
        System.out.println(111111111111L + " in ordinal words: '" + tn.toOrdinalWords("", 111111111111L, TITLE_CASE) + "'");
        System.out.println(156156L + " in ordinal words: '" + tn.toOrdinalWords("", 156156L, TITLE_CASE) + "'");

        System.out.println("");

        for (int month = 1; month <= 12; month++) {
            System.out.println("month " + month + " in 4 characters, minimum 5 characters wide result: '" + tn.monthName(month, 5, 4) + "'");
            System.out.println("month " + month + " in 5 characters, minimum 4 characters wide result: '" + tn.monthName(month, 4, 5) + "'");
            System.out.println("month " + month + " in 10 characters, minimum 10 characters wide result: '" + tn.monthName(month, 10, 10) + "'");
        }

        System.out.println("");

        for (int day = 1; day <= 7; day++) {
            System.out.println("day " + day + " in 4 characters, minimum 5 characters wide result: '" + tn.dayName(day, 5, 4) + "'");
            System.out.println("day " + day + " in 5 characters, minimum 4 characters wide result: '" + tn.dayName(day, 4, 5) + "'");
            System.out.println("day " + day + " in 10 characters, minimum 10 characters wide result: '" + tn.dayName(day, 10, 10) + "'");
        }

        System.out.println("");

        System.out.println("abbreviating 156156 in (lower case) words to 18 characters: '" + tn.abbreviate(tn.numberer(156156L, LOWER_CASE), 18) + "'");
        System.out.println("abbreviating 156156 in (upper case) words to 18 characters: '" + tn.abbreviate(tn.numberer(156156L, UPPER_CASE), 18) + "'");
        System.out.println("abbreviating 156156 in (title case) words to 18 characters: '" + tn.abbreviate(tn.numberer(156156L, TITLE_CASE), 18) + "'");
    }
}
