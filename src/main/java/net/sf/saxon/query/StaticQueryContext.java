////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.GlobalParam;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.parser.CodeInjector;
import net.sf.saxon.expr.parser.OptimizerOptions;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.UnprefixedElementMatchingPolicy;
import net.sf.saxon.trace.TraceCodeInjector;
import net.sf.saxon.trace.XQueryTraceCodeInjector;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Schema;
import net.sf.saxon.value.SequenceType;

import javax.xml.transform.ErrorListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * StaticQueryContext contains information used to build a StaticContext for use when processing XQuery
 * expressions.
 * <p>Despite its name, <code>StaticQueryContext</code> no longer implements the <code>StaticContext</code>
 * interface, which means it cannot be used directly by Saxon when parsing a query. Instead it is first copied
 * to create a <code>QueryModule</code> object, which does implement the <code>StaticContext</code> interface.</p>
 * <p>The application constructs a StaticQueryContext
 * and initializes it with information about the context, for example, default namespaces, base URI, and so on.
 * When a query is compiled using this StaticQueryContext, the query parser makes a copy of the StaticQueryContext
 * and uses this internally, modifying it with information obtained from the query prolog, as well as information
 * such as namespace and variable declarations that can occur at any point in the query. The query parser does
 * not modify the original StaticQueryContext supplied by the calling application, which may therefore be used
 * for compiling multiple queries, serially or even in multiple threads.</p>
 * <p>This class forms part of Saxon's published XQuery API. Methods that
 * are considered stable are labelled with the JavaDoc "since" tag.
 * The value 8.4 indicates a method introduced at or before Saxon 8.4; other
 * values indicate the version at which the method was introduced.</p>
 * <p>In the longer term, this entire API may at some stage be superseded by a proposed
 * standard Java API for XQuery.</p>
 *
 * @since 8.4
 */

//@CSharpInjectMembers(code = {
//        "    public void setErrorReporter(System.Action<Saxon.Hej.s9api.XmlProcessingError> reporter) {"
//                + "        setErrorReporter(new Saxon.Impl.Helpers.ErrorReportingAction(reporter));"
//                + "    }"
//})
public class StaticQueryContext {

    private Configuration config;
    private NamePool namePool;
    private String baseURI;
    private NamespaceMap predeclaredNamespaces = NamespaceMap.STANDARD_QUERY_NAMESPACES;
    /*@Nullable*/ private Set<GlobalVariable> userDeclaredVariables;
    private boolean inheritNamespaces = true;
    private boolean preserveNamespaces = true;
    private int constructionMode = Validation.PRESERVE;
    private NamespaceUri defaultFunctionNamespace = NamespaceUri.FN;
    /*@Nullable*/ private NamespaceUri defaultElementNamespace = NamespaceUri.NULL;
    private SequenceType requiredContextValueType = SequenceType.SINGLE_ITEM;
    private boolean preserveSpace = false;
    private boolean defaultEmptyLeast = true;
    /*@Nullable*/ private ModuleURIResolver moduleURIResolver;
    private ErrorReporter errorReporter;
    /*@Nullable*/ private CodeInjector codeInjector;
    private boolean updating = false;
    private String defaultCollationName;
    private Location moduleLocation;
    private OptimizerOptions optimizerOptions;
    private int languageVersion = 31;
    private UnprefixedElementMatchingPolicy unprefixedElementMatchingPolicy
            = UnprefixedElementMatchingPolicy.DEFAULT_NAMESPACE;
    private Schema preLoadedSchema = null;


    /**
     * Create a StaticQueryContext using a given Configuration. This creates a StaticQueryContext for a main module
     * (that is, a module that is not a library module). The static query context is initialized from the
     * default static query context held in the configuration.
     *
     * @param config the Saxon Configuration
     * @since 8.4
     */

    public StaticQueryContext(Configuration config) {
        this(config, true);
    }

    /**
     * Create a StaticQueryContext using a given Configuration. This creates a StaticQueryContext for a main module
     * (that is, a module that is not a library module). The static query context is initialized from the
     * default static query context held in the configuration only if the {@code initialize} argument is set to true.
     *
     * @param config the Saxon Configuration
     * @param initialize specifies whether the static context should be initialized using defaults held in the
     *                   configuration. Setting this to true has the same effect as calling the single-argument
     *                   constructor. Setting it to false is typically used only when creating the default
     *                   static query context held by the configuration.
     * @since 9.9
     */

    public StaticQueryContext(Configuration config, boolean initialize) {
        this.config = config;
        this.namePool = config.getNamePool();
        this.errorReporter = config.makeErrorReporter();
        this.languageVersion = config.getBooleanProperty(Feature.ALLOW_SYNTAX_EXTENSIONS) ? 40 : 31;
        if (initialize) {
            copyFrom(config.getDefaultStaticQueryContext());
        } else {
            predeclaredNamespaces = NamespaceMap.STANDARD_QUERY_NAMESPACES;
            userDeclaredVariables = new HashSet<>();
            optimizerOptions = config.getOptimizerOptions();
            clearNamespaces();
        }
    }

    /**
     * Create a copy of a supplied StaticQueryContext
     *
     * @param c the StaticQueryContext to be copied
     */

    public StaticQueryContext(/*@NotNull*/ StaticQueryContext c) {
        copyFrom(c);
    }


    /**
     * Copy details from another StaticQueryContext
     * @param c the other StaticQueryContext
     */

    public void copyFrom(/*@NotNull*/ StaticQueryContext c) {
        config = c.config;
        namePool = c.namePool;
        baseURI = c.baseURI;
        moduleURIResolver = c.moduleURIResolver;
        if (c.predeclaredNamespaces != null) {
            predeclaredNamespaces = c.predeclaredNamespaces;
        }
        if (c.userDeclaredVariables != null) {
            userDeclaredVariables = new HashSet<>(c.userDeclaredVariables);
        }
        inheritNamespaces = c.inheritNamespaces;
        preserveNamespaces = c.preserveNamespaces;
        constructionMode = c.constructionMode;
        defaultElementNamespace = c.defaultElementNamespace;
        defaultFunctionNamespace = c.defaultFunctionNamespace;
        requiredContextValueType = c.requiredContextValueType;
        preserveSpace = c.preserveSpace;
        defaultEmptyLeast = c.defaultEmptyLeast;
        errorReporter = c.errorReporter;
        codeInjector = c.codeInjector;
        updating = c.updating;
        optimizerOptions = c.optimizerOptions;
        unprefixedElementMatchingPolicy = c.unprefixedElementMatchingPolicy;
    }


    /**
     * Reset the state of this StaticQueryContext to an uninitialized state
     *
     * @since 8.4
     */

    public void reset() {
        predeclaredNamespaces = NamespaceMap.emptyMap();
        errorReporter = config.makeErrorReporter();
        constructionMode = getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY) ?
                Validation.PRESERVE : Validation.STRIP;
        preserveSpace = false;
        defaultEmptyLeast = true;
        requiredContextValueType = SequenceType.SINGLE_ITEM;
        defaultFunctionNamespace = NamespaceUri.FN;
        defaultElementNamespace = NamespaceUri.NULL;
        moduleURIResolver = null;
        defaultCollationName = config.getDefaultCollationName();
        clearNamespaces();
        updating = false;
        optimizerOptions = config.getOptimizerOptions();
        unprefixedElementMatchingPolicy = UnprefixedElementMatchingPolicy.DEFAULT_NAMESPACE;

    }

    /**
     * Set the Configuration options
     *
     * @param config the Saxon Configuration
     * @throws IllegalArgumentException if the configuration supplied is different from the existing
     *                                  configuration
     * @since 8.4
     */

    public void setConfiguration(/*@NotNull*/ Configuration config) {
        if (this.config != null && this.config != config) {
            throw new IllegalArgumentException("Configuration cannot be changed dynamically");
        }
        this.config = config;
        namePool = config.getNamePool();
    }

    /**
     * Get the Configuration options
     *
     * @return the Saxon configuration
     * @since 8.4
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the executable containing this query
     *
     * @return the executable (which is newly created by this method)
     */

    /*@NotNull*/
    public Executable makeExecutable() {
        Executable executable = new Executable(config);
        executable.setSchemaAware(isSchemaAware());
        executable.setHostLanguage(HostLanguage.XQUERY);
        return executable;
    }

    /**
     * Say whether this query is schema-aware
     *
     * @param aware true if this query is schema-aware
     * @since 9.2.1.2
     */

    public void setSchemaAware(boolean aware) {
        if (aware) {
            throw new UnsupportedOperationException("Schema-awareness requires Saxon-EE");
        }
    }

    /**
     * Ask whether this query is schema-aware
     *
     * @return true if this query is schema-aware
     * @since 9.2.1.2
     */

    public boolean isSchemaAware() {
        return false;
    }

    /**
     * Say whether the query should be compiled and evaluated to use streaming.
     * This affects subsequent calls on the compile() methods. This option requires
     * Saxon-EE.
     * @param option if true, the compiler will attempt to compile a query to be
     * capable of executing in streaming mode. If the query cannot be streamed,
     * a compile-time exception is reported. In streaming mode, the source
     * document is supplied as a stream, and no tree is built in memory. The default
     * is false. Setting the value to true has the side-effect of setting the required
     * context item type to "document node"; this is to ensure that expresions such as
     * //BOOK are streamable.
     * @since 9.6
     */

    public void setStreaming(boolean option) {
        if (option) {
            throw new UnsupportedOperationException("Streaming requires Saxon-EE");
        }
    }

    /**
     * Ask whether the streaming option has been set, that is, whether
     * subsequent calls on compile() will compile queries to be capable
     * of executing in streaming mode.
     * @return true if the streaming option has been set.
     * @since 9.6
     */

    public boolean isStreaming() {
        return false;
    }


    /**
     * Set the Base URI of the query
     *
     * @param baseURI the base URI of the query, or null to indicate that no base URI is available
     * @since 8.4
     */

    public void setBaseURI(String baseURI) {
        this.baseURI = baseURI;
    }


    /**
     * Set the language version.
     *
     * @param version The XQuery language version. Must be 10 (="1.0") or 30 (="3.0") or 31 (="3.1").
     * @since 9.2; changed in 9.3 to expect a DecimalValue rather than a string. Changed in 9.7 to
     * accept an int (30 = "3.0") and to allow "3.1".  From 9.8.0.3 the supplied value was ignored
     * and the language version was always set to "3.1".  From 11.0 the value 40 was accepted.
     */
    
    public void setLanguageVersion(int version) {
        if (version==10 || version==30 || version==31) {
            languageVersion = 31;
        } else if (version == 40) {
            config.checkLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION, "XQuery 4.0", -1);
            languageVersion = 40;
        } else {
            throw new IllegalArgumentException("languageVersion = " + version);
        }
    }

    /**
     * Get the language version
     *
     * @return the language version. Either "1.0" or "1.1". Default is "1.0".
     * @since 9.2; changed in 9.3 to return a DecimalValue rather than a string;
     * changed in 9.7 to return an int (30 = "3.0" and so on). Changed in 9.8.0.3 to
     * always return 31. Changed in 11.0 to return 31 or 40.
     */

    public int getLanguageVersion() {
        return languageVersion;
    }

    /**
     * Get any extension function library that was previously set.
     *
     * @return the extension function library, or null if none has been set. The result will always be null if called
     *         in Saxon-HE; setting an extension function library requires the Saxon-PE or Saxon-EE versions of this class.
     * @since 9.4
     */


    /*@Nullable*/
    public FunctionLibrary getExtensionFunctionLibrary() {
        return null;
    }


    /**
     * Ask whether compile-time generation of trace code was requested
     *
     * @return true if compile-time generation of code was requested
     * @since 9.0
     */

    public boolean isCompileWithTracing() {
        return codeInjector instanceof TraceCodeInjector;
    }

    /**
     * Request compile-time generation of trace code (or not)
     *
     * @param trace true if compile-time generation of trace code is required
     * @since 9.0
     */

    public void setCompileWithTracing(boolean trace) {
        codeInjector = trace ? new XQueryTraceCodeInjector() : null;
    }

    /**
     * Request that the parser should insert custom code into the expression tree
     * by calling a supplied CodeInjector to process each expression as it is parsed,
     * for example for tracing or performance measurement
     *
     * @param injector the CodeInjector to be used. May be null, in which case
     *                 no code is injected
     * @since 9.4
     */

    public void setCodeInjector( /*@Nullable*/ CodeInjector injector) {
        this.codeInjector = injector;
    }

    /**
     * Get any CodeInjector that has been registered
     *
     * @return the registered CodeInjector, or null
     * @since 9.4
     */

    /*@Nullable*/
    public CodeInjector getCodeInjector() {
        return codeInjector;
    }

    /**
     * Ask whether XQuery Update is allowed
     *
     * @return true if XQuery update is supported by queries using this context
     */

    public boolean isUpdating() {
        return updating;
    }

    /**
     * Set the namespace inheritance mode
     *
     * @param inherit true if namespaces are inherited, false if not
     * @since 8.4
     */

    public void setInheritNamespaces(boolean inherit) {
        inheritNamespaces = inherit;
    }

    /**
     * Get the namespace inheritance mode
     *
     * @return true if namespaces are inherited, false if not
     * @since 8.4
     */

    public boolean isInheritNamespaces() {
        return inheritNamespaces;
    }

    /**
     * Set the namespace copy mode
     *
     * @param inherit true if namespaces are preserved, false if not
     * @since 8.4
     */

    public void setPreserveNamespaces(boolean inherit) {
        preserveNamespaces = inherit;
    }

    /**
     * Get the namespace copy mode
     *
     * @return true if namespaces are preserved, false if not
     * @since 8.4
     */

    public boolean isPreserveNamespaces() {
        return preserveNamespaces;
    }

    /**
     * Set the construction mode for this module
     *
     * @param mode one of {@link net.sf.saxon.lib.Validation#STRIP}, {@link net.sf.saxon.lib.Validation#PRESERVE}
     * @since 8.4
     */

    public void setConstructionMode(int mode) {
        constructionMode = mode;
    }

    /**
     * Get the current construction mode
     *
     * @return one of {@link net.sf.saxon.lib.Validation#STRIP}, {@link net.sf.saxon.lib.Validation#PRESERVE}
     * @since 8.4
     */

    public int getConstructionMode() {
        return constructionMode;
    }

    /**
     * Set the module location. Normally the module location is assumed to be line 1, column 1 of the
     * resource identified by the base URI. But a different location can be set, for example if the query
     * is embedded in an XML document, and this information will be available in diagnostics.
     * @param location the module location
     * @since 9.7
     */

    public void setModuleLocation(Location location) {
        this.moduleLocation = location;
    }

    /**
     * Get the module location. Normally the module location is assumed to be line 1, column 1 of the
     * resource identified by the base URI. But a different location can be set, for example if the query
     * is embedded in an XML document, and this information will be available in diagnostics.
     * @return the module location
     * @since 9.7
     */

    public Location getModuleLocation() {
        return moduleLocation;
    }

    /**
     * Set the optimizer options to be used for compiling queries that use this static context.
     * By default the optimizer options set in the {@link Configuration} are used.
     *
     * @param options the optimizer options to be used
     */

    public void setOptimizerOptions(OptimizerOptions options) {
        this.optimizerOptions = options;
    }

    /**
     * Get the optimizer options being used for compiling queries that use this static context.
     * By default the optimizer options set in the {@link Configuration} are used.
     *
     * @return the optimizer options being used
     */

    public OptimizerOptions getOptimizerOptions() {
        return this.optimizerOptions;
    }


    /**
     * Prepare an XQuery query for subsequent evaluation. The source text of the query
     * is supplied as a String. The base URI of the query is taken from the static context,
     * and defaults to the current working directory.
     * <p>Note that this interface makes the caller responsible for decoding the query and
     * presenting it as a string of characters. This means it is likely that any encoding
     * specified in the query prolog will be ignored.</p>
     *
     * @param query The XQuery query to be evaluated, supplied as a string.
     * @return an XQueryExpression object representing the prepared expression
     * @throws net.sf.saxon.trans.XPathException
     *          if the syntax of the expression is wrong,
     *          or if it references namespaces, variables, or functions that have not been declared,
     *          or contains other static errors.
     * @since 8.4
     */

    /*@NotNull*/
    public XQueryExpression compileQuery(/*@NotNull*/ String query) throws XPathException {
        QueryModule mainModule = new QueryModule(this);
        XQueryParser qp = (XQueryParser) config.newExpressionParser("XQ", updating, mainModule);
        if (codeInjector != null) {
            qp.setCodeInjector(codeInjector);
        } else if (config.isCompileWithTracing()) {
            qp.setCodeInjector(new TraceCodeInjector());
        }
        qp.setStreaming(isStreaming());
        return qp.makeXQueryExpression(query, mainModule, config);
    }

    /**
     * Prepare an XQuery query for subsequent evaluation. The Query is supplied
     * in the form of a Reader. The base URI of the query is taken from the static context,
     * and defaults to the current working directory.
     * <p>Note that this interface makes the Reader responsible for decoding the query and
     * presenting it as a stream of characters. This means it is likely that any encoding
     * specified in the query prolog will be ignored. Also, some implementations of Reader
     * cannot handle a byte order mark.</p>
     *
     * @param source A Reader giving access to the text of the XQuery query to be compiled.
     * @return an XPathExpression object representing the prepared expression.
     * @throws net.sf.saxon.trans.XPathException
     *                             if the syntax of the expression is wrong, or if it references namespaces,
     *                             variables, or functions that have not been declared, or any other static error is reported.
     * @throws java.io.IOException if a failure occurs reading the supplied input.
     * @since 8.4
     */

    /*@Nullable*/
    public synchronized XQueryExpression compileQuery(/*@NotNull*/ Reader source)
            throws XPathException, IOException {
        char[] buffer = new char[4096];
        StringBuilder sb = new StringBuilder(4096);
        while (true) {
            int n = source.read(buffer, 0, buffer.length);
            if (n > 0) {
                sb.append(buffer, 0, n);
            } else {
                break;
            }
        }
        return compileQuery(sb.toString());
    }

    /**
     * Prepare an XQuery query for subsequent evaluation. The Query is supplied
     * in the form of a InputStream, with an optional encoding. If the encoding is not specified,
     * the query parser attempts to obtain the encoding by inspecting the input stream: it looks specifically
     * for a byte order mark, and for the encoding option in the version declaration of an XQuery prolog.
     * The encoding defaults to UTF-8.
     * The base URI of the query is taken from the static context,
     * and defaults to the current working directory.
     *
     * @param source   An InputStream giving access to the text of the XQuery query to be compiled, as a stream
     *                 of octets
     * @param encoding The encoding used to translate characters to octets in the query source. The parameter
     *                 may be null: in this case the query parser attempts to infer the encoding by inspecting the source,
     *                 and if that fails, it assumes UTF-8 encoding
     * @return an XPathExpression object representing the prepared expression.
     * @throws net.sf.saxon.trans.XPathException
     *                             if the syntax of the expression is wrong, or if it references namespaces,
     *                             variables, or functions that have not been declared, or any other static error is reported.
     * @since 8.5
     */
    /*@Nullable*/
    public synchronized XQueryExpression compileQuery(/*@NotNull*/ InputStream source, /*@Nullable*/ String encoding)
            throws XPathException {
        try {
            String query = QueryReader.readInputStream(source, encoding, config.getValidCharacterChecker());
            return compileQuery(query);
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
        }
    }

    /**
     * Compile an XQuery library module for subsequent evaluation. This method is supported
     * only in Saxon-EE
     *
     * @param query the content of the module, as a string
     * @throws XPathException                if a static error exists in the query
     * @throws UnsupportedOperationException if not using Saxon-EE
     * @since 9.2 (changed in 9.3 to return void)
     */

    public void compileLibrary(String query) throws XPathException {
        throw new XPathException("Separate compilation of query libraries requires Saxon-EE");
    }

    /**
     * Prepare an XQuery library module for subsequent evaluation. This method is supported
     * only in Saxon-EE. The effect of the method is that subsequent query compilations using this static
     * context can import the module URI without specifying a location hint; the import then takes effect
     * without requiring the module to be compiled each time it is imported.
     *
     * @param source the content of the module, as a Reader which supplies the source code
     * @throws XPathException                if a static error exists in the query
     * @throws IOException                   if the input cannot be read
     * @throws UnsupportedOperationException if not using Saxon-EE
     * @since 9.2 (changed in 9.3 to return void)
     */

    public void compileLibrary(Reader source)
            throws XPathException, IOException {
        throw new XPathException("Separate compilation of query libraries requires Saxon-EE");
    }

    /**
     * Prepare an XQuery library module for subsequent evaluation. This method is supported
     * only in Saxon-EE. The effect of the method is that subsequent query compilations using this static
     * context can import the module URI without specifying a location hint; the import then takes effect
     * without requiring the module to be compiled each time it is imported.
     *
     * @param source   the content of the module, as an InputStream which supplies the source code
     * @param encoding the character encoding of the input stream. May be null, in which case the encoding
     *                 is inferred, for example by looking at the query declaration
     * @throws XPathException                if a static error exists in the query
     * @throws IOException                   if the input cannot be read
     * @throws UnsupportedOperationException if not using Saxon-EE
     * @since 9.2 (changed in 9.3 to return void)
     */

    public void compileLibrary(InputStream source, /*@Nullable*/ String encoding)
            throws XPathException, IOException {
        throw new UnsupportedOperationException("Separate compilation of query libraries requires Saxon-EE");
    }

    /**
     * Get a previously compiled library module
     *
     * @param namespace the module namespace
     * @return the QueryLibrary if a module with this namespace has been compiled as a library module;
     *         otherwise null. Always null when not using Saxon-EE.
     * @since 9.3
     */

    /*@Nullable*/
    public QueryLibrary getCompiledLibrary(NamespaceUri namespace) {
        return null;
    }

    public Iterable<QueryLibrary> getCompiledLibraries() {
        return Collections.emptySet();
    }


    /**
     * Declare a namespace whose prefix can be used in expressions. This is
     * equivalent to declaring a prefix in the Query prolog. It is not necessary
     * to declare the standard namespaces that are present in every XQuery module
     * by definition.
     * Any namespace declared in the Query prolog overrides a namespace declared using
     * this API.
     *
     * @param prefix The namespace prefix. Must not be null. Setting this to "" means that the
     *               namespace will be used as the default namespace for elements and types.
     * @param uri    The namespace URI. Must not be null. The value "" (zero-length string) is used
     *               to undeclare a namespace; it is not an error if there is no existing binding for
     *               the namespace prefix.
     * @throws NullPointerException     if either the prefix or URI is null
     * @throws IllegalArgumentException if the prefix is "xml" and the namespace is not the XML namespace, or vice
     *                                  versa; or if the prefix is "xmlns", or the URI is "http://www.w3.org/2000/xmlns/"
     * @since 9.0
     */

    public void declareNamespace(String prefix, NamespaceUri uri) {
        if (prefix == null) {
            throw new NullPointerException("Null prefix supplied to declareNamespace()");
        }
        if (uri == null) {
            throw new NullPointerException("Null namespace URI supplied to declareNamespace()");
        }
        if (prefix.equals("xml") != uri.equals(NamespaceUri.XML)) {
            throw new IllegalArgumentException("Misdeclaration of XML namespace");
        }
        if (prefix.equals("xmlns") || uri.equals(NamespaceUri.XMLNS)) {
            throw new IllegalArgumentException("Misdeclaration of xmlns namespace");
        }
        if (prefix.isEmpty()) {
            defaultElementNamespace = uri;
        }
        if (uri.isEmpty()) {
            predeclaredNamespaces = predeclaredNamespaces.remove(prefix);
        } else {
            predeclaredNamespaces = predeclaredNamespaces.put(prefix, uri);
        }

    }

    /**
     * Clear all the user-declared namespaces
     *
     * @since 9.0
     */

    public void clearNamespaces() {
        predeclaredNamespaces = NamespaceMap.STANDARD_QUERY_NAMESPACES;
//        declareNamespace("xml", NamespaceUri.XML);
//        declareNamespace("xs", NamespaceUri.SCHEMA);
//        declareNamespace("xsi", NamespaceUri.SCHEMA_INSTANCE);
//        declareNamespace("fn", NamespaceUri.FN);
//        declareNamespace("math", NamespaceUri.MATH);
//        declareNamespace("map", NamespaceUri.MAP_FUNCTIONS);
//        declareNamespace("array", NamespaceUri.ARRAY_FUNCTIONS);
//        declareNamespace("local", NamespaceUri.LOCAL);
//        declareNamespace("err", NamespaceUri.ERR);
//        declareNamespace("output", NamespaceUri.OUTPUT);
//        declareNamespace("xq", NamespaceUri.XQUERY);
//        declareNamespace("saxon", NamespaceUri.SAXON);
//        declareNamespace("", NamespaceUri.NULL);

    }

    /**
     * Get the map of user-declared namespaces
     *
     * @return the user-declared namespaces
     */

    protected NamespaceMap getPredeclaredNamespaces() {
        return predeclaredNamespaces;
    }

    /**
     * Get the namespace prefixes that have been declared using the method {@link #declareNamespace}
     *
     * @return an iterator that returns the namespace prefixes that have been explicitly declared, as
     *         strings. The default namespace for elements and types will be included, using the prefix "".
     * @since 9.0
     */

    public Iterator<String> iterateDeclaredPrefixes() {
        return predeclaredNamespaces.iteratePrefixes();
    }

    /**
     * Get the namespace URI for a given prefix, which must have been declared using the method
     * {@link #declareNamespace}.
     *
     * @param prefix the namespace prefix, or "" to represent the null prefix
     * @return the namespace URI. Returns "" to represent the non-namespace,
     *         null to indicate that the prefix has not been declared
     */

    public NamespaceUri getNamespaceForPrefix(String prefix) {
        return predeclaredNamespaces.getNamespaceUri(prefix);
    }

    /**
     * Get the default function namespace
     *
     * @return the default function namespace (defaults to the fn: namespace)
     * @since 8.4
     */

    public NamespaceUri getDefaultFunctionNamespace() {
        return defaultFunctionNamespace;
    }

    /**
     * Set the default function namespace
     *
     * @param defaultFunctionNamespace The namespace to be used for unprefixed function calls
     * @since 8.4
     */

    public void setDefaultFunctionNamespace(NamespaceUri defaultFunctionNamespace) {
        this.defaultFunctionNamespace = defaultFunctionNamespace;
    }

    /**
     * Set the default element namespace
     *
     * @param uri the namespace URI to be used as the default namespace for elements and types
     * @since 8.4
     */

    public void setDefaultElementNamespace(NamespaceUri uri) {
        defaultElementNamespace = uri;
        declareNamespace("", uri);
    }

    /**
     * Get the default namespace for elements and types
     *
     * @return the namespace URI to be used as the default namespace for elements and types
     * @since 8.9 Modified in 8.9 to return the namespace URI as a string rather than an integer code
     */

    /*@Nullable*/
    public NamespaceUri getDefaultElementNamespace() {
        return defaultElementNamespace;
    }

    /**
     * Get the matching policy for unprefixed element names in axis steps. This is a Saxon extension.
     * The value can be any of {@link UnprefixedElementMatchingPolicy#DEFAULT_NAMESPACE} (the default),
     * which uses the value of {@link #getDefaultElementNamespace()}, or {@link UnprefixedElementMatchingPolicy#DEFAULT_NAMESPACE_OR_NONE},
     * which matches both the namespace given in {@link #getDefaultElementNamespace()} and the null namespace,
     * or {@link UnprefixedElementMatchingPolicy#ANY_NAMESPACE}, which matches any namespace (that is, it
     * matches by local name only).
     */

    public UnprefixedElementMatchingPolicy getUnprefixedElementMatchingPolicy() {
        return unprefixedElementMatchingPolicy;
    }

    /**
     * Set the matching policy for unprefixed element names in axis steps. This is a Saxon extension.
     * The value can be any of {@link UnprefixedElementMatchingPolicy#DEFAULT_NAMESPACE} (the default),
     * which uses the value of {@link #getDefaultElementNamespace()}, or {@link UnprefixedElementMatchingPolicy#DEFAULT_NAMESPACE_OR_NONE},
     * which matches both the namespace given in {@link #getDefaultElementNamespace()} and the null namespace,
     * or {@link UnprefixedElementMatchingPolicy#ANY_NAMESPACE}, which matches any namespace (that is, it
     * matches by local name only).
     */
    public void setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy unprefixedElementMatchingPolicy) {
        this.unprefixedElementMatchingPolicy = unprefixedElementMatchingPolicy;
    }


    /**
     * Declare a global variable. This has the same effect as including a global variable declaration
     * in the Query Prolog of the main query module. A static error occurs when compiling the query if the
     * query prolog contains a declaration of the same variable.
     *
     * @param qName    the qualified name of the variable
     * @param type     the declared type of the variable
     * @param value    the initial value of the variable. May be null if the variable is external.
     * @param external true if the variable is external, that is, if its value may be set at run-time.
     * @throws NullPointerException if the value is null, unless the variable is external
     * @throws XPathException       if the value of the variable is not consistent with its type.
     * @since 9.1
     */

    public void declareGlobalVariable(
            StructuredQName qName, SequenceType type, GroundedValue value, boolean external)
            throws XPathException {
        if (value == null && !external) {
            throw new NullPointerException("No initial value for declared variable");
        }
        if (value != null && !type.matches(value)) {
            throw new XPathException("Value of declared variable does not match its type");
        }
        GlobalVariable var = external ? new GlobalParam() : new GlobalVariable();

        var.setVariableQName(qName);
        var.setRequiredType(type);
        if (value != null) {
            var.setBody(Literal.makeLiteral(value.materialize()));
        }
        if (userDeclaredVariables == null) {
            userDeclaredVariables = new HashSet<>();
        }
        userDeclaredVariables.add(var);
    }

    /**
     * Iterator over all the declared global variables
     *
     * @return an iterator over all the global variables that have been declared. They are returned
     *         as instances of class {@link GlobalVariable}
     * @since 9.1
     */

    public Iterable<GlobalVariable> iterateDeclaredGlobalVariables() {
        if (userDeclaredVariables == null) {
            return Collections.emptyList();
        } else {
            return userDeclaredVariables;
        }
    }

    /**
     * Clear all declared global variables
     *
     * @since 9.1
     */

    public void clearDeclaredGlobalVariables() {
        userDeclaredVariables = null;
    }

    /**
     * Set a user-defined ModuleURIResolver for resolving URIs used in "import module"
     * declarations in the XQuery prolog.
     * This will be used for resolving URIs in XQuery "import module" declarations, overriding
     * any ModuleURIResolver that was specified as part of the configuration.
     *
     * @param resolver the ModuleURIResolver to be used
     */

    public void setModuleURIResolver(ModuleURIResolver resolver) {
        moduleURIResolver = resolver;
    }

    /**
     * Get the user-defined ModuleURIResolver for resolving URIs used in "import module"
     * declarations in the XQuery prolog; returns null if none has been explicitly set either
     * on the StaticQueryContext or on the Configuration.
     *
     * @return the registered ModuleURIResolver
     */

    /*@Nullable*/
    public ModuleURIResolver getModuleURIResolver() {
        return moduleURIResolver;
    }


    /**
     * Set the default collation.
     *
     * @param name The collation name, as specified in the query prolog. Must be the name
     *             of a known registered collation.
     * @throws NullPointerException  if the supplied value is null
     * @throws IllegalStateException if the supplied value is not a known collation URI registered with the
     *                               configuration.
     * @since 8.4. Changed in 9.7.0.15 so that it validates the collation name: see bug 3121.
     */

    public void declareDefaultCollation(String name) {
        if (name == null) {
            throw new NullPointerException();
        }
        StringCollator c;
        try {
            c = getConfiguration().getCollation(name);
        } catch (XPathException e) {
            c = null;
        }
        if (c == null) {
            throw new IllegalStateException("Unknown collation " + name);
        }
        this.defaultCollationName = name;
    }

    /**
     * Get the name of the default collation.
     *
     * @return the name of the default collation; or the name of the codepoint collation
     *         if no default collation has been defined. The name is returned in the form
     *         it was specified; that is, it is not yet resolved against the base URI. (This
     *         is because the base URI declaration can follow the default collation declaration
     *         in the query prolog.) If no default collation has been specified, the "default default"
     *         (that is, the Unicode codepoint collation) is returned.
     * @since 8.4
     */

    /*@Nullable*/
    public String getDefaultCollationName() {
        return defaultCollationName;
    }

    /**
     * Declare the static type of the context item. If this type is declared, and if a context item
     * is supplied when the query is invoked, then the context item must conform to this type (no
     * type conversion will take place to force it into this type).
     *
     * @param type the required type of the context item
     */

    public void setRequiredContextItemType(ItemType type) {
        requiredContextValueType = SequenceType.one(type);
    }

    /**
     * Declare the static type of the context value. If this type is declared, and if a context value
     * is supplied when the query is invoked, then the context value must conform to this type (no
     * type conversion will take place to force it into this type).
     *
     * @param type the required type of the context item
     */

    public void setRequiredContextValueType(SequenceType type) {
        requiredContextValueType = type;
    }

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     *
     * @return the required type of the context item
     */

    public ItemType getRequiredContextItemType() {
        return requiredContextValueType.getPrimaryType();
    }

    /**
     * Get the required type of the context value. If no type has been explicitly declared for the context
     * value, an type representing the type item() is returned.
     *
     * @return the required type of the context value
     */

    public SequenceType getRequiredContextValueType() {
        return requiredContextValueType;
    }

    /**
     * Get the NamePool used for compiling expressions
     *
     * @return the name pool
     * @since 8.4
     */

    public NamePool getNamePool() {
        return namePool;
    }

    /**
     * Get the system ID of the container of the expression. Used to construct error messages.
     * Note that the systemID and the Base URI are currently identical, but they might be distinguished
     * in the future.
     *
     * @return the Base URI
     * @since 8.4
     */

    public String getSystemId() {
        return baseURI;
    }

    /**
     * Get the Base URI of the query, for resolving any relative URI's used
     * in the expression.
     * Note that the systemID and the Base URI are currently identical, but they might be distinguished
     * in the future.
     * Used by the document() function.
     *
     * @return the base URI of the query
     * @since 8.4
     */

    public String getBaseURI() {
        return baseURI;
    }

    /**
     * Set the policy for preserving boundary space
     *
     * @param preserve true if boundary space is to be preserved, false if it is to be stripped
     * @since 9.0
     */

    public void setPreserveBoundarySpace(boolean preserve) {
        preserveSpace = preserve;
    }

    /**
     * Ask whether the policy for boundary space is "preserve" or "strip"
     *
     * @return true if the policy is to preserve boundary space, false if it is to strip it
     * @since 9.0
     */

    public boolean isPreserveBoundarySpace() {
        return preserveSpace;
    }

    /**
     * Set the option for where an empty sequence appears in the collation order, if not otherwise
     * specified in the "order by" clause
     *
     * @param least true if the empty sequence is considered less than any other value (the default),
     *              false if it is considered greater than any other value
     * @since 9.0
     */

    public void setEmptyLeast(boolean least) {
        defaultEmptyLeast = least;
    }

    /**
     * Ask where an empty sequence should appear in the collation order, if not otherwise
     * specified in the "order by" clause
     *
     * @return true if the empty sequence is considered less than any other value (the default),
     *         false if it is considered greater than any other value
     * @since 9.0
     */

    public boolean isEmptyLeast() {
        return defaultEmptyLeast;
    }

    /**
     * Set the ErrorListener to be used to report compile-time errors in a query. This will also
     * be the default for the run-time error listener used to report dynamic errors.
     *
     * @param listener the ErrorListener to be used
     * @deprecated since 10.0: use {@link #setErrorReporter(ErrorReporter)}
     */
    @Deprecated
    public void setErrorListener(ErrorListener listener) {
        setErrorReporter(new ErrorReporterToListener(listener));
    }

    /**
     * Get the ErrorListener in use for this static context
     *
     * @return the registered ErrorListener
     * @deprecated since 10.0: use {@link #getErrorReporter()}
     */
    @Deprecated
    public ErrorListener getErrorListener() {
        if (errorReporter instanceof ErrorReporterToListener) {
            return ((ErrorReporterToListener) errorReporter).getErrorListener();
        } else {
            return null;
        }
    }

    /**
     * Set an error reporter: that is, a used-supplied object that is to receive
     * notification of static errors found in the stylesheet
     *
     * @param reporter the object to be notified of static errors
     * @since 10.0
     */

    public void setErrorReporter(ErrorReporter reporter) {
        this.errorReporter = reporter;
    }

    /**
     * Get the error reporter: that is, a used-supplied object that is to receive
     * notification of static errors found in the stylesheet
     *
     * @return the object to be notified of static errors. This may be the error reporter
     * that was previously set using {@link #setErrorReporter(ErrorReporter)}, or it may be
     * a system-allocated error reporter.
     * @since 10.0
     */

    public ErrorReporter getErrorReporter() {
        return this.errorReporter;
    }


    /**
     * Say whether the query is allowed to be updating. XQuery update syntax will be rejected
     * during query compilation unless this flag is set.
     *
     * @param updating true if the query is allowed to use the XQuery Update facility
     *                 (requires Saxon-EE). If set to false, the query must not be an updating query. If set
     *                 to true, it may be either an updating or a non-updating query.
     * @since 9.1
     */

    public void setUpdatingEnabled(boolean updating) {
        this.updating = updating;
    }

    /**
     * Ask whether the query is allowed to be updating
     *
     * @return true if the query is allowed to use the XQuery Update facility. Note that this
     *         does not necessarily mean that the query is an updating query; but if the value is false,
     *         the it must definitely be non-updating.
     * @since 9.1
     */

    public boolean isUpdatingEnabled() {
        return updating;
    }

    public void preLoadSchema(Schema schema) {
        preLoadedSchema = schema;
    }

    public Schema getPreLoadedSchema() {
        return preLoadedSchema;
    }

}

