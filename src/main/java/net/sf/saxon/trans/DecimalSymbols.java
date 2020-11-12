////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.z.IntHashMap;

import java.util.Arrays;

/**
 * This class is modelled on Java's DecimalFormatSymbols, but it allows the use of any
 * Unicode character to represent symbols such as the decimal point and the grouping
 * separator, whereas DecimalFormatSymbols restricts these to a char (1-65535).
 */
public class DecimalSymbols {

    public static final int DECIMAL_SEPARATOR = 0;
    public static final int GROUPING_SEPARATOR = 1;
    public static final int DIGIT = 2;
    public static final int MINUS_SIGN = 3;
    public static final int PERCENT = 4;
    public static final int PER_MILLE = 5;
    public static final int ZERO_DIGIT = 6;
    public static final int EXPONENT_SEPARATOR = 7;
    public static final int PATTERN_SEPARATOR = 8;
    public static final int INFINITY = 9;
    public static final int NAN = 10;

    private static final int ERR_NOT_SINGLE_CHAR = 0;
    private static final int ERR_NOT_UNICODE_DIGIT = 1;
    private static final int ERR_SAME_CHAR_IN_TWO_ROLES = 2;
    private static final int ERR_TWO_VALUES_FOR_SAME_PROPERTY = 3;

    private static String[] XSLT_CODES = {"XTSE0020", "XTSE1295", "XTSE1300", "XTSE1290"};
    private static String[] XQUERY_CODES = {"XQST0097", "XQST0097", "XQST0098", "XQST0114"};
    private String[] errorCodes = XSLT_CODES;


    private String infinityValue;
    private String NaNValue;

    public final static String[] propertyNames = {
        "decimal-separator",
        "grouping-separator",
        "digit",
        "minus-sign",
        "percent",
        "per-mille",
        "zero-digit",
        "exponent-separator",
        "pattern-separator",
        "infinity",
        "NaN"
    };

    private int[] intValues = new int[propertyNames.length - 2];
    private int[] precedences = new int[propertyNames.length];
    private boolean[] inconsistent = new boolean[propertyNames.length];

    /**
     * Create a DecimalSymbols object with default values for all properties
     */

    public DecimalSymbols(HostLanguage language, int languageLevel) {
        intValues[DECIMAL_SEPARATOR] = '.';
        intValues[GROUPING_SEPARATOR] = ',';
        intValues[DIGIT] = '#';
        intValues[MINUS_SIGN] = '-';
        intValues[PERCENT] = '%';
        intValues[PER_MILLE] = '\u2030';
        intValues[ZERO_DIGIT] = '0';
        intValues[EXPONENT_SEPARATOR] = 'e';
        intValues[PATTERN_SEPARATOR] = ';';
        infinityValue = "Infinity";
        NaNValue = "NaN";
        Arrays.fill(precedences, Integer.MIN_VALUE);
        setHostLanguage(language, languageLevel);
    }

    public void setHostLanguage(HostLanguage language, int languageLevel) {
        if (language == HostLanguage.XQUERY) {
            errorCodes = XQUERY_CODES;
        } else {
            errorCodes = XSLT_CODES;
        }
    }

    /**
     * Get the decimal separator value
     *
     * @return the decimal separator value that has been explicitly set, or its default ('.')
     */

    public int getDecimalSeparator() {
        return intValues[DECIMAL_SEPARATOR];
    }

    /**
     * Get the grouping separator value
     *
     * @return the grouping separator value that has been explicitly set, or its default (',')
     */

    public int getGroupingSeparator() {
        return intValues[GROUPING_SEPARATOR];
    }

    /**
     * Get the digit symbol value
     *
     * @return the digit symbol value that has been explicitly set, or its default ('#')
     */

    public int getDigit() {
        return intValues[DIGIT];
    }

    /**
     * Get the minus sign value
     *
     * @return the minus sign value that has been explicitly set, or its default ('-')
     */

    public int getMinusSign() {
        return intValues[MINUS_SIGN];
    }

    /**
     * Get the percent symbol value
     *
     * @return the percent symbol value that has been explicitly set, or its default ('%')
     */

    public int getPercent() {
        return intValues[PERCENT];
    }

    /**
     * Get the per-mille symbol value
     *
     * @return the per-mille symbol value that has been explicitly set, or its default
     */

    public int getPerMille() {
        return intValues[PER_MILLE];
    }

    /**
     * Get the zero digit symbol value
     *
     * @return the zero digit symbol value that has been explicitly set, or its default ('0')
     */

    public int getZeroDigit() {
        return intValues[ZERO_DIGIT];
    }

    /**
     * Get the exponent separator symbol
     * @return the exponent separator character that has been explicitly set, or its default ('e');
     */

    public int getExponentSeparator() { return intValues[EXPONENT_SEPARATOR]; }

    /**
     * Get the pattern separator value
     *
     * @return the pattern separator value that has been explicitly set, or its default (';')
     */

    public int getPatternSeparator() {
        return intValues[PATTERN_SEPARATOR];
    }

    /**
     * Get the infinity symbol value
     *
     * @return the infinity symbol value that has been explicitly set, or its default ('Infinity')
     */

    public String getInfinity() {
        return infinityValue;
    }

    /**
     * Get the NaN symbol value
     *
     * @return the NaN symbol value that has been explicitly set, or its default ('NaN')
     */

    public String getNaN() {
        return NaNValue;
    }

    /**
     * Set the character to be used as the decimal separator
     *
     * @param value the character to be used
     * @throws XPathException if the value is not a single Unicode character (a surrogate pair is permitted)
     */

    public void setDecimalSeparator(String value) throws XPathException {
        setProperty(DECIMAL_SEPARATOR, value, 0);
    }

    /**
     * Set the character to be used as the grouping separator
     *
     * @param value the character to be used
     * @throws XPathException if the value is not a single Unicode character (a surrogate pair is permitted)
     */

    public void setGroupingSeparator(String value) throws XPathException {
        setProperty(GROUPING_SEPARATOR, value, 0);
    }

    /**
     * Set the character to be used as the digit symbol (default is '#')
     *
     * @param value the character to be used
     * @throws XPathException if the value is not a single Unicode character (a surrogate pair is permitted)
     */

    public void setDigit(String value) throws XPathException {
        setProperty(DIGIT, value, 0);
    }

    /**
     * Set the character to be used as the minus sign
     *
     * @param value the character to be used
     * @throws XPathException if the value is not a single Unicode character (a surrogate pair is permitted)
     */

    public void setMinusSign(String value) throws XPathException {
        setProperty(MINUS_SIGN, value, 0);
    }

    /**
     * Set the character to be used as the percent sign
     *
     * @param value the character to be used
     * @throws XPathException if the value is not a single Unicode character (a surrogate pair is permitted)
     */

    public void setPercent(String value) throws XPathException {
        setProperty(PERCENT, value, 0);
    }

    /**
     * Set the character to be used as the per-mille sign
     *
     * @param value the character to be used
     * @throws XPathException if the value is not a single Unicode character (a surrogate pair is permitted)
     */

    public void setPerMille(String value) throws XPathException {
        setProperty(PER_MILLE, value, 0);
    }

    /**
     * Set the character to be used as the zero digit (which determines the digit family used in the output)
     *
     * @param value the character to be used
     * @throws XPathException if the value is not a single Unicode character (a surrogate pair is permitted),
     *                        or if it is not a character classified in Unicode as a digit with numeric value zero
     */

    public void setZeroDigit(String value) throws XPathException {
        setProperty(ZERO_DIGIT, value, 0);
    }

    /**
     * Set the character to be used as the exponent separator
     *
     * @param value the character to be used
     * @throws XPathException if the value is not a single Unicode character (a surrogate pair is permitted)
     */

    public void setExponentSeparator(String value) throws XPathException {
        setProperty(EXPONENT_SEPARATOR, value, 0);
    }

    /**
     * Set the character to be used as the pattern separator (default ';')
     *
     * @param value the character to be used
     * @throws XPathException if the value is not a single Unicode character (a surrogate pair is permitted)
     */

    public void setPatternSeparator(String value) throws XPathException {
        setProperty(PATTERN_SEPARATOR, value, 0);
    }

    /**
     * Set the string to be used to represent infinity
     *
     * @param value the string to be used
     * @throws XPathException - should not happen
     */

    public void setInfinity(String value) throws XPathException {
        setProperty(INFINITY, value, 0);
    }

    /**
     * Set the string to be used to represent NaN
     *
     * @param value the string to be used
     * @throws XPathException - should not happen
     */

    public void setNaN(String value) throws XPathException {
        setProperty(NAN, value, 0);
    }

    /**
     * Set the value of a property
     *
     * @param key        the integer key of the property to be set
     * @param value      the value of the property as a string (in many cases, this must be a single character)
     * @param precedence the precedence of the property value
     * @throws XPathException if the property is invalid.
     *                        This method does not check the consistency of different properties. If two different values are supplied
     *                        for the same property at the same precedence, the method does not complain, but notes the fact, and if
     *                        the inconsistency is not subsequently cleared by supplying another value at a higher precedence, the
     *                        error is reported when the checkConsistency() method is subsequently called.
     */

    public void setProperty(int key, String value, int precedence) throws XPathException {
        String name = propertyNames[key];
        if (key <= PATTERN_SEPARATOR) {
            int intValue = singleChar(name, value);
            if (precedence > precedences[key]) {
                intValues[key] = intValue;
                precedences[key] = precedence;
                inconsistent[key] = false;
            } else if (precedence == precedences[key]) {
                if (intValue != intValues[key]) {
                    inconsistent[key] = true;
                }
            } else {
                // ignore the new value
            }
            if (key == ZERO_DIGIT && !isValidZeroDigit(intValue)) {
                throw new XPathException("The value of the zero-digit attribute must be a Unicode digit with value zero",
                        errorCodes[ERR_NOT_UNICODE_DIGIT]);
            }
        } else if (key == INFINITY) {
            if (precedence > precedences[key]) {
                infinityValue = value;
                precedences[key] = precedence;
                inconsistent[key] = false;
            } else if (precedence == precedences[key]) {
                if (!infinityValue.equals(value)) {
                    inconsistent[key] = true;
                }
            }
        } else if (key == NAN) {
            if (precedence > precedences[key]) {
                NaNValue = value;
                precedences[key] = precedence;
                inconsistent[key] = false;
            } else if (precedence == precedences[key]) {
                if (!NaNValue.equals(value)) {
                    inconsistent[key] = false;
                }
            }
        } else {
            throw new IllegalArgumentException();
        }

    }

    /**
     * Set one of the single-character properties. Used when reloading an exported package
     * @param name the name of the property
     * @param value the Unicode codepoint of the property value
     */

    public void setIntProperty(String name, int value) {
        for (int i=0; i<propertyNames.length; i++) {
            if (propertyNames[i].equals(name)) {
                intValues[i] = value;
            }
        }
    }

    public void export(StructuredQName name, ExpressionPresenter out) {
        DecimalSymbols defaultSymbols = new DecimalSymbols(HostLanguage.XSLT, 31);
        out.startElement("decimalFormat");
        if (name != null) {
            out.emitAttribute("name", name);
        }
        for (int i=0; i<intValues.length; i++) {
            int propValue = intValues[i];
            if (propValue != defaultSymbols.intValues[i]) {
                out.emitAttribute(propertyNames[i], propValue + "");
            }
        }
        if (!"Infinity".equals(getInfinity())) {
            out.emitAttribute("infinity", getInfinity());
        }
        if (!"NaN".equals(getNaN())) {
            out.emitAttribute("NaN", getNaN());
        }
        out.endElement();
    }

    /**
     * Get the Unicode codepoint corresponding to a String, which must represent a single Unicode character
     *
     * @param name  the name of the property, for use in error messages
     * @param value the input string, representing a single Unicode character, perhaps as a surrogate pair
     * @return the corresponding Unicode codepoint
     * @throws XPathException if the supplied string is not a single character
     */
    private int singleChar(String name, String value) throws XPathException {
        UnicodeString us = UnicodeString.makeUnicodeString(value);
        if (us.uLength() != 1) {
            XPathException err = new XPathException("Attribute " + name + " should be a single character",
                    errorCodes[ERR_NOT_SINGLE_CHAR]);
            err.setIsStaticError(true);
            throw err;
        }
        return us.uCharAt(0);
    }


    /**
     * Check that no character is used in more than one role
     *
     * @param name the name of the decimal format (null for the unnamed decimal format)
     * @throws XPathException if the same character is used in conflicting rules, for example as decimal separator
     *                        and also as grouping separator
     */

    public void checkConsistency(StructuredQName name) throws XPathException {

        for (int i = 0; i < 10; i++) {
            if (inconsistent[i]) {
                XPathException err = new XPathException(
                        "Inconsistency in " +
                                (name == null ? "unnamed decimal format. " : "decimal format " + name.getDisplayName() + ". ") +
                                "There are two inconsistent values for decimal-format property " + propertyNames[i] +
                                " at the same import precedence");
                err.setErrorCode(errorCodes[ERR_TWO_VALUES_FOR_SAME_PROPERTY]);
                err.setIsStaticError(true);
                throw err;
            }
        }

        IntHashMap<String> map = new IntHashMap<String>(20);
        map.put(getDecimalSeparator(), "decimal-separator");

        if (map.get(getGroupingSeparator()) != null) {
            duplicate("grouping-separator", map.get(getGroupingSeparator()), name);
        }
        map.put(getGroupingSeparator(), "grouping-separator");

        if (map.get(getPercent()) != null) {
            duplicate("percent", map.get(getPercent()), name);
        }
        map.put(getPercent(), "percent");

        if (map.get(getPerMille()) != null) {
            duplicate("per-mille", map.get(getPerMille()), name);
        }
        map.put(getPerMille(), "per-mille");

        if (map.get(getDigit()) != null) {
            duplicate("digit", map.get(getDigit()), name);
        }
        map.put(getDigit(), "digit");

        if (map.get(getPatternSeparator()) != null) {
            duplicate("pattern-separator", map.get(getPatternSeparator()), name);
        }
        map.put(getPatternSeparator(), "pattern-separator");

        if (map.get(getExponentSeparator()) != null) {
            duplicate("exponent-separator", map.get(getExponentSeparator()), name);
        }
        map.put(getExponentSeparator(), "exponent-separator");

        int zero = getZeroDigit();
        for (int i = zero; i < zero + 10; i++) {
            if (map.get(i) != null) {
                XPathException err = new XPathException(
                    "Inconsistent properties in " +
                        (name == null ? "unnamed decimal format. " : "decimal format " + name.getDisplayName() + ". ") +
                        "The same character is used as digit " + (i - zero) +
                        " in the chosen digit family, and as the " + map.get(i));
                err.setErrorCode(errorCodes[ERR_SAME_CHAR_IN_TWO_ROLES]);
                throw err;
            }
        }
    }

    /**
     * Report that a character is used in more than one role
     *
     * @param role1 the first role
     * @param role2 the second role
     * @param name  the name of the decimal format (null for the unnamed decimal format)
     * @throws XPathException (always)
     */

    private void duplicate(String role1, String role2, StructuredQName name) throws XPathException {
        XPathException err = new XPathException(
                "Inconsistent properties in " +
                        (name == null ? "unnamed decimal format. " : "decimal format " + name.getDisplayName() + ". ") +
                        "The same character is used as the " + role1 + " and as the " + role2);
        err.setErrorCode(errorCodes[ERR_SAME_CHAR_IN_TWO_ROLES]);
        throw err;
    }

    /**
     * Check that the character declared as a zero-digit is indeed a valid zero-digit
     *
     * @param zeroDigit the value to be checked
     * @return false if it is not a valid zero-digit
     */

    public static boolean isValidZeroDigit(int zeroDigit) {
        return Arrays.binarySearch(zeroDigits, zeroDigit) >= 0;
    }

    /*@NotNull*/ static int[] zeroDigits = {0x0030, 0x0660, 0x06f0, 0x0966, 0x09e6, 0x0a66, 0x0ae6, 0x0b66, 0x0be6, 0x0c66,
            0x0ce6, 0x0d66, 0x0e50, 0x0ed0, 0x0f20, 0x1040, 0x17e0, 0x1810, 0x1946, 0x19d0,
            0xff10, 0x104a0, 0x1d7ce, 0x1d7d8, 0x1d7e2, 0x1d7ec, 0x1d7f6};

    /**
     * Test if two sets of decimal format symbols are the same
     *
     * @param obj the other set of symbols
     * @return true if the same characters/strings are assigned to each role in both sets of symbols.
     *         The precedences are not compared.
     */

    public boolean equals(Object obj) {
        if (!(obj instanceof DecimalSymbols)) {
            return false;
        }
        DecimalSymbols o = (DecimalSymbols) obj;
        return getDecimalSeparator() == o.getDecimalSeparator() &&
                getGroupingSeparator() == o.getGroupingSeparator() &&
                getDigit() == o.getDigit() &&
                getMinusSign() == o.getMinusSign() &&
                getPercent() == o.getPercent() &&
                getPerMille() == o.getPerMille() &&
                getZeroDigit() == o.getZeroDigit() &&
                getPatternSeparator() == o.getPatternSeparator() &&
                getInfinity().equals(o.getInfinity()) &&
                getNaN().equals(o.getNaN());
    }

    public int hashCode() {
        return getDecimalSeparator() + (37 * getGroupingSeparator()) + (41 * getDigit());
    }

}

