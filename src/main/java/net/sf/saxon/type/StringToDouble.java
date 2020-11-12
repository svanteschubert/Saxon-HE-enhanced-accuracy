////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.trans.Err;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.Whitespace;

/**
 * This class converts a string to an xs:double according to the rules in XML Schema 1.0
 */
public class StringToDouble extends StringConverter {

    private static StringToDouble THE_INSTANCE = new StringToDouble();

    /**
     * Get the singleton instance
     *
     * @return the singleton instance of this class
     */

    public static StringToDouble getInstance() {
        return THE_INSTANCE;
    }

    protected StringToDouble() {
    }

    /**
     * Convert a string to a double.
     *
     * @param s the String to be converted
     * @return a double representing the value of the String
     * @throws NumberFormatException if the value cannot be converted
     */

    public double stringToNumber(CharSequence s) throws NumberFormatException {
        // first try to parse simple numbers by hand (it's cheaper)
        int len = s.length();
        boolean containsDisallowedChars = false;
        boolean containsWhitespace = false;
        if (len < 9) {
            boolean useJava = false;
            long num = 0;
            int dot = -1;
            int lastDigit = -1;
            boolean onlySpaceAllowed = false;
            loop:
            for (int i = 0; i < len; i++) {
                char c = s.charAt(i);
                switch (c) {
                    case ' ':
                    case '\n':
                    case '\t':
                    case '\r':
                        containsWhitespace = true;
                        if (lastDigit != -1) {
                            onlySpaceAllowed = true;
                        }
                        break;
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        if (onlySpaceAllowed) {
                            throw new NumberFormatException("Numeric value contains embedded whitespace");
                        }
                        lastDigit = i;
                        num = num * 10 + (c - '0');
                        break;
                    case '.':
                        if (onlySpaceAllowed) {
                            throw new NumberFormatException("Numeric value contains embedded whitespace");
                        }
                        if (dot != -1) {
                            throw new NumberFormatException("Only one decimal point allowed");
                        }
                        dot = i;
                        break;
                    case 'x':
                    case 'X':
                    case 'f':
                    case 'F':
                    case 'd':
                    case 'D':
                    case 'n':
                    case 'N':
                        containsDisallowedChars = true;
                        useJava = true;
                        break loop;
                    default:
                        // there's something like a sign or an exponent: take the slow train instead
                        // But keep going to look for disallowed characters - bug 3495
                        useJava = true;
                        break;
                }
            }
            if (!useJava) {
                if (lastDigit == -1) {
                    throw new NumberFormatException("String to double conversion: no digits found");
                } else if (dot == -1 || dot > lastDigit) {
                    return (double) num;
                } else {
                    int afterPoint = lastDigit - dot;
                    return (double) num / powers[afterPoint];
                }
            }
        } else {
            loop2:
            for (int i = 0; i < len; i++) {
                char c = s.charAt(i);
                switch (c) {
                    case ' ':
                    case '\n':
                    case '\t':
                    case '\r':
                        containsWhitespace = true;
                        break;
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                    case '.':
                    case 'e':
                    case 'E':
                    case '+':
                    case '-':
                        break;
                    default:
                        containsDisallowedChars = true;
                        break loop2;
                }
            }
        }
        String n = containsWhitespace ? Whitespace.trimWhitespace(s).toString() : s.toString();
        if ("INF".equals(n)) {
            return Double.POSITIVE_INFINITY;
        } else if ("+INF".equals(n)) {
            // Allowed in XSD 1.1 but not in XSD 1.0
            return signedPositiveInfinity();
        } else if ("-INF".equals(n)) {
            return Double.NEGATIVE_INFINITY;
        } else if ("NaN".equals(n)) {
            return Double.NaN;
        } else {
            // reject strings containing characters such as (x, f, d) allowed in Java but not in XPath,
            // and other representations of NaN and Infinity such as 'Infinity'
            if (containsDisallowedChars) {
                throw new NumberFormatException("invalid floating point value: " + s);
            }
            return Double.parseDouble(n);
        }
    }

    protected double signedPositiveInfinity() {
        throw new NumberFormatException("the float/double value '+INF' is not allowed under XSD 1.0");
    }

    /*@NotNull*/ private static double[] powers = new double[]{1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000};

    /**
     * Convert a string to the target type of this converter.
     *
     * @param input the string to be converted
     * @return either an {@link net.sf.saxon.value.AtomicValue} of the appropriate type for this converter (if conversion
     *         succeeded), or a {@link net.sf.saxon.type.ValidationFailure} if conversion failed.
     */
    @Override
    public ConversionResult convertString(CharSequence input) {
        try {
            double d = stringToNumber(input);
            return new DoubleValue(d);
        } catch (NumberFormatException e) {
            return new ValidationFailure("Cannot convert string " + Err.wrap(input, Err.VALUE) + " to double");
        }
    }

}

