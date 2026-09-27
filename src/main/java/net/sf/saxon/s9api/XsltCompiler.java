////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.expr.parser.OptimizerOptions;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.ResourceResolver;
import net.sf.saxon.lib.ResourceResolverWrappingURIResolver;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeSource;
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
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.linked.ElementImpl;
import net.sf.saxon.type.Schema;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.*;

/**
 * An {@code XsltCompiler} object allows XSLT stylesheets to be compiled. The compiler holds information that
 * represents the static context for the compilation.
 * <p>To construct an {@code XsltCompiler}, use the factory method {@link Processor#newXsltCompiler}
 * on the {@link Processor} object.</p>
 * <p>An {@code XsltCompiler} may be used repeatedly to compile multiple stylesheets. Any changes made to the
 * {@code XsltCompiler} (that is, to the static context) do not affect stylesheets that have already been compiled.
 * An XsltCompiler may in principle be used concurrently in multiple threads, but in practice this
 * is best avoided because all instances will share the same {@link ErrorReporter} and it may therefore be
 * difficult to establish which error messages are associated with each compilation.</p>
 * <p>If JIT compilation is enabled (this is the default for Saxon-EE), then static errors in template
 *  rules might be detected not during execution of the <code>compile()</code> method, but rather
 *  when the relevant code is first executed. In this situation the <code>compile()</code> method will
 *  not throw an exception, but the errors will still (eventually) be notified to the {@link ErrorReporter}
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


@CSharpModifiers(code = {"internal"})
public class XsltCompiler {

    private final Processor processor;
    private final Configuration config;
    private final CompilerInfo compilerInfo;
    private Map<String, XsdSchema> usedXsdSchemata;
    private Map<String, Schema> usedSchemata;


    /**
     * Protected constructor. The public way to create an <code>XsltCompiler</code> is by using the factory method
     * {@link Processor#newXsltCompiler} .
     *
     * @param processor the Saxon {@link Processor}
     */

    protected XsltCompiler(Processor processor) {
        this.processor = processor;
        this.config = processor.getUnderlyingConfiguration();
        compilerInfo = new CompilerInfo(config.getDefaultXsltCompilerInfo());
        compilerInfo.setTargetEdition(config.getEditionCode());
        compilerInfo.setJustInTimeCompilation(config.isJITEnabled());
        XsdSchema legacySchema = processor.getLegacySchema();
        if (legacySchema != null) {
            useSchema(legacySchema);
        }
    }

    /**
     * Set the {@link XsdSchema} to be used with this {@code XsltCompiler} for a schema-aware
     * transformation (requires Saxon-EE).
     *
     * <p>If this method is called, then any <code>xsl:import-schema</code> declaration
     * for a target namespace that is present in the supplied stylesheet (other than an
     * <code>xsl:import-schema</code> with an inline <code>xs:schema</code>) is satisfied
     * by schema components in this schema; the <code>schema-location</code> attribute
     * on the declaration is ignored.</p>
     *
     * <p>However, this does not make <code>xsl:import-schema</code> declarations redundant;
     * they are still needed in order to permit reference to schema components by name (that is,
     * to make the names of element and attribute declarations and schema types visible in the
     * static context).</p>
     *
     * <p>When validation is invoked within the XSLT code (using a validation or type attribute),
     * all the schema components supplied in this schema are available for use during the validation
     * episode, whether or not they have been imported into the static context by means of an
     * <code>xsl:import-schema</code> declaration.</p>
     *
     * <p>The same also applies when a stylesheet is loaded from a SEF file. Any schema contained
     * within the SEF file, with a matching role, is ignored in favour of the schema supplied
     * via this API.</p>
     *
     * @param role   the schema role. Set this to "" (an empty string) or to null for the default
     *               (unnamed) schema role. If a role name is specified then the supplied schema
     *               is ignored unless the stylesheet actually contains an <code>xsl:import-schema</code>
     *               declaration for this schema role.
     * @param schema the schema to be imported
     * @since 13.0
     */

    public void useSchema(String role, XsdSchema schema) {
        compilerInfo.setSchemaAware(true);
        compilerInfo.preLoadSchema(role, schema.getUnderlyingSchema());
        if (usedXsdSchemata == null) {
            usedXsdSchemata = new HashMap<>();
            usedSchemata = new HashMap<>();
        }
        usedXsdSchemata.put(role, schema);
        usedSchemata.put(role,  schema.getUnderlyingSchema());
    }

    /**
     * Set the {@link XsdSchema} to be used with this {@code XsltCompiler} for a schema-aware
     * transformation (requires Saxon-EE). Equivalent to <code>useSchema("", schema)</code>
     * @param schema the schema to be imported
     * @since 13.0
     */

    public void useSchema(XsdSchema schema) {
        useSchema("", schema);
    }

    /**
     * Clear all used schemas (for subsequent stylesheet compilations)
     */

    public void clearRegisteredSchemas() {
        usedSchemata = null;
        usedXsdSchemata = null;
    }


    /**
     * Get the schema that is to be used for a schema-aware transformation.
     * @param role the schema role. Set this to "" (an empty string) or to null for the default
     *      (unnamed) schema role.
     * @return the schema previous supplied in a call of {@link #useSchema(String, XsdSchema)}, or null if
     * that method has not been called.
     */

    public XsdSchema getUsedSchema(String role) {
        if (role == null) {
            role = "";
        }
        if (usedXsdSchemata == null) {
            return null;
        }
        return usedXsdSchemata.get(role);
    }

    /**
     * Get the {@link Processor} from which this {@code XsltCompiler} was constructed
     *
     * @return the {@link Processor} to which this {@code XsltCompiler} belongs
     * @since 9.3
     */

    public Processor getProcessor() {
        return processor;
    }

    /**
     * Set the {@link URIResolver} to be used during stylesheet compilation. This interface is
     * retained for backwards compatibility reasons. The supplied <code>URIResolver</code>
     * is wrapped in a {@link ResourceResolver}. The <code>ResourceResolver</code>
     * interface is preferred because more information is made available; most obviously,
     * the absolute URI formed by combining the base URI and relative URI.
     *
     * @param resolver the URIResolver to be used during stylesheet compilation.
     * @deprecated since 11.1. Use {@link #setResourceResolver}
     */

    @Deprecated
    public void setURIResolver(URIResolver resolver) {
        compilerInfo.setResourceResolver(
                new ResourceResolverWrappingURIResolver(resolver));
    }

    /**
     * Set the {@link ResourceResolver} to be used during stylesheet compilation.
     * The <code>ResourceResolver</code> is used for dereferencing
     * an absolute URI (after URI resolution) to return a {@link javax.xml.transform.Source} representing the
     * location where a stylesheet module can be found.
     * <p>This <code>ResourceResolver</code> is used to dereference the URIs appearing in <code>xsl:import</code>,
     * <code>xsl:include</code>, and <code>xsl:import-schema</code> declarations. It is not used
     * for resolving the URI supplied for the main stylesheet module (as supplied to the
     * {@link #compile(javax.xml.transform.Source)} or {@link #compilePackage(javax.xml.transform.Source)} methods.
     * It is not used at run-time for resolving requests to the <code>document()</code> or similar functions.
     * (Instead, such functions use the run-time <code>ResourceResolver</code> passed to the {@link XsltTransformer}
     * or {@link Xslt30Transformer}).</p>
     *
     * @param resolver the <code>ResourceResolver</code> to be used during stylesheet compilation.
     */

    public void setResourceResolver(ResourceResolver resolver) {
        compilerInfo.setResourceResolver(resolver);
    }

    /**
     * Set the value of a stylesheet parameter. Static (compile-time) parameters must be provided using
     * this method on the <code>XsltCompiler</code> object, prior to stylesheet compilation. Non-static parameters
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
     * This resets the parameters to their initial (undeclared) state
     */

    public void clearParameters() {
        compilerInfo.clearParameters();
    }

    /**
     * Get the {@link URIResolver} to be used during stylesheet compilation.
     *
     * @return the {@code URIResolver} used during stylesheet compilation. Returns null if no user-supplied
     * {@code URIResolver} has been set.
     * @deprecated since 11.1 - use a {@link ResourceResolver} instead
     */

    @Deprecated
    public URIResolver getURIResolver() {
        ResourceResolver rr = compilerInfo.getResourceResolver();
        if (rr instanceof ResourceResolverWrappingURIResolver) {
            return ((ResourceResolverWrappingURIResolver)rr).getWrappedURIResolver();
        } else {
            return null;
        }
    }

    /**
     * Get the {@link ResourceResolver} to be used during stylesheet compilation.
     *
     * @return the {@code ResourceResolver} used during stylesheet compilation. Returns null if no user-supplied
     * {@code ResourceResolver} has been set.
     */

    public ResourceResolver getResourceResolver() {
        return compilerInfo.getResourceResolver();
    }

    /**
     * Set the  to be used during this compilation
     *
     * @param listener The error listener to be used. This is notified of all errors detected during the
     *                 compilation.
     *
     *                 <p><i>In earlier releases, contrary to the documentation, the supplied
     *                 {@code ErrorListener} was also notified of run-time errors, unless a different
     *                 {@code ErrorListener} was supplied at run-time. This is no longer the case from
     *                 Saxon 10.0</i></p>
     * @deprecated since 10.0. Use {@link ErrorListener}{@link #setErrorReporter(ErrorReporter)}
     */

    @Deprecated
    public void setErrorListener(ErrorListener listener) {
        compilerInfo.setErrorListener(listener);
    }

    /**
     * Get the {@link ErrorListener} being used during this compilation episode
     *
     * @return listener The error listener in use. This is notified of all errors detected during the
     * compilation. Returns null if no user-supplied ErrorListener has been set.
     * @deprecated since 10.0. Use {@link #getErrorReporter()}
     */
    @Deprecated
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

    public void setErrorList(List<? super XmlProcessingError> errorList) {
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
     * @param reporter a class which will be notified of all static errors and warnings
     *                 encountered during a compilation episode.
     * @since 10.0
     */

    public void setErrorReporter(ErrorReporter reporter) {
        compilerInfo.setErrorReporter(reporter);
    }

    /**
     * Get the recipient of error information previously registered using {@link #setErrorReporter(ErrorReporter)}.
     *
     * @return the error reporter previously registered explicitly using {@link #setErrorReporter(ErrorReporter)},
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
     * {@code xsl:import-schema} declarations. By default a stylesheet is treated as schema-aware
     * only if it contains one or more {@code xsl:import-schema} declarations. If it is not schema-aware,
     * then all input documents must be untyped, and validation of temporary trees is disallowed
     * (though validation of the final result tree is permitted). Setting the argument to true
     * means that schema-aware code will be compiled regardless.
     *
     * <p>Setting the value to true has no effect (it is not an error) if the configuration does
     * not support schema processing. (Schema processing requires a Saxon-EE license.)</p>
     * 
     * @param schemaAware If true, the stylesheet will be compiled with schema-awareness
     *                    enabled even if it contains no {@code xsl:import-schema} declarations. If false, the stylesheet
     *                    is treated as schema-aware only if it contains one or more {@code xsl:import-schema} declarations.
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
     * @return if true then {@code static-base-uri()} represents the deployed location of the package,
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
     * @param edition the Saxon edition for the run-time environment. One of "EE", "PE", "HE",
     *                or "JS", "JS2", or "JS3".
     * @since 9.7.0.5. Experimental and subject to change.
     */

    public void setTargetEdition(String edition) {
        switch (edition) {
            case "EE":
            case "PE":
            case "HE":
            case "JS":
            case "JS2":
            case "JS3":
                compilerInfo.setTargetEdition(edition);
                return;
            default:
                throw new IllegalArgumentException("Unknown Saxon edition " + edition);
        }
    }

    /**
     * Get the target edition under which the stylesheet will be executed.
     *
     * @return the Saxon edition for the run-time environment. One of "EE", "PE", "HE",
     * or "JS", "JS2", "JS3"
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
     * Set the XSLT (and XPath) language level to be supported by the processor. Set the value to "4.0"
     * to enable support for experimental features defined in the XSLT 4.0 proposal (which is likely to change
     * before it stabilizes).
     *
     * @param version the language level to be supported. The values "3.0" and "4.0" are recognized
     * @throws IllegalArgumentException if the value is not equal to 3.0 or 4.0
     * @since 9.3. From 11.0, accepts the values "3.0" and "4.0". From 12.2, also accepts "3" or "4".
     */

    public void setXsltLanguageVersion(String version) {
        switch (version) {
            case "3":
            case "3.0":
                compilerInfo.setXsltVersion(30);
                break;
            case "4":
            case "4.0":
                compilerInfo.setXsltVersion(40);
                break;
            default:
                throw new IllegalArgumentException("Language version must be 3.0|4.0");
        }
    }

    /**
     * Get the XSLT (and XPath) language level supported by the processor.
     *
     * @return the language level supported. In Saxon 11 this returns either "3.0" or "4.0".
     * @since 9.3
     */

    public String getXsltLanguageVersion() {
        return compilerInfo.getXsltVersion() == 40 ? "4.0" : "3.0";
    }

    /**
     * Ask whether assertions ({@code xsl:assert} instructions) should be enabled. By default
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
     * Say whether assertions ({@code xsl:assert} instructions) should be enabled. By default
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
     * has no effect from 12.0 (bytecode generation has been dropped from the product)
     *
     * @param option true if bytecode is to be generated, false otherwise
     * @since 9.6.
     * @deprecated since 12.0 - the call has no effect.
     */

    @Deprecated
    public void setGenerateByteCode(boolean option) {
        // no action
    }

    /**
     * Ask whether bytecode is to be generated in the compiled code. From 12.0 this
     * always returns false.
     *
     * @return true if bytecode is to be generated, false if not.
     * @since 9.6
     * @deprecated since 12.0 - always returns false.
     */

    @Deprecated
    public boolean isGenerateByteCode() {
        return false;
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
     * Get the stylesheet associated via the {@code xml-stylesheet} processing instruction (see
     * <a href="http://www.w3.org/TR/xml-stylesheet/">W3C specification</a>) with the document
     * specified in the source parameter, and that matches
     * the given criteria.  If there are several suitable {@code xml-stylesheet}
     * processing instructions, then the returned {@link Source} will identify
     * a synthesized stylesheet module that imports all the referenced
     * stylesheet module.
     * <p>The returned {@link Source} will have an absolute URI, created by resolving
     * any relative URI against the base URI of the supplied source document,
     * and redirected if necessary by using the URIResolver associated with this
     * <code>XsltCompiler</code>.</p>
     *
     * @param source  The XML source document. Note that if the source document
     *                is available as an instance of {@link XdmNode}, a corresponding <code>Source</code>
     *                can be obtained using the method {@link XdmNode#asSource()}.
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
     * @return A Source object suitable for passing to {@link #compile(Source)}.
     * @throws SaxonApiException if any problems occur, including the case where no matching
     *                           xml-stylesheet processing instruction is found.
     * @since 9.6
     */

    @CSharpReplaceBody(code="throw new NotImplementedException();")
    public Source getAssociatedStylesheet(Source source, String media, String title, String charset)
            throws SaxonApiException {
        try {
            return StylesheetModule.getAssociatedStylesheet(config, compilerInfo.getResourceResolver(), source, media, title, charset);
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
     * @param source identifies an XML document holding the XSLT package to be compiled
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
            Compilation compilation = null;
            if (source instanceof NodeSource
                    && ((NodeSource)source).getNode() instanceof DocumentImpl) {
                ElementImpl elem = ((DocumentImpl) ((NodeSource) source).getNode()).getDocumentElement();
                if (elem instanceof StyleElement) {
                    compilation = ((StyleElement)elem).getCompilation();
                }
            }
            if (compilation == null) {
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

    /**
     * Compile a library package.
     * <p>This is a convenience method that simple calls <code>compilePackage(new StreamSource(file))</code>.</p>
     *
     * @param file identifies an XML document holding the XSLT package to be compiled
     * @return the XsltPackage that results from the compilation. Note that this package
     * is not automatically imported to this <code>XsltCompiler</code>; if the package is required
     * for use in subsequent compilations then it must be explicitly imported.
     * @throws SaxonApiException if the source cannot be read or if static errors are found during the
     *                           compilation. Any such errors will have been notified to the registered <code>ErrorListener</code>
     *                           if there is one, or reported on the <code>System.err</code> output stream otherwise.
     * @see #compile(Source) - especially the notes regarding error handling and just-in-time compilation.
     * @since 12.0
     */

    public XsltPackage compilePackage(File file) throws SaxonApiException {
        return compilePackage(new StreamSource(file));
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
                StylesheetPackage pack = loader.loadPackage(input, usedSchemata);
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
     * Load a compiled package from a supplied source, with the intent to use this as a complete
     * executable stylesheet, not as a library package.
     * <p>The supplied source represents the location of a resource which must have been originally
     * created using {@link XsltPackage#save(java.io.File)} or some equivalent.</p>
     * <p>The result of loading the package is returned as an <code>XsltExecutable</code> object.</p>
     *
     * @param source the source from which the package is to be loaded
     * @return the compiled package loaded from the supplied file or remote location
     * @throws SaxonApiException if no resource can be loaded from the supplied location or if the
     *                           resource that is loaded is not a compiled package, or if the compiled package is not
     *                           consistent with this <code>XsltCompiler</code> (for example, if it was created using an
     *                           incompatible Saxon version).
     * @since 11
     */

    public XsltExecutable loadExecutablePackage(Source source) throws SaxonApiException {
        return loadLibraryPackage(source).link();
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
     * @throws SaxonApiException if an error is detected
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
            List<VersionedPackageName> packageNames = new ArrayList<>();
            StylesheetPackage pack = getPackageLibrary().obtainLoadedPackage(details, packageNames, usedSchemata);
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
     * <p>If the source is a {@code StreamSource} or a {@code SAXSource} with no user-supplied {@code XmlReader},
     * then Saxon will allocate and configure an {@code XmlReader}. This will NOT take account of any configuration
     * level parser settings (such as DTD validation, XInclude processing, and so on). The recommended way to customise
     * parsing options is by creating an {@code XmlReader} and supplying this as a property of a {@code SAXSource}. For
     * options when parsing included or imported stylesheet modules, it is possible to do the same in the
     * {@link ResourceResolver} set using the {@link #setResourceResolver(ResourceResolver)} method.</p>
     * </p>
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
            return new XsltExecutable(processor, pss, compilerInfo.getXsltVersion());
        } catch (UncheckedXPathException e) {
            throw new SaxonApiException(e.getXPathException());
        } catch (XPathException | XmlProcessingAbort e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile a stylesheet held in a file.
     * <p><i>Note: the term "compile" here indicates that the stylesheet is converted into an executable
     * form. There is no implication that this involves code generation.</i></p>
     * <p>This is a convenience method that simply calls <code>compile(new StreamSource(file))</code>.</p>
     *
     * @param file The file containing the principal stylesheet module to be compiled.
     *               Must not be null.
     * @return an {@code XsltExecutable}, which represents the compiled stylesheet. The {@code XsltExecutable}
     * is immutable and thread-safe; it may be used to run multiple transformations, in series or concurrently.
     * @throws SaxonApiException if the stylesheet contains static errors or if it cannot be read. Note that
     *                           the exception that is thrown will <b>not</b> contain details of the actual errors found in the stylesheet. These
     *                           will instead be notified to the registered {@code ErrorListener} or {@code ErrorList}.
     *                           The default {@code ErrorListener} displays error messages on the standard error output.
     * @since 12.0
     */

    public XsltExecutable compile(File file) throws SaxonApiException {
        return compile(new StreamSource(file));
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

    public void setJustInTimeCompilation(boolean jit) {
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

    public boolean isJustInTimeCompilation() {
        return compilerInfo.isJustInTimeCompilation();
    }

    /**
     * Get the value of the default namespace for elements and types
     * @return the value that was set using the method {@link #setDefaultElementNamespace(String)},
     * if any; otherwise, the default value which is an empty string, representing "no namespace".
     */

    public String getDefaultElementNamespace() {
        return compilerInfo.getDefaultElementNamespace().toString();
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
        compilerInfo.setDefaultElementNamespace(NamespaceUri.of(defaultNS));
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
     *     or <code>ReverseStep</code>) in an XPath expression within the stylesheet, other than an
     *     axis step using the attribute or namespace axis</li>
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

    /**
     * Say whether any stylesheet being compiled by this compiler should be compiled with export in mind;
     * that is, whether a SEF file is to be created from the compiled package. It can be useful to set
     * this flag to inhibit compile-time optimisations that are unhelpful when creating a SEF file. Notably,
     * pre-evaluating functions like fn:invisible-xml may create function items that cannot be exported
     * to a SEF file, and this flag inhibits such pre-evaluation.
     * @param exportable true if stylesheets are to be compiled with export (to a SEF file) in mind.
     */

    public void setCompileForExport(boolean exportable) {
        compilerInfo.setCompileForExport(exportable);
    }

    /**
     * Ask whether any stylesheet being compiled by this compiler will be compiled with export in mind;
     * that is, whether {@link #setCompileForExport(boolean)} has been called.
     * @return true if {@link #setCompileForExport(boolean)} has been called
     */

    public boolean isCompileForExport() {
        return compilerInfo.isCompileForExport();
    }


}

