////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.expr.parser.OptimizerOptions;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.style.*;
import net.sf.saxon.trace.XSLTTraceCodeInjector;
import net.sf.saxon.trans.CompilerInfo;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingAbort;
import net.sf.saxon.trans.packages.IPackageLoader;
import net.sf.saxon.trans.packages.PackageDetails;
import net.sf.saxon.trans.packages.PackageLibrary;
import net.sf.saxon.trans.packages.VersionedPackageName;
import net.sf.saxon.tree.linked.DocumentImpl;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * An XsltCompiler object allows XSLT 2.0 and XSLT 3.0 stylesheets to be compiled. The compiler holds information that
 * represents the static context for the compilation.
 * <p>To construct an {@code XsltCompiler}, use the factory method {@link Processor#newXsltCompiler} on the Processor object.</p>
 * <p>An {@code XsltCompiler} may be used repeatedly to compile multiple queries. Any changes made to the
 * {@code XsltCompiler} (that is, to the static context) do not affect queries that have already been compiled.
 * An XsltCompiler may in principle be used concurrently in multiple threads, but in practice this
 * is best avoided because all instances will share the same ErrorListener and it may therefore be
 * difficult to establish which error messages are associated with each compilation.</p>
 * <p>If JIT compilation is enabled (this is the default for Saxon-EE), then static errors in template
 *  rules might be detected not during execution of the <code>compile()</code> method, but rather
 *  when the relevant code is first executed. In this situation the <code>compile()</code> method will
 *  not throw an exception, but the errors will still (eventually) be notified to the <code>ErrorListener</code>
 *  associated with the compiler.</p>
 *  <p>To avoid problems with error reporting, it is recommended that:
 *  <ul>
 *      <li>When code is under development and static errors are therefore likely, a new
 *      {@code XsltCompiler} should be created for each compilation (and JIT should preferably
 *      be disabled, so that all static errors are detected.)</li>
 *      <li>When production code is being loaded, and errors are therefore unlikely,
 *      it is safe to use the same {@code XsltCompiler} for all compilations, and to
 *      enable JIT for maximum performance.</li>
 *  </ul>
 *
 * @since 9.0
 */
public class XsltCompiler {

    private Processor processor;
    private Configuration config;
    private CompilerInfo compilerInfo;


    /**
     * Protected constructor. The public way to create an <tt>XsltCompiler</tt> is by using the factory method
     * {@link Processor#newXsltCompiler} .
     *
     * @param processor the Saxon processor
     */

    protected XsltCompiler(Processor processor) {
        this.processor = processor;
        this.config = processor.getUnderlyingConfiguration();
        compilerInfo = new CompilerInfo(config.getDefaultXsltCompilerInfo());
        compilerInfo.setGenerateByteCode(config.isGenerateByteCode(HostLanguage.XSLT));
        compilerInfo.setTargetEdition(config.getEditionCode());
        compilerInfo.setJustInTimeCompilation(config.isJITEnabled());
    }

    /**
     * Get the Processor from which this XsltCompiler was constructed
     *
     * @return the Processor to which this XsltCompiler belongs
     * @since 9.3
     */

    public Processor getProcessor() {
        return processor;
    }

    /**
     * Set the URIResolver to be used during stylesheet compilation. This URIResolver, despite its name,
     * is <b>not</b> used for resolving relative URIs against a base URI; it is used for dereferencing
     * an absolute URI (after resolution) to return a {@link javax.xml.transform.Source} representing the
     * location where a stylesheet module can be found.
     * <p>This URIResolver is used to dereference the URIs appearing in <code>xsl:import</code>,
     * <code>xsl:include</code>, and <code>xsl:import-schema</code> declarations. It is not used
     * for resolving the URI supplied for the main stylesheet module (as supplied to the
     * {@link #compile(javax.xml.transform.Source)} or {@link #compilePackage(javax.xml.transform.Source)} methods.
     * It is not used at run-time for resolving requests to the <code>document()</code> or similar functions.
     * (Instead, such functions use the run-time URIResolver passed to the {@link XsltTransformer}
     * or {@link Xslt30Transformer}).</p>
     *
     * @param resolver the URIResolver to be used during stylesheet compilation.
     */

    public void setURIResolver(URIResolver resolver) {
        compilerInfo.setURIResolver(resolver);
    }

    /**
     * Set the value of a stylesheet parameter. Static (compile-time) parameters must be provided using
     * this method on the XsltCompiler object, prior to stylesheet compilation. Non-static parameters
     * may also be provided using this method if their values will not vary from one transformation
     * to another.
     *
     * @param name  the qualified name identifying the parameter, as declared in a global <code>&lt;xsl:param&gt;</code>
     *              element in the stylesheet
     * @param value the value of the parameter
     * @throws SaxonApiUncheckedException if the value is lazily evaluated, and evaluation fails
     */
    public void setParameter(QName name, XdmValue value) {
        try {
            compilerInfo.setParameter(name.getStructuredQName(),
                                      ((Sequence) value.getUnderlyingValue()).materialize());
        } catch (XPathException e) {
            throw new SaxonApiUncheckedException(e);
        }
    }

    /**
     * Clear the values of all stylesheet parameters previously set using {@link #setParameter(QName, XdmValue)}.
     * This resets the parameters to their initial ("undeclared") state
     */

    public void clearParameters() {
        compilerInfo.clearParameters();
    }

    /**
     * Get the URIResolver to be used during stylesheet compilation.
     *
     * @return the URIResolver used during stylesheet compilation. Returns null if no user-supplied
     * URIResolver has been set.
     */

    public URIResolver getURIResolver() {
        return compilerInfo.getURIResolver();
    }

    /**
     * Set the ErrorListener to be used during this compilation episode
     *
     * @param listener The error listener to be used. This is notified of all errors detected during the
     *                 compilation.
     *
     *                 <p><i>In earlier releases, contrary to the documentation, the supplied
     *                 {@code ErrorListener} was also notified of run-time errors, unless a different
     *                 {@code ErrorListener} was supplied at run-time. This is no longer the case from
     *                 Saxon 10.0</i></p>
     * @deprecated since 10.0. Use {@link #setErrorReporter(ErrorReporter)}
     */

    public void setErrorListener(ErrorListener listener) {
        compilerInfo.setErrorListener(listener);
    }

    /**
     * Get the ErrorListener being used during this compilation episode
     *
     * @return listener The error listener in use. This is notified of all errors detected during the
     * compilation. Returns null if no user-supplied ErrorListener has been set.
     * @deprecated since 10.0. Use {@link #getErrorReporter()}
     */

    public ErrorListener getErrorListener() {
        return compilerInfo.getErrorListener();
    }

    /**
     * Supply a (typically empty) {@code List} which will be populated with information about any static errors
     * encountered during the transformation.
     * <p>Calling this method overwrites the effect of any previous call on {@link #setErrorListener(ErrorListener)}
     * as well as previous calls on {@code setErrorList}.</p>
     *
     * @param errorList a List (typically empty) to which information will be appended about
     *                  static errors found during the compilation. Each such error is represented by a
     *                  {@link XmlProcessingError} object.
     * @since 9.9.
     */

    public void setErrorList(List<? super StaticError> errorList) {
        compilerInfo.setErrorReporter(errorList::add);
    }

    /**
     * Supply a callback which will be notified of all static errors and warnings
     * encountered during a compilation carried out using this {@code XsltCompiler}.
     * <p>Calling this method overwrites the effect of any previous call on {@link #setErrorListener(ErrorListener)}
     * or {@code setErrorList}.</p>
     * <p>Note that if multiple compilations are carried out concurrently in different threads using
     * the same {@code XsltCompiler}, then the {@link ErrorReporter} must be thread-safe; messages from different
     * compilations will be interleaved, and there is no obvious way of determining which message originated
     * from which compilation. In practice, it is only sensible to do this in an environment
     * where the stylesheets being compiled are known to be error-free.</p>
     *
     * @param reporter a Consumer which will be notified of all Static errors and warnings
     *                 encountered during a compilation episode.
     * @since 10.0
     */

    public void setErrorReporter(ErrorReporter reporter) {
        compilerInfo.setErrorReporter(reporter);
    }

    /**
     * Get the recipient of error information previously registered using {@link #setErrorReporter(ErrorReporter)}.
     *
     * @return the consumer previously registered explicitly using {@link #setErrorReporter(ErrorReporter)},
     * or implicitly using {@link #setErrorListener(ErrorListener)} or {@link #setErrorList(List)}.
     * If no error reporter has been registered, the result may be null, or may return
     * a system supplied error reporter.
     * @since 10.0
     */

    public ErrorReporter getErrorReporter() {
        return compilerInfo.getErrorReporter();
    }


    /**
     * Say that the stylesheet must be compiled to be schema-aware, even if it contains no
     * xsl:import-schema declarations. Normally a stylesheet is treated as schema-aware
     * only if it contains one or more xsl:import-schema declarations. If it is not schema-aware,
     * then all input documents must be untyped, and validation of temporary trees is disallowed
     * (though validation of the final result tree is permitted). Setting the argument to true
     * means that schema-aware code will be compiled regardless.
     *
     * @param schemaAware If true, the stylesheet will be compiled with schema-awareness
     *                    enabled even if it contains no xsl:import-schema declarations. If false, the stylesheet
     *                    is treated as schema-aware only if it contains one or more xsl:import-schema declarations.
     * @since 9.2
     */

    public void setSchemaAware(boolean schemaAware) {
        compilerInfo.setSchemaAware(schemaAware);
    }

    /**
     * Ask whether schema-awareness has been requested by means of a call on
     * {@link #setSchemaAware}
     *
     * @return true if schema-awareness has been requested
     * @since 9.2
     */

    public boolean isSchemaAware() {
        return compilerInfo.isSchemaAware();
    }

    /**
     * Ask whether any package produced by this compiler can be deployed to a different location, with a different base URI
     *
     * @return if true then static-base-uri() represents the deployed location of the package,
     *                    rather than its compile time location
     * @since 9.8
     */

    public boolean isRelocatable() {
        return compilerInfo.isRelocatable();
    }

    /**
     * Say whether any package produced by this compiler can be deployed to a different location, with a different base URI
     *
     * @param relocatable if true then static-base-uri() represents the deployed location of the package,
     *                    rather than its compile time location
     * @since 9.8
     * */

    public void setRelocatable(boolean relocatable) {
        compilerInfo.setRelocatable(relocatable);
    }

    /**
     * Set the target edition under which the stylesheet will be executed.
     *
     * @param edition the Saxon edition for the run-time environment. One of "EE", "PE", "HE", or "JS".
     * @since 9.7.0.5. Experimental and subject to change.
     */

    public void setTargetEdition(String edition) {
        if (!("EE".equals(edition) || "PE".equals(edition) || "HE".equals(edition) || "JS".equals(edition))) {
            throw new IllegalArgumentException("Unknown Saxon edition " + edition);
        }
        compilerInfo.setTargetEdition(edition);
    }

    /**
     * Get the target edition under which the stylesheet will be executed.
     *
     * @return the Saxon edition for the run-time environment. One of "EE", "PE", "HE", or "JS".
     * @since 9.7.0.5. Experimental and subject to change.
     */

    public String getTargetEdition() {
        return compilerInfo.getTargetEdition();
    }

    /**
     * Declare the default collation
     *
     * @param uri the absolute URI of the default collation. Either this URI must have been bound to a collation
     *            using the method {@link Configuration#registerCollation(String, StringCollator)}, or it must be a
     *            collation that is recognized implicitly, such as a UCA collation
     * @throws IllegalStateException if the collation URI is not a recognized collation
     * @since 9.5
     */

    public void declareDefaultCollation(String uri) {
        StringCollator c;
        try {
            c = getProcessor().getUnderlyingConfiguration().getCollation(uri);
        } catch (XPathException e) {
            c = null;
        }
        if (c == null) {
            throw new IllegalStateException("Unknown collation " + uri);
        }
        compilerInfo.setDefaultCollation(uri);
    }

    /**
     * Get the default collation
     *
     * @return the URI of the default collation if one has been set, or the URI of the codepoint collation otherwise
     * @since 9.7.0.2
     */

    public String getDefaultCollation() {
        return compilerInfo.getDefaultCollation();
    }

    /**
     * Set the XSLT (and XPath) language level to be supported by the processor. This has no effect
     * from Saxon 9.8: the processor is an XSLT 3.0 processor regardless of the language level
     * requested.
     *
     * @param version the language level to be supported. The value is ignored.
     * @throws IllegalArgumentException if the value is not equal to 0.0, 2.0, or 3.0
     * @deprecated Has no effect from Saxon 9.8.
     * @since 9.3. Has no effect from Saxon 9.8.
     */

    public void setXsltLanguageVersion(String version) {
    }

    /**
     * Get the XSLT (and XPath) language level supported by the processor.
     *
     * @return the language level supported. From Saxon 9.8 this always returns "3.0".
     * @since 9.3
     */

    public String getXsltLanguageVersion() {
        return "3.0";
    }

    /**
     * Ask whether assertions (xsl:assert instructions) should be enabled. By default
     * they are disabled. If assertions are enabled at compile time, then by
     * default they will also be enabled at run time; but they can be
     * disabled at run time by specific request
     *
     * @return true if assertions are enabled at compile time
     * @since 9.7
     */

    public boolean isAssertionsEnabled() {
        return compilerInfo.isAssertionsEnabled();
    }

    /**
     * Say whether assertions (xsl:assert instructions) should be enabled. By default
     * they are disabled. If assertions are enabled at compile time, then by
     * default they will also be enabled at run time; but they can be
     * disabled at run time by specific request
     *
     * @param enabled true if assertions are enabled at compile time
     * @since 9.7
     */


    public void setAssertionsEnabled(boolean enabled) {
        compilerInfo.setAssertionsEnabled(enabled);
    }

    /**
     * Request fast compilation. Fast compilation will generally be achieved at the expense of run-time performance
     * and quality of diagnostics. Fast compilation is a good trade-off if (a) the stylesheet is known to be correct,
     * and (b) once compiled, it is only executed once against a document of modest size.
     * <p>Fast compilation may result in static errors going unreported, especially if they occur in code
     * that is never executed.</p>
     * <p><i>The current implementation is equivalent to switching off all optimizations other than just-in-time
     * compilation of template rules. Setting this option, however, indicates an intent rather than a mechanism,
     * and the implementation details may change in future to reflect the intent.</i></p>
     *
     * @param fast set to true to request fast compilation; set to false to revert to the optimization options
     *             defined in the Configuration.
     * @since 9.9
     */

    public void setFastCompilation(boolean fast) {
        if (fast) {
            // The only optimizer option that speeds up compilation is JIT.
            compilerInfo.setOptimizerOptions(new OptimizerOptions(OptimizerOptions.JIT));
        } else {
            compilerInfo.setOptimizerOptions(getProcessor().getUnderlyingConfiguration().getOptimizerOptions());
        }
    }

    /**
     * Ask if fast compilation has been enabled.
     *
     * @return true if fast compilation has been enabled (technically, this returns true if the only
     * optimizer option that has been enabled is JIT compilation of template rules).
     * @since 9.9
     */

    public boolean isFastCompilation() {
        return compilerInfo.getOptimizerOptions().getOptions() == OptimizerOptions.JIT;
    }


    /**
     * Set whether trace hooks are to be included in the compiled code. To use tracing, it is necessary
     * both to compile the code with trace hooks included, and to supply a TraceListener at run-time
     *
     * <p>Setting tracing to true suppresses those optimizations that cause major reorganisation of the code,
     * such as function inlining and loop-lifting. These optimizations can be reinstated, but this will tend
     * to make trace output harder to interpret.</p>
     *
     * @param option true if trace code is to be compiled in, false otherwise
     * @since 9.3
     */

    public void setCompileWithTracing(boolean option) {
        if (option) {
            compilerInfo.setCodeInjector(new XSLTTraceCodeInjector());
            compilerInfo.setOptimizerOptions(compilerInfo.getOptimizerOptions().except(
                    new OptimizerOptions(OptimizerOptions.COMMON_SUBEXPRESSIONS |
                            OptimizerOptions.CONSTANT_FOLDING |
                            OptimizerOptions.INLINE_FUNCTIONS |
                            OptimizerOptions.INLINE_VARIABLES |
                            OptimizerOptions.LOOP_LIFTING |
                            OptimizerOptions.EXTRACT_GLOBALS)
                                             )
            );
        } else {
            compilerInfo.setCodeInjector(null);
        }
    }

    /**
     * Ask whether trace hooks are included in the compiled code.
     *
     * @return true if trace hooks are included, false if not.
     * @since 9.3
     */

    public boolean isCompileWithTracing() {
        return compilerInfo.isCompileWithTracing();
    }

    /**
     * Set whether bytecode should be generated for the compiled stylesheet. This option
     * is available only with Saxon-EE. The default depends on the setting in the configuration
     * at the time the XsltCompiler is instantiated, and by default is true for Saxon-EE.
     *
     * @param option true if bytecode is to be generated, false otherwise
     * @since 9.6
     */

    public void setGenerateByteCode(boolean option) {
        compilerInfo.setGenerateByteCode(option);
    }

    /**
     * Ask whether bytecode is to be generated in the compiled code.
     *
     * @return true if bytecode is to be generated, false if not.
     * @since 9.6
     */

    public boolean isGenerateByteCode() {
        return compilerInfo.isGenerateByteCode();
    }


    /**
     * Import a compiled XQuery library. This makes pre-compiled XQuery library modules available
     * to the <code>saxon:import-query</code> declaration.
     * @param queryCompiler An XQueryCompiler that has been used to compile a library of XQuery functions
     * (by using one of the overloaded methods named <code>compileLibrary</code>).
     */

    public void importXQueryEnvironment(XQueryCompiler queryCompiler) {
        compilerInfo.setXQueryLibraries(queryCompiler.getUnderlyingStaticContext().getCompiledLibraries());
    }


    /**
     * Get the stylesheet associated
     * via the xml-stylesheet processing instruction (see
     * http://www.w3.org/TR/xml-stylesheet/) with the document
     * document specified in the source parameter, and that match
     * the given criteria.  If there are several suitable xml-stylesheet
     * processing instructions, then the returned Source will identify
     * a synthesized stylesheet module that imports all the referenced
     * stylesheet module.
     * <p>The returned Source will have an absolute URI, created by resolving
     * any relative URI against the base URI of the supplied source document,
     * and redirected if necessary by using the URIResolver associated with this
     * <code>XsltCompiler</code>.</p>
     *
     * @param source  The XML source document. Note that if the source document
     *                is available as an instance of {@link XdmNode}, a corresponding <code>Source</code>
     *                can be obtained using the method {@link net.sf.saxon.s9api.XdmNode#asSource()}.
     *                If the source is a StreamSource or SAXSource, it will be read only as far as the
     *                xml-stylesheet processing instruction (but the Source will be consumed and must not
     *                be re-used).
     * @param media   The media attribute to be matched.  May be null, in which
     *                case the prefered templates will be used (i.e. alternate = no).
     *                Note that Saxon does not implement the complex CSS3-based syntax for
     *                media queries. By default, the media value is simply ignored. An algorithm for
     *                comparing the requested media with the declared media can be defined using
     *                the method {@link Configuration#setMediaQueryEvaluator(Comparator)}.
     * @param title   The value of the title attribute to match.  May be null.
     * @param charset The value of the charset attribute to match.  May be null.
     * @return A Source object suitable for passing to {@link #compile(javax.xml.transform.Source)}.
     * @throws SaxonApiException if any problems occur, including the case where no matching
     *                           xml-stylesheet processing instruction is found.
     * @since 9.6
     */


    public Source getAssociatedStylesheet(Source source, String media, String title, String charset)
            throws SaxonApiException {
        try {
            return StylesheetModule.getAssociatedStylesheet(config, compilerInfo.getURIResolver(), source, media, title, charset);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }


    /**
     * Compile a library package.
     * <p>The source argument identifies an XML file containing an &lt;xsl:package&gt; element. Any packages
     * on which this package depends must have been made available to the <code>XsltCompiler</code>
     * by importing them using {@link #importPackage}.</p>
     *
     *
     * @param source identifies an XML document holding the the XSLT package to be compiled
     * @return the XsltPackage that results from the compilation. Note that this package
     * is not automatically imported to this <code>XsltCompiler</code>; if the package is required
     * for use in subsequent compilations then it must be explicitly imported.
     * @throws SaxonApiException if the source cannot be read or if static errors are found during the
     *                           compilation. Any such errors will have been notified to the registered <code>ErrorListener</code>
     *                           if there is one, or reported on the <code>System.err</code> output stream otherwise.
     * @since 9.6
     * @see #compile(Source) - especially the notes regarding error handling and just-in-time compilation.
     */

    public XsltPackage compilePackage(Source source) throws SaxonApiException {
        try {
            Compilation compilation;
            if (source instanceof DocumentImpl && ((DocumentImpl)source).getDocumentElement() instanceof StyleElement) {
                compilation = ((StyleElement)((DocumentImpl) source).getDocumentElement()).getCompilation();
            } else {
                compilation = new Compilation(config, new CompilerInfo(compilerInfo));
            }
            compilation.setLibraryPackage(true);
            XsltPackage pack = new XsltPackage(this, compilation.compilePackage(source).getStylesheetPackage());
            int errors = compilation.getErrorCount();
            if (errors > 0) {
                String count = errors == 1 ? "one error" : errors + " errors";
                throw new SaxonApiException("Package compilation failed: " + count + " reported");
            }
            return pack;
        } catch (UncheckedXPathException e) {
            throw new SaxonApiException(e.getXPathException());
        } catch (XPathException | XmlProcessingAbort e) {
            throw new SaxonApiException(e);
        }
    }

    private PackageLibrary getPackageLibrary() {
        return compilerInfo.getPackageLibrary();
    }


    /**
     * Load a compiled package from a file or from a remote location.
     * <p>The supplied URI represents the location of a resource which must have been originally
     * created using {@link XsltPackage#save(java.io.File)}.</p>
     * <p>The result of loading the package is returned as an <code>XsltPackage</code> object.
     * Note that this package is not automatically imported to this <code>XsltCompiler</code>;
     * if the package is required for use in subsequent compilations then it must be explicitly
     * imported.</p>
     *
     * @param location the location from which the package is to be loaded, as a URI
     * @return the compiled package loaded from the supplied file or remote location
     * @throws SaxonApiException if no resource can be loaded from the supplied location or if the
     *                           resource that is loaded is not a compiled package, or if the compiled package is not
     *                           consistent with this <code>XsltCompiler</code> (for example, if it was created using an
     *                           incompatible Saxon version).
     * @since 9.7
     */

    public XsltPackage loadLibraryPackage(URI location) throws SaxonApiException {
        return loadLibraryPackage(new StreamSource(location.toString()));
    }

    /**
     * Load a compiled package from a file or from a remote location.
     * <p>The supplied URI represents the location of a resource which must have been originally
     * created using {@link XsltPackage#save(java.io.File)}.</p>
     * <p>The result of loading the package is returned as an <code>XsltPackage</code> object.
     * Note that this package is not automatically imported to this <code>XsltCompiler</code>;
     * if the package is required for use in subsequent compilations then it must be explicitly
     * imported.</p>
     *
     * @param input the source from which the package is to be loaded, as a JAXP Source object
     * @return the compiled package loaded from the supplied file or remote location
     * @throws SaxonApiException if no resource can be loaded from the supplied location or if the
     *                           resource that is loaded is not a compiled package, or if the compiled package is not
     *                           consistent with this <code>XsltCompiler</code> (for example, if it was created using an
     *                           incompatible Saxon version).
     * @since 10.0
     */

    public XsltPackage loadLibraryPackage(Source input) throws SaxonApiException {
        try {
            IPackageLoader loader = processor.getUnderlyingConfiguration().makePackageLoader();
            if (loader != null) {
                StylesheetPackage pack = loader.loadPackage(input);
                return new XsltPackage(this, pack);
            }
            throw new SaxonApiException("Loading library package requires Saxon PE or higher");
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }


    /**
     * Load a compiled package from a file or from a remote location, with the intent to use this as a complete
     * executable stylesheet, not as a library package.
     * <p>The supplied URI represents the location of a resource which must have been originally
     * created using {@link XsltPackage#save(java.io.File)} or some equivalent.</p>
     * <p>The result of loading the package is returned as an <code>XsltExecutable</code> object.</p>
     *
     * @param location the location from which the package is to be loaded, as a URI
     * @return the compiled package loaded from the supplied file or remote location
     * @throws SaxonApiException if no resource can be loaded from the supplied location or if the
     *                           resource that is loaded is not a compiled package, or if the compiled package is not
     *                           consistent with this <code>XsltCompiler</code> (for example, if it was created using an
     *                           incompatible Saxon version).
     * @since 9.7
     */

    public XsltExecutable loadExecutablePackage(URI location) throws SaxonApiException {
        return loadLibraryPackage(location).link();
    }

    /**
     * Import a library package. Calling this method makes the supplied package available for reference
     * in the <code>xsl:use-package</code> declarations of subsequent compilations performed using this
     * <code>XsltCompiler</code>.
     *
     * @param thePackage the package to be imported
     * @throws SaxonApiException if the imported package was created under a different {@link Processor}
     * @since 9.6
     */

    public void importPackage(XsltPackage thePackage) throws SaxonApiException {
        if (thePackage.getProcessor() != processor) {
            throw new SaxonApiException(
                    "The imported package and the XsltCompiler must belong to the same Processor");
        }
        compilerInfo.getPackageLibrary().addPackage(thePackage.getUnderlyingPreparedPackage());
    }

    /**
     * Import a library package, changing the package name and/or version. Calling this method
     * makes the supplied package available for reference in the <code>xsl:use-package</code>
     * declaration of subsequent compilations performed using the <code>XsltCompiler</code>. The
     * supplied package name and version are used in place of the name and version used in the XSLT
     * source code. This provides a level of indirection: for example the same source package can
     * be compiled twice, with different settings for the static parameter values, and the two
     * different compiled versions can then be selected from xsl:use-package declarations. distinguishing
     * them by the new package name and/or version.
     *
     * @param thePackage the package to be imported
     * @param packageName the new package name to be used. If null, the original package name is used
     *  unchanged
     * @param version the new package version number to be used. If null, the original package version
     *  number is used unchanged
     * @throws SaxonApiException if the imported package was created under a different {@link Processor},
     * or if the supplied version number is invalid
     * @since 9.8
     */

    public void importPackage(XsltPackage thePackage, String packageName, String version) throws SaxonApiException {
        try {
            if (thePackage.getProcessor() != processor) {
                throw new SaxonApiException("The imported package and the XsltCompiler must belong to the same Processor");
            }
            PackageDetails details = new PackageDetails();
            if (packageName == null) {
                packageName = thePackage.getName();
            }
            if (version == null) {
                version = thePackage.getVersion();
            }
            details.nameAndVersion = new VersionedPackageName(packageName, version);
            details.loadedPackage = thePackage.getUnderlyingPreparedPackage();
            compilerInfo.getPackageLibrary().addPackage(details);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Import a named package, together with all the packages on which it depends, recursively.
     * The package must be identified in the package library for this {@code XsltCompiler}, which defaults
     * to the package library defined in the {@link Configuration}, typically set up by loading a configuration
     * file.
     * @param packageName the name of the required package. This is the name under which it is registered
     *                    in the package library, which is not necessarily the same as the name appearing
     *                    in the XSLT source code.
     * @param versionRange the required version of the package, or range of versions, in the format
     *                     of the package-version attribute of xsl:use-package.
     * @return the best matching package if there is one, or null otherwise. The name of the package
     * must match; if there are multiple versions, then the version chosen is based first on the
     * priority attached to this package/version in the library, and if the priorities are equal (or
     * there are no explicit priorities) then the one with highest version number is taken.
     * @since 9.8
     */

    public XsltPackage obtainPackage(String packageName, String versionRange) throws SaxonApiException {
        try {
            PackageVersionRanges pvr = new PackageVersionRanges(versionRange);
            PackageDetails details = getPackageLibrary().findPackage(packageName, pvr);
            if (details != null) {
                if (details.loadedPackage != null) {
                    return new XsltPackage(this, details.loadedPackage);
                } else if (details.sourceLocation != null) {
                    XsltPackage pack = compilePackage(details.sourceLocation);
                    details.loadedPackage = pack.getUnderlyingPreparedPackage();
                    return pack;
                }
            }
            return null;
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Import a package from the configuration file (or more generally, from the packageLibrary
     * of this XsltCompiler) given an alias used to identify it
     * @param alias the alias of the package/version in the configuration file
     * @return the referenced package
     * @throws SaxonApiException the package does not exist, or if compiling the package fails
     */

    public XsltPackage obtainPackageWithAlias(String alias) throws SaxonApiException {
        PackageDetails details = getPackageLibrary().findDetailsForAlias(alias);
        if (details == null) {
            throw new SaxonApiException("No package with alias " + alias + " found in package library");
        }
        try {
            StylesheetPackage pack = getPackageLibrary().obtainLoadedPackage(details, new ArrayList<>());
            return new XsltPackage(this, pack);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a stylesheet.
     * <p><i>Note: the term "compile" here indicates that the stylesheet is converted into an executable
     * form. There is no implication that this involves code generation.</i></p>
     * <p>The source argument identifies the XML document holding the principal stylesheet module. Other
     * modules will be located relative to this module by resolving relative URIs found in <code>&lt;xsl:include&gt;</code>
     * and <code>&lt;xsl:import&gt;</code> declarations against the base URI that is defined
     * as the {@code systemId} property of the supplied {@code Source}.</p>
     * <p>The following kinds of {@link javax.xml.transform.Source} are recognized:</p>
     * <ul>
     * <li>{@link javax.xml.transform.stream.StreamSource}, allowing the stylesheet to be supplied as a
     * URI, as a {@link java.io.File}, as an {@link java.io.InputStream}, or as a {@link java.io.Reader}</li>
     * <li>{@link javax.xml.transform.sax.SAXSource}, allowing the stylesheet to be supplied as a stream
     * of SAX events from a SAX2-compliant XML parser (or any other source of SAX events)</li>
     * <li>{@link javax.xml.transform.dom.DOMSource}, allowing the stylesheet to be supplied as a
     * DOM tree</li>
     * <li>Document wrappers for XOM, JDOM, DOM4J, or AXIOM trees</li>
     * <li>A Saxon {@link NodeInfo}, representing the root of a tree in any of the native tree formats supported
     * by Saxon</li>
     * </ul>
     * <p>The compilation uses a snapshot of the properties of the <code>XsltCompiler</code> at the
     * time this method is invoked. This is relevant when JIT compilation is enabled (which is the default
     * under Saxon-EE). Under JIT compilation, templates are compiled when first executed, which will typically be after
     * the <code>compile()</code> method has returned. Subsequent changes to the properties of the <code>XsltCompiler</code>,
     * for example, setting a new <code>ErrorListener</code>, have no effect on the delayed compilation of
     * template rules at execution time.</p>
     * <p>If JIT compilation is enabled (this is the default for Saxon-EE), then static errors in template
     * rules might be detected not during execution of the <code>compile()</code> method, but rather
     * when the relevant code is first executed. In this situation the <code>compile()</code> method will
     * not throw an exception, but the errors will still (eventually) be notified to the <code>ErrorListener</code>
     * or <code>ErrorList</code> associated with the compiler: more specifically, the <code>ErrorListener</code>
     * or <code>ErrorList</code> that was associated with the <code>XsltCompiler</code> at the time
     * <code>compile()</code> was invoked.</p>
     *
     * @param source Source object representing the principal stylesheet module to be compiled.
     *               Must not be null. If the {@code Source} wraps a resource such as a {@link java.io.Reader}
     *               or {@link InputStream} then the resource will be consumed by the method.
     * @return an {@code XsltExecutable}, which represents the compiled stylesheet. The {@code XsltExecutable}
     * is immutable and thread-safe; it may be used to run multiple transformations, in series or concurrently.
     * @throws SaxonApiException if the stylesheet contains static errors or if it cannot be read. Note that
     * the exception that is thrown will <b>not</b> contain details of the actual errors found in the stylesheet. These
     * will instead be notified to the registered {@code ErrorListener} or {@code ErrorList}.
     * The default {@code ErrorListener} displays error messages on the standard error output.
     *
     */

    public XsltExecutable compile(Source source) throws SaxonApiException {
        Objects.requireNonNull(source);
        try {
            CompilerInfo ci2 = new CompilerInfo(compilerInfo);
            PreparedStylesheet pss = Compilation.compileSingletonPackage(config, ci2, source);
            return new XsltExecutable(processor, pss);
        } catch (UncheckedXPathException e) {
            throw new SaxonApiException(e.getXPathException());
        } catch (XPathException | XmlProcessingAbort e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get the underlying {@link CompilerInfo} object, which provides more detailed (but less stable) control
     * over some compilation options
     *
     * @return the underlying {@code CompilerInfo} object, which holds compilation-time options. The methods on
     * the {@code CompilerInfo} object are not guaranteed stable from release to release.
     */

    public CompilerInfo getUnderlyingCompilerInfo() {
        return compilerInfo;
    }

    /**
     * Say whether just-in-time compilation of template rules should be used.
     * @param jit true if just-in-time compilation is to be enabled. With this option enabled,
     *            static analysis of a template rule is deferred until the first time that the
     *            template is matched. This can improve performance when many template
     *            rules are rarely used during the course of a particular transformation; however,
     *            it means that static errors in the stylesheet will not necessarily cause the
     *            {@link #compile(Source)} method to throw an exception (errors in code that is
     *            actually executed will still be notified to the registered <code>ErrorListener</code>
     *            or <code>ErrorList</code>, but this may happen after the {@link #compile(Source)}
     *            method returns). This option is enabled by default in Saxon-EE, and is not available
     *            in Saxon-HE or Saxon-PE.
     *            <p><b>Recommendation:</b> disable this option unless you are confident that the
     *            stylesheet you are compiling is error-free.</p>
     * @throws UnsupportedOperationException if the argument is set to <code>true</code> and the
     * configuration is not a licensed Saxon-EE configuration.
     */

    public void setJustInTimeCompilation(boolean jit){
        if (jit && !config.isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT)){
            throw new UnsupportedOperationException("XSLT just-in-time compilation requires a Saxon-EE license");
        }
        compilerInfo.setJustInTimeCompilation(jit);
    }

    /**
     * Ask whether just-in-time compilation of template rules should be used.
     *
     * @return true if just-in-time compilation is enabled. With this option enabled,
     *            static analysis of a template rule is deferred until the first time that the
     *            template is matched. This can improve performance when many template
     *            rules are rarely used during the course of a particular transformation; however,
     *            it means that static errors in the stylesheet may go undetected.
     */

    public boolean isJustInTimeCompilation(){
        return compilerInfo.isJustInTimeCompilation();
    }

    /**
     * Get the value of the default namespace for elements and types
     * @return the value that was set using the method {@link #getDefaultElementNamespace()},
     * if any; otherwise, the default value which is an empty string, representing "no namespace".
     */

    public String getDefaultElementNamespace() {
        return compilerInfo.getDefaultElementNamespace();
    }

    /**
     * Set the value of the default namespace for elements and types
     *
     * @param defaultNS the value to be used as the default namespace for elements and types.
     *                  This provides a default for the <code>[xsl:]xpath-default-namespace</code>
     *                  attribute in the stylesheet, and it has no effect in any part of the
     *                  stylesheet where an explicit value for <code>[xsl:]xpath-default-namespace</code>
     *                  is in force. The default value is a zero-length string, representing "no namespace".
     */

    public void setDefaultElementNamespace(String defaultNS) {
        compilerInfo.setDefaultElementNamespace(defaultNS);
    }

    /**
     * Get the policy for handling of unprefixed element names in path expressions and match patterns.
     * @return the policy previously set using {@link #setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy)},
     * or its default, which is {@link UnprefixedElementMatchingPolicy#DEFAULT_NAMESPACE}.
     */

    public UnprefixedElementMatchingPolicy getUnprefixedElementMatchingPolicy() {
        return compilerInfo.getUnprefixedElementMatchingPolicy();
    }

    /**
     * Set the policy for handling of unprefixed element names in path expressions and match patterns.
     * By default, such names are expanded using the default namespace for elements and types, which
     * can be set either using the <code>[xsl:]default-xpath-namespace</code> attribute in the stylesheet,
     * or programmatically using the method {@link #setDefaultElementNamespace(String)}. The default policy
     * is {@link UnprefixedElementMatchingPolicy#DEFAULT_NAMESPACE},
     * which causes such names to be expanded using the default namespace for elements and types,
     * which itself defaults to "no namespace", but which can be changed either using the
     * <code>[xsl:]xpath-default-namespace</code> attribute in the stylesheet, or using the
     * {@link #setDefaultElementNamespace(String)} method.
     *
     * <p>Note that any setting other than the default causes the stylesheet to behave in a way that
     * is not conformant with the W3C XSLT 3.0 specifications.</p>
     *
     * <p>The chosen policy affects:</p>
     * <ul>
     *     <li>Any NCName used as a node-test in an axis step (production <code>ForwardStep</code>
     *     or <code>ReverseStep</code>) in an
     *     XPath expression within the stylesheet, other than an axis step using the attribute or
     *     namespace axis</li>
     *     <li>Any NCName used as a node-test in an axis step (production <code>ForwardStepP</code>
     *     in a pattern within the stylesheet, other than an axis step using the attribute or namespace
     *     axis</li>
     * </ul>
     *
     * <p>It does not affect names appearing in other contexts (for example, names used in
     * {@code xsl:strip-space/@elements}), and it does not affect name tests expressed in a form
     * other than a simple NCName (for example tests of the form <code>Q{}local</code>, or
     * <code>*:local</code>, or <code>element(local)</code>).</p>
     *
     * <p>It does not affect XPath expressions evaluated dynamically using <code>xsl:evaluate</code>.</p>
     * @param unprefixedElementMatchingPolicy the policy for handling unprefixed elements.
     */

    public void setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy unprefixedElementMatchingPolicy) {
        compilerInfo.setUnprefixedElementMatchingPolicy(unprefixedElementMatchingPolicy);
    }


}

