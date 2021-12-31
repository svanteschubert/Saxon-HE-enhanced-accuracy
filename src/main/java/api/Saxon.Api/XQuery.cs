using System;
using System.IO;
using System.Xml;
using System.Collections;
using System.Globalization;
using System.Collections.Generic;
using JConfiguration = net.sf.saxon.Configuration;
using JXQueryEvaluator = net.sf.saxon.s9api.XQueryEvaluator;
using JXQName = net.sf.saxon.s9api.QName;
using JXdmValue = net.sf.saxon.s9api.XdmValue;
using JNodeInfo = net.sf.saxon.om.NodeInfo;
using JSequence = net.sf.saxon.om.Sequence;
using JDynamicQueryContext = net.sf.saxon.query.DynamicQueryContext;
using JDotNetStandardModuleURIResolver = net.sf.saxon.dotnet.DotNetStandardModuleURIResolver;
using JDotNetComparator = net.sf.saxon.dotnet.DotNetComparator;
using JDotNetInputStream = net.sf.saxon.dotnet.DotNetInputStream;
using JDotNetURIResolver = net.sf.saxon.dotnet.DotNetURIResolver;
using JDotNetReader = net.sf.saxon.dotnet.DotNetReader;
using JValidation = net.sf.saxon.lib.Validation;
using JXPathException = net.sf.saxon.trans.XPathException;
using JStreamSource = javax.xml.transform.stream.StreamSource;
using JXQueryCompiler = net.sf.saxon.s9api.XQueryCompiler;
using JXQueryExecutable = net.sf.saxon.s9api.XQueryExecutable;
using JSaxonApiException = net.sf.saxon.s9api.SaxonApiException;

namespace Saxon.Api
{

    /// <summary>
    /// The <c>XQueryCompiler</c> object allows XQuery queries to be compiled.
    /// </summary>
    /// <remarks>
    /// <para>To construct an <c>XQueryCompiler</c>, use the factory method
	/// <c>NewXQueryCompiler</c> on the <see cref="Processor"/> object.</para>
    /// <para>The <c>XQueryCompiler</c> holds information that represents the static context
    /// for the queries that it compiles. This information remains intact after performing
    /// a compilation. An <c>XQueryCompiler</c> may therefore be used repeatedly to compile multiple
    /// queries. Any changes made to the <c>XQueryCompiler</c> (that is, to the
    /// static context) do not affect queries that have already been compiled.</para>
    /// <para>An <c>XQueryCompiler</c> may be used concurrently in multiple threads, but
    /// it should not then be modified once initialized.</para>
    /// </remarks>

    [Serializable]
    public class XQueryCompiler
    {

        private JConfiguration config;
        private Processor processor;
        private IQueryResolver moduleResolver;
        private IList<StaticError> errorList;
        private ErrorReporterToStaticError errorGatherer;
        private ErrorReporter errorReporter;
        private JXQueryCompiler compiler;

        // internal constructor: the public interface is a factory method
        // on the Processor object

        internal XQueryCompiler(Processor processor)
        {
            this.processor = processor;
			this.config = processor.Implementation;
            compiler = processor.JProcessor.newXQueryCompiler();
            compiler.setModuleURIResolver(new JDotNetStandardModuleURIResolver(processor.XmlResolver));
        }

        /// <summary>
        /// Create a collation based on a given <c>CompareInfo</c> and <c>CompareOptions</c>    
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

        public void DeclareCollation(Uri uri, CompareInfo compareInfo, CompareOptions options, Boolean isDefault) {
			JDotNetComparator comparator = new JDotNetComparator(uri.ToString(), compareInfo, options);
			config.registerCollation(uri.ToString(), comparator);
            if (isDefault) {
                compiler.declareDefaultCollation(uri.ToString());
            }
        }


        /// <summary>
        /// Declare a namespace for use by the query. This has the same
        /// status as a namespace appearing within the query prolog (though
        /// a declaration in the query prolog of the same prefix will take
        /// precedence).
        /// </summary>
        /// <param name="prefix">The namespace prefix to be declared. Use
        /// a zero-length string to declare the default namespace (that is, the
        /// default namespace for elements and types).</param>
        /// <param name="uri">The namespace URI. It is possible to specify
        /// a zero-length string to "undeclare" a namespace.</param>

        public void DeclareNamespace(String prefix, String uri)
        {
            compiler.declareNamespace(prefix, uri);
        }


        /// <summary>
		/// Get the <c>Processor</c> from which this <c>XQueryCompiler</c> was constructed
        /// </summary>
        
		public Processor Processor
        {
            get {return processor;}
        }

        /// <summary>
        /// The required context item type for the expression. This is used for
        /// optimizing the expression at compile time, and to check at run-time
        /// that the value supplied for the context item is the correct type.
        /// </summary>

        public XdmItemType ContextItemType
        {
            get { return XdmItemType.MakeXdmItemType(compiler.getRequiredContextItemType().getUnderlyingItemType()); }
            set { compiler.setRequiredContextItemType(value.Unwrap()); }
        }

        /// <summary>
        /// The base URI of the query, which forms part of the static context
        /// of the query. This is used for resolving any relative URIs appearing
        /// within the query, for example in references to library modules, schema
        /// locations, or as an argument to the <c>doc()</c> function.
        /// </summary>


        public String BaseUri
        {
            get { return compiler.getBaseURI().toASCIIString(); }
            set { compiler.setBaseURI(new java.net.URI(value)); }
        }


        /// <summary>
        /// Say that the query must be compiled to be schema-aware, even if it contains no
        /// "import schema" declarations. Normally a query is treated as schema-aware
		/// only if it contains one or more "import schema" declarations. 
		/// </summary>
		/// <remarks>
		/// <para>If the query is not schema-aware, then all input documents must be untyped 
		/// (or <c>xs:anyType</c>), and validation of temporary nodes is disallowed
        /// (though validation of the final result tree is permitted). Setting the argument to true
		/// means that schema-aware code will be compiled regardless.</para>
		/// </remarks>

        public Boolean SchemaAware
        {
            get
            {
                return compiler.isSchemaAware();
            }
            set
            {
                compiler.setSchemaAware(value);
            }
        }

        /// <summary>
        /// This property indicates whether XQuery Update syntax is accepted. The default
        /// value is false. This property must be set to true before compiling a query that
        /// uses update syntax.
        /// </summary>
        /// <remarks>
        /// <para>This propery must be set to true before any query can be compiled
        /// that uses updating syntax. This applies even if the query is not actually an updating
        /// query (for example, a copy-modify expression). XQuery Update syntax is accepted
        /// only by Saxon-EE. Non-updating queries are accepted regardless of the value of this
        /// property.</para>
		/// </remarks>


        public bool UpdatingEnabled
        {
            get { return compiler.isUpdatingEnabled(); }
            set { compiler.setUpdatingEnabled(value); }
        }

        /// <summary>
        /// This property indicates which version of XQuery language syntax is accepted. In this version
        /// of Saxon the version is always "3.1"; any attempt to set a different value is ignored.
        /// </summary>

        public string XQueryLanguageVersion
        {
			get { 
				return "3.1";
			}
            set {}
        }


        /// <summary>
        /// A user-supplied <c>IQueryResolver</c> used to resolve location hints appearing in an
        /// <c>import module</c> declaration.
        /// </summary>
        /// <remarks>
        /// <para>In the absence of a user-supplied <c>QueryResolver</c>, an <c>import module</c> declaration
        /// is interpreted as follows. First, if the module URI identifies an already loaded module, that module
        /// is used and the location hints are ignored. Otherwise, each URI listed in the location hints is
        /// resolved using the <c>XmlResolver</c> registered with the <c>Processor</c>.</para>
        /// </remarks>

        public IQueryResolver QueryResolver
        {
            get { return moduleResolver; }
            set
            {
                moduleResolver = value;
                compiler.setModuleURIResolver(new DotNetModuleURIResolver(value));
            }
        }

        /// <summary>
        /// List of errors. The caller should supply an empty list before calling <c>Compile()</c>;
        /// the processor will then populate the list with error information obtained during
        /// the compilation. Each error will be included as an object of type <c>StaticError</c>.
        /// If no error list is supplied by the caller, error information will be written to
        /// the standard error stream.
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
                compiler.setErrorReporter(errorGatherer);
            }
            get
            {
                return errorList;
            }
        }


        /// <summary>Set the <c>ErrorReporter</c> to be used when validating instance documents as a user defined IErrorReporter.
        ///  Requirements here is IErrorReport implementation with a user-written <c>report()</c> method, it is possible to
        /// intercept error conditions as they occur.
        /// If this property is used then the ErrorList property and SetErrorList method is overriden.</summary>
        /// <remarks>The <c>IErrorReporter</c> to be used</remarks>
        public IErrorReporter ErrorReporter
        {
            set
            {
                errorReporter = null;
                compiler.setErrorReporter(new ErrorReporterWrapper(value));
            }

        }

        /// <summary>
        /// List of errors. The caller may supply an empty list before calling <c>Compile</c>;
        /// the processor will then populate the list with error information obtained during
        /// the query compilation. Each error will be included as an object of type <c>XmlProcessingError</c>.
        /// If no error list is supplied by the caller, error information will be written to
        /// the standard error stream.
        /// </summary>
		/// <remarks>
		/// <para>By supplying a custom <c>List</c> with a user-written <c>add()</c> method, it is possible to
		/// intercept error conditions as they occur.</para>
        /// <para>Note that this error list is used only for errors detected during the compilation
        /// of the stylesheet. It is not used for errors detected when executing the stylesheet.</para>
		/// </remarks>
		/// <param name="value">Supplied list.</param>
        public void SetErrorList(IList<XmlProcessingError> value)
        {
            errorReporter = new ErrorReporter(value);
            compiler.setErrorReporter(errorReporter);
        }

		/// <summary>
		/// Get list of errors as <code>IList&lt;XmlProcessingError&gt;</code>
		/// </summary>
        public IList<XmlProcessingError> GetErrorList()
        {
            if(errorReporter == null)
            {
                return new List<XmlProcessingError>();
            }
            return errorReporter.ErrorList;

        }

        /// <summary>
		/// Compile a query supplied as a <c>Stream</c>.
        /// </summary>
        /// <remarks>
        /// <para>The XQuery processor attempts to deduce the encoding of the query
        /// by looking for a byte-order-mark, or if none is present, by looking
        /// for the encoding declaration in the XQuery version declaration.
        /// For this to work, the stream must have the <c>CanSeek</c> property.
        /// If no encoding information is present, UTF-8 is assumed.</para>
        /// <para>The base URI of the query is set to the value of the <c>BaseUri</c>
        /// property. If this has not been set, then the base URI will be undefined, which
        /// means that any use of an expression that depends on the base URI will cause
        /// an error.</para>
        /// </remarks>
        /// <example>
        /// <code>
        /// XQueryExecutable q = compiler.Compile(new FileStream("input.xq", FileMode.Open, FileAccess.Read));
        /// </code>
        /// </example>
        /// <param name="query">A stream containing the source text of the query</param>
        /// <returns>An <c>XQueryExecutable</c> which represents the compiled query object.
        /// The <c>XQueryExecutable</c> may be run as many times as required, in the same or a different
        /// thread. The <c>XQueryExecutable</c> is not affected by any changes made to the <c>XQueryCompiler</c>
        /// once it has been compiled.</returns>
        /// <exception cref="StaticError">Throws a <c>StaticError</c> if errors were detected
        /// during static analysis of the query. Details of the errors will be added as <c>StaticError</c>
        /// objects to the <c>ErrorList</c> if supplied; otherwise they will be written to the standard
        /// error stream. The exception that is returned is merely a summary indicating the
        /// status.</exception>

        public XQueryExecutable Compile(Stream query)
        {
            try
            {
                JXQueryExecutable exec = compiler.compile(new JDotNetInputStream(query));
                return new XQueryExecutable(exec);
            }
            catch (JSaxonApiException e)
            {
                throw new StaticError(e);
            }
        }

        /// <summary>
		/// Compile a query supplied as a <c>String</c>.
        /// </summary>
        /// <remarks>
        /// Using this method the query processor is provided with a string of Unicode
        /// characters, so no decoding is necessary. Any encoding information present in the
        /// version declaration is therefore ignored.
        /// </remarks>
        /// <example>
        /// <code>
        /// XQueryExecutable q = compiler.Compile("distinct-values(//*/node-name()");
        /// </code>
        /// </example>
        /// <param name="query">A string containing the source text of the query</param>
        /// <returns>An <c>XQueryExecutable</c> which represents the compiled query object.
        /// The <c>XQueryExecutable</c> may be run as many times as required, in the same or a different
        /// thread. The <c>XQueryExecutable</c> is not affected by any changes made to the <c>XQueryCompiler</c>
        /// once it has been compiled.</returns>
        /// <exception cref="StaticError">Throws a <c>StaticError</c> if errors were detected
        /// during static analysis of the query. Details of the errors will be added as <c>StaticError</c>
        /// objects to the <c>ErrorList</c> if supplied; otherwise they will be written to the standard
        /// error stream. The exception that is returned is merely a summary indicating the
        /// status.</exception>        

        public XQueryExecutable Compile(String query)
        {
            try
            {
                JXQueryExecutable exec = compiler.compile(query);
                return new XQueryExecutable(exec);
            }
            catch (JSaxonApiException e)
            {
                throw new StaticError(e);
            }
        }

        /// <summary>
        /// Escape hatch to the underlying Java implementation
        /// </summary>

        public JXQueryCompiler Implementation
        {
            get { return compiler; }
        }


		/// <summary>
		/// Request fast compilation. Fast compilation will generally be achieved at the expense of run-time performance
		/// and quality of diagnostics. Fast compilation is a good trade-off if (a) the expression is known to be correct,
		/// and (b) once compiled, the expression is only executed once against a document of modest size.
		/// </summary>
		/// <remarks>
		/// <para><i>The current implementation is equivalent to switching off all optimizations. Setting this option, however,
		/// indicates an intent rather than a mechanism, and the implementation details may change in future to reflect
		/// the intent.</i></para>
		/// <para>Set to true to request fast compilation; set to false to revert to the optimization options
		/// defined in the Configuration.</para>
		/// </remarks>

		public bool FastCompliation
        {

            set { compiler.setFastCompilation(value); }
            get { return compiler.isFastCompilation(); }
        }
    }



    /// <summary>
    /// An <c>XQueryExecutable</c> represents the compiled form of a query. To execute the query,
    /// it must first be loaded to form an <c>XQueryEvaluator</c>.
    /// </summary>
    /// <remarks>
    /// <para>An <c>XQueryExecutable</c> is immutable, and therefore thread-safe. It is simplest to
    /// load a new <c>XQueryEvaluator</c> each time the query is to be run. However, the 
    /// <c>XQueryEvaluator</c> is serially reusable within a single thread.</para>
    /// <para>An <c>XQueryExecutable</c> is created by using one of the <c>Compile</c>
    /// methods on the <c>XQueryCompiler</c> class.</para>
    /// </remarks>    

    [Serializable]
    public class XQueryExecutable
    {

        private JXQueryExecutable executable;

        // internal constructor

        internal XQueryExecutable(JXQueryExecutable exec)
        {
            this.executable = exec;
        }

        /// <summary>Ask whether this is an updating query (that is, one that returns a pending
        /// update list rather than a conventional value).</summary>

        public bool IsUpdateQuery
        {
            get { return executable.isUpdateQuery(); }
        }
        
        /// <summary>Escape-hatch method to get the underlying Saxon implementation object if required.
        /// This provides access to methods that may not be stable from release to release.</summary>

		public JXQueryExecutable getUnderlyingCompiledQuery(){
			return executable;
		}

        /// <summary>
        /// Load the query to prepare it for execution.
        /// </summary>
        /// <returns>
        /// An <c>XQueryEvaluator</c>. The returned <c>XQueryEvaluator</c> can be used to
        /// set up the dynamic context for query evaluation, and to run the query.
        /// </returns>

        public XQueryEvaluator Load()
        {
            return  new XQueryEvaluator(executable.load());
        }
    }

    /// <summary inherits="IEnumerable">
    /// An <c>XQueryEvaluator</c> represents a compiled and loaded query ready for execution.
    /// The <c>XQueryEvaluator</c> holds details of the dynamic evaluation context for the query.
    /// </summary>
    /// <remarks>
    /// <para>An <c>XQueryEvaluator</c> must not be used concurrently in multiple threads. It is safe,
    /// however, to reuse the object within a single thread to run the same query several times.
    /// Running the query does not change the context that has been established.</para>
    /// <para>An <c>XQueryEvaluator</c> is always constructed by running the <c>Load</c> method of
    /// an <c>XQueryExecutable</c>.</para>
    /// </remarks>     

    [Serializable]
    public class XQueryEvaluator : IEnumerable
    {
        private JXQueryEvaluator evaluator;
        private StandardLogger traceFunctionDestination;

        //private JXQueryExpression exp;
        //private JDynamicQueryContext context;
        //private JController controller;
        //private StandardLogger traceFunctionDestination;

        // internal constructor

        internal XQueryEvaluator(JXQueryEvaluator eval)
        {
            this.evaluator = eval;
            //this.exp = exp;
            //this.context =
			//	new JDynamicQueryContext(exp.getConfiguration());
        }

        /// <summary>
        /// The context item for the query.
        /// </summary>
        /// <remarks> This may be a node, an atomic value, or a function item such as a map or array.
        /// Most commonly it will be a document node, which might be constructed
        /// using a <c>DocumentBuilder</c> created from the <c>Processor</c> object.
        /// </remarks>

        public XdmItem ContextItem
        {
            get { return (XdmItem)XdmValue.Wrap(evaluator.getContextItem().getUnderlyingValue()); }
            set { evaluator.setContextItem((net.sf.saxon.s9api.XdmItem)XdmValue.FromGroundedValueToJXdmValue(value.value)); }
        }

        /// <summary>
        /// The <c>SchemaValidationMode</c> to be used in this query, especially for documents
        /// loaded using the <c>doc()</c>, <c>document()</c>, or <c>collection()</c> functions.
        /// </summary>
        /// <remarks>
        /// This does not affect any document supplied as the context item for the query, or as the values
        /// of external variables.
        /// </remarks>

        public SchemaValidationMode SchemaValidationMode
        {
            get
            {
                switch (evaluator.getUnderlyingQueryContext().getSchemaValidationMode())
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
                JDynamicQueryContext context = evaluator.getUnderlyingQueryContext();
                switch (value)
                {
                    case SchemaValidationMode.Strict:
                        context.setSchemaValidationMode(JValidation.STRICT);
                        break;
                    case SchemaValidationMode.Lax:
                        context.setSchemaValidationMode(JValidation.LAX);
                        break;
                    case SchemaValidationMode.None:
                        context.setSchemaValidationMode(JValidation.STRIP);
                        break;
                    case SchemaValidationMode.Preserve:
                        context.setSchemaValidationMode(JValidation.PRESERVE);
                        break;
                    case SchemaValidationMode.Unspecified:
                    default:
                        context.setSchemaValidationMode(JValidation.DEFAULT);
                        break;
                }
            }
        }

        /// <summary>
        /// The <code>XmlResolver</code> to be used at run-time to resolve and dereference URIs
        /// supplied to the <c>doc()</c> function.
        /// </summary>

        public XmlResolver InputXmlResolver
        {
            get
            {
                return ((JDotNetURIResolver)evaluator.getURIResolver()).getXmlResolver();
            }
            set
            {
                evaluator.setURIResolver(new JDotNetURIResolver(value));
            }
        }

        /// <summary>
        /// Set the value of an external variable declared in the query.
        /// </summary>
        /// <param name="name">The name of the external variable, expressed
		/// as a <c>QName</c>. If an external variable of this name has been declared in the
        /// query prolog, the given value will be assigned to the variable. If the
        /// variable has not been declared, calling this method has no effect (it is
        /// not an error).</param>
        /// <param name="value">The value to be given to the external variable.
        /// If the variable declaration defines a required type for the variable, then
        /// this value must match the required type: no conversions are applied.</param>

        public void SetExternalVariable(QName name, XdmValue value)
        {
            evaluator.setExternalVariable(new JXQName(name.ToStructuredQName()), value == null ? null : XdmValue.FromGroundedValueToJXdmValue(value.value));
        }

        /// <summary>
        /// Destination for output of messages produced using the <c>trace()</c> function. 
        /// </summary>
		/// <remarks>
		/// <para>If no specific destination is supplied by the caller, message information will be written to
		/// the standard error stream.</para>
        /// <para>The supplied destination is ignored if a <c>TraceListener</c> is in use.</para>
        /// </remarks>

        public StandardLogger TraceFunctionDestination
        {
            set
            {
				traceFunctionDestination = value;
				evaluator.setTraceFunctionDestination(value);
            }
            get
            {
                return traceFunctionDestination;
            }
        }


        /// <summary>
        /// Evaluate the query, returning the result as an <c>XdmValue</c> (that is,
        /// a sequence of nodes and/or atomic values).
        /// </summary>
        /// <returns>
        /// An <c>XdmValue</c> representing the results of the query
        /// </returns>
		/// <exception cref="DynamicError">Throws a <c>DynamicError</c> if any run-time failure
        /// occurs while evaluating the query.</exception>

        public XdmValue Evaluate()
        {
            try
            {
                JXdmValue value = evaluator.evaluate();
                return XdmValue.Wrap(value.getUnderlyingValue());
            }
            catch (JSaxonApiException err)
            {
                throw new DynamicError(err);
            }
        }

        /// <summary>
        /// Evaluate the query, returning the result as an <c>XdmItem</c> (that is,
        /// a single node or atomic value).
        /// </summary>
        /// <returns>
        /// An <c>XdmItem</c> representing the result of the query, or null if the query
        /// returns an empty sequence. If the query returns a sequence of more than one item,
        /// any items after the first are ignored.
        /// </returns>
		/// <exception cref="DynamicError">Throws a <c>DynamicError</c> if any run-time failure
        /// occurs while evaluating the expression.</exception>

        public XdmItem EvaluateSingle()
        {
            try
            {
                return (XdmItem)XdmValue.Wrap(evaluator.evaluateSingle().getUnderlyingValue());
            }
            catch (JSaxonApiException err)
            {
                throw new DynamicError(err);
            }
        }

        /// <summary>
        /// Evaluate the query, returning the result as an <c>IEnumerator</c> (that is,
        /// an enumerator over a sequence of nodes and/or atomic values).
        /// </summary>
        /// <returns>
        /// An enumerator over the sequence that represents the results of the query.
        /// Each object in this sequence will be an instance of <c>XdmItem</c>. Note
        /// that the query may be evaluated lazily, which means that a successful response
        /// from this method does not imply that the query has executed successfully: failures
        /// may be reported later while retrieving items from the iterator. 
        /// </returns>
		/// <exception cref="DynamicError">Throws a <c>DynamicError</c> if any run-time failure
        /// occurs while evaluating the expression.</exception>

        public IEnumerator GetEnumerator()
        {
            try
            {
                return new SequenceEnumerator<XdmItem>(evaluator.iterator());
            }
            catch (net.sf.saxon.s9api.SaxonApiUncheckedException err)
            {
                throw new DynamicError(JXPathException.makeXPathException(err));
            }
        }

        

        /// <summary>
        /// Evaluate the query, sending the result to a specified destination.
        /// </summary>
        /// <param name="destination">
        /// The destination for the results of the query. The class <c>XmlDestination</c>
        /// is an abstraction that allows a number of different kinds of destination
        /// to be specified.
        /// </param>
		/// <exception cref="DynamicError">Throws a <c>DynamicError</c> if any run-time failure
        /// occurs while evaluating the expression.</exception>

        public void Run(XmlDestination destination)
        {
            try
            {
                evaluator.setDestination(destination.GetUnderlyingDestination());
                evaluator.run();
    
            }
            catch (JSaxonApiException err)
            {
                throw new DynamicError(err);
            }
         
        }

        /// <summary>
        /// Execute an updating query.
        /// </summary>
        /// <returns>An array containing the root nodes of documents that have been
        /// updated by the query.</returns>
		/// <exception cref="DynamicError">Throws a <c>DynamicError</c> if any run-time failure
        /// occurs while evaluating the expression, or if the expression is not an
        /// updating query.</exception>

        public XdmNode[] RunUpdate()
        {
            try
            {

                evaluator.run();
                java.util.Iterator updatedDocsIter = evaluator.getUpdatedDocuments();
                List<XdmNode> resultList = new List<XdmNode>();

                for (; updatedDocsIter.hasNext(); )

                {
                    resultList.Add((XdmNode)XdmValue.Wrap( ((net.sf.saxon.s9api.XdmNode)updatedDocsIter.next()).getUnderlyingNode()));
                }
                XdmNode[] result = resultList.ToArray();
                return result;
            }
            catch (JSaxonApiException err)
            {
                throw new DynamicError(err);
            }
        }

    /// <summary>
    /// Call a global user-defined function in the compiled query.
    /// </summary>
    /// <remarks>
    /// If this is called more than once (to evaluate the same function repeatedly with different arguments,
    /// or to evaluate different functions) then the sequence of evaluations uses the same values of global
    /// variables including external variables (query parameters); the effect of any changes made to query parameters
    /// between calls is undefined.
    /// </remarks>
    /// <param name="function">
    /// The name of the function to be called
    /// </param>
    /// <param name="arguments">
    /// The values of the arguments to be supplied to the function. These
    /// must be of the correct type as defined in the function signature (there is no automatic
    /// conversion to the required type).
    /// </param>
    /// <exception cref="ArgumentException">If no function has been defined with the given name and arity
    /// or if any of the arguments does not match its required type according to the function
    /// signature.</exception>
    /// <exception cref="DynamicError">If a dynamic error occurs in evaluating the function.
    /// </exception>

    public XdmValue CallFunction(QName function, XdmValue[] arguments) {    
        try {
            JXdmValue[] vr = new JXdmValue[arguments.Length];
            for (int i=0; i<arguments.Length; i++) {
                vr[i] = XdmValue.FromGroundedValueToJXdmValue(arguments[i].value);
            }
            JSequence result = evaluator.callFunction(function.UnderlyingQName(), vr).getUnderlyingValue();
            return XdmValue.Wrap(result);
        } catch (JSaxonApiException e) {
            throw new DynamicError(e);
        }
    }



        /// <summary>
        /// Escape hatch to the <c>net.sf.saxon.query.DynamicQueryContext</c> object in the underlying Java implementation
        /// </summary>

        public JXQueryEvaluator Implementation
        {
            get { return evaluator; }
        }

    }


    /// <summary>
    /// Interface defining a user-supplied class used to retrieve XQuery library modules listed
    /// in an <c>import module</c> declaration in the query prolog.
    /// </summary>


    public interface IQueryResolver
    {

        /// <summary>
        /// Given a module URI and a set of location hints, return a set of query modules.
        /// </summary>
        /// <param name="moduleUri">The URI of the required library module as written in the
        /// <c>import module</c> declaration</param>
        /// <param name="baseUri">The base URI of the module containing the <c>import module</c>
        /// declaration</param>
        /// <param name="locationHints">The sequence of URIs (if any) listed as location hints
        /// in the <c>import module</c> declaration in the query prolog.</param>
        /// <returns>A set of absolute URIs identifying the query modules to be loaded. There is no requirement
        /// that these correspond one-to-one with the URIs defined in the <c>locationHints</c>. The 
        /// returned URIs will be dereferenced by calling the <c>GetEntity</c> method.
        /// </returns>

		/**public**/ Uri[] GetModules(String moduleUri, Uri baseUri, String[] locationHints);

        /// <summary>
        /// Dereference a URI returned by <c>GetModules</c> to retrieve a <c>Stream</c> containing
        /// the actual query text.
        /// </summary>
        /// <param name="absoluteUri">A URI returned by the <code>GetModules</code> method.</param>
        /// <returns>Either a <c>Stream</c> or a <c>String</c> containing the query text. 
        /// The supplied URI will be used as the base URI of the query module.</returns>

		/**public**/ Object GetEntity(Uri absoluteUri);

    }

    /// <summary>
	/// Internal class that wraps a (.NET) <c>IQueryResolver</c> to create a (Java) <c>ModuleURIResolver</c>.
	/// <para>A <c>ModuleURIResolver</c> is used when resolving references to
	/// query modules. It takes as input a URI that identifies the module to be loaded, and a set of
	/// location hints, and returns one or more <c>StreamSource</c> obects containing the queries
	/// to be imported.</para>
    /// </summary>

    internal class DotNetModuleURIResolver : net.sf.saxon.lib.ModuleURIResolver
    {

        private IQueryResolver resolver;

		/// <summary>
		/// Initializes a new instance of the <see cref="Saxon.Api.DotNetModuleURIResolver"/> class.
		/// </summary>
		/// <param name="resolver">Resolver.</param>
        public DotNetModuleURIResolver(IQueryResolver resolver)
        {
            this.resolver = resolver;
        }


		/// <summary>
		/// Resolve a module URI and associated location hints.
		/// </summary>
		/// <param name="moduleURI">ModuleURI. The module namespace URI of the module to be imported; or null when
		/// loading a non-library module.</param>
		/// <param name="baseURI">BaseURI. The base URI of the module containing the "import module" declaration;
		/// null if no base URI is known</param>
		/// <param name="locations">Locations. The set of URIs specified in the "at" clause of "import module",
		/// which serve as location hints for the module</param>
		/// <returns>an array of StreamSource objects each identifying the contents of a module to be
		/// imported. Each StreamSource must contain a
		/// non-null absolute System ID which will be used as the base URI of the imported module,
		/// and either an InputSource or a Reader representing the text of the module. The method
		/// may also return null, in which case the system attempts to resolve the URI using the
		/// standard module URI resolver.</returns>
        public JStreamSource[] resolve(String moduleURI, String baseURI, String[] locations)
        {
            Uri baseU = (baseURI == null ? null : new Uri(baseURI));
            Uri[] modules = resolver.GetModules(moduleURI, baseU, locations);
            JStreamSource[] ss = new JStreamSource[modules.Length];
            for (int i = 0; i < ss.Length; i++)
            {
                ss[i] = new JStreamSource();
                ss[i].setSystemId(modules[i].ToString());
                Object query = resolver.GetEntity(modules[i]);
                if (query is Stream)
                {
                    ss[i].setInputStream(new JDotNetInputStream((Stream)query));
                }
                else if (query is String)
                {
                    ss[i].setReader(new JDotNetReader(new StringReader((String)query)));
                }
                else
                {
                    throw new ArgumentException("Invalid response from GetEntity()");
                }
            }
            return ss;
        }
    }






}


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////