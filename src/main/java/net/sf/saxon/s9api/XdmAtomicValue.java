////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.*;

/**
 * The class XdmAtomicValue represents an item in an XPath 2.0 sequence that is an atomic value.
 * The value may belong to any of the 19 primitive types defined in XML Schema, or to a type
 * derived from these primitive types, or the XPath 2.0 type xs:untypedAtomic. The type may
 * be either a built-in type or a user-defined type.
 * <p>An <code>XdmAtomicValue</code> is immutable.</p>
 */
public class XdmAtomicValue extends XdmItem {

    private XdmAtomicValue(){}

    protected XdmAtomicValue(AtomicValue value, boolean flag) {
        setValue(value);
    }

    /**
     * Create an <tt>xs:boolean</tt> atomic value
     *
     * @param value the boolean value, true or false
     */

    public XdmAtomicValue(boolean value) {
        this(BooleanValue.get(value), true);
    }

    /**
     * Create an <tt>xs:long</tt> atomic value
     *
     * @param value the <tt>xs:integer</tt> value, as a long
     */

    public XdmAtomicValue(long value) {
        this(Int64Value.makeDerived(value, BuiltInAtomicType.LONG), true);
    }

    /**
     * Create an <tt>xs:int</tt> atomic value
     *
     * @param value the <tt>xs:integer</tt> value, as a long
     */

    public XdmAtomicValue(int value) {
        this(Int64Value.makeDerived(value, BuiltInAtomicType.INT), true);
    }

    /**
     * Create an <tt>xs:short</tt> atomic value
     *
     * @param value the <tt>xs:integer</tt> value, as a long
     */

    public XdmAtomicValue(short value) {
        this(Int64Value.makeDerived(value, BuiltInAtomicType.SHORT), true);
    }

    /**
     * Create an <tt>xs:short</tt> atomic value
     *
     * @param value the <tt>xs:integer</tt> value, as a long
     */

    public XdmAtomicValue(byte value) {
        this(Int64Value.makeDerived(value, BuiltInAtomicType.BYTE), true);
    }

    /**
     * Create an <tt>xs:decimal</tt> atomic value
     *
     * @param value the <tt>xs:decimal</tt> value, as a BigDecimal
     */

    public XdmAtomicValue(BigDecimal value) {
        this(new BigDecimalValue(value), true);
    }

    /**
     * Create an <tt>xs:double</tt> atomic value
     *
     * @param value the <tt>xs:double</tt> value, as a double
     */

    public XdmAtomicValue(double value) {
        this(new DoubleValue(value), true);
    }

    /**
     * Create an <tt>xs:float</tt> atomic value
     *
     * @param value the <tt>xs:float</tt> value, as a float
     */

    public XdmAtomicValue(float value) {
        this(new FloatValue(value), true);
    }

    /**
     * Create an <tt>xs:string</tt> atomic value
     *
     * @param value the <tt>xs:string</tt> value, as a string
     */

    public XdmAtomicValue(String value) {
        this(new StringValue(value), true);
    }

    /**
     * Create an <tt>xs:anyURI</tt> atomic value
     *
     * @param value the <tt>xs:anyURI</tt> value, as a URI
     */

    public XdmAtomicValue(URI value) {
        this(new AnyURIValue(value.toString()), true);
    }

    /**
     * Create an <tt>xs:QName</tt> atomic value
     *
     * @param value the <tt>xs:QName</tt> value, as a QName
     */

    public XdmAtomicValue(QName value) {
        this(new QNameValue(value.getStructuredQName(), BuiltInAtomicType.QNAME), true);
    }


    /**
     * Create an <tt>xs:dateTime</tt> atomic value from a {@link Instant}. The resulting value
     * will always have a timezone component.
     *
     * @param value the <tt>xs:dateTime</tt> value in the form of a {@link Instant}
     * @since 10.0
     */

    public XdmAtomicValue(Instant value) {
        this(DateTimeValue.fromJavaInstant(value), true);
    }

    /**
     * Create an <tt>xs:dateTime</tt> atomic value from a {@link ZonedDateTime}. The resulting value
     * will always have a timezone component. Note that the timezone offset (e.g. -05:00) is retained,
     * but the civil time zone identity (e.g. America/New_York) is lost.
     *
     * @param value the <tt>xs:dateTime</tt> value in the form of a {@link ZonedDateTime}
     * @since 10.0
     */

    public XdmAtomicValue(ZonedDateTime value) {
        this(DateTimeValue.fromZonedDateTime(value), true);
    }

    /**
     * Create an <tt>xs:dateTime</tt> atomic value from a {@link OffsetDateTime}. The resulting value
     * will always have a timezone component. The timezone offset (e.g. -05:00) is retained.
     *
     * @param value the <tt>xs:dateTime</tt> value in the form of a {@link OffsetDateTime}
     * @since 10.0
     */

    public XdmAtomicValue(OffsetDateTime value) {
        this(DateTimeValue.fromOffsetDateTime(value), true);
    }

    /**
     * Create an <tt>xs:dateTime</tt> atomic value from a {@link LocalDateTime}. The resulting value
     * will have no timezone component.
     *
     * @param value the <tt>xs:dateTime</tt> value in the form of a {@link LocalDateTime}
     * @since 10.0
     */

    public XdmAtomicValue(LocalDateTime value) {
        this(DateTimeValue.fromLocalDateTime(value), true);
    }

    /**
     * Create an <tt>xs:date</tt> atomic value from a {@link LocalDate}. The resulting value
     * will have no timezone component.
     *
     * @param value the <tt>xs:dateTime</tt> value in the form of a {@link LocalDate}
     * @since 10.0
     */

    public XdmAtomicValue(LocalDate value) {
        this(new DateValue(value), true);
    }

    /**
     * Construct an atomic value given its lexical representation and the name of the required
     * built-in atomic type.
     * <p>This method cannot be used to construct values that are namespace-sensitive (QNames and Notations)</p>
     *
     * @param lexicalForm the value in the lexical space of the target data type. More strictly, the input
     *                    value before the actions of the whitespace facet for the target data type are applied.
     * @param type        the required atomic type. This must either be one of the built-in
     *                    atomic types defined in XML Schema, or a user-defined type whose definition appears
     *                    in a schema that is known to the Processor. It must not be an abstract type.
     * @throws SaxonApiException if the type is unknown, or is not atomic, or is namespace-sensitive;
     *                           or if the value supplied in <tt>lexicalForm</tt> is not in the lexical space of the specified atomic
     *                           type.
     */

    public XdmAtomicValue(String lexicalForm, ItemType type) throws SaxonApiException {
        net.sf.saxon.type.ItemType it = type.getUnderlyingItemType();
        if (!it.isPlainType()) {
            throw new SaxonApiException("Requested type is not atomic");
        }
        if (((AtomicType) it).isAbstract()) {
            throw new SaxonApiException("Requested type is an abstract type");
        }
        if (((AtomicType) it).isNamespaceSensitive()) {
            throw new SaxonApiException("Requested type is namespace-sensitive");
        }
        try {
            StringConverter converter = ((AtomicType)it).getStringConverter(type.getConversionRules());
            setValue(converter.convertString(lexicalForm).asAtomic());
        } catch (ValidationException e) {
            throw new SaxonApiException(e);
        }
    }



    /**
     * Attempt conversion from an arbitrary Java object
     * @param value the value to convert from. This must either be an instance of {@link AtomicValue}
     *              or {@link QName},
     *              or one of the basic Java types Boolean, Integer, Long, Short, Character, Byte,
     *              String, Double, Float, BigDecimal, BigInteger, URI, ZonedDateTime,
     *              LocalDateTime, or LocalDate
     * @return the result of the conversion if successful
     * @throws IllegalArgumentException if conversion from this kind of Object is not supported
     * @since 10.0 (extended in 10.0 to handle additional data types ZonedDateTime, LocalDateTime, LocalDate)
     */

    public static XdmAtomicValue makeAtomicValue(Object value) {
        if (value instanceof AtomicValue) {
            return new XdmAtomicValue((AtomicValue)value, true);
        } else if (value instanceof Boolean) {
            return new XdmAtomicValue((Boolean)value);
        } else if (value instanceof Integer) {
            return new XdmAtomicValue((Integer) value);
        } else if (value instanceof Long) {
            return new XdmAtomicValue((Long) value);
        } else if (value instanceof Short) {
            return new XdmAtomicValue((Short) value);
        } else if (value instanceof Character) {
            return new XdmAtomicValue((Character) value);
        } else if (value instanceof Byte) {
            return new XdmAtomicValue((Byte) value);
        } else if (value instanceof String) {
            return new XdmAtomicValue((String) value);
        } else if (value instanceof Double) {
            return new XdmAtomicValue((Double) value);
        } else if (value instanceof Float) {
            return new XdmAtomicValue((Float) value);
        } else if (value instanceof BigDecimal) {
            return new XdmAtomicValue((BigDecimal) value);
        } else if (value instanceof BigInteger) {
            return new XdmAtomicValue(IntegerValue.makeIntegerValue((BigInteger) value), true);
        } else if (value instanceof URI) {
            return new XdmAtomicValue((URI) value);
        } else if (value instanceof QName) {
            return new XdmAtomicValue((QName) value);
        } else if (value instanceof ZonedDateTime) {
            return new XdmAtomicValue((ZonedDateTime) value);
        } else if (value instanceof LocalDateTime) {
            return new XdmAtomicValue((LocalDateTime) value);
        } else if (value instanceof LocalDate) {
            return new XdmAtomicValue((LocalDate) value);
        } else if (value instanceof XdmAtomicValue) {
            return (XdmAtomicValue)value;
        } else {
            throw new IllegalArgumentException(value.toString());
        }
    }

    /**
     * Get the underlying implementation object representing the value. This method allows
     * access to lower-level Saxon functionality, including classes and methods that offer
     * no guarantee of stability across releases.
     *
     * @return the underlying implementation object representing the value
     * @since 9.8 (previously inherited from XdmValue which returns a Sequence)
     */
    @Override
    public AtomicValue getUnderlyingValue() {
        return (AtomicValue)super.getUnderlyingValue();
    }

    /**
     * Get the result of converting the atomic value to a string. This has the same
     * effect as the XPath string() function.
     */

    public String toString() {
        return getStringValue();
    }

    /**
     * Get the primitive type of this atomic value, as a QName. The primitive types for this purpose are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is xs:anyAtomicType.
     *
     * @return a QName naming the primitive type of this atomic value. This will always be an atomic type.
     */

    /*@NotNull*/
    public QName getPrimitiveTypeName() {
        AtomicValue value = getUnderlyingValue();
        BuiltInAtomicType type = value.getPrimitiveType();
        return new QName(type.getStructuredQName());
    }


    /**
     * Get the type of this atomic value, as a QName.
     *
     * @return a QName naming the type of this atomic value. This will always be an atomic type.
     */
    public QName getTypeName() {
            AtomicValue value = getUnderlyingValue();
            AtomicType type = value.getItemType();
            return new QName(type.getStructuredQName());
        }

    /**
     * Get the value as a Java object of the nearest equivalent type.
     * <p>The result type is as follows:</p>
     * <table>
     *     <caption>XPath types and corresponding Java classes</caption>
     * <tr><th>XPath type</th>      <th>Java class</th></tr>
     * <tr><td>xs:string</td>       <td>String</td></tr>
     * <tr><td>xs:integer</td>      <td>java.math.BigInteger</td></tr>
     * <tr><td>xs:decimal</td>      <td>java.math.BigDecimal</td></tr>
     * <tr><td>xs:double</td>       <td>Double</td></tr>
     * <tr><td>xs:float</td>        <td>Float</td></tr>
     * <tr><td>xs:boolean</td>      <td>Boolean</td></tr>
     * <tr><td>xs:QName</td>        <td>QName</td></tr>
     * <tr><td>xs:anyURI</td>       <td>String</td></tr>
     * <tr><td>xs:dateTime</td>     <td>ZonedDateTime or LocalDateTime (depending whether a timezone is present)</td></tr>
     * <tr><td>xs:date</td>         <td>LocalDate (dropping any timezone)</td></tr>
     * <tr><td>xs:untypedAtomic</td><td>String</td></tr>
     * <tr><td>Other types</td>     <td>currently String, but this may change in the future</td></tr>
     * </table>
     *
     * @return the value, converted to a Java object of a suitable type
     * @since 10.0 (added additional return types for xs:dateTime and xs:date)
     */

    @SuppressWarnings({"AutoBoxing"})
    public Object getValue() {
        AtomicValue av = getUnderlyingValue();
        if (av instanceof StringValue) {
            return av.getStringValue();
        } else if (av instanceof IntegerValue) {
            return ((IntegerValue) av).asBigInteger();
        } else if (av instanceof DoubleValue) {
            return ((DoubleValue) av).getDoubleValue();
        } else if (av instanceof FloatValue) {
            return ((FloatValue) av).getFloatValue();
        } else if (av instanceof BooleanValue) {
            return ((BooleanValue) av).getBooleanValue();
        } else if (av instanceof BigDecimalValue) {
            return ((BigDecimalValue) av).getDecimalValue();
        } else if (av instanceof DateTimeValue) {
            if (((DateTimeValue) av).hasTimezone()) {
                return ((DateTimeValue) av).toZonedDateTime();
            } else {
                return ((DateTimeValue) av).toLocalDateTime();
            }
        } else if (av instanceof DateValue) {
            return ((DateValue)av).toLocalDate();
        } else if (av instanceof QNameValue) {
            QNameValue q = (QNameValue) av;
            return new QName(q.getPrefix(), q.getNamespaceURI(), q.getLocalName());
        } else {
            return av.getStringValue();
        }
    }

    /**
     * Get the value converted to a boolean using the XPath casting rules
     *
     * @return the result of converting to a boolean (Note: this is not the same as the
     *         effective boolean value).
     * @throws SaxonApiException if the value cannot be cast to a boolean
     */

    public boolean getBooleanValue() throws SaxonApiException {
        AtomicValue av = getUnderlyingValue();
        if (av instanceof BooleanValue) {
            return ((BooleanValue) av).getBooleanValue();
        } else if (av instanceof NumericValue) {
            return !av.isNaN() && ((NumericValue) av).signum() != 0;
        } else if (av instanceof StringValue) {
            String s = av.getStringValue().trim();
            return "1".equals(s) || "true".equals(s);
        } else {
            throw new SaxonApiException("Cannot cast item to a boolean");
        }
    }

    /**
     * Get the value converted to an integer using the XPath casting rules
     *
     * @return the result of converting to an integer
     * @throws SaxonApiException if the value cannot be cast to an integer
     */

    public long getLongValue() throws SaxonApiException {
        AtomicValue av = getUnderlyingValue();
        if (av instanceof BooleanValue) {
            return ((BooleanValue) av).getBooleanValue() ? 0L : 1L;
        } else if (av instanceof NumericValue) {
            try {
                return ((NumericValue) av).longValue();
            } catch (XPathException e) {
                throw new SaxonApiException("Cannot cast item to an integer");
            }
        } else if (av instanceof StringValue) {
            StringToDouble converter = StringToDouble.getInstance();
            return (long) converter.stringToNumber(av.getStringValueCS());
        } else {
            throw new SaxonApiException("Cannot cast item to an integer");
        }
    }

    /**
     * Get the value converted to a double using the XPath casting rules.
     * <p>If the value is a string, the XSD 1.1 rules are used, which means that the string
     * "+INF" is recognised.</p>
     *
     * @return the result of converting to a double
     * @throws SaxonApiException if the value cannot be cast to a double
     */

    public double getDoubleValue() throws SaxonApiException {
        AtomicValue av = getUnderlyingValue();
        if (av instanceof BooleanValue) {
            return ((BooleanValue) av).getBooleanValue() ? 0.0 : 1.0;
        } else if (av instanceof NumericValue) {
            return ((NumericValue) av).getDoubleValue();
        } else if (av instanceof StringValue) {
            try {
                StringToDouble converter = StringToDouble11.getInstance();
                return converter.stringToNumber(av.getStringValueCS());
            } catch (NumberFormatException e) {
                throw new SaxonApiException(e.getMessage());
            }
        } else {
            throw new SaxonApiException("Cannot cast item to a double");
        }
    }

    /**
     * Get the value converted to a decimal using the XPath casting rules
     *
     * @return the result of converting to a decimal
     * @throws SaxonApiException if the value cannot be cast to a double
     */

    public BigDecimal getDecimalValue() throws SaxonApiException {
        AtomicValue av = getUnderlyingValue();
        if (av instanceof BooleanValue) {
            return ((BooleanValue) av).getBooleanValue() ? BigDecimal.ZERO : BigDecimal.ONE;
        } else if (av instanceof NumericValue) {
            try {
                return ((NumericValue) av).getDecimalValue();
            } catch (XPathException e) {
                throw new SaxonApiException("Cannot cast item to a decimal");
            }
        } else if (av instanceof StringValue) {
            return new BigDecimal(av.getStringValueCS().toString());
        } else {
            throw new SaxonApiException("Cannot cast item to a decimal");
        }
    }

    /**
     * For an XdmAtomicValue representing an xs:QName or xs:NOTATION value,
     * get the value as an instance of QName
     * @return the corresponding QName if the value is an xs:QName or xs:NOTATION,
     * or null otherwise
     */

    public QName getQNameValue() {
        AtomicValue av = getUnderlyingValue();
        if (av instanceof QualifiedNameValue) {
            return new QName(((QualifiedNameValue)av).getStructuredQName());
        } else {
            return null;
        }
    }

    /**
     * For an XdmAtomicValue representing an xs:dateTime value including timezone,
     * get the value as an instance of {@link Instant}
     *
     * @return the corresponding Instant if the value is an xs:dateTime including
     * a timezone; otherwise null
     * @since 10.0
     */

    public Instant getInstant() {
        AtomicValue av = getUnderlyingValue();
        if (av instanceof DateTimeValue && ((DateTimeValue)av).hasTimezone()) {
            return ((DateTimeValue) av).toJavaInstant();
        } else {
            return null;
        }
    }

    /**
     * For an XdmAtomicValue representing an xs:dateTime value including timezone,
     * get the value as an instance of {@link ZonedDateTime}
     *
     * @return the corresponding ZonedDateTime if the value is an xs:dateTime including
     * a timezone; otherwise null
     * @since 10.0
     */

    public ZonedDateTime getZonedDateTime() {
        AtomicValue av = getUnderlyingValue();
        if (av instanceof DateTimeValue && ((DateTimeValue) av).hasTimezone()) {
            return ((DateTimeValue) av).toZonedDateTime();
        } else {
            return null;
        }
    }

    /**
     * For an XdmAtomicValue representing an xs:dateTime value including timezone,
     * get the value as an instance of {@link OffsetDateTime}
     *
     * @return the corresponding OffsetDateTime if the value is an xs:dateTime including
     * a timezone; otherwise null
     * @since 10.0
     */

    public OffsetDateTime getOffsetDateTime() {
        AtomicValue av = getUnderlyingValue();
        if (av instanceof DateTimeValue && ((DateTimeValue) av).hasTimezone()) {
            return ((DateTimeValue) av).toOffsetDateTime();
        } else {
            return null;
        }
    }

    /**
     * For an XdmAtomicValue representing an xs:dateTime value,
     * get the value as an instance of {@link LocalDateTime}. Any timezone
     * information in the value is discarded
     *
     * @return the corresponding LocalDateTime if the value is an xs:dateTime; otherwise null
     * @since 10.0
     */

    public LocalDateTime getLocalDateTime() {
        AtomicValue av = getUnderlyingValue();
        if (av instanceof DateTimeValue) {
            return ((DateTimeValue) av).toLocalDateTime();
        } else {
            return null;
        }
    }

    /**
     * For an XdmAtomicValue representing an xs:date value,
     * get the value as an instance of {@link LocalDate}. Any timezone
     * information in the value is discarded
     *
     * @return the corresponding LocalDate if the value is an xs:date; otherwise null
     * @since 10.0
     */

    public LocalDate getLocalDate() {
        AtomicValue av = getUnderlyingValue();
        if (av instanceof DateValue) {
            return ((DateValue) av).toLocalDate();
        } else {
            return null;
        }
    }

    /**
     * Compare values for equality.
     * Two atomic values are considered equal if they are equal according to the
     * rules of the op:is-same-key() operation, used when comparing keys in maps
     * @param other the value to be compared
     * @return true if the values are deemed equal
     */

    public boolean equals(Object other) {
        if (other instanceof XdmAtomicValue) {
            AtomicMatchKey a = getUnderlyingValue().asMapKey();
            AtomicMatchKey b = ((XdmAtomicValue)other).getUnderlyingValue().asMapKey();
            return a.equals(b);
        } else {
            return false;
        }
    }

    /**
     * Get a hashcode that reflects the rules for equality matching
     * @return a suitable hashcode
     */

    @Override
    public int hashCode() {
        return getUnderlyingValue().asMapKey().hashCode();
    }
}

