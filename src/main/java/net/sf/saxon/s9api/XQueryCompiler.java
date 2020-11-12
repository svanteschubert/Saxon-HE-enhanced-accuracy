////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.OptimizerOptions;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.ModuleURIResolver;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.query.StaticQueryContext;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingAbort;

import javax.xml.transform.ErrorListener;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * An {@code XQueryCompiler} object allows XQuery 1.0 queries to be compiled. The compiler holds information that
 * represents the static context for the compilation.
 * <p>To construct an {@code XQueryCompiler}, use the factory method {@link Processor#newXQueryCompiler}.</p>
 * <p>An {@code XQueryCompiler} may be used repeatedly to compile multiple queries. Any changes made to the
 * XQueryCompiler (that is, to the static context) do not affect queries that have already been compiled.
 * An {@code XQueryCompiler} may in principle be used concurrently in multiple threads, but in practice this
 * is best avoided because all instances will share the same {@code ErrorReporter} or {@code ErrorListener}
 * and it will therefore be difficult to establish which error messages are associated with each compilation.</p>
 *
 * @since 9.0
 */

public class XQueryCompiler {

    private Processor processor;
    private StaticQueryContext staticQueryContext;
    private ItemType requiredContextItemType;
    private String encoding;

    /**
     * Protected constructor
     *
     * @param processor the Saxon Processor
     */

    protected XQueryCompiler(Processor processor) {
        this.processor = processor;
        this.staticQueryContext = processor.getUnderlyingConfiguration().newStaticQueryContext();
    }

    /**
     * Get the Processor from which this XQueryCompiler was constructed
     *
     * @return the Processor to which this XQueryCompiler belongs
     * @since 9.3
     */

    public Processor getProcessor() {
        return processor;
    }

    /**
     * Set the static base URI for the query
     *
     * @param baseURI the static base URI; or null to indicate that no base URI is available
     */

    public void setBaseURI(URI baseURI) {
        if (baseURI == null) {
            staticQueryContext.setBaseURI(null);
        } else {
            if (!baseURI.isAbsolute()) {
                throw new IllegalArgumentException("Base URI must be an absolute URI: " + baseURI);
            }
            staticQueryContext.setBaseURI(baseURI.toString());
        }
    }

    /**
     * Get the static base URI for the query
     *
     * @return the static base URI
     */

    public URI getBaseURI() {
        try {
            return new URI(staticQueryContext.getBaseURI());
        } catch (URISyntaxException err) {
            throw new IllegalStateException(err);
        }
    }

    /**
     * Set the ErrorListener to be used during this query compilation episode.
     *
     * <p>This method is obsoleted by {@link #setErrorReporter(ErrorReporter)}, and its effect
     * is to set an error reporter that delegates to the supplied {@link ErrorListener}.</p>
     *
     * @param listener The error listener to be used. This is notified of all errors detected during the
     *                 compilation. Static errors (as defined in the XQuery specification) are notified
     *                 to the {@link ErrorListener#fatalError(javax.xml.transform.TransformerException)} method.
     *                 Warnings are notified to the {@link ErrorListener#warning(javax.xml.transform.TransformerException)}.
     *                 Any exception thrown by these methods is ignored.
     */

    public void setErrorListener(ErrorListener listener) {
        staticQueryContext.setErrorListener(listener);
    }

    /**
     * Get the ErrorListener being used during this compilation episode
     *
     * @return listener The error listener in use. This is notified of all errors detected during the
     * compilation. If no user-supplied ErrorListener has been set, returns the system-supplied
     * ErrorListener.
     */

    public ErrorListener getErrorListener() {
        return staticQueryContext.getErrorListener();
    }

    /**
     * Supply a callback which will be notified of all static errors and warnings
     * encountered during a compilation carried out using this {@code XQueryCompiler}.
     * <p>Calling this method overwrites the effect of any previous call on {@link #setErrorListener(ErrorListener)}
     * or {@code setErrorList}.</p>
     * <p>If no error reporter is supplied by the caller, error information will be written to the standard error stream.
     * Applications should then ensure that the contents of this stream are made visible to the query author.</p>
     * <p>This error reporter is <b>not</b> used for dynamic errors occurring during query execution.</p>
     * <p>Note that if multiple compilations are carried out concurrently in different threads using
     * the same {@code XQueryCompiler}, then the {@link ErrorReporter} must be thread-safe; messages from different
     * compilations will be interleaved, and there is no obvious way of determining which message originated
     * from which compilation. In practice, it is only sensible to do this in an environment
     * where the queries are known to be error-free.</p>
     *
     * @param reporter a callback function which will be notified of all Static errors and warnings
     *                 encountered during a compilation episode.
     * @since 10.0
     */

    public void setErrorReporter(ErrorReporter reporter) {
        staticQueryContext.setErrorReporter(reporter);
    }

    /**
     * Get the recipient of error information previously registered using {@link #setErrorReporter(ErrorReporter)}.
     *
     * @return the recipient previously registered explicitly using {@link #setErrorReporter(ErrorReporter)},
     * or implicitly using {@link #setErrorListener(ErrorListener)} or {@link #setErrorList(List)}.
     * If no error reporter has been registered, the result may be null, or may return
     * a system supplied error reporter.
     * @since 10.0
     */

    public ErrorReporter getErrorReporter() {
        return staticQueryContext.getErrorReporter();
    }


    /**
     * Set whether trace hooks are to be included in the compiled code. To use tracing, it is necessary
     * both to compile the code with trace hooks included, and to supply a TraceListener at run-time
     *
     * @param option true if trace code is to be compiled in, false otherwise
     */

    public void setCompileWithTracing(boolean option) {
        staticQueryContext.setCompileWithTracing(option);
    }

    /**
     * Ask whether trace hooks are included in the compiled code.
     *
     * @return true if trace hooks are included, false if not.
     */

    public boolean isCompileWithTracing() {
        return staticQueryContext.isCompileWithTracing();
    }

    /**
     * Set a user-defined ModuleURIResolver for resolving URIs used in <code>import module</code>
     * declarations in the XQuery prolog.
     * This will override any ModuleURIResolver that was specified as part of the configuration.
     *
     * @param resolver the ModuleURIResolver to be used
     */

    public void setModuleURIResolver(ModuleURIResolver resolver) {
        staticQueryContext.setModuleURIResolver(resolver);
    }

    /**
     * Get the user-defined ModuleURIResolver for resolving URIs used in <code>import module</code>
     * declarations in the XQuery prolog; returns null if none has been explicitly set either
     * here or in the Saxon Configuration.
     *
     * @return the registered ModuleURIResolver
     */

    /*@Nullable*/
    public ModuleURIResolver getModuleURIResolver() {
        return staticQueryContext.getModuleURIResolver();
    }

    /**
     * Set the encoding of the supplied query. This is ignored if the query is supplied
     * in character form, that is, as a <code>String</code> or as a <code>Reader</code>. If no value
     * is set, the query processor will attempt to infer the encoding, defaulting to UTF-8 if no
     * information is available.
     *
     * @param encoding the encoding of the supplied query, for example "iso-8859-1"
     * @since 9.1
     */

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Get the encoding previously set for the supplied query.
     *
     * @return the encoding previously set using {@link #setEncoding(String)}, or null
     * if no value has been set. Note that this is not necessarily the actual encoding of the query.
     * @since 9.2
     */

    public String getEncoding() {
        return encoding;
    }

    /**
     * Say whether the query is allowed to be updating. XQuery update syntax will be rejected
     * during query compilation unless this flag is set. XQuery Update is supported only under Saxon-EE.
     *
     * @param updating true if the query is allowed to use the XQuery Update facility
     *                 (requires Saxon-EE). If set to false, the query must not be an updating query. If set
     *                 to true, it may be either an updating or a non-updating query.
     * @throws UnsupportedOperationException if updating is requested and the Saxon Configuration does
     *                                       not support updating, either because it is not an EnterpriseConfiguration, or because no license
     *                                       key is available.
     * @since 9.1
     */

    public void setUpdatingEnabled(boolean updating) {
        if (updating && !staticQueryContext.getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY)) {
            throw new UnsupportedOperationException("XQuery Update is not supported in this Saxon Configuration");
        }
        staticQueryContext.setUpdatingEnabled(updating);
    }

    /**
     * Ask whether the query is allowed to use XQuery Update syntax
     *
     * @return true if the query is allowed to use the XQuery Update facility. Note that this
     * does not necessarily mean that the query is an updating query; but if the value is false,
     * then it must definitely be non-updating.
     * @since 9.1
     */

    public boolean isUpdatingEnabled() {
        return staticQueryContext.isUpdatingEnabled();
    }

    /**
     * Say that the query must be compiled to be schema-aware, even if it contains no
     * "import schema" declarations. Normally a query is treated as schema-aware
     * only if it contains one or more "import schema" declarations. If it is not schema-aware,
     * then all input documents must be untyped (or xs:anyType), and validation of temporary nodes is disallowed
     * (though validation of the final result tree is permitted). Setting the argument to true
     * means that schema-aware code will be compiled regardless.
     *
     * @param schemaAware If true, the stylesheet will be compiled with schema-awareness
     *                    enabled even if it contains no xsl:import-schema declarations. If false, the stylesheet
     *                    is treated as schema-aware only if it contains one or more xsl:import-schema declarations.
     *                    Note that setting the value to false does not disable use of an import-schema declaration.
     * @since 9.2
     */

    public void setSchemaAware(boolean schemaAware) {
        // We check this again more securely, but it's good to give the error as soon as possible
        if (schemaAware && !processor.getUnderlyingConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY)) {
            throw new UnsupportedOperationException("Schema-awareness requires a Saxon-EE license");
        }
        staticQueryContext.setSchemaAware(schemaAware);
    }

    /**
     * Ask whether schema-awareness has been requested either by means of a call on
     * {@link #setSchemaAware}
     *
     * @return true if schema-awareness has been requested
     * @since 9.2
     */

    public boolean isSchemaAware() {
        return staticQueryContext.isSchemaAware();
    }

    /**
     * Say whether the query should be compiled and evaluated to use streaming.
     * This affects subsequent calls on the compile() methods. This option requires
     * Saxon-EE.
     *
     * @param option if true, the compiler will attempt to compile a query to be
     *               capable of executing in streaming mode. If the query cannot be streamed,
     *               a compile-time exception is reported. In streaming mode, the source
     *               document is supplied as a stream, and no tree is built in memory. The default
     *               is false.
     *               <p>
     *               <p>When setStreaming(true) is specified, this has the additional side-effect of setting the required
     *               context item type to "document-node()"
     * @since 9.6
     */

    public void setStreaming(boolean option) {
        staticQueryContext.setStreaming(option);
        // We check this again more securely, but it's good to give the error as soon as possible
        if (option && !processor.getUnderlyingConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY)) {
            throw new UnsupportedOperationException("Streaming requires a Saxon-EE license");
        }
        if (option) {
            setRequiredContextItemType(new ConstructedItemType(NodeKindTest.DOCUMENT, getProcessor()));
        }
    }

    /**
     * Ask whether the streaming option has been set, that is, whether
     * subsequent calls on compile() will compile queries to be capable
     * of executing in streaming mode.
     *
     * @return true if the streaming option has been set.
     * @since 9.6
     */

    public boolean isStreaming() {
        return staticQueryContext.isStreaming();
    }

    /**
     * Ask whether an XQuery 1.0 or XQuery 3.0 or XQuery 3.1 processor is being used
     *
     * @return always "3.1" in the current Saxon release.
     * @since 9.2. From Saxon 9.8, only XQuery 3.1 is supported
     */

    public String getLanguageVersion() {
        return "3.1";
    }

    /**
     * Declare a namespace binding as part of the static context for queries compiled using this
     * XQueryCompiler. This binding may be overridden by a binding that appears in the query prolog.
     * The namespace binding will form part of the static context of the query, but it will not be copied
     * into result trees unless the prefix is actually used in an element or attribute name.
     *
     * @param prefix The namespace prefix. If the value is a zero-length string, this method sets the default
     *               namespace for elements and types.
     * @param uri    The namespace URI. It is possible to specify a zero-length string to "undeclare" a namespace;
     *               in this case the prefix will not be available for use, except in the case where the prefix
     *               is also a zero length string, in which case the absence of a prefix implies that the name
     *               is in no namespace.
     * @throws NullPointerException     if either the prefix or uri is null.
     * @throws IllegalArgumentException in the event of an invalid declaration of the XML namespace
     */

    public void declareNamespace(String prefix, String uri) {
        staticQueryContext.declareNamespace(prefix, uri);
    }

    /**
     * Declare the default collation
     *
     * @param uri the absolute URI of the default collation. This URI must have been bound to a collation
     *            using the method {@link Configuration#registerCollation(String, StringCollator)}, or it must be
     *            one that is recognized implicitly, such as a UCA collation
     * @throws NullPointerException  if the collation URI is null
     * @throws IllegalStateException if the collation URI has not been registered, unless it is the standard
     *                               Unicode codepoint collation which is registered implicitly
     * @since 9.4
     */

    public void declareDefaultCollation(String uri) {
        staticQueryContext.declareDefaultCollation(uri);
    }


    /**
     * Declare the static type of the context item. If this type is declared, and if a context item
     * is supplied when the query is invoked, then the context item must conform to this type (no
     * type conversion will take place to force it into this type).
     *
     * @param type the required type of the context item
     */

    public void setRequiredContextItemType(ItemType type) {
        requiredContextItemType = type;
        staticQueryContext.setRequiredContextItemType(type.getUnderlyingItemType());
    }

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     *
     * @return the required type of the context item
     */

    public ItemType getRequiredContextItemType() {
        return requiredContextItemType;
    }

    /**
     * Request fast compilation. Fast compilation will generally be achieved at the expense of run-time performance
     * and quality of diagnostics. Fast compilation is a good trade-off if (a) the expression is known to be correct,
     * and (b) once compiled, the expression is only executed once against a document of modest size.
     * <p><i>The current implementation is equivalent to switching off all optimizations. Setting this option, however,
     * indicates an intent rather than a mechanism, and the implementation details may change in future to reflect
     * the intent.</i></p>
     *
     * @param fast set to true to request fast compilation; set to false to revert to the optimization options
     *             defined in the Configuration.
     * @since 9.9
     */

    public void setFastCompilation(boolean fast) {
        if (fast) {
            staticQueryContext.setOptimizerOptions(new OptimizerOptions(0));
        } else {
            staticQueryContext.setOptimizerOptions(getProcessor().getUnderlyingConfiguration().getOptimizerOptions());
        }
    }

    /**
     * Ask if fast compilation has been enabled.
     *
     * @return true if fast compilation has been enabled
     * @since 9.9
     */

    public boolean isFastCompilation() {
        return staticQueryContext.getOptimizerOptions().getOptions() == 0;
    }

    /**
     * Compile a library module supplied as a string. The code generated by compiling the library is available
     * for importing by all subsequent compilations using the same XQueryCompiler; it is identified by an
     * "import module" declaration that specifies the module URI of the library module. No module location
     * hint is required, and if one is present, it is ignored.
     * <p>The base URI of the query should be supplied by calling {@link #setBaseURI(java.net.URI)} </p>
     * <p>Separate compilation of library modules is supported only under Saxon-EE</p>
     *
     * @param query the text of the query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @since 9.2
     */

    public void compileLibrary(String query) throws SaxonApiException {
        try {
            staticQueryContext.compileLibrary(query);
        } catch (XPathException | UncheckedXPathException | XmlProcessingAbort e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a library module  supplied as a file. The code generated by compiling the library is available
     * for importing by all subsequent compilations using the same XQueryCompiler; it is identified by an
     * "import module" declaration that specifies the module URI of the library module. No module location
     * hint is required, and if one is present, it is ignored.
     * <p>The encoding of the input stream may be specified using {@link #setEncoding(String)};
     * if this has not been set, the compiler determines the encoding from the version header of the
     * query, and if that too is absent, it assumes UTF-8.</p>
     * <p>Separate compilation of library modules is supported only under Saxon-EE</p>
     *
     * @param query the file containing the query. The URI corresponding to this file will be used as the
     *              base URI of the query, overriding any URI supplied using {@link #setBaseURI(java.net.URI)} (but not
     *              overriding any base URI specified within the query prolog)
     * @throws SaxonApiException if the query compilation fails with a static error
     * @throws IOException       if the file does not exist or cannot be read
     * @since 9.2
     */

    public void compileLibrary(File query) throws SaxonApiException, IOException {
        try (FileInputStream stream = new FileInputStream(query)) {
            String savedBaseUri = staticQueryContext.getBaseURI();
            staticQueryContext.setBaseURI(query.toURI().toString());
            staticQueryContext.compileLibrary(stream, encoding);
            staticQueryContext.setBaseURI(savedBaseUri);
        } catch (XPathException | UncheckedXPathException | XmlProcessingAbort e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a library module supplied as a Reader. The code generated by compiling the library is available
     * for importing by all subsequent compilations using the same XQueryCompiler; it is identified by an
     * "import module" declaration that specifies the module URI of the library module. No module location
     * hint is required, and if one is present, it is ignored.
     * <p>The base URI of the query should be supplied by calling {@link #setBaseURI(java.net.URI)} </p>
     * <p>Separate compilation of library modules is supported only under Saxon-EE</p>
     *
     * @param query the text of the query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @since 9.2
     */

    public void compileLibrary(Reader query) throws SaxonApiException {
        try {
            staticQueryContext.compileLibrary(query);
        } catch (XPathException | IOException | UncheckedXPathException | XmlProcessingAbort e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a library module supplied as an InputStream. The code generated by compiling the library is available
     * for importing by all subsequent compilations using the same XQueryCompiler; it is identified by an
     * "import module" declaration that specifies the module URI of the library module. No module location
     * hint is required, and if one is present, it is ignored.
     * <p>The encoding of the input stream may be specified using {@link #setEncoding(String)};
     * if this has not been set, the compiler determines the encoding from the version header of the
     * query, and if that too is absent, it assumes UTF-8. </p>
     * <p>The base URI of the query should be supplied by calling {@link #setBaseURI(java.net.URI)} </p>
     * <p>Separate compilation of library modules is supported only under Saxon-EE</p>
     *
     * @param query the text of the query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @since 9.2
     */

    public void compileLibrary(InputStream query) throws SaxonApiException {
        try {
            staticQueryContext.compileLibrary(query, encoding);
        } catch (XPathException | IOException | UncheckedXPathException | XmlProcessingAbort e ) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a query supplied as a string.
     * <p>The base URI of the query should be supplied by calling {@link #setBaseURI(java.net.URI)} </p>
     * <p>If the query contains static errors, these will be notified to the registered {@link ErrorListener}.
     * More than one static error may be reported. If any static errors have been reported, this method
     * will exit with an exception, but the exception will only contain a summary message. The default
     * {@link ErrorListener} writes details of each error to the <code>System.err</code> output stream.</p>
     *
     * @param query the text of the query
     * @return an XQueryExecutable representing the compiled query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @since 9.0
     */

    public XQueryExecutable compile(String query) throws SaxonApiException {
        try {
            return new XQueryExecutable(processor, staticQueryContext.compileQuery(query));
        } catch (UncheckedXPathException e) {
            throw new SaxonApiException(e.getXPathException());
        } catch (XPathException | XmlProcessingAbort e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a query supplied as a file
     *
     * @param query the file containing the query. The URI corresponding to this file will be used as the
     *              base URI of the query, overriding any URI supplied using {@link #setBaseURI(java.net.URI)} (but not
     *              overriding any base URI specified within the query prolog)
     * @return an XQueryExecutable representing the compiled query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @throws IOException       if the file does not exist or cannot be read
     * @since 9.1
     */

    public XQueryExecutable compile(File query) throws SaxonApiException, IOException {
        try (FileInputStream stream = new FileInputStream(query)) {
            String savedBaseUri = staticQueryContext.getBaseURI();
            staticQueryContext.setBaseURI(query.toURI().toString());
            XQueryExecutable exec =
                    new XQueryExecutable(processor, staticQueryContext.compileQuery(stream, encoding));
            staticQueryContext.setBaseURI(savedBaseUri);
            return exec;
        } catch (UncheckedXPathException e) {
            throw new SaxonApiException(e.getXPathException());
        } catch (XPathException | XmlProcessingAbort e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a query supplied as an InputStream
     * <p>The base URI of the query should be supplied by calling {@link #setBaseURI(java.net.URI)} </p>
     *
     * @param query the input stream on which the query is supplied. This will be consumed by this method
     * @return an XQueryExecutable representing the compiled query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @since 9.1
     */

    public XQueryExecutable compile(InputStream query) throws SaxonApiException {
        try {
            return new XQueryExecutable(processor, staticQueryContext.compileQuery(query, encoding));
        } catch (UncheckedXPathException e) {
            throw new SaxonApiException(e.getXPathException());
        } catch (XPathException | XmlProcessingAbort e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a query supplied as a Reader
     * <p>The base URI of the query should be supplied by calling {@link #setBaseURI(java.net.URI)} </p>
     *
     * @param query the input stream on which the query is supplied. This will be consumed by this method
     * @return an XQueryExecutable representing the compiled query
     * @throws SaxonApiException if the query compilation fails with a static error
     * @throws IOException       if the file does not exist or cannot be read
     * @since 9.1
     */

    public XQueryExecutable compile(Reader query) throws SaxonApiException, IOException {
        try {
            return new XQueryExecutable(processor, staticQueryContext.compileQuery(query));
        } catch (UncheckedXPathException e) {
            throw new SaxonApiException(e.getXPathException());
        } catch (XPathException | XmlProcessingAbort e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get the underlying {@link net.sf.saxon.query.StaticQueryContext} object that maintains the static context
     * information on behalf of this XQueryCompiler. This method provides an escape hatch to internal Saxon
     * implementation objects that offer a finer and lower-level degree of control than the s9api classes and
     * methods. Some of these classes and methods may change from release to release.
     *
     * @return the underlying StaticQueryContext object
     * @since 9.2
     */

    public StaticQueryContext getUnderlyingStaticContext() {
        return staticQueryContext;
    }

    /**
     * List of errors. The caller should supply an empty list before calling Compile; the processor will then populate
     * the list with error information obtained during the compilation. Each error will be included as an object of type
     * {@link StaticError}.
     * <p>If no error list is supplied by the caller, error information will be written to the standard error stream.
     * Applications should then ensure that the contents of this stream are made visible to the query author.</p>
     * <p>By supplying a custom List with a user-written add() method, it is possible to intercept error conditions as they occur.
     * Such a custom List can conveniently be implemented by extending {@link java.util.AbstractList}, in which case the
     * only methods that need to be provided are {@code java.util.AbstractList#get}, {@code java.util.AbstractList#size},
     * and {@code java.util.AbstractList#add}; and the first two of these can be dummy implementations.</p>
     * <p>Calling this method is equivalent to calling <code>setErrorReporter(errorList::add)</code>: that is,
     * it registers an {@link ErrorReporter} whose {@link ErrorReporter#report} method adds the error to the list.</p>
     *
     * @param errorList a (typically empty) list that will be populated with {@link StaticError} objects giving details
     *                  of any static errors found in the query.
     * @since 9.9. Redefined in 10.0 as a convenience wrapper over the new {@link #setErrorReporter(ErrorReporter)} method.
     */
    public void setErrorList(List<? super StaticError> errorList) {
        setErrorReporter(errorList::add);
    }

}

