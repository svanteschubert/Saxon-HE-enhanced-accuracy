////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.HostLanguage;

import java.util.HashMap;
import java.util.Map;

/**
 * A PipelineConfiguration sets options that apply to all the operations in a pipeline.
 * Unlike the global Configuration, these options are always local to a process.
 */

//@CSharpInjectMembers(code = {
//        "    public void setErrorReporter(System.Action<Saxon.Hej.s9api.XmlProcessingError> reporter) {"
//                + "        setErrorReporter(new Saxon.Impl.Helpers.ErrorReportingAction(reporter));"
//                + "    }"
//})
public class PipelineConfiguration {

    /*@NotNull*/ private Configuration config;
    private Controller controller;
    private ParseOptions parseOptions;
    private HostLanguage hostLanguage = HostLanguage.UNKNOWN;
    private Map<String, Object> components;
    private XPathContext context;
    private java.util.function.Function<NodeInfo, Object> copyInformee;

    /**
     * Create a PipelineConfiguration. Note: the normal way to create
     * a PipelineConfiguration is via the factory methods in the Controller and
     * Configuration classes
     *
     * @param config the Saxon configuration
     * @see Configuration#makePipelineConfiguration
     * @see Controller#makePipelineConfiguration
     */

    public PipelineConfiguration(/*@NotNull*/ Configuration config) {
        this.config = config;
        parseOptions = new ParseOptions();
    }

    public PipelineConfiguration(/*@NotNull*/ Configuration config, ParseOptions parseOptions) {
        this.config = config;
        this.parseOptions = parseOptions;
    }

    /**
     * Create a PipelineConfiguration as a copy of an existing
     * PipelineConfiguration
     *
     * @param p the existing PipelineConfiguration
     */

    public PipelineConfiguration(PipelineConfiguration p) {
        config = p.config;
        controller = p.controller;
        parseOptions = p.parseOptions;
        hostLanguage = p.hostLanguage;
        if (p.components != null) {
            components = new HashMap<>(p.components);
        }
        context = p.context;
        copyInformee = null;
    }

    /**
     * Get the Saxon Configuration object
     *
     * @return the Saxon Configuration
     */

    /*@NotNull*/
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Set the Saxon Configuration object
     *
     * @param config the Saxon Configuration
     */

    public void setConfiguration(/*@NotNull*/ Configuration config) {
        this.config = config;
    }

    /**
     * Get an ErrorListener for reporting errors in processing this pipeline; this
     * will be the ErrorListener set locally in the PipelineConfiguration if there is one,
     * or the ErrorListener from the Configuration otherwise.
     *
     * @return the ErrorListener to be used; never null
     */

    public ErrorReporter getErrorReporter() {
        ErrorReporter reporter = getParseOptions().getErrorReporter();
        if (reporter == null) {
            reporter = controller == null ? config.makeErrorReporter() : controller.getErrorReporter();
        }
        return reporter;
    }

    /**
     * Set the ErrorListener used for reporting errors in processing this pipeline
     *
     * @param errorReporter the ErrorListener
     */

    public void setErrorReporter(ErrorReporter errorReporter) {
        parseOptions = getParseOptions().withErrorReporter(errorReporter);
    }

    /**
     * Set the document parsing and building options to be used on this pipeline
     *
     * @param options the options to be used
     */

    public void setParseOptions(ParseOptions options) {
        parseOptions = options;
    }

    /**
     * Get the document parsing and building options to be used on this pipeline
     * return the options to be used
     *
     * @return the parser options for this pipeline
     */

    public ParseOptions getParseOptions() {
        if (parseOptions == null) {
            parseOptions = config.getParseOptions();
        }
        return parseOptions;
    }

    /**
     * Say whether xsi:schemaLocation and xsi:noNamespaceSchemaLocation attributes
     * should be recognized while validating an instance document
     *
     * @param recognize true if these attributes should be recognized
     */

    public void setUseXsiSchemaLocation(boolean recognize) {
        parseOptions = getParseOptions().withUseXsiSchemaLocation(recognize);
    }

    /**
     * Say whether validation errors encountered on this pipeline should be treated as fatal
     * or as recoverable.
     * <p>Note this is a shortcut for <code>getParseOptions().setContinueAfterValidationErrors()</code>, retained
     * for backwards compatibility.</p>
     *
     * @param recover set to true if validation errors are to be treated as recoverable. If this option is set to true,
     *                such errors will be reported to the ErrorListener using the error() method, and validation will continue.
     *                If it is set to false, errors will be reported using the fatalError() method, and validation will
     *                be abandoned.  The default depends on the circumstances: typically during standalone instance validation
     *                the default is true, but during XSLT and XQuery processing it is false.
     */

    public void setRecoverFromValidationErrors(boolean recover) {
        parseOptions = getParseOptions().withContinueAfterValidationErrors(recover);
    }

    /**
     * Ask if this pipeline recovers from validation errors
     * <p>Note this is a shortcut for <code>getParseOptions().isContinueAfterValidationErrors()</code>, retained
     * for backwards compatibility.</p>
     *
     * @return true if validation errors on this pipeline are treated as recoverable; false if they are treated
     *         as fatal
     */

    public boolean isRecoverFromValidationErrors() {
        return getParseOptions().isContinueAfterValidationErrors();
    }

//    /**
//     * Set a user-defined SchemaURIResolver for resolving URIs used in "import schema"
//     * declarations.
//     *
//     * @param resolver the SchemaURIResolver
//     */
//
//    public void setSchemaURIResolver(SchemaURIResolver resolver) {
//        schemaURIResolver = resolver;
//    }

    /**
     * Get the controller associated with this pipelineConfiguration
     *
     * @return the controller if it is known; otherwise null.
     */

    public Controller getController() {
        return controller;
    }

    /**
     * Set the Controller associated with this pipelineConfiguration
     *
     * @param controller the Controller
     */

    public void setController(Controller controller) {
        this.controller = controller;
    }

    /**
     * Get the host language in use
     *
     * @return for example {@link HostLanguage#XSLT} or {@link HostLanguage#XQUERY}
     */

    public HostLanguage getHostLanguage() {
        if (hostLanguage == HostLanguage.UNKNOWN) {
            hostLanguage = controller == null ? HostLanguage.UNKNOWN : controller.getExecutable().getHostLanguage();
        }
        return hostLanguage;
    }

    /**
     * Ask if the host language is XSLT
     * @return true if the host language is XSLT
     */

    public boolean isXSLT() {
        return getHostLanguage() == HostLanguage.XSLT;
    }

    /**
     * Set the host language in use
     *
     * @param language for example {@link HostLanguage#XSLT} or {@link HostLanguage#XQUERY}
     */

    public void setHostLanguage(HostLanguage language) {
        hostLanguage = language;
    }

    /**
     * Set a named component of the pipeline
     *
     * @param name  string the component name
     * @param value the component value
     */

    public void setComponent(String name, Object value) {
        if (components == null) {
            components = new HashMap<>();
        }
        components.put(name, value);
    }

    public void setCopyInformee(java.util.function.Function<NodeInfo, Object> informee) {
        this.copyInformee = informee;
    }

    public java.util.function.Function<NodeInfo, Object> getCopyInformee() {
        return this.copyInformee;
    }

    /**
     * Get a named component of the pipeline
     *
     * @param name string the component name
     * @return the component value, or null if absent
     */

    public Object getComponent(String name) {
        if (components == null) {
            return null;
        } else {
            return components.get(name);
        }
    }

    /**
     * Set the XPathContext. Used during validation, for diagnostics, to identify the source node that was
     * being processed at the time a validation error in the result document was found
     * @param context the XPath dynamic context.
     */

    public void setXPathContext(XPathContext context) {
        this.context = context;
    }

    /**
     * Get the XPathContext. Used during validation, for diagnostics, to identify the source node that was
     * being processed at the time a validation error in the result document was found
     *
     * @return the XPath dynamic context.
     */

    public XPathContext getXPathContext() {
        return context;
    }

}

