////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.saxonica.xqj;

import net.sf.saxon.Configuration;
import net.sf.saxon.dom.DOMObjectModel;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Sender;
import net.sf.saxon.expr.EarlyEvaluationContext;
import net.sf.saxon.expr.JPConverter;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.JavaExternalObjectType;
import net.sf.saxon.value.*;
import org.w3c.dom.Node;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.stax.StAXSource;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQItemAccessor;
import javax.xml.xquery.XQItemType;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * This class provides all the conversion methods used to convert data between XDM values
 * and Java values in the XQJ API. At one time the XQJ specification defined such a class,
 * and it has been retained in the Saxon implementation.
 * <p>
 * This handler implements the mappings defined in the XQJ specification. In addition,
 * it defines the following mappings, which are applied after those defined in XQJ:</p>
 * <p>For fromObject:</p>
 * <ul>
 * <li>If the supplied object is an instance of javax.xml.transform.Source, a document
 * node is constructed from the source and the resulting node is returned as the Item</li>
 * <li>If the supplied object is an instance of javax.xml.stream.XMLStreamReader, a document
 * node is constructed from the XMLStreamReader and the resulting node is returned as the Item</li>
 * <li>If the supplied object is
 * </ul>
 */
public class StandardObjectConverter implements ObjectConverter {

    Configuration config;

    /**
     * CreateCharacter an instance of the class
     *
     * @param factory the factory object
     */

    public StandardObjectConverter(/*@NotNull*/ SaxonXQDataFactory factory) {
        config = factory.getConfiguration();
    }

    //@SuppressWarnings({"ConstantConditions"})
    /*@Nullable*/
    @Override
    public Object toObject(/*@NotNull*/ XQItemAccessor xqItemAccessor) throws XQException {
        Item item = ((SaxonXQItemAccessor) xqItemAccessor).getSaxonItem();
        if (item instanceof AtomicValue) {
            AtomicValue p = (AtomicValue) item;
            int t = p.getItemType().getPrimitiveType();
            switch (t) {
                case StandardNames.XS_ANY_URI:
                    return p.getStringValue();
                case StandardNames.XS_BASE64_BINARY:
                    return ((Base64BinaryValue) p).getBinaryValue();
                case StandardNames.XS_BOOLEAN:
                    return ((BooleanValue) p).getBooleanValue();
                case StandardNames.XS_DATE:
                    return new SaxonXMLGregorianCalendar((CalendarValue) p);
                case StandardNames.XS_DATE_TIME:
                    return new SaxonXMLGregorianCalendar((CalendarValue) p);
                case StandardNames.XS_DECIMAL:
                    return ((BigDecimalValue) p).getDecimalValue();
                case StandardNames.XS_DOUBLE:
                    return ((DoubleValue) p).getDoubleValue();
                case StandardNames.XS_DURATION:
                    return new SaxonDuration((DurationValue) p);
                case StandardNames.XS_FLOAT:
                    return ((FloatValue) p).getFloatValue();
                case StandardNames.XS_G_DAY:
                case StandardNames.XS_G_MONTH:
                case StandardNames.XS_G_MONTH_DAY:
                case StandardNames.XS_G_YEAR:
                case StandardNames.XS_G_YEAR_MONTH:
                    return new SaxonXMLGregorianCalendar((CalendarValue) p);
                case StandardNames.XS_HEX_BINARY:
                    return ((HexBinaryValue) p).getBinaryValue();
                case StandardNames.XS_INTEGER:
                    if (p instanceof BigIntegerValue) {
                        return ((BigIntegerValue) p).asBigInteger();
                    } else {
                        int sub = p.getItemType().getFingerprint();
                        switch (sub) {
                            case StandardNames.XS_INTEGER:
                            case StandardNames.XS_NEGATIVE_INTEGER:
                            case StandardNames.XS_NON_NEGATIVE_INTEGER:
                            case StandardNames.XS_NON_POSITIVE_INTEGER:
                            case StandardNames.XS_POSITIVE_INTEGER:
                            case StandardNames.XS_UNSIGNED_LONG:
                                return BigInteger.valueOf(((Int64Value) p).longValue());
                            case StandardNames.XS_BYTE:
                                return (byte) ((Int64Value) p).longValue();
                            case StandardNames.XS_INT:
                            case StandardNames.XS_UNSIGNED_SHORT:
                                return (int) ((Int64Value) p).longValue();
                            case StandardNames.XS_LONG:
                            case StandardNames.XS_UNSIGNED_INT:
                                return ((Int64Value) p).longValue();
                            case StandardNames.XS_SHORT:
                            case StandardNames.XS_UNSIGNED_BYTE:
                                return (short) ((Int64Value) p).longValue();
                            default:
                                throw new XQException("Unrecognized integer subtype " + sub);
                        }
                    }
                case StandardNames.XS_QNAME:
                    return ((QualifiedNameValue) p).toJaxpQName();
                case StandardNames.XS_STRING:
                case StandardNames.XS_UNTYPED_ATOMIC:
                    return p.getStringValue();
                case StandardNames.XS_TIME:
                    return new SaxonXMLGregorianCalendar((CalendarValue) p);
                case StandardNames.XS_DAY_TIME_DURATION:
                    return new SaxonDuration((DurationValue) p);
                case StandardNames.XS_YEAR_MONTH_DURATION:
                    return new SaxonDuration((DurationValue) p);
                default:
                    throw new XQException("unsupported type");
            }
        } else {
            return NodeOverNodeInfo.wrap((NodeInfo) item);
        }
    }

    /**
     * Convert a Java object to a Saxon Item
     *
     * @param value the Java object. If null is supplied, null is returned.
     * @return the corresponding Item
     * @throws XQException if the object cannot be converted
     */

    /*@Nullable*/
    @Override
    public Item convertToItem(/*@NotNull*/ Object value) throws XQException {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Boolean) {
                return BooleanValue.get((Boolean) value);
            } else if (value instanceof byte[]) {
                return new HexBinaryValue((byte[]) value);
            } else if (value instanceof Byte) {
                return new Int64Value((Byte) value, BuiltInAtomicType.BYTE, false);
            } else if (value instanceof Float) {
                return new FloatValue((Float) value);
            } else if (value instanceof Double) {
                return new DoubleValue((Double) value);
            } else if (value instanceof Integer) {
                return new Int64Value((Integer) value, BuiltInAtomicType.INT, false);
            } else if (value instanceof Long) {
                return new Int64Value((Long) value, BuiltInAtomicType.LONG, false);
            } else if (value instanceof Short) {
                return new Int64Value((Short) value, BuiltInAtomicType.SHORT, false);
            } else if (value instanceof String) {
                return new StringValue((String) value);
            } else if (value instanceof BigDecimal) {
                return new BigDecimalValue((BigDecimal) value);
            } else if (value instanceof BigInteger) {
                return new BigIntegerValue((BigInteger) value);
            } else if (value instanceof SaxonDuration) {
                return ((SaxonDuration) value).getDurationValue();
            } else if (value instanceof Duration) {
                // this is simpler and safer (but perhaps slower) than extracting all the components
                return DurationValue.makeDuration(value.toString()).asAtomic();
            } else if (value instanceof SaxonXMLGregorianCalendar) {
                return ((SaxonXMLGregorianCalendar) value).toCalendarValue();
            } else if (value instanceof XMLGregorianCalendar) {
                XMLGregorianCalendar g = (XMLGregorianCalendar) value;
                QName gtype = g.getXMLSchemaType();
                if (gtype.equals(DatatypeConstants.DATETIME)) {
                    return DateTimeValue.makeDateTimeValue(value.toString(), config.getConversionRules()).asAtomic();
                } else if (gtype.equals(DatatypeConstants.DATE)) {
                    return DateValue.makeDateValue(value.toString(), config.getConversionRules()).asAtomic();
                } else if (gtype.equals(DatatypeConstants.TIME)) {
                    return TimeValue.makeTimeValue(value.toString()).asAtomic();
                } else if (gtype.equals(DatatypeConstants.GYEAR)) {
                    return GYearValue.makeGYearValue(value.toString(), config.getConversionRules()).asAtomic();
                } else if (gtype.equals(DatatypeConstants.GYEARMONTH)) {
                    return GYearMonthValue.makeGYearMonthValue(value.toString(), config.getConversionRules()).asAtomic();
                } else if (gtype.equals(DatatypeConstants.GMONTH)) {
                    return GMonthValue.makeGMonthValue(value.toString()).asAtomic();
                } else if (gtype.equals(DatatypeConstants.GMONTHDAY)) {
                    return GMonthDayValue.makeGMonthDayValue(value.toString()).asAtomic();
                } else if (gtype.equals(DatatypeConstants.GDAY)) {
                    return GDayValue.makeGDayValue(value.toString()).asAtomic();
                } else {
                    throw new AssertionError("Unknown Gregorian date type");
                }
            } else if (value instanceof QName) {
                QName q = (QName) value;
                return new QNameValue(q.getPrefix(), q.getNamespaceURI(), q.getLocalPart(),
                        BuiltInAtomicType.QNAME, false);
            } else if (value instanceof Node) {
                JPConverter jp = DOMObjectModel.getInstance().getJPConverter(Node.class, config);
                return SequenceTool.asItem(jp.convert(value, new EarlyEvaluationContext(config)));
                //return Value.asItem(DOMObjectModel.getInstance().convertObjectToXPathValue(value, config));
            } else if (value instanceof Source) {
                // Saxon extension to the XQJ specification
                PipelineConfiguration pipe = config.makePipelineConfiguration();
                TinyBuilder b = new TinyBuilder(pipe);
                b.setStatistics(config.getTreeStatistics().SOURCE_DOCUMENT_STATISTICS);
                Sender.send((Source) value, b, null);
                NodeInfo node = b.getCurrentRoot();
                b.reset();
                return node;
            } else if (value instanceof XMLStreamReader) {
                // Saxon extension to the XQJ specification
                PipelineConfiguration pipe = config.makePipelineConfiguration();
                TinyBuilder b = new TinyBuilder(pipe);
                b.setStatistics(config.getTreeStatistics().SOURCE_DOCUMENT_STATISTICS);
                Sender.send(new StAXSource((XMLStreamReader)value), b, null);
                NodeInfo node = b.getCurrentRoot();
                b.reset();
                return node;
            } else {
                throw new XPathException("Java object cannot be converted to an XQuery value");
            }
        } catch (XPathException e) {
            XQException xqe = new XQException(e.getMessage());
            xqe.initCause(e);
            throw xqe;
        }
    }

    /**
     * Convert a Java object to an Item, when a required type has been specified. Note that Saxon only calls
     * this method when none of the standard conversions defined in the XQJ specification is able to handle
     * the object.
     *
     * @param value the supplied Java object. If null is supplied, null is returned.
     * @param type  the required XPath data type
     * @return the Item that results from the conversion
     * @throws XQException if the Java object cannot be converted to an XQItem
     */

    /*@Nullable*/
    @Override
    public Item convertToItem(Object value, /*@NotNull*/ XQItemType type) throws XQException {
        if (value == null) {
            return null;
        }
        if (((SaxonXQItemType) type).getSaxonItemType() instanceof JavaExternalObjectType) {
            Item result = (ExternalObject<Object>) new ObjectValue<>(value);
            try {
                if (((SaxonXQItemType) type).getSaxonItemType().matches(result, config.getTypeHierarchy())) {
                    return result;
                } else {
                    throw new XQException("The result of wrapping an object of class " + value.getClass().getName() +
                            " does not match the required type " + type);
                }
            } catch (XPathException e) {
                // Fall through
            }
        }
        throw new XQException("Supplied Java object cannot be converted to an XQItem");

    }


}
