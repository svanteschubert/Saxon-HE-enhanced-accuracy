////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.QNameException;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;

import java.util.Objects;

/**
 * A {@link Converter} that accepts a string as input. This subclass of Converter is provided
 * to avoid having to wrap the string into a StringValue prior to conversion. Every Converter whose
 * source type is xs:string must be an instance of this subclass.
 * <p>The input to a StringConverter can also be an xs:untypedAtomic value, since the conversion semantics
 * are always the same as from a string.</p>
 * <p>A StringConverter also provides a method to validate that a string is valid against the target type,
 * without actually performing the conversion.</p>
 */
public abstract class StringConverter extends Converter {

    // Constants are defined only for converters that are independent of the conversion rules



    /**
     * Create a StringConverter
     */

    protected StringConverter() {
    }

    /**
     * Create a StringConverter
     *
     * @param rules the conversion rules to be applied
     */

    protected StringConverter(ConversionRules rules) {
        super(rules);
    }

    /**
     * Convert a string to the target type of this converter.
     *
     * @param input the string to be converted
     * @return either an {@link net.sf.saxon.value.AtomicValue} of the appropriate type for this converter (if conversion
     * succeeded), or a {@link ValidationFailure} if conversion failed.
     */

    
    public abstract ConversionResult convertString(UnicodeString input);

    /**
     * Validate a string for conformance to the target type, without actually performing
     * the conversion
     *
     * @param input the string to be validated
     * @return null if validation is successful, or a ValidationFailure indicating the reasons for failure
     * if unsuccessful
     */

    /*@Nullable*/
    public ValidationFailure validate(UnicodeString input) {
        ConversionResult result = convertString(input);
        return result instanceof ValidationFailure ? (ValidationFailure) result : null;
    }

    
    @Override
    public ConversionResult convert(AtomicValue input) {
        return convertString(input.getUnicodeStringValue());
    }

    /**
     * Converter from string to a derived type (derived from a type other than xs:string),
     * where the derived type needs to retain the original
     * string for validating against lexical facets such as pattern.
     */

    public static class StringToNonStringDerivedType extends StringConverter {
        private final StringConverter phaseOne;
        private final DownCastingConverter phaseTwo;

        public StringToNonStringDerivedType(StringConverter phaseOne, DownCastingConverter phaseTwo) {
            this.phaseOne = phaseOne;
            this.phaseTwo = phaseTwo;
        }

        @Override
        public StringToNonStringDerivedType setNamespaceResolver(NamespaceResolver resolver) {
            return new StringToNonStringDerivedType(
                    (StringConverter) phaseOne.setNamespaceResolver(resolver),
                    (DownCastingConverter) phaseTwo.setNamespaceResolver(resolver));
        }

        
        public ConversionResult convert(UnicodeString input) {
            UnicodeString in = input;
            try {
                in = phaseTwo.getTargetType().preprocess(in);
            } catch (ValidationException err) {
                return err.getValidationFailure();
            }
            ConversionResult temp = phaseOne.convertString(in);
            if (temp instanceof ValidationFailure) {
                return temp;
            }
            return phaseTwo.convert((AtomicValue) temp, in);
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            try {
                input = phaseTwo.getTargetType().preprocess(input);
            } catch (ValidationException err) {
                return err.getValidationFailure();
            }
            ConversionResult temp = phaseOne.convertString(input);
            if (temp instanceof ValidationFailure) {
                return temp;
            }
            return phaseTwo.convert((AtomicValue) temp, input);
        }

        /**
         * Validate a string for conformance to the target type, without actually performing
         * the conversion
         *
         * @param input the string to be validated
         * @return null if validation is successful, or a ValidationFailure indicating the reasons for failure
         * if unsuccessful
         */
        @Override
        public ValidationFailure validate(UnicodeString input) {
            try {
                input = phaseTwo.getTargetType().preprocess(input);
            } catch (ValidationException err) {
                return err.getValidationFailure();
            }
            ConversionResult temp = phaseOne.convertString(input);
            if (temp instanceof ValidationFailure) {
                return (ValidationFailure) temp;
            }
            return phaseTwo.validate((AtomicValue) temp, input);
        }
    }

    /**
     * Converts from xs:string or xs:untypedAtomic to xs:String
     */

    public static class StringToString extends StringConverter {
        public static final StringToString INSTANCE = new StringToString();

        @Override
        public ConversionResult convert(AtomicValue input) {
            return new StringValue(input.getUnicodeStringValue().tidy());
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            return new StringValue(input.tidy());
        }

        /*@Nullable*/
        @Override
        public ValidationFailure validate(UnicodeString input) {
            return null;
        }

        @Override
        public boolean isAlwaysSuccessful() {
            return true;
        }
    }

    /**
     * Converts from xs:string or xs:untypedAtomic to xs:untypedAtomic
     */

    public static class StringToUntypedAtomic extends StringConverter {
        public static final StringToUntypedAtomic INSTANCE = new StringToUntypedAtomic();

        @Override
        public StringValue convert(AtomicValue input) {
            return StringValue.makeUntypedAtomic(input.getUnicodeStringValue());
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            return StringValue.makeUntypedAtomic(input);
        }

        /*@Nullable*/
        @Override
        public ValidationFailure validate(UnicodeString input) {
            return null;
        }

        @Override
        public boolean isAlwaysSuccessful() {
            return true;
        }
    }


    /**
     * Converts from xs:string to xs:normalizedString
     */

    public static class StringToNormalizedString extends StringConverter {
        public static final StringToNormalizedString INSTANCE = new StringToNormalizedString();

        @Override
        public ConversionResult convertString(UnicodeString input) {
            return new StringValue(Whitespace.normalizeWhitespace(input).tidy(), BuiltInAtomicType.NORMALIZED_STRING);
        }

        /*@Nullable*/
        @Override
        public ValidationFailure validate(UnicodeString input) {
            return null;
        }

        @Override
        public boolean isAlwaysSuccessful() {
            return true;
        }
    }

    /**
     * Converts from xs:string to xs:token
     */

    public static class StringToToken extends StringConverter {
        public static final StringToToken INSTANCE = new StringToToken();

        @Override
        public ConversionResult convertString(UnicodeString input) {
            return new StringValue(Whitespace.collapseWhitespace(input).tidy(), BuiltInAtomicType.TOKEN);
        }

        /*@Nullable*/
        @Override
        public ValidationFailure validate(UnicodeString input) {
            return null;
        }

        @Override
        public boolean isAlwaysSuccessful() {
            return true;
        }
    }

    /**
     * Converts from xs:string to xs:language
     */

    public static class StringToLanguage extends StringConverter {
        private final static ARegularExpression regex =
                ARegularExpression.compile("[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*", "");
        // See erratum E2-25 to XML Schema Part 2.
        public static final StringToLanguage INSTANCE = new StringToLanguage();

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            UnicodeString trimmed = Whitespace.trim(input);
            if (!regex.matches(trimmed)) {
                return new ValidationFailure("The value '" + input + "' is not a valid xs:language");
            }
            return new StringValue(trimmed, BuiltInAtomicType.LANGUAGE);
        }

        /*@Nullable*/
        @Override
        public ValidationFailure validate(UnicodeString input) {
            if (regex.matches(Whitespace.trim(input))) {
                return null;
            } else {
                return new ValidationFailure("The value '" + input + "' is not a valid xs:language");
            }
        }
    }

    /**
     * Converts from xs:string to xs:NCName, xs:ID, xs:IDREF, or xs:ENTITY
     */

    public static class StringToNCName extends StringConverter {

        public static final StringToNCName TO_ID = new StringToNCName(BuiltInAtomicType.ID);
        public static final StringToNCName TO_ENTITY = new StringToNCName(BuiltInAtomicType.ENTITY);
        public static final StringToNCName TO_NCNAME = new StringToNCName(BuiltInAtomicType.NCNAME);
        public static final StringToNCName TO_IDREF = new StringToNCName(BuiltInAtomicType.IDREF);

        AtomicType targetType;

        public StringToNCName(AtomicType targetType) {
            this.targetType = targetType;
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            UnicodeString trimmed = Whitespace.trim(input);
            if (NameChecker.isValidNCName(trimmed.codePoints())) {
                return new StringValue(trimmed, targetType);
            } else {
                return new ValidationFailure("The value '" + input + "' is not a valid " + targetType.getDisplayName());
            }
        }

        /*@Nullable*/
        @Override
        public ValidationFailure validate(UnicodeString input) {
            if (NameChecker.isValidNCName(Whitespace.trim(input).codePoints())) {
                return null;
            } else {
                return new ValidationFailure("The value '" + input + "' is not a valid " + targetType.getDisplayName());
            }
        }
    }

    /**
     * Converts from xs:string to xs:NMTOKEN
     */

    public static class StringToNMTOKEN extends StringConverter {

        public final static StringToNMTOKEN INSTANCE = new StringToNMTOKEN();

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            UnicodeString trimmed = Whitespace.trim(input);
            if (NameChecker.isValidNmtoken(trimmed)) {
                return new StringValue(trimmed, BuiltInAtomicType.NMTOKEN);
            } else {
                return new ValidationFailure("The value '" + input + "' is not a valid xs:NMTOKEN");
            }
        }

        /*@Nullable*/
        @Override
        public ValidationFailure validate(UnicodeString input) {
            if (NameChecker.isValidNmtoken(Whitespace.trim(input))) {
                return null;
            } else {
                return new ValidationFailure("The value '" + input + "' is not a valid xs:NMTOKEN");
            }
        }
    }


    /**
     * Converts from xs:string to xs:Name
     */

    public static class StringToName extends StringToNCName {
        public static final StringToName INSTANCE = new StringToName();

        public StringToName() {
            super(BuiltInAtomicType.NAME);
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            ValidationFailure vf = validate(input);
            if (vf == null) {
                return new StringValue(Whitespace.trim(input), BuiltInAtomicType.NAME);
            } else {
                return vf;
            }
        }

        /*@Nullable*/
        @Override
        public ValidationFailure validate(UnicodeString input) {
            // if it's valid as an NCName then it's OK
            UnicodeString trimmed = Whitespace.trim(input);
            if (NameChecker.isValidNCName(trimmed.codePoints())) {
                return null;
            }

            // if not, replace any colons by underscores and then test if it's a valid NCName
            if (NameChecker.isValidNCName(trimmed.toString().replace(':', '_'))) {
                return null;
            } else {
                return new ValidationFailure("The value '" + trimmed + "' is not a valid xs:Name");
            }
        }
    }

    /**
     * Converts from xs:string to a user-defined type derived directly from xs:string
     */

    public static class StringToStringSubtype extends StringConverter {
        AtomicType targetType;
        int whitespaceAction;

        public StringToStringSubtype(ConversionRules rules,  AtomicType targetType) {
            super(rules);
            this.targetType = targetType;
            this.whitespaceAction = targetType.getWhitespaceAction();
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            UnicodeString cs = Whitespace.applyWhitespaceNormalization(whitespaceAction, input);
            try {
                cs = targetType.preprocess(cs);
            } catch (ValidationException err) {
                return err.getValidationFailure();
            }
            ValidationFailure f = targetType.validate(new StringValue(cs), cs, getConversionRules());
            if (f == null) {
                return new StringValue(cs, targetType);
            } else {
                return f;
            }
        }

        @Override
        public ValidationFailure validate(UnicodeString input) {
            UnicodeString cs = Whitespace.applyWhitespaceNormalization(whitespaceAction, input);
            try {
                cs = targetType.preprocess(cs);
            } catch (ValidationException err) {
                return err.getValidationFailure();
            }
            return targetType.validate(new StringValue(cs), cs, getConversionRules());
        }
    }

    /**
     * Converts from xs;string to a user-defined type derived from a built-in subtype of xs:string
     */

    public static class StringToDerivedStringSubtype extends StringConverter {
        AtomicType targetType;
        StringConverter builtInValidator;
        int whitespaceAction;

        public StringToDerivedStringSubtype( ConversionRules rules,  AtomicType targetType) {
            super(rules);
            this.targetType = targetType;
            this.whitespaceAction = targetType.getWhitespaceAction();
            builtInValidator = ((AtomicType) targetType.getBuiltInBaseType()).getStringConverter(rules);
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            UnicodeString cs = Whitespace.applyWhitespaceNormalization(whitespaceAction, input);
            ValidationFailure f = builtInValidator.validate(cs);
            if (f != null) {
                return f;
            }
            try {
                cs = targetType.preprocess(cs);
            } catch (ValidationException err) {
                return err.getValidationFailure();
            }
            f = targetType.validate(new StringValue(cs), cs, getConversionRules());
            if (f == null) {
                return new StringValue(cs, targetType);
            } else {
                return f;
            }
        }
    }


    /**
     * Converts a string to xs:float
     */

    public static class StringToFloat extends StringConverter {
        public StringToFloat(ConversionRules rules) {
            super(Objects.requireNonNull(rules));
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            try {
                float flt = (float) getConversionRules().getStringToDoubleConverter().stringToNumber(input);
                return new FloatValue(flt);
            } catch (NumberFormatException err) {
                ValidationFailure ve = new ValidationFailure("Cannot convert string to float: " + input);
                ve.setErrorCode("FORG0001");
                return ve;
            }
        }
    }

    /**
     * Converts a string to an xs:decimal
     */

    public static class StringToDecimal extends StringConverter {
        public static final StringToDecimal INSTANCE = new StringToDecimal();

        @Override
        public ConversionResult convertString(UnicodeString input) {
            return BigDecimalValue.makeDecimalValue(input.toString(), true);
        }

        /*@Nullable*/
        @Override
        public ValidationFailure validate(UnicodeString input) {
            if (BigDecimalValue.castableAsDecimal(input.toString())) {
                return null;
            } else {
                return new ValidationFailure("Cannot convert string to decimal: " + input);
            }
        }
    }

    /**
     * Converts a string to an integer
     */

    public static class StringToInteger extends StringConverter {
        public static final StringToInteger INSTANCE = new StringToInteger();

        public ConversionResult convert(StringValue input) {
            return IntegerValue.stringToInteger(input.toString());
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            return IntegerValue.stringToInteger(input.toString());
        }

        @Override
        public ValidationFailure validate(UnicodeString input) {
            return IntegerValue.castableAsInteger(input);
        }
    }

    /**
     * Converts a string to a built-in subtype of integer
     */

    public static class StringToIntegerSubtype extends StringConverter {

        BuiltInAtomicType targetType;

        public StringToIntegerSubtype(BuiltInAtomicType targetType) {
            this.targetType = targetType;
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            ConversionResult iv = IntegerValue.stringToInteger(input.toString());
            if (iv instanceof Int64Value) {
                boolean ok = IntegerValue.checkRange(((Int64Value) iv).longValue(), targetType);
                if (ok) {
                    return ((Int64Value) iv).withMetadata(targetType);
                } else {
                    return new ValidationFailure("Integer value is out of range for type " + targetType);
                }
            } else if (iv instanceof BigIntegerValue) {
                boolean ok = IntegerValue.checkBigRange(((BigIntegerValue) iv).asBigInteger(), targetType);
                if (ok) {
                    return ((BigIntegerValue) iv).withMetadata(targetType);
                } else {
                    return new ValidationFailure("Integer value is out of range for type " + targetType);
                }
            } else {
                assert iv instanceof ValidationFailure;
                return iv;
            }
        }
    }

    /**
     * Converts a string to a duration
     */

    public static class StringToDuration extends StringConverter {
        public static final StringToDuration INSTANCE = new StringToDuration();

        @Override
        public ConversionResult convertString(UnicodeString input) {
            return DurationValue.makeDuration(input);
        }
    }

    /**
     * Converts a string to a dayTimeDuration
     */


    public static class StringToDayTimeDuration extends StringConverter {
        public static final StringToDayTimeDuration INSTANCE = new StringToDayTimeDuration();

        @Override
        public ConversionResult convertString(UnicodeString input) {
            return DayTimeDurationValue.makeDayTimeDurationValue(input);
        }
    }

    /**
     * Converts a string to a yearMonthDuration
     */

    public static class StringToYearMonthDuration extends StringConverter {
        public static final StringToYearMonthDuration INSTANCE = new StringToYearMonthDuration();

        @Override
        public ConversionResult convertString(UnicodeString input) {
            return YearMonthDurationValue.makeYearMonthDurationValue(input);
        }
    }

    /**
     * Converts a string to a dateTime
     */

    public static class StringToDateTime extends StringConverter {
        public StringToDateTime(ConversionRules rules) {
            super(rules);
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            return DateTimeValue.makeDateTimeValue(input, getConversionRules());
        }
    }

    /**
     * Converts a string to a dateTimeStamp
     */

    public static class StringToDateTimeStamp extends StringConverter {
        public StringToDateTimeStamp(ConversionRules rules) {
            super(rules);
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            ConversionResult val = DateTimeValue.makeDateTimeValue(input, getConversionRules());
            if (val instanceof DateTimeValue) {
                if (!((DateTimeValue) val).hasTimezone()) {
                    return new ValidationFailure("Supplied DateTimeStamp value " + input + " has no time zone");
                } else {
                    val = ((DateTimeValue) val).withMetadata(BuiltInAtomicType.DATE_TIME_STAMP);
                }
            }
            return val;
        }
    }

    /**
     * Converts a string to a date
     */

    public static class StringToDate extends StringConverter {
        public StringToDate(ConversionRules rules) {
            super(rules);
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            return DateValue.tryParseDate(input.toString(), getConversionRules().isAllowYearZero());
        }
    }

    /**
     * Converts a string to a gMonth
     */

    public static class StringToGMonth extends StringConverter {
        public static final StringToGMonth INSTANCE = new StringToGMonth();

        @Override
        public ConversionResult convertString(UnicodeString input) {
            return GMonthValue.makeGMonthValue(input);
        }
    }

    /**
     * Converts a string to a gYearMonth
     */

    public static class StringToGYearMonth extends StringConverter {
        public StringToGYearMonth(ConversionRules rules) {
            super(rules);
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            return GYearMonthValue.makeGYearMonthValue(input, getConversionRules());
        }
    }

    /**
     * Converts a string to a gYear
     */

    public static class StringToGYear extends StringConverter {
        public StringToGYear(ConversionRules rules) {
            super(rules);
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            return GYearValue.makeGYearValue(input, getConversionRules());
        }
    }

    /**
     * Converts a string to a gMonthDay
     */

    public static class StringToGMonthDay extends StringConverter {
        public static final StringToGMonthDay INSTANCE = new StringToGMonthDay();

        @Override
        public ConversionResult convertString(UnicodeString input) {
            return GMonthDayValue.makeGMonthDayValue(input);
        }
    }

    /**
     * Converts a string to a gDay
     */

    public static class StringToGDay extends StringConverter {
        public static final StringToGDay INSTANCE = new StringToGDay();

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            return GDayValue.makeGDayValue(input);
        }
    }

    /**
     * Converts a string to a time
     */

    public static class StringToTime extends StringConverter {
        public static final StringToTime INSTANCE = new StringToTime();

        @Override
        public ConversionResult convertString(UnicodeString input) {
            return TimeValue.makeTimeValue(input);
        }
    }

    /**
     * Converts a string to a boolean
     */

    public static class StringToBoolean extends StringConverter {
        public static final StringToBoolean INSTANCE = new StringToBoolean();

        @Override
        public ConversionResult convertString(UnicodeString input) {
            return BooleanValue.fromString(input);
        }
    }

    /**
     * Converts a string to hexBinary
     */

    public static class StringToHexBinary extends StringConverter {
        public static final StringToHexBinary INSTANCE = new StringToHexBinary();

        @Override
        public ConversionResult convertString(UnicodeString input) {
            try {
                return new HexBinaryValue(input);
            } catch (XPathException e) {
                return ValidationFailure.fromException(e);
            }
        }
    }

    /**
     * Converts string to base64
     */

    public static class StringToBase64Binary extends StringConverter {
        public static final StringToBase64Binary INSTANCE = new StringToBase64Binary();

        @Override
        public ConversionResult convertString(UnicodeString input) {
            try {
                return new Base64BinaryValue(input);
            } catch (XPathException e) {
                return ValidationFailure.fromException(e);
            }
        }
    }


    /**
     * Converts String to QName
     */

    public static class StringToQName extends StringConverter {

        private NamespaceResolver nsResolver;

        public StringToQName(ConversionRules rules) {
            super(rules);
        }

        @Override
        public StringToQName setNamespaceResolver(NamespaceResolver resolver) {
            StringToQName c = new StringToQName(getConversionRules());
            c.nsResolver = resolver;
            return c;
        }

        @Override
        public NamespaceResolver getNamespaceResolver() {
            return nsResolver;
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            if (nsResolver == null) {
                throw new UnsupportedOperationException("Cannot validate a QName without a namespace resolver");
            }
            try {
                String[] parts = NameChecker.getQNameParts(Whitespace.trim(input.toString()));
                NamespaceUri uri = nsResolver.getURIForPrefix(parts[0], true);
                if (uri == null) {
                    ValidationFailure failure = new ValidationFailure("Namespace prefix " +
                                                                              Err.wrap(parts[0]) + " has not been declared");
                    failure.setErrorCode("FONS0004");
                    return failure;
                }
                return new QNameValue(parts[0], uri, parts[1], BuiltInAtomicType.QNAME, false);
            } catch (QNameException err) {
                return new ValidationFailure("Invalid lexical QName " + Err.wrap(input));
            } catch (XPathException err) {
                return new ValidationFailure(err.getMessage());
            }
        }
    }

    /**
     * Converts String to NOTATION
     */

    public static class StringToNotation extends StringConverter {

        private NamespaceResolver nsResolver;

        public StringToNotation(ConversionRules rules) {
            super(rules);
        }

        @Override
        public StringToNotation setNamespaceResolver(NamespaceResolver resolver) {
            StringToNotation c = new StringToNotation(getConversionRules());
            c.nsResolver = resolver;
            return c;
        }

        @Override
        public NamespaceResolver getNamespaceResolver() {
            return nsResolver;
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            if (getNamespaceResolver() == null) {
                throw new UnsupportedOperationException("Cannot validate a NOTATION without a namespace resolver");
            }
            try {
                String[] parts = NameChecker.getQNameParts(Whitespace.trim(input.toString()));
                NamespaceUri uri = getNamespaceResolver().getURIForPrefix(parts[0], true);
                if (uri == null) {
                    return new ValidationFailure("Namespace prefix " + Err.wrap(parts[0]) + " has not been declared");
                }
                // No need to check that this is a declared notation. That is only needed when checking the
                // emumeration facet of a type derived from xs:NOTATION, and that is handled by the schema compiler.
//                // This check added in 9.3. The XSLT spec says that this check should not be performed during
//                // validation. However, this appears to be based on an incorrect assumption: see spec bug 6952
//                if (!getConversionRules().isDeclaredNotation(uri, parts[1])) {
//                    //System.err.println(getConversionRules().isDeclaredNotation(uri, parts[1]));
//                    return new ValidationFailure("Notation {" + uri + "}" + parts[1] + " is not declared in the schema");
//                }
                return new NotationValue(parts[0], uri, parts[1], false);
            } catch (QNameException err) {
                return new ValidationFailure("Invalid lexical QName " + Err.wrap(input));
            } catch (XPathException err) {
                return new ValidationFailure(err.getMessage());
            }
        }
    }

    /**
     * Converts string to anyURI
     */

    public static class StringToAnyURI extends StringConverter {
        public StringToAnyURI(ConversionRules rules) {
            super(rules);
        }

        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            if (getConversionRules().isValidURI(input.toString())) {
                return new AnyURIValue(input);
            } else {
                return new ValidationFailure("Invalid URI: " + input);
            }
        }

        /*@Nullable*/
        @Override
        public ValidationFailure validate(UnicodeString input) {
            if (getConversionRules().isValidURI(input.toString())) {
                return null;
            } else {
                return new ValidationFailure("Invalid URI: " + input);
            }
        }
    }

    /**
     * Converter from string to plain union types
     */

    public static class StringToUnionConverter extends StringConverter {

        PlainType targetType;
        ConversionRules rules;

        public StringToUnionConverter(PlainType targetType, ConversionRules rules) {
            if (!targetType.isPlainType()) {
                throw new IllegalArgumentException();
            }
            if (targetType.isNamespaceSensitive()) {
                throw new IllegalArgumentException();
            }
            this.targetType = targetType;
            this.rules = rules;
        }

        /**
         * Convert a string to the target type of this converter.
         *
         * @param input the string to be converted
         * @return either an {@link net.sf.saxon.value.AtomicValue} of the appropriate type for this converter (if conversion
         * succeeded), or a {@link net.sf.saxon.type.ValidationFailure} if conversion failed.
         */
        
        @Override
        public ConversionResult convertString(UnicodeString input) {
            try {
                //noinspection RedundantCast
                return (AtomicValue)((UnionType)targetType).getTypedValue(input, null, rules).head();
            } catch (ValidationException err) {
                return err.getValidationFailure();
            }
        }
    }
}

