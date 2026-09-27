////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.om.StandardNames;
import net.sf.saxon.type.*;
import net.sf.saxon.value.StringToDouble11;

/**
 * This class defines a set of rules for converting between different atomic types. It handles the variations
 * that arise between different versions of the W3C specifications, for example the changes in Name syntax
 * between XML 1.0 and XML 1.1, the introduction of "+INF" as a permitted xs:double value in XSD 1.1, and so on.
 * <p>It is possible to nominate a customized <code>ConversionRules</code> object at the level of the
 * {@link net.sf.saxon.Configuration}, either by instantiating this class and changing the properties, or
 * by subclassing.</p>
 *
 * @see net.sf.saxon.Configuration#setConversionRules(ConversionRules)
 * @since 9.3
 */

public class ConversionRules {

    private StringToDouble stringToDouble = StringToDouble11.getInstance();
    private URIChecker uriChecker;
    private boolean allowYearZero = true;


    /**
     * Default conversion rules. Changed in Saxon 9.9 so these are the XSD 1.1 rules (year zero allowed in dates,
     * {@code -INF} allowed in {@code xs:double}). Modifying the default conversion rules is inadvisable,
     * but it could potentially be done in order to retain compatibility with earlier Saxon releases.
     */
    public final static ConversionRules DEFAULT = new ConversionRules();


    public ConversionRules() {
    }

    /**
     * Create a copy of these conversion rules.
     *
     * @return a copy of the rules. The cache of converters is NOT copied (because changes to the conversion rules would
     *         invalidate the cache)
     */

    public ConversionRules copy() {
        ConversionRules cr = new ConversionRules();
        copyTo(cr);
        return cr;
    }

    /**
     * Create a copy of these conversion rules.
     *
     * @param cr a ConversionRules object which will be updated to hold a copy of the rules.
     *           The cache of converters is NOT copied (because changes to the conversion rules would
     *           invalidate the cache)
     */

    public void copyTo(ConversionRules cr) {
        cr.stringToDouble = stringToDouble;
        cr.uriChecker = uriChecker;
        cr.allowYearZero = allowYearZero;
    }

    /**
     * Set the converter that will be used for converting strings to doubles and floats.
     *
     * @param converter the converter to be used. There are two converters in regular use:
     *                  they differ only in whether the lexical value "+INF" is recognized as a representation of
     *                  positive infinity.
     */

    public void setStringToDoubleConverter(StringToDouble converter) {
        this.stringToDouble = converter;
    }

    /**
     * Get the converter that will be used for converting strings to doubles and floats.
     *
     * @return the converter to be used. There are two converters in regular use:
     *         they differ only in whether the lexical value "+INF" is recognized as a representation of
     *         positive infinity.
     */

    public StringToDouble getStringToDoubleConverter() {
        return stringToDouble;
    }

    /**
     * Set the class to be used for checking URI values. By default, no checking takes place.
     *
     * @param checker an object to be used for checking URIs; or null if any string is accepted as an anyURI value
     */

    public void setURIChecker(URIChecker checker) {
        this.uriChecker = checker;
    }

    /**
     * Ask whether a string is a valid instance of xs:anyURI according to the rules
     * defined by the current URIChecker
     *
     * @param str the string to be checked against the rules for URIs
     * @return true if the string represents a valid xs:anyURI value
     */

    public boolean isValidURI(String str) {
        return uriChecker == null || uriChecker.isValidURI(str);
    }

    /**
     * Say whether year zero is permitted in dates. By default it is not permitted when XSD 1.0 is in use,
     * but it is permitted when XSD 1.1 is used.
     *
     * @param allowed true if year zero is permitted
     */

    public void setAllowYearZero(boolean allowed) {
        allowYearZero = allowed;
    }

    /**
     * Ask whether  year zero is permitted in dates. By default it is not permitted when XSD 1.0 is in use,
     * but it is permitted when XSD 1.1 is used.
     *
     * @return true if year zero is permitted
     */

    public boolean isAllowYearZero() {
        return allowYearZero;
    }

    /**
     * Get a Converter for a given pair of atomic types. These can be primitive types,
     * derived types, or user-defined types. The converter implements the casting rules.
     *
     * @param source the source type
     * @param target the target type
     * @return a Converter if conversion between the two types is possible; or null otherwise
     */

    /*@Nullable*/
    public Converter getConverter(AtomicType source, AtomicType target) {
        // Handle some common cases first
        if (source == target) {
            return StringConverter.IdentityConverter.INSTANCE;
        } else if (source == BuiltInAtomicType.STRING || source == BuiltInAtomicType.UNTYPED_ATOMIC) {
            return target.getStringConverter(this);
        } else if (target == BuiltInAtomicType.STRING) {
            return Converter.ToStringConverter.INSTANCE;
        } else if (target == BuiltInAtomicType.UNTYPED_ATOMIC) {
            return Converter.ToUntypedAtomicConverter.INSTANCE;
        }

        int tt = target.getFingerprint();
        int tp = target.getPrimitiveType();
        int st = source.getPrimitiveType();

        if ((st == StandardNames.XS_STRING || st == StandardNames.XS_UNTYPED_ATOMIC) &&
                (tp == StandardNames.XS_STRING || tp == StandardNames.XS_UNTYPED_ATOMIC)) {
            return makeStringConverter(target);
        }

        if (!target.isPrimitiveType()) {
            @SuppressWarnings("RedundantCast")
            AtomicType primTarget = (AtomicType) target.getPrimitiveItemType();
            if (source == primTarget) {
                return new Converter.DownCastingConverter(target, this);
            } else if (st == StandardNames.XS_STRING || st == StandardNames.XS_UNTYPED_ATOMIC) {
                return makeStringConverter(target);
            } else if (tt == StandardNames.XS_ERROR) {
                return new Converter.ConverterToError();
            } else {
                Converter stageOne = getConverter(source, primTarget);
                if (stageOne == null) {
                    return null;
                }
                Converter stageTwo = new Converter.DownCastingConverter(target, this);
                return new Converter.TwoPhaseConverter(stageOne, stageTwo);
            }
        }


        if (st == tt) {
            // we are casting between subtypes of the same primitive type.
            if (Subsumption.derivesFrom(source, target)) {
                return new Converter.UpCastingConverter(target);
            }
            @SuppressWarnings("RedundantCast")
            Converter upcast = new Converter.UpCastingConverter((AtomicType) source.getPrimitiveItemType());
            Converter downcast = new Converter.DownCastingConverter(target, this);
            return new Converter.TwoPhaseConverter(upcast, downcast);
        }

        return switch (tt) {
            case StandardNames.XS_UNTYPED_ATOMIC -> Converter.ToUntypedAtomicConverter.INSTANCE;
            case StandardNames.XS_STRING -> Converter.ToStringConverter.INSTANCE;
            case StandardNames.XS_FLOAT -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        new StringConverter.StringToFloat(this);
                case StandardNames.XS_DOUBLE, StandardNames.XS_DECIMAL, StandardNames.XS_INTEGER,
                     StandardNames.XS_NUMERIC -> Converter.NumericToFloat.INSTANCE;
                case StandardNames.XS_BOOLEAN -> Converter.BooleanToFloat.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_DOUBLE, StandardNames.XS_NUMERIC -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING -> stringToDouble;
                case StandardNames.XS_FLOAT, StandardNames.XS_DECIMAL, StandardNames.XS_INTEGER,
                     StandardNames.XS_NUMERIC -> Converter.NumericToDouble.INSTANCE;
                case StandardNames.XS_BOOLEAN -> Converter.BooleanToDouble.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_DECIMAL -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        StringConverter.StringToDecimal.INSTANCE;
                case StandardNames.XS_FLOAT -> Converter.FloatToDecimal.INSTANCE;
                case StandardNames.XS_DOUBLE -> Converter.DoubleToDecimal.INSTANCE;
                case StandardNames.XS_INTEGER -> Converter.IntegerToDecimal.INSTANCE;
                case StandardNames.XS_NUMERIC -> Converter.NumericToDecimal.INSTANCE;
                case StandardNames.XS_BOOLEAN -> Converter.BooleanToDecimal.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_INTEGER -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        StringConverter.StringToInteger.INSTANCE;
                case StandardNames.XS_FLOAT -> Converter.FloatToInteger.INSTANCE;
                case StandardNames.XS_DOUBLE -> Converter.DoubleToInteger.INSTANCE;
                case StandardNames.XS_DECIMAL -> Converter.DecimalToInteger.INSTANCE;
                case StandardNames.XS_NUMERIC -> Converter.NumericToInteger.INSTANCE;
                case StandardNames.XS_BOOLEAN -> Converter.BooleanToInteger.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_DURATION -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        StringConverter.StringToDuration.INSTANCE;
                case StandardNames.XS_DAY_TIME_DURATION, StandardNames.XS_YEAR_MONTH_DURATION ->
                        new Converter.UpCastingConverter(BuiltInAtomicType.DURATION);
                default -> null;
            };
            case StandardNames.XS_YEAR_MONTH_DURATION -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        StringConverter.StringToYearMonthDuration.INSTANCE;
                case StandardNames.XS_DURATION, StandardNames.XS_DAY_TIME_DURATION ->
                        Converter.DurationToYearMonthDuration.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_DAY_TIME_DURATION -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        StringConverter.StringToDayTimeDuration.INSTANCE;
                case StandardNames.XS_DURATION, StandardNames.XS_YEAR_MONTH_DURATION ->
                        Converter.DurationToDayTimeDuration.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_DATE_TIME -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        new StringConverter.StringToDateTime(this);
                case StandardNames.XS_DATE -> Converter.DateToDateTime.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_TIME -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING -> StringConverter.StringToTime.INSTANCE;
                case StandardNames.XS_DATE_TIME -> Converter.DateTimeToTime.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_DATE -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING -> new StringConverter.StringToDate(this);
                case StandardNames.XS_DATE_TIME -> Converter.DateTimeToDate.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_G_YEAR_MONTH -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        new StringConverter.StringToGYearMonth(this);
                case StandardNames.XS_DATE -> Converter.TwoPhaseConverter.makeTwoPhaseConverter(
                        BuiltInAtomicType.DATE, BuiltInAtomicType.DATE_TIME, BuiltInAtomicType.G_YEAR_MONTH, this);
                case StandardNames.XS_DATE_TIME -> Converter.DateTimeToGYearMonth.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_G_YEAR -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        new StringConverter.StringToGYear(this);
                case StandardNames.XS_DATE ->
                        Converter.TwoPhaseConverter.makeTwoPhaseConverter(BuiltInAtomicType.DATE, BuiltInAtomicType.DATE_TIME,
                                                                          BuiltInAtomicType.G_YEAR, this);
                case StandardNames.XS_DATE_TIME -> Converter.DateTimeToGYear.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_G_MONTH_DAY -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        StringConverter.StringToGMonthDay.INSTANCE;
                case StandardNames.XS_DATE ->
                        Converter.TwoPhaseConverter.makeTwoPhaseConverter(BuiltInAtomicType.DATE, BuiltInAtomicType.DATE_TIME,
                                                                          BuiltInAtomicType.G_MONTH_DAY, this);
                case StandardNames.XS_DATE_TIME -> Converter.DateTimeToGMonthDay.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_G_DAY -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING -> StringConverter.StringToGDay.INSTANCE;
                case StandardNames.XS_DATE ->
                        Converter.TwoPhaseConverter.makeTwoPhaseConverter(BuiltInAtomicType.DATE, BuiltInAtomicType.DATE_TIME,
                                                                          BuiltInAtomicType.G_DAY, this);
                case StandardNames.XS_DATE_TIME -> Converter.DateTimeToGDay.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_G_MONTH -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        StringConverter.StringToGMonth.INSTANCE;
                case StandardNames.XS_DATE ->
                        Converter.TwoPhaseConverter.makeTwoPhaseConverter(BuiltInAtomicType.DATE, BuiltInAtomicType.DATE_TIME,
                                                                          BuiltInAtomicType.G_MONTH, this);
                case StandardNames.XS_DATE_TIME -> Converter.DateTimeToGMonth.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_BOOLEAN -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        StringConverter.StringToBoolean.INSTANCE;
                case StandardNames.XS_FLOAT, StandardNames.XS_DOUBLE, StandardNames.XS_DECIMAL,
                     StandardNames.XS_INTEGER, StandardNames.XS_NUMERIC -> Converter.NumericToBoolean.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_BASE64_BINARY -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        StringConverter.StringToBase64Binary.INSTANCE;
                case StandardNames.XS_HEX_BINARY -> Converter.HexBinaryToBase64Binary.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_HEX_BINARY -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        StringConverter.StringToHexBinary.INSTANCE;
                case StandardNames.XS_BASE64_BINARY -> Converter.Base64BinaryToHexBinary.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_ANY_URI -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        new StringConverter.StringToAnyURI(this);
                default -> null;
            };
            case StandardNames.XS_QNAME -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        new StringConverter.StringToQName(this);
                case StandardNames.XS_NOTATION -> Converter.NotationToQName.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_NOTATION -> switch (st) {
                case StandardNames.XS_UNTYPED_ATOMIC, StandardNames.XS_STRING ->
                        new StringConverter.StringToNotation(this);
                case StandardNames.XS_QNAME -> Converter.QNameToNotation.INSTANCE;
                default -> null;
            };
            case StandardNames.XS_ANY_ATOMIC_TYPE -> StringConverter.IdentityConverter.INSTANCE;
            default -> throw new IllegalArgumentException("Unknown primitive type " + tt);
        };
    }

    /**
     * Get a converter from xs:string to a given built-in atomic type
     */

    public StringConverter getConverterFromString(BuiltInAtomicType target) {
        return switch (target.getFingerprint()) {
            case StandardNames.XS_ANY_ATOMIC_TYPE,
                 StandardNames.XS_STRING -> StringConverter.StringToString.INSTANCE;

            case StandardNames.XS_DECIMAL -> StringConverter.StringToDecimal.INSTANCE;
            case StandardNames.XS_DURATION -> StringConverter.StringToDuration.INSTANCE;
            case StandardNames.XS_G_MONTH -> StringConverter.StringToGMonth.INSTANCE;
            case StandardNames.XS_G_MONTH_DAY -> StringConverter.StringToGMonthDay.INSTANCE;
            case StandardNames.XS_G_DAY -> StringConverter.StringToGDay.INSTANCE;
            case StandardNames.XS_TIME -> StringConverter.StringToTime.INSTANCE;
            case StandardNames.XS_BOOLEAN -> StringConverter.StringToBoolean.INSTANCE;
            case StandardNames.XS_HEX_BINARY -> StringConverter.StringToHexBinary.INSTANCE;
            case StandardNames.XS_BASE64_BINARY -> StringConverter.StringToBase64Binary.INSTANCE;

            case StandardNames.XS_QNAME -> new StringConverter.StringToQName(this);
            case StandardNames.XS_NOTATION -> new StringConverter.StringToNotation(this);
            case StandardNames.XS_ANY_URI -> new StringConverter.StringToAnyURI(this);
            case StandardNames.XS_DOUBLE -> stringToDouble;
            case StandardNames.XS_FLOAT -> new StringConverter.StringToFloat(this);

            case StandardNames.XS_UNTYPED_ATOMIC -> StringConverter.StringToUntypedAtomic.INSTANCE;

            case StandardNames.XS_LANGUAGE -> StringConverter.StringToLanguage.INSTANCE;
            case StandardNames.XS_NORMALIZED_STRING -> StringConverter.StringToNormalizedString.INSTANCE;
            case StandardNames.XS_TOKEN -> StringConverter.StringToToken.INSTANCE;
            case StandardNames.XS_NCNAME -> StringConverter.StringToNCName.TO_NCNAME;
            case StandardNames.XS_NAME -> StringConverter.StringToName.INSTANCE;
            case StandardNames.XS_NMTOKEN -> StringConverter.StringToNMTOKEN.INSTANCE;
            case StandardNames.XS_ID -> StringConverter.StringToNCName.TO_ID;
            case StandardNames.XS_IDREF -> StringConverter.StringToNCName.TO_IDREF;
            case StandardNames.XS_ENTITY -> StringConverter.StringToNCName.TO_ENTITY;

            case StandardNames.XS_INTEGER -> StringConverter.StringToInteger.INSTANCE;
            case StandardNames.XS_NON_POSITIVE_INTEGER -> new StringConverter.StringToIntegerSubtype(target);
            case StandardNames.XS_NEGATIVE_INTEGER -> new StringConverter.StringToIntegerSubtype(target);
            case StandardNames.XS_LONG -> new StringConverter.StringToIntegerSubtype(target);
            case StandardNames.XS_INT -> new StringConverter.StringToIntegerSubtype(target);
            case StandardNames.XS_SHORT -> new StringConverter.StringToIntegerSubtype(target);
            case StandardNames.XS_BYTE -> new StringConverter.StringToIntegerSubtype(target);
            case StandardNames.XS_NON_NEGATIVE_INTEGER -> new StringConverter.StringToIntegerSubtype(target);
            case StandardNames.XS_POSITIVE_INTEGER -> new StringConverter.StringToIntegerSubtype(target);
            case StandardNames.XS_UNSIGNED_LONG -> new StringConverter.StringToIntegerSubtype(target);
            case StandardNames.XS_UNSIGNED_INT -> new StringConverter.StringToIntegerSubtype(target);
            case StandardNames.XS_UNSIGNED_SHORT -> new StringConverter.StringToIntegerSubtype(target);
            case StandardNames.XS_UNSIGNED_BYTE -> new StringConverter.StringToIntegerSubtype(target);

            case StandardNames.XS_DAY_TIME_DURATION -> StringConverter.StringToDayTimeDuration.INSTANCE;
            case StandardNames.XS_YEAR_MONTH_DURATION -> StringConverter.StringToYearMonthDuration.INSTANCE;

            case StandardNames.XS_G_YEAR -> new StringConverter.StringToGYear(this);
            case StandardNames.XS_G_YEAR_MONTH -> new StringConverter.StringToGYearMonth(this);
            case StandardNames.XS_DATE -> new StringConverter.StringToDate(this);
            case StandardNames.XS_DATE_TIME -> new StringConverter.StringToDateTime(this);
            case StandardNames.XS_DATE_TIME_STAMP -> new StringConverter.StringToDateTimeStamp(this);

            default -> throw new UnsupportedOperationException();


        };
    }

    /**
     * Static factory method to get a StringConverter for a specific target type
     *
     * @param targetType the target type of the conversion
     * @return a StringConverter that can be used to convert strings to the target type, or to
     *         validate strings against the target type
     */

    /*@NotNull*/
    public StringConverter makeStringConverter(final AtomicType targetType) {

        int tt = targetType.getPrimitiveType();
        if (targetType.isBuiltInType()) {
            if (tt == StandardNames.XS_STRING) {
                return switch (targetType.getFingerprint()) {
                    case StandardNames.XS_STRING -> StringConverter.StringToString.INSTANCE;
                    case StandardNames.XS_NORMALIZED_STRING -> StringConverter.StringToNormalizedString.INSTANCE;
                    case StandardNames.XS_TOKEN -> StringConverter.StringToToken.INSTANCE;
                    case StandardNames.XS_LANGUAGE -> StringConverter.StringToLanguage.INSTANCE;
                    case StandardNames.XS_NAME -> StringConverter.StringToName.INSTANCE;
                    case StandardNames.XS_NCNAME -> StringConverter.StringToNCName.TO_NCNAME;
                    case StandardNames.XS_ID -> StringConverter.StringToNCName.TO_ID;
                    case StandardNames.XS_IDREF -> StringConverter.StringToNCName.TO_IDREF;
                    case StandardNames.XS_ENTITY -> StringConverter.StringToNCName.TO_ENTITY;
                    case StandardNames.XS_NMTOKEN -> StringConverter.StringToNMTOKEN.INSTANCE;
                    default -> throw new AssertionError("Unknown built-in subtype of xs:string");
                };
            } else if (tt == StandardNames.XS_UNTYPED_ATOMIC) {
                return StringConverter.StringToUntypedAtomic.INSTANCE;
            } else if (targetType.isPrimitiveType()) {
                // converter to built-in types unrelated to xs:string
                Converter converter = getConverter(BuiltInAtomicType.STRING, targetType);
                assert converter != null;
                return (StringConverter) converter;
            } else if (tt == StandardNames.XS_INTEGER) {
                return new StringConverter.StringToIntegerSubtype((BuiltInAtomicType) targetType);
            } else {
                switch (targetType.getFingerprint()) {
                    case StandardNames.XS_DAY_TIME_DURATION:
                        return StringConverter.StringToDayTimeDuration.INSTANCE;
                    case StandardNames.XS_YEAR_MONTH_DURATION:
                        return StringConverter.StringToYearMonthDuration.INSTANCE;
                    case StandardNames.XS_DATE_TIME_STAMP:
                        StringConverter first = new StringConverter.StringToDateTime(this);
                        Converter.DownCastingConverter second = new Converter.DownCastingConverter(targetType, this);
                        return new StringConverter.StringToNonStringDerivedType(first, second);
                    default:
                        throw new AssertionError("Unknown built in type " + targetType);
                }
            }
        } else {
            if (tt == StandardNames.XS_STRING) {
                if (targetType.getBuiltInBaseType() == BuiltInAtomicType.STRING) {
                    // converter to user-defined subtypes of xs:string
                    return new StringConverter.StringToStringSubtype(this, targetType);
                } else {
                    // converter to user-defined subtypes of built-in subtypes of xs:string
                    return new StringConverter.StringToDerivedStringSubtype(this, targetType);
                }
            } else {
                // converter to user-defined types derived from types other than xs:string
                @SuppressWarnings("RedundantCast")
                StringConverter first = ((AtomicType)targetType.getPrimitiveItemType()).getStringConverter(this);
                Converter.DownCastingConverter second = new Converter.DownCastingConverter(targetType, this);
                return new StringConverter.StringToNonStringDerivedType(first, second);
            }
        }

    }

}

