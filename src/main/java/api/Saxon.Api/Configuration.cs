using System;
using System.IO;
using System.Xml;
using System.Collections;
using System.Globalization;
using JResult = net.sf.saxon.@event.Receiver;
using JConfiguration = net.sf.saxon.Configuration;
using JVersion = net.sf.saxon.Version;
using JLogger = net.sf.saxon.lib.Logger;
using JDotNetDocumentWrapper = net.sf.saxon.dotnet.DotNetDocumentWrapper;
using JDotNetObjectModel = net.sf.saxon.dotnet.DotNetObjectModel;
using JNodeInfo = net.sf.saxon.om.NodeInfo;
using JSequence = net.sf.saxon.om.Sequence;
using JPipelineConfiguration = net.sf.saxon.@event.PipelineConfiguration;
using AugmentedSource = net.sf.saxon.lib.AugmentedSource;
using NodeName = net.sf.saxon.om.NodeName;
using FingerprintedQName = net.sf.saxon.om.FingerprintedQName;
using Whitespace = net.sf.saxon.value.Whitespace;
using JReceiver = net.sf.saxon.@event.Receiver;
using JValidation = net.sf.saxon.lib.Validation;
using JXPathException = net.sf.saxon.trans.XPathException;
using JProcessor = net.sf.saxon.s9api.Processor;
using JParseOptions = net.sf.saxon.lib.ParseOptions;
using JPullSource = net.sf.saxon.pull.PullSource;
using JPullProvider = net.sf.saxon.pull.PullProvider;
using JDotNetWriter = net.sf.saxon.dotnet.DotNetWriter;
using JDotNetInputStream = net.sf.saxon.dotnet.DotNetInputStream;
using JDotNetURIResolver = net.sf.saxon.dotnet.DotNetURIResolver;
using JDotNetEnumerableCollection = net.sf.saxon.dotnet.DotNetEnumerableCollection;
using JDotNetPullProvider = net.sf.saxon.dotnet.DotNetPullProvider;
using JDotNetReader = net.sf.saxon.dotnet.DotNetReader;
using JDotNetComparator = net.sf.saxon.dotnet.DotNetComparator;
using JDocumentBuilder = net.sf.saxon.s9api.DocumentBuilder;
using JWhiteSpaceStrippingPolicy = net.sf.saxon.s9api.WhitespaceStrippingPolicy;
using JSpaceStrippingRule = net.sf.saxon.om.SpaceStrippingRule;
using JWhitespace = net.sf.saxon.value.Whitespace;
using JXsltExecutable = net.sf.saxon.s9api.XsltExecutable;
using JFilterFactory = net.sf.saxon.@event.FilterFactory;
using JProxyReceiver = net.sf.saxon.@event.ProxyReceiver;
using JExpressionPresenter = net.sf.saxon.trace.ExpressionPresenter;
using JStripper = net.sf.saxon.@event.Stripper;
using JSchemaType = net.sf.saxon.type.SchemaType;
using JStreamResult = javax.xml.transform.stream.StreamResult;
using JSource = javax.xml.transform.Source;
using JStreamSource = javax.xml.transform.stream.StreamSource;
using java.util.function;

namespace Saxon.Api
{

    /// <summary>
	/// The <c>Processor</c> class serves three purposes: it allows global Saxon configuration
    /// options to be set; it acts as a factory for generating XQuery, XPath, and XSLT
	/// compilers; and it owns certain shared resources such as the Saxon <c>NamePool</c> and 
    /// compiled schemas. This is the first object that a Saxon application should create. Once
	/// established, a <c>Processor</c> may be used in multiple threads.
    /// </summary>

    [Serializable]
    public class Processor : JConfiguration.ApiProvider
    {

        //Transformation data variables
        private SchemaManager schemaManager = null;
        /*internal JConfiguration config;
       */
        private TextWriter textWriter = Console.Error;
        private JProcessor processor;
        private IQueryResolver moduleResolver;
        private ICollectionFinder collectionFinder = null;
        private StandardCollectionFinder standardCollectionFinder;

        internal Processor(JProcessor p) {
            processor = p;
            processor.getUnderlyingConfiguration().setProcessor(this);
            collectionFinder = new StandardCollectionFinder(Implementation.getCollectionFinder());
            standardCollectionFinder = new StandardCollectionFinder(Implementation.getStandardCollectionFinder());
        }

        /// <summary>
        /// Create a new <c>Processor</c>. This <c>Processor</c> will have capabilities that depend on the version
        /// of the software that has been loaded, and on the features that have been licensed.
        /// </summary>

        public Processor()
        {
            processor = new JProcessor(false);
            processor.getUnderlyingConfiguration().setProcessor(this);
            processor.getUnderlyingConfiguration().registerExternalObjectModel(new DotNetObjectModelDefinition());
            collectionFinder = new StandardCollectionFinder(Implementation.getCollectionFinder());
            standardCollectionFinder = new StandardCollectionFinder(Implementation.getStandardCollectionFinder());
        }

        /// <summary>
		/// Create a <c>Processor</c>.
        /// </summary>
		/// <param name="licensedEdition">Set to true if the <c>Processor</c> is to use a licensed edition of Saxon
		/// (that is, Saxon-PE or Saxon-EE). If true, the <c>Processor</c> will attempt to enable the capabilities
        /// of the licensed edition of Saxon, according to the version of the software that is loaded, and will
		/// verify the license key. If false, the <c>Processor</c> will load a default <c>Configuration</c> that gives restricted
        /// capability and does not require a license, regardless of which version of the software is actually being run.</param>

        public Processor(bool licensedEdition)
            // newline needed by documentation stylesheet
            : this(licensedEdition, false) { }

        /// <summary>
		/// Create a <c>Processor</c>.
        /// </summary>
		/// <param name="licensedEdition">Set to true if the <c>Processor</c> is to use a licensed edition of Saxon
		/// (that is, Saxon-PE or Saxon-EE). If true, the <c>Processor</c> will attempt to enable the capabilities
        /// of the licensed edition of Saxon, according to the version of the software that is loaded, and will
		/// verify the license key. If false, the <c>Processor</c> will load a default <c>Configuration</c> that gives restricted
        /// capability and does not require a license, regardless of which version of the software is actually being run.</param>
        /// <param name="loadLocally">This option has no effect at this release.</param>

        public Processor(bool licensedEdition, bool loadLocally)
        {
            processor = new JProcessor(licensedEdition);
            processor.getUnderlyingConfiguration().registerExternalObjectModel(new DotNetObjectModelDefinition());
        }

        /// <summary>
        /// Create a <c>Processor</c>, based on configuration information supplied in a configuration file.
        /// </summary>
        /// <param name="configurationFile">A stream holding the text of the XML configuration file. Details of the file format
        /// can be found in the Saxon documentation.</param>

        [Obsolete("Use the Processor(Stream, Uri) constructor instead.")]
        public Processor(Stream configurationFile)
        {
            JStreamSource ss = new JStreamSource(new JDotNetInputStream(configurationFile));
            JConfiguration config = JConfiguration.readConfiguration(ss);
            config.registerExternalObjectModel(new DotNetObjectModelDefinition());
            processor = new JProcessor(config);
        }

        /// <summary>
        /// Create a <c>Processor</c>, based on configuration information supplied in a configuration file.
        /// </summary>
        /// <param name="configurationFile">A stream holding the text of the XML configuration file. Details of the file format
        /// can be found in the Saxon documentation.</param>
        /// <param name="baseUri">baseUri of the configuration file used for resolving any relative URIs in the file</param> 

        public Processor(Stream configurationFile, Uri baseUri)
        {
            JStreamSource ss = new JStreamSource(new JDotNetInputStream(configurationFile), baseUri.AbsoluteUri);
            JConfiguration config = JConfiguration.readConfiguration(ss);
            config.registerExternalObjectModel(new DotNetObjectModelDefinition());
            processor = new JProcessor(config);
        }

        public ICollectionFinder CollectionFinder
		{  
            set {
                if (value != null)
                {
                    collectionFinder = value;
                    Implementation.setCollectionFinder(new CollectionFinderWrapper(collectionFinder));

                }

            }
            get {
                return collectionFinder;
            }

        }


        public StandardCollectionFinder StandardCollectionFinder
        {
            get
            {
                return standardCollectionFinder;
            }

        }

		/// <summary>
		/// Register a specific URI and bind it to a specific <c>ResourceCollection</c>
		/// </summary>
		/// <param name="collectionURI">the collection URI to be registered. Must not be null.</param>
		/// <param name="collection">the ResourceCollection to be associated with this URI. Must not be null.</param>
        public void RegisterCollection(String collectionURI, IResourceCollection collection) {
            StandardCollectionFinder.RegisterCollection(collectionURI, collection);
            if (CollectionFinder is StandardCollectionFinder && collectionFinder != standardCollectionFinder) {
                ((StandardCollectionFinder)collectionFinder).RegisterCollection(collectionURI, collection);
            }
        }



        /// <summary>
        /// Declare a mapping from a specific namespace URI to a .NET class
        /// This will get applied to Saxon-PEN or Saxon-EEN product
        /// </summary>
        /// <param name="uri">the namespace URI of the function name</param>
        /// <param name="type">the .NET class that implements the functions in this namespace</param>
        public void BindExtensions(string uri, System.Type type) {
            JConfiguration config = processor.getUnderlyingConfiguration();
            config.bindExtensions(uri, type);
        }




        /// <summary>
		/// Get the full name of the Saxon product version implemented by this <c>Processor</c>
        /// </summary>

        public string ProductTitle
        {
            get { return JVersion.getProductTitle(); }
        }

        /// <summary>
        /// Get the Saxon product version number (for example, "9.2.0.2")
        /// </summary>

        public string ProductVersion
        {
            get { return JVersion.getProductVersion(); }
        }

        /// <summary>
        /// Get the Saxon product edition (for example, "EE" for Enterprise Edition)
        /// </summary>
        /// 

        public string Edition
        {
            get { return processor.getUnderlyingConfiguration().getEditionCode(); }
        }



        /// <summary>
		/// Gets the <c>SchemaManager</c> for the <c>Processor</c>. Returns null
		/// if the <c>Processor</c> is not schema-aware.
        /// </summary>

        public SchemaManager SchemaManager
        {
            get {
                if (schemaManager == null)
                {
                    schemaManager = new SchemaManager(this);
                }

                return schemaManager; }
        }

        /// <summary>
		/// An <c>XmlResolver</c>, which will be used while compiling and running queries, 
		/// XPath expressions, and stylesheets, if no other <c>XmlResolver</c> is nominated
        /// </summary>
        /// <remarks>
        /// <para>By default an <c>XmlUrlResolver</c> is used. This means that the responsibility
        /// for resolving and dereferencing URIs rests with the .NET platform, not with the
        /// IKVM/OpenJDK runtime.</para>
        /// <para>When Saxon invokes a user-written <c>XmlResolver</c>, the <c>GetEntity</c> method
        /// may return any of: a <c>System.IO.Stream</c>; a <c>System.IO.TextReader</c>; or a
        /// <c>java.xml.transform.Source</c>.</para>
        /// </remarks>

        public XmlResolver XmlResolver
        {
            get
            {
                javax.xml.transform.URIResolver resolver = processor.getUnderlyingConfiguration().getURIResolver();
                if (resolver is JDotNetURIResolver) {
                    return ((JDotNetURIResolver)resolver).getXmlResolver();
                } else {
                    return new XmlUrlResolver();
                }
            }
            set
            {
                processor.getUnderlyingConfiguration().setURIResolver(new JDotNetURIResolver(value));
            }
        }


        /// <summary>
		/// A <c>TextWriter</c> used as the destination of miscellaneous error, warning, and progress messages.
        /// </summary>
        /// <remarks>
        /// <para>By default the <c>Console.Error</c> is used for all such messages.</para>
        /// <para>A user can supply their own <c>TextWriter</c> to redirect error messages from the standard output.</para>
        /// </remarks>
        public TextWriter ErrorWriter
        {
            get
            {
                return textWriter;
            }
            set
            {
                textWriter = value;
                StandardLogger logger = new StandardLogger(value);
                processor.getUnderlyingConfiguration().setLogger(logger);
            }
        }

        /// <summary>
        /// Create a new <c>DocumentBuilder</c>, which may be used to build XDM documents from
        /// a variety of sources.
        /// </summary>
        /// <returns>A new <c>DocumentBuilder</c></returns>

        public DocumentBuilder NewDocumentBuilder()
        {
            DocumentBuilder builder = new DocumentBuilder(this);
            builder.XmlResolver = XmlResolver;
            return builder;
        }

        /// <summary>
		/// Create a new <c>XQueryCompiler</c>, which may be used to compile XQuery queries.
        /// </summary>
        /// <remarks>
		/// The returned <c>XQueryCompiler</c> retains a live link to the <c>Processor</c>, and
		/// may be affected by subsequent changes to the <c>Processor</c>.
        /// </remarks>
		/// <returns>A new <c>XQueryCompiler</c></returns>

        public XQueryCompiler NewXQueryCompiler()
        {
            return new XQueryCompiler(this);
        }

        /// <summary>
		/// Create a new <c>XsltCompiler</c>, which may be used to compile XSLT stylesheets.
        /// </summary>
        /// <remarks>
		/// The returned <c>XsltCompiler</c> retains a live link to the <c>Processor</c>, and
		/// may be affected by subsequent changes to the <c>Processor</c>.
        /// </remarks>
		/// <returns>A new <c>XsltCompiler</c></returns>

        public XsltCompiler NewXsltCompiler()
        {
            return new XsltCompiler(this);
        }

        /// <summary>
		/// Create a new <c>XPathCompiler</c>, which may be used to compile XPath expressions.
        /// </summary>
        /// <remarks>
		/// The returned <c>XPathCompiler</c> retains a live link to the <c>Processor</c>, and
		/// may be affected by subsequent changes to the <c>Processor</c>.
        /// </remarks>
		/// <returns>A new <c>XPathCompiler</c></returns>

        public XPathCompiler NewXPathCompiler()
        {
            return new XPathCompiler(this, JProcessor.newXPathCompiler());
        }

        /// <summary>
		/// Create a <c>Serializer</c>
        /// </summary>
		/// <returns> a new <c>Serializer</c> </returns>
        public Serializer NewSerializer() {
            Serializer s = new Serializer(processor.newSerializer());
            s.SetProcessor(this); //TODO this method call might no longer be needed
            return s;
        }

        /// <summary>
        /// Create a <c>Serializer</c> initialized to write to a given <c>TextWriter</c>.
        /// Closing the writer after use is the responsibility of the caller.
        /// </summary>
        /// <param name="textWriter">The <c>TextWriter</c> to which the <c>Serializer</c> will write</param>
        /// <returns> a new <c>Serializer</c> </returns>
        public Serializer NewSerializer(TextWriter textWriter)
        {
            Serializer s = new Serializer(processor.newSerializer());
            s.SetOutputWriter(textWriter);
            return s;
        }

        /// <summary>
        /// Create a <c>Serializer</c> initialized to write to a given output <c>Stream</c>.
        /// Closing the output stream after use is the responsibility of the caller.
        /// </summary>
        /// <param name="stream">The output <c>Stream</c> to which the <c>Serializer</c> will write</param>
        /// <returns> a new Serializer </returns>
        public Serializer NewSerializer(Stream stream)
        {
            Serializer s = new Serializer(processor.newSerializer());
            s.SetOutputStream(stream);
            return s;
        }

        /// <summary>
        /// The XML version used in this <c>Processor</c>
        /// </summary>
        /// <remarks>
        /// The value must be 1.0 or 1.1, as a <c>decimal</c>. The default version is currently 1.0, but may
        /// change in the future.
        /// </remarks>

        public decimal XmlVersion
        {
            get
            {
                return (processor.getUnderlyingConfiguration().getXMLVersion() == JConfiguration.XML10 ? 1.0m : 1.1m);
            }
            set
            {
                if (value == 1.0m)
                {
                    processor.setXmlVersion("1.0");
                }
                else if (value == 1.1m)
                {
                    processor.setXmlVersion("1.1");
                }
                else
                {
                    throw new ArgumentException("Invalid XML version: " + value);
                }
            }
        }

        /// <summary>
        /// Create a collation based on a given <c>CompareInfo</c> and <c>CompareOptions</c>    
        /// </summary>
        /// <param name="uri">The collation URI to be used within an XPath expression to refer to this collation</param>
        /// <param name="compareInfo">The <c>CompareInfo</c>, which determines the language-specific
        /// collation rules to be used</param>
        /// <param name="options">Options to be used in performing comparisons, for example
        /// whether they are to be case-blind and/or accent-blind</param>

        public void DeclareCollation(Uri uri, CompareInfo compareInfo, CompareOptions options)
        {
            JDotNetComparator comparator = new JDotNetComparator(uri.ToString(), compareInfo, options);
            Implementation.registerCollation(uri.ToString(), comparator);
        }

        /// <summary>
        /// Register a named collection. A collection is identified by a URI (the collection URI),
        /// and its content is represented by an <c>IEnumerable</c> that enumerates the contents
        /// of the collection. The values delivered by this enumeration are Uri values, which 
        /// can be mapped to nodes using the registered <c>XmlResolver</c>.
        /// </summary>
        /// <param name="collectionUri">The URI used to identify the collection in a call
        /// of the XPath <c>collection()</c> function. The default collection is registered
        /// by supplying null as the value of this argument (this is the collection returned
        /// when the XPath <c>collection()</c> function is called with no arguments).</param> 
        /// <param name="contents">An enumerable object that represents the contents of the
        /// collection, as a sequence of document URIs. The enumerator returned by this
        /// IEnumerable object must return instances of the Uri class.</param>
        /// <remarks>
        /// <para>Collections should be stable: that is, two calls to retrieve the same collection URI
        /// should return the same sequence of document URIs. This requirement is imposed by the
        /// W3C specifications, but in the case of a user-defined collection it is not enforced by
        /// the Saxon product.</para>
        /// <para>A collection may be replaced by specifying the URI of an existing
        /// collection.</para>
        /// <para>Collections registered with a processor are available to all queries and stylesheets
        /// running under the control of that processor. Collections should not normally be registered
        /// while queries and transformations are in progress.</para>
        /// </remarks>
        /// 

        public void RegisterCollection(Uri collectionUri, IEnumerable contents)
        {
            String u = (collectionUri == null ? null : collectionUri.ToString());
            JConfiguration config = processor.getUnderlyingConfiguration();
            config.registerCollection(u, new JDotNetEnumerableCollection(config, contents));
        }

        /// <summary>
		/// Register an extension function with the <c>Processor</c>
        /// </summary>
        /// <param name="function">
        /// An object that defines the extension function, including its name, arity, arguments types, and
        /// a reference to the class that implements the extension function call.
        /// </param>

        public void RegisterExtensionFunction(ExtensionFunctionDefinition function)
        {
            WrappedExtensionFunctionDefinition f = new WrappedExtensionFunctionDefinition(function);
            processor.registerExtensionFunction(f);
        }

        /// <summary>
        /// Register a simple external/extension function that is to be made available within any stylesheet, query
        /// or XPath expression compiled under the control of this <c>Processor</c>
        /// </summary>
        /// <param name="function">
        /// This interface provides only for simple extensions that have no side-effects and no
        /// dependencies on the static or dynamic context.
        /// </param>

        public void RegisterExtensionFunction(ExtensionFunction function)
        {
            WrappedExtensionFunction f = new WrappedExtensionFunction(function);
            processor.registerExtensionFunction(f);
        }


        /// <summary>
        /// Set the media type to be associated with a file extension by  the standard collection handler
        /// </summary>
        /// <param name="extension">the file extension, for exmaple "xml". The value "" sets the 
        /// default media type to be used for unregistered file extensions</param>
        /// <param name="mediaType">the corresponding media type, for example "application/xml". The
        /// choice of media type determines how a resource with this extension gets parsed, when the file 
        /// appears as part of a collection</param>
        public void RegisterFileExtension(String extension, String mediaType) {
            Implementation.registerFileExtension(extension, mediaType);
        }

        /// <summary>
        /// Associated a media type with a resource factory. This methodm may be called
        /// to customize the behaviour of a collection to recognize different file extensions
        /// </summary>
        /// <param name="contentType">a media type or MIME type, for example application/xsd+xml</param>
        /// <param name="factory">a ResourceFactory used to parse (or otherweise process) resource of that type</param>
        public void RegisterMediaType(String contentType, IResourceFactory factory) {
            Implementation.registerMediaType(contentType, new ResourceFactoryWrapper(factory, this));
        }


        /// <summary>
        /// Get the media type to be associated with a file extension by the standard 
        /// collection handler
        /// </summary>
        /// <param name="extension">the file extension, for example "xml". The value "" gets
        /// the default media type to be used for unregistered file extensions. The default 
        /// media type is also returned if the supplied file extension is not registered</param>
        /// <returns>the corresponding media type, for example "application/xml". The choice
        /// of media type determines how a resource with this extension gets parsed, when the file
        /// appears as part of a collection.</returns>
        public String GetMediaTypeForFileExtension(String extension) {
            return Implementation.getMediaTypeForFileExtension(extension);
        }

        /// <summary>
		/// Copy an <c>XdmValue</c> to an <c>XmlDestination</c>
        /// </summary>
        /// <remarks>
        /// In principle this method can be used to copy any kind of <c>XdmValue</c> to any kind
        /// of <c>XmlDestination</c>. However, some kinds of destination may not accept arbitrary
        /// sequences of items; for example, some may reject function items. Some destinations
        /// perform sequence normalization, as defined in the W3C serialization specification,
        /// to convert the supplied sequence to a well-formed XML document; it is a property
        /// of the chosen <c>XmlDestination</c> whether it does this or not.</remarks>
        /// <param name="sequence">The value to be written</param>
        /// <param name="destination">The destination to which the value should be written</param>
        /// 

        public void WriteXdmValue(XdmValue sequence, XmlDestination destination)
        {
            try
            {
                JPipelineConfiguration pipe = processor.getUnderlyingConfiguration().makePipelineConfiguration();
                JResult r = destination.GetUnderlyingDestination().getReceiver(pipe, pipe.getConfiguration().obtainDefaultSerializationProperties());
                r.open();
                foreach (XdmItem item in sequence) {
                    r.append(item.Unwrap().head());
                }
                r.close();
            } catch (JXPathException err) {
                throw new DynamicError(err);
            }
        }


        /// <summary>
		/// The underlying <c>Configuration</c> object in the Saxon implementation
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

        public JConfiguration Implementation
        {
            get { return processor.getUnderlyingConfiguration(); }
        }

        /// <summary>
        /// A user-supplied <c>IQueryResolver</c> used to resolve location hints appearing in an
        /// <c>import module</c> declaration.
        /// </summary>
        /// <remarks>
        /// <para>This acts as the default value for the ModuleURIResolver.</para>
        /// <para> The URI Resolver for XQuery modules. May be null, in which case any 
        /// existing Module URI Resolver is removed  from the Configuration</para>
        /// </remarks>
        public IQueryResolver QueryResolver
        {
            get { return moduleResolver; }
            set
            {
                moduleResolver = value;
                processor.getUnderlyingConfiguration().setModuleURIResolver((value == null ? null : new DotNetModuleURIResolver(value)));
            }
        }

        /// <summary>
        /// The underlying <c>net.sf.saxon.s9api.Processor</c> in the Java implementation
        /// </summary>

        public JProcessor JProcessor
        {
            get { return processor; }
        }

        /// <summary>
        /// Set a configuration property
        /// </summary>
        /// <remarks>
        /// <para>This method provides the ability to set named properties of the configuration.
        /// The property names are set as strings, whose values can be found in the Java
        /// class <c>net.sf.saxon.FeatureKeys</c>. The property values are always strings. 
        /// Properties whose values are other types are not available via this interface:
        /// however all properties have an effective equivalent whose value is a string.
        /// Note that on/off properties are set using the strings "true" and "false".</para>
        /// <para><i>Method added in Saxon 9.1</i></para>
        /// </remarks>
        /// <param name="name">The property name</param>
        /// <param name="value">The property value</param>
        public void SetProperty(String name, String value)
        {
            if (name.Equals("http://saxonica.com/oem-data"))
            {
                processor.setConfigurationProperty("http://saxonica.com/oem-data", value);
            }
            else
            {
                processor.setConfigurationProperty(net.sf.saxon.lib.Feature.byName(name), value);
            }
        }

        /// <summary>
        /// Set a configuration property
        /// </summary>
        /// <param name="feature">The property feature</param>
        /// <param name="value">The property value</param>

        public void SetProperty<T>(Feature<T> feature, T value)
        {
            if (value.GetType() == typeof(Boolean))
            {
                processor.setConfigurationProperty(feature.JFeature, java.lang.Boolean.valueOf((bool)(object)value));
            }
            else
            {
                processor.setConfigurationProperty(feature.JFeature, (object)value);
            }
        }

        /// <summary>
        /// Get the value of a configuration property
        /// </summary>
        /// <remarks>
        /// <para>This method provides the ability to get named properties of the configuration.
        /// The property names are supplied as strings, whose values can be found in the Java
        /// class <c>net.sf.saxon.FeatureKeys</c>. The property values are always returned as strings. 
        /// Properties whose values are other types are returned by converting the value to a string.
        /// Note that on/off properties are returned using the strings "true" and "false".</para>
        /// <para><i>Method added in Saxon 9.1</i></para>
        /// </remarks>
        /// <param name="name">The property name</param>
        /// <returns>The property value, as a string; or null if the property is unset.</returns>

        public String GetProperty(String name)
        {
            Object obj = processor.getConfigurationProperty(net.sf.saxon.lib.Feature.byName(name));
            return (obj == null ? null : obj.ToString());
        }


        /// <summary>
        /// Get a property of the configuration
        /// </summary>
        /// <typeparam name="T">See the class <c>Feature</c> for constants 
        /// representing the properties that can be requested.</typeparam>
        /// <param name="feature">the required property. </param>
        /// <returns>the value of the property</returns>
        public T GetProperty<T>(Feature<T> feature) {
            return (T)processor.getConfigurationProperty(feature.JFeature);
        }

    }

    /// <summary>
    /// The <c>DocumentBuilder</c> class enables XDM documents to be built from various sources.
    /// The class is always instantiated using the <c>NewDocumentBuilder</c> method
    /// on the <c>Processor</c> object.
    /// </summary>

    [Serializable]
    public class DocumentBuilder
    {

        private Processor processor;
        private JConfiguration config;
        private XmlResolver xmlResolver;
        private SchemaValidationMode validation;
        private SchemaValidator schemaValidator;
        private WhitespacePolicy whitespacePolicy;
        private Uri baseUri;
        private QName topLevelElement;
        private XQueryExecutable projectionQuery;

        private JDocumentBuilder builder;


        internal DocumentBuilder(Processor processor)
        {
            this.processor = processor;
            this.builder = processor.JProcessor.newDocumentBuilder();
            this.config = processor.Implementation;
            this.xmlResolver = new XmlUrlResolver();
        }

        /// <summary>
		/// An <c>XmlResolver</c>, which will be used to resolve URIs of documents being loaded
        /// and of references to external entities within those documents (including any external DTD).
        /// </summary>
        /// <remarks>
        /// <para>By default an <c>XmlUrlResolver</c> is used. This means that the responsibility
        /// for resolving and dereferencing URIs rests with the .NET platform (and not with the
        /// GNU Classpath).</para>
        /// <para>When Saxon invokes a user-written <c>XmlResolver</c>, the <c>GetEntity</c> method
        /// may return any of: a <c>System.IO.Stream</c>; a <c>System.IO.TextReader</c>; or a
        /// <c>java.xml.transform.Source</c>. However, if the <c>XmlResolver</c> is called
        /// by the XML parser to resolve external entity references, then it must return an 
        /// instance of <c>System.IO.Stream</c>.</para>
        /// </remarks>

        public XmlResolver XmlResolver
        {
            get
            {
                return xmlResolver;
            }
            set
            {
                xmlResolver = value;
            }
        }

        /// <summary>
        /// Determines whether line numbering is enabled for documents loaded using this
        /// <c>DocumentBuilder</c>.
        /// </summary>
        /// <remarks>
        /// <para>By default, line numbering is disabled.</para>
        /// <para>Line numbering is not available for all kinds of source: in particular,
		/// it is not available when loading from an existing <c>XmlDocument</c>.</para>
        /// <para>The resulting line numbers are accessible to applications using the
		/// extension function <c>saxon:line-number()</c> applied to a node.</para>  
        /// <para>Line numbers are maintained only for element nodes; the line number
        /// returned for any other node will be that of the most recent element.</para> 
        /// </remarks>

        public bool IsLineNumbering
        {
            get
            {
                return builder.isLineNumbering();
            }
            set
            {
                builder.setLineNumbering(value);
            }
        }

        /// <summary>
        /// Determines whether schema validation is applied to documents loaded using this
        /// <c>DocumentBuilder</c>, and if so, whether it is strict or lax.
        /// </summary>
        /// <remarks>
        /// <para>By default, no schema validation takes place.</para>
        /// <para>This option requires Saxon Enterprise Edition (Saxon-EE).</para>
        /// </remarks>

        public SchemaValidationMode SchemaValidationMode
        {
            get
            {
                return validation;
            }
            set
            {
                validation = value;
            }
        }


        /// <summary>
        /// Property to set and get the schemaValidator to be used. This determines whether schema validation is applied
        /// to an input document and whether type annotations in a supplied document are retained. If no schemaValidator
        /// is supplied, then schema validation does not take place.
        /// </summary>
        public SchemaValidator SchemaValidator {
            get { return schemaValidator; }
            set {
                schemaValidator = value;
                builder.setSchemaValidator(schemaValidator == null ? null : schemaValidator.UnderlyingSchemaValidator);
            }
        }

        /// <summary>
        /// The required name of the top level element in a document instance being validated
        /// against a schema.
        /// </summary>
        /// <remarks>
        /// <para>If this property is set, and if schema validation is requested, then validation will
        /// fail unless the outermost element of the document has the required name.</para>
        /// <para>This option requires the schema-aware version of the Saxon product (Saxon-EE).</para>
        /// </remarks> 

        public QName TopLevelElementName
        {
            get
            {
                return topLevelElement;
            }
            set
            {
                topLevelElement = value;
                schemaValidator.UnderlyingSchemaValidator.setDocumentElementName(topLevelElement == null ? null : topLevelElement.UnderlyingQName());
            }
        }

        /// <summary>
        /// Determines whether DTD validation is applied to documents loaded using this
        /// <c>DocumentBuilder</c>.
        /// </summary>
        /// <remarks>
        /// <para>By default, no DTD validation takes place.</para>
        /// </remarks>

        public bool DtdValidation
        {
            get
            {
                return builder.isDTDValidation();
            }
            set
            {
                builder.setDTDValidation(value);
            }
        }

        /// <summary>
        /// Determines the whitespace stripping policy applied when loading a document
        /// using this <c>DocumentBuilder</c>.
        /// </summary>
        /// <remarks>
        /// <para>By default, whitespace text nodes appearing in element-only content
        /// are stripped, and all other whitespace text nodes are retained.</para>
        /// </remarks>

        public WhitespacePolicy WhitespacePolicy
        {
            get
            {
                return whitespacePolicy;
            }
            set
            {
                whitespacePolicy = value;
                builder.setWhitespaceStrippingPolicy(whitespacePolicy == null ? null : whitespacePolicy.GetJWhiteSpaceStrippingPolicy());
            }
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
                return (TreeModel)builder.getTreeModel().getSymbolicValue();
            }
            set
            {
                builder.setTreeModel(net.sf.saxon.om.TreeModel.getTreeModel((int)value));
            }
        }

        /// <summary>
        /// The base URI of a document loaded using this <c>DocumentBuilder</c>.
        /// This is used for resolving any relative URIs appearing
        /// within the document, for example in references to DTDs and external entities.
        /// </summary>
        /// <remarks>
        /// This information is required when the document is loaded from a source that does not
		/// provide an intrinsic URI, notably when loading from a <c>Stream</c> or a <c>TextReader</c>.
        /// </remarks>


        public Uri BaseUri
        {
            get { return baseUri; }
            set { baseUri = value;
                if (baseUri != null)
                {
                    builder.setBaseURI(new java.net.URI(value.AbsoluteUri));
                }
            }
        }


        /// <summary>
        /// Set a compiled query to be used for implementing document projection. 
        /// </summary>
        /// <remarks>
        /// <para>
        /// The effect of using this option is that the tree constructed by the 
        /// <c>DocumentBuilder</c> contains only those parts
        /// of the source document that are needed to answer this query. Running this query against
        /// the projected document should give the same results as against the raw document, but the
        /// projected document typically occupies significantly less memory. It is permissible to run
        /// other queries against the projected document, but unless they are carefully chosen, they
        /// will give the wrong answer, because the document being used is different from the original.
        /// </para>
        /// <para>The query should be written to use the projected document as its initial context item.
        /// For example, if the query is <code>//ITEM[COLOR='blue']</code>, then only <code>ITEM</code>
        /// elements and their <code>COLOR</code> children will be retained in the projected document.</para>
        /// <para>This facility is only available in Saxon-EE; if the facility is not available,
        /// calling this method has no effect.</para>
        /// </remarks>


        public XQueryExecutable DocumentProjectionQuery {
            get { return projectionQuery; }
            set {
                projectionQuery = value;
                builder.setDocumentProjectionQuery(projectionQuery == null ? null : projectionQuery.getUnderlyingCompiledQuery());
            }

        }

        /// <summary>
        /// Load an XML document, retrieving it via a URI.
        /// </summary>
        /// <remarks>
        /// <para>Note that the type <c>Uri</c> requires an absolute URI.</para>
        /// <para>The URI is dereferenced using the registered <c>XmlResolver</c>.</para>
        /// <para>This method takes no account of any fragment part in the URI.</para>
        /// <para>The <c>role</c> passed to the <c>GetEntity</c> method of the <c>XmlResolver</c> 
        /// is "application/xml", and the required return type is <c>System.IO.Stream</c>.</para>
        /// <para>The document located via the URI is parsed using the <c>System.Xml</c> parser.</para>
        /// <para>Note that the Microsoft <c>System.Xml</c> parser does not report whether attributes are
        /// defined in the DTD as being of type <c>ID</c> and <c>IDREF</c>. This is true whether or not
        /// DTD-based validation is enabled. This means that such attributes are not accessible to the 
        /// <c>id()</c> and <c>idref()</c> functions.</para>
        /// </remarks>
        /// <param name="uri">The URI identifying the location where the document can be
        /// found. This will also be used as the base URI of the document (regardless
		/// of the setting of the <c>BaseUri</c> property).</param>
		/// <returns>An <c>XdmNode</c>, the document node at the root of the tree of the resulting
		/// in-memory document.
		/// </returns>

        public XdmNode Build(Uri uri)
        {
            Object obj = XmlResolver.GetEntity(uri, "application/xml", System.Type.GetType("System.IO.Stream"));
            if (obj is Stream)
            {
                try
                {
                    return Build((Stream)obj, uri);
                }
                finally
                {
                    ((Stream)obj).Close();
                }
            }
            else
            {
                throw new ArgumentException("Invalid type of result from XmlResolver.GetEntity: " + obj);
            }
        }

        /// <summary>
		/// Load an XML document supplied as raw (lexical) XML on a <c>Stream</c>.
        /// </summary>
        /// <remarks>
        /// <para>The document is parsed using the Microsoft <c>System.Xml</c> parser if the
        /// "http://saxon.sf.net/feature/preferJaxpParser" property on the <c>Processor</c> is set to false;
        /// otherwise it is parsed using the Apache Xerces XML parser.</para>
        /// <para>Before calling this method, the <c>BaseUri</c> property must be set to identify the
        /// base URI of this document, used for resolving any relative URIs contained within it.</para>
        /// <para>Note that the Microsoft <c>System.Xml</c> parser does not report whether attributes are
        /// defined in the DTD as being of type <c>ID</c> and <c>IDREF</c>. This is true whether or not
        /// DTD-based validation is enabled. This means that such attributes are not accessible to the 
        /// <c>id()</c> and <c>idref()</c> functions.</para>         
        /// </remarks>
        /// <param name="input">The <c>Stream</c> containing the XML source to be parsed. Closing this stream
        /// on completion is the responsibility of the caller.</param>
        /// <returns>An <c>XdmNode</c>, the document node at the root of the tree of the resulting
        /// in-memory document.
        /// </returns>

        public XdmNode Build(Stream input)
        {
            if (baseUri == null)
            {
                throw new ArgumentException("No base URI supplied");
            }
            return Build(input, baseUri);
        }

        // Build a document from a given stream, with the base URI supplied
        // as an extra argument

        internal XdmNode Build(Stream input, Uri baseUri)
        {
            JSource source;
            JParseOptions options = new JParseOptions(config.getParseOptions());

            if (processor.GetProperty("http://saxon.sf.net/feature/preferJaxpParser") == "true")
            {
                source = new JStreamSource(new JDotNetInputStream(input), baseUri.ToString());
                options.setEntityResolver(new JDotNetURIResolver(XmlResolver));
                source = augmentSource(source, options);
            }
            else
            {

                XmlReaderSettings settings = new XmlReaderSettings();
                settings.DtdProcessing = DtdProcessing.Parse;   // must expand entity references


                //((XmlTextReader)parser).Normalization = true;
                /*if (whitespacePolicy != null) {
                    int optioni = whitespacePolicy.ordinal();
                    if (optioni == JWhitespace.XSLT)
                    {
                        options.setSpaceStrippingRule(WhitespacePolicy.PreserveAll.GetJWhiteSpaceStrippingPolicy());
                        options.addFilter(whitespacePolicy.makeStripper());
                    }
                    else {
                        options.setSpaceStrippingRule(whitespacePolicy.GetJWhiteSpaceStrippingPolicy());
                    }
                 

                }*/

                if (xmlResolver != null)
                {
                    settings.XmlResolver = xmlResolver;
                }

                settings.ValidationType = (DtdValidation ? ValidationType.DTD : ValidationType.None);

                XmlReader parser = XmlReader.Create(input, settings, baseUri.ToString());
                source = new JPullSource(new JDotNetPullProvider(parser));
                source.setSystemId(baseUri.ToString());
            }

            try
            {
                XdmNode node = (XdmNode)XdmNode.Wrap(builder.build(source).getUnderlyingNode());
                node.SetProcessor(processor);
                return node;
            } catch (JXPathException ex)
            { throw new StaticError(ex); }


        }

        /// <summary>
		/// Load an XML document supplied using a <c>TextReader</c>.
        /// </summary>
        /// <remarks>
        /// <para>The document is parsed using the Microsoft <c>System.Xml</c> parser if the
        /// "http://saxon.sf.net/feature/preferJaxpParser" property on the <c>Processor</c> is set to false;
        /// otherwise it is parsed using the Apache Xerces XML parser.</para>
        /// <para>Before calling this method, the <c>BaseUri</c> property must be set to identify the
        /// base URI of this document, used for resolving any relative URIs contained within it.</para>
        /// <para>Note that the Microsoft <c>System.Xml</c> parser does not report whether attributes are
        /// defined in the DTD as being of type <c>ID</c> and <c>IDREF</c>. This is true whether or not
        /// DTD-based validation is enabled. This means that such attributes are not accessible to the 
        /// <c>id()</c> and <c>idref()</c> functions.</para>         
        /// </remarks>
        /// <param name="input">The <c>TextReader</c> containing the XML source to be parsed</param>
        /// <returns>An <c>XdmNode</c>, the document node at the root of the tree of the resulting
        /// in-memory document.
        /// </returns>

        public XdmNode Build(TextReader input)
        {
            if (baseUri == null)
            {
                throw new ArgumentException("No base URI supplied");
            }
            return Build(input, baseUri);
        }

        // Build a document from a given stream, with the base URI supplied
        // as an extra argument

        internal XdmNode Build(TextReader input, Uri baseUri)
        {
            JSource source;
            JParseOptions options = new JParseOptions(config.getParseOptions());
            if (processor.GetProperty("http://saxon.sf.net/feature/preferJaxpParser") == "true")
            {
                source = new JStreamSource(new JDotNetReader(input), baseUri.ToString());
                options.setEntityResolver(new JDotNetURIResolver(XmlResolver));
                source = augmentSource(source, options);
            }
            else
            {

                XmlReaderSettings settings = new XmlReaderSettings();
                settings.DtdProcessing = DtdProcessing.Parse;   // must expand entity references


                //((XmlTextReader)parser).Normalization = true;

                if (xmlResolver != null)
                {
                    settings.XmlResolver = xmlResolver;
                }

                settings.ValidationType = (DtdValidation ? ValidationType.DTD : ValidationType.None);

                XmlReader parser = XmlReader.Create(input, settings, baseUri.ToString());
                source = new JPullSource(new JDotNetPullProvider(parser));
                source.setSystemId(baseUri.ToString());
            }
            //augmentParseOptions(options);
            JNodeInfo doc = null;
            try
            {
                doc = builder.build(source).getUnderlyingNode(); //config.buildDocumentTree(source, options).getRootNode();
            } catch (net.sf.saxon.s9api.SaxonApiException ex) {
                throw new Exception(ex.getMessage());
            }
            return (XdmNode)XdmValue.Wrap(doc);
        }

        private JSource augmentSource(JSource source, JParseOptions options)
        {

            return new AugmentedSource(source, options);
        }


        /*private void augmentParseOptions(JParseOptions options)
		{
			if (validation != SchemaValidationMode.None)
			{
				
				if (validation == SchemaValidationMode.Strict)
				{
					options.setSchemaValidationMode(JValidation.STRICT);
				}
				else if (validation == SchemaValidationMode.Lax)
				{
					options.setSchemaValidationMode(JValidation.LAX);
				}
				else if (validation == SchemaValidationMode.None)
				{
					options.setSchemaValidationMode(JValidation.STRIP);
				}
				else if (validation == SchemaValidationMode.Preserve)
				{
					options.setSchemaValidationMode(JValidation.PRESERVE);
				}
			}
			if (topLevelElement != null)
			{
				
				options.setTopLevelElement(
					new FingerprintedQName(
						topLevelElement.Prefix, topLevelElement.Uri.ToString(), topLevelElement.LocalName).getStructuredQName());
			}

			if (whitespacePolicy != null)
			{
                int option = whitespacePolicy.ordinal();
				if (option == JWhitespace.XSLT)
				{
                    options.setSpaceStrippingRule(WhitespacePolicy.PreserveAll.GetJWhiteSpaceStrippingPolicy());
                    options.addFilter(whitespacePolicy.makeStripper());
				}
				else
				{
                    options.setSpaceStrippingRule(whitespacePolicy.GetJWhiteSpaceStrippingPolicy());
                }
			}
			if (treeModel != TreeModel.Unspecified)
			{
				
				if (treeModel == TreeModel.TinyTree)
				{
					options.setModel(net.sf.saxon.om.TreeModel.TINY_TREE);
				}
				else if (treeModel == TreeModel.TinyTreeCondensed)
				{
					options.setModel(net.sf.saxon.om.TreeModel.TINY_TREE_CONDENSED);
				}
				else
				{
					options.setModel(net.sf.saxon.om.TreeModel.LINKED_TREE);
				}
			}
			if (lineNumbering)
			{

				options.setLineNumbering(true);
			}
			if (dtdValidation)
			{
				
				options.setDTDValidationMode(JValidation.STRICT);
			}
			if (projectionQuery != null) {
				net.sf.saxon.s9api.XQueryExecutable exp = projectionQuery.getUnderlyingCompiledQuery();
				net.sf.saxon.@event.FilterFactory ff = config.makeDocumentProjector(exp.getUnderlyingCompiledQuery());
				if (ff != null) {
					
					options.addFilter (ff);
				}
			}

		} */

        /// <summary>
        /// Load an XML document, delivered using an <c>XmlReader</c>.
        /// </summary>
        /// <remarks>
        /// <para>The <c>XmlReader</c> is responsible for parsing the document; this method builds a tree
        /// representation of the document (in an internal Saxon format) and returns its document node.
        /// The <c>XmlReader</c> is not required to perform validation but it must expand any entity references.
        /// Saxon uses the properties of the <c>XmlReader</c> as supplied.</para>
        /// <para>Use of a plain <c>XmlTextReader</c> is discouraged, because it does not expand entity
        /// references. This should only be used if you know in advance that the document will contain
        /// no entity references (or perhaps if your query or stylesheet is not interested in the content
        /// of text and attribute nodes). Instead, with .NET 1.1 use an <c>XmlValidatingReader</c> (with <c>ValidationType</c>
        /// set to <c>None</c>). The constructor for <c>XmlValidatingReader</c> is obsolete in .NET 2.0,
        /// but the same effect can be achieved by using the <c>Create</c> method of <c>XmlReader</c> with
        /// appropriate <c>XmlReaderSettings</c>.</para>
        /// <para>Conformance with the W3C specifications requires that the <c>Normalization</c> property
        /// of an <c>XmlTextReader</c> should be set to <c>true</c>. However, Saxon does not insist
        /// on this.</para>
        /// <para>If the <c>XmlReader</c> performs schema validation, Saxon will ignore any resulting type
        /// information. Type information can only be obtained by using Saxon's own schema validator, which
        /// will be run if the <c>SchemaValidationMode</c> property is set to <c>Strict</c> or <c>Lax</c>.</para>
        /// <para>Note that the Microsoft <c>System.Xml</c> parser does not report whether attributes are
        /// defined in the DTD as being of type <c>ID</c> and <c>IDREF</c>. This is true whether or not
        /// DTD-based validation is enabled. This means that such attributes are not accessible to the 
        /// <c>id()</c> and <c>idref()</c> functions.</para>
        /// <para>Note that setting the <c>XmlResolver</c> property of the <c>DocumentBuilder</c>
        /// has no effect when this method is used; if an <c>XmlResolver</c> is required, it must
        /// be set on the <c>XmlReader</c> itself.</para>
        /// </remarks>
        /// <param name="reader">The <c>XMLReader</c> that supplies the parsed XML source</param>
        /// <returns>An <c>XdmNode</c>, the document node at the root of the tree of the resulting
        /// in-memory document.
        /// </returns>

        public XdmNode Build(XmlReader reader)
        {
            JPullProvider pp = new JDotNetPullProvider(reader);
            pp.setPipelineConfiguration(config.makePipelineConfiguration());
            // pp = new PullTracer(pp);  /* diagnostics */
            JSource source = new JPullSource(pp);
            source.setSystemId(reader.BaseURI);
            JParseOptions options = new JParseOptions(config.getParseOptions());

            source = augmentSource(source, options);
            JNodeInfo doc = builder.build(source).getUnderlyingNode(); //config.buildDocumentTree(source, options).getRootNode();
            return (XdmNode)XdmValue.Wrap(doc);
        }

        /// <summary>
		/// Load an XML DOM document, supplied as an <c>XmlNode</c>, into a Saxon <c>XdmNode</c>.
        /// </summary>
        /// <remarks>
        /// <para>
        /// The returned document will contain only the subtree rooted at the supplied node.
        /// </para>
        /// <para>
        /// This method copies the DOM tree to create a Saxon tree. See the <c>Wrap</c> method for
        /// an alternative that creates a wrapper around the DOM tree, allowing it to be modified in situ.
        /// </para>
        /// </remarks>
        /// <param name="source">The DOM Node to be copied to form a Saxon tree</param>
        /// <returns>An <c>XdmNode</c>, the document node at the root of the tree of the resulting
        /// in-memory document.
        /// </returns>

        public XdmNode Build(XmlNode source)
        {
            return Build(new XmlNodeReader(source));
        }


        /// <summary>
        /// Wrap an XML DOM document, supplied as an <c>XmlNode</c>, as a Saxon <c>XdmNode</c>.
        /// </summary>
        /// <remarks>
        /// <para>
        /// This method must be applied at the level of the Document Node. Unlike the
        /// <c>Build</c> method, the original DOM is not copied. This saves memory and
        /// time, but it also means that it is not possible to perform operations such as
        /// whitespace stripping and schema validation.
        /// </para>
        /// </remarks>
        /// <param name="doc">The DOM document node to be wrapped</param>
        /// <returns>An <c>XdmNode</c>, the Saxon document node at the root of the tree of the resulting
        /// in-memory document.
        /// </returns>

        public XdmNode Wrap(XmlDocument doc)
        {
            String baseu = (baseUri == null ? null : baseUri.ToString());
            JDotNetDocumentWrapper wrapper = new JDotNetDocumentWrapper(doc, baseu, config);
            return (XdmNode)XdmValue.Wrap(wrapper.getRootNode());
        }
    }

    /// <summary>The default Logger used by Saxon on the .NET platform. All messages are written by
    /// default to System.err. The logger can be configured by setting a different output
    /// destination, and by setting a minimum threshold for the severity of messages to be output.</summary>

    public class StandardLogger : JLogger {

        private TextWriter outi = Console.Error;
        JDotNetWriter writer;
        int threshold = JLogger.INFO;
        java.io.PrintStream stream;

        /// <summary>
        /// Default constructor that wraps a <c>TextWriter</c> to write Saxon messages
        /// </summary>
		public StandardLogger() {
            writer = new JDotNetWriter(outi);
        }

        /// <summary>
        /// Constructor method to supply a user defined TextWriter to the logger
        /// </summary>
        /// <param name="w"></param>
		public StandardLogger(TextWriter w) {
            writer = new JDotNetWriter(w);

        }

        /// <summary>
        /// Property to get the udnerlying TextWriter object.
        /// </summary>
		public JDotNetWriter UnderlyingTextWriter {
            set {
                writer = value;
            }
            get {
                return writer;
            }
        }

        /// <summary> Set the minimum threshold for the severity of messages to be output. Defaults to
        /// <see cref="net.sf.saxon.lib.Logger#INFO"/>. Messages whose severity is below this threshold will be ignored. </summary>
        /// <param name="threshold">the minimum severity of messages to be output. </param>

        public int Threshold {

            set {
                threshold = value;
            }

            get {
                return threshold;
            }

        }

        /// <summary>
        /// Java internal streamResult object wrapping the TextWriter
        /// </summary>
        /// <returns></returns>
        public override JStreamResult asStreamResult()
        {
            return new JStreamResult(writer);
        }

        /// <summary>
        /// Write the message to the TextWriter object
        /// </summary>
        /// <param name="str">The message</param>
        /// <param name="severity">the severity of the error message</param>
        public override void println(string str, int severity)
        {
            if (severity >= threshold) {
                writer.write(str);
            }
        }
    }


    /// <summary>
    /// Enumeration identifying the various Schema validation modes
    /// </summary>

    public enum SchemaValidationMode
    {
        /// <summary>No validation (or strip validation, which removes existing type annotations)</summary> 
        None,
        /// <summary>Strict validation</summary>
        Strict,
        /// <summary>Lax validation</summary>
        Lax,
        /// <summary>Validation mode preserve, which preserves any existing type annotations</summary>
        Preserve,
        /// <summary>Unspecified validation: this means that validation is defined elsewhere, for example in the
		/// Saxon <c>Configuration</c></summary>
        Unspecified
    }


    /// <summary>
    /// Identifiies a host language in which XPath expressions appear. Generally used when different error codes
    /// need to be returned depending on the host language.
    /// </summary>
    public enum HostLanguage
    {
        XSLT,
        XQUERY,
        XML_SCHEMA,
        XPATH,
        XSLT_PATTERN
    }


    internal class JPredicateImpl : java.util.function.Predicate
    {

        System.Predicate<QName> predicate;

        public JPredicateImpl(System.Predicate<QName> p) {
            predicate = p;

        }

        public Predicate and(Predicate value1, Predicate value2)
        {
            throw new NotImplementedException();
        }

        public Predicate negate(Predicate value)
        {
            throw new NotImplementedException();
        }

        public Predicate or(Predicate value1, Predicate value2)
        {
            throw new NotImplementedException();
        }

        public Predicate and(Predicate other)
        {
            throw new NotImplementedException();
        }

        public Predicate isEqual(object targetRef)
        {
            throw new NotImplementedException();
        }

        public Predicate negate()
        {
            throw new NotImplementedException();
        }

        public Predicate or(Predicate other)
        {
            throw new NotImplementedException();
        }

        public bool test(object t)
        {
            return predicate.Invoke(new QName((net.sf.saxon.s9api.QName)t));
        }
    }



    /// <summary>
    /// <c>WhitespacePolicy</c> is a class defining the possible policies for handling
    /// whitespace text nodes in a source document.
    /// </summary>
    /// <remarks>
    /// Please note that since Saxon 9.7.0.8 this class has been refactored from the enumeration
    /// type with the same name and therefore will work as before.
    /// </remarks>

    [Serializable]
    public class WhitespacePolicy
    {
       private int policy;
       JWhiteSpaceStrippingPolicy strippingPolicy = null;

        /// <summary>All whitespace text nodes are stripped</summary>
        public static WhitespacePolicy StripAll = new WhitespacePolicy(JWhitespace.ALL, JWhiteSpaceStrippingPolicy.ALL);

        /// <summary>Whitespace text nodes appearing in element-only content are stripped</summary>
        public static WhitespacePolicy StripIgnorable = new WhitespacePolicy(JWhitespace.IGNORABLE, JWhiteSpaceStrippingPolicy.IGNORABLE);

        /// <summary>No whitespace is stripped</summary>
        public static WhitespacePolicy PreserveAll = new WhitespacePolicy(JWhitespace.NONE, JWhiteSpaceStrippingPolicy.NONE);

        /// <summary>Unspecified means that no other value has been specifically requested</summary>
        public static WhitespacePolicy Unspecified = new WhitespacePolicy(JWhitespace.UNSPECIFIED,  null);

        private WhitespacePolicy(int policy, JWhiteSpaceStrippingPolicy strippingPolicy)
        {
            this.policy = policy;
            this.strippingPolicy = strippingPolicy;
        }


        internal JWhiteSpaceStrippingPolicy GetJWhiteSpaceStrippingPolicy() {
            if (strippingPolicy != null) {
                return strippingPolicy;
            }
            return null;
        }


        internal WhitespacePolicy(JXsltExecutable executable)
        {
            policy = Whitespace.XSLT;
            strippingPolicy = executable.getWhitespaceStrippingPolicy();
        }


        /// <summary>
        /// Create a custom whitespace stripping policy
        /// </summary>
        /// <param name="elementTest">a predicate applied to element names, which should return true if whitespace-only
        /// text node children of the element are to be stripped, false if they are to be retained.</param>
		/// <returns>A <c>WhitespacePolicy</c> object</returns>
        [Obsolete("This method has been replaced by MakeCustomPolicy.")]
        public static WhitespacePolicy makeCustomPolicy(System.Predicate<QName> elementTest) {
            JWhiteSpaceStrippingPolicy policy = JWhiteSpaceStrippingPolicy.makeCustomPolicy(new JPredicateImpl(elementTest));
            WhitespacePolicy wsp = new WhitespacePolicy(JWhitespace.XSLT, null);
            wsp.strippingPolicy = policy;
            return wsp;
        }

        /// <summary>
        /// Create a custom whitespace stripping policy
        /// </summary>
        /// <param name="elementTest">a predicate applied to element names, which should return true if whitespace-only
        /// text node children of the element are to be stripped, false if they are to be retained.</param>
        /// <returns>A <c>WhitespacePolicy</c> object</returns>
        public static WhitespacePolicy MakeCustomPolicy(System.Predicate<QName> elementTest)
        {
            JWhiteSpaceStrippingPolicy policy = JWhiteSpaceStrippingPolicy.makeCustomPolicy(new JPredicateImpl(elementTest));
            WhitespacePolicy wsp = new WhitespacePolicy(JWhitespace.XSLT, null);
            wsp.strippingPolicy = policy;
            return wsp;
        }

        internal int ordinal() {
            return policy;
        }

        /*internal net.sf.saxon.@event.FilterFactory makeStripper() {
            return new FilterFactory(strippingPolicy);
        }      */ 

    }



    internal class SpaceStrippingRule : net.sf.saxon.om.SpaceStrippingRule
    {
        private System.Predicate<QName> elementTest;

        public SpaceStrippingRule(System.Predicate<QName> elementTest)
        {
            this.elementTest = elementTest;
        }

        public void export(JExpressionPresenter ep)
        {
            throw new NotImplementedException(); 
        }


        public int isSpacePreserving(NodeName nn, JSchemaType st)
        {
            return elementTest(new QName(nn.getStructuredQName().ToString())) ?
                JStripper.ALWAYS_STRIP :
                JStripper.ALWAYS_PRESERVE;
        }

        public JProxyReceiver makeStripper(JReceiver r)
        {
            return new net.sf.saxon.@event.Stripper(this, r);
        }
    }

    internal class FilterFactory : JFilterFactory
    {
        JSpaceStrippingRule stripperRules;
        public FilterFactory(JSpaceStrippingRule sr)
        {
            stripperRules = sr;
        }
        public JProxyReceiver makeFilter(JReceiver r)
        {
            return new net.sf.saxon.@event.Stripper(stripperRules, r);
        }

        JReceiver JFilterFactory.makeFilter(JReceiver r)
        {
            return (JReceiver)new JStripper(stripperRules, r);
        }
    }

    /// <summary>
    /// Enumeration identifying the different tree model implementations
    /// </summary>
    /// 
    public enum TreeModel
    {
        /// <summary>
        /// Saxon <c>LinkedTree</c>. This tree model is primarily useful when using XQuery Update, since it is the
        /// only standard tree model to support updates.
        /// </summary>
        LinkedTree,
        /// <summary>
		/// Saxon <c>TinyTree</c>. This is the default model and is suitable for most purposes.
        /// </summary>
        TinyTree,
        /// <summary>
		/// Saxon Condensed <c>TinyTree</c>. This is a variant of the <c>TinyTree</c> that shares storage for 
        /// duplicated text and attribute nodes. It gives a further saving in space occupied, at the cost
        /// of some increase in the time taken for tree construction.
        /// </summary>
        TinyTreeCondensed,
        /// <summary>
        /// Unspecified tree model. This value is used to indicate that there is no preference for any specific
        /// tree model, which allows the choice to fall back to other interfaces.
        /// </summary>
        Unspecified = -1
    }

    internal class DotNetObjectModelDefinition : JDotNetObjectModel
    {

        public override bool isXdmValue(object obj)
        {
            return obj is XdmValue;
        }

        public override bool isXdmAtomicValueType(System.Type type)
        {
            return typeof(XdmAtomicValue).IsAssignableFrom(type);
        }

        public override bool isXdmValueType(System.Type type)
        {
            return typeof(XdmValue).IsAssignableFrom(type);
        }

        public override JSequence unwrapXdmValue(object obj)
        {
            return ((XdmValue)obj).Unwrap();
        }

        public override object wrapAsXdmValue(JSequence value)
        {
            return XdmValue.Wrap(value);
        }

        public override bool isXmlNodeType(System.Type type)
        {
            return typeof(System.Xml.XmlNode).IsAssignableFrom(type);
        }

    }

}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////