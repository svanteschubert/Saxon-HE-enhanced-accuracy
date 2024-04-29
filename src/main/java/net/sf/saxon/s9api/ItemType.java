////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.s9api.streams.Step;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.NumericValue;

import java.util.function.Predicate;

/**
 * An item type, as defined in the XPath/XQuery specifications.
 * <p>This class contains a number of static constant fields
 * referring to instances that represent simple item types, such as
 * <code>item()</code>, <code>node()</code>, and <code>xs:anyAtomicType</code>. These named types are currently
 * based on the definitions in XSD 1.0 and XML 1.0. They may be changed in a future version to be based
 * on a later version.</p>
 * <p>More complicated item types, especially those that are dependent on information in a schema,
 * are available using factory methods on the {@link ItemTypeFactory} object. The factory methods can
 * also be used to create variants of the types that use the rules given in the XML 1.1 and/or XSD 1.1
 * specifications. In addition the method {@link ItemTypeFactory#parseItemType} can be used to construct
 * <code>ItemType</code> objects from their representation in XPath syntax.</p>
 * <p>An <code>ItemType</code> can be used as a predicate in contexts where a predicate is required
 * to filter items, for example in {@link Step#where}.</p>
 */

@SuppressWarnings("WeakerAccess")
public abstract class ItemType implements Predicate<XdmItem> {

    protected final net.sf.saxon.type.ItemType underlyingType;

    public ItemType(net.sf.saxon.type.ItemType underlyingType) {
        this.underlyingType = underlyingType;
    }

    private static final ConversionRules defaultConversionRules = new ConversionRules();

    static {
        defaultConversionRules.setStringToDoubleConverter(StringToDouble.getInstance());
        defaultConversionRules.setNotationSet(null);
        defaultConversionRules.setURIChecker(StandardURIChecker.getInstance());
    }

    /**
     * Combine an item type with an occurrence indicator to produce a SequenceType. For example
     * <code>ItemType.ANY_ITEM.with(OccurrenceIndicator.ONE_OR_MORE)</code> returns the sequence
     * type <code>item()+</code>.
     * @param occurrenceIndicator the occurrence indicator to be used
     * @return the corresponding sequence type
     */

    public SequenceType with(OccurrenceIndicator occurrenceIndicator) {
        return SequenceType.makeSequenceType(this, occurrenceIndicator);
    }

    /**
     * Combine the item type with the occurrence indicator "exactly one"
     * to form a sequence type.
     * @return the result of <code>with(OccurrenceIndicator.ONE)</code>
     */

    public SequenceType one() {
        return SequenceType.makeSequenceType(this, OccurrenceIndicator.ONE);
    }

    /**
     * Combine the item type with the occurrence indicator "one or more" (+)
     * to form a sequence type.
     *
     * @return the result of <code>with(OccurrenceIndicator.ONE_OR_MORE)</code>
     */

    public SequenceType oneOrMore() {
        return SequenceType.makeSequenceType(this, OccurrenceIndicator.ONE_OR_MORE);
    }

    /**
     * Combine the item type with the occurrence indicator "zero or more" (*)
     * to form a sequence type.
     *
     * @return the result of <code>with(OccurrenceIndicator.ZERO_OR_MORE)</code>
     */

    public SequenceType zeroOrMore() {
        return SequenceType.makeSequenceType(this, OccurrenceIndicator.ZERO_OR_MORE);
    }

    /**
     * Combine the item type with the occurrence indicator "zero or one" (?)
     * to form a sequence type.
     *
     * @return the result of <code>with(OccurrenceIndicator.ZERO_OR_ONE)</code>
     */

    public SequenceType zeroOrOne() {
        return SequenceType.makeSequenceType(this, OccurrenceIndicator.ZERO_OR_ONE);
    }

    /**
     * ItemType representing the type item(), that is, any item at all
     */

    public static ItemType ANY_ITEM = new ItemType(AnyItemType.getInstance()) {

        @Override
        public ConversionRules getConversionRules() {
            return defaultConversionRules;
        }

        @Override
        public boolean matches(XdmItem item) {
            return true;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return true;
        }

    };


    /**
     * ItemType representing the type function(*), that is, any function
     */

    public static ItemType ANY_FUNCTION = new ItemType(AnyFunctionType.getInstance()) {

        @Override
        public boolean matches(XdmItem item) {
            return item.getUnderlyingValue() instanceof FunctionItem;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType() instanceof FunctionItemType;
        }

    };

    /**
     * ItemType representing the type node(), that is, any node
     */

    public static final ItemType ANY_NODE = new ItemType(AnyNodeTest.getInstance()) {

        @Override
        public boolean matches(XdmItem item) {
            return item.getUnderlyingValue() instanceof NodeInfo;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType() instanceof NodeTest;
        }

    };


    /**
     * ItemType representing the ATTRIBUTE node() type
     */

    public static final ItemType ATTRIBUTE_NODE = new ItemType(NodeKindTest.ATTRIBUTE) {

        @Override
        public boolean matches(XdmItem item) {
            Item it = item.getUnderlyingValue();
            return it instanceof NodeInfo && ((NodeInfo) it).getNodeKind() == Type.ATTRIBUTE;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType().getUType() == UType.ATTRIBUTE;
        }

    };


    /**
     * ItemType representing the COMMENT node() type
     */

    public static final ItemType COMMENT_NODE = new ItemType(NodeKindTest.COMMENT) {

        @Override
        public boolean matches(XdmItem item) {
            Item it = item.getUnderlyingValue();
            return it instanceof NodeInfo && ((NodeInfo) it).getNodeKind() == Type.COMMENT;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType().getUType() == UType.COMMENT;
        }

    };


    /**
     * ItemType representing the TEXT node() type
     */

    public static final ItemType TEXT_NODE = new ItemType(NodeKindTest.TEXT) {

        @Override
        public boolean matches(XdmItem item) {
            Item it = item.getUnderlyingValue();
            return it instanceof NodeInfo && ((NodeInfo) it).getNodeKind() == Type.TEXT;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType().getUType() == UType.TEXT;
        }

    };


    /**
     * ItemType representing the ELEMENT node() type
     */

    public static final ItemType ELEMENT_NODE = new ItemType(NodeKindTest.ELEMENT) {

        @Override
        public boolean matches(XdmItem item) {
            Item it = item.getUnderlyingValue();
            return it instanceof NodeInfo && ((NodeInfo) it).getNodeKind() == Type.ELEMENT;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType().getUType() == UType.ELEMENT;
        }

    };

    /**
     * ItemType representing the DOCUMENT node() type
     */

    public static final ItemType DOCUMENT_NODE = new ItemType(NodeKindTest.DOCUMENT) {

        @Override
        public boolean matches(XdmItem item) {
            Item it = item.getUnderlyingValue();
            return it instanceof NodeInfo && ((NodeInfo) it).getNodeKind() == Type.DOCUMENT;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType().getUType() == UType.DOCUMENT;
        }

    };


    /**
     * ItemType representing the NAMESPACE node() type
     */

    public static final ItemType NAMESPACE_NODE = new ItemType(NodeKindTest.NAMESPACE) {

        @Override
        public boolean matches(XdmItem item) {
            Item it = item.getUnderlyingValue();
            return it instanceof NodeInfo && ((NodeInfo) it).getNodeKind() == Type.NAMESPACE;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType().getUType() == UType.NAMESPACE;
        }

    };


    /**
     * ItemType representing the PROCESSING_INSTRUCTION node() type
     */

    public static final ItemType PROCESSING_INSTRUCTION_NODE = new ItemType(NodeKindTest.PROCESSING_INSTRUCTION) {

        @Override
        public boolean matches(XdmItem item) {
            Item it = item.getUnderlyingValue();
            return it instanceof NodeInfo && ((NodeInfo) it).getNodeKind() == Type.PROCESSING_INSTRUCTION;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType().getUType() == UType.PI;
        }

    };


    /**
     * ItemType representing the type map(*), that is, any map
     */

    public static final ItemType ANY_MAP = new ItemType(MapType.ANY_MAP_TYPE) {

        @Override
        public boolean matches(XdmItem item) {
            return item.getUnderlyingValue() instanceof MapItem;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType() instanceof MapType;
        }

    };

    /**
     * ItemType representing the type array(*), that is, any array
     */

    public static final ItemType ANY_ARRAY = new ItemType(ArrayItemType.ANY_ARRAY_TYPE) {

        @Override
        public boolean matches(XdmItem item) {
            return item.getUnderlyingValue() instanceof ArrayItem;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType() instanceof ArrayItemType;
        }

    };


    /**
     * ItemType representing the type xs:anyAtomicType, that is, any atomic value
     */

    public static final ItemType ANY_ATOMIC_VALUE = atomic(BuiltInAtomicType.ANY_ATOMIC, defaultConversionRules);

    /**
     * ItemType representing the type xs:error: a type with no instances
     */

    public static final ItemType ERROR = new ItemType(ErrorType.getInstance()) {

        @Override
        public boolean matches(XdmItem item) {
            return false;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return other.getUnderlyingItemType() instanceof ErrorType;
        }

    };

    /**
     * ItemType representing a built-in atomic type
     */

    protected static class BuiltInAtomicItemType extends ItemType {

        private final ConversionRules conversionRules;

        public BuiltInAtomicItemType(BuiltInAtomicType underlyingType, ConversionRules conversionRules) {
            super(underlyingType);
            this.conversionRules = conversionRules;
        }

        public static BuiltInAtomicItemType makeVariant(BuiltInAtomicItemType type, ConversionRules conversionRules) {
            return new BuiltInAtomicItemType((BuiltInAtomicType)type.underlyingType, conversionRules);
        }

        @Override
        public ConversionRules getConversionRules() {
            return conversionRules;
        }

        @Override
        public boolean matches(XdmItem item) {
            Item value = item.getUnderlyingValue();
            if (!(value instanceof AtomicValue)) {
                return false;
            }
            AtomicType type = ((AtomicValue) value).getItemType();
            return subsumesUnderlyingType(type);
        }

        @Override
        public boolean subsumes(ItemType other) {
            net.sf.saxon.type.ItemType otherType = other.getUnderlyingItemType();
            if (!otherType.isPlainType()) {
                return false;
            }
            AtomicType type = (AtomicType) otherType;
            return subsumesUnderlyingType(type);
        }

        private boolean subsumesUnderlyingType(AtomicType type) {
            BuiltInAtomicType builtIn =
                    type instanceof BuiltInAtomicType ? (BuiltInAtomicType) type : (BuiltInAtomicType) type.getBuiltInBaseType();
            while (true) {
                if (builtIn.isSameType((AtomicType)underlyingType)) {
                    return true;
                }
                SchemaType base = builtIn.getBaseType();
                if (!(base instanceof BuiltInAtomicType)) {
                    return false;
                }
                builtIn = (BuiltInAtomicType) base;
            }
        }

        @Override
        public net.sf.saxon.type.ItemType getUnderlyingItemType() {
            return underlyingType;
        }

        @Override
        public String toString() {
            return "xs:" + ((BuiltInAtomicType)underlyingType).getStructuredQName().getLocalPart();
        }
    }

    /**
     * ItemType representing the primitive type xs:string
     */

    public static final ItemType STRING = atomic(BuiltInAtomicType.STRING, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:boolean
     */

    public static final ItemType BOOLEAN = atomic(BuiltInAtomicType.BOOLEAN, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:duration
     */

    public static final ItemType DURATION = atomic(BuiltInAtomicType.DURATION, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:dateTime
     */

    public static final ItemType DATE_TIME = atomic(BuiltInAtomicType.DATE_TIME, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:date
     */

    public static final ItemType DATE = atomic(BuiltInAtomicType.DATE, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:time
     */

    public static final ItemType TIME = atomic(BuiltInAtomicType.TIME, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:gYearMonth
     */

    public static final ItemType G_YEAR_MONTH = atomic(BuiltInAtomicType.G_YEAR_MONTH, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:gMonth
     */

    public static final ItemType G_MONTH = atomic(BuiltInAtomicType.G_MONTH, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:gMonthDay
     */

    public static final ItemType G_MONTH_DAY = atomic(BuiltInAtomicType.G_MONTH_DAY, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:gYear
     */

    public static final ItemType G_YEAR = atomic(BuiltInAtomicType.G_YEAR, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:gDay
     */

    public static final ItemType G_DAY = atomic(BuiltInAtomicType.G_DAY, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:hexBinary
     */

    public static final ItemType HEX_BINARY = atomic(BuiltInAtomicType.HEX_BINARY, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:base64Binary
     */

    public static final ItemType BASE64_BINARY = atomic(BuiltInAtomicType.BASE64_BINARY, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:anyURI
     */

    public static final ItemType ANY_URI = atomic(BuiltInAtomicType.ANY_URI, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:QName
     */

    public static final ItemType QNAME = atomic(BuiltInAtomicType.QNAME, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:NOTATION
     */

    public static final ItemType NOTATION = atomic(BuiltInAtomicType.NOTATION, defaultConversionRules);

    /**
     * ItemType representing the XPath-defined type xs:untypedAtomic
     */

    public static final ItemType UNTYPED_ATOMIC = atomic(BuiltInAtomicType.UNTYPED_ATOMIC, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:decimal
     */

    public static final ItemType DECIMAL = atomic(BuiltInAtomicType.DECIMAL, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:float
     */

    public static final ItemType FLOAT = atomic(BuiltInAtomicType.FLOAT, defaultConversionRules);

    /**
     * ItemType representing the primitive type xs:double
     */

    public static final ItemType DOUBLE = atomic(BuiltInAtomicType.DOUBLE, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:integer
     */

    public static final ItemType INTEGER = atomic(BuiltInAtomicType.INTEGER, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:nonPositiveInteger
     */

    public static final ItemType NON_POSITIVE_INTEGER = atomic(BuiltInAtomicType.NON_POSITIVE_INTEGER, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:negativeInteger
     */

    public static final ItemType NEGATIVE_INTEGER = atomic(BuiltInAtomicType.NEGATIVE_INTEGER, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:long
     */

    public static final ItemType LONG = atomic(BuiltInAtomicType.LONG, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:int
     */

    public static final ItemType INT = atomic(BuiltInAtomicType.INT, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:short
     */

    public static final ItemType SHORT = atomic(BuiltInAtomicType.SHORT, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:byte
     */

    public static final ItemType BYTE = atomic(BuiltInAtomicType.BYTE, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:nonNegativeInteger
     */

    public static final ItemType NON_NEGATIVE_INTEGER = atomic(BuiltInAtomicType.NON_NEGATIVE_INTEGER, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:positiveInteger
     */

    public static final ItemType POSITIVE_INTEGER = atomic(BuiltInAtomicType.POSITIVE_INTEGER, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:unsignedLong
     */

    public static final ItemType UNSIGNED_LONG = atomic(BuiltInAtomicType.UNSIGNED_LONG, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:unsignedInt
     */

    public static final ItemType UNSIGNED_INT = atomic(BuiltInAtomicType.UNSIGNED_INT, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:unsignedShort
     */

    public static final ItemType UNSIGNED_SHORT = atomic(BuiltInAtomicType.UNSIGNED_SHORT, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:unsignedByte
     */

    public static final ItemType UNSIGNED_BYTE = atomic(BuiltInAtomicType.UNSIGNED_BYTE, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:yearMonthDuration
     */

    public static final ItemType YEAR_MONTH_DURATION = atomic(BuiltInAtomicType.YEAR_MONTH_DURATION, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:dayTimeDuration
     */

    public static final ItemType DAY_TIME_DURATION = atomic(BuiltInAtomicType.DAY_TIME_DURATION, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:normalizedString
     */

    public static final ItemType NORMALIZED_STRING = atomic(BuiltInAtomicType.NORMALIZED_STRING, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:token
     */

    public static final ItemType TOKEN = atomic(BuiltInAtomicType.TOKEN, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:language
     */

    public static final ItemType LANGUAGE = atomic(BuiltInAtomicType.LANGUAGE, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:Name
     */

    public static final ItemType NAME = atomic(BuiltInAtomicType.NAME, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:NMTOKEN
     */

    public static final ItemType NMTOKEN = atomic(BuiltInAtomicType.NMTOKEN, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:NCName
     */

    public static final ItemType NCNAME = atomic(BuiltInAtomicType.NCNAME, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:ID
     */

    public static final ItemType ID = atomic(BuiltInAtomicType.ID, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:IDREF
     */

    public static final ItemType IDREF = atomic(BuiltInAtomicType.IDREF, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:ENTITY
     */

    public static final ItemType ENTITY = atomic(BuiltInAtomicType.ENTITY, defaultConversionRules);

    /**
     * ItemType representing the built-in (but non-primitive) type xs:dateTimeStamp
     * (introduced in XSD 1.1)
     */

    public static final ItemType DATE_TIME_STAMP = atomic(BuiltInAtomicType.DATE_TIME_STAMP, defaultConversionRules);

    /**
     * Create an ItemType representing a built-in atomic type.
     *
     * @param underlyingType  the BuiltInAtomicType object representing this type
     * @param conversionRules the conversion rules to be used
     * @return the ItemType.
     */

    private static ItemType atomic(BuiltInAtomicType underlyingType, ConversionRules conversionRules) {
        return new BuiltInAtomicItemType(underlyingType, conversionRules);
    }

    /**
     * ItemType representing the built-in union type xs:numeric defined in XDM 3.1
     */

    public static final ItemType NUMERIC = new ItemType(NumericType.getInstance()) {
        @Override
        public ConversionRules getConversionRules() {
            return defaultConversionRules;
        }

        @Override
        public boolean matches(XdmItem item) {
            return item.getUnderlyingValue() instanceof NumericValue;
        }

        @Override
        public boolean subsumes(ItemType other) {
            return DECIMAL.subsumes(other) || DOUBLE.subsumes(other) || FLOAT.subsumes(other);
        }

    };

    /**
     * Get the conversion rules implemented by this type. The conversion rules reflect variations
     * between different versions of the W3C specifications, for example XSD 1.1 allows "+INF" as
     * a lexical representation of xs:double, while XSD 1.0 does not.
     *
     * @return the conversion rules
     */

    /*@Nullable*/
    public ConversionRules getConversionRules() {
        return defaultConversionRules;
    }

    /**
     * Determine whether this item type matches a given item. This is a synonym of
     * the {@link #matches} method, provided so that an <code>ItemType</code> can be
     * used as a <code>Predicate</code>.
     *
     * @param item the item to be tested against this item type
     * @return true if the item matches this item type, false if it does not match.
     */

    public boolean test(XdmItem item) {
        return matches(item);
    }

    /**
     * Determine whether this item type matches a given item.
     *
     * @param item the item to be tested against this item type
     * @return true if the item matches this item type, false if it does not match.
     */

    public abstract boolean matches(XdmItem item);

    /**
     * Determine whether this ItemType subsumes another ItemType. Specifically,
     * <code>A.subsumes(B)</code> is true if every value that matches the ItemType B also matches
     * the ItemType A.
     *
     * @param other the other ItemType
     * @return true if this ItemType subsumes the other ItemType. This includes the case where A and B
     * represent the same ItemType.
     * @since 9.1
     */

    public abstract boolean subsumes(ItemType other);

    /**
     * Method to get the underlying Saxon implementation object
     * <p>This gives access to Saxon methods that may change from one release to another.</p>
     *
     * @return the underlying Saxon implementation object
     */

    public net.sf.saxon.type.ItemType getUnderlyingItemType() {
        return underlyingType;
    }

    /**
     * Get the name of the type, if it has one
     *
     * @return the name of the type, or null if it is either an anonymous schema-defined type,
     * or an XDM-defined type such as node() or map().
     * @since 9.7
     */

    public QName getTypeName() {
        net.sf.saxon.type.ItemType type = getUnderlyingItemType();
        if (type instanceof SchemaType) {
            StructuredQName name = ((SchemaType) type).getStructuredQName();
            return name == null ? null : new QName(name);
        } else {
            return null;
        }
    }

    /**
     * Test whether two ItemType objects represent the same type
     *
     * @param other the other ItemType object
     * @return true if the other object is an ItemType representing the same type
     * @since 9.5
     */

    public final boolean equals(Object other) {
        return other instanceof ItemType && getUnderlyingItemType().equals(((ItemType) other).getUnderlyingItemType());
    }

    /**
     * Get a hash code with semantics corresponding to the equals() method
     *
     * @return the hash code
     * @since 9.5
     */

    public final int hashCode() {
        return getUnderlyingItemType().hashCode();
    }

    /**
     * Get a string representation of the type. This will be generally a string that conforms to the
     * XPath ItemType production, for example a QName, or a construct
     * such as "node()" or "map(*)".
     *
     * <p>QNames are generally in EQName (<code>Q{uri}local</code>) format, except that
     * the prefix <code>xs:</code> is used for the XML Schema namespace.</p>
     *
     * <p>If the type is an anonymous schema type, the name of the nearest
     * named base type will be given, preceded by the character "&lt;".</p>
     *
     * <p>In the case of a function item type, the returned string will be
     * in parentheses (for example <code>(function() as xs:string)</code>) so
     * that an occurrence indicator can be added without ambiguity.</p>
     *
     * @return a string representation of the type
     * @since 9.7
     */

    public String toString() {
        net.sf.saxon.type.ItemType type = getUnderlyingItemType();
        if (type instanceof SchemaType) {
            String marker = "";
            SchemaType st = (SchemaType) type;
            StructuredQName name;
            while (true) {
                name = st.getStructuredQName();
                if (name != null) {
                    return marker + name.getEQName();
                } else {
                    marker = "<";
                    st = st.getBaseType();
                    if (st == null) {
                        return "Q{" + NamespaceConstant.SCHEMA + "}anyType";
                    }
                }
            }
        } else {
            return type.toString();
        }
    }


}

