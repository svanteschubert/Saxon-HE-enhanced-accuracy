using System;
using System.IO;
using System.Xml;
using JConfiguration = net.sf.saxon.Configuration;
using JAttributeMap = net.sf.saxon.om.AttributeMap; 
using JPipelineConfiguration = net.sf.saxon.@event.PipelineConfiguration;
using JReceiver = net.sf.saxon.@event.Receiver;
using JProperties = java.util.Properties;
using JCharSequence = java.lang.CharSequence;
using JXPathException = net.sf.saxon.trans.XPathException;
using JCharacterMapIndex = net.sf.saxon.serialize.CharacterMapIndex;
using JSchemaType = net.sf.saxon.type.SchemaType;
using JLocation = net.sf.saxon.s9api.Location;
using JProxyReceiver = net.sf.saxon.@event.ProxyReceiver;
using JNodeName = net.sf.saxon.om.NodeName;
using JNamespaceMap = net.sf.saxon.om.NamespaceMap;
using JItem = net.sf.saxon.om.Item;
using JNodeInfo = net.sf.saxon.om.NodeInfo;
using JDotNetDomBuilder = net.sf.saxon.dotnet.DotNetDomBuilder;
using JDotNetOutputStream = net.sf.saxon.dotnet.DotNetOutputStream;
using JDotNetWriter = net.sf.saxon.dotnet.DotNetWriter;
using JDotNetReceiver = net.sf.saxon.dotnet.DotNetReceiver;
using JSerializationProperties = net.sf.saxon.serialize.SerializationProperties;
using JDestination = net.sf.saxon.s9api.Destination;
using JAbstractDestination = net.sf.saxon.s9api.AbstractDestination;
using JAction = net.sf.saxon.s9api.Action;
using java.net;
using net.sf.saxon.s9api;

namespace Saxon.Api
{



    /// <summary>
    /// An abstract destination for the results of a query or transformation
    /// </summary>
    /// <remarks>
    /// </remarks>


    public interface XmlDestination
    {

        /// <summary>
        /// The underlying <c>Destination</c> object in the Saxon implementation
        /// </summary>
        /// <remarks>
        /// <para>This property provides access to internal methods in the Saxon engine that are
        /// not specifically exposed in the .NET API. In general these methods should be
        /// considered to be less stable than the classes in the Saxon.Api namespace.</para> 
        /// <para>The internal methods follow
        /// Java naming conventions rather than .NET conventions.</para>
		/// <para>Information about the returned <see cref="net.sf.saxon.Configuration"/> object 
		/// (and the objects it provides access to) is included in the Saxon JavaDoc docmentation.
        /// </para>
        /// </remarks>
        /// <returns>returns the underlying Destination object</returns>
		/**public**/ JDestination GetUnderlyingDestination();

    }

  

    /// <summary>
    /// A <c>Serializer</c> takes a tree representation of XML and turns
    /// it into lexical XML markup.
    /// </summary>
    /// <remarks>
    /// Note that this is serialization in the sense of the W3C XSLT and XQuery specifications.
    /// Unlike the class <c>System.Xml.Serialization.XmlSerializer</c>, this object does not
    /// serialize arbitrary CLI objects.
    /// </remarks>

    public class Serializer : XmlDestination
    {

        private net.sf.saxon.s9api.Serializer serializer;
        private JProperties props = new JProperties();
		private JCharacterMapIndex characterMap;
		private JProperties defaultOutputProperties = null;
        private Processor processor = null;
        private JConfiguration config = null; // Beware: this will often be null

        /// <summary>QName identifying the serialization parameter "method". If the method
        /// is a user-defined method, then it is given as a QName in Clark notation, that is
        /// "{uri}local".</summary>

        public static readonly QName METHOD =
            new QName("", "method");

        /// <summary>QName identifying the serialization parameter "byte-order-mark"</summary>

        public static readonly QName BYTE_ORDER_MARK =
            new QName("", "byte-order-mark");

        /// <summary>QName identifying the serialization parameter "cdata-section-elements".
        /// The value of this parameter is given as a space-separated list of expanded QNames in
        /// Clark notation, that is "{uri}local".</summary>

        public static readonly QName CDATA_SECTION_ELEMENTS =
            new QName("", "cdata-section-elements");

        /// <summary>QName identifying the serialization parameter "doctype-public"</summary>

        public static readonly QName DOCTYPE_PUBLIC =
            new QName("", "doctype-public");

        /// <summary>QName identifying the serialization parameter "doctype-system"</summary>

        public static readonly QName DOCTYPE_SYSTEM =
            new QName("", "doctype-system");

        /// <summary>QName identifying the serialization parameter "encoding"</summary>

        public static readonly QName ENCODING =
            new QName("", "encoding");

        /// <summary>QName identifying the serialization parameter "escape-uri-attributes".
        /// The value is the string "yes" or "no".</summary>

        public static readonly QName ESCAPE_URI_ATTRIBUTES =
            new QName("", "escape-uri-attributes");


        /// <summary>QName identifying the serialization parameter "include-content-type".
        /// The value is the string "yes" or "no".</summary>

        public static readonly QName INCLUDE_CONTENT_TYPE =
            new QName("", "include-content-type");

        /// <summary>QName identifying the serialization parameter "indent".
        /// The value is the string "yes" or "no".</summary>

        public static readonly QName INDENT =
            new QName("", "indent");

        /// <summary>QName identifying the serialization parameter "media-type".</summary>

        public static readonly QName MEDIA_TYPE =
            new QName("", "media-type");

        /// <summary>QName identifying the serialization parameter "normalization-form"</summary>

        public static readonly QName NORMALIZATION_FORM =
            new QName("", "normalization-form");

        /// <summary>
        /// Set to a string used to separate adjacent items in an XQuery result sequence
        /// </summary>
        public static readonly QName ITEM_SEPARATOR =
            new QName("", "item-separator");


        /// <summary>
        /// HTML version number
        /// </summary>
        public static readonly QName HTML_VERSION =
            new QName("" , "html-version");


        /// <summary>
        /// Build-tree option (XSLT only)
        /// </summary>
        public static readonly QName BUILD_TREE =
            new QName("", "build-tree");

        /// <summary>QName identifying the serialization parameter "omit-xml-declaration".
        /// The value is the string "yes" or "no".</summary>

        public static readonly QName OMIT_XML_DECLARATION =
            new QName("", "omit-xml-declaration");

        /// <summary>QName identifying the serialization parameter "standalone".
        /// The value is the string "yes" or "no" or "omit".</summary>

        public static readonly QName STANDALONE =
            new QName("", "standalone");

        /// <summary>QName identifying the serialization parameter "suppress-indentation"
        /// (introduced in XSLT/XQuery 3.0). Previously available as "saxon:suppress-indentation"
        /// The value is the string "yes" or "no" or "omit".</summary>

        public static readonly QName SUPPRESS_INDENTATION =
            new QName("", "suppress-indentation");

        /// <summary>QName identifying the serialization parameter "undeclare-prefixes".
        /// The value is the string "yes" or "no".</summary>

        public static readonly QName UNDECLARE_PREFIXES =
            new QName("", "undeclare-prefixes");

        /// <summary>QName identifying the serialization parameter "use-character-maps".
        /// This is available only with XSLT. The value of the parameter is a list of expanded QNames
        /// in Clark notation giving the names of character maps defined in the XSLT stylesheet.</summary>

        public static readonly QName USE_CHARACTER_MAPS =
            new QName("", "use-character-maps");

        /// <summary>QName identifying the serialization parameter "version"</summary>

        public static readonly QName VERSION =
            new QName("", "version");

        private static readonly String SAXON = NamespaceConstant.SAXON;


        /// <summary>QName identifying the serialization parameter "saxon:character-representation"</summary>


        public static readonly QName SAXON_CHARACTER_REPRESENTATION =
            new QName(SAXON, "saxon:character-representation");


        /// <summary>
        /// Saxon extension for use when writing to the text output method; this option causes the processing
        /// instructions hex and b64 to be recognized containing hexBinary or base64 data respectively.
        /// </summary>
        public static readonly QName SAXON_RECOGNIZE_BINARY =
            new QName("", "saxon:recognize-binary");

        /// <summary>QName identifying the serialization parameter "saxon:indent-spaces". The value
        /// is an integer (represented as a string) indicating the amount of indentation required.
        /// If specified, this parameter overrides indent="no".</summary>

        public static readonly QName SAXON_INDENT_SPACES =
            new QName(SAXON, "saxon:indent-spaces");


        /// <summary>
        /// Saxon extension: set to an integer (represented as a string) giving the desired maximum
        /// length of lines when indenting.Default is 80.
        /// </summary>
        public static readonly QName SAXON_LINE_LENGTH =
            new QName("", "saxon:line-length");


        /// <summary>
        /// Saxon extension: set to a space-separated list of attribute names, in Clark notation,
        /// indicating that attributes present in the list should be serialized in the order
        /// indicated, followed by attributes not present in the list(these are sorted first
        /// by namespace, then by local name).
        /// </summary>
        public static readonly QName SAXON_ATTRIBUTE_ORDER =
            new QName("", "saxon:attribute-order");

        /// <summary>
        ///  Saxon extension: request canonical XML output.
        /// </summary>
        public static readonly QName SAXON_CANONICAL =
            new QName("", "saxon:canonical");

        /// <summary>
        /// Saxon extension: set to any string. Indicates the sequence of characters used to represent
        /// a newline in the text output method, and in newlines used for indentation in any output
        /// methods that use indentation.
        /// </summary>
        public static readonly QName SAXON_NEWLINE =
            new QName("", "saxon:newline");


        /// <summary>
        /// Saxon extension for internal use: used in XSLT to tell the serializer whether the
        /// stylesheet used version="1.0" or version = "2.0"
        /// </summary>
        public static readonly QName SAXON_STYLESHEET_VERSION =
            new QName("", "saxon:stylesheet-version");

        /// <summary>QName identifying the serialization parameter "saxon:double-space". The value of this 
        /// parameter is given as a space-separated list of expanded QNames in Clark notation, that is 
        /// "{uri}local"; each QName identifies an element that should be preceded by an extra blank line within
        /// indented output.</summary>

        public static readonly QName SAXON_DOUBLE_SPACE =
            new QName(SAXON, "saxon:double-space");

        /// <summary>QName identifying the serialization parameter "suppress-indentation". Retained
        /// as a synonym of SUPPRESS_INDENTATION for backwards compatibility.</summary>

        public static readonly QName SAXON_SUPPRESS_INDENTATION = SUPPRESS_INDENTATION;

        /// <summary>QName identifying the serialization parameter "saxon:next-in-chain". This
        /// is available only with XSLT, and identifies the URI of a stylesheet that is to be used to
        /// process the results before passing them to their final destination.</summary>

        public static readonly QName NEXT_IN_CHAIN =
            new QName(SAXON, "saxon:next-in-chain");

        /// <summary>QName identifying the serialization parameter "saxon:require-well-formed". The
        /// value is the string "yes" or "no". If set to "yes", the output must be a well-formed
        /// document, or an error will be reported. ("Well-formed" here means that the document node
        /// must have exactly one element child, and no text node children other than whitespace-only
        /// text nodes).</summary>

        public static readonly QName SAXON_REQUIRE_WELL_FORMED =
            new QName(SAXON, "saxon:require-well-formed");


        /// <summary>
        /// Saxon extension for interfacing with debuggers; indicates that the location information is
        /// available for events in this output stream
        /// </summary>
        public static readonly QName SUPPLY_SOURCE_LOCATOR =
            new QName("", "supply-source-locator");


        /// <summary>
        /// Saxon extension, indicates that the output of a query is to be wrapped before serialization,
        /// such that each item in the result sequence is enclosed in an element indicating its type
        /// </summary>
        public static readonly QName SAXON_WRAP =
            new QName("", "", "wrap-result-sequence");

        /// <summary>Create a Serializer</summary>

        internal Serializer(net.sf.saxon.s9api.Serializer s)
        {
            serializer = s;
        }

        /// <summary>Set a serialization property</summary>
        /// <remarks>In the case of XSLT, properties set within the serializer override
        /// any properties set in <c>xsl:output</c> declarations in the stylesheet.
        /// Similarly, with XQuery, they override any properties set in the Query
        /// prolog using <c>declare option saxon:output</c>.</remarks>
        /// <example>
        ///   <code>
        ///     Serializer qout = new Serializer();
        ///     qout.SetOutputProperty(Serializer.METHOD, "xml");
        ///     qout.SetOutputProperty(Serializer.INDENT, "yes");
        ///     qout.SetOutputProperty(Serializer.SAXON_INDENT_SPACES, "1");
        ///   </code>
        /// </example> 
        /// <param name="name">The name of the serialization property to be set</param>
        /// <param name="value">The value to be set for the serialization property. May be null
        /// to unset the property (that is, to set it back to the default value).</param>

        public void SetOutputProperty(QName name, String value)
        {
           props.setProperty(name.ClarkName, value);
           serializer.setOutputProperty(net.sf.saxon.s9api.Serializer.getProperty(name.UnderlyingQName()), value);
        }

        /// <summary>
		/// Set default output properties, for use when no explicit properties are set using <c>SetOutputProperty()</c>.
        /// The values supplied are typically those specified in the stylesheet or query. In the case of XSLT,
        /// they are the properties associated with unamed <c>xsl:output</c> declarations.
        /// </summary>
        /// <param name="props"></param>

		public void SetDefaultOutputProperties(JProperties props)
		{
			this.defaultOutputProperties = props;
		}

        /// <summary>
        /// Get a character map
        /// </summary>
        
		public JCharacterMapIndex GetCharacterMap()
        {
            return this.characterMap;
        }

        /// <summary>
        /// Set a character map to be used
        /// </summary>
        /// <param name="charMap">the character map</param>

		public void SetCharacterMap(JCharacterMapIndex charMap)
		{
			this.characterMap = charMap;
		}
			

        /// <summary>Specify the destination of the serialized output, in the
        /// form of a file name</summary>
        /// <param name="filename">The name of the file to receive the serialized output. This
        /// method sets the destination base URI to the URI corresponding to the name of the supplied file.</param>
		/// <exception cref="DynamicError">Throws a <c>DynamicError</c> if it is not possible to 
		/// create an output stream to write to this file, for example, if the filename is in a
        /// directory that does not exist.</exception>

        public void SetOutputFile(String filename)
        {
            try
            {
                serializer.setOutputFile(new java.io.File(filename));
            }
            catch (java.io.IOException err)
            {
                JXPathException e = new JXPathException(err);
                throw new DynamicError(e);
            }
        }


        /// <summary>Specify the destination of the serialized output, in the
        /// form of a <c>Stream</c>.</summary>
        /// <remarks>Saxon will not close the stream on completion; this is the
        /// caller's responsibility.</remarks>
        /// <param name="stream">The stream to which the output will be written.
        /// This must be a stream that allows writing.</param>

        public void SetOutputStream(Stream stream)
        {

            serializer.setOutputStream(new JDotNetOutputStream(stream));
        }
			
    	/// <summary>Get the current output destination.</summary> 
		/// <returns>an <c>OutputStream</c>, <c>Writer</c>, or <c>File</c>, depending on the previous calls to
		/// <c>SetOutputstream</c>, <c>SetOutputWriter</c>, or <c>SetOutputFile</c>; or null, if no output destintion has
        /// been set up.</returns>

		public object GetOutputDestination(){
			return serializer.getOutputDestination();		
		}


		/// <summary>Set the <c>Processor</c> associated with this <c>Serializer</c>. This will be called automatically if the
		/// serializer is created using one of the <c>Processor.NewSerializer()</c> methods.</summary>
		/// <param name="processor"> the associated <c>Processor</c></param>
        
        public void SetProcessor(Processor processor)
        {
            this.processor = processor;
            this.config = processor.Implementation;
        }

        /// <summary>Specify the destination of the serialized output, in the
        /// form of a <c>TextWriter</c>.</summary>
        /// <remarks>Note that when writing to a <c>TextWriter</c>, character encoding is
        /// the responsibility of the <c>TextWriter</c>, not the <c>Serializer</c>. This
        /// means that the encoding requested in the output properties is ignored; it also
        /// means that characters that cannot be represented in the target encoding will
        /// use whatever fallback representation the <c>TextWriter</c> defines, rather than
        /// being represented as XML character references.</remarks>
        /// <param name="textWriter">The stream to which the output will be written.
        /// This must be a stream that allows writing. Saxon will not close the
        /// <c>TextWriter</c> on completion; this is the caller's responsibility.</param>

        public void SetOutputWriter(TextWriter textWriter)
        {
            serializer.setOutputWriter(new JDotNetWriter(textWriter));
        }


        /// <summary>
		/// Serialize an <c>XdmNode</c> to the selected output destination using this serializer.
        /// </summary>
        /// <param name="node">The node to be serialized</param>
        /// <remarks>since 9.8</remarks>
        public void SerializeXdmNode(XdmNode node)
        {
            serializer.serializeNode((net.sf.saxon.s9api.XdmNode)XdmValue.FromGroundedValueToJXdmValue(node.value));
        }


        /// <summary>
		/// Serialize an arbitary <c>XdmValue</c> to the selected output destination using this serializer.
        /// The supplied sequence is first wrapped in a document node according to the rules given in section 2
		/// (Sequence Normalization) of the XSLT/XQuery serialization specification; the resulting document node 
		/// is then serialized using the serialization parameters defined in this serializer. A call on this 
		/// method will close the writer or output stream internally.
        /// </summary>
        /// <param name="value"> The value to be serialized</param>
        /// <remarks>since 9.8</remarks>
        public void SerializeXdmValue(XdmValue value) {


            
            if (value is XdmNode)
            {
                SerializeXdmNode((XdmNode)value);  
            }
            else {
                serializer.serializeXdmValue(net.sf.saxon.s9api.XdmValue.wrap(value.Unwrap()));
            }

        }

        
        /// <summary>
        /// Close any resources associated with this destination. Note that this does <b>not</b>
        /// close any user-supplied OutputStream or Writer; those must be closed explicitly
        /// by the calling application.
        /// </summary>
        public void Close()
        {
            serializer.close();
        }


        /// <summary>
        /// The underlying <c>Destination</c> object in the Saxon implementation
        /// </summary>
        /// <remarks>
        /// <para>This property provides access to internal methods in the Saxon engine that are
        /// not specifically exposed in the .NET API. In general these methods should be
        /// considered to be less stable than the classes in the Saxon.Api namespace.</para> 
        /// <para>The internal methods follow
        /// Java naming conventions rather than .NET conventions.</para>
        /// <para>Information about the returned <see cref="net.sf.saxon.Configuration"/> object 
        /// (and the objects it provides access to) is included in the Saxon JavaDoc docmentation.
        /// </para>
        /// </remarks>
        /// <returns>returns the underlying Destination object</returns>
        public JDestination GetUnderlyingDestination()
        {
            return serializer;
        }


        /// <summary>
        /// Close the destination and notify all registered listeners that it has been closed.
        /// This method is intended for internal use by Saxon. The method first calls
		/// <see cref="Close"/> to close the destination, then it calls <c>java.util.function.Consumer.accept()</c> 
		/// on each of the listeners in turn to notify the fact that it has been closed.
        /// </summary>
        public void CloseAndNotify()
        {
            try
            {
                serializer.closeAndNotify();
            }
            catch (net.sf.saxon.s9api.SaxonApiException exception) {
                throw new StaticError(exception);
            }
        }


        /// <summary>This property determines the base Uri of the constructed <c>Serializer</c>. 
        /// </summary>

        public Uri BaseUri
        {
            get { return new Uri(serializer.getDestinationBaseURI().toASCIIString()); }
            set { serializer.setDestinationBaseURI(new java.net.URI(value.ToString())); }
        }



    }

    /// <summary>
	/// A <c>DomDestination</c> represents an <c>XmlDocument</c> that is constructed to hold the
    /// output of a query or transformation.
    /// </summary>
    /// <remarks>
    /// No data needs to be supplied to the <c>DomDestination</c> object. The query or transformation
    /// populates an <c>XmlDocument</c>, which may then be retrieved as the value of the <c>XmlDocument</c>
    /// property.
    /// </remarks>

    public class DomDestination : XmlDestination
    {

        internal JDotNetDomBuilder builder;
        internal net.sf.saxon.dotnet.DotNetDomDestination destination;

        /// <summary>Construct a <c>DomDestination</c>.</summary>
        /// <remarks>With this constructor, the system will create a new DOM Document
        /// to act as the destination of the query or transformation results. The document
		/// node of the new document may be retrieved via the <c>XmlDocument</c> property.</remarks>

        public DomDestination()
        {
            builder = new JDotNetDomBuilder();
            destination = new net.sf.saxon.dotnet.DotNetDomDestination(builder);
        }

        /// <summary>Construct a <c>DomDestination</c> based on an existing document node.</summary>
        /// <remarks>The new data will be added as a child of the supplied node.</remarks>
        /// <param name="attachmentPoint">The document node to which new contents will
        /// be attached. To ensure that the new document is well-formed, this document node
        /// should have no existing children.</param>

        public DomDestination(XmlDocument attachmentPoint)
        {
            builder = new JDotNetDomBuilder();
            builder.setAttachmentPoint(attachmentPoint);
        }

        /// <summary>Construct a <c>DomDestination</c> based on an existing document fragment node.</summary>
        /// <remarks>The new data will be added as a child of the supplied node.</remarks>
        /// <param name="attachmentPoint">The document fragment node to which new contents will
        /// be attached. The new contents will be added after any existing children.</param>

        public DomDestination(XmlDocumentFragment attachmentPoint)
        {
            builder = new JDotNetDomBuilder();
            builder.setAttachmentPoint(attachmentPoint);
        }

        /// <summary>Construct a <c>DomDestination</c> based on an existing element node.</summary>
        /// <remarks>The new data will be added as a child of the supplied element node.</remarks>
        /// <param name="attachmentPoint">The element node to which new contents will
        /// be attached. The new contents will be added after any existing children.</param>

        public DomDestination(XmlElement attachmentPoint)
        {
            builder = new JDotNetDomBuilder();
            builder.setAttachmentPoint(attachmentPoint);
        }

        /// <summary>After construction, retrieve the constructed document node.</summary>
        /// <remarks>If the zero-argument constructor was used, this will be a newly
        /// constructed document node. If the constructor supplied a document node, the
        /// same document node will be returned. If the constructor supplied a document fragment
        /// node or an element node, this method returns the <c>OwnerDocument</c> property of 
        /// that node.</remarks>

        public XmlDocument XmlDocument
        {
            get { return builder.getDocumentNode(); }
        }


        /// <summary>
        /// Close any resources associated with this destination. Note that this does <b>not</b>
        /// close any user-supplied OutputStream or Writer; those must be closed explicitly
        /// by the calling application.
        /// </summary>
        public void Close()
        {
            builder.close();
        }



        /// <summary>
        /// The underlying <c>Destination</c> object in the Saxon implementation
        /// </summary>
        /// <remarks>
        /// <para>This property provides access to internal methods in the Saxon engine that are
        /// not specifically exposed in the .NET API. In general these methods should be
        /// considered to be less stable than the classes in the Saxon.Api namespace.</para> 
        /// <para>The internal methods follow
        /// Java naming conventions rather than .NET conventions.</para>
        /// <para>Information about the returned <see cref="net.sf.saxon.Configuration"/> object 
        /// (and the objects it provides access to) is included in the Saxon JavaDoc docmentation.
        /// </para>
        /// </remarks>
        /// <returns>returns the underlying Destination object</returns>
        public JDestination GetUnderlyingDestination()
        {
            return destination;
        }


	}

	/// <summary>
	/// A <c>RawDestination</c> is an <c>Destination</c> that accepts a sequence output
	/// by a stylesheet or query and returns it directly as an <c>XdmValue</c>, without
	/// constructing an XML tree, and without serialization. It corresponds to the serialization
	/// option <code>build-tree="no"</code>.
	/// </summary>

    public class RawDestination : XmlDestination

    {

        internal net.sf.saxon.s9api.RawDestination destination;

        /// <summary>Construct a <c>RawDestination</c></summary>

        public RawDestination()
        {
            destination = new net.sf.saxon.s9api.RawDestination();
        }

        /// <summary>
        /// The underlying <c>Destination</c> object in the Saxon implementation
        /// </summary>
        /// <remarks>
        /// <para>This property provides access to internal methods in the Saxon engine that are
        /// not specifically exposed in the .NET API. In general these methods should be
        /// considered to be less stable than the classes in the Saxon.Api namespace.</para> 
        /// <para>The internal methods follow
        /// Java naming conventions rather than .NET conventions.</para>
        /// <para>Information about the returned <see cref="net.sf.saxon.Configuration"/> object 
        /// (and the objects it provides access to) is included in the Saxon JavaDoc docmentation.
        /// </para>
        /// </remarks>
        /// <returns>returns the underlying Destination object</returns>
        public JDestination GetUnderlyingDestination()
        {
            return destination;
        }

        /// <summary>
        /// Close the destination and notify all registered listeners that it has been closed.
        /// This method is intended for internal use by Saxon. The method first calls
		/// <see cref="Close"/> to close the destination, then it calls <c>java.util.function.Consumer.accept()</c>
		/// on each of the listeners in turn to notify the fact that it has been closed.
        /// </summary>
        public void CloseAndNotify()
        {
            destination.closeAndNotify();
        }



        /// <summary>This property determines the base URI of the constructed <c>XdmNode</c>. 
        /// If the <c>BaseUri</c> property of the <c>XdmDestination</c> is set before the destination is written to,
        /// then the constructed <c>XdmNode</c> will have this base URI. Setting this property after constructing the node
        /// has no effect.
        /// </summary>

        public Uri BaseUri
        {
            get { return new Uri(destination.getDestinationBaseURI().toASCIIString()); }
            set { destination.setDestinationBaseURI(new java.net.URI(value.ToString())); }
        }


        /// <summary>
        /// Close any resources associated with this destination. Note that this does <b>not</b>
        /// close any user-supplied OutputStream or Writer; those must be closed explicitly
        /// by the calling application.
        /// </summary>
        public void Close()
        {
            destination.close();
        }

        /// <summary>After construction, retrieve the constructed document node.</summary>
        /// <remarks>
        /// <para>The value of the property will be null if no data has been written to the
        /// <c>RawDestination</c>, either because the process that writes to the destination has not
        /// yet been run, or because the process produced no output.</para>
        /// </remarks>

        public XdmValue XdmValue
        {
            get
            {
                net.sf.saxon.om.GroundedValue value = destination.getXdmValue().getUnderlyingValue();
                return (value == null ? null : XdmValue.Wrap(value));
            }
        }
    }

    /// <summary>
	/// A <c>NullDestination</c> is an <c>XmlDestination</c> that discards all its output.
    /// </summary>

    public class NullDestination : XmlDestination
    {

        net.sf.saxon.s9api.NullDestination destination;
        /// <summary>Construct a <c>NullDestination</c></summary>

        public NullDestination()
        {
            destination = new net.sf.saxon.s9api.NullDestination();
        }

        /// <summary>This property determines the base Uri of the constructed <c>Serializer</c>. 
        /// </summary>

        public Uri BaseUri
        {
            get { return new Uri(destination.getDestinationBaseURI().toASCIIString()); }
            set { destination.setDestinationBaseURI(new java.net.URI(value.ToString())); }
        }


        /// <summary>
        /// The underlying <c>Destination</c> object in the Saxon implementation, which in the NullDestination is null.
        /// </summary>
        /// <returns>returns null</returns>
        public JDestination GetUnderlyingDestination()
        {
            return destination;
        }

    }


    /// <summary>
    /// A <c>TextWriterDestination</c> is an implementation of <c>XmlDestination</c> that wraps
    /// an instance of <c>XmlWriter</c>.
    /// </summary>
    /// <remarks>
    /// <para>The name <c>TextWriterDestination</c> is a misnomer; originally this class would
    /// only wrap an <c>XmlTextWriter</c>. It will now wrap any <c>XmlWriter</c>.</para>
    /// <para>Note that when a <c>TextWriterDestination</c> is used to process the output of a stylesheet
    /// or query, the output format depends only on the way the underlying <c>XmlWriter</c>
    /// is configured; serialization parameters present in the stylesheet or query are ignored.
    /// The XSLT <c>disable-output-escaping</c> option is also ignored. If serialization
    /// is to be controlled from the stylesheet or query, use a <c>Serializer</c> as the
    /// <c>Destination</c>.</para>
    /// </remarks>

    public class TextWriterDestination : JAbstractDestination, XmlDestination
    {

        internal XmlWriter writer;
        internal bool closeAfterUse = true;

		/// <summary>Construct a <c>TextWriterDestination</c></summary>
        /// <param name="writer">The <c>XmlWriter</c> that is to be notified of the events
        /// representing the XML document.</param>

        public TextWriterDestination(XmlWriter writer)
        {
            this.writer = writer;
        }

        /// <summary>
        /// The <c>CloseAfterUse</c> property indicates whether the underlying <c>XmlWriter</c> is closed
        /// (by calling its <c>Close()</c> method) when Saxon has finished writing to it. The default
        /// value is true, in which case <c>Close()</c> is called. If the property is set to <c>false</c>,
        /// Saxon will refrain from calling the <c>Close()</c> method, and merely call <c>Flush()</c>,
        /// which can be useful if further output is to be written to the <c>XmlWriter</c> by the application.
        /// </summary>

        public bool CloseAfterUse
        {
            get { return closeAfterUse; }
            set { closeAfterUse = value; }
        }

        /// <summary>
        /// Close any resources associated with this destination. Note that this does <b>not</b>
        /// close any user-supplied OutputStream or Writer; those must be closed explicitly
        /// by the calling application.
        /// </summary>
        public override void close()
        {
            writer.Close();
        }


        /// <summary>
		/// Return a <c>Receiver</c>. Saxon calls this method to obtain a Java <c>Receiver</c>, to which it then sends
        /// a sequence of events representing the content of an XML document. The method is intended
        /// primarily for internal use, and may give poor diagnostics if used incorrectly.
        /// </summary>
        /// <returns>The receiver</returns>
		/// <param name="pipe">The Saxon configuration as a <c>JPipelineConfiguration</c>. 
		/// This is supplied so that the destination can
        /// use information from the configuration (for example, a reference to the name pool)
		/// to construct or configure the returned <c>Receiver</c>.</param>
		/// <param name="params1"></param>
        
		public JReceiver GetReceiver(JPipelineConfiguration pipe, JSerializationProperties params1)
        {
            JDotNetReceiver dnr = new JDotNetReceiver(writer);
			dnr.setPipelineConfiguration (pipe);
            dnr.setCloseAfterUse(closeAfterUse);
            return params1.makeSequenceNormalizer(dnr);
        }

        /// <summary>
		/// Return a <c>Receiver</c>. Saxon calls this method to obtain a Java <c>Receiver</c>, to which it then sends
        /// a sequence of events representing the content of an XML document. The method is intended
        /// primarily for internal use, and may give poor diagnostics if used incorrectly.
        /// </summary>
        /// <returns>The receiver</returns>
		/// <param name="pipe">The Saxon configuration as a <c>JPipelineConfiguration</c>. 
		/// This is supplied so that the destination can
        /// use information from the configuration (for example, a reference to the name pool)
		/// to construct or configure the returned <c>Receiver</c>.</param>
		/// <param name="value2">Serialization parameters known to the caller of the method; typically, output
        ///  properties defined in a stylesheet or query. These will mainly be of interest if the destination is performing serialization, but
        ///  soem properties (such as <c>item-separator</c>) are also used in other situations.</param>
        public override JReceiver getReceiver(JPipelineConfiguration pipe, JSerializationProperties value2)
        {
            return GetReceiver(pipe, value2); 
        }

        /// <summary>
        /// The underlying <c>Destination</c> object in the Saxon implementation
        /// </summary>
        /// <remarks>
        /// <para>This property provides access to internal methods in the Saxon engine that are
        /// not specifically exposed in the .NET API. In general these methods should be
        /// considered to be less stable than the classes in the Saxon.Api namespace.</para> 
        /// <para>The internal methods follow
        /// Java naming conventions rather than .NET conventions.</para>
        /// <para>Information about the returned <see cref="net.sf.saxon.Configuration"/> object 
        /// (and the objects it provides access to) is included in the Saxon JavaDoc docmentation.
        /// </para>
        /// </remarks>
        /// <returns>returns the underlying Destination object</returns>
        public JDestination GetUnderlyingDestination()
        {
            return this;
        }
    }


    internal class AbstractDestination : XmlDestination
    {
        private Xslt30Transformer xslt30Transformer;
        private XmlDestination destination;

        internal AbstractDestination(Xslt30Transformer xslt30Transformer, XmlDestination destination)
        {
            this.xslt30Transformer = xslt30Transformer;
            this.destination = destination;
        }

        JDestination XmlDestination.GetUnderlyingDestination()
        {
            return xslt30Transformer.GetUnderlyingXslt30Transformer.asDocumentDestination(destination.GetUnderlyingDestination());
        }
    }


    /// <summary>
    /// An <c>XdmDestination</c> is an <c>XmlDestination</c> in which an <c>XdmNode</c> 
    /// is constructed to hold the output of a query or transformation: 
    /// that is, a tree using Saxon's implementation of the XDM data model.
    /// </summary>
    /// <remarks>
    /// <para>No data needs to be supplied to the <c>XdmDestination</c> object. The query or transformation
    /// populates an <c>XmlNode</c>, which may then be retrieved as the value of the <c>XmlNode</c>
    /// property.</para>
    /// <para>An <c>XdmDestination</c> can be reused to hold the results of a second transformation only
    /// if the <c>Reset</c> method is first called to reset its state.</para>
    /// </remarks>

    public class XdmDestination : XmlDestination
    {
        internal net.sf.saxon.s9api.XdmDestination destination;

        /// <summary>Construct an <c>XdmDestination</c></summary>

        public XdmDestination()
        {
            destination = new net.sf.saxon.s9api.XdmDestination();
        }

        ///<summary>
        /// The Tree Model implementation to be used for the constructed document. By default
		/// the <c>TinyTree</c> is used. The main reason for using the <c>LinkedTree</c> alternative is if
		/// updating is required (the <c>TinyTree</c> is not updateable).
        ///</summary>

        public TreeModel TreeModel
        {
            get
            {
                return  (TreeModel)destination.getTreeModel().getSymbolicValue();
            }
            set
            {
                destination.setTreeModel(net.sf.saxon.om.TreeModel.getTreeModel((int)value));
            }
        }

		/// <summary>This property determines the base URI of the constructed <c>XdmNode</c>. 
		/// If the <c>BaseUri</c> property of the <c>XdmDestination</c> is set before the destination is written to,
		/// then the constructed <c>XdmNode</c> will have this base URI. Setting this property after constructing the node
        /// has no effect.
        /// </summary>

        public Uri BaseUri
        {
            get { return new Uri(destination.getBaseURI().toASCIIString()); }
            set { destination.setBaseURI(new java.net.URI(value.ToString())); }
        }


        /// <summary>Reset the state of the <c>XdmDestination</c> so that it can be used to hold
        /// the result of another query or transformation.</summary>

        public void Reset()
        {
            destination.reset();
        }

        /// <summary>After construction, retrieve the constructed document node.</summary>
        /// <remarks>
        /// <para>The value of the property will be null if no data has been written to the
		/// <c>XdmDestination</c>, either because the process that writes to the destination has not
        /// yet been run, or because the process produced no output.</para>
        /// </remarks>

        public XdmNode XdmNode
        {
            get
            {
                JNodeInfo jnode = destination.getXdmNode().getUnderlyingNode();
                return (jnode == null ? null : (XdmNode)XdmValue.Wrap(jnode));
            }
        }


        /// <summary>
		/// Get the underlying Saxon <c>Destination</c> object from the <c>XdmDestination</c>.
        /// This method is for internal use but is provided for the benefit of applications that need to mix
        /// use of the Saxon .NET API with direct use of the underlying objects
        /// and methods offered by the Java implementation.
        /// </summary>
        public JDestination GetUnderlyingDestination()
        {
            return destination;
        }


        /// <summary>
        /// Set the base URI of the resource being written to this destination
        /// </summary>
        /// <param name="uri">the base URI to be used</param>
        public void setDestinationBaseURI(java.net.URI uri)
        {
            destination.setDestinationBaseURI(uri);
        }

        /// <summary>
        /// Get the base URI of the resource being written to this destination
        /// </summary>
        /// <returns>the base URI, or null if none is known</returns>
        public java.net.URI getDestinationBaseURI()
        {
            return destination.getDestinationBaseURI();
        }



        /// <summary>
        /// Close the destination, allowing resources to be released. Saxon calls this
        /// method when it has finished writing to the destination.
        /// </summary>
        public void Close()
        {
            destination.close();
        }

        /// <summary>
        /// <c>TreeProtector</c> is a filter that ensures that the events reaching the <c>Builder</c> constitute a single
        /// tree rooted at an element or document node (because anything else will crash the builder)
        /// </summary>

        public class TreeProtector : JProxyReceiver
        {

            private int level = 0;
            private bool ended = false;

            /// <summary>
            /// Constructor
            /// </summary>
            /// <param name="next">Set the underlying receiver</param>
            public TreeProtector(JReceiver next)
                : base(next)
            {

            }

            /// <summary>
            /// Start of a document node
            /// </summary>
            /// <param name="properties"></param>
            public override void startDocument(int properties)
            {
                if (ended)
                {
                    JXPathException e = new JXPathException("Only a single document can be written to an XdmDestination");
                    throw new DynamicError(e);
                }
                base.startDocument(properties);
                level++;
            }

            /// <summary>
            /// Notify the end of a document node
            /// </summary>
            public override void endDocument()
            {
                base.endDocument();
                level--;
                if (level == 0)
                {
                    ended = true;
                }
            }

            /// <summary>
            /// Notify the start of an element
            /// </summary>
            /// <param name="nameCode"></param>
			/// <param name="typeCode"></param>
			/// <param name="attributes"></param>
			/// <param name="namespaces"></param>
            /// <param name="location"></param>
            /// <param name="properties"></param>
            public override void startElement(JNodeName nameCode, JSchemaType typeCode, JAttributeMap attributes, JNamespaceMap namespaces, JLocation location, int properties)
            {
                if (ended)
                {
                    JXPathException e = new JXPathException("Only a single root node can be written to an XdmDestination");
                    throw new DynamicError(e);
                }
                base.startElement(nameCode, typeCode, attributes, namespaces, location, properties);
                level++;
            }

            /// <summary>
            /// End of element
            /// </summary>
            public override void endElement()
            {
                base.endElement();
                level--;
                if (level == 0)
                {
                    ended = true;
                }
            }

            /// <summary>
            /// Character data
            /// </summary>
            /// <param name="chars">Character data as input</param>
            /// <param name="location">Provides information such as line number and system ID</param>
            /// <param name="properties">Bit significant value. The following bits are defined</param>
			public override void characters(JCharSequence chars, JLocation location, int properties)
            {
                if (level == 0)
                {
                    JXPathException e = new JXPathException("When writing to an XdmDestination, text nodes are only allowed within a document or element node");
                    throw new DynamicError(e);
                }
                base.characters(chars, location, properties);
            }

            /// <summary>
            /// Processing instruction
            /// </summary>
            /// <param name="target">The PI name. This must be a legal name (it will not be checked)</param>
            /// <param name="data">The data portion of the processing instruction</param>
            /// <param name="location">provides information about the PI</param>
            /// <param name="properties">Additional information about the PI</param>
			public override void processingInstruction(String target, JCharSequence data, JLocation location, int properties)
            {
                if (level == 0)
                {
                    JXPathException e = new JXPathException("When writing to an XdmDestination, processing instructions are only allowed within a document or element node");
                    throw new DynamicError(e);
                }
                base.processingInstruction(target, data, location, properties);
            }

            /// <summary>
            /// Output a comment
            /// </summary>
            /// <param name="chars">The content of the comment</param>
            /// <param name="location">provides information such as line number and system ID</param>
            /// <param name="properties">Additional information about the comment</param>
			public override void comment(JCharSequence chars, JLocation location, int properties)
            {
                if (level == 0)
                {
                    JXPathException e = new JXPathException("When writing to an XdmDestination, comment nodes are only allowed within a document or element node");
                }
                base.comment(chars, location, properties);
            }


            /// <summary>
            /// Append an  arbitrary item (node or atomic value) to the output
            /// </summary>
            /// <param name="item">the item to be appended</param>
            /// <param name="location">the location of the calling instruction, for diagnostics</param>
            /// <param name="copyNamespaces">if the item is an element node, this indicates whether its namespace need to be copied. 
            /// 0x80000 means ALL_NAMESPACE, 0x40000 means LOCAL_NAMESPACE and 0 means no namespace</param>
			public override void append(JItem item, JLocation location, int copyNamespaces)
            {
                if (level == 0)
                {
                    JXPathException e = new JXPathException("When writing to an XdmDestination, atomic values are only allowed within a document or element node");
                }
                base.append(item, location, copyNamespaces);
            }

        }
    }


}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////