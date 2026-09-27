////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.str.UnicodeChar;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.z.IntHashMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is modelled on Java's DecimalFormatSymbols, but it allows the use of any
 * Unicode character to represent symbols such as the decimal point and the grouping
 * separator, whereas DecimalFormatSymbols restricts these to a char (1-65535).
 *
 * <p>Upgraded July 2024 to handle new QT4 features, in particular the ability
 * to supply both a marker character and a rendition string for properties like
 * {@code PERCENT}: the marker character is used in the picture string, while
 * the rendition form is used in the output.</p>
 */
public class DecimalSymbols {

    /**
     * Decimal format properties. Properties such as "percent" are split into two,
     * the marker character and the rendered output.
     */
    public enum Property {
        DECIMAL_SEPARATOR_MARKER,
        DECIMAL_SEPARATOR_RENDITION,
        GROUPING_SEPARATOR_MARKER,
        GROUPING_SEPARATOR_RENDITION,
        DIGIT,
        EXPONENT_SEPARATOR_MARKER,
        EXPONENT_SEPARATOR_RENDITION,
        INFINITY,
        MINUS_SIGN,
        NAN,
        PATTERN_SEPARATOR,
        PERCENT_MARKER,
        PERCENT_RENDITION,
        PER_MILLE_MARKER,
        PER_MILLE_RENDITION,
        ZERO_DIGIT
    }

    /**
     * Property settings for this decimal format
     */
    private Map<Property, UnicodeString> properties = new HashMap<>();

    /**
     * Error conditions, potentially mapped to different actual error codes.
     */
    private static final int ERR_NOT_SINGLE_CHAR = 0;
    private static final int ERR_NOT_UNICODE_DIGIT = 1;
    private static final int ERR_SAME_CHAR_IN_TWO_ROLES = 2;
    private static final int ERR_TWO_VALUES_FOR_SAME_PROPERTY = 3;

    private static final String[] XSLT_CODES = {"XTSE0020", "XTSE1295", "XTSE1300", "XTSE1290"};
    private static final String[] XQUERY_CODES = {"XQST0097", "XQST0097", "XQST0098", "XQST0114"};
    private String[] errorCodes = XSLT_CODES;

    /**
     * Language level: 40 for 4.0, 31 for 3.1 etc
     */
    private int languageLevel;

    /**
     * Default values of the decimal format properties
     */

    private final static Map<Property, UnicodeString> defaultProperties = new HashMap<>();

    static {
        defaultProperties.put(Property.DECIMAL_SEPARATOR_MARKER, new UnicodeChar('.'));
        defaultProperties.put(Property.DECIMAL_SEPARATOR_RENDITION, new UnicodeChar('.'));
        defaultProperties.put(Property.GROUPING_SEPARATOR_MARKER, new UnicodeChar(','));
        defaultProperties.put(Property.GROUPING_SEPARATOR_RENDITION, new UnicodeChar(','));
        defaultProperties.put(Property.DIGIT, new UnicodeChar('#'));
        defaultProperties.put(Property.EXPONENT_SEPARATOR_MARKER, new UnicodeChar('e'));
        defaultProperties.put(Property.EXPONENT_SEPARATOR_RENDITION, new UnicodeChar('e'));
        defaultProperties.put(Property.INFINITY, new Twine8("Infinity"));
        defaultProperties.put(Property.MINUS_SIGN, new UnicodeChar('-'));
        defaultProperties.put(Property.NAN, new Twine8("NaN"));
        defaultProperties.put(Property.PATTERN_SEPARATOR, new UnicodeChar(';'));
        defaultProperties.put(Property.PERCENT_MARKER, new UnicodeChar('%'));
        defaultProperties.put(Property.PERCENT_RENDITION, new UnicodeChar('%'));
        defaultProperties.put(Property.PER_MILLE_MARKER, new UnicodeChar('‰'));
        defaultProperties.put(Property.PER_MILLE_RENDITION, new UnicodeChar('‰'));
        defaultProperties.put(Property.ZERO_DIGIT, new UnicodeChar('0'));
    }


    /**
     * Ask if the supplied string is recognized as a decimal format property name
     * @param name the name in question
     * @return true if this is an acceptable property name
     */
    public static boolean isValidPropertyName(String name) {
        switch (name) {
            case "decimal-separator":
            case "grouping-separator":
            case "digit":
            case "minus-sign":
            case "percent":
            case "per-mille":
            case "zero-digit":
            case "exponent-separator":
            case "pattern-separator":
            case "infinity":
            case "NaN":
                return true;
            default:
                return false;
        }
    };

    /**
     * Get the property with a given name
     * @param name the property name
     * @return the corresponding property. In the case of "dual" properties such as {@code PERCENT},
     * the marker property is returned.
     */

    public static Property getPropertyForName(String name) {
        return switch (name) {
            case "decimal-separator" -> Property.DECIMAL_SEPARATOR_MARKER;
            case "grouping-separator" -> Property.GROUPING_SEPARATOR_MARKER;
            case "digit" -> Property.DIGIT;
            case "minus-sign" -> Property.MINUS_SIGN;
            case "percent" -> Property.PERCENT_MARKER;
            case "per-mille" -> Property.PER_MILLE_MARKER;
            case "zero-digit" -> Property.ZERO_DIGIT;
            case "exponent-separator" -> Property.EXPONENT_SEPARATOR_MARKER;
            case "pattern-separator" -> Property.PATTERN_SEPARATOR;
            case "infinity" -> Property.INFINITY;
            case "NaN" -> Property.NAN;
            default -> throw new IllegalArgumentException();
        };
    }

    /**
     * The import precedences of the properties. This is needed in XSLT because different properties
     * of a decimal format can be set in different stylesheet modules, and therefore have different
     * precedences. Not used in XPath/XQuery.
     */
    private Map<Property, Integer> precedences = new HashMap<>(20);

    /**
     * Map noting properties that have inconsistent settings (two different settings at the same
     * precedence level). This is reported as an error only if there is no higher-precedence
     * setting of the property to remove the ambiguity. XSLT only.
     */
    private Map<Property, Boolean> inconsistent = new HashMap<>(20);

    /**
     * Create a DecimalSymbols object with default values for all properties
     * @param language e.g. XSLT or XQuery
     * @param languageLevel language version (times ten), e.g. 40 for XQuery 4.0
     */

    public DecimalSymbols(HostLanguage language, int languageLevel) {
        properties = new HashMap<>(defaultProperties);
        setHostLanguage(language, languageLevel);
    }

    /**
     * Copy a set of decimal format symbols
     * @param input the symbols to be copied
     */

    public DecimalSymbols(DecimalSymbols input) {
        properties = new HashMap<>(input.properties);
        precedences = new HashMap<>(input.precedences);
        inconsistent = new HashMap<>(input.inconsistent);
        errorCodes = input.errorCodes;
        languageLevel = input.languageLevel;
    }

    /**
     * Set the host language and version
     * @param language e.g. XSLT or XQuery
     * @param languageLevel language version (times ten), e.g. 40 for XQuery 4.0
     */
    public void setHostLanguage(HostLanguage language, int languageLevel) {
        if (language == HostLanguage.XQUERY) {
            errorCodes = XQUERY_CODES;
        } else {
            errorCodes = XSLT_CODES;
        }
        this.languageLevel = languageLevel;
    }

    /**
     * Get the value of a given property
     * @param property the property required
     * @return the property value if set, or null if not.
     */

    public UnicodeString getProperty(Property property) {
        return properties.get(property);
    }

    /**
     * Get the value of a single-character property
     * @param marker the property required
     * @return the value of the property as a single codepoint
     * @throws IllegalStateException if the value of the property is not a single character
     */

    public int getMarker(Property marker) {
        UnicodeString val = properties.get(marker);
        if (val.length() == 1) {
            return val.codePointAt(0);
        } else {
            throw new IllegalStateException("Invalid decimal property found");
        }
    }

    /**
     * Ask whether a given property takes its default value
     * @param property the property in question
     * @return true if the value is explicitly or implicitly set to its default value
     */
    public boolean hasDefaultValue(Property property) {
        return properties.get(property).equals(defaultProperties.get(property));
    }

    /**
     * Set the value of a property
     * @param propertyName the name of the property as a string
     * @param value the value of the property
     * @throws XPathException if the name or value is invalid
     */
    public void setProperty(String propertyName, UnicodeString value) throws XPathException {
        switch (propertyName) {
            case "decimal-separator" ->
                    setDualProperty(propertyName, Property.DECIMAL_SEPARATOR_MARKER, Property.DECIMAL_SEPARATOR_RENDITION, value);
            case "grouping-separator" ->
                    setDualProperty(propertyName, Property.GROUPING_SEPARATOR_MARKER, Property.GROUPING_SEPARATOR_RENDITION, value);
            case "digit" ->
                    setSingleCharProperty(propertyName, Property.DIGIT, value);
            case "minus-sign" ->
                    setStringProperty(propertyName, Property.MINUS_SIGN, value);
            case "percent" ->
                    setDualProperty(propertyName, Property.PERCENT_MARKER, Property.PERCENT_RENDITION, value);
            case "per-mille" ->
                    setDualProperty(propertyName, Property.PER_MILLE_MARKER, Property.PER_MILLE_RENDITION, value);
            case "zero-digit" -> {
                setSingleCharProperty(propertyName, Property.ZERO_DIGIT, value);
                if (!isValidZeroDigit(value.codePointAt(0))) {
                    throw new XPathException("The value of the zero-digit attribute must be a Unicode digit with value zero",
                                             errorCodes[ERR_NOT_UNICODE_DIGIT]);
                }
            }
            case "exponent-separator" ->
                    setDualProperty(propertyName, Property.EXPONENT_SEPARATOR_MARKER, Property.EXPONENT_SEPARATOR_RENDITION, value);
            case "pattern-separator" ->
                    setSingleCharProperty(propertyName, Property.PATTERN_SEPARATOR, value);
            case "infinity" ->
                    setStringProperty(propertyName, Property.INFINITY, value);
            case "NaN" ->
                    setStringProperty(propertyName, Property.NAN, value);
            default ->
                    throw new XPathException("Unknown decimal format property: " + propertyName);
        }
    }

    private void setSingleCharProperty(String propertyName, Property prop, UnicodeString value) throws XPathException {
        if (value.length32() != 1) {
            throw new XPathException("Decimal format property " + propertyName + " must be a single character", errorCodes[ERR_NOT_SINGLE_CHAR]);
        }
        properties.put(prop, value);
        precedences.put(prop, 0);
    }

    private void setStringProperty(String propertyName, Property prop, UnicodeString value) throws XPathException {
        properties.put(prop, value);
        precedences.put(prop, 0);
    }

    private void setDualProperty(String propertyName, Property marker, Property rendition, UnicodeString value) throws XPathException {
        if (value.length32() == 1 || languageLevel < 4) {
            setSingleCharProperty(propertyName, marker,value);
            setStringProperty(propertyName, rendition, value);
        } else if (value.length32() > 2 && value.codePointAt(1) == ':') {
            setSingleCharProperty(propertyName, marker, value.substring(0, 1));
            setStringProperty(propertyName, rendition,value.substring(2));
        } else {
            throw new XPathException("Value of decimal-format property " + propertyName + " must either be a single character, "
                                             + "or have a colon (:) as its second character", errorCodes[ERR_NOT_SINGLE_CHAR]);
        }
        precedences.put(marker, 0);
        precedences.put(rendition, 0);
    }


    /**
     * Set the value of a property at a given precedence level
     *
     * @param prop        the property to be set
     * @param value      the value of the property as a string (in many cases, this must be a single character)
     * @param precedence the precedence of the property value
     * @throws XPathException if the property is invalid.
     *                        This method does not check the consistency of different properties. If two different values are supplied
     *                        for the same property at the same precedence, the method does not complain, but notes the fact, and if
     *                        the inconsistency is not subsequently cleared by supplying another value at a higher precedence, the
     *                        error is reported when the checkConsistency() method is subsequently called.
     */

    public void setProperty(String prop, UnicodeString value, int precedence) throws XPathException {
        if (!isValidPropertyName(prop)) {
            throw new XPathException("invalid decimal property name");
        }
        Property p = getPropertyForName(prop);
        int existingPrecedence = precedences.getOrDefault(p, -1);
        if (precedence > existingPrecedence) {
            setProperty(prop, value);
            precedences.put(p, precedence);
            inconsistent.put(p, false);
        } else if (precedence == existingPrecedence) {
            if (!value.equals(properties.get(p))) {
                inconsistent.put(p, true);
            }
        } else {
            // ignore the new value
        }

    }

    /**
     * Export a decimal format to a SEF file
     * @param name the name of the decimal format to be exported, or null for the unnamed decimal format
     * @param out the destination of the export
     */
    public void export(StructuredQName name, ExpressionPresenter out) {
        out.startElement("decimalFormat");
        if (name != null) {
            out.emitAttribute("name", name);
        }
        HashMap<String, UnicodeString> result = new HashMap<>(10);
        if (!(hasDefaultValue(Property.DECIMAL_SEPARATOR_MARKER) && hasDefaultValue(Property.DECIMAL_SEPARATOR_RENDITION))) {
            exportDual("decimal-separator", Property.DECIMAL_SEPARATOR_MARKER, Property.DECIMAL_SEPARATOR_RENDITION, out);
        }
        if (!(hasDefaultValue(Property.GROUPING_SEPARATOR_MARKER) && hasDefaultValue(Property.GROUPING_SEPARATOR_RENDITION))) {
            exportDual("grouping-separator", Property.GROUPING_SEPARATOR_MARKER, Property.GROUPING_SEPARATOR_RENDITION, out);
        }
        if (!hasDefaultValue(Property.DIGIT)) {
            out.emitAttribute("digit", getProperty(Property.DIGIT).toString());
        }
        if (!(hasDefaultValue(Property.EXPONENT_SEPARATOR_MARKER) && hasDefaultValue(Property.EXPONENT_SEPARATOR_RENDITION))) {
            exportDual("exponent-separator", Property.EXPONENT_SEPARATOR_MARKER, Property.EXPONENT_SEPARATOR_RENDITION, out);
        }
        if (!hasDefaultValue(Property.INFINITY)) {
            out.emitAttribute("infinity", getProperty(Property.INFINITY).toString());
        }
        if (!hasDefaultValue(Property.MINUS_SIGN)) {
            out.emitAttribute("minus-sign", getProperty(Property.MINUS_SIGN).toString());
        }
        if (!hasDefaultValue(Property.NAN)) {
            out.emitAttribute("NaN", getProperty(Property.NAN).toString());
        }
        if (!hasDefaultValue(Property.PATTERN_SEPARATOR)) {
            out.emitAttribute("pattern-separator", getProperty(Property.PATTERN_SEPARATOR).toString());
        }
        if (!(hasDefaultValue(Property.PERCENT_MARKER) && hasDefaultValue(Property.PERCENT_RENDITION))) {
            exportDual("percent", Property.PERCENT_MARKER, Property.PERCENT_RENDITION, out);
        }
        if (!(hasDefaultValue(Property.PER_MILLE_MARKER) && hasDefaultValue(Property.PER_MILLE_RENDITION))) {
            exportDual("per-mille", Property.PER_MILLE_MARKER, Property.PER_MILLE_RENDITION, out);
        }
        if (!hasDefaultValue(Property.ZERO_DIGIT)) {
            out.emitAttribute("zero-digit", getProperty(Property.ZERO_DIGIT).toString());
        }

        out.endElement();
    }

    private void exportDual(String propertyName, Property marker, Property rendition, ExpressionPresenter out) {
        if (getProperty(marker).equals(getProperty(rendition))) {
            out.emitAttribute(propertyName, getProperty(marker).toString());
        } else {
            out.emitAttribute(propertyName, getProperty(marker)
                    .concat(new UnicodeChar(':'))
                    .concat(getProperty(rendition))
                    .toString());
        }
    }

    private String getPropertyName(Property prop) {
        switch (prop) {
            case DECIMAL_SEPARATOR_MARKER:
                return "decimal-separator-marker";
            case DECIMAL_SEPARATOR_RENDITION:
                return "decimal-separator";
            case GROUPING_SEPARATOR_MARKER:
                return "grouping-separator-marker";
            case GROUPING_SEPARATOR_RENDITION:
                return "grouping-separator";
            case DIGIT:
                return "digit";
            case EXPONENT_SEPARATOR_MARKER:
                return "decimal-separator-marker";
            case EXPONENT_SEPARATOR_RENDITION:
                return "exponent-separator";
            case INFINITY:
                return "infinity";
            case MINUS_SIGN:
                return "minus-sign";
            case NAN:
                return "NaN";
            case PATTERN_SEPARATOR:
                return "pattern-separator";
            case PERCENT_MARKER:
                return "percent-marker";
            case PERCENT_RENDITION:
                return "percent";
            case PER_MILLE_MARKER:
                return "per-mille-marker";
            case PER_MILLE_RENDITION:
                return "per-mille";
            case ZERO_DIGIT:
                return "zero-digit";
            default:
                return "";
        }
    }


    /**
     * Check that no character is used in more than one role
     *
     * @param name the name of the decimal format (null for the unnamed decimal format)
     * @throws XPathException if the same character is used in conflicting rules, for example as decimal separator
     *                        and also as grouping separator
     */

    public void checkConsistency(StructuredQName name) throws XPathException {

        for (Property prop : properties.keySet()) {
            boolean isInconsistent = inconsistent.containsKey(prop) && inconsistent.get(prop);
            if (isInconsistent) {
                throw new XPathException(
                        "Inconsistency in " +
                                (name == null ? "unnamed decimal format. " : "decimal format " + name.getDisplayName() + ". ") +
                                "There are two inconsistent values for decimal-format property " + getPropertyName(prop) +
                                " at the same import precedence")
                        .withErrorCode(errorCodes[ERR_TWO_VALUES_FOR_SAME_PROPERTY])
                        .asStaticError();
            }
        }

        IntHashMap<String> map = new IntHashMap<String>(20);
        map.put(getMarker(Property.DECIMAL_SEPARATOR_MARKER), "decimal-separator");

        if (map.get(getMarker(Property.GROUPING_SEPARATOR_MARKER)) != null) {
            duplicate("grouping-separator", map.get(getMarker(Property.GROUPING_SEPARATOR_MARKER)), name);
        }
        map.put(getMarker(Property.GROUPING_SEPARATOR_MARKER), "grouping-separator");

        if (map.get(getMarker(Property.PERCENT_MARKER)) != null) {
            duplicate("percent", map.get(getMarker(Property.PERCENT_MARKER)), name);
        }
        map.put(getMarker(Property.PERCENT_MARKER), "percent");

        if (map.get(getMarker(Property.PER_MILLE_MARKER)) != null) {
            duplicate("per-mille", map.get(getMarker(Property.PER_MILLE_MARKER)), name);
        }
        map.put(getMarker(Property.PER_MILLE_MARKER), "per-mille");

        if (map.get(getMarker(Property.DIGIT)) != null) {
            duplicate("digit", map.get(getMarker(Property.DIGIT)), name);
        }
        map.put(getMarker(Property.DIGIT), "digit");

        if (map.get(getMarker(Property.PATTERN_SEPARATOR)) != null) {
            duplicate("pattern-separator", map.get(getMarker(Property.PATTERN_SEPARATOR)), name);
        }
        map.put(getMarker(Property.PATTERN_SEPARATOR), "pattern-separator");

        if (map.get(getMarker(Property.EXPONENT_SEPARATOR_MARKER)) != null) {
            duplicate("exponent-separator", map.get(getMarker(Property.EXPONENT_SEPARATOR_MARKER)), name);
        }
        map.put(getMarker(Property.EXPONENT_SEPARATOR_MARKER), "exponent-separator");

        int zero = getMarker(Property.ZERO_DIGIT);
        for (int i = zero; i < zero + 10; i++) {
            if (map.get(i) != null) {
                throw new XPathException(
                    "Inconsistent properties in " +
                        (name == null ? "unnamed decimal format. " : "decimal format " + name.getDisplayName() + ". ") +
                        "The same character is used as digit " + (i - zero) +
                        " in the chosen digit family, and as the " + map.get(i))
                        .withErrorCode(errorCodes[ERR_SAME_CHAR_IN_TWO_ROLES]);
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
        throw new XPathException(
                "Inconsistent properties in " +
                        (name == null ? "unnamed decimal format. " : "decimal format " + name.getDisplayName() + ". ") +
                        "The same character is used as the " + role1 + " and as the " + role2)
                .withErrorCode(errorCodes[ERR_SAME_CHAR_IN_TWO_ROLES]);
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
        return properties.equals(((DecimalSymbols) obj).properties);
    }

    public int hashCode() {
        return properties.hashCode();
    }

}

