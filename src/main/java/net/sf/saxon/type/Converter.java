////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.value.*;

import java.math.BigDecimal;

/**
 * A converter implements conversions from one atomic type to another - that is, it implements the casting
 * rules for a (source type, destination type) pair.
 * <p>There is potentially one Converter implementation for each pair of (source, target) classes; though in many
 * cases the same implementation handles a number of such pairs.</p>
 * <p>In cases where the conversion rules are fixed (specifically, where they do not depend on differences between
 * versions of the XSD or QT specifications), the appropriate Converter can be obtained as a static constant, for example
 * {@link BooleanToDouble#INSTANCE}. In other cases the converter is parameterized by the {@link ConversionRules} object,
 * and should be obtained by calling the appropriate factory method on the ConversionRules.</p>
 * <p>Where the source type of the conversion is xs:string, the converter will always be a subclass of
 * {@link StringConverter}</p>
 */
public abstract class Converter {


    /**
     * Convenience method to convert a given value to a given type. Note: it is more efficient
     * to obtain a converter in advance and to reuse it for multiple conversions
     *
     * @param value      the value to be converted
     * @param targetType the type to which the value is to be converted
     * @param rules      the conversion rules for the configuration
     * @return the converted value
     * @throws ValidationException if conversion fails
     */

    public static AtomicValue convert(AtomicValue value, AtomicType targetType, ConversionRules rules)
            throws ValidationException {
        Converter converter = rules.getConverter(value.getPrimitiveType(), targetType);
        if (converter == null) {
            ValidationFailure ve = new ValidationFailure("Cannot convert value from " + value.getPrimitiveType() + " to " + targetType);
            ve.setErrorCode("FORG0001");
            throw ve.makeException();
        }
        return converter.convert(value).asAtomic();
    }


    // All converters can hold a reference to the conversion rules, though many don't
    private ConversionRules conversionRules;

    // Protected constructor for a Converter

    protected Converter() {
    }

    /**
     * Construct a converter with a given set of conversion rules. For use in constructing subclasses
     *
     * @param rules the conversion rules for the configuration
     */
    protected Converter(ConversionRules rules) {
        setConversionRules(rules);
    }

    /**
     * Convert an atomic value from the source type to the target type
     *
     * @param input the atomic value to be converted, which the caller guarantees to be of the appropriate
     *              type for the converter. The results are undefined if the value is of the wrong type;
     *              possible outcomes are (apparent) success, or a ClassCastException.
     * @return the result of the conversion, as an {@link AtomicValue}, if conversion succeeds, or a {@link ValidationFailure}
     *         object describing the reasons for failure if conversion is not possible. Note that the ValidationFailure
     *         object is not (and does not contain) an exception, because it does not necessarily result in an error being
     *         thrown, and creating exceptions on non-failure paths is expensive.
     */

    /*@NotNull*/
    public abstract ConversionResult convert(AtomicValue input);

    /**
     * Set the conversion rules to be used by this Converter
     *
     * @param rules the conversion rules
     */

    public final void setConversionRules(ConversionRules rules) {
        this.conversionRules = rules;
    }

    /**
     * Get the conversion rules to be used by this Converter
     *
     * @return the conversion rules
     */


    public final ConversionRules getConversionRules() {
        return conversionRules;
    }

    /**
     * Ask if this converter will always succeed
     *
     * @return true if this Converter will never return a ValidationFailure
     */

    public boolean isAlwaysSuccessful() {
        return false;
    }

    /**
     * Provide a namespace resolver, needed for conversion to namespace-sensitive types such as QName and NOTATION.
     * The resolver is ignored if the target type is not namespace-sensitive
     *
     * @param resolver the namespace resolver to be used
     * @return a new Converter customised with the supplied namespace context. The original Converter is
     * unchanged (see bug 2754)
     */

    public Converter setNamespaceResolver(NamespaceResolver resolver) {
        return this;
        // no action
    }

    /**
     * Get the namespace resolver if one has been supplied
     *
     * @return the namespace resolver, or null if none has been supplied
     */

    /*@Nullable*/
    public NamespaceResolver getNamespaceResolver() {
        return null;
    }

    /**
     * Specialisation for converters that always succeed
     */

    public static abstract class UnfailingConverter extends Converter {
        @Override
        public abstract AtomicValue convert(AtomicValue input);

        @Override
        public final boolean isAlwaysSuccessful() {
            return true;
        }
    }

    /**
     * Converter that does nothing - it returns the input unchanged
     */

    public static class IdentityConverter extends Converter {
        public final static IdentityConverter INSTANCE = new IdentityConverter();

        @Override
        public ConversionResult convert(AtomicValue input) {
            return input;
        }

        @Override
        public boolean isAlwaysSuccessful() {
            return true;
        }

    }


    /**
     * Converter that does nothing except change the type annotation of the value. The caller
     * is responsible for ensuring that this type annotation is legimite, that is, that the value
     * is in the value space of this type
     */

    public static class UpCastingConverter extends UnfailingConverter {
        private final AtomicType newTypeAnnotation;

        public UpCastingConverter(AtomicType annotation) {
            this.newTypeAnnotation = annotation;
        }

        @Override
        public AtomicValue convert(/*@NotNull*/ AtomicValue input) {
            return input.copyAsSubType(newTypeAnnotation);
        }

    }

    /**
     * Converter that checks that a value belonging to a supertype is a valid
     * instance of a subtype, and returns an instance of the subtype
     */

    public static class DownCastingConverter extends Converter {
        private final AtomicType newType;

        public DownCastingConverter(AtomicType annotation, ConversionRules rules) {
            this.newType = annotation;
            setConversionRules(rules);
        }

        public AtomicType getTargetType() {
            return newType;
        }

        /*@NotNull*/
        @Override
        public ConversionResult convert(/*@NotNull*/ AtomicValue input) {
            return convert(input, input.getCanonicalLexicalRepresentation());
        }

        public ConversionResult convert(AtomicValue input, CharSequence lexicalForm) {
            ValidationFailure f = newType.validate(input, lexicalForm, getConversionRules());
            if (f == null) {
                // success
                return input.copyAsSubType(newType);
            } else {
                // validation failed
                return f;
            }
        }

        public ValidationFailure validate(AtomicValue input, CharSequence lexicalForm) {
            return newType.validate(input, lexicalForm, getConversionRules());
        }
    }

    /**
     * Converter that operates in two phases, via an intermediate type
     */

    public static class TwoPhaseConverter extends Converter {
        private final Converter phaseOne;
        private final Converter phaseTwo;

        public TwoPhaseConverter(Converter phaseOne, Converter phaseTwo) {
            this.phaseOne = phaseOne;
            this.phaseTwo = phaseTwo;
        }

        /*@Nullable*/
        public static TwoPhaseConverter makeTwoPhaseConverter(
                AtomicType inputType, AtomicType viaType, AtomicType outputType, ConversionRules rules) {
            return new TwoPhaseConverter(
                    rules.getConverter(inputType, viaType),
                    rules.getConverter(viaType, outputType));
        }

        @Override
        public Converter setNamespaceResolver(NamespaceResolver resolver) {
            return new TwoPhaseConverter(phaseOne.setNamespaceResolver(resolver), phaseTwo.setNamespaceResolver(resolver));
        }

        /*@NotNull*/
        @Override
        public ConversionResult convert(AtomicValue input) {
            ConversionResult temp = phaseOne.convert(input);
            if (temp instanceof ValidationFailure) {
                return temp;
            }
            AtomicValue aTemp = (AtomicValue)temp;
            if (phaseTwo instanceof DownCastingConverter) {
                return ((DownCastingConverter) phaseTwo).convert(aTemp, aTemp.getCanonicalLexicalRepresentation());
            } else {
                return phaseTwo.convert(aTemp);
            }
        }

    }

    /**
     * Converts any value to untyped atomic
     */

    public static class ToUntypedAtomicConverter extends UnfailingConverter {
        public static final ToUntypedAtomicConverter INSTANCE = new ToUntypedAtomicConverter();
        @Override
        public UntypedAtomicValue convert(AtomicValue input) {
            return new UntypedAtomicValue(input.getStringValueCS());
        }
    }

    /**
     * Converts any value to a string
     */

    public static class ToStringConverter extends UnfailingConverter {
        public static final ToStringConverter INSTANCE = new ToStringConverter();
        @Override
        public StringValue convert(AtomicValue input) {
            return new StringValue(input.getStringValueCS());
        }
    }

    /**
     * Converts any numeric value to xs:float
     */

    public static class NumericToFloat extends UnfailingConverter {
        public final static NumericToFloat INSTANCE = new NumericToFloat();
        @Override
        public FloatValue convert(AtomicValue input) {
            return new FloatValue(((NumericValue)input).getFloatValue());
        }
    }

    /**
     * Converts a boolean to an xs:float
     */

    public static class BooleanToFloat extends UnfailingConverter {
        public final static BooleanToFloat INSTANCE = new BooleanToFloat();
        @Override
        public FloatValue convert(AtomicValue input) {
            return new FloatValue(((BooleanValue)input).getBooleanValue() ? 1.0f : 0.0f);
        }
    }

    /**
     * Converts any numeric value to a double.
     */

    public static class NumericToDouble extends UnfailingConverter {
        public static final NumericToDouble INSTANCE = new NumericToDouble();
        @Override
        public DoubleValue convert(AtomicValue input) {
            if (input instanceof DoubleValue) {
                return (DoubleValue)input;
            } else {
                return new DoubleValue(((NumericValue)input).getDoubleValue());
            }
        }
    }

    /**
     * Converts a boolean to a double
     */

    public static class BooleanToDouble extends UnfailingConverter {
        public final static BooleanToDouble INSTANCE = new BooleanToDouble();
        @Override
        public DoubleValue convert(AtomicValue input) {
            return new DoubleValue(((BooleanValue) input).getBooleanValue() ? 1.0e0 : 0.0e0);
        }
    }

    /**
     * Convers a double to a decimal
     */

    public static class DoubleToDecimal extends Converter {
        public final static DoubleToDecimal INSTANCE = new DoubleToDecimal();
        @Override
        public ConversionResult convert(AtomicValue input) {
            try {
                return new BigDecimalValue(((DoubleValue) input).getDoubleValue());
            } catch (ValidationException e) {
                return e.getValidationFailure();
            }
        }
    }

    /**
     * Converts a float to a decimal
     */

    public static class FloatToDecimal extends Converter {
        public final static FloatToDecimal INSTANCE = new FloatToDecimal();
        @Override
        public ConversionResult convert(AtomicValue input) {
            try {
                return new BigDecimalValue(((FloatValue) input).getFloatValue());
            } catch (ValidationException e) {
                return e.getValidationFailure();
            }
        }
    }

    /**
     * Converts an integer to a decimal
     */

    public static class IntegerToDecimal extends UnfailingConverter {
        public final static IntegerToDecimal INSTANCE = new IntegerToDecimal();
        @Override
        public BigDecimalValue convert(AtomicValue input) {
            if (input instanceof Int64Value) {
                return new BigDecimalValue(((Int64Value) input).longValue());
            } else {
                return new BigDecimalValue(((BigIntegerValue) input).asDecimal());
            }
        }
    }

    /**
     * Converts any numeric value to a decimal
     */

    public static class NumericToDecimal extends Converter {
        public final static NumericToDecimal INSTANCE = new NumericToDecimal();
        @Override
        public ConversionResult convert(AtomicValue input) {
            try {
                BigDecimal decimal = ((NumericValue) input).getDecimalValue();
                return new BigDecimalValue(decimal);
            } catch (ValidationException e) {
                return e.getValidationFailure();
            }
        }
    }

    /**
     * Converts a boolean to a decimal
     */

    public static class BooleanToDecimal extends UnfailingConverter {
        public final static BooleanToDecimal INSTANCE = new BooleanToDecimal();
        @Override
        public BigDecimalValue convert(AtomicValue input) {
            return ((BooleanValue) input).getBooleanValue() ? BigDecimalValue.ONE : BigDecimalValue.ZERO;
        }
    }


    /**
     * Converts a double to an integer
     */

    public static class DoubleToInteger extends Converter {
        public final static DoubleToInteger INSTANCE = new DoubleToInteger();
        @Override
        public ConversionResult convert(AtomicValue input) {
            return IntegerValue.makeIntegerValue((DoubleValue)input);
        }
    }

    /**
     * Converts a float to an integer
     */

    public static class FloatToInteger extends Converter {
        public final static FloatToInteger INSTANCE = new FloatToInteger();
        @Override
        public ConversionResult convert(AtomicValue input) {
            return IntegerValue.makeIntegerValue(new DoubleValue(((FloatValue) input).getDoubleValue()));
        }
    }

    /**
     * Converts a decimal to an integer. Because an xs:integer is an xs:decimal,
     * this must also be prepared to accept an xs:integer
     */

    public static class DecimalToInteger extends UnfailingConverter {
        public final static DecimalToInteger INSTANCE = new DecimalToInteger();
        @Override
        public IntegerValue convert(AtomicValue input) {
            if (input instanceof IntegerValue) {
                return (IntegerValue)input;
            }
            return BigIntegerValue.makeIntegerValue(((BigDecimalValue) input).getDecimalValue().toBigInteger());
        }
    }

    /**
     * Converts any numeric value to an integer.
     */

    public static class NumericToInteger extends Converter {
        public final static NumericToInteger INSTANCE = new NumericToInteger();
        @Override
        public ConversionResult convert(AtomicValue input) {
            NumericValue in = (NumericValue) input;
            try {
                if (in instanceof IntegerValue) {
                    return in;
                } else if (in instanceof DoubleValue) {
                    return IntegerValue.makeIntegerValue((DoubleValue) in);
                } else if (in instanceof FloatValue) {
                    return IntegerValue.makeIntegerValue(new DoubleValue(in.getDoubleValue()));
                } else {
                    return BigIntegerValue.makeIntegerValue(in.getDecimalValue().toBigInteger());
                }
            } catch (ValidationException e) {
                return e.getValidationFailure();
            }
        }
    }

    /**
     * Converts a boolean to an integer
     */

    public static class BooleanToInteger extends UnfailingConverter {
        public final static BooleanToInteger INSTANCE = new BooleanToInteger();
        @Override
        public Int64Value convert(AtomicValue input) {
            return ((BooleanValue) input).getBooleanValue() ? Int64Value.PLUS_ONE : Int64Value.ZERO;
        }
    }

    /**
     * Converts a duration to a dayTimeDuration
     */

    public static class DurationToDayTimeDuration extends UnfailingConverter {
        public final static DurationToDayTimeDuration INSTANCE = new DurationToDayTimeDuration();
        @Override
        public DayTimeDurationValue convert(AtomicValue duration) {
            DurationValue d = (DurationValue)duration;
            if (d.signum() < 0) {
                return new DayTimeDurationValue(-d.getDays(), -d.getHours(), -d.getMinutes(), -d.getSeconds(), -d.getNanoseconds());
            } else {
                return new DayTimeDurationValue(d.getDays(), d.getHours(), d.getMinutes(), d.getSeconds(), d.getNanoseconds());
            }
        }
    }

    /**
     * Converts a duration to a yearMonthDuration
     */

    public static class DurationToYearMonthDuration extends UnfailingConverter {
        public final static DurationToYearMonthDuration INSTANCE = new DurationToYearMonthDuration();
        @Override
        public YearMonthDurationValue convert(AtomicValue input) {
            return YearMonthDurationValue.fromMonths(((DurationValue) input).getTotalMonths());
        }
    }

    /**
     * Converts a date to a dateTime
     */

    public static class DateToDateTime extends UnfailingConverter {
        public final static DateToDateTime INSTANCE = new DateToDateTime();
        @Override
        public DateTimeValue convert(AtomicValue input) {
            return ((DateValue) input).toDateTime();
        }
    }

    /**
     * Converts a dateTime to a date
     */

    public static class DateTimeToDate extends UnfailingConverter {
        public final static DateTimeToDate INSTANCE = new DateTimeToDate();
        @Override
        public DateValue convert(AtomicValue input) {
            DateTimeValue dt = (DateTimeValue)input;
            return new DateValue(dt.getYear(), dt.getMonth(), dt.getDay(), dt.getTimezoneInMinutes(), dt.isXsd10Rules());
        }
    }

    /**
     * Converts a dateTime to a gMonth
     */

    public static class DateTimeToGMonth extends UnfailingConverter {
        public static final DateTimeToGMonth INSTANCE = new DateTimeToGMonth();
        @Override
        public GMonthValue convert(AtomicValue input) {
            DateTimeValue dt = (DateTimeValue) input;
            return new GMonthValue(dt.getMonth(), dt.getTimezoneInMinutes());
        }
    }

    /**
     * Converts a dateTime to a gYearMonth
     */

    public static class DateTimeToGYearMonth extends UnfailingConverter {
        public static final DateTimeToGYearMonth INSTANCE = new DateTimeToGYearMonth();
        @Override
        public GYearMonthValue convert(AtomicValue input) {
            DateTimeValue dt = (DateTimeValue) input;
            return new GYearMonthValue(dt.getYear(), dt.getMonth(), dt.getTimezoneInMinutes(), dt.isXsd10Rules());
        }
    }

    /**
     * Converts a dateTime to a gYear
     */

    public static class DateTimeToGYear extends UnfailingConverter {
        public static final DateTimeToGYear INSTANCE = new DateTimeToGYear();
        @Override
        public GYearValue convert(AtomicValue input) {
            DateTimeValue dt = (DateTimeValue) input;
            return new GYearValue(dt.getYear(), dt.getTimezoneInMinutes(), dt.isXsd10Rules());
        }
    }

    /**
     * Converts a dateTime to a gMonthDay
     */

    public static class DateTimeToGMonthDay extends UnfailingConverter {
        public static final DateTimeToGMonthDay INSTANCE = new DateTimeToGMonthDay();
        @Override
        public GMonthDayValue convert(AtomicValue input) {
            DateTimeValue dt = (DateTimeValue) input;
            return new GMonthDayValue(dt.getMonth(), dt.getDay(), dt.getTimezoneInMinutes());
        }
    }

    /**
     * Converts a dateTime to a gDay
     */

    public static class DateTimeToGDay extends UnfailingConverter {
        public final static DateTimeToGDay INSTANCE = new DateTimeToGDay();
        @Override
        public GDayValue convert(AtomicValue input) {
            DateTimeValue dt = (DateTimeValue) input;
            return new GDayValue(dt.getDay(), dt.getTimezoneInMinutes());
        }
    }

    /**
     * Converts a dateTime to a time
     */

    public static class DateTimeToTime extends UnfailingConverter {
        public final static DateTimeToTime INSTANCE = new DateTimeToTime();
        @Override
        public TimeValue convert(AtomicValue input) {
            DateTimeValue dt = (DateTimeValue) input;
            return new TimeValue(dt.getHour(), dt.getMinute(), dt.getSecond(), dt.getNanosecond(), dt.getTimezoneInMinutes(), "");
        }
    }

    /**
     * Converts a numeric value to a boolean
     */

    public static class NumericToBoolean extends UnfailingConverter {
        public static final NumericToBoolean INSTANCE = new NumericToBoolean();
        @Override
        public BooleanValue convert(AtomicValue input) {
            return BooleanValue.get(((NumericValue)input).effectiveBooleanValue());
        }
    }

    /**
     * Converts base64 to hexBinary
     */

    public static class Base64BinaryToHexBinary extends UnfailingConverter {
        public static final Base64BinaryToHexBinary INSTANCE = new Base64BinaryToHexBinary();
        @Override
        public HexBinaryValue convert(AtomicValue input) {
            return new HexBinaryValue(((Base64BinaryValue)input).getBinaryValue());
        }
    }


    /**
     * Converts hexBinary to base64Binary
     */

    public static class HexBinaryToBase64Binary extends UnfailingConverter {
        public static final HexBinaryToBase64Binary INSTANCE = new HexBinaryToBase64Binary();
        @Override
        public Base64BinaryValue convert(AtomicValue input) {
            return new Base64BinaryValue(((HexBinaryValue)input).getBinaryValue());
        }
    }

    /**
     * Converts Notation to QName
     */

    public static class NotationToQName extends UnfailingConverter {
        public static final NotationToQName INSTANCE = new NotationToQName();
        @Override
        public QNameValue convert(AtomicValue input) {
            return new QNameValue(((NotationValue)input).getStructuredQName(), BuiltInAtomicType.QNAME);
        }
    }

    /**
     * Converts QName to Notation
     */

    public static class QNameToNotation extends UnfailingConverter {
        public static final QNameToNotation INSTANCE = new QNameToNotation();
        @Override
        public NotationValue convert(AtomicValue input) {
            return new NotationValue(((QNameValue)input).getStructuredQName(), BuiltInAtomicType.NOTATION);
        }
    }

    /**
     * Converter that implements the promotion rules to a required type of xs:double
     */

    public static class PromoterToDouble extends Converter {

        /*@Nullable*/ private StringConverter stringToDouble = null;

        /*@NotNull*/
        @Override
        public ConversionResult convert(AtomicValue input) {
            if (input instanceof DoubleValue) {
                return input;
            } else if (input instanceof NumericValue) {
                return new DoubleValue(((NumericValue) input).getDoubleValue());
            } else if (input instanceof UntypedAtomicValue) {
                if (stringToDouble == null) {
                    stringToDouble = BuiltInAtomicType.DOUBLE.getStringConverter(getConversionRules());
                }
                return stringToDouble.convert(input);
            } else {
                ValidationFailure err = new ValidationFailure(
                        "Cannot promote non-numeric value to xs:double");
                err.setErrorCode("XPTY0004");
                return err;
            }
        }
    }

    /**
     * Converter that implements the promotion rules to a required type of xs:float
     */

    public static class PromoterToFloat extends Converter {

        /*@Nullable*/ private StringConverter stringToFloat = null;

        /*@NotNull*/
        @Override
        public ConversionResult convert(/*@NotNull*/ AtomicValue input) {
            if (input instanceof FloatValue) {
                return input;
            } else if (input instanceof DoubleValue) {
                ValidationFailure err = new ValidationFailure(
                        "Cannot promote from xs:double to xs:float");
                err.setErrorCode("XPTY0004");
                return err;
            } else if (input instanceof NumericValue) {
                return new FloatValue((float) ((NumericValue) input).getDoubleValue());
            } else if (input instanceof UntypedAtomicValue) {
                if (stringToFloat == null) {
                    stringToFloat = BuiltInAtomicType.FLOAT.getStringConverter(getConversionRules());
                }
                return stringToFloat.convert(input);
            } else {
                ValidationFailure err = new ValidationFailure(
                        "Cannot promote non-numeric value to xs:double");
                err.setErrorCode("XPTY0004");
                return err;
            }
        }
    }


}

