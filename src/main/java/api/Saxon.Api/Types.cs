using System;
using System.Collections.Generic;
using System.Text;
using System.Xml;
using JType = net.sf.saxon.type.Type;
using JXdmItemType = net.sf.saxon.s9api.ItemType;
using JItemTypeType = net.sf.saxon.type.ItemType;
using JAnyItemType = net.sf.saxon.type.AnyItemType;
using JAtomicType = net.sf.saxon.type.AtomicType;
using JAnyMapType = net.sf.saxon.ma.map.MapType;
using JAnyArrayType = net.sf.saxon.ma.arrays.ArrayItemType;
using JNodeTest = net.sf.saxon.pattern.NodeTest;
using JAnyNodeType = net.sf.saxon.pattern.AnyNodeTest;
using JErrorType = net.sf.saxon.type.ErrorType;
using JFunctionItemType = net.sf.saxon.type.FunctionItemType;
using JNodeKindTest = net.sf.saxon.pattern.NodeKindTest;
using JSequenceType = net.sf.saxon.value.SequenceType;
using JXdmSequenceType = net.sf.saxon.s9api.SequenceType;
using JStandardNames = net.sf.saxon.om.StandardNames;
using JStructuredQName = net.sf.saxon.om.StructuredQName;
using JStaticProperty = net.sf.saxon.expr.StaticProperty;
using JConversionRules = net.sf.saxon.lib.ConversionRules;
using JOccurrenceIndicator = net.sf.saxon.s9api.OccurrenceIndicator;

namespace Saxon.Api
{
    /// <summary>
    /// Abstract class representing an item type. This may be the generic item type <c>item()</c>,
    /// an atomic type, the generic node type <code>node()</code>, a specific node kind such as
    /// <c>element()</c> or <c>text()</c>, or the generic function type <code>function()</code>.
    /// </summary>
    /// <remarks>
	/// More specific node types (such as <c>element(E)</c> or <c>schema-element(E)</c>) cannot currently
    /// be instantiated in this API.
    /// </remarks>

    public abstract class XdmItemType
    {
        protected JXdmItemType type;

        private JConversionRules defaultConversionRules = new JConversionRules();

        internal static XdmItemType MakeXdmItemType(JItemTypeType type)
        {
            
            if (type is JAtomicType)
            {
                XdmAtomicType itype = XdmAtomicType.BuiltInAtomicType(QName.FromStructuredQName(((JAtomicType)type).getStructuredQName()));
               
                return itype;
            }
            else if (type is JErrorType)
            {
                return XdmAnyNodeType.Instance;  // TODO: need to represent this properly
            }
            else if (type is JNodeTest)
            {
                if (type is JAnyNodeType)
                {
                    return XdmAnyNodeType.Instance;
                }
                else
                {
                    int kind = ((JNodeTest)type).getPrimitiveType();
                    return XdmNodeKind.ForNodeKindTest((JNodeKindTest)JNodeKindTest.makeNodeKindTest(kind));
                }
            }
            else if (type is JAnyMapType) {
                return XdmAnyMapType.Instance;
            }
            else if (type is JAnyArrayType)
            {
                return XdmAnyArrayType.Instance;
            }
            else if (type is JAnyItemType)
            {
                return XdmAnyItemType.Instance;
            }
            else if (type is JFunctionItemType)
            {
                return XdmAnyFunctionType.Instance;
            }
            else
            {
                return null;
            }
        }

        internal JXdmItemType Unwrap() {
            return type;
        }

        internal void SetJXdmItemType(JXdmItemType type) {
            this.type = type;
        }


        /// <summary>
        /// Determine whether this item type matches a given item.
        /// </summary>
        /// <param name="item">the item to be tested against this item type</param>
        /// <returns>true if the item matches this item type, false if it does not match.</returns>
        virtual public bool Matches(XdmItem item) {
            return type.matches((net.sf.saxon.s9api.XdmItem)XdmValue.FromGroundedValueToJXdmValue(item.value));
        }

        /// <summary>
        /// Determine whether this ItemType subsumes another ItemType. Specifically,
        /// <code>A.subsumes(B)</code> is true if every value that matches the ItemType B also matches
        /// the ItemType A.
        /// </summary>
		/// <param name="other">the other ItemType</param>
        /// <returns>true if this ItemType subsumes the other ItemType. This includes the case where A and B
        ///         represent the same ItemType.</returns>
        virtual public bool Subsumes(XdmItemType other) {
            return type.subsumes(other.type);
        }


        /// <summary>
        /// Get a string representation of the type. This will be a string that confirms to
        /// XPath ItemType production, for example a QName (always in 'Q{uri}local' format, or a
        /// construct such as 'node()' or 'map(*)'). If the type is an anonymous schema type, the 
        /// name of the nearest named base type will be given, preceded by the character '&lt;'
        /// </summary>
        /// <returns>a string representation of the type</returns>
        public override String ToString() {
            return type.toString();
        }


    }

    

    /// <summary>
    /// Singleton class representing the item type <c>item()</c>, which matches any item.
    /// </summary>

    public class XdmAnyItemType : XdmItemType
	{

		/// <summary>
		/// The singleton instance of this class: an <c>XdmItemType</c> corresponding to the
		/// item type <c>item()</c>, which matches any item.
		/// </summary>

		public static XdmAnyItemType Instance = new XdmAnyItemType();

		internal XdmAnyItemType()
		{
			this.type = JXdmItemType.ANY_ITEM;
		}

        /// <summary>
        /// Determine whether this item type matches a given item.
        /// </summary>
        /// <param name="item">the item to be tested against this item type</param>
        /// <returns>true if the item matches this item type, false if it does not match.</returns>
        public override bool Matches(XdmItem item)
        {
            return true;
        }

        /// <summary>
        /// Determine whether this ItemType subsumes another ItemType. Specifically,
        /// <code>A.subsumes(B)</code> is true if every value that matches the ItemType B also matches
        /// the ItemType A.
        /// </summary>
		/// <param name="other">the other ItemType</param>
        /// <returns>true if this ItemType subsumes the other ItemType. This includes the case where A and B
        ///         represent the same ItemType.</returns>
		public override bool Subsumes(XdmItemType other)
        {
            return true;
        }
    }

	/// <summary>
	/// Singleton class representing the item type <c>map(*)</c>, which matches any map.
	/// </summary>

    public class XdmAnyMapType : XdmItemType
    {


        /// <summary>
        /// The singleton instance of this class: an <c>XdmMapType</c> corresponding to the
        /// item type <c>map(*)</c>, which matches any map.
        /// </summary>

        public static XdmAnyMapType Instance = new XdmAnyMapType();

        internal XdmAnyMapType()
        {
            this.type = JXdmItemType.ANY_MAP;
        }



	}

	/// <summary>
	/// Singleton class representing the item type <c>array(*)</c>, which matches any array.
	/// </summary>

    public class XdmAnyArrayType : XdmItemType
    {


        /// <summary>
        /// The singleton instance of this class: an <c>XdmArrayType</c> corresponding to the
        /// item type <c>array(*)</c>, which matches any array.
        /// </summary>

        public static XdmAnyArrayType Instance = new XdmAnyArrayType();

        internal XdmAnyArrayType()
        {
            this.type = JXdmItemType.ANY_ARRAY;
        }


    }

    /// <summary>
    /// Singleton class representing the item type <c>node()</c>, which matches any node.
    /// </summary>

    public class XdmAnyNodeType : XdmItemType
	{

		/// <summary>
		/// The singleton instance of this class: an <c>XdmItemType</c> corresponding to the
		/// item type <c>node()</c>, which matches any node.
		/// </summary>

		public static XdmAnyNodeType Instance = new XdmAnyNodeType();

		internal XdmAnyNodeType()
		{
			this.type = JXdmItemType.ANY_NODE;
		}

        /// <summary>
        /// Determine whether this item type matches a given item.
        /// </summary>
        /// <param name="item">the item to be tested against this item type</param>
        /// <returns>true if the item matches this item type, false if it does not match.</returns>
        public override bool Matches(XdmItem item)
        {
            return ((net.sf.saxon.om.Item)item.value) is net.sf.saxon.om.NodeInfo;
        }

        /// <summary>
        ///  Determine whether this ItemType subsumes another ItemType. Specifically,
        /// <code>A.subsumes(B)</code> is true if every value that matches the ItemType B also matches
        /// the ItemType A.
        /// </summary>
		/// <param name="other">the other ItemType</param>
        /// <returns>true if this ItemType subsumes the other ItemType. This includes the case where A and B
        ///         represent the same ItemType.</returns>
        public override bool Subsumes(XdmItemType other)
        {
            return true;
        }
    }


	/// <summary>
	/// Singleton class representing the item type <c>function(*)</c>, which matches any function item.
	/// </summary>

	public class XdmAnyFunctionType : XdmItemType
	{

		/// <summary>
		/// The singleton instance of this class: an <c>XdmItemType</c> corresponding to the
		/// item type <c>function(*)</c>, which matches any function item.
		/// </summary>

		public static XdmAnyFunctionType Instance = new XdmAnyFunctionType();

		internal XdmAnyFunctionType()
		{
			this.type = JXdmItemType.ANY_FUNCTION;
		}


    }

    
	/// <summary>
	/// An instance of class <c>XdmAtomicType</c> represents a specific atomic type, for example
	/// <c>xs:double</c>, <c>xs:integer</c>, or <c>xs:anyAtomicType</c>. This may be either a built-in
	/// atomic type or a type defined in a user-written schema.
	/// </summary>
	/// <remarks>
	/// To get an <c>XdmAtomicType</c> instance representing a built-in atomic type, use one of the predefined instances
	/// of the subclass <c>XdmBuiltInAtomicType</c>. To get an <c>XdmAtomicType</c> instance representing a user-defined
	/// atomic type (defined in a schema), use the method <c>GetAtomicType</c> defined on the <c>SchemaManager</c> class.
	/// </remarks>

	public class XdmAtomicType : XdmItemType
	{

        /// <summary>
		/// Instance object of the <c>XdmAtomicType</c> class
        /// </summary>
        public static XdmAtomicType Instance = new XdmAtomicType(JXdmItemType.ANY_ATOMIC_VALUE);

        internal XdmAtomicType(JXdmItemType type)
        {
            this.type = type;
        }

        /// <summary>
        ///  ItemType representing the built-in (but non-primitive) type xs:short
        /// </summary>
        public static XdmAtomicType SHORT = BuiltInAtomicType(JStandardNames.XS_SHORT);


        /// <summary>
        /// ItemType representing the primitive type xs:string
        /// </summary>
        public static XdmAtomicType STRING = BuiltInAtomicType(JStandardNames.XS_STRING);

        /// <summary>
        /// ItemType representing the primitive type xs:boolean
        /// </summary>
        public static XdmAtomicType BOOLEAN = BuiltInAtomicType(JStandardNames.XS_BOOLEAN);

        /// <summary>
        /// ItemType representing the primitive type xs:duration
        /// </summary>

        public static XdmAtomicType DURATION = BuiltInAtomicType(JStandardNames.XS_DURATION);

        /// <summary>
        /// ItemType representing the primitive type xs:dateTime
        /// </summary>

        public static XdmAtomicType DATE_TIME = BuiltInAtomicType(JStandardNames.XS_DATE_TIME);

        /// <summary>
        /// ItemType representing the primitive type xs:date
        /// </summary>

        public static XdmAtomicType DATE = BuiltInAtomicType(JStandardNames.XS_DATE);

        /// <summary>
        /// ItemType representing the primitive type xs:time
        /// </summary>

        public static XdmAtomicType TIME = BuiltInAtomicType(JStandardNames.XS_TIME);

        /// <summary>
        /// ItemType representing the primitive type xs:gYearMonth
        /// </summary>

        public static XdmAtomicType G_YEAR_MONTH = BuiltInAtomicType(JStandardNames.XS_G_YEAR_MONTH);

        /// <summary>
        ///  ItemType representing the primitive type xs:gMonth
        /// </summary>

        public static XdmAtomicType G_MONTH = BuiltInAtomicType(JStandardNames.XS_G_MONTH);

        /// <summary>
        /// ItemType representing the primitive type xs:gMonthDay
        /// </summary>

        public static XdmAtomicType G_MONTH_DAY = BuiltInAtomicType(JStandardNames.XS_G_MONTH_DAY);

        /// <summary>
        /// ItemType representing the primitive type xs:gYear
        /// </summary>

        public static XdmAtomicType G_YEAR = BuiltInAtomicType(JStandardNames.XS_G_YEAR);

        /// <summary>
        /// ItemType representing the primitive type xs:gDay
        /// </summary>

        public static XdmAtomicType G_DAY = BuiltInAtomicType(JStandardNames.XS_G_DAY);

        /// <summary>
        /// ItemType representing the primitive type xs:hexBinary
        /// </summary>

        public static XdmAtomicType HEX_BINARY = BuiltInAtomicType(JStandardNames.XS_HEX_BINARY);

        /// <summary>
        ///  ItemType representing the primitive type xs:base64Binary
        /// </summary>

        public static XdmAtomicType BASE64_BINARY = BuiltInAtomicType(JStandardNames.XS_BASE64_BINARY);

        /// <summary>
        /// ItemType representing the primitive type xs:anyURI
        /// </summary>

        public static XdmAtomicType ANY_URI = BuiltInAtomicType(JStandardNames.XS_ANY_URI);

        /// <summary>
        /// ItemType representing the primitive type xs:QName
        /// </summary>

        public static XdmAtomicType QNAME = BuiltInAtomicType(JStandardNames.XS_QNAME);

        /// <summary>
        /// ItemType representing the primitive type xs:NOTATION
        /// </summary>

        public static XdmAtomicType NOTATION = BuiltInAtomicType(JStandardNames.XS_NOTATION);

        /// <summary>
        ///  ItemType representing the XPath-defined type xs:untypedAtomic
        /// </summary>

        public static XdmAtomicType UNTYPED_ATOMIC = BuiltInAtomicType(JStandardNames.XS_UNTYPED_ATOMIC);

        /// <summary>
        /// ItemType representing the primitive type xs:decimal
        /// </summary>

        public static XdmAtomicType DECIMAL = BuiltInAtomicType(JStandardNames.XS_DECIMAL);

        /// <summary>
        /// ItemType representing the primitive type xs:float
        /// </summary>

        public static XdmAtomicType FLOAT = BuiltInAtomicType(JStandardNames.XS_FLOAT);

        /// <summary>
        /// ItemType representing the primitive type xs:double
        /// </summary>

        public static XdmAtomicType DOUBLE = BuiltInAtomicType(JStandardNames.XS_DOUBLE);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:integer
        /// </summary>

        public static XdmAtomicType INTEGER = BuiltInAtomicType(JStandardNames.XS_INTEGER);

        /// <summary>
        /// 
        /// </summary>

        public static XdmAtomicType NON_POSITIVE_INTEGER = BuiltInAtomicType(JStandardNames.XS_NON_POSITIVE_INTEGER);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:negativeInteger
        /// </summary>

        public static XdmAtomicType NEGATIVE_INTEGER = BuiltInAtomicType(JStandardNames.XS_NEGATIVE_INTEGER);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:long
        /// </summary>

        public static XdmAtomicType LONG = BuiltInAtomicType(JStandardNames.XS_LONG);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:int
        /// </summary>

        public static XdmAtomicType INT = BuiltInAtomicType(JStandardNames.XS_INT);


        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:byte
        /// </summary>

        public static XdmAtomicType BYTE = BuiltInAtomicType(JStandardNames.XS_BYTE);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:nonNegativeInteger
        /// </summary>

        public static XdmAtomicType NON_NEGATIVE_INTEGER = BuiltInAtomicType(JStandardNames.XS_NON_NEGATIVE_INTEGER);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:positiveInteger
        /// </summary>

        public static XdmAtomicType POSITIVE_INTEGER = BuiltInAtomicType(JStandardNames.XS_POSITIVE_INTEGER);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:unsignedLong
        /// </summary>

        public static XdmAtomicType UNSIGNED_LONG = BuiltInAtomicType(JStandardNames.XS_UNSIGNED_LONG);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:unsignedInt
        /// </summary>

        public static XdmAtomicType UNSIGNED_INT = BuiltInAtomicType(JStandardNames.XS_UNSIGNED_INT);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:unsignedShort
        /// </summary>

        public static XdmAtomicType UNSIGNED_SHORT = BuiltInAtomicType(JStandardNames.XS_UNSIGNED_SHORT);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:unsignedByte
        /// </summary>

        public static XdmAtomicType UNSIGNED_BYTE = BuiltInAtomicType(JStandardNames.XS_UNSIGNED_BYTE);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:yearMonthDuration
        /// </summary>

        public static XdmAtomicType YEAR_MONTH_DURATION = BuiltInAtomicType(JStandardNames.XS_YEAR_MONTH_DURATION);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:dayTimeDuration
        /// </summary>

        public static XdmAtomicType DAY_TIME_DURATION = BuiltInAtomicType(JStandardNames.XS_DAY_TIME_DURATION);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:normalizedString
        /// </summary>

        public static XdmAtomicType NORMALIZED_STRING = BuiltInAtomicType(JStandardNames.XS_NORMALIZED_STRING);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:token
        /// </summary>

        public static XdmAtomicType TOKEN = BuiltInAtomicType(JStandardNames.XS_TOKEN);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:language
        /// </summary>

        public static XdmAtomicType LANGUAGE = BuiltInAtomicType(JStandardNames.XS_LANGUAGE);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:Name
        /// </summary>

        public static XdmAtomicType NAME = BuiltInAtomicType(JStandardNames.XS_NAME);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:NMTOKEN
        /// </summary>

        public static XdmAtomicType NMTOKEN = BuiltInAtomicType(JStandardNames.XS_NMTOKEN);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:NCName
        /// </summary>

        public static XdmAtomicType NCNAME = BuiltInAtomicType(JStandardNames.XS_NCNAME);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:ID
        /// </summary>

        public static XdmAtomicType ID = BuiltInAtomicType(JStandardNames.XS_ID);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:IDREF
        /// </summary>

        public static XdmAtomicType IDREF = BuiltInAtomicType(JStandardNames.XS_IDREF);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:ENTITY
        /// </summary>

        public static XdmAtomicType ENTITY = BuiltInAtomicType(JStandardNames.XS_ENTITY);

        /// <summary>
        /// ItemType representing the built-in (but non-primitive) type xs:dateTimeStamp
        /// (introduced in XSD 1.1)
        /// </summary>

        public static XdmAtomicType DATE_TIME_STAMP = BuiltInAtomicType(JStandardNames.XS_DATE_TIME_STAMP);


        /// <summary>
        /// Get an <c>XdmAtomicType</c> object representing a built-in atomic type with a given name.
        /// </summary>
        /// <param name="name">The name of the required built-in atomic type</param>
        /// <returns>An <c>XdmAtomicType</c> object representing the built-in atomic type with the supplied name.
        /// Returns null if there is no built-in atomic type with this name.
        /// It is undefined whether two requests for the same built-in type will return the same object.</returns>


        public static XdmAtomicType BuiltInAtomicType(QName name) {
            int fingerprint = JStandardNames.getFingerprint(name.Uri, name.LocalName);
            return BuiltInAtomicType(fingerprint);
        }

        internal static XdmAtomicType BuiltInAtomicType(int fingerprint)
		{

			if (fingerprint == -1)
			{
				return null;
			}
            switch (fingerprint) {
                case JStandardNames.XS_ANY_ATOMIC_TYPE:
                    return XdmAtomicType.Instance;

                case JStandardNames.XS_STRING:
                    return new XdmAtomicType(JXdmItemType.STRING);

                case JStandardNames.XS_BOOLEAN:
                    return new XdmAtomicType(JXdmItemType.BOOLEAN);

                case JStandardNames.XS_DURATION:
                    return new XdmAtomicType(JXdmItemType.DURATION);

                case JStandardNames.XS_DATE_TIME:
                    return new XdmAtomicType(JXdmItemType.DATE_TIME);

                case JStandardNames.XS_DATE:
                    return new XdmAtomicType(JXdmItemType.DATE);

                case JStandardNames.XS_TIME:
                    return new XdmAtomicType(JXdmItemType.TIME);

                case JStandardNames.XS_G_YEAR_MONTH:
                    return new XdmAtomicType(JXdmItemType.G_YEAR_MONTH);

                case JStandardNames.XS_G_MONTH:
                    return new XdmAtomicType(JXdmItemType.G_MONTH);

                case JStandardNames.XS_G_MONTH_DAY:
                    return new XdmAtomicType(JXdmItemType.G_MONTH_DAY);

                case JStandardNames.XS_G_YEAR:
                    return new XdmAtomicType(JXdmItemType.G_YEAR);

                case JStandardNames.XS_G_DAY:
                    return new XdmAtomicType(JXdmItemType.G_DAY);

                case JStandardNames.XS_HEX_BINARY:
                    return new XdmAtomicType(JXdmItemType.HEX_BINARY);

                case JStandardNames.XS_BASE64_BINARY:
                    return new XdmAtomicType(JXdmItemType.BASE64_BINARY);

                case JStandardNames.XS_ANY_URI:
                    return new XdmAtomicType(JXdmItemType.ANY_URI);

                case JStandardNames.XS_QNAME:
                    return new XdmAtomicType(JXdmItemType.QNAME);

                case JStandardNames.XS_NOTATION:
                    return new XdmAtomicType(JXdmItemType.NOTATION);

                case JStandardNames.XS_UNTYPED_ATOMIC:
                    return new XdmAtomicType(JXdmItemType.UNTYPED_ATOMIC);

                case JStandardNames.XS_DECIMAL:
                    return new XdmAtomicType(JXdmItemType.DECIMAL);

                case JStandardNames.XS_FLOAT:
                    return new XdmAtomicType(JXdmItemType.FLOAT);

                case JStandardNames.XS_DOUBLE:
                    return new XdmAtomicType(JXdmItemType.DOUBLE);

                case JStandardNames.XS_INTEGER:
                    return new XdmAtomicType(JXdmItemType.INTEGER);

                case JStandardNames.XS_NON_POSITIVE_INTEGER:
                    return new XdmAtomicType(JXdmItemType.NON_POSITIVE_INTEGER);

                case JStandardNames.XS_NEGATIVE_INTEGER:
                    return new XdmAtomicType(JXdmItemType.NEGATIVE_INTEGER);

                case JStandardNames.XS_LONG:
                    return new XdmAtomicType(JXdmItemType.LONG);

                case JStandardNames.XS_INT:
                    return new XdmAtomicType(JXdmItemType.INT);

                case JStandardNames.XS_SHORT:
                    return new XdmAtomicType(JXdmItemType.SHORT);

                case JStandardNames.XS_BYTE:
                    return new XdmAtomicType(JXdmItemType.BYTE);

                case JStandardNames.XS_NON_NEGATIVE_INTEGER:
                    return new XdmAtomicType(JXdmItemType.NON_NEGATIVE_INTEGER);

                case JStandardNames.XS_POSITIVE_INTEGER:
                    return new XdmAtomicType(JXdmItemType.POSITIVE_INTEGER);

                case JStandardNames.XS_UNSIGNED_LONG:
                    return new XdmAtomicType(JXdmItemType.UNSIGNED_LONG);

                case JStandardNames.XS_UNSIGNED_INT:
                    return new XdmAtomicType(JXdmItemType.UNSIGNED_INT);

                case JStandardNames.XS_UNSIGNED_SHORT:
                    return new XdmAtomicType(JXdmItemType.UNSIGNED_SHORT);

                case JStandardNames.XS_UNSIGNED_BYTE:
                    return new XdmAtomicType(JXdmItemType.UNSIGNED_BYTE);

                case JStandardNames.XS_YEAR_MONTH_DURATION:
                    return new XdmAtomicType(JXdmItemType.YEAR_MONTH_DURATION);

                case JStandardNames.XS_DAY_TIME_DURATION:
                    return new XdmAtomicType(JXdmItemType.DAY_TIME_DURATION);

                case JStandardNames.XS_NORMALIZED_STRING:
                    return new XdmAtomicType(JXdmItemType.NORMALIZED_STRING);

                case JStandardNames.XS_TOKEN:
                    return new XdmAtomicType(JXdmItemType.TOKEN);

                case JStandardNames.XS_LANGUAGE:
                    return new XdmAtomicType(JXdmItemType.LANGUAGE);

                case JStandardNames.XS_NAME:
                    return new XdmAtomicType(JXdmItemType.NAME);

                case JStandardNames.XS_NMTOKEN:
                    return new XdmAtomicType(JXdmItemType.NMTOKEN);

                case JStandardNames.XS_NCNAME:
                    return new XdmAtomicType(JXdmItemType.NCNAME);

                case JStandardNames.XS_ID:
                    return new XdmAtomicType(JXdmItemType.ID);

                case JStandardNames.XS_IDREF:
                    return new XdmAtomicType(JXdmItemType.IDREF);

                case JStandardNames.XS_ENTITY:
                    return new XdmAtomicType(JXdmItemType.ENTITY);

                case JStandardNames.XS_DATE_TIME_STAMP:
                    return new XdmAtomicType(JXdmItemType.DATE_TIME_STAMP);


                case JStandardNames.XS_ERROR:
                    return null;

                default:
                    throw new StaticError(new net.sf.saxon.s9api.SaxonApiException("Unknown atomic type " + JStandardNames.getClarkName(fingerprint)));


            }
            
		}

    

        /// <summary>
        /// The name of the atomic type, or null if the type is anonymous.
        /// </summary>

        public QName Name
		{
			get
			{
				JStructuredQName jQName = ((JAtomicType)type).getTypeName();
				if (jQName == null)
				{
					return null;
				}
				return new QName(jQName.getPrefix(), jQName.getURI(), jQName.getLocalPart());


            }
		}

        /// <summary>
        /// Determine whether this item type matches a given item
        /// </summary>
        /// <param name="item">the item to be tested against this item type</param>
        /// <returns>true if the item matches this item type, false if it does not match</returns>
        public override bool Matches(XdmItem item)
        {
            return type.matches(XdmItem.FromXdmItemItemToJXdmItem(item));
        }


        /// <summary>
        /// Determine whether this ItemType subsumes another ItemType. Specifically
        /// <code>A.Sumsumes(B)</code> is true if every value that matches the ItemType B also
        /// matches the ItemType A.
        /// </summary>
        /// <param name="other">the other ItemType</param>
        /// <returns>true if this ItemType subsumes the other ItemType. This includes the case where A and B
        /// represent the same ItemType.</returns>
        public override bool Subsumes(XdmItemType other)
        {
            return type.subsumes(other.Unwrap());
        }
    }

	/// <summary>
	/// Instances of <c>XdmNodeKind</c> represent the item types denoted in XPath as <c>document-node()</c>,
	/// <c>element()</c>, <c>attribute()</c>, <c>text()</c>, and so on. These are all represented by singular named instances.
	/// </summary>

	public class XdmNodeKind : XdmItemType
	{

        //public static XdmNodeKind Instance = new XdmNodeKind();
        private JNodeKindTest nodekind;
        private int kind;

        internal XdmNodeKind(JNodeKindTest test, JXdmItemType type)
		{
			nodekind = test;
            this.type = type;
            kind = test.getNodeKind();
        }

		/// <summary>
		/// The item type <c>document-node()</c>
		/// </summary>

		public static XdmNodeKind Document = new XdmNodeKind(JNodeKindTest.DOCUMENT, JXdmItemType.DOCUMENT_NODE);

		/// <summary>
		/// The item type <c>element()</c>
		/// </summary>

		public static XdmNodeKind Element = new XdmNodeKind(JNodeKindTest.ELEMENT, JXdmItemType.ELEMENT_NODE);

		/// <summary>
		/// The item type <c>attribute()</c>
		/// </summary>

		public static XdmNodeKind Attribute = new XdmNodeKind(JNodeKindTest.ATTRIBUTE, JXdmItemType.ATTRIBUTE_NODE);

		/// <summary>
		/// The item type <c>text()</c>
		/// </summary>

		public static XdmNodeKind Text = new XdmNodeKind(JNodeKindTest.TEXT, JXdmItemType.TEXT_NODE);

		/// <summary>
		/// The item type <c>comment()</c>
		/// </summary>

		public static XdmNodeKind Comment = new XdmNodeKind(JNodeKindTest.COMMENT, JXdmItemType.COMMENT_NODE);

		/// <summary>
		/// The item type <c>processing-instruction()</c>
		/// </summary>

		public static XdmNodeKind ProcessingInstruction = new XdmNodeKind(JNodeKindTest.PROCESSING_INSTRUCTION, JXdmItemType.PROCESSING_INSTRUCTION_NODE);

		/// <summary>
		/// The item type <c>namespace-node()</c>
		/// </summary>

		public static XdmNodeKind Namespace = new XdmNodeKind(JNodeKindTest.NAMESPACE, JXdmItemType.NAMESPACE_NODE);

		internal static XdmNodeKind ForNodeKindTest(JNodeKindTest test)
		{
			int kind = test.getPrimitiveType();
			switch (kind)
			{
			case JType.DOCUMENT:
				return Document;
			case JType.ELEMENT:
				return Element;
			case JType.ATTRIBUTE:
				return Attribute;
			case JType.TEXT:
				return Text;
			case JType.COMMENT:
				return Comment;
			case JType.PROCESSING_INSTRUCTION:
				return ProcessingInstruction;
			case JType.NAMESPACE:
				return Namespace;
			default:
				throw new ArgumentException("Unknown node kind");
			}
		}

		/// <summary>
		/// Get the item type representing the node kind of a supplied node
		/// </summary>
		/// <param name="node">The node whose node kind is required</param>
		/// <returns>The relevant node kind</returns>

		public static XdmNodeKind ForNode(XdmNode node)
		{
			return ForNodeType(node.NodeKind);
		}

        /// <summary>
        /// Determine whether this item type matches a given item.
        /// </summary>
        /// <param name="item">item the item to be tested against this item type</param>
        /// <returns>true if the item matches this item type, false if it does not match.</returns>
        public override bool Matches(XdmItem item)
        {
            return item.Unwrap() is JNodeTest && kind == ((net.sf.saxon.om.NodeInfo)item.Unwrap()).getNodeKind();
        }

        /// <summary>
        ///  Determine whether this ItemType subsumes another ItemType. Specifically,
        /// <code>A.subsumes(B)</code> is true if every value that matches the ItemType B also matches
        /// the ItemType A.
        /// </summary>
		/// <param name="other">the other ItemType</param>
        /// <returns>true if this ItemType subsumes the other ItemType. This includes the case where A and B
        ///         represent the same ItemType.</returns>
        public override bool Subsumes(XdmItemType other)
        {
            return true;
        }

        /// <summary>
		/// Get the item type corresponding to an <c>XmlNodeType</c> as defined in the <c>System.Xml</c> package
        /// </summary>
        /// <param name="type">The <c>XmlNodeType</c> to be converted</param>
        /// <returns>The corresponding <c>XdmNodeKind</c></returns>

        public static XdmNodeKind ForNodeType(XmlNodeType type)
		{
			switch (type)
			{
			case XmlNodeType.Document:
				return Document;
			case XmlNodeType.Element:
				return Element;
			case XmlNodeType.Attribute:
				return Attribute;
			case XmlNodeType.Text:
				return Text;
			case XmlNodeType.Comment:
				return Comment;
			case XmlNodeType.ProcessingInstruction:
				return ProcessingInstruction;
			default:
				throw new ArgumentException("Unknown node kind");
			}
		}
	}

	/// <summary>
	/// An instance of class <c>XdmSequenceType</c> represents a sequence type, that is, the combination
	/// of an item type and an occurrence indicator.
	/// </summary>

	public class XdmSequenceType
	{

		internal XdmItemType itemType;
		internal char occurrenceIn;
        internal int occurrence;


		public const char ZERO_OR_MORE='*';
		public const char ONE_OR_MORE = '+';
		public const char ZERO_OR_ONE = '?';
		public const char ZERO = '0'; //xBA
		public const char ONE = ' ';

		/// <summary>
		/// Create an <c>XdmSequenceType</c> corresponding to a given <c>XdmItemType</c> and occurrence indicator
		/// </summary>
		/// <param name="itemType">The item type</param>
		/// <param name="occurrenceIndicator">The occurrence indicator, one of '?' (zero-or-one), 
		/// '*' (zero-or-more), '+' (one-or-more), ' ' (a single space) (exactly one),
		/// or '0' (exactly zero). The type <c>empty-sequence()</c>
		/// can be represented by an occurrence indicator of 'º' with any item type.</param>

		public XdmSequenceType(XdmItemType itemType, char occurrenceIndicator)
		{
			int occ;
			switch (occurrenceIndicator)
			{
			case ZERO_OR_MORE:
				occ = JStaticProperty.ALLOWS_ZERO_OR_MORE;
				break;
			case ONE_OR_MORE:
				occ = JStaticProperty.ALLOWS_ONE_OR_MORE;
				break;
			case ZERO_OR_ONE:
				occ = JStaticProperty.ALLOWS_ZERO_OR_ONE;
				break;
			case ONE:

				occ = JStaticProperty.EXACTLY_ONE;
				break;
			case ZERO:
				occ = JStaticProperty.ALLOWS_ZERO;
				break;
			default:
				throw new ArgumentException("Unknown occurrence indicator");
			}
			this.itemType = itemType;
            this.occurrence = occ;
            this.occurrenceIn = occurrenceIndicator;
        }

		internal static XdmSequenceType FromSequenceType(JSequenceType seqType)
		{
			XdmItemType itemType = XdmItemType.MakeXdmItemType(seqType.getPrimaryType());
			char occ;
			if (seqType.getCardinality() == JStaticProperty.ALLOWS_ZERO_OR_MORE)
			{
				occ = ZERO_OR_MORE;
			}
			else if (seqType.getCardinality() == JStaticProperty.ALLOWS_ONE_OR_MORE)
			{
				occ = ONE_OR_MORE;
			}
			else if (seqType.getCardinality() == JStaticProperty.ALLOWS_ZERO_OR_ONE)
			{
				occ = ZERO_OR_ONE;
			}
			else if (seqType.getCardinality() == JStaticProperty.ALLOWS_ZERO)
			{
				occ = ZERO;
			}
			else
			{
				occ = ONE;
			}
            if (itemType == null) {
                return null;
            }
			return new XdmSequenceType(itemType, occ);
		}

       

        internal JSequenceType ToSequenceType() {
            JItemTypeType jit = itemType.Unwrap().getUnderlyingItemType();
			return JSequenceType.makeSequenceType(jit, occurrence);
		}

        internal JOccurrenceIndicator GetJOccurrenceIndicator(int cardinality) {
            switch (cardinality) {
                case JStaticProperty.EMPTY:
                    return JOccurrenceIndicator.ZERO;
                case JStaticProperty.ALLOWS_ZERO_OR_ONE:
                    return JOccurrenceIndicator.ZERO_OR_ONE;
                case JStaticProperty.ALLOWS_ZERO_OR_MORE:
                    return JOccurrenceIndicator.ZERO_OR_MORE;
                case JStaticProperty.ALLOWS_ONE:
                    return JOccurrenceIndicator.ONE;
                case JStaticProperty.ALLOWS_ONE_OR_MORE:
                    return JOccurrenceIndicator.ONE_OR_MORE;
                default:
                    return JOccurrenceIndicator.ZERO_OR_MORE;
            }
        }

        internal JXdmSequenceType ToJXdmSequenceType()
        {
            JItemTypeType jit = itemType.Unwrap().getUnderlyingItemType();
            return JXdmSequenceType.makeSequenceType(itemType.Unwrap(), GetJOccurrenceIndicator(occurrence));
        }


    }


}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
