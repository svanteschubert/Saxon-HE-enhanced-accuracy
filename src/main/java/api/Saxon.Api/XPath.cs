using System;
using System.Xml;
using System.Collections;
using System.Globalization;
using JIterator = java.util.Iterator;
using JSequence = net.sf.saxon.om.Sequence;
using JStructuredQName = net.sf.saxon.om.StructuredQName;
using JIndependentContext = net.sf.saxon.sxpath.IndependentContext;
using JXPathExecutable = net.sf.saxon.s9api.XPathExecutable;
using JXPathCompiler = net.sf.saxon.s9api.XPathCompiler;
using JXPathSelector = net.sf.saxon.s9api.XPathSelector; 
using JXPathVariable = net.sf.saxon.sxpath.XPathVariable;
using JStaticProperty = net.sf.saxon.expr.StaticProperty;
using JSaxonApiException = net.sf.saxon.s9api.SaxonApiException;
using JXPathException = net.sf.saxon.trans.XPathException;
using JDotNetComparator = net.sf.saxon.dotnet.DotNetComparator;
using JDotNetURIResolver = net.sf.saxon.dotnet.DotNetURIResolver;
using JXdmItem = net.sf.saxon.s9api.XdmItem;
using System.Collections.Generic;

namespace Saxon.Api
{

    /// <summary>
	/// An <c>XPathCompiler</c> object allows XPath queries to be compiled.
    /// The compiler holds information that represents the static context
    /// for the expression.
    /// </summary>
    /// <remarks>
	/// <para>To construct an <c>XPathCompiler</c>, use the factory method
	/// <c>NewXPathCompiler</c> on the <see cref="Processor"/> object.</para>
	/// <para>An <c>XPathCompiler</c> may be used repeatedly to compile multiple
	/// queries. Any changes made to the <c>XPathCompiler</c> (that is, to the
    /// static context) do not affect queries that have already been compiled.
	/// An <c>XPathCompiler</c> may be used concurrently in multiple threads, but
    /// it should not then be modified once initialized.</para>
    /// <para> The <code>XPathCompiler</code> has the ability to maintain a cache of compiled
    /// expressions. This is active only if enabled by setting the <c>Caching</c> property.
    /// If caching is enabled, then the compiler will recognize an attempt to compile
    /// the same expression twice, and will avoid the cost of recompiling it. The cache
    /// is emptied by any method that changes the static context for subsequent expressions,
    /// for example, by setting the <c>BaseUri</c> property. Unless the cache is emptied,
    /// it grows indefinitely: compiled expressions are never discarded.</para>
    /// </remarks>

    [Serializable]
    public class XPathCompiler
    {

        private JXPathCompiler compiler;
        private Processor processor;

        // internal constructor: the public interface is a factory method
        // on the Processor object

        internal XPathCompiler(Processor processor, JXPathCompiler compiler)
        {
            this.compiler = compiler;
            this.processor = processor;
        }



        /// <summary>
        /// Declare the default collation
        /// </summary>
        /// <param name="uri">the absolute URI of the default collation. This URI must identify a known collation;
        /// either one that has been explicitly declared, or one that is recognized implicitly, such as a UCA collation</param>
        public void DeclareDefaultCollation(String uri)
        {
            compiler.declareDefaultCollation(uri);
        }

        /// <summary>
        /// Declare a namespace for use by the XPath expression.
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
        /// Set the error reporter to be used for reporting static warnings during compilation.
		/// By default, the <see cref="ErrorReporter"/> associated with the Saxon Configuration is used.
        /// Note that fatal static errors are always reported in the form
		/// of an exception thrown by the <see cref="Compile(String)"/> method, so this method only controls
        /// the handling of warnings
        /// </summary>
        /// <remarks>The property IErrorReporter to which warnings will be notified</remarks>
        public IErrorReporter WarningHandler {
            set {
                compiler.setWarningHandler(new ErrorReporterWrapper(value));
            }

        }


        /// <summary>
        /// Make available a set of functions defined in an XSLT 3.0 package. All functions
		/// defined with <c>visibility="public"</c>
		/// (or exposed as public using <c>xsl:expose</c>
        /// become part of the static context for an XPath expression created using this
		/// <c>XPathCompiler</c>. The functions are added to the search path after all existing
        /// functions, including functions added using a previous call on this method.
        /// <p>Note that if the library package includes functions that reference stylesheet parameters
        /// (or global variables that depend on the context item), then there is no way of supplying
        /// values for such parameters; calling such functions will cause a run-time error.</p>
        /// </summary>
        /// <param name="libraryPackage">the XSLT compiled library package whose functions are to be made available</param>
        public void AddXsltFunctionLibrary(XsltPackage libraryPackage) {
            compiler.addXsltFunctionLibrary(libraryPackage.GetUnderlyingXsltPackage());

        }

        /// <summary>
        /// Get the namespace URI part of a QName provided in lexical form (<c>prefix:localname</c>)
        /// </summary>
        /// <param name="lexicalName">The lexical QName. This may either be a plain <c>NCName</c> (a local name
        /// with no prefix or colon) or a lexical name using a prefix that is bound to a namespace.</param>
        /// <param name="useDefault">Set to true if the default namespace for elements and types is to be used
        /// in the case where there is no prefix. If false, no prefix means no namespace.</param>
        /// <returns>The namespace URI associated with the prefix (or absence thereof) in the supplied
        /// lexical QName. The "null namespace" is represented by a zero length string. The method returns null
        /// if there is no known binding for the prefix used in the lexical QName.</returns>

        public string GetNamespaceURI(string lexicalName, Boolean useDefault)
        {
            String[] parts = net.sf.saxon.om.NameChecker.checkQNameParts(net.sf.saxon.value.Whitespace.trimWhitespace(lexicalName));

            return compiler.getUnderlyingStaticContext().getNamespaceResolver().getURIForPrefix(parts[0], useDefault);
        }

        /// <summary>
        /// Get the <c>Processor</c> from which this <c>XPathCompiler</c> was constructed
        /// </summary>

        public Processor Processor
        {
            get { return processor; }
        }

        /// <summary>
        /// Import schema definitions for a specified namespace. That is, add the element and attribute declarations 
		/// and type definitions contained in a given namespace to the static context for the XPath expression.
        /// </summary>
        /// <remarks>
        /// <para>This method will not cause the schema to be loaded. That must be done separately, using the
        /// <c>SchemaManager</c>. This method will not fail if the schema has not been loaded (but in that case
        /// the set of declarations and definitions made available to the XPath expression is empty). The schema
        /// document for the specified namespace may be loaded before or after this method is called.
        /// </para>
        /// <para>
        /// This method does not bind a prefix to the namespace. That must be done separately, using the
        /// <c>DeclareNamespace</c> method.
        /// </para>
        /// </remarks>
        /// <param name="uri">The namespace URI whose declarations and type definitions are to
        /// be made available for use within the XPath expression.</param>

        public void ImportSchemaNamespace(String uri)
        {
            compiler.importSchemaNamespace(uri);
        }


        /// <summary>
        /// Escape hatch to the <c>net.sf.saxon.s9api.XPathCompiler</c> object in the underlying Java implementation
        /// </summary>

        public JXPathCompiler Implementation
        {
            get { return compiler; }
        }


        /// <summary>
        /// This property indicates whether the XPath expression may contain references to variables that have not been
        /// explicitly declared by calling <c>DeclareVariable</c>. The property is false by default (that is, variables
        /// must be declared).
        /// </summary>
        /// <remarks>
        /// If undeclared variables are permitted, then it is possible to determine after compiling the expression which
        /// variables it refers to by calling the method <c>EnumerateExternalVariables</c> on the <c>XPathExecutable</c> object.
        /// </remarks>

        public Boolean AllowUndeclaredVariables
        {
            get
            {
                return compiler.isAllowUndeclaredVariables();
            }
            set
            {
                compiler.setAllowUndeclaredVariables(value);
            }
        }

        /// <summary>
		/// Say whether XPath expressions compiled using this <c>XPathCompiler</c> are
        /// schema-aware. They will automatically be schema-aware if the method
        /// <see cref="ImportSchemaNamespace"/> is called. An XPath expression
        /// must be marked as schema-aware if it is to handle typed (validated)
        /// input documents.
        /// </summary>

        public Boolean SchemaAware
        {
            get {
				return compiler.isSchemaAware();
            }
            set 
            {
                compiler.setSchemaAware(value);
            }
        }


        private int checkSingleChar(String s)
        {
            int[] e = net.sf.saxon.value.StringValue.expand(s);
            if (e.Length != 1)
            {
                throw new ArgumentException("Attribute \"" + s + "\" should be a single character");
            }
            return e[0];

        }

        /// <summary>
		/// Sets a property of a selected decimal format, for use by the <c>format-number()</c> function.
        /// </summary>
        /// <remarks>
        /// This method checks that the value is valid for the particular property, but it does not
        /// check that all the values for the decimal format are consistent (for example, that the
        /// decimal separator and grouping separator have different values). This consistency
        /// check is performed only when the decimal format is used.
        /// </remarks>
        /// <param name="format">The name of the decimal format whose property is to be set.
        ///  Supply null to set a property of the default (unnamed) decimal format.
		///  This correponds to a name used in the third argument of <c>format-number()</c>.</param>
        /// <param name="property">The name of the property to set: one of
        ///   "decimal-separator", "grouping-separator", "infinity", "NaN",
        ///   "minus-sign", "percent", "per-mille", "zero-digit", "digit",
        ///   or "pattern-separator".</param>
        /// <param name="value">The new value for the property.</param>

        public void SetDecimalFormatProperty(QName format, String property, String value) {
            try
            {
                compiler.setDecimalFormatProperty(format.UnderlyingQName(), property, value);
            }
            catch (JSaxonApiException e) {
                throw new StaticError(e);
            }

        }

        /// <summary>
        /// Declare a variable for use by the XPath expression. If the expression
        /// refers to any variables, then they must be declared here, unless the
        /// <c>AllowUndeclaredVariables</c> property has been set to true.
        /// </summary>
        /// <param name="name">The name of the variable, as a <c>QName</c></param>


        public void DeclareVariable(QName name)
        {
            compiler.declareVariable(name.UnderlyingQName());
        }

        /// <summary>
        /// This property indicates which version of XPath language syntax is accepted. The accepted values
        /// are "2.0", "3.0", and "3.1". The default is "3.1".
        /// </summary>
        /// <remarks>
        /// <para>Requesting a value other than 3.1 restricts the XPath grammar to constructs defined
        /// in the appropriate version, and uses the appropriate subsets of the functions in the standard
        /// function library. However, the semantics of remaining constructs will generally follow the XPath 3.1
        /// rules. For example, there is a rule in XPath 2.0 that casting to
		/// <c>xs:QName</c> is only permitted if the operand is a string literal, but this is not enforced when
        /// this property is set to "2.0".</para>
        /// <para>There is no support for XPath 1.0.</para>
        /// </remarks>


        public string XPathLanguageVersion
        {
			get {
                try
                {
                    return compiler.getLanguageVersion();
                }
                catch (Exception) {
                    throw new StaticError(new net.sf.saxon.trans.XPathException("Unknown XPath version " + compiler.getLanguageVersion()));
                }
            }
            set { 
                try
                {
                    compiler.setLanguageVersion(value);
                }
                catch (Exception)
                {
                    throw new StaticError(new net.sf.saxon.trans.XPathException("Unknown XPath version " + value));
                }
                
            }
        }


        /// <summary>
        /// The required context item type for the expression. This is used for
        /// optimizing the expression at compile time, and to check at run-time
        /// that the value supplied for the context item is the correct type.
        /// </summary>

        public XdmItemType ContextItemType
        {
            get { return XdmItemType.MakeXdmItemType( compiler.getRequiredContextItemType().getUnderlyingItemType()); }
            set { compiler.setRequiredContextItemType(value.Unwrap()); }
        }


        /// <summary>
        /// The base URI of the expression, which forms part of the static context
        /// of the expression. This is used for resolving any relative URIs appearing
        /// within the expression, for example in the argument to the <c>doc()</c> function.
        /// </summary>

        public String BaseUri
        {
			get { return compiler.getBaseURI().getRawPath(); }
            set {
               compiler.setBaseURI(new java.net.URI(value)); 
            }
        }

        /// <summary>
        /// XPath 1.0 Backwards Compatibility Mode (that is, XPath 1.0 compatibility mode). 
        /// If true, backwards compatibility mode
        /// is set. In backwards compatibility mode, more implicit type conversions are
        /// allowed in XPath expressions, for example it is possible to compare a number
        /// with a string. The default is false (backwards compatibility mode is off).
        /// </summary>
        /// <remarks>
        /// <para>Setting XPath 1.0 compatibility mode does not prevent the use of constructs
        /// defined in a later XPath version; rather, it modifies the semantics of some
        /// constructs.</para></remarks>

        public Boolean BackwardsCompatible
        {
            get { return compiler.isBackwardsCompatible(); }
            set {
                compiler.setBackwardsCompatible(value);
            }
        }

        /// <summary>
        /// This property controls caching of compiled XPath expressions. If caching is enabled,
        /// then compiled expressions are saved in a cache and reused if the same expression is compiled
        /// again. The cache is cleared (invalidated) if any change is made to the properties of the
        /// <c>XPathCompiler</c> that would affect the validity of cached expressions. Caching is disabled
        /// by default.
        /// </summary>

        public Boolean Caching
        {
            get { 
                return compiler.isCaching(); 
            }
            set
            {
                compiler.setCaching(value);
            }
        }


		/// <summary>
		/// Request fast compilation. Fast compilation will generally be achieved at the expense of run-time performance
		/// and quality of diagnostics. Fast compilation is a good trade-off if (a) the expression is known to be correct,
		/// and (b) once compiled, it is only executed once against a document of modest size.
		/// </summary>
		/// <remarks>
		/// Set to true to request fast compilation; set to false to revert to the optimization options
		/// defined in the Configuration.
		/// </remarks>
        
		public bool FastCompliation
        {

            set { compiler.setFastCompilation(value); }
            get { return compiler.isFastCompilation(); }
        }

        /// <summary>
		/// Compile an expression supplied as a <c>String</c>.
        /// </summary>
        /// <example>
        /// <code>
        /// XPathExecutable q = compiler.Compile("distinct-values(//*/node-name()");
        /// </code>
        /// </example>
        /// <param name="source">A string containing the source text of the XPath expression</param>
        /// <returns>An <c>XPathExecutable</c> which represents the compiled XPath expression object.
		/// The <c>XPathExecutable</c> may be run as many times as required, in the same or a different
        /// thread. The <c>XPathExecutable</c> is not affected by any changes made to the <c>XPathCompiler</c>
        /// once it has been compiled.</returns>
        /// <exception cref="StaticError">
        /// Throws a <c>Saxon.Api.StaticError</c> if there is any static error in the XPath expression.
        /// This includes both syntax errors, semantic errors such as references to undeclared functions or
        /// variables, and statically-detected type errors.
        /// </exception>

        public XPathExecutable Compile(String source)
        {
            try { 
                JXPathExecutable executable = compiler.compile(source);
                return new XPathExecutable(executable);
           
            }
            catch (JSaxonApiException err)
            {
                throw new StaticError(err);
            }
        }

        /// <summary>
		/// Compile and execute an expression supplied as a <c>String</c>, with a given context item.
        /// </summary>
        /// <param name="expression">A string containing the source text of the XPath expression</param>
        /// <param name="contextItem">The context item to be used for evaluation of the XPath expression.
        /// May be null, in which case the expression is evaluated without any context item.</param>
        /// <returns>An <c>XdmValue</c> which is the result of evaluating the XPath expression.</returns>
        /// <exception cref="StaticError">
        /// Throws a <c>Saxon.Api.StaticError</c> if there is any static error in the XPath expression.
        /// This includes both syntax errors, semantic errors such as references to undeclared functions or
        /// variables, and statically-detected type errors.
        /// </exception>
        /// <exception cref="DynamicError">
        /// Throws a <c>Saxon.Api.DynamicError</c> if there is any dynamic error during evaluation of the XPath expression.
        /// This includes, for example, referring to the context item if no context item was supplied.
        /// </exception>

        public XdmValue Evaluate(String expression, XdmItem contextItem)
        {
            try
            {
                net.sf.saxon.s9api.XdmValue value = compiler.evaluate(expression, contextItem == null ? null : XdmItem.FromXdmItemItemToJXdmItem(contextItem));
                return XdmValue.Wrap(value.getUnderlyingValue());
            }
            catch (JSaxonApiException err)
            {
                if (err.getCause() is JXPathException)
                {
                    JXPathException xpathException = (JXPathException)err.getCause();
                    if (xpathException.isStaticError())
                    {
                        throw new StaticError(err);
                    }
                    else
                    {
                        throw new DynamicError(err.getMessage());
                    }
                }
                else
                {
                    throw new StaticError(err);
                }
            }
        }

        /// <summary>
		/// Compile and execute an expression supplied as a <c>String</c>, with a given context item, where
        /// the expression is expected to return a single item as its result.
        /// </summary>
        /// <param name="expression">A string containing the source text of the XPath expression</param>
        /// <param name="contextItem">The context item to be used for evaluation of the XPath expression.
        /// May be null, in which case the expression is evaluated without any context item.</param>
        /// <returns>If the XPath expression returns a singleton, then the the <c>XdmItem</c> 
        /// which is the result of evaluating the XPath expression. If the expression returns an empty sequence,
        /// then null. If the expression returns a sequence containing more than one item, then the first
        /// item in the result.</returns>
        /// <exception cref="StaticError">
        /// Throws a <c>Saxon.Api.StaticError</c> if there is any static error in the XPath expression.
        /// This includes both syntax errors, semantic errors such as references to undeclared functions or
        /// variables, and statically-detected type errors.
        /// </exception>
        /// <exception cref="DynamicError">
        /// Throws a <c>Saxon.Api.DynamicError</c> if there is any dynamic error during evaluation of the XPath expression.
        /// This includes, for example, referring to the context item if no context item was supplied.
        /// </exception>

        public XdmItem EvaluateSingle(String expression, XdmItem contextItem)
        {

            try
            {
                JXdmItem value = compiler.evaluateSingle(expression, contextItem == null ? null : XdmItem.FromXdmItemItemToJXdmItem(contextItem));
                return (value == null ? null : (XdmItem)XdmValue.Wrap(value.getUnderlyingValue()));
            }
            catch (JSaxonApiException err)
            {
                if (err.getCause() is JXPathException)
                {
                    JXPathException xpathException = (JXPathException)err.getCause();
                    if (xpathException.isStaticError())
                    {
                        throw new StaticError(err);
                    }
                    else {
                        throw new DynamicError(err.getMessage());
                    }
                }
                else
                {
                    throw new StaticError(err);
                }
            }
            
        }

    }

    /// <summary>
    /// An <c>XPathExecutable</c> represents the compiled form of an XPath expression. 
    /// To evaluate the expression, it must first be loaded to form an <c>XPathSelector</c>.
    /// </summary>
    /// <remarks>
    /// <para>An <c>XPathExecutable</c> is immutable, and therefore thread-safe. It is simplest to
    /// load a new <c>XPathSelector</c> each time the expression is to be evaluated. However, the 
    /// <c>XPathSelector</c> is serially reusable within a single thread.</para>
    /// <para>An <c>XPathExecutable</c> is created by using the <c>Compile</c>
    /// method on the <c>XPathCompiler</c> class.</para>
    /// </remarks>    

    [Serializable]
    public class XPathExecutable
    {

        private JXPathExecutable executable;

        // internal constructor

        internal XPathExecutable(JXPathExecutable executable)
        {
            this.executable = executable;
        }

        /// <summary>
        /// Get a list of external variables used by the expression. This will include both variables that were explicitly
        /// declared to the <c>XPathCompiler</c>, and (if the <c>AllowUndeclaredVariables</c> option was set) variables that
        /// are referenced within the expression but not explicitly declared.
        /// </summary>
        /// <returns>
		/// An <c>IEnumerator</c> over the names of the external variables, as instances of <c>QName</c>.
		/// </returns>
        
        public IEnumerator EnumerateExternalVariables()
        {
            ArrayList list = new ArrayList();
            JIterator iter = executable.iterateExternalVariables();
            while (iter.hasNext())
            {
                net.sf.saxon.s9api.QName var = (net.sf.saxon.s9api.QName)iter.next();
                list.Add(new QName(var));
            }
            return list.GetEnumerator();
        }

        /// <summary>
        /// Escape hatch to the <c>net.sf.saxon.s9api.XPathExecutable</c> object in the underlying Java implementation
        /// </summary>

        public JXPathExecutable Implementation
        {
            get { return executable; }
        }

        /// <summary>
        /// Get a list of external variables used by the expression. This will include both variables that were explicitly
        /// declared to the <c>XPathCompiler</c>, and (if the <c>AllowUndeclaredVariables</c> option was set) variables that
        /// are referenced within the expression but not explicitly declared.
        /// </summary>
        /// <returns>
        /// An <c>IEnumerator</c> over the names of the external variables, as instances of <c>QName</c>.
        /// </returns>

        public IEnumerator<QName> EnumerateExternalVariables2()
        {
            IList<QName> list = new List<QName>();
            JIterator iter = executable.iterateExternalVariables();
            while (iter.hasNext())
            {
                net.sf.saxon.s9api.QName var = (net.sf.saxon.s9api.QName)iter.next();
                list.Add(new QName(var));
            }
            return list.GetEnumerator();
        }


        /// <summary>
        /// Get the required cardinality of a declared variable in the static context of the expression.
        /// </summary>
        /// <remarks>
        /// <para>The result is given as an occurrence indicator, one of:</para>
        /// <list>
        /// <item>'?' (zero-or-one)</item> 
        /// <item>'*' (zero-or-more)</item>
        /// <item>'+' (one-or-more)</item>
        /// <item>' ' (a single space) (exactly one)</item> 
        /// <item>'º' (masculine ordinal indicator, xBA) (exactly zero)</item>
        /// </list>
        /// <para>The type <c>empty-sequence()</c> can be represented by an occurrence indicator of 'º' with 
        /// any item type.</para>
        /// <para>If the variable was explicitly declared, this will be the occurrence indicator that was set when the
        /// variable was declared. If no item type was set, it will be 
        /// <see cref="net.sf.saxon.s9api.OccurrenceIndicator#ZERO_OR_MORE"/>.</para>
        /// <para>If the variable was implicitly declared by reference (which can happen only when the
        /// <c>allowUndeclaredVariables</c> option is set), the returned type will be
        /// <see cref="net.sf.saxon.s9api.OccurrenceIndicator#ZERO_OR_MORE"/>.</para>
        /// <para>If no variable with the specified <c>QName</c> has been declared either explicitly or implicitly,
        /// the method returns '0'.</para>
        /// </remarks>
        /// <param name="variableName">the name of a declared variable</param>
        /// <returns>The required cardinality, in the form of an occurrence indicator.</returns>


        public char GetRequiredCardinalityForVariable(QName variableName)
        {
            JXPathVariable var = ((JIndependentContext)executable.getUnderlyingStaticContext()).getExternalVariable(variableName.ToStructuredQName());
            if (var == null)
            {
                return '0';
            }
            else
            {
                return GetOccurrenceIndicator(var.getRequiredType().getCardinality());
            }
        }


        //internal method

        internal char GetOccurrenceIndicator(int occurrenceIndicator)
        {
                switch (occurrenceIndicator)
                {
                    case JStaticProperty.ALLOWS_ZERO_OR_MORE:

                        return XdmSequenceType.ZERO_OR_MORE;

                    case JStaticProperty.ALLOWS_ONE_OR_MORE:

                        return XdmSequenceType.ONE_OR_MORE;

                    case JStaticProperty.ALLOWS_ZERO_OR_ONE:

                        return XdmSequenceType.ZERO_OR_ONE;

                    case JStaticProperty.EXACTLY_ONE:

                        return XdmSequenceType.ONE;

                    case JStaticProperty.ALLOWS_ZERO:

                        return XdmSequenceType.ZERO;

                    default:
                        throw new ArgumentException("Unknown occurrence indicator");
                }
            
        }


        /// <summary>
        /// Load the compiled XPath expression to prepare it for execution.
        /// </summary>
        /// <returns>
        /// An <c>XPathSelector</c>. The returned <c>XPathSelector</c> can be used to
        /// set up the dynamic context, and then to evaluate the expression.
        /// </returns>

        public XPathSelector Load()
        {
            JXPathSelector selector = executable.load();
            return new XPathSelector(selector);
        }
    }

    /// <summary inherits="IEnumerable">
    /// An <c>XPathSelector</c> represents a compiled and loaded XPath expression ready for execution.
    /// The <c>XPathSelector</c> holds details of the dynamic evaluation context for the XPath expression.
    /// </summary>
    /// <remarks>
    /// <para>An <c>XPathSelector</c> should not be used concurrently in multiple threads. It is safe,
    /// however, to reuse the object within a single thread to evaluate the same XPath expression several times.
    /// Evaluating the expression does not change the context that has been established.</para>
    /// <para>An <c>XPathSelector</c> is always constructed by running the <c>Load</c> method of
    /// an <c>XPathExecutable</c>.</para>
    /// <para>The class <c>XPathSelector</c> implements <c>IEnumerable</c>, so it is possible to
	/// enumerate the results in a <c>for</c> expression.</para>
    /// </remarks>     

    [Serializable]
    public class XPathSelector : IEnumerable
    {
        JXPathSelector selector;

        // internal constructor

        internal XPathSelector(JXPathSelector selector)
        {
            this.selector = selector;
        }

        /// <summary>
        /// The context item for the XPath expression evaluation.
        /// </summary>
        /// <remarks> This may be either a node or an atomic
        /// value. Most commonly it will be a document node, which might be constructed
        /// using the <c>Build</c> method of the <c>DocumentBuilder</c> object.
        /// </remarks>

        public XdmItem ContextItem
        {
            get { return (XdmItem)XdmValue.Wrap(selector.getContextItem().getUnderlyingValue()); }
            set
            {
                if (value == null)
                {
                    throw new ArgumentException("contextItem is null");
                }
                try
                {
                    selector.setContextItem(XdmItem.FromXdmItemItemToJXdmItem(value));
                }
                catch (net.sf.saxon.trans.XPathException err)
                {
                    throw new StaticError(err);
                }
            }
        }

        /// <summary>
        /// Escape hatch to the <c>net.sf.saxon.s9api.XPathSelector</c> object in the underlying Java implementation
        /// </summary>

        public JXPathSelector Implementation
        {
            get { return selector; }
        }

        /// <summary>
        /// Set the value of a variable
        /// </summary>
        /// <param name="name">The name of the variable. This must match the name of a variable
		/// that was declared to the <c>XPathCompiler</c>. No error occurs if the expression does not
        /// actually reference a variable with this name.</param>
        /// <param name="value">The value to be given to the variable.</param>

        public void SetVariable(QName name, XdmValue value)
        {
            try
            {
                selector.setVariable(name.UnderlyingQName(), value== null ? null : XdmValue.FromGroundedValueToJXdmValue(value.value));

            }
            catch (JXPathException err)
            {
                throw new StaticError(err);
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
                return ((JDotNetURIResolver)selector.getURIResolver()).getXmlResolver();
            }
            set
            {
                selector.setURIResolver(new JDotNetURIResolver(value));
            }
        }

        /// <summary>
        /// Evaluate the expression, returning the result as an <c>XdmValue</c> (that is,
        /// a sequence of nodes, atomic values, and possibly function items such as maps and arrays).
        /// </summary>
        /// <remarks>
        /// Although a singleton result <i>may</i> be represented as an <c>XdmItem</c>, there is
        /// no guarantee that this will always be the case. If you know that the expression will return at
        /// most one node or atomic value, it is best to use the <c>EvaluateSingle</c> method, which 
        /// does guarantee that an <c>XdmItem</c> (or null) will be returned.
        /// </remarks>
        /// <returns>
        /// An <c>XdmValue</c> representing the results of the expression. 
        /// </returns>
        /// <exception cref="DynamicError">
        /// Throws <c>Saxon.Api.DynamicError</c> if the evaluation of the XPath expression fails
        /// with a dynamic error.
        /// </exception>

        public XdmValue Evaluate()
        {
            try {
                net.sf.saxon.s9api.XdmValue value = selector.evaluate();
                return value == null ? null : XdmValue.Wrap(value.getUnderlyingValue());
            } catch(JSaxonApiException err) {
                throw new DynamicError(err);
            }
        }

        /// <summary>
        /// Evaluate the XPath expression, returning the result as an <c>XdmItem</c> (that is,
        /// a single node or atomic value).
        /// </summary>
        /// <returns>
        /// An <c>XdmItem</c> representing the result of the expression, or null if the expression
        /// returns an empty sequence. If the expression returns a sequence of more than one item,
        /// any items after the first are ignored.
        /// </returns>
        /// <exception cref="DynamicError">
        /// Throws <c>Saxon.Api.DynamicError</c> if the evaluation of the XPath expression fails
        /// with a dynamic error.
        /// </exception>


        public XdmItem EvaluateSingle()
        {

            try
            {
                JXdmItem item = selector.evaluateSingle();
                return item == null ? null : (XdmItem)XdmValue.Wrap(item.getUnderlyingValue().materialize());
            }
            catch (JSaxonApiException err)
            {
                throw new DynamicError(err);
            }

        }
        
        /// <summary>
        /// Evaluate the effective boolean value of the XPath expression, returning the result as a <c>Boolean</c>
        /// </summary>
        /// <returns>
        /// A <c>Boolean</c> representing the result of the expression, converted to its
		/// effective boolean value as if by applying the XPath <c>boolean()</c> function
        /// </returns>
        /// <exception cref="DynamicError">
        /// Throws <c>Saxon.Api.DynamicError</c> if the evaluation of the XPath expression fails
        /// with a dynamic error.
        /// </exception>


        public Boolean EffectiveBooleanValue()
        {
            try
            {
                return selector.effectiveBooleanValue();
            } catch(JSaxonApiException err) {
                throw new DynamicError(err);
            }
        }
        

        /// <summary>
        /// Evaluate the expression, returning the result as an <c>IEnumerator</c> (that is,
        /// an enumerator over a sequence of nodes and/or atomic values).
        /// </summary>
        /// <returns>
        /// An enumerator over the sequence that represents the results of the expression.
        /// Each object in this sequence will be an instance of <c>XdmItem</c>. Note
        /// that the expression may be evaluated lazily, which means that a successful response
        /// from this method does not imply that the expression has executed successfully: failures
        /// may be reported later while retrieving items from the iterator. 
        /// </returns>
        /// <exception cref="DynamicError">
        /// May throw a <c>Saxon.Api.DynamicError</c> if the evaluation of the XPath expression fails
        /// with a dynamic error. However, some errors will not be detected during the invocation of this
		/// method, but only when stepping through the returned <c>SequenceEnumerator</c>.
        /// </exception>

        public IEnumerator GetEnumerator()
        {
            try {
                return new SequenceEnumerator<XdmItem>(selector.iterator());
            } catch (net.sf.saxon.s9api.SaxonApiUncheckedException err) {
                throw new DynamicError(JXPathException.makeXPathException(err));
            }
        }

    }

	/// <summary>
	/// A set of query parameters on a URI passed to the <c>collection()</c> or <c>document()</c> function.
	/// </summary>

    public class URIQueryParameters
    {

        private net.sf.saxon.functions.URIQueryParameters uriQueryParameters;


        public const int ON_ERROR_FAIL = 1;
        public const int ON_ERROR_WARNING = 2;
        public const int ON_ERROR_IGNORE = 3;

		/// <summary>
		/// Create an object representing the query part of a URI
		/// </summary>
		/// <param name="query">the part of the URI after the "?" symbol</param>
		/// <param name="proc">the Saxon Processor</param>

        public URIQueryParameters(String query, Processor proc)
        {
            uriQueryParameters = new net.sf.saxon.functions.URIQueryParameters(query, proc.Implementation);

        }

        internal net.sf.saxon.functions.URIQueryParameters Implementation() 
		{
            return uriQueryParameters;
        } 

        /* public static FileNameFilter MakeGlobFilter(String value) 
        {
            return new FileNameFilter(net.sf.saxon.functions.URIQueryParameters.makeGlobFilter(value));
        } */

        public int ValidationMode() 
		{
            return uriQueryParameters.getValidationMode().intValue();
        }

        /* public FileNameFilter FileNameFilter
        {
            get {
                return new FileNameFilter(uriQueryParameters.getFilenameFilter());
            }
            
        }*/

        public bool GetRecurse() 
		{
            return uriQueryParameters.getRecurse().booleanValue();
        }

        public int GetOnError() 
		{
            return uriQueryParameters.getOnError().intValue();
        }


        public bool GetXInclude()
        {
            return uriQueryParameters.getXInclude().booleanValue();
        }


        public bool GetMeataData()
        {
            return uriQueryParameters.getMetaData().booleanValue();
        }


        public String ContentType()
        {
            return uriQueryParameters.getContentType();
        }

        public bool GetStable()
        {
            return uriQueryParameters.getStable().booleanValue();
        }

    }

    /*public class FileNameFilter
    {
        java.io.FilenameFilter filter;
        internal FileNameFilter(java.io.FilenameFilter filter) {
            this.filter = filter;
        }
    }*/


}



////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////