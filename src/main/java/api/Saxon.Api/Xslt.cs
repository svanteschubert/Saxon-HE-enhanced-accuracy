using System;
using System.IO;
using System.Xml;
using System.Collections;
using System.Collections.Generic;
using System.Globalization;
using JStreamSource = javax.xml.transform.stream.StreamSource;
using JResult = javax.xml.transform.Result;
using JTransformerException = javax.xml.transform.TransformerException;
using JOutputURIResolver = net.sf.saxon.lib.OutputURIResolver;
using JConfiguration = net.sf.saxon.Configuration;
using JPipelineConfiguration = net.sf.saxon.@event.PipelineConfiguration;
using JSequenceWriter = net.sf.saxon.@event.SequenceWriter;
using JReceiver = net.sf.saxon.@event.Receiver;
//using JReceiverOptions = net.sf.saxon.@event.ReceiverOptions;
using JCompilerInfo = net.sf.saxon.trans.CompilerInfo;
using JExpressionPresenter = net.sf.saxon.trace.ExpressionPresenter;
using JValidation = net.sf.saxon.lib.Validation;
using JValidationMode = net.sf.saxon.s9api.ValidationMode;
using JCompilation = net.sf.saxon.style.Compilation;
using JController = net.sf.saxon.Controller;
using JXsltController = net.sf.saxon.trans.XsltController;
using JItem = net.sf.saxon.om.Item;
using JNodeInfo = net.sf.saxon.om.NodeInfo;
using JNodeName = net.sf.saxon.om.NodeName;
using JSchemaType = net.sf.saxon.type.SchemaType;
using JTreeInfo = net.sf.saxon.om.TreeInfo;
using JPullSource = net.sf.saxon.pull.PullSource;
using JProcInstParser = net.sf.saxon.tree.util.ProcInstParser;
using JXsltCompiler = net.sf.saxon.s9api.XsltCompiler;
using JXsltExecutable = net.sf.saxon.s9api.XsltExecutable;
using JSaxonApiException = net.sf.saxon.s9api.SaxonApiException;
using JDotNetComparator = net.sf.saxon.dotnet.DotNetComparator;
using JDotNetURIResolver = net.sf.saxon.dotnet.DotNetURIResolver;
using JDotNetInputStream = net.sf.saxon.dotnet.DotNetInputStream;
using JDotNetReader = net.sf.saxon.dotnet.DotNetReader;
using JDotNetPullProvider = net.sf.saxon.dotnet.DotNetPullProvider;
using CharSequence = java.lang.CharSequence;
using JXsltTransformer = net.sf.saxon.s9api.XsltTransformer;
using JXslt30Transformer = net.sf.saxon.s9api.Xslt30Transformer;
using JStylesheetPackage = net.sf.saxon.style.StylesheetPackage;
using JXsltPackage = net.sf.saxon.s9api.XsltPackage;
using JPackageDetails = net.sf.saxon.trans.packages.PackageDetails;
using JPackageLibrary = net.sf.saxon.trans.packages.PackageLibrary;
using JVersionedPackageName = net.sf.saxon.trans.packages.VersionedPackageName;
using JMap = java.util.Map;
using JDotNetOutputStream = net.sf.saxon.dotnet.DotNetOutputStream;
using JDestination = net.sf.saxon.s9api.Destination;
using javax.xml.transform;
using JResultDocumentResolver = net.sf.saxon.lib.ResultDocumentResolver;
using System.Runtime.CompilerServices;

namespace Saxon.Api
{

    /// <summary>
    /// An <c>XsltCompiler</c> object allows XSLT 3.0 stylesheets to be compiled.
    /// The compiler holds information that represents the static context
    /// for the compilation.
    /// </summary>
    /// <remarks>
    /// <para>To construct an <c>XsltCompiler</c>, use the factory method
	/// <c>NewXsltCompiler</c> on the <see cref="Processor"/> object.</para>
    /// <para>An <c>XsltCompiler</c> may be used repeatedly to compile multiple
    /// queries. Any changes made to the <c>XsltCompiler</c> (that is, to the
    /// static context) do not affect queries that have already been compiled.
    /// An <c>XsltCompiler</c> may be used concurrently in multiple threads, but
    /// it should not then be modified once initialized.</para>
    /// </remarks>

    [Serializable]
    public class XsltCompiler
    {
        private Processor processor;
        private Uri baseUri;
        private ErrorReporterToStaticError errorGatherer;
        private ErrorReporter errorReporter;
        private IDictionary<QName, XdmValue> variableList = new Dictionary<QName, XdmValue>();
        private JXsltCompiler xsltCompiler;
        private XmlResolver xmlResolver = null;

        // internal constructor: the public interface is a factory method
        // on the Processor object

        internal XsltCompiler(Processor processor)
        {
            this.processor = processor;
            xsltCompiler = processor.JProcessor.newXsltCompiler();
            errorGatherer = new ErrorReporterToStaticError(new List<StaticError>());
            errorReporter = new ErrorReporter(new List<XmlProcessingError>());
            //xsltCompiler.setErrorReporter(errorReporter);
            xsltCompiler.setURIResolver(processor.Implementation.getURIResolver());
        }

        /// <summary>
        /// The base URI of the stylesheet, which forms part of the static context
        /// of the stylesheet. This is used for resolving any relative URIs appearing
        /// within the stylesheet, for example in <c>xsl:include</c> and <c>xsl:import</c>
        /// declarations, in schema locations defined to <c>xsl:import-schema</c>, 
        /// or as an argument to the <c>document()</c> or <c>doc()</c> function.
        /// </summary>
        /// <remarks>
        /// This base URI is used only if the input supplied to the <c>Compile</c> method
        /// does not provide its own base URI. It is therefore used on the version of the
        /// method that supplies input from a <c>Stream</c>. On the version that supplies
        /// input from an <c>XmlReader</c>, this base URI is used only if the <c>XmlReader</c>
        /// does not have its own base URI.
        /// </remarks>


        public Uri BaseUri
        {
            get { return baseUri; }
            set { baseUri = value; }
        }

        /// <summary>
        /// Create a collation based on a given <c>CompareInfo</c> and <c>CompareOptions</c>.    
        /// </summary>
        /// <remarks>
        /// In the current and recent releases of Saxon, collations are always defined at the level of a <c>Configuration</c>.
        /// Declaring a collation here may therefore have wider effects than intended. It is recommended not to use
        /// this method, but to use <see cref="Processor.DeclareCollation(Uri, CompareInfo, CompareOptions)"/> instead.
        /// </remarks>
        /// <param name="uri">The collation URI to be used within the XPath expression to refer to this collation</param>
        /// <param name="compareInfo">The <c>CompareInfo</c>, which determines the language-specific
        /// collation rules to be used</param>
        /// <param name="options">Options to be used in performing comparisons, for example
        /// whether they are to be case-blind and/or accent-blind</param>
        /// <param name="isDefault">If true, this collation will be used as the default collation</param>

        [Obsolete("Declare collations globally at the Processor level.")]
        public void DeclareCollation(Uri uri, CompareInfo compareInfo, CompareOptions options, Boolean isDefault)
        {
            JDotNetComparator comparator = new JDotNetComparator(uri.ToString(), compareInfo, options);
            processor.JProcessor.getUnderlyingConfiguration().registerCollation(uri.ToString(), comparator);
        }

        /// <summary>
        /// The name of the default collation used by stylesheets compiled using this <c>XsltCompiler</c>.
        /// This must be the name of a collation that is known to the <c>Processor</c>.
        /// </summary>

        public String DefaultCollationName
        {
            get { return xsltCompiler.getDefaultCollation(); }
            set { xsltCompiler.declareDefaultCollation(value); }
        }

        /// <summary>
        /// The <c>Processor</c> from which this <c>XsltCompiler</c> was constructed
        /// </summary>

        public Processor Processor
        {
            get { return processor; }
        }

        /// <summary>
        /// An <c>XmlResolver</c>, which will be used to resolve URI references while compiling
        /// a stylesheet.
        /// </summary>
        /// <remarks>
        /// If no <c>XmlResolver</c> is set for the <c>XsltCompiler</c>, the <c>XmlResolver</c>
        /// that is used is the one that was set on the <c>Processor</c> at the time <c>NewXsltCompiler</c>
        /// was called.
        /// </remarks>

        public XmlResolver XmlResolver
        {
            get
            {
                if (xmlResolver == null)
                {
                    javax.xml.transform.URIResolver resolver = xsltCompiler.getUnderlyingCompilerInfo().getURIResolver();
                    if (resolver is JDotNetURIResolver)
                    {
                        return ((JDotNetURIResolver)resolver).getXmlResolver();
                    }
                    else
                    {
                        xmlResolver = new XmlUrlResolver();
                        return xmlResolver;
                    }
                }
                else
                {
                    return xmlResolver;
                }

            }
            set
            {
                xmlResolver = value;
                xsltCompiler.setURIResolver(new JDotNetURIResolver(xmlResolver));
            }
        }

        /// <summary>
        /// The <c>SchemaAware</c> property determines whether the stylesheet is schema-aware. By default, a stylesheet
        /// is schema-aware if it contains one or more <code>xsl:import-schema</code> declarations. This option allows
        /// a stylesheet to be marked as schema-aware even if it does not contain such a declaration.
        /// </summary>
        /// <remarks>
        /// <para>If the stylesheet is not schema-aware, then schema-validated input documents will be rejected.</para>
        /// <para>The reason for this option is that it is expensive to generate code that can handle typed input
        /// documents when they will never arise in practice.</para>
        /// <para>The initial setting of this property is false, regardless of whether or not the <c>Processor</c>
        /// is schema-aware. Setting this property to true if the processor is not schema-aware will cause an Exception.</para>
        /// </remarks>

        public bool SchemaAware
        {
            get
            {
                return xsltCompiler.isSchemaAware();
            }
            set
            {
                xsltCompiler.setSchemaAware(value);
            }
        }


        /// <summary>
        /// Indicates whether or not assertions (<c>xsl:assert</c> instructions) are enabled at compile time. 
        /// </summary>
        /// <remarks>By default assertions are disabled at compile time. If assertions are enabled at compile time, then by
        /// default they will also be enabled at run time; but they can be disabled at run time by
        /// specific request. At compile time, assertions can be enabled for some packages and
        /// disabled for others; at run time, they can only be enabled or disabled globally.</remarks>
		/// <returns>true if assertions are enabled at compile time</returns>


        public bool AssertionsEnabled
        {
            get
            {
                return xsltCompiler.isAssertionsEnabled();
            }
            set
            {
                xsltCompiler.setAssertionsEnabled(value);
            }
        }

        /// <summary>
        /// The <c>XsltLanguageVersion</c> property determines the version of the XSLT language specification
        /// implemented by the compiler. In this Saxon release the value is always "3.0".
        /// </summary>
        /// <remarks>
        /// <para>Getting this property always returns "3.0".</para>
        /// <para>Setting this property has no effect.</para>
        /// </remarks>

        public string XsltLanguageVersion
        {
            get
            {
                return "3.0";
            }
            set
            { }
        }

        /// <summary>
        /// This property determines whether bytecode is to be generated in the compiled stylesheet.
        /// </summary>
        /// <remarks>
        /// <para>
        /// Bytecode generation is enabled by default in Saxon-EE, but can be disabled by clearing this property.
        /// In Saxon-HE and Saxon-PE, attempting to set this property to true either has no effect, or causes an error.
        /// </para>
        /// <para>
        /// Setting this property on causes bytecode to be generated for sections of the stylesheet that are
        /// executed frequently enough to justify it. It does not force immediate (eager) byte code generation.
        /// </para>
        /// </remarks>
        /// <returns>true if bytecode is to be generated, false if not</returns>

        public bool ByteCodeEnabled {

            get { return xsltCompiler.getUnderlyingCompilerInfo().isGenerateByteCode(); }
            set { xsltCompiler.getUnderlyingCompilerInfo().setGenerateByteCode(value); }
        }



        /// <summary>
        /// Property to say whether just-in-time compilation of template rules should be used.
        /// jit true if just-in-time compilation is to be enabled. With this option enabled,
        /// static analysis of a template rule is deferred until the first time that the
        /// template is matched.This can improve performance when many template
        /// rules are rarely used during the course of a particular transformation; however,
        /// it means that static errors in the stylesheet will not necessarily cause the
        /// <code>Compile(Source)</code> method to throw an exception (errors in code that is
        ///  actually executed will still be notified to the registered <code>ErrorList</code>
        ///  or <code>ErrorList</code>, but this may happen after the {
        /// <c>Compile(Source)</c>
        ///  method returns). This option is enabled by default in Saxon - EE, and is not available
        ///  in Saxon - HE or Saxon-PE.
        ///  <p><b> Recommendation:</b> disable this option unless you are confident that the
        ///  stylesheet you are compiling is error - free.</p>
        /// </summary>
        public bool JustInTimeCompilation {
            get { return xsltCompiler.isJustInTimeCompilation(); }
            set { xsltCompiler.setJustInTimeCompilation(value); }
        }



        /// <summary>
        /// List of errors. The caller should supply an empty list before calling Compile;
        /// the processor will then populate the list with error information obtained during
        /// the compilation. Each error will be included as an object of type <c>StaticError</c>.
        /// If no error list is supplied by the caller, error information will be written to
        /// an error list allocated by the system, which can be obtained as the value of this property.
        /// </summary>
        /// <remarks>
		/// By supplying a custom <c>List</c> with a user-written <c>add()</c> method, it is possible to
        /// intercept error conditions as they occur.
        /// </remarks>
        [Obsolete("IList<StaticError> ErrorList is deprecated, please use the methods SetErrorList and GetErrorList which hanldes IList<XmlProcessingError>.")]
        public IList<StaticError> ErrorList
        {
            set
            {
                errorGatherer = new ErrorReporterToStaticError(value);
                xsltCompiler.setErrorReporter(errorGatherer);
            }
            get
            {
                return errorGatherer.ErrorList;
            }
        }

        /// <summary>Set the <c>ErrorReporter</c> to be used when validating instance documents as a user defined IErrorReporter.
        /// If this property is used then the ErrorList property and SetErrorList method is overriden </summary>
        /// <remarks>The <c>IErrorReporter</c> to be used</remarks>
        public IErrorReporter ErrorReporter
        {
            set
            {
                errorReporter = null;
                xsltCompiler.setErrorReporter(new ErrorReporterWrapper(value));
            }

        }


        /// <summary>
		/// List of errors. The caller may supply an empty list before calling <c>Compile</c>;
        /// the processor will then populate the list with error information obtained during
		/// the XSLT compilation. Each error will be included as an object of type <c>XmlProcessingError</c>.
        /// If no error list is supplied by the caller, error information will be written to
        /// the standard error stream.
        /// </summary>
        /// <remarks>
		/// <para>By supplying a custom <c>List</c> or IErrorReport implementation with a user-written <c>report()</c> method, it is possible to
        /// intercept error conditions as they occur.</para>
        /// <para>Note that this error list is used only for errors detected during the compilation
        /// of the stylesheet. It is not used for errors detected when executing the stylesheet.</para>
		/// </remarks>
		/// <param name="value">Supplied list.</param>
        public void SetErrorList(IList<XmlProcessingError> value)
        {
            errorReporter = new ErrorReporter(value);
            xsltCompiler.setErrorReporter(errorReporter);
        }

		/// <summary>
		/// Get list of errors as <code>IList&lt;XmlProcessingError&gt;</code>
		/// </summary>
        public IList<XmlProcessingError> GetErrorList()
        {
            return errorReporter.ErrorList;

        }



        /// <summary>
        /// Compile a stylesheet supplied as a Stream.
        /// </summary>
        /// <example>
        /// <code>
        /// Stream source = new FileStream("input.xsl", FileMode.Open, FileAccess.Read);
        /// XsltExecutable q = compiler.Compile(source);
        /// source.Close();
        /// </code>
        /// </example>
        /// <param name="input">A stream containing the source text of the stylesheet</param>
        /// <returns>An <c>XsltExecutable</c> which represents the compiled stylesheet object.
        /// The <c>XsltExecutable</c> may be loaded as many times as required, in the same or a different
        /// thread. The <c>XsltExecutable</c> is not affected by any changes made to the <c>XsltCompiler</c>
        /// once it has been compiled.</returns>
        /// <remarks>
        /// <para>If the stylesheet contains any <c>xsl:include</c> or <c>xsl:import</c> declarations,
        /// then the <c>BaseUri</c> property must be set to allow these to be resolved.</para>
        /// <para>The stylesheet is contained in the part of the input stream between its current
        /// position and the end of the stream. It is the caller's responsibility to close the input 
        /// stream after use. If the compilation succeeded, then on exit the stream will be 
        /// exhausted; if compilation failed, the current position of the stream on exit is
        /// undefined.</para>
        /// </remarks>

        public XsltExecutable Compile(Stream input)
        {
            try
            {
                JStreamSource ss = new JStreamSource(new JDotNetInputStream(input));
                if (baseUri != null)
                {
                    ss.setSystemId(Uri.EscapeUriString(baseUri.ToString()));
                }
                XsltExecutable executable = new XsltExecutable(xsltCompiler.compile(ss));
                executable.InternalProcessor = processor;
                return executable;
            }
            catch (JSaxonApiException err)
            {
                throw new StaticError(err);
            }
        }

		// internal method: Compile a stylesheet supplied as a Stream.
		// For example:
		// <code>
		// Stream source = new FileStream("input.xsl", FileMode.Open, FileAccess.Read);
		// XsltExecutable q = compiler.Compile(source);
		// source.Close();
		// </code>
		// <param name="input">A stream containing the source text of the stylesheet</param>
		// <param name="theBaseUri">Specify the base URI of the stream</param>
		// <param name="closeStream">Flag to indicate if the stream should be closed in the method</param>
		// <returns>An <c>XsltExecutable</c> which represents the compiled stylesheet object.
		// The XsltExecutable may be loaded as many times as required, in the same or a different
		// thread. The <c>XsltExecutable</c> is not affected by any changes made to the <c>XsltCompiler</c>
		// once it has been compiled.</returns>
		// <para>If the stylesheet contains any <c>xsl:include</c> or <c>xsl:import</c> declarations,
		// then the <c>BaseUri</c> property must be set to allow these to be resolved.</para>
		// <para>The stylesheet is contained in the part of the input stream between its current
		// position and the end of the stream. It is the caller's responsibility to close the input 
		// stream after use. If the compilation succeeded, then on exit the stream will be 
		// exhausted; if compilation failed, the current position of the stream on exit is
		// undefined.</para>

        internal XsltExecutable Compile(Stream input, String theBaseUri, bool closeStream)
        {
            //See bug 2306
            try
            {
                JStreamSource ss = new JStreamSource(new JDotNetInputStream(input));
                if (theBaseUri != null)
                {
                    ss.setSystemId(Uri.EscapeUriString(theBaseUri.ToString()));
                }
                else
                {
                    if (baseUri != null)
                    {
                        ss.setSystemId(Uri.EscapeUriString(baseUri.ToString()));
                    }
                }
                JXsltExecutable jexecutable =  xsltCompiler.compile(ss);
               
                if (closeStream)
                {
                    input.Close();
                }
                XsltExecutable executable = new XsltExecutable(jexecutable);
                executable.InternalProcessor = processor;
                return executable;
            }
            catch (JSaxonApiException err)
            {
                throw new StaticError(err);
            }
        }



        /// <summary>Compile a library package.</summary>
        /// <remarks>
        /// <para>The source argument identifies an XML file containing an <c>xsl:package</c> element. Any packages
        /// on which this package depends must have been made available to the <c>XsltCompiler</c>
        /// by importing them using <see cref="ImportPackage"/>.</para>
        /// </remarks>
        /// <param name='input'>source identifies an XML document holding the the XSLT package to be compiled</param>
        /// <returns>The <c>XsltPackage</c> that results from the compilation. Note that this package
        /// is not automatically imported to this <c>XsltCompiler</c>; if the package is required
        /// for use in subsequent compilations then it must be explicitly imported.</returns>

        public XsltPackage CompilePackage(Stream input)
        {
            try
            {
                JStreamSource ss = new JStreamSource(new JDotNetInputStream(input));
                if (baseUri != null)
                {
                    ss.setSystemId(Uri.EscapeUriString(baseUri.ToString()));
                }

                return new XsltPackage(processor, xsltCompiler.compilePackage(ss));
            }
            catch (JSaxonApiException err)
            {
                throw new StaticError(err);
            }
        }




        /// <summary>Compile a list of packages.</summary>
        /// <param name='sources'> the collection of packages to be compiled, in the form of an
		/// <c>Iterable</c></param>
		/// <returns> the collection of compiled packages, in the form of an <c>Iterable</c></returns> 
        [Obsolete("CompilePackages is deprecated, please use configuration to add list of packages.")]
        public IList<XsltPackage> CompilePackages(IList<String> sources)
        {
            JCompilerInfo compilerInfo = xsltCompiler.getUnderlyingCompilerInfo();
            JConfiguration config = xsltCompiler.getUnderlyingCompilerInfo().getConfiguration();
            JCompilation compilation = new JCompilation(config,compilerInfo);
            
             java.util.Set sourcesJList = new java.util.HashSet();

            foreach (String sourceStr in sources)
            {
                sourcesJList.add(new java.io.File(sourceStr));

            }


            java.lang.Iterable resultJList = null;

            try
            {
                compilerInfo.setPackageLibrary(new JPackageLibrary(compilerInfo, sourcesJList));

                resultJList =compilerInfo.getPackageLibrary().getPackages();


            }
            catch (JTransformerException ex)
            {
                throw new StaticError(ex);
            }
            IList<XsltPackage> result = new List<XsltPackage>();
            java.util.Iterator iter = resultJList.iterator();

            for (; iter.hasNext();)
            {
                JXsltPackage pp = (JXsltPackage)iter.next();
                result.Add(new XsltPackage(processor, pp));
            }

            return result;
        }




        /// <summary>Import a library package. Calling this method makes the supplied package available for reference
        /// in the <code>xsl:use-package</code> declaration of subsequent compilations performed using this
        /// <code>XsltCompiler</code>.</summary>
        /// <param name='thePackage'> the package to be imported</param>
        /// <remarks>since 9.6</remarks>

        public void ImportPackage(XsltPackage thePackage)
        {
            if (thePackage.Processor != this.processor)
            {
                throw new StaticError(new JTransformerException("The imported package and the XsltCompiler must belong to the same Processor"));
            }
            GetUnderlyingCompilerInfo().getPackageLibrary().addPackage(thePackage.getUnderlyingPreparedPackage());
        }


        /// <summary>Import a library package. Calling this method makes the supplied package available for reference
        /// in the <code>xsl:use-package</code> declaration of subsequent compilations performed using this
        /// <code>XsltCompiler</code>.</summary>
		/// <param name='thePackage'> the package to be imported</param>
		/// <param name='packageName'> name of the package to be imported</param>
		/// <param name='version'> version identifier for the package to be imported</param>
        /// <remarks>since 9.8</remarks>

        public void ImportPackage(XsltPackage thePackage, string packageName, string version)
        {
            if (thePackage.Processor != this.processor)
            {
                throw new StaticError(new JTransformerException("The imported package and the XsltCompiler must belong to the same Processor"));
            }
            try {
                JPackageDetails details = new JPackageDetails();
                if (packageName == null) {
                    packageName = thePackage.PackageName;
                }
                if (version == null) {
                    version = thePackage.Version;
                }
                details.nameAndVersion = new JVersionedPackageName(packageName, version);
                details.loadedPackage = thePackage.getUnderlyingPreparedPackage();
                xsltCompiler.getUnderlyingCompilerInfo().getPackageLibrary().addPackage(details);
            } catch (JTransformerException ex) {
                throw new StaticError(ex);
            }
        }


        /// <summary>
		/// Load a compiled package from a file or from a remote location.
		/// </summary>
		/// <remarks>
        /// The supplied URI represents the location of a resource which must have been originally
		/// created using <see cref="XsltPackage.Save(Stream)"/>.
        /// The result of loading the package is returned as an <code>XsltPackage</code> object.
        /// Note that this package is not automatically imported to this <code>XsltCompiler</code>;
        /// if the package is required for use in subsequent compilations then it must be explicitly
        /// imported.
		/// </remarks>
        /// <param name="location">the location from which the package is to be loaded, as a URI</param>
        /// <returns>the compiled package loaded from the supplied file or remote location</returns>

        public XsltPackage LoadLibraryPackage(Uri location)
        {
            try
            {
                JXsltPackage package = xsltCompiler.loadLibraryPackage(new java.net.URI(location.ToString()));
                return new XsltPackage(processor, package);

            }
            catch (net.sf.saxon.trans.XPathException e)
            {
                throw new StaticError(e);
            }


        }

        /// <summary>
        /// Load a compiled package from a file or from a remote location, with the intent to use this as a complete
		/// executable stylesheet, not as a library package.
		/// </summary>
		/// <remarks>
        /// The supplied URI represents the location of a resource which must have been originally
		/// created using <see cref="XsltPackage.Save(Stream)"/>.
		/// </remarks>
        /// <param name="location"> the location from which the package is to be loaded, as a URI</param>
        /// <returns>the compiled package loaded from the supplied file or remote location</returns>

        public XsltExecutable LoadExecutablePackage(Uri location)
        {
            XsltExecutable executable = LoadLibraryPackage(location).Link();
            executable.InternalProcessor = processor;
            return executable;
        }


        ///	<summary>  
		/// Get the underlying <c>CompilerInfo</c> object, which provides more detailed (but less stable) control
        /// over some compilation options
        /// </summary>
		/// <returns> the underlying <c>CompilerInfo</c> object, which holds compilation-time options. The methods on
        /// this object are not guaranteed stable from release to release.
        /// </returns>

        public JCompilerInfo GetUnderlyingCompilerInfo()
        {
            return xsltCompiler.getUnderlyingCompilerInfo();
        }



        /// <summary>
        /// Escape hatch to the underlying Java implementation of the <c>XsltCompiler</c>
        /// </summary>

        public JXsltCompiler Implementation
        {
            get { return xsltCompiler; }
        }


        /// <summary>
        /// Externally set the value of a static parameter (new facility in XSLT 3.0) 
        /// </summary>
        /// <param name="name">The name of the parameter, expressed
        /// as a QName. If a parameter of this name has been declared in the
        /// stylesheet, the given value will be assigned to the variable. If the
        /// variable has not been declared, calling this method has no effect (it is
        /// not an error).</param>
        /// <param name="value">The value to be given to the parameter.
        /// If the parameter declaration defines a required type for the variable, then
        /// this value will be converted in the same way as arguments to function calls
        /// (for example, numeric promotion is applied).</param>

        public void SetParameter(QName name, XdmValue value)
        {
            if (value == null)
            {
                if (variableList.ContainsKey(name))
                {
                    variableList.Remove(name);
                    xsltCompiler.getUnderlyingCompilerInfo().setParameter(name.ToStructuredQName(), null);
                }
            }
            else
            {
                variableList[name] = value;
                xsltCompiler.getUnderlyingCompilerInfo().setParameter(name.ToStructuredQName(), value.value);
            }
        }


        /// <summary>
        /// Clear the values of all stylesheet parameters previously set using <c>SetParameter(QName, XdmValue)</c>.
        /// This resets the parameters to their initial ("undeclared") state
        /// </summary>
        public void ClearParameters() {
            xsltCompiler.clearParameters();
        }

        /// <summary>
        /// Property to check and set fast compilation. Fast compilation will generally be achieved at the expense of run-time performance
        /// and quality of diagnostics. Fast compilation is a good trade-off if (a) the stylesheet is known to be correct,
        /// and (b) once compiled, it is only executed once against a document of modest size.
		/// </summary>
		/// <remarks>
        /// <para>Fast compilation may result in static errors going unreported, especially if they occur in code
        /// that is never executed.</para>
        /// <para><i>The current implementation is equivalent to switching off all optimizations other than just-in-time
        /// compilation of template rules. Setting this option, however, indicates an intent rather than a mechanism,
        /// and the implementation details may change in future to reflect the intent.</i></para>
        /// <para>Set to true to request fast compilation; set to false to revert to the optimization options
		/// defined in the Configuration.</para>
		/// </remarks>

        public bool FastCompilation {

            set { xsltCompiler.setFastCompilation(value); }
            get { return xsltCompiler.isFastCompilation(); }
        }


        [Obsolete("This property has been replaced by FastCompilation.")]
        public bool FastCompliation
        {

            set { xsltCompiler.setFastCompilation(value); }
            get { return xsltCompiler.isFastCompilation(); }
        }

        /// <summary>
		/// Compile a stylesheet supplied as a <c>TextReader</c>.
        /// </summary>
        /// <example>
        /// <code>
        /// String ss = "<![CDATA[<xsl:stylesheet version='2.0'>....</xsl:stylesheet>]]>";
        /// TextReader source = new StringReader(ss);
        /// XsltExecutable q = compiler.Compile(source);
        /// source.Close();
        /// </code>
        /// </example>
        /// <param name="input">A <c>TextReader</c> containing the source text of the stylesheet</param>
        /// <returns>An <c>XsltExecutable</c> which represents the compiled stylesheet object.
		/// The <c>XsltExecutable</c> may be loaded as many times as required, in the same or a different
        /// thread. The <c>XsltExecutable</c> is not affected by any changes made to the <c>XsltCompiler</c>
        /// once it has been compiled.</returns>
        /// <remarks>
        /// <para>If the stylesheet contains any <c>xsl:include</c> or <c>xsl:import</c> declarations,
        /// then the <c>BaseUri</c> property must be set to allow these to be resolved.</para>
        /// <para>The stylesheet is contained in the part of the input stream between its current
        /// position and the end of the stream. It is the caller's responsibility to close the 
        /// <c>TextReader</c> after use. If the compilation succeeded, then on exit the stream will be 
        /// exhausted; if compilation failed, the current position of the stream on exit is
        /// undefined.</para>
        /// </remarks>

        public XsltExecutable Compile(TextReader input)
        {
            try {
                JStreamSource ss = new JStreamSource(new JDotNetReader(input));
                if (baseUri != null)
                {
                    ss.setSystemId(Uri.EscapeUriString(baseUri.ToString()));
                }

                XsltExecutable executable = new XsltExecutable(xsltCompiler.compile(ss));
                executable.InternalProcessor = processor;
                return executable;
            }
            catch (JSaxonApiException ex) {
                throw new StaticError(ex);
            }
        }

        /// <summary>
        /// Compile a stylesheet, retrieving the source using a URI.
        /// </summary>
        /// <remarks>
        /// The document located via the URI is parsed using the <c>System.Xml</c> parser. This
        /// URI is used as the base URI of the stylesheet: the <c>BaseUri</c> property of the
        /// <c>Compiler</c> is ignored.
        /// </remarks>
        /// <param name="uri">The URI identifying the location where the stylesheet document can be
        /// found</param>
        /// <returns>An <c>XsltExecutable</c> which represents the compiled stylesheet object.
		/// The <c>XsltExecutable</c> may be run as many times as required, in the same or a different
        /// thread. The <c>XsltExecutable</c> is not affected by any changes made to the <c>XsltCompiler</c>
        /// once it has been compiled.</returns>

        public XsltExecutable Compile(Uri uri)
        {
            Object obj = XmlResolver.GetEntity(uri, "application/xml", Type.GetType("System.IO.Stream"));
            if (obj is Stream)
            {

                // See bug issue #2306
                XsltExecutable executable = Compile((Stream)obj, uri.ToString(), true);
                executable.InternalProcessor = processor;
                return executable;

            }
            else
            {
                throw new ArgumentException("Invalid type of result from XmlResolver.GetEntity: " + obj);
            }
        }

        /// <summary>
		/// Compile a stylesheet, delivered using an <c>XmlReader</c>.
        /// </summary>
        /// <remarks>
		/// <para>
        /// The <c>XmlReader</c> is responsible for parsing the document; this method builds a tree
        /// representation of the document (in an internal Saxon format) and compiles it.
        /// The <c>XmlReader</c> will be used as supplied; it is the caller's responsibility to
        /// ensure that the settings of the <c>XmlReader</c> are consistent with the requirements
        /// of the XSLT specification (for example, that entity references are expanded and whitespace
        /// is preserved).
		/// </para>
		/// <para>
        /// If the <c>XmlReader</c> has a <c>BaseUri</c> property, then that property determines
        /// the base URI of the stylesheet module, which is used when resolving any <c>xsl:include</c>
        /// or <c>xsl:import</c> declarations. If the <c>XmlReader</c> has no <c>BaseUri</c>
        /// property, then the <c>BaseUri</c> property of the <c>Compiler</c> is used instead.
        /// An <c>ArgumentNullException</c> is thrown if this property has not been supplied.
		/// </para>
        /// </remarks>
        /// <param name="reader">The XmlReader (that is, the XML parser) used to supply the document containing
        /// the principal stylesheet module.</param>
        /// <returns>An <c>XsltExecutable</c> which represents the compiled stylesheet object.
        /// The XsltExecutable may be run as many times as required, in the same or a different
        /// thread. The <c>XsltExecutable</c> is not affected by any changes made to the <c>XsltCompiler</c>
        /// once it has been compiled.</returns>


        public XsltExecutable Compile(XmlReader reader)
        {
            JDotNetPullProvider pp = new JDotNetPullProvider(reader);
            JPipelineConfiguration pipe = processor.Implementation.makePipelineConfiguration();
            pp.setPipelineConfiguration(pipe);
            // pp = new PullTracer(pp);  /* diagnostics */
            JPullSource source = new JPullSource(pp);
            String baseu = reader.BaseURI;
            if (baseu == null || baseu == String.Empty)
            {
                // if no baseURI is supplied by the XmlReader, use the one supplied to this Compiler
                if (baseUri == null)
                {
                    throw new ArgumentNullException("BaseUri");
                }
                baseu = Uri.EscapeUriString(baseUri.ToString());
                pp.setBaseURI(baseu);
            }
            source.setSystemId(baseu);
            try {

                XsltExecutable executable =  new XsltExecutable(xsltCompiler.compile(source));
                executable.InternalProcessor = processor;
                return executable;
            }
            catch (JSaxonApiException e) {
                throw new StaticError(e);
            }

            }

        /// <summary>
		/// Compile a stylesheet, located at an <c>XdmNode</c>. This may be a document node whose
        /// child is an <c>xsl:stylesheet</c> or <c>xsl:transform</c> element, or it may be
        /// the <c>xsl:stylesheet</c> or <c>xsl:transform</c> element itself.
        /// </summary>
        /// <param name="node">The document node or the outermost element node of the document
        /// containing the principal stylesheet module.</param>
        /// <returns>An <c>XsltExecutable</c> which represents the compiled stylesheet object.
		/// The <c>XsltExecutable</c> may be run as many times as required, in the same or a different
        /// thread. The <c>XsltExecutable</c> is not affected by any changes made to the <c>XsltCompiler</c>
        /// once it has been compiled.</returns>

        public XsltExecutable Compile(XdmNode node)
        {
            XsltExecutable executable = new XsltExecutable(xsltCompiler.compile(node.Implementation));
            executable.InternalProcessor = processor;
            return executable;
        }

        /// <summary>Locate and compile a stylesheet identified by an <c>&lt;?xml-stylesheet?&gt;</c>
		/// processing instruction within a source document, and that match the given criteria.
        /// </summary>
        /// <param name="uri">The URI of the source document containing the xml-stylesheet processing instruction.</param>
        /// <param name="media">The media attribute to be matched. May be null, in which case the 
        /// "application/xml" mime type will be used when fetching the source document from the Uri.</param>
        /// <returns>An <c>XsltExecutable</c> which represents the compiled stylesheet object.</returns>
		/// <remarks>There are some limitations in the current implementation. Parameters of the xml-stylesheet 
		/// instruction other than the media type, are ignored. The
        /// <c>href</c> attribute must either reference an embedded stylesheet within the same
        /// document or a non-embedded external stylesheet.</remarks>

        public XsltExecutable CompileAssociatedStylesheet(Uri uri, String media)
        {

            Object obj = XmlResolver.GetEntity(uri, media == null ? "application/xml" : media, Type.GetType("System.IO.Stream"));
            if (obj is Stream)
            {
                JStreamSource ss = new JStreamSource(new JDotNetInputStream((Stream)obj));

                ss.setSystemId(Uri.EscapeUriString(uri.ToString()));
                try
                {

                    // See bug issue #2306
                    XsltExecutable executable = new XsltExecutable(xsltCompiler.compile(xsltCompiler.getAssociatedStylesheet(ss, media, null, null)));
                    executable.InternalProcessor = processor;
                    return executable;

                }
                catch (JSaxonApiException e)
                {
                    throw new StaticError(e);
                }

            }
            else
            {
                throw new ArgumentException("Invalid type of result from XmlResolver.GetEntity: " + obj);

            }
         
        }

            /// <summary>Locate and compile a stylesheet identified by an <c>&lt;?xml-stylesheet?&gt;</c>
            /// processing instruction within a source document.
            /// </summary>
            /// <param name="source">The document node of the source document containing the
            /// xml-stylesheet processing instruction.</param>
            /// <returns>An <c>XsltExecutable</c> which represents the compiled stylesheet object.</returns>
            /// <remarks>There are some limitations in the current implementation. The media type
            /// is ignored, as are the other parameters of the xml-stylesheet instruction. The
            /// <c>href</c> attribute must either reference an embedded stylesheet within the same
            /// document or a non-embedded external stylesheet.</remarks>

            public XsltExecutable CompileAssociatedStylesheet(XdmNode source)
        {


            // TODO: lift the restrictions
            if (source == null || source.NodeKind != XmlNodeType.Document)
            {
                throw new ArgumentException("Source must be a document node");
            }
            IEnumerator kids = source.Children(Predicates.IsProcessingInstruction().And(Predicates.HasName("", "xml-stylesheet"))).GetEnumerator();
            while (kids.MoveNext())
            {
                XdmNode n = (XdmNode)kids.Current;
                // TODO: check the media type
                String href = JProcInstParser.getPseudoAttribute(n.StringValue, "href");
                if (href == null)
                {
                    throw new DynamicError("xml-stylesheet processing instruction has no href attribute");
                }
                String fragment = null;
                int hash = href.LastIndexOf('#');
                if (hash == 0)
                {
                    if (href.Length == 1)
                    {
                        throw new DynamicError("Relative URI of '#' is invalid");
                    }
                fragment = href.Substring(1);
                JNodeInfo target = ((JTreeInfo)source.value).selectID(fragment, true);
                XdmNode targetWrapper = null;
                if (target == null)
                {
                            // There's a problem here because the Microsoft XML parser doesn't
                            // report id values, so selectID() will never work. We work around that
                            // by looking for an attribute named "id" appearing on an xsl:stylesheet
                            // or xsl:transform element
                            QName qid = new QName("", "id");
                            IEnumerator en = source.EnumerateAxis(XdmAxis.Descendant);
                            while (en.MoveNext())
                            {
                                XdmNode x = (XdmNode)en.Current;
                                if (x.NodeKind == XmlNodeType.Element &&
                                        x.NodeName.Uri == "http://www.w3.org/1999/XSL/Transform" &&
                                        (x.NodeName.LocalName == "stylesheet" || x.NodeName.LocalName == "transform" &&
                                        x.GetAttributeValue(qid) == fragment))
                                {
                                    targetWrapper = x;
                                }
                            }
                        }
                        else
                        {
                            targetWrapper = (XdmNode)XdmValue.Wrap(target);
                            targetWrapper.SetProcessor(processor);
                        }
                        if (targetWrapper == null)
                        {
                            throw new DynamicError("No element with id='" + fragment + "' found");
                        }
                        return Compile(targetWrapper);
                    }
                    else if (hash > 0)
                    {
                        throw new NotImplementedException("href cannot identify an embedded stylesheet in a different document");
                    }
                    else
                    {
                        Uri uri = new Uri(n.BaseUri, href);
                        return Compile(uri);
                    }
                
            }
            throw new DynamicError("xml-stylesheet processing instruction not found");
        }
    }

    /// <summary>
    /// An <c>XsltExecutable</c> represents the compiled form of a stylesheet. To execute the stylesheet,
	/// it must first be loaded to form an <see cref="XsltTransformer"/> or <see cref="Xslt30Transformer"/>.
    /// </summary>
    /// <remarks>
    /// <para>An <c>XsltExecutable</c> is immutable, and therefore thread-safe. It is simplest to
    /// load a new <c>XsltEvaluator</c> each time the stylesheet is to be run. However, the 
    /// <c>XsltEvaluator</c> is serially reusable within a single thread.</para>
    /// <para>An <c>XsltExecutable</c> is created by using one of the <c>Compile</c>
	/// methods on the <see cref="XsltCompiler"/> class.</para>
    /// </remarks>    

    [Serializable]
    public class XsltExecutable
    {

        // private JPreparedStylesheet pss;
        private Processor processor;
        private JXsltExecutable executable;

        // internal constructor

        internal XsltExecutable(JXsltExecutable executable)
        {
            //this.processor = proc;
            this.executable = executable;
        }

        internal Processor InternalProcessor
        { 
            set { processor = value;}

        }

        /// <summary>
        /// Get the Processor that was used to create this XsltExecutable
        /// </summary>
        public Processor Processor
        {
            get { return processor; }

        }

        /// <summary>
        /// Load the stylesheet to prepare it for execution.
        /// </summary>
        /// <returns>
        /// An <c>XsltTransformer</c>. The returned <c>XsltTransformer</c> can be used to
        /// set up the dynamic context for stylesheet evaluation, and to run the stylesheet.
        /// </returns>

        public XsltTransformer Load()
        {
           // JXsltController c = pss.newController();
            return new XsltTransformer(executable.load());
        }



        /// <summary>
		/// Load the stylesheet to prepare it for execution. This version of the <c>load()</c> method
        /// creates an <code>Xslt30Transformer</code> which offers interfaces for stylesheet
        /// invocation corresponding to those described in the XSLT 3.0 specification. It can be used
        /// with XSLT 2.0 or XSLT 3.0 stylesheets, and in both cases it offers new XSLT 3.0 functionality such
        /// as the ability to supply parameters to the initial template, or the ability to invoke
        /// stylesheet-defined functions, or the ability to return an arbitrary sequence as a result
        /// without wrapping it in a document node.
        /// </summary>
        /// <returns>
        /// An <c>Xslt30Transformer</c>. The returned <c>Xslt30Transformer</c> can be used to
        /// set up the dynamic context for stylesheet evaluation, and to run the stylesheet.
        /// </returns>

        public Xslt30Transformer Load30()
        {
            return new Xslt30Transformer(executable.load30());
        }

        /// <summary>
        /// Output an XML representation of the compiled code of the stylesheet, for purposes of 
        /// diagnostics and instrumentation.
        /// </summary>
        /// <param name="destination">The destination for the diagnostic output</param>

        public void Explain(XmlDestination destination)
        {
            executable.explain(destination.GetUnderlyingDestination());
        }

        /// <summary>
        /// Escape hatch to the underlying Java implementation object.
        /// </summary>

        public JXsltExecutable Implementation
        {
            get
            {
                return executable;
            }
        }

        /// <summary>
        /// Get the whitespace stripping policy defined by this stylesheet, that is, the policy
        /// defined by the <c>xsl:strip-space</c> and <c>xsl:preserve-space</c> elements in the stylesheet.
        /// </summary>
        /// <returns> a newly constructed <c>WhitespacePolicy</c> based on the declarations in this
        /// stylesheet. This policy can be used as input to a <see cref="DocumentBuilder"/>.</returns>
        
        public WhitespacePolicy getWhitespaceStrippingPolicy() {
            return new WhitespacePolicy(executable);
        }


        /// <summary>
        /// Get the names of the <c>xsl:param</c> elements defined in this stylesheet, with details
        /// of each parameter including its required type, and whether it is required or optional.
        /// </summary>
        /// <returns>
		/// a <c>Dictionary</c> whose keys are the names of global parameters in the stylesheet,
		/// and whose values are <see cref="Saxon.Api.XsltExecutable+ParameterDetails"/> objects giving information about the
        /// corresponding parameter.
        /// </returns>
        public Dictionary<QName, ParameterDetails> GetGlobalParameters()
        {
            java.util.HashMap globals = executable.getGlobalParameters();
            Dictionary<QName, ParameterDetails> params1 = new Dictionary<QName, ParameterDetails>();
            int count = globals.size();
            object[] names = globals.keySet().toArray();
            for (int i = 0; i < count; i++) {
                JXsltExecutable.ParameterDetails details = (JXsltExecutable.ParameterDetails)globals.get(names[i]);
                XdmSequenceType sType = XdmSequenceType.FromSequenceType(details.getUnderlyingDeclaredType());
                sType.itemType.SetJXdmItemType(details.getDeclaredItemType());
                params1.Add(new QName((net.sf.saxon.s9api.QName)names[i]), new ParameterDetails(sType, false));

            }
            return params1;
        }

        /// <summary>
        /// Information about a global parameter to a stylesheet.
        /// </summary>

        public class ParameterDetails
        {
            private XdmSequenceType type;
            private bool isRequired;

            /// <summary>
            /// Create parameter details.
            /// </summary>
            /// <param name="type">The declared type of the parameter.</param>
            /// <param name="isRequired">Indicates whether the parameter is required or optional.</param>
            
            public ParameterDetails(XdmSequenceType type, bool isRequired)
            {
                this.type = type;
                this.isRequired = isRequired;
            }

            /// <summary>
            /// Gets the declared item type of the parameter.
            /// </summary>
            /// <returns>The type defined in the <code>as</code> attribute of the <code>xsl:param</code> element,
            /// without its occurrence indicator</returns>
            public XdmItemType GetDeclaredItemType()
            {
                return type.itemType;
            }


            /// <summary>
            /// Gets the declared cardinality of the parameter.
            /// </summary>
            /// <returns>The occurrence indicator from the type appearing in the <code>as</code> attribute
            /// of the <code>xsl:param</code> element</returns>

            public char GetDeclaredCardinality()
            {
                return type.occurrenceIn;
            }

            /// <summary>
            /// Gets the underlying declared type of the parameter.
            /// </summary>
            /// <returns>The underlying declared type.</returns>

            public XdmSequenceType GetUnderlyingDeclaredType()
            {
                return type;
            }


            /// <summary>
            /// Ask whether the parameter is required (mandatory) or optional
            /// </summary>
            /// <returns><c>true</c> if the parameter is mandatory (<code>required="yes"</code>), false
            /// if it is optional.</returns>

            public bool IsRequired
            {
                set { this.isRequired = value; }
                get { return this.isRequired; }

            }
        }




    }


    /// <summary inherits="XdmDestination">
    /// An <c>XsltTransformer</c> represents a compiled and loaded stylesheet ready for execution.
    /// The <c>XsltTransformer</c> holds details of the dynamic evaluation context for the stylesheet.
    /// </summary>
    /// <remarks>
    /// <para>An <c>XsltTransformer</c> must not be used concurrently in multiple threads. It is safe,
    /// however, to reuse the object within a single thread to run the same stylesheet several times.
    /// Running the stylesheet does not change the context that has been established.</para>
    /// <para>An <c>XsltTransformer</c> is always constructed by running the <c>Load</c> method of
    /// an <c>XsltExecutable</c>.</para>
	/// <para>The <see cref="Xslt30Transformer"/> class provides invocation options that are more closely aligned
    /// with the XSLT 3.0 specification, for example streamed evaluation. However, both <c>XsltTransformer</c> 
	/// and <c>Xslt30Transformer</c> can be used irrespective of the XSLT language version used in the stylesheet.</para>
    /// </remarks>

    [Serializable]
    public class XsltTransformer : XmlDestination
    {

        /* private JXsltController controller;
      
         private JStreamSource streamSource;
         private GlobalParameterSet globalParameters;
         private bool baseOutputUriWasSet = false;*/
        private XmlDestination xmlDestination;
        private StandardLogger traceFunctionDestination;
        private IMessageListener messageListener;
        private IMessageListener2 messageListener2;
        private IResultDocumentHandler resultDocumentHandler;
        private QName initialTemplateName;
        private XdmNode initialContextNode;
        private JXsltTransformer transformer;

        // internal constructor

        internal XsltTransformer(JXsltTransformer transformer)
        {
            this.transformer = transformer;
        }

        /// <summary>
        /// The global context item for the stylesheet, as a node.
        /// </summary>
        /// <remarks><para>Although XSLT 3.0 allows the global context item to be any item,
        /// this interface only allows it to be a node.
        /// Most commonly it will be a document node, which might be constructed
        /// using the <c>Build</c> method of the <c>DocumentBuilder</c> object.</para>
        /// <para>Note that this can be inefficient if the stylesheet uses <c>xsl:strip-space</c>
        /// to strip whitespace, or <c>input-type-annotations="strip"</c> to remove type
        /// annotations, since this will result in the transformation operating on a virtual document
        /// implemented as a view or wrapper of the supplied document.</para>
        /// </remarks>

        public XdmNode InitialContextNode
        {
            get {
                JNodeInfo node = transformer.getInitialContextNode().getUnderlyingNode();
                return node == null ? null : ((XdmNode)XdmValue.Wrap(node)); }
            set { transformer.setInitialContextNode(value == null ? null : (net.sf.saxon.s9api.XdmNode)XdmValue.FromGroundedValueToJXdmValue(value.value)); }
        }

        /// <summary>
        /// Supply the principal input document for the transformation in the form of a stream.
        /// </summary>
        /// <remarks>
        /// <para>If this method is used, the <c>InitialContextNode</c> is ignored.</para>
        /// <para>The supplied stream will be consumed by the <c>Run()</c> method.
        /// Closing the input stream after use is the client's responsibility.</para>
        /// <para>A base URI must be supplied in all cases. It is used to resolve relative
        /// URI references appearing within the input document.</para>
        /// <para>Schema validation is applied to the input document according to the value of
        /// the <c>SchemaValidationMode</c> property.</para>
        /// <para>Whitespace stripping is applied according to the value of the
        /// <c>xsl:strip-space</c> and <c>xsl:preserve-space</c> declarations in the stylesheet.</para>
        /// </remarks>
        /// <param name="input">
        /// The stream containing the source code of the principal input document to the transformation. The document
        /// node at the root of this document will be the global context item for the transformation.
        /// </param>
        /// <param name="baseUri">
        /// The base URI of the principal input document. This is used for example by the <c>document()</c>
        /// function if the document contains links to other documents in the form of relative URIs.</param>

        public void SetInputStream(Stream input, Uri baseUri)
        {
            JStreamSource streamSource = new JStreamSource(new JDotNetInputStream(input), Uri.EscapeUriString(baseUri.ToString()));
            transformer.setSource(streamSource);
        }

        /// <summary>
		/// The initial mode for the stylesheet. This is either a <c>QName</c>, for a 
        /// specific mode, or null, for the default mode.
        /// </summary>
        /// <remarks>
        /// The default mode will usually be the unnamed mode, but if the stylesheet declares a
        /// named mode as the default mode, then supplying null as the <c>InitialMode</c> invokes this default.
        /// </remarks>

        public QName InitialMode
        {
            get
            {
                net.sf.saxon.s9api.QName mode = transformer.getInitialMode();
                if (mode == null)
                {
                    return null;
                }

                return QName.FromClarkName(mode.getClarkName());
            }
            set
            {
               transformer.setInitialMode(value == null ? null : net.sf.saxon.s9api.QName.fromEQName(value.EQName));
            }
        }


        /// <summary>
		/// The initial template for the stylesheet. This is either a <c>QName</c>, for a 
        /// named template, or null, if no initial template has been set.
        /// </summary>
        /// <remarks>
        /// If the stylesheet is to be invoked by calling the template named <c>xsl:initial-template</c>,
		/// then the <c>InitialTemplate</c> property should be set to this <c>QName</c> explicitly.
        /// </remarks>
        /// <exception cref="DynamicError">Setting this property to the name of a template
		/// that does not exist in the stylesheet throws a <c>DynamicError</c> with error 
        /// code XTDE0040. Setting it to the name of a template that has template
		/// parameters throws a <c>DynamicError</c> with error code XTDE0060.</exception>

        public QName InitialTemplate
        {
            get
            {
                return initialTemplateName;
            }
            set
            {
                initialTemplateName = value;
                transformer.setInitialTemplate(initialTemplateName.UnderlyingQName());
            }
        }

        /// <summary>
        /// The base output URI, which acts as the base URI for resolving the <c>href</c>
        /// attribute of <c>xsl:result-document</c>.
        /// </summary>

        public Uri BaseOutputUri
        {
            get
            {
                return new Uri(transformer.getBaseOutputURI());
            }
            set
            {
                transformer.setBaseOutputURI(value.ToString());
            }
        }


        /// <summary>
        /// The <c>SchemaValidationMode</c> to be used in this transformation, especially for documents
        /// loaded using the <c>doc()</c>, <c>document()</c>, or <c>collection()</c> functions.
        /// </summary>

        public SchemaValidationMode SchemaValidationMode
        {
            get
            {
                switch (transformer.getUnderlyingController().getSchemaValidationMode())
                {
                    case JValidation.STRICT:
                        return SchemaValidationMode.Strict;
                    case JValidation.LAX:
                        return SchemaValidationMode.Lax;
                    case JValidation.STRIP:
                        return SchemaValidationMode.None;
                    case JValidation.PRESERVE:
                        return SchemaValidationMode.Preserve;
                    case JValidation.DEFAULT:
                    default:
                        return SchemaValidationMode.Unspecified;
                }
            }

            set
            {
                switch (value)
                {
                    case SchemaValidationMode.Strict:
                        transformer.setSchemaValidationMode(JValidationMode.STRICT);
                        break;
                    case SchemaValidationMode.Lax:
                        transformer.setSchemaValidationMode(JValidationMode.LAX);
                        break;
                    case SchemaValidationMode.None:
                        transformer.setSchemaValidationMode(JValidationMode.STRIP);
                        break;
                    case SchemaValidationMode.Preserve:
                        transformer.setSchemaValidationMode(JValidationMode.PRESERVE);
                        break;
                    case SchemaValidationMode.Unspecified:
                    default:
                        transformer.setSchemaValidationMode(JValidationMode.DEFAULT);
                        break;
                }
            }
        }





        /// <summary>
        /// The <c>XmlResolver</c> to be used at run-time to resolve and dereference URIs
        /// supplied to the <c>doc()</c> and <c>document()</c> functions.
        /// </summary>

        public XmlResolver InputXmlResolver
        {
            get
            {
                return ((JDotNetURIResolver)transformer.getURIResolver()).getXmlResolver();
            }
            set
            {
                transformer.setURIResolver(new JDotNetURIResolver(value));
            }
        }

        /// <summary>
        /// The <c>IResultDocumentHandler</c> to be used at run-time to process the output
        /// produced by any <c>xsl:result-document</c> instruction with an <c>href</c>
        /// attribute.
        /// </summary>
        /// <remarks>
        /// In the absence of a user-supplied result document handler, the <c>href</c>
        /// attribute of the <c>xsl:result-document</c> instruction must be a valid relative
        /// URI, which is resolved against the value of the <c>BaseOutputUri</c> property,
        /// and the resulting absolute URI must identify a writable resource (typically
        /// a file in filestore, using the <c>file:</c> URI scheme).
        /// </remarks>

        public IResultDocumentHandler ResultDocumentHandler
        {
            get
            {
                return resultDocumentHandler;
            }
            set
            {
                resultDocumentHandler = value;
                transformer.getUnderlyingController().setResultDocumentResolver(new ResultDocumentHandlerWrapper(value, transformer.getUnderlyingController().makePipelineConfiguration()));
            }
        }

        /// <summary>
		/// Listener for messages output using <c>&lt;xsl:message&gt;</c>.
		/// </summary>
		/// <remarks> 
        /// <para>The caller may supply a message listener before calling <c>Run</c>;
        /// the processor will then invoke the listener once for each message generated during
        /// the transformation. Each message will be output as an object of type <c>XdmNode</c>
        /// representing a document node.</para>
        /// <para>If no message listener is supplied by the caller, message information will be written to
        /// the standard error stream.</para>
        /// <para>Each message is presented as an XML document node. Calling <c>ToString()</c>
        /// on the message object will usually generate an acceptable representation of the
        /// message.</para>
		/// <para>When the <c>xsl:message</c> instruction specifies <c>terminate="yes"</c>,
        /// the message is first notified using this interface, and then an exception is thrown
        /// which terminates the transformation.</para>
        /// </remarks>

        public IMessageListener MessageListener
        {
            set
            {
                messageListener = value;
                transformer.setMessageListener(new MessageListenerProxy(value));
            }
            get
            {
                return messageListener;
            }
        }



        /// <summary>
        /// Listener for messages output using <c>&lt;xsl:message&gt;</c>.
        /// </summary>
        /// <remarks> 
        /// <para>The caller may supply a message listener before calling <c>Run</c>;
        /// the processor will then invoke the listener once for each message generated during
        /// the transformation. Each message will be output as an object of type <c>XdmNode</c>
        /// representing a document node.</para>
        /// <para>If no message listener is supplied by the caller, message information will be written to
        /// the standard error stream.</para>
        /// <para>Each message is presented as an XML document node. Calling <c>ToString()</c>
        /// on the message object will usually generate an acceptable representation of the
        /// message.</para>
        /// <para>When the <c>xsl:message</c> instruction specifies <c>terminate="yes"</c>,
        /// the message is first notified using this interface, and then an exception is thrown
        /// which terminates the transformation.</para>
        /// <para>The <c>MessageListener2</c> property interface differs from the <c>MessageListener</c>
        /// in allowing the error code supplied to xsl:message to be notified</para>
        /// </remarks>

        public IMessageListener2 MessageListener2
        {
            set
            {
                messageListener2 = value;
                transformer.setMessageListener(new MessageListenerProxy2(value));
            }
            get
            {
                return messageListener2;
            }
        }

        /// <summary>
        /// Destination for output of messages using the <c>trace()</c> function.
        /// </summary>
		/// <remarks> 
		/// <para>If no message listener is supplied by the caller, message information will be written to
		/// the standard error stream.</para>
        /// <para>The supplied destination is ignored if a <c>TraceListener</c> is in use.</para>
        /// </remarks>

        public StandardLogger TraceFunctionDestination
        {
            set
            {
                traceFunctionDestination = value;
                transformer.setTraceFunctionDestination(value);
            }
            get
            {
                return traceFunctionDestination;
            }
        }



        /// <summary>
        /// Set the value of a stylesheet parameter.
        /// </summary>
        /// <param name="name">The name of the parameter, expressed
        /// as a QName. If a parameter of this name has been declared in the
        /// stylesheet, the given value will be assigned to the variable. If the
        /// variable has not been declared, calling this method has no effect (it is
        /// not an error).</param>
        /// <param name="value">The value to be given to the parameter.
        /// If the parameter declaration defines a required type for the variable, then
        /// this value will be converted in the same way as arguments to function calls
        /// (for example, numeric promotion is applied).</param>

        public void SetParameter(QName name, XdmValue value)
        {
            transformer.setParameter(net.sf.saxon.s9api.QName.fromEQName(name.EQName), net.sf.saxon.s9api.XdmValue.wrap(value.value));
        }

		public JDestination GetUnderlyingDestination() 
		{
			return transformer;
		}


        /// <summary>
        /// Get the underlying Java (s9api) XsltTransformer object which provides the underlying
        /// functionality of this object.
        /// </summary>
        /// <remarks>
        /// <para>Previously delivered under the misspelt name <code>Implemmentation</code></para>
        /// </remarks>

        public JXsltTransformer WrappedXsltTransformer
        {
            get
            {
                return transformer;

            }
        }



        /// <summary>
		/// The destination for the result of the transformation. The class <c>XmlDestination</c> is an abstraction 
        /// that allows a number of different kinds of destination to be specified.
        /// </summary>
        /// <remarks>
        /// <para>The Destination can be used to chain transformations into a pipeline, by using one
        /// <c>XsltTransformer</c> as the destination of another.</para>
        /// </remarks>

        public XmlDestination Destination
        {
            get
            {
                return this.xmlDestination;
            }
            set
            {
                this.xmlDestination = value;
                transformer.setDestination(value.GetUnderlyingDestination());
            }

        }


        /// <summary>
		/// Close the <c>Destination</c>, releasing any resources that need to be released.
        /// </summary>
        /// <remarks>
        /// This method is called by the system on completion of a query or transformation.
		/// Some kinds of <c>Destination</c> may need to close an output stream, others might
        /// not need to do anything. The default implementation does nothing.
        /// </remarks>

        public void Close()
        {
            transformer.close();

        }

		// internal method

        internal JReceiver GetDestinationReceiver(XmlDestination destination)
        {

            JController controller = transformer.getUnderlyingController();
                JPipelineConfiguration pipe = controller.getConfiguration().makePipelineConfiguration();
                JReceiver r = destination.GetUnderlyingDestination().getReceiver(pipe, controller.getExecutable().getPrimarySerializationProperties());
                pipe.setController(controller);
                return r;
            //}
        }

        /// <summary>
        /// Run the transformation, sending the result to a specified destination.
        /// </summary>
        /// <param name="destination">
        /// The destination for the results of the stylesheet. The class <c>XmlDestination</c>
        /// is an abstraction that allows a number of different kinds of destination
        /// to be specified.
        /// </param>
		/// <exception cref="DynamicError">Throws a <c>DynamicError</c> if the transformation
        /// fails.</exception>

        public void Run(XmlDestination destination)
        {
            if (destination == null)
            {
                throw new DynamicError("Destination is null");
            }
           
            try
            {
                transformer.setDestination(destination.GetUnderlyingDestination());
                transformer.transform() ;
               
            }
            catch (JSaxonApiException err)
            {
                throw new DynamicError(err);
            }
        }

        /*internal void applyTemplatesToSource(JStreamSource streamSource, JReceiver outi){

           
            if (controller.getInitialMode().isDeclaredStreamable()) {
                controller.applyStreamingTemplates(streamSource, outi);
            }
        } */

        /// <summary>
        /// Escape hatch to the underlying Java implementation
        /// </summary>

        public JController Implementation
        {
            get { return transformer.getUnderlyingController(); }
        }


    }

    /// <summary>
	/// <c>RecoveryPolicy</c> is an enumeration of the different actions that can be taken when a "recoverable error" occurs.
    /// </summary>  

    public enum RecoveryPolicy
    {
        /// <summary>
        /// Ignore the error, take the recovery action, do not produce any message
        /// </summary>
        RecoverSilently,

        /// <summary>
        /// Take the recovery action after outputting a warning message
        /// </summary>
        RecoverWithWarnings,

        /// <summary>
        /// Treat the error as fatal
        /// </summary>
        DoNotRecover

    }



    /// <summary>An <c>IResultDocumentHandler</c> can be nominated to handle output
    /// produced by the <c>xsl:result-document</c> instruction in an XSLT stylesheet.
    /// </summary>
    /// <remarks>
    /// <para>This interface affects any <c>xsl:result-document</c> instruction
    /// executed by the stylesheet, provided that it has an <c>href</c> attribute.</para> 
    /// <para>If no <c>IResultDocumentHandler</c> is nominated (in the
    /// <c>IResultDocumentHandler</c> property of the <c>XsltTransformer</c>), the output
    /// of <code>xsl:result-document</code> is serialized, and is written to the file
    /// or other resource identified by the URI in the <c>href</c> attribute, resolved
    /// (if it is relative) against the URI supplied in the <c>BaseOutputUri</c> property
    /// of the <c>XsltTransformer</c>.</para>
    /// <para>If an <c>IResultDocumentHandler</c> is nominated, however, its
    /// <c>HandleResultDocument</c> method will be called whenever an <c>xsl:result-document</c>
    /// instruction with an <c>href</c> attribute is evaluated, and the generated result tree
    /// will be passed to the <c>XmlDestination</c> returned by that method.</para> 
    /// </remarks>

    public interface IResultDocumentHandler
    {

        /// <summary> Handle output produced by the <c>xsl:result-document</c>
        /// instruction in an XSLT stylesheet. This method is called by the XSLT processor
        /// when an <c>xsl:result-document</c> with an <c>href</c> attribute is evaluated.
        /// </summary>
        /// <param name="href">An absolute or relative URI. This will be the effective value of the 
        /// <c>href</c> attribute of the <c>xsl:result-document</c> in the stylesheet.</param>
        /// <param name="baseUri">The base URI that should be used for resolving the value of
        /// <c>href</c> if it is relative. This will always be the value of the <c>BaseOutputUri</c>
        /// property of the <c>XsltTransformer</c>.</param>
        /// <returns>An <c>XmlDestination</c> to handle the result tree produced by the
        /// <c>xsl:result-document</c> instruction. The <c>Close</c> method of the returned
        /// <c>XmlDestination</c> will be called when the output is complete.</returns>
        /// <remarks>
        /// <para>The XSLT processor will ensure that the stylesheet cannot create
        /// two distinct result documents which are sent to the same URI. It is the responsibility
        /// of the <c>IResultDocumentHandler</c> to ensure that two distinct result documents are
        /// not sent to the same <c>XmlDestination</c>. Failure to observe this rule can result
        /// in output streams being incorrectly closed.
        /// </para>
        /// <para>Note that more than one result document can be open at the same time,
        /// and that the order of opening, writing, and closing result documents chosen
        /// by the processor does not necessarily bear any direct resemblance to the way
        /// that the XSLT source code is written.</para></remarks>
        /**public**/
        XmlDestination HandleResultDocument(string href, Uri baseUri);

    }

    ///<summary>Internal wrapper class for <c>IResultDocumentHandler</c></summary>
    internal class ResultDocumentHandlerWrapper : JResultDocumentResolver
    {

        private IResultDocumentHandler handler;
        private ArrayList resultList = new ArrayList();
        private ArrayList destinationList = new ArrayList();
        private JPipelineConfiguration pipe;

        /// <summary>
        /// Initializes a new instance of the <see cref="Saxon.Api.ResultDocumentHandlerWrapper"/> class.
        /// </summary>
        /// <param name="handler">Handler.</param>
        /// <param name="pipe">Pipe.</param>
        public ResultDocumentHandlerWrapper(IResultDocumentHandler handler, JPipelineConfiguration pipe)
        {
            this.handler = handler;
            this.pipe = pipe;
        }




        /// <summary>
        /// Close the specified result.
        /// </summary>
        /// <param name="result">Result.</param>
        public void close(JResult result)
        {
            for (int i = 0; i < resultList.Count; i++)
            {
                if (Object.ReferenceEquals(resultList[i], result))
                {
                    ((XmlDestination)destinationList[i]).GetUnderlyingDestination().close();
                    resultList.RemoveAt(i);
                    destinationList.RemoveAt(i);
                    return;
                }
            }
        }

        /// <summary>
        /// Resolve the specified href and baseString.
        /// </summary>
        /// <param name="href">Href.</param>
        /// <param name="baseString">Base string.</param>
        public JReceiver resolve(net.sf.saxon.expr.XPathContext xpc, string href, string baseString, net.sf.saxon.serialize.SerializationProperties sp)
        {
            Uri baseUri;
            try
            {
                baseUri = new Uri(baseString);
            }
            catch (System.UriFormatException err)
            {
                throw new JTransformerException("Invalid base output URI " + baseString, err);
            }
            XmlDestination destination = handler.HandleResultDocument(href, baseUri);
            JReceiver result = destination.GetUnderlyingDestination().getReceiver(pipe, sp);
            resultList.Add(result);
            destinationList.Add(destination);
            return result;
        }
    }

    /// <summary>An <c>IMessageListener</c> can be nominated to handle output
    /// produced by the <c>xsl:message</c> instruction in an XSLT stylesheet.
    /// </summary>
    /// <remarks>
    /// <para>This interface affects any <c>xsl:message</c> instruction
    /// executed by the stylesheet.</para> 
    /// <para>If no <c>IMessageListener</c> is nominated (in the
    /// <c>MessageListener</c> property of the <c>XsltTransformer</c>), the output
    /// of <code>xsl:message</code> is serialized, and is written to standard error
    /// output stream.</para>
    /// <para>If an <c>IMessageListener</c> is nominated, however, its
    /// <c>Message</c> method will be called whenever an <c>xsl:message</c>
    /// instruction is evaluated.</para> 
    /// </remarks>

    public interface IMessageListener
    {

        /// <summary>Handle the output of an <c>xsl:message</c> instruction
        /// in the stylesheet
        /// </summary>
        /// <param name="content">a document node representing the message content. Note the putput of <c>xsl:message</c>
        /// is always an XML document node. It can be flattened to obtain the stringvalue if required by calling
        /// <c>GetStringValue()</c></param>
        /// <param name="terminate">Set to true if <c>terminate ='yes'</c> was specified or to false otherwise</param>
        /// <param name="location">an object that contains the location of the <c>xsl:message</c> This provides access to the URI of the stylesheet
        /// module and the line number of the <c>xsl:message</c></param>

        /**public**/
        void Message(XdmNode content, bool terminate, IXmlLocation location);

    }



    /// <summary>An <c>IMessageListener</c> can be nominated to handle output
    /// produced by the <c>xsl:message</c> instruction in an XSLT stylesheet.
    /// </summary>
    /// <remarks>
    /// <para>This interface affects any <c>xsl:message</c> instruction
    /// executed by the stylesheet.</para> 
    /// <para>If no <c>IMessageListener</c> is nominated (in the
    /// <c>MessageListener</c> property of the <c>XsltTransformer</c>), the output
    /// of <code>xsl:message</code> is serialized, and is written to standard error
    /// output stream.</para>
    /// <para>If an <c>IMessageListener</c> is nominated, however, its
    /// <c>Message</c> method will be called whenever an <c>xsl:message</c>
    /// instruction is evaluated.</para> 
    /// <para>The <c>MessageListener2</c> interface differs from <c>MessageListener</c>
    /// in allowing the error code supplied to <c>xsl:message</c> to be made available.</para>
    /// </remarks>

    public interface IMessageListener2
    {

        /// <summary>Handle the output of an <c>xsl:message</c> instruction
        /// in the stylesheet
        /// </summary>
        /// <param name="content">a document node representing the message content. Note the putput of <c>xsl:message</c>
        /// is always an XML document node. It can be flattened to obtain the stringvalue if required by calling
        /// <c>GetStringValue()</c></param>
        /// <param name="errorCode">a QName containing the error supplied to the called on xsl:message, or its default of err:XTM9000.</param>
        /// <param name="terminate">Set to true if <c>terminate ='yes'</c> was specified or to false otherwise</param>
        /// <param name="location">an object that contains the location of the <c>xsl:message</c> This provides access to the URI of the stylesheet
        /// module and the line number of the <c>xsl:message</c></param>

        /**public**/
        void Message(XdmNode content, QName errorCode,  bool terminate, IXmlLocation location);

    }

    /// <summary>
    /// An <c>IXmlLocation</c> represents the location of a node within an XML document.
    /// It is in two parts: the base URI (or system ID) of the external entity (which will usually
    /// be the XML document entity itself), and the line number of a node relative
    /// to the base URI of the containing external entity.
    /// </summary>

    public interface IXmlLocation
    {

        /// <summary>
        /// The base URI (system ID) of an external entity within an XML document.
        /// Set to null if the base URI is not known (for example, for an XML document
        /// created programmatically where no base URI has been set up).
        /// </summary>

        /**public**/
        Uri BaseUri { get; set; }

        /// <summary>
        /// The line number of a node relative to the start of the external entity.
        /// The value -1 indicates that the line number is not known or not applicable.
        /// </summary>

        /**public**/
        int LineNumber { get; set; }
    }


    /// <summary>
	/// Xml location. An implementation of <c>IXmlLocation</c>.
    /// </summary>
    internal class XmlLocation : IXmlLocation
    {
        private Uri baseUri;
        private int lineNumber;
        public Uri BaseUri
        {
            get { return baseUri; }
            set { baseUri = value; }
        }
        public int LineNumber
        {
            get { return lineNumber; }
            set { lineNumber = value; }
        }
    }


    /// <summary>
    /// Message listener proxy. This class implements a <c>net.sf.saxon.s9api.MessageListener</c> that can receive 
    /// <c>xsl:message</c> output and send it to a user-supplied <c>MessageListener</c>
    /// </summary>
    [Serializable]
    internal class MessageListenerProxy : net.sf.saxon.s9api.MessageListener
    {

        public IMessageListener listener;

        /// <summary>
        /// Initializes a new instance of the <see cref="Saxon.Api.MessageListenerProxy"/> class.
        /// </summary>
        /// <param name="ml">ml.</param>
        public MessageListenerProxy(IMessageListener ml)
        {
            listener = ml;
        }


        public void message(net.sf.saxon.s9api.XdmNode xn, bool b, SourceLocator sl)
        {
            IXmlLocation location = new XmlLocation();
            location.BaseUri = new Uri(sl.getSystemId());
            location.LineNumber = sl.getLineNumber();
            listener.Message((XdmNode)XdmValue.Wrap(xn.getUnderlyingValue()), b, location);
        }
    }



    /// <summary>
	/// Message listener proxy. This class implements a <c>net.sf.saxon.s9api.MessageListener2</c> that can receive 
	/// <c>xsl:message</c> output and send it to a user-supplied <c>MessageListener</c>
    /// </summary>
    [Serializable]
    internal class MessageListenerProxy2 : net.sf.saxon.s9api.MessageListener2
    {

        public IMessageListener2 listener;

        /// <summary>
        /// Initializes a new instance of the <see cref="Saxon.Api.MessageListenerProxy"/> class.
        /// </summary>
        /// <param name="ml">ml.</param>
        public MessageListenerProxy2(IMessageListener2 ml)
        {
            listener = ml;
        }


        public void message(net.sf.saxon.s9api.XdmNode xn, net.sf.saxon.s9api.QName qn, bool b, SourceLocator sl)
        {
            IXmlLocation location = new XmlLocation();
            location.BaseUri = new Uri(sl.getSystemId());
            location.LineNumber = sl.getLineNumber();
            listener.Message((XdmNode)XdmValue.Wrap(xn.getUnderlyingValue()), new QName(qn),b, location);
        }
    }



    /// <summary>An <code>Xslt30Transformer</code> represents a compiled and loaded stylesheet ready for execution.
    /// The <code>Xslt30Transformer</code> holds details of the dynamic evaluation context for the stylesheet.</summary>
    /// <remarks><para>The <code>Xslt30Transformer</code> differs from <see cref="XsltTransformer"/> 
    /// in supporting new options
    /// for invoking a stylesheet, corresponding to facilities defined in the XSLT 3.0 specification. However,
    /// it is not confined to use with XSLT 3.0, and most of the new invocation facilities (for example,
    /// calling a stylesheet-defined function directly) work equally well with XSLT 2.0 and in some cases
    /// XSLT 1.0 stylesheets.</para>
    /// <para>An <code>Xslt30Transformer</code> must not be used concurrently in multiple threads.
    /// It is safe, however, to reuse the object within a single thread to run the same
    /// stylesheet several times. Running the stylesheet does not change the context
    /// that has been established.</para>
    /// <para>An <code>Xslt30Transformer</code> is always constructed by running the <code>Load30</code>
    /// method of an <see cref="XsltExecutable"/>.</para>
    /// <para>Unlike <code>XsltTransformer</code>, an <code>Xslt30Transformer</code> is not a <code>Destination</code>.
    /// To pipe the results of one transformation into another, the target should be an <code>XsltTransfomer</code>
    /// rather than an <code>Xslt30Transformer</code>.</para>
    /// <para>Evaluation of an Xslt30Transformer proceeds in a number of phases:</para>
    /// <list type="number">
    /// <item>First, values may be supplied for stylesheet parameters and for the global context item. The
    /// global context item is used when initializing global variables. Unlike earlier transformation APIs,
    /// the global context item is quite independent of the "principal source document".
    /// </item>
    /// <item>The stylesheet may now be repeatedly invoked. Each invocation takes
    /// one of three forms:
    /// <list type="number">
    /// <item>Invocation by applying templates. In this case, the information required is (i) an initial
    /// mode (which defaults to the unnamed mode), (ii) an initial match sequence, which is any
    /// XDM value, which is used as the effective "select" expression of the implicit apply-templates
    /// call, and (iii) optionally, values for the tunnel and non-tunnel parameters defined on the
    /// templates that get invoked (equivalent to using <code>xsl:with-param</code> on the implicit
    /// <code>apply-templates</code> call).</item>
    /// <item>Invocation by calling a named template. In this case, the information required is
    /// (i) the name of the initial template (which defaults to "xsl:initial-template"), and
    /// (ii) optionally, values for the tunnel and non-tunnel parameters defined on the
    /// templates that get invoked (equivalent to using <code>xsl:with-param</code> on the implicit
    /// <code>call-template</code> instruction).</item>
    /// <item>Invocation by calling a named function. In this case, the information required is
    /// the sequence of arguments to the function call.</item>
    /// </list>
    /// </item>
    /// <item>Whichever invocation method is chosen, the result may either be returned directly, as an arbitrary
    /// XDM value, or it may effectively be wrapped in an XML document. If it is wrapped in an XML document,
    /// that document can be processed in a number of ways, for example it can be materialized as a tree in
    /// memory, it can be serialized as XML or HTML, or it can be subjected to further transformation.</item>
    /// </list>
    /// <para>Once the stylesheet has been invoked (using any of these methods), the values of the global context
    /// item and stylesheet parameters cannot be changed. If it is necessary to run another transformation with
    /// a different context item or different stylesheet parameters, a new <c>Xslt30Transformer</c>
    /// should be created from the original <c>XsltExecutable</c>.</para>
    /// <para> @since 9.6</para> 
    /// </remarks>

    [Serializable]
    public class Xslt30Transformer
    {


        private IResultDocumentHandler resultDocumentHandler;
        private StandardLogger traceFunctionDestination;
        private IMessageListener messageListener;
        private IMessageListener2 messageListener2;
        private JXslt30Transformer transformer;

		// internal constructor

        internal Xslt30Transformer(JXslt30Transformer transformer)
        {

            this.transformer = transformer;
            //this.globalParameterSet = new GlobalParameterSet(globalParameters);
        }


        /// <summary> Supply the context item to be used when evaluating global variables and parameters.
        /// This argument can be null if no context item is to be supplied.</summary>

        public XdmItem GlobalContextItem
        {
            set
            {
               transformer.setGlobalContextItem(value == null ? null : XdmItem.FromXdmItemItemToJXdmItem(value));
            }
         
            get { return (XdmItem)XdmItem.Wrap(transformer.getUnderlyingController().getGlobalContextItem()); }

        }


		/// <summary> Get the underlying <c>Controller</c> used to implement this <c>XsltTransformer</c>. This provides access
        /// to lower-level methods not otherwise available in the Saxon.Api interface. Note that classes
        /// and methods obtained by this route cannot be guaranteed stable from release to release.</summary>

        public JXsltController GetUnderlyingController
        {
            get { return transformer.getUnderlyingController(); }
        }

        internal JXslt30Transformer GetUnderlyingXslt30Transformer
        {
            get { return transformer; }
        }


        /// <summary>
		/// Construct a <c>Destination</c> object whose effect is to perform this transformation
		/// on any input that is sent to that <c>Destination</c>: for example, it allows this transformation
		/// to post-process the results of another transformation.
		/// </summary>
		/// <remarks>
        /// <para>This method allows a pipeline of transformations to be created in which
        /// one transformation is used as the destination of another. The transformations
        /// may use streaming, in which case intermediate results will not be materialized
        /// in memory. If a transformation does not use streaming, then its input will
        /// first be assembled in memory as a node tree.</para>
		/// <para>The <c>Destination</c> returned by this method performs <em>sequence normalization</em>
        /// as defined in the serialization specification: that is, the raw result of the transformation
        /// sent to this destination is wrapped into a document node. Any item-separator present in
        /// any serialization parameters is ignored (adjacent atomic values are separated by whitespace). 
        /// This makes the method unsuitable for passing intermediate results other than XML document
        /// nodes.</para>
		/// </remarks>
        /// <param name="finalDestination">supplied final destination</param>
		/// <returns>a <c>Destination</c> which accepts an XML document (typically as a stream
        /// of events) and which transforms this supplied XML document (possibly using streaming)
		/// as defined by the stylesheet from which which this <c>Xslt30Transformer</c>
        /// was generated,
		/// sending the principal result of the transformation to the supplied <c>finalDestination</c>.
		/// The transformation is performed as if by the <see cref="ApplyTemplates(Stream, XmlDestination)"/>
        /// method: that is, by applying templates to the root node of the supplied XML document.
		/// </returns>
        public XmlDestination AsDocumentDestination(XmlDestination finalDestination) {

            AbstractDestination localDestination = new AbstractDestination(this, finalDestination);
            return localDestination;
        }

		// internal method
        internal JReceiver GetDestinationReceiver(XmlDestination destination)
        {
            JReceiver r = transformer.getDestinationReceiver(transformer.getUnderlyingController(), destination.GetUnderlyingDestination()); 
            return r;
        }



        /// <summary>
        /// The <c>IResultDocumentHandler</c> to be used at run-time to process the output
        /// produced by any <c>xsl:result-document</c> instruction with an <c>href</c>
        /// attribute.
        /// </summary>
        /// <remarks>
        /// In the absence of a user-supplied result document handler, the <c>href</c>
        /// attribute of the <c>xsl:result-document</c> instruction must be a valid relative
        /// URI, which is resolved against the value of the <c>BaseOutputUri</c> property,
        /// and the resulting absolute URI must identify a writable resource (typically
        /// a file in filestore, using the <c>file:</c> URI scheme).
        /// </remarks>

        public IResultDocumentHandler ResultDocumentHandler
        {
            get
            {
                return resultDocumentHandler;
            }
            set
            {
                resultDocumentHandler = value;
                transformer.getUnderlyingController().setResultDocumentResolver(new ResultDocumentHandlerWrapper(value, transformer.getUnderlyingController().makePipelineConfiguration()));
            }
        }

        /// <summary>
        /// The <c>SchemaValidationMode</c> to be used in this transformation, especially for documents
        /// loaded using the <code>doc()</code>, <code>document()</code>, or <code>collection()</code> functions.
        /// </summary>

        public SchemaValidationMode SchemaValidationMode
        {
            get
            {
                switch (transformer.getUnderlyingController().getSchemaValidationMode())
                {
                    case JValidation.STRICT:
                        return SchemaValidationMode.Strict;
                    case JValidation.LAX:
                        return SchemaValidationMode.Lax;
                    case JValidation.STRIP:
                        return SchemaValidationMode.None;
                    case JValidation.PRESERVE:
                        return SchemaValidationMode.Preserve;
                    case JValidation.DEFAULT:
                    default:
                        return SchemaValidationMode.Unspecified;
                }
            }


            set
            {
                switch (value)
                {
                    case SchemaValidationMode.Strict:
                        transformer.setSchemaValidationMode(JValidationMode.STRICT);
                        break;
                    case SchemaValidationMode.Lax:
                        transformer.setSchemaValidationMode(JValidationMode.LAX);
                        break;
                    case SchemaValidationMode.None:
                        transformer.setSchemaValidationMode(JValidationMode.STRIP);
                        break;
                    case SchemaValidationMode.Preserve:
                        transformer.setSchemaValidationMode(JValidationMode.PRESERVE);
                        break;
                    case SchemaValidationMode.Unspecified:
                    default:
                        transformer.setSchemaValidationMode(JValidationMode.DEFAULT);
                        break;
                }
            }
        }



        /// <summary> Supply the values of global stylesheet variables and parameters.</summary>
		/// <param name="parameters"> A <c>Dictionary</c> whose keys are QNames identifying global stylesheet parameters,
        /// and whose corresponding values are the values to be assigned to those parameters. If necessary
        /// the supplied values are converted to the declared type of the parameter.
		/// The contents of the supplied <c>Dictionary</c> are copied by this method,
		/// so subsequent changes to the <c>Dictionary</c> have no effect.</param>
        
        public void SetStylesheetParameters(Dictionary<QName, XdmValue> parameters)
        {
            try
            {
                JMap map = new java.util.HashMap();
                foreach (KeyValuePair<QName, XdmValue> entry in parameters)
                {
                    QName qname = entry.Key;
                    map.put(qname.UnderlyingQName(), XdmValue.FromGroundedValueToJXdmValue(entry.Value.value));
                }
                transformer.setStylesheetParameters(map);

            }
            catch (net.sf.saxon.trans.XPathException e)
            {
                throw new StaticError(e);
            }

        }



        /// <summary>Get the base output URI.</summary>
        /// <remarks><para> This returns the value set using the setter method. If no value has been set
        /// explicitly, then the method returns null if called before the transformation, or the computed
        /// default base output URI if called after the transformation.
        /// </para>
        /// <para> The base output URI is used for resolving relative URIs in the <code>href</code> attribute
        /// of the <code>xsl:result-document</code> instruction.</para></remarks>
        /// <returns> The base output URI</returns>

        public String BaseOutputURI
        {
            set
            {
                transformer.setBaseOutputURI(value);
            }
            get { return transformer.getBaseOutputURI(); }
        }

        /// <summary>
        /// The <c>XmlResolver</c> to be used at run-time to resolve and dereference URIs
        /// supplied to the <c>doc()</c> and <c>document()</c> functions.
        /// </summary>

        public XmlResolver InputXmlResolver
        {
            get
            {
                return ((JDotNetURIResolver)transformer.getURIResolver()).getXmlResolver();
            }
            set
            {
                transformer.setURIResolver(new JDotNetURIResolver(value));
            }
        }

        /// <summary>
        /// Ask whether assertions (<c>xsl:assert</c> instructions) have been enabled at run time. 
        /// </summary>
        /// <remarks>By default they are disabled at compile time. If assertions are enabled at compile time, then by
        /// default they will also be enabled at run time; but they can be disabled at run time by
        /// specific request. At compile time, assertions can be enabled for some packages and
        /// disabled for others; at run time, they can only be enabled or disabled globally.</remarks>
        /// <returns>true if assertions are enabled at run time</returns>
        /// <remarks>Since 9.7</remarks>
 
        public bool AssertionsEnabled
        {
            get
            {
                return transformer.isAssertionsEnabled();
            }
        }


        /// <summary>
        /// Ask whether assertions (<c>xsl:assert</c> instructions) have been enabled at run time.
        /// This property name has been misspelt, use <c>AssertionsEnabled</c> instead.
        /// </summary>
        [Obsolete("This property has been replaced by AssertionsEnabled.")]
        public bool ssAssertionsEnabled
        {
            get
            {
                return transformer.isAssertionsEnabled();
            }
        }


        /// <summary>
		/// Listener for messages output using <c>&lt;xsl:message&gt;</c>.
		/// </summary>
		/// <remarks> 
        /// <para>The caller may supply a message listener before calling <c>Run</c>;
        /// the processor will then invoke the listener once for each message generated during
        /// the transformation. Each message will be output as an object of type <c>XdmNode</c>
        /// representing a document node.</para>
        /// <para>If no message listener is supplied by the caller, message information will be written to
        /// the standard error stream.</para>
        /// <para>Each message is presented as an XML document node. Calling <c>ToString()</c>
        /// on the message object will usually generate an acceptable representation of the
        /// message.</para>
		/// <para>When the <c>xsl:message</c> instruction specifies <c>terminate="yes"</c>,
        /// the message is first notified using this interface, and then an exception is thrown
        /// which terminates the transformation.</para>
        /// </remarks>

        public IMessageListener MessageListener
        {
            set
            {
                messageListener = value;
                transformer.setMessageListener(new MessageListenerProxy(value));
            }
            get
            {
                return messageListener;
            }
        }



        /// <summary>
        /// Listener for messages output using <c>&lt;xsl:message&gt;</c>.
        /// </summary>
        /// <remarks> 
        /// <para>The caller may supply a message listener before calling <c>Run</c>;
        /// the processor will then invoke the listener once for each message generated during
        /// the transformation. Each message will be output as an object of type <c>XdmNode</c>
        /// representing a document node.</para>
        /// <para>If no message listener is supplied by the caller, message information will be written to
        /// the standard error stream.</para>
        /// <para>Each message is presented as an XML document node. Calling <c>ToString()</c>
        /// on the message object will usually generate an acceptable representation of the
        /// message.</para>
        /// <para>When the <c>xsl:message</c> instruction specifies <c>terminate="yes"</c>,
        /// the message is first notified using this interface, and then an exception is thrown
        /// which terminates the transformation.</para>
        /// <para>The <c>MessageListener2</c> property interface differs from the <c>MessageListener</c>
        /// in allowing the error code supplied to xsl:message to be notified</para>
        /// </remarks>

        public IMessageListener2 MessageListener2
        {
            set
            {
                messageListener2 = value;
                transformer.setMessageListener(new MessageListenerProxy2(value));
            }
            get
            {
                return messageListener2;
            }
        }

        /// <summary>
        /// Destination for output of messages using the <c>trace()</c> function. 
        /// </summary>
		/// <remarks>
		/// <para>If no message listener is supplied by the caller, message information will be written to
		/// the standard error stream.</para>
        /// <para>The supplied destination is ignored if a <code>TraceListener</code> is in use.</para>
        /// </remarks>

        public StandardLogger TraceFunctionDestination
        {
            set
            {
                traceFunctionDestination = value;
                transformer.setTraceFunctionDestination(value);
            }
            get
            {
                return traceFunctionDestination;
            }
        }


        /// <summary>Set parameters to be passed to the initial template. These are used
        /// whether the transformation is invoked by applying templates to an initial source item,
        /// or by invoking a named template. The parameters in question are the <c>xsl:param</c> elements
        /// appearing as children of the <c>xsl:template</c> element.
		/// </summary>
        /// <remarks>
        /// <para>The parameters are supplied in the form of a map; the key is a <c>QName</c> which must
        /// match the name of the parameter; the associated value is an <c>XdmValue</c> containing the
        /// value to be used for the parameter. If the initial template defines any required
        /// parameters, the map must include a corresponding value. If the initial template defines
        /// any parameters that are not present in the map, the default value is used. If the map
        /// contains any parameters that are not defined in the initial template, these values
        /// are silently ignored.</para>
        /// <para>The supplied values are converted to the required type using the function conversion
        /// rules. If conversion is not possible, a run-time error occurs (not now, but later, when
        /// the transformation is actually run).</para>
        /// <para>The <code>Xslt30Transformer</code> retains a reference to the supplied map, so parameters can be added or
        /// changed until the point where the transformation is run.</para>
        /// <para>The XSLT 3.0 specification makes provision for supplying parameters to the initial
        /// template, as well as global stylesheet parameters. Although there is no similar provision
        /// in the XSLT 1.0 or 2.0 specifications, this method works for all stylesheets, regardless whether
        /// XSLT 3.0 is enabled or not.</para></remarks>

        /// <param name="parameters"> The parameters to be used for the initial template</param>
        /// <param name="tunnel"> true if these values are to be used for setting tunnel parameters;
        /// false if they are to be used for non-tunnel parameters</param>
        
        public void SetInitialTemplateParameters(Dictionary<QName, XdmValue> parameters, bool tunnel)
        {

            JMap templateParameters = new java.util.HashMap();
            foreach (KeyValuePair<QName, XdmValue> entry in parameters)
            {
                QName qname = entry.Key;
                templateParameters.put(qname.UnderlyingQName(), XdmValue.FromGroundedValueToJXdmValue(entry.Value.value));
            }

            transformer.setInitialTemplateParameters(templateParameters, tunnel);


        }


        /// <summary>Initial mode for the transformation. This is used if the stylesheet is
        /// subsequently invoked by any of the <code>applyTemplates</code> methods.</summary>
        /// <remarks><para>The value may be the name of the initial mode, or null to indicate the default
        /// (unnamed) mode</para></remarks>

        public QName InitialMode
        {

            set
            {
                try
                {
                    transformer.setInitialMode(value == null ? null : value.UnderlyingQName());
                }
                catch (net.sf.saxon.trans.XPathException e)
                {
                    throw new DynamicError(e);
                }
            }
            get
            {
                net.sf.saxon.s9api.QName mode = transformer.getInitialMode();
                if (mode == null)
                    return null;
                else
                {
                    return new QName(mode);
                }
            }


        }


        /// <summary>Invoke the stylesheet by applying templates to a supplied source document, 
		/// sending the results (wrapped in a document node) to a given <c>Destination</c>. The 
		/// invocation uses any initial mode set using <see cref="InitialMode"/>,
        /// and any template parameters set using <see cref="SetInitialTemplateParameters"/>.
        /// </summary>
        /// <param name="input">The source document. To apply more than one transformation to the same source 
		/// document, the source document tree can be pre-built using a <see cref="DocumentBuilder"/>.</param>
        /// <param name="destination">The destination of the result document produced by wrapping the result 
		/// of the apply-templates call in a document node.  If the destination is a <see cref="Serializer"/>, 
		/// then the serialization parameters set in the serializer are combined with those defined in the 
		/// stylesheet (the parameters set in the serializer take precedence).</param>

        public void ApplyTemplates(Stream input, XmlDestination destination)
        {
            try
            {
                JStreamSource streamSource = new JStreamSource(new JDotNetInputStream(input));
                transformer.applyTemplates(streamSource, destination.GetUnderlyingDestination());
            
            }
            catch (JSaxonApiException exp)
            {
                throw new DynamicError(exp);
            }

        }


		/// <summary>Invoke the stylesheet by applying templates to a supplied source document, 
		/// using the supplied base URI,
		/// sending the results (wrapped in a document node) to a given <c>Destination</c>. The 
		/// invocation uses any initial mode set using <see cref="InitialMode"/>,
		/// and any template parameters set using <see cref="SetInitialTemplateParameters"/>.
		/// </summary>
		/// <param name="input">The source document. To apply more than one transformation to the same source 
		/// document, the source document tree can be pre-built using a <see cref="DocumentBuilder"/>.</param>
		/// <param name="baseUri">Base URI used for the input document</param>
		/// <param name="destination">The destination of the result document produced by wrapping the result 
		/// of the apply-templates call in a document node.  If the destination is a <see cref="Serializer"/>, 
		/// then the serialization parameters set in the serializer are combined with those defined in the 
		/// stylesheet (the parameters set in the serializer take precedence).</param>

        public void ApplyTemplates(Stream input, Uri baseUri, XmlDestination destination)
        {
            JStreamSource streamSource = new JStreamSource(new JDotNetInputStream(input), Uri.EscapeUriString(baseUri.ToString()));
            try
            {
                transformer.applyTemplates(streamSource, destination.GetUnderlyingDestination());
            }
            catch (JSaxonApiException exp)
            {
                throw new DynamicError(exp.getMessage());
            }

        }


        /// <summary>
        /// Invoke the stylesheet by applying templates to a supplied source document, sending the results
        /// to a given <c>Destination</c>. The invocation uses the initial mode set using <see cref="InitialMode"/>
        /// (defaulting to the default mode defined in the stylesheet itself, which by default is the unnamed mode).
        /// It also uses any template parameters set using <see cref="SetInitialTemplateParameters"/>.
        /// </summary>
        /// <param name="input">The source document. To apply more than one transformation to the same source 
        /// document, the source document tree can be pre-built using a <see cref="DocumentBuilder"/>.</param>
        /// <param name="destination">The destination of the principal result of the transformation.
        /// If the destination is a <see cref="Serializer"/>, then the serialization
        /// parameters set in the serializer are combined with those defined in the stylesheet
        /// (the parameters set in the serializer take precedence).</param>
        /// <remarks>since 9.9.1.5</remarks>
        public void Transform(Stream input, XmlDestination destination) {
            JStreamSource streamSource = new JStreamSource(new JDotNetInputStream(input));

            try
            {
                transformer.transform(streamSource, destination.GetUnderlyingDestination());
            }
            catch (JSaxonApiException exp)
            {
                throw new DynamicError(exp.getMessage());
            }

        }

        /// <summary>
        /// Invoke the stylesheet by applying templates to a supplied Source document,  
        /// using the supplied base URI, sending the results
        /// to a given <c>Destination</c>. The invocation uses the initial mode set using <see cref="InitialMode"/>
        /// (defaulting to the default mode defined in the stylesheet itself, which by default is the unnamed mode).
        /// It also uses any template parameters set using <see cref="SetInitialTemplateParameters"/>.
        /// </summary>
        /// <param name="input">The source document. To apply more than one transformation to the same source 
        /// document, the source document tree can be pre-built using a <see cref="DocumentBuilder"/>.</param>
        /// <param name="baseUri">Base URI used for the input document</param>
        /// <param name="destination">The destination of the principal result of the transformation.
        /// If the destination is a <see cref="Serializer"/>, then the serialization
        /// parameters set in the serializer are combined with those defined in the stylesheet
        /// (the parameters set in the serializer take precedence).</param>
        /// <remarks>since 9.9.1.5</remarks>
        public void Transform(Stream input, Uri baseUri, XmlDestination destination)
        {
            JStreamSource streamSource = new JStreamSource(new JDotNetInputStream(input), Uri.EscapeUriString(baseUri.ToString()));
            try
            {
                transformer.transform(streamSource, destination.GetUnderlyingDestination());
            }
            catch (JSaxonApiException exp)
            {
                throw new DynamicError(exp.getMessage());
            }

        }




        /// <summary>Invoke the stylesheet by applying templates to a supplied source document, 
		/// using the supplied base URI,
		/// returning the raw results as an <c>XdmValue</c>. The 
		/// invocation uses any initial mode set using <see cref="InitialMode"/>,
		/// and any template parameters set using <see cref="SetInitialTemplateParameters"/>.
		/// </summary>
		/// <param name="input">The source document. To apply more than one transformation to the same source 
		/// document, the source document tree can be pre-built using a <see cref="DocumentBuilder"/>.</param>
		/// <param name="baseUri">Base URI</param>
		/// <returns>the raw result of applying templates to the supplied selection value, without wrapping in
        /// a document node or serializing the result. If there is more than one item in the selection, the result
        /// is the concatenation of the results of applying templates to each item in turn.</returns>

        public XdmValue ApplyTemplates(Stream input, Uri baseUri)
        {
            JStreamSource streamSource = new JStreamSource(new JDotNetInputStream(input), Uri.EscapeUriString(baseUri.ToString()));

            try
            {
                net.sf.saxon.s9api.XdmValue value = transformer.applyTemplates(streamSource);
                return XdmValue.Wrap(value.getUnderlyingValue());

            }
            catch (JSaxonApiException exp)
            {
                throw new DynamicError(exp);
            }

        }


        /// <summary>
        /// Invoke the stylesheet by applying templates to a supplied input sequence, sending the results (wrapped
		/// in a document node) to a given <c>Destination</c>. The invocation uses any initial mode set using 
		/// <see cref="InitialMode"/>, and any template parameters set using <see cref="SetInitialTemplateParameters"/>.
        /// </summary>
        /// <param name="selection">The initial value to which templates are to be applied (equivalent to the <code>select</code>
        /// attribute of <code>xsl:apply-templates</code>)</param>
        /// <param name="destination">The destination of the result document produced by wrapping the result of the apply-templates
        /// call in a document node.  If the destination is a <see cref="Serializer"/>, then the serialization
        /// parameters set in the serializer are combined with those defined in the stylesheet
        /// (the parameters set in the serializer take precedence).</param>

        public void ApplyTemplates(XdmValue selection, XmlDestination destination)
        {
            
            try
            {
                transformer.applyTemplates(selection == null ? null : XdmValue.FromGroundedValueToJXdmValue(selection.value), destination.GetUnderlyingDestination());
            }
            catch (JSaxonApiException ex)
            {

                throw new DynamicError(ex);
            }

        }



        /// <summary>
        /// Invoke the stylesheet by applying templates to a supplied input sequence, returning the raw results
        /// as an <see cref="XdmValue"/>. The invocation uses any initial mode set using <see cref="InitialMode"/>,
        /// and any template parameters set using <see cref="SetInitialTemplateParameters"/>.
        /// </summary>
        /// <param name="selection">The initial value to which templates are to be applied (equivalent to the <code>select</code>
        /// attribute of <code>xsl:apply-templates</code>)</param>
        /// <returns>the raw result of applying templates to the supplied selection value, without wrapping in
        /// a document node or serializing the result. If there is more than one item in the selection, the result
        /// is the concatenation of the results of applying templates to each item in turn.</returns>

        public XdmValue ApplyTemplates(XdmValue selection)
        {
           
            try
            {
                return XdmValue.Wrap(transformer.applyTemplates(selection == null ? null : XdmValue.FromGroundedValueToJXdmValue(selection.value)).getUnderlyingValue());
               
            }
            catch (JSaxonApiException ex)
            {

                throw new DynamicError(ex);
            }

        }


        /// <summary> Invoke a transformation by calling a named template. The results of calling
        /// the template are wrapped in a document node, which is then sent to the specified
        /// destination. If <see cref="SetInitialTemplateParameters"/> has been
        /// called, then the parameters supplied are made available to the called template (no error
        /// occurs if parameters are supplied that are not used).</summary> 
        /// <param name="templateName"> The name of the initial template. This must match the name of a
        /// public named template in the stylesheet. If the value is null,
        /// the QName <code>xsl:initial-template</code> is used.</param>
        /// <param name="destination"> The destination of the result document produced by wrapping the result 
		/// of the apply-templates call in a document node.  If the destination is a <see cref="Serializer"/>, 
		/// then the serialization parameters set in the serializer are combined with those defined in the stylesheet
        /// (the parameters set in the serializer take precedence).</param> 

        public void CallTemplate(QName templateName, XmlDestination destination)
        {
           
            if (templateName == null)
            {
                templateName = new QName("xsl", NamespaceConstant.XSLT, "initial-template");
            }

            try
            {
                transformer.callTemplate(templateName.UnderlyingQName(), destination.GetUnderlyingDestination());
               
            }
            catch (JSaxonApiException exp)
            {
                throw new DynamicError(exp);
            }
        }



        /// <summary>
        /// Invoke a transformation by calling a named template. The results of calling
        /// the template are returned as a raw value, without wrapping in a document nnode
        /// or serializing.
        /// </summary>
        /// <param name="templateName">The name of the initial template. This must match the name of a
        /// public named template in the stylesheet. If the value is null, the QName <c>xsl:initial-template</c> is used.</param>
        /// <returns>the raw results of the called template, without wrapping in a document node or serialization.</returns>

        public XdmValue CallTemplate(QName templateName)
        {
            if (templateName == null)
            {
                templateName = new QName("xsl", NamespaceConstant.XSLT, "initial-template");
            }

            try
            {
                return XdmValue.Wrap(transformer.callTemplate(templateName.UnderlyingQName()).getUnderlyingValue());
            }
            catch (JSaxonApiException exp)
            {
                throw new DynamicError(exp);
            }
        }


        /// <summary> Call a public user-defined function in the stylesheet. </summary>
        /// <param name="function"> The name of the function to be called</param>
        /// <param name="arguments">  The values of the arguments to be supplied to the function. These
        /// will be converted if necessary to the type as defined in the function signature, using
        /// the function conversion rules.</param>
        /// <returns> the result of calling the function. This is the raw result, without wrapping in a document
        /// node and without serialization.</returns>

        public XdmValue CallFunction(QName function, XdmValue[] arguments)
        {
            try
            {
                int len = arguments.Length;
                net.sf.saxon.s9api.XdmValue[] values = new net.sf.saxon.s9api.XdmValue[len];

                for (int i=0; i<len; i++) {
                    values[i] = XdmValue.FromGroundedValueToJXdmValue(arguments[i].value);

                }

                return XdmValue.Wrap(transformer.callFunction(function.UnderlyingQName(), values).getUnderlyingValue());

            }
            catch (JSaxonApiException ex)
            {
                throw new DynamicError(ex);

            }

        }



        /// <summary>Call a public user-defined function in the stylesheet, wrapping the result in an XML document, 
		/// and sending this document to a specified destination</summary>    
        /// <param name="function"> The name of the function to be called</param>
        /// <param name="arguments"> The values of the arguments to be supplied to the function. These
        /// will be converted if necessary to the type as defined in the function signature, using
        /// the function conversion rules.</param>
        /// <param name="destination"> The destination of the result document produced by wrapping the 
		/// result of the apply-templates call in a document node.  If the destination is a <see cref="Serializer"/>, 
		/// then the serialization parameters set in the serializer are combined with those defined in the stylesheet
        /// (the parameters set in the serializer take precedence).</param>

        public void CallFunction(QName function, XdmValue[] arguments, XmlDestination destination)
        {

            int len = arguments.Length;
            net.sf.saxon.s9api.XdmValue[] values = new net.sf.saxon.s9api.XdmValue[len];

            for (int i = 0; i < len; i++)
            {
                values[i] = XdmValue.FromGroundedValueToJXdmValue(arguments[i].value);

            }
            try
            {
                transformer.callFunction(function.UnderlyingQName(), values, destination.GetUnderlyingDestination());

            }
            catch (JSaxonApiException ex) {
                throw new DynamicError(ex);
            }
        }


    }

    /// <summary> An <c>XsltPackage</c> object represents the result of compiling an XSLT 3.0 package, as
    /// represented by an XML document containing an <c>xsl:package</c> element.</summary>

    [Serializable]
    public class XsltPackage
    {
        private JXsltPackage package;
        private Processor processor;
		// internal constructor: Initializes a new instance of the <see cref="Saxon.Api.XsltPackage"/> class.
        internal XsltPackage(Processor p, JXsltPackage pp)
        {
            this.package = pp;
            this.processor = p;

        }

        /// <summary>
        /// Get the Processor from which this <c>XsltPackage</c> was constructed
        /// </summary>

        public Processor Processor
        {
            get { return processor; }
        }


        /// <summary>
        /// Get the name of the package (the URI appearing as the value of <code>xsl:package/@name</code>)
        /// </summary>
        /// <returns>The package name</returns>

        public String PackageName
        {
            get { return package.getName(); }
        }


        /// <summary>Get the version number of the package (the value of the attribute 
		/// <code>xsl:package/@package-version</code></summary>
        /// <returns>The package version number</returns>

        public String Version
        {
            get { return package.getVersion(); }
        }


        /// <summary>Link this package with the packages it uses to form an executable stylesheet. This process fixes
        /// up any cross-package references to files, templates, and other components, and checks to ensure
        /// that all such references are consistent.</summary>
		/// <returns> the resulting <c>XsltExecutable</c></returns>

        public XsltExecutable Link()
        {
            try
            {
                JXsltExecutable jexecutable = package.link();
                XsltExecutable executable = new XsltExecutable(jexecutable);
                executable.InternalProcessor = processor;
                return executable;
            }
            catch (net.sf.saxon.trans.XPathException e)
            {
                throw new StaticError(e);
            }
        }

        /// <summary>Save this compiled package to filestore.</summary>
        /// <param name="stream"> the stream to which the compiled package should be saved</param>

        public void Save(Stream stream)
        {
            JDotNetOutputStream outputStream = new JDotNetOutputStream(stream);
            JExpressionPresenter outp = new JExpressionPresenter(package.getProcessor().getUnderlyingConfiguration(), new javax.xml.transform.stream.StreamResult(outputStream), true);
            try
            {
                package.getUnderlyingPreparedPackage().export(outp);
            }
            catch (net.sf.saxon.trans.XPathException e)
            {
                throw new StaticError(e);
            }
        }


        /// <summary>Escape-hatch interface to the underlying implementation class.</summary>
		/// <returns>the underlying <c>StylesheetPackage</c>. The interface to <c>StylesheetPackage</c>
        /// is not a stable part of the s9api API definition.</returns>

        public JStylesheetPackage getUnderlyingPreparedPackage()
        {
            return package.getUnderlyingPreparedPackage();
        }

        internal JXsltPackage GetUnderlyingXsltPackage() {
            return package;
        }
        
    }


}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
