////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.instruct.GlobalParameterSet;
import net.sf.saxon.expr.parser.CodeInjector;
import net.sf.saxon.expr.parser.OptimizerOptions;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.QueryLibrary;
import net.sf.saxon.s9api.UnprefixedElementMatchingPolicy;
import net.sf.saxon.trans.packages.PackageLibrary;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.URIResolver;
import java.util.Collection;

/**
 * This class exists to hold information associated with a specific XSLT compilation episode.
 * In JAXP, the URIResolver and ErrorListener used during XSLT compilation are those defined in the
 * TransformerFactory. The .NET API and the s9api API, however, allow finer granularity,
 * and this class exists to support that.
 */

public class CompilerInfo {

    private Configuration config;
    private URIResolver uriResolver;
    private OutputURIResolver outputURIResolver = StandardOutputResolver.getInstance();
    private ErrorReporter errorReporter = new StandardErrorReporter();
    private CodeInjector codeInjector;
    private int recoveryPolicy = Mode.RECOVER_WITH_WARNINGS;
    private boolean schemaAware;
    private String messageReceiverClassName = "net.sf.saxon.serialize.MessageEmitter";
    private StructuredQName defaultInitialMode;
    private StructuredQName defaultInitialTemplate;
    private GlobalParameterSet suppliedParameters = new GlobalParameterSet();
    private String defaultCollation;
    private PackageLibrary packageLibrary;
    private boolean assertionsEnabled = false;
    private String targetEdition = "HE";
    private boolean relocatable = false;
    private Collection<QueryLibrary> queryLibraries;
    private OptimizerOptions optimizerOptions;
    private String defaultNamespaceForElementsAndTypes = "";
    private UnprefixedElementMatchingPolicy unprefixedElementMatchingPolicy
            = UnprefixedElementMatchingPolicy.DEFAULT_NAMESPACE;

    /**
     * Create an empty CompilerInfo object with default settings. (Note, this does not
     * copy settings from the defaultXsltCompilerInfo held by the configuration. For that,
     * use <code>new CompilerInfo(config.getDefaultXsltCompilerInfo()</code>).
     */

    public CompilerInfo(Configuration config) {
        this.config = config;
        packageLibrary = new PackageLibrary(this);
        optimizerOptions = config.getOptimizerOptions();
    }

    /**
     * Create a CompilerInfo object as a copy of another CompilerInfo object
     *
     * @param info the existing CompilerInfo object
     * @since 9.2
     */

    public CompilerInfo(CompilerInfo info) {
        copyFrom(info);
    }

    /**
     * Copy all properties from a supplied CompilerInfo
     *
     * @param info the CompilerInfo to be copied
     */

    public void copyFrom(CompilerInfo info) {
        config = info.config;
        uriResolver = info.uriResolver;
        outputURIResolver = info.outputURIResolver;
        errorReporter = info.errorReporter;
        codeInjector = info.codeInjector;
        recoveryPolicy = info.recoveryPolicy;
        schemaAware = info.schemaAware;
        messageReceiverClassName = info.messageReceiverClassName;
        defaultInitialMode = info.defaultInitialMode;
        defaultInitialTemplate = info.defaultInitialTemplate;
        suppliedParameters = new GlobalParameterSet(info.suppliedParameters);
        defaultCollation = info.defaultCollation;
        assertionsEnabled = info.assertionsEnabled;
        targetEdition = info.targetEdition;
        packageLibrary = new PackageLibrary(info.packageLibrary);
        relocatable = info.relocatable;
        optimizerOptions = info.optimizerOptions;
        queryLibraries = info.queryLibraries;
        defaultNamespaceForElementsAndTypes = info.defaultNamespaceForElementsAndTypes;
        unprefixedElementMatchingPolicy = info.unprefixedElementMatchingPolicy;
    }

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Say whether just-in-time compilation of template rules should be used for this XSLT compilation
     * <p>The same effect can be achieved by {@link #setOptimizerOptions(OptimizerOptions)} with the appropriate
     * setting of {@link OptimizerOptions#JIT}.</p>
     *
     * @param jit true if just-in-time compilation should be used. With this option, static errors in the
     *            stylesheet code may be reported only when a template rule is first executed
     * @since 9.8
     */

    public void setJustInTimeCompilation(boolean jit) {
        if (jit) {
            optimizerOptions = optimizerOptions.union(new OptimizerOptions(OptimizerOptions.JIT));
        } else {
            optimizerOptions = optimizerOptions.except(new OptimizerOptions(OptimizerOptions.JIT));
        }
    }

    /**
     * Ask whether just-in-time compilation of template rules is in use for this XSLT compilation
     *
     * @return true if just-in-time compilation should be used. With this option, static errors in the
     * stylesheet code may be reported only when a template rule is first executed
     * @since 9.8
     */

    public boolean isJustInTimeCompilation() {
        return optimizerOptions.isSet(OptimizerOptions.JIT);
    }

    /**
     * Set the URI Resolver to be used in this compilation episode.
     *
     * @param resolver The URIResolver to be used. This is used to dereference URIs encountered in constructs
     *                 such as xsl:include, xsl:import, and xsl:import-schema.
     * @since 8.7
     */

    public void setURIResolver(URIResolver resolver) {
        uriResolver = resolver;
    }

    /**
     * Set the value of a stylesheet parameter. Static (compile-time) parameters must be provided using
     * this method on the XsltCompiler object, prior to stylesheet compilation. Non-static parameters
     * may also be provided using this method if their values will not vary from one transformation
     * to another. No error occurs at this stage if the parameter is unknown, or if the value is incorrect
     * for the parameter. Setting a value for a parameter overwrites any previous value set for the
     * same parameter.
     *
     * @param name the name of the stylesheet parameter
     * @param seq  the value of the stylesheet parameter
     */

    public void setParameter(StructuredQName name, GroundedValue seq) {
        suppliedParameters.put(name, seq);
    }

    /**
     * Get the values of all stylesheet parameters that have been set using the
     * {@link #setParameter(net.sf.saxon.om.StructuredQName, net.sf.saxon.om.GroundedValue)}
     * method.
     *
     * @return the set of all parameters that have been set.
     */

    public GlobalParameterSet getParameters() {
        return suppliedParameters;
    }

    /**
     * Clear the values of all stylesheet parameters that have been set using the
     * {@link #setParameter(net.sf.saxon.om.StructuredQName, net.sf.saxon.om.GroundedValue)}
     * method.
     */

    public void clearParameters() {
        suppliedParameters.clear();
    }

    /**
     * Set the target edition under which the stylesheet will be executed.
     *
     * @param edition the Saxon edition for the run-time environment. One of "EE", "PE", "HE", or "JS".
     * @since 9.7.0.5. Experimental and subject to change.
     */

    public void setTargetEdition(String edition) {
        this.targetEdition = edition;
    }

    /**
     * Get the target edition under which the stylesheet will be executed.
     *
     * @return the Saxon edition for the run-time environment. One of "EE", "PE", "HE", or "JS".
     * @since 9.7.0.5. Experimental and subject to change.
     */

    public String getTargetEdition() {
        return targetEdition;
    }

    /**
     * Ask whether any package produced by this compiler can be deployed to a different location, with a different base URI
     *
     * @return if true then static-base-uri() represents the deployed location of the package,
     * rather than its compile time location
     */

    public boolean isRelocatable() {
        return relocatable;
    }

    /**
     * Say whether any package produced by this compiler can be deployed to a different location, with a different base URI
     *
     * @param relocatable if true then static-base-uri() represents the deployed location of the package,
     *                    rather than its compile time location
     */

    public void setRelocatable(boolean relocatable) {
        this.relocatable = relocatable;
    }

    /**
     * Set the package library to be used during the compilation episode. Any packages referenced
     * using xsl:use-package declarations during the stylesheet compilation must be found in this
     * package library
     *
     * @param library the package library
     */

    public void setPackageLibrary(PackageLibrary library) {
        packageLibrary = library;
    }

    /**
     * Get the package library to be used during the compilation episode. Any packages referenced
     * using xsl:use-package declarations during the stylesheet compilation must be found in this
     * package library
     *
     * @return the package library
     */

    public PackageLibrary getPackageLibrary() {
        return packageLibrary;
    }

    /**
     * Ask whether assertions (xsl:assert instructions) should be enabled. By default
     * they are disabled. If assertions are enabled at compile time, then by
     * default they will also be enabled at run time; but they can be
     * disabled at run time by specific request
     *
     * @return true if assertions are enabled at compile time
     */

    public boolean isAssertionsEnabled() {
        return assertionsEnabled;
    }

    /**
     * Say whether assertions (xsl:assert instructions) should be enabled. By default
     * they are disabled. If assertions are enabled at compile time, then by
     * default they will also be enabled at run time; but they can be
     * disabled at run time by specific request
     *
     * @param enabled true if assertions are enabled at compile time
     */


    public void setAssertionsEnabled(boolean enabled) {
        this.assertionsEnabled = enabled;
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
     * Set whether bytecode should be generated for the compiled stylesheet. This option
     * is available only with Saxon-EE. The default depends on the setting in the configuration
     * at the time the XsltCompiler is instantiated, and by default is true for Saxon-EE.
     * <p>The same effect can be achieved by {@link #setOptimizerOptions(OptimizerOptions)} with the appropriate
     * setting of {@link OptimizerOptions#BYTE_CODE}.</p>
     *
     * @param option true if bytecode is to be generated, false otherwise
     * @since 9.6
     */

    public void setGenerateByteCode(boolean option) {
        if (option) {
            optimizerOptions = optimizerOptions.union(new OptimizerOptions(OptimizerOptions.BYTE_CODE));
        } else {
            optimizerOptions = optimizerOptions.except(new OptimizerOptions(OptimizerOptions.BYTE_CODE));
        }
    }


    /**
     * Ask whether bytecode is to be generated in the compiled code.
     *
     * @return true if bytecode is to be generated, false if not.
     * @since 9.6
     */

    public boolean isGenerateByteCode() {
        return optimizerOptions.isSet(OptimizerOptions.BYTE_CODE);
    }

    /**
     * Get the URI Resolver being used in this compilation episode.
     *
     * @return resolver The URIResolver in use. This is used to dereference URIs encountered in constructs
     * such as xsl:include, xsl:import, and xsl:import-schema.
     * @since 8.7
     */

    public URIResolver getURIResolver() {
        return uriResolver;
    }

    /**
     * Get the OutputURIResolver that will be used to resolve URIs used in the
     * href attribute of the xsl:result-document instruction.
     *
     * @return the OutputURIResolver. If none has been supplied explicitly, the
     * default OutputURIResolver is returned.
     * @since 9.2
     */

    public OutputURIResolver getOutputURIResolver() {
        return outputURIResolver;
    }

    /**
     * Set the OutputURIResolver that will be used to resolve URIs used in the
     * href attribute of the xsl:result-document instruction.
     *
     * @param outputURIResolver the OutputURIResolver to be used.
     * @since 9.2
     */

    public void setOutputURIResolver(OutputURIResolver outputURIResolver) {
        this.outputURIResolver = outputURIResolver;
    }


    /**
     * Set the ErrorListener to be used during this compilation episode
     *
     * @param listener The error listener to be used. This is notified of all errors detected during the
     *                 compilation.
     * @since 8.7
     * @deprecated since 10.0 - use {@link #setErrorReporter(ErrorReporter)}
     */

    public void setErrorListener(ErrorListener listener) {
        setErrorReporter(new ErrorReporterToListener(listener));
    }

    /**
     * Get the ErrorListener being used during this compilation episode
     *
     * @return listener The error listener in use. This is notified of all errors detected during the
     * compilation.
     * @since 8.7
     * @deprecated since 10.0 - use {@link #getErrorReporter()}
     */

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
     * @param reporter the object to be notified of static errors
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
     */

    public ErrorReporter getErrorReporter() {
        return this.errorReporter;
    }


    /**
     * Get the name of the class that will be instantiated to create a MessageEmitter,
     * to process the output of xsl:message instructions in XSLT.
     *
     * @return the full class name of the message emitter class.
     * @since 9.2
     */

    public String getMessageReceiverClassName() {
        return messageReceiverClassName;
    }

    /**
     * Set the name of the class that will be instantiated to create a MessageEmitter,
     * to process the output of xsl:message instructions in XSLT.
     *
     * @param messageReceiverClassName the message emitter class. This
     *                                 must implement net.sf.saxon.event.Emitter.
     * @since 9.2
     */

    public void setMessageReceiverClassName(String messageReceiverClassName) {
        this.messageReceiverClassName = messageReceiverClassName;
    }

    /**
     * Set the name of the default collation
     *
     * @param collation the name of the default collation (if the stylesheet doesn't specify otherwise)
     * @since 9.6
     */

    public void setDefaultCollation(String collation) {
        this.defaultCollation = collation;
    }

    /**
     * Get the name of the default collation
     *
     * @return the default collation. If none has been specified at the level of this CompilerInfo,
     * this defaults to the default collation defined in the Configuration, which in turn defaults
     * to the Unicode codepoint collation
     * @since 9.6
     */

    public String getDefaultCollation() {
        return this.defaultCollation;
    }

    /**
     * Set whether trace hooks are to be included in the compiled code. To use tracing, it is necessary
     * both to compile the code with trace hooks included, and to supply a TraceListener at run-time
     *
     * @param injector the code injector used to insert trace or debugging hooks, or null to clear any
     *                 existing entry
     * @since 9.4
     */

    public void setCodeInjector(CodeInjector injector) {
        codeInjector = injector;
    }

    /**
     * Get the registered CodeInjector, if any
     *
     * @return the code injector used to insert trace or debugging hooks, or null if absent
     */

    public CodeInjector getCodeInjector() {
        return codeInjector;
    }

    /**
     * Determine whether trace hooks are included in the compiled code.
     *
     * @return true if trace hooks are included, false if not.
     * @since 8.9
     */

    public boolean isCompileWithTracing() {
        return codeInjector != null;
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
     *                    is treated as schema-aware only if it contains one or more xsl:import-schema declarations
     * @since 9.2
     */

    public void setSchemaAware(boolean schemaAware) {
        this.schemaAware = schemaAware;
    }

    /**
     * Ask whether schema-awareness has been requested by means of a call on
     * {@link #setSchemaAware}
     *
     * @return true if schema-awareness has been requested
     */

    public boolean isSchemaAware() {
        return schemaAware;
    }

    /**
     * Set the default initial template name for a stylesheet compiled using this CompilerInfo.
     * This is only a default; it can be overridden when the stylesheet is executed
     *
     * @param initialTemplate the name of the default initial template, or null if there is
     *                        no default. No error occurs (until run-time) if the stylesheet does not contain a template
     *                        with this name.
     * @since 9.3
     */

    public void setDefaultInitialTemplate(StructuredQName initialTemplate) {
        defaultInitialTemplate = initialTemplate;
    }

    /**
     * Get the default initial template name for a stylesheet compiled using this CompilerInfo.
     * This is only a default; it can be overridden when the stylesheet is executed
     *
     * @return the name of the default initial template, or null if there is
     * no default, as set using {@link #setDefaultInitialTemplate}
     * @since 9.3
     */

    public StructuredQName getDefaultInitialTemplate() {
        return defaultInitialTemplate;
    }

    /**
     * Set the default initial mode name for a stylesheet compiled using this CompilerInfo.
     * This is only a default; it can be overridden when the stylesheet is executed
     *
     * @param initialMode the name of the default initial mode, or null if there is
     *                    no default. No error occurs (until run-time) if the stylesheet does not contain a mode
     *                    with this name.
     * @since 9.3
     */

    public void setDefaultInitialMode(StructuredQName initialMode) {
        defaultInitialMode = initialMode;
    }

    /**
     * Get the default initial mode name for a stylesheet compiled using this CompilerInfo.
     * This is only a default; it can be overridden when the stylesheet is executed
     *
     * @return the name of the default initial mode, or null if there is
     * no default, as set using {@link #setDefaultInitialMode}
     * @since 9.3
     */

    public StructuredQName getDefaultInitialMode() {
        return defaultInitialMode;
    }

    /**
     * Set the version of XSLT to be supported by this processor. From Saxon 9.8 this has
     * no effect; the processor will always be an XSLT 3.0 processor.
     *
     * @param version The decimal version number times ten, for example 30 indicates
     *                XSLT 3.0.
     * @since 9.3. Changed in 9.7 to take an integer rather than a decimal. Ignored from 9.8.
     * @deprecated since Saxon 9.8 (has no effect).
     */

    public void setXsltVersion(int version) {
    }

    /**
     * Get the version of XSLT requested for this compilation.
     *
     * @return 30 (for XSLT 3.0)
     * @since 9.3  Changed in 9.7 to take an integer rather than a decimal. Changed in 9.8 to return the
     * value 30 unconditionally.
     * @deprecated since 9.8 (always returns 30)
     */

    public int getXsltVersion() {
        return 30;
    }

    public String getDefaultElementNamespace() {
        return defaultNamespaceForElementsAndTypes;
    }

    public void setDefaultElementNamespace(String defaultNamespaceForElementsAndTypes) {
        this.defaultNamespaceForElementsAndTypes = defaultNamespaceForElementsAndTypes;
    }


    public UnprefixedElementMatchingPolicy getUnprefixedElementMatchingPolicy() {
        return unprefixedElementMatchingPolicy;
    }

    public void setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy unprefixedElementMatchingPolicy) {
        this.unprefixedElementMatchingPolicy = unprefixedElementMatchingPolicy;
    }


    public void setXQueryLibraries(Collection<QueryLibrary> libraries) {
        this.queryLibraries = libraries;
    }

    public Collection<QueryLibrary> getQueryLibraries() {
        return queryLibraries;
    }

}

