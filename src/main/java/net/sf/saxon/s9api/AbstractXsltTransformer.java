////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.GlobalParameterSet;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.Durability;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.transpile.CSharpInnerClass;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.tree.tiny.TinyBuilder;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A class that exists to contain common code shared between {@link XsltTransformer} and
 * {@link Xslt30Transformer}. Not intended for external use.
 */

//@CSharpInjectMembers(code = {
//        "    public void setErrorReporter(System.Action<net.sf.saxon.s9api.XmlProcessingError> reporter) {"
//                + "        setErrorReporter(new Saxon.Impl.Helpers.ErrorReportingAction(reporter));"
//                + "    }"
//})
@CSharpModifiers(code = {"abstract", "internal"})
public abstract class AbstractXsltTransformer {
    protected Processor processor;
    protected XsltController controller;
    protected boolean baseOutputUriWasSet = false;
    private MessageListener2 messageListener2;

    AbstractXsltTransformer(Processor processor, XsltController controller) {
        this.processor = processor;
        this.controller = controller;
    }

    /**
     * Set the base output URI.
     * <p>This defaults to the base URI of the {@link Destination} for the principal output
     * of the transformation if a destination is supplied and its base URI is known.</p>
     * <p>If a base output URI is supplied using this method then it takes precedence
     * over any base URI defined in the supplied {@code Destination} object, and
     * it may cause the base URI of the {@code Destination} object to be modified in situ.</p>
     * <p> The base output URI is used for resolving relative URIs in the <code>href</code> attribute
     * of the <code>xsl:result-document</code> instruction; it is accessible to XSLT stylesheet
     * code using the XPath {@code current-output-uri()} function</p>
     *
     * @param uri the base output URI
     */

    public synchronized void setBaseOutputURI(String uri) {
        controller.setBaseOutputURI(uri);
        baseOutputUriWasSet = uri != null;
    }

    /**
     * Get the base output URI.
     * <p>This returns the value set using the {@link #setBaseOutputURI} method. If no value has been set
     * explicitly, then the method returns null if called before the transformation, or the computed
     * default base output URI if called after the transformation.</p>
     * <p>The base output URI is used for resolving relative URIs in the <code>href</code> attribute
     * of the <code>xsl:result-document</code> instruction.</p>
     *
     * @return the base output URI
     */

    public String getBaseOutputURI() {
        return controller.getBaseOutputURI();
    }

    /**
     * Set an object that will be used to resolve URIs used in
     * fn:doc() and related functions.
     *
     * @param resolver An object that implements the URIResolver interface, or
     *                 null.
     * @deprecated since 11.1. Use {@link #setResourceResolver} in preference.
     */

    @Deprecated
    public void setURIResolver(URIResolver resolver) {
        controller.setResourceResolver(new ResourceResolverWrappingURIResolver(resolver));
    }

    /**
     * Set the <code>ResourceResolver</code> to be used during stylesheet execution.
     * The <code>ResourceResolver</code> is used for dereferencing
     * an absolute URI (after URI resolution) to return a {@link javax.xml.transform.Source} representing the
     * required resource.
     * <p>This <code>ResourceResolver</code> is used to dereference the URIs appearing in the <code>doc()</code>,
     * <code>doc-available()</code>, and <code>document()</code> functions: in these cases it may return any
     * supported <code>Source</code> object.</p>
     * <p>It is also used to dereference the URI supplied to the <code>xsl:source-document</code>
     * instruction. In this case the resource request passed to the resolver indicates whether the
     * instruction has the attribute <code>streamable="yes"</code>; if it does, the returned
     * <code>Source</code> object must be a <code>StreamSource</code> or <code>SAXSource</code>.</p>
     * <p>The resolver is also used for a number of other cases where URIs are dereferenced during
     * stylesheet execution, for example in the <code>fn:transform</code> and
     * <code>fn:unparsed-binary</code> functions.</p>
     *
     * @param resolver the <code>ResourceResolver</code> to be used during stylesheet execution.
     */

    public void setResourceResolver(ResourceResolver resolver) {
        controller.setResourceResolver(resolver);
    }

    /**
     * Get the resource resolver.
     *
     * @return the user-supplied resource resolver if there is one, or null otherwise
     */

    public ResourceResolver getResourceResolver() {
        return controller.getResourceResolver();
    }

    /**
     * Get the URI resolver.
     *
     * @return the user-supplied URI resolver if there is one, or null otherwise
     */

    public URIResolver getURIResolver() {
        ResourceResolver resolver = controller.getResourceResolver();
        if (resolver instanceof ResourceResolverWrappingURIResolver) {
            return ((ResourceResolverWrappingURIResolver)resolver).getWrappedURIResolver();
        }
        return null;
    }

    /**
     * Set an object that will be used to resolve URIs used in
     * <code>fn:unparsed-text()</code> and related functions.
     *
     * @param resolver An object that implements the UnparsedTextURIResolver interface, or
     *                 null.
     * @since 11
     */

    public void setUnparsedTextResolver(UnparsedTextURIResolver resolver) {
        controller.setUnparsedTextURIResolver(resolver);
    }

    /**
     * Get the URI resolver used for <code>fn:unparsed-text()</code> and related functions.
     *
     * @return the user-supplied URI resolver if there is one, or the
     * system-defined one otherwise
     * @since 11
     */

    public UnparsedTextURIResolver getUnparsedTextURIResolver() {
        return controller.getUnparsedTextURIResolver();
    }


    /**
     * Set the ErrorListener to be used during this transformation
     *
     * @param listener The error listener to be used. This is notified of all dynamic errors detected during the
     *                 transformation.
     */

    public void setErrorListener(ErrorListener listener) {
        controller.setErrorReporter(new ErrorReporterToListener(listener));
    }

    /**
     * Get the ErrorListener being used during this transformation
     *
     * @return the error listener in use. If no user-supplied {@code ErrorListener} has been set the method will return
     * a system-supplied ErrorListener that delegates to the {@link ErrorReporter}.
     * If an explicit {@code ErrorListener} has been set using {@link #setErrorListener(ErrorListener)},
     * then that ErrorListener will generally be returned, unless the internal ErrorListener has been changed
     * by some other mechanism.
     */

    public ErrorListener getErrorListener() {
        ErrorReporter uel = controller.getErrorReporter();
        if (uel instanceof ErrorReporterToListener) {
            return ((ErrorReporterToListener) uel).getErrorListener();
        } else {
            return null;
        }
    }

    /**
     * Set a callback that will be used when reporting a dynamic error or warning. This is relevant primarily
     * for non-fatal errors.
     * <p>By default, errors are reported to a default error reporter which writes to
     * the logging destination associated with the Saxon {@link Configuration}, which
     * in turn defaults to the system error stream.</p>
     * @param reporter the callback
     */

    public void setErrorReporter(ErrorReporter reporter) {
        controller.setErrorReporter(reporter);
    }

    /**
     * Get the callback that will be used when reporting a dynamic error or warning
     * @return the callback
     */

    public ErrorReporter getErrorReporter() {
        return controller.getErrorReporter();
    }

    /**
     * Set a callback function that will be used when {@code xsl:result-document} is evaluated. The argument
     * is a function that takes a URI as input (specifically, the value of the {@code href} argument
     * to {@code xsl:result-document}, resolved against the base output URI of the transformation),
     * and returns a {@link Destination}, which will be used as the destination for the result document.
     * <p>If the {@code href} argument of the {@code xsl:result-document} instruction is absent or if
     * it is set to a zero length string, then the callback function is not normally called; instead
     * a {@code Receiver} for the secondary output is obtained by making a second call on
     * {@link Destination#getReceiver(PipelineConfiguration, SerializationProperties)}
     * for the principal destination of the transformation. In that situation, this result document handler
     * is invoked only if the call on {@link Destination#getReceiver(PipelineConfiguration, SerializationProperties)}
     * returns null. </p>
     * <p>If the base output URI is absent (perhaps because the principal output destination for the
     * transformation was supplied as a {@link OutputStream} or {@link Writer} with no associated
     * URI or systemId), then the value of the {@code href} attribute is used <i>as is</i> if it
     * is an absolute URI; if it is a relative URI (including the case where it is absent or zero-length)
     * then the callback function is not called; instead a dynamic error is raised (code
     * {@link SaxonErrorCode#SXRD0002}).</p>
     * <p>If the callback function throws a {@link SaxonApiUncheckedException}, this will result
     * in the {@code xsl:result-document} instruction failing with a dynamic error, which can be caught
     * using {@code xsl:try/xsl:catch}. The error code, by default, will be "err:SXRD0001".</p>
     * <p>The application can request to be notified when the {@code Destination} is closed by setting
     * a {@link Destination#onClose(Action)} callback on the {@code Destination} object.</p>
     *
     * @param handler the callback function to be invoked whenever an {@code xsl:result-document}
     *                instruction is evaluated.
     */

    @CSharpInnerClass(
            outer=false,
            extra={"System.Func<System.Uri,Saxon.Hej.s9api.Destination> handler"})
    
    public void setResultDocumentHandler(java.util.function.Function<URI, Destination> handler) {
        controller.setResultDocumentResolver(new ResultDocumentResolver() {
            @Override
            public Receiver resolve(
                    XPathContext context, String href, String baseUri, SerializationProperties properties)
                    throws XPathException {
                try {
                    URI abs = ResolveURI.makeAbsolute(href, baseUri);
                    Destination destination;
                    try {
                        destination = handler.apply(abs);
                    } catch (SaxonApiUncheckedException e) {
                        XPathException xe = XPathException.makeXPathException(e);
                        xe.maybeSetErrorCode("SXRD0001");
                        throw xe;
                    }
                    try {
                        PipelineConfiguration pipe = context.getController().makePipelineConfiguration();
                        return destination.getReceiver(pipe, properties);
                    } catch (SaxonApiException e) {
                        throw XPathException.makeXPathException(e);
                    }
                } catch (URISyntaxException e) {
                    throw XPathException.makeXPathException(e);
                }
            }
        });
    }

    /**
     * Set the MessageListener to be notified of {@code xsl:message} and {@code xsl:assert} output.
     * @param listener the MessageListener to be notified
     * @deprecated since 12.0 - use {@link #setMessageHandler(Consumer)}
     */

    @Deprecated
    public synchronized void setMessageListener(MessageListener2 listener) {
        messageListener2 = listener;
        setMessageHandler(message -> listener.message(
                message.getContent(), message.getErrorCode(), message.isTerminate(), message.getLocation())
        );
    }

    /**
     * Get the MessageListener2 to be notified whenever the stylesheet evaluates an
     * <code>xsl:message</code> instruction. If no MessageListener2 has been nominated,
     * return null
     *
     * @return the user-supplied MessageListener2, or null if none has been supplied
     * @deprecated since 12.0
     */

    @Deprecated
    public MessageListener2 getMessageListener2() {
        return messageListener2;
    }


    /**
     * Set a message handler to be notified of {@code xsl:message} and {@code xsl:assert} output.
     * @param messageHandler the message handler to be notified
     * @since 11
     */

    public void setMessageHandler(Consumer<Message> messageHandler) {
        controller.setMessageHandler(messageHandler);
    }

    /**
     * Get the message handler to be notified of {@code xsl:message} and {@code xsl:assert} output.
     *
     * @return the message handler to be notified
     * @since 13
     */

    public Consumer<Message> getMessageHandler(Consumer<Message> messageHandler) {
        return controller.getMessageHandler();
    }



    /**
     * Say whether assertions (xsl:assert instructions) should be enabled at run time. By default
     * they are disabled at compile time. If assertions are enabled at compile time, then by
     * default they will also be enabled at run time; but they can be disabled at run time by
     * specific request. At compile time, assertions can be enabled for some packages and
     * disabled for others; at run-time, they can only be enabled or disabled globally.
     *
     * @param enabled true if assertions are to be enabled at run time; this has no effect
     *                if assertions were disabled (for a particular package) at compile time
     * @since 9.7
     */

    public void setAssertionsEnabled(boolean enabled) {
        controller.setAssertionsEnabled(enabled);
    }

    /**
     * Ask whether assertions (xsl:assert instructions) have been enabled at run time. By default
     * they are disabled at compile time. If assertions are enabled at compile time, then by
     * default they will also be enabled at run time; but they can be disabled at run time by
     * specific request. At compile time, assertions can be enabled for some packages and
     * disabled for others; at run-time, they can only be enabled or disabled globally.
     *
     * @return true if assertions are enabled at run time
     * @since 9.7
     */

    public boolean isAssertionsEnabled() {
        return controller.isAssertionsEnabled();
    }

    /**
     * Set a TraceListener to be notified of all events occurring during the transformation.
     * This will only be effective if the stylesheet was compiled with trace code enabled
     * (see {@link XsltCompiler#setCompileWithTracing(boolean)})
     *
     * @param listener the TraceListener to be used. Note that the TraceListener has access to
     *                 interal Saxon interfaces which may vary from one release to the next. It is also possible that
     *                 the TraceListener interface itself may be changed in future releases.
     */

    public void setTraceListener(TraceListener listener) {
        controller.setTraceListener(listener);
    }

    /**
     * Get the TraceListener to be notified of all events occurring during the transformation.
     * If no TraceListener has been nominated, return null
     *
     * @return the user-supplied TraceListener, or null if none has been supplied
     */

    public TraceListener getTraceListener() {
        return controller.getTraceListener();
    }

    /**
     * Set the destination for output from the fn:trace() function.
     * By default, the destination is System.err. If a TraceListener is in use,
     * this is ignored, and the trace() output is sent to the TraceListener.
     *
     * @param stream the PrintStream to which trace output will be sent. If set to
     *               null, trace output is suppressed entirely. It is the caller's responsibility
     *               to close the stream after use.
     */

    public void setTraceFunctionDestination(Logger stream) {
        controller.setTraceFunctionDestination(stream);
    }

    /**
     * Get the destination for output from the fn:trace() function.
     *
     * @return the Logger to which trace output will be sent. If no explicitly
     * destination has been set, returns System.err. If the destination has been set
     * to null to suppress trace output, returns null.
     */

    public Logger getTraceFunctionDestination() {
        return controller.getTraceFunctionDestination();
    }


    protected void applyTemplatesToSource(Source source, Receiver out) throws XPathException {
        Objects.requireNonNull(source);
        Objects.requireNonNull(out);
        if (controller.getInitialMode().isDeclaredStreamable()) {
            controller.applyStreamingTemplates(source, out);
        } else {
            NodeInfo node;
            if (source instanceof NodeInfo) {
                node = (NodeInfo)source;
            } else {
                node = controller.makeSourceTree(source, controller.getSchemaValidationMode());
            }
            controller.applyTemplates(node, out);
        }
    }

    protected boolean isStreamableSource(Source source) {
        if (source instanceof AugmentedSource) {
            return isStreamableSource (((AugmentedSource) source).getContainedSource());
        }
        Configuration config = controller.getConfiguration();
        try {
            source = config.getSourceResolver().resolveSource(source, new ParseOptions(), config);
        } catch (XPathException e) {
            return false;
        }
        return source instanceof SAXSource || source instanceof StreamSource || source instanceof EventSource;
    }

    /**
     * Set the schema validation mode for the transformation. This indicates how source documents
     * loaded specifically for this transformation will be handled. This applies to the
     * principal source document if supplied as a SAXSource or StreamSource, and to all
     * documents loaded during the transformation using the <code>doc()</code>, <code>document()</code>,
     * or <code>collection()</code> functions.
     *
     * @param mode the validation mode. Passing null causes no change to the existing value.
     *             Passing {@link ValidationMode#DEFAULT} resets to the initial value, which determines
     *             the validation requirements from the Saxon Configuration.
     */

    public void setSchemaValidationMode(ValidationMode mode) {
        if (mode != null) {
            controller.setSchemaValidationMode(Validation.fromValidationMode(mode));
        }
    }

    /**
     * Get the schema validation mode for the transformation. This indicates how source documents
     * loaded specifically for this transformation will be handled. This applies to the
     * principal source document if supplied as a SAXSource or StreamSource, and to all
     * documents loaded during the transformation using the <code>doc()</code>, <code>document()</code>,
     * or <code>collection()</code> functions.
     *
     * @return the validation mode.
     */

    public ValidationMode getSchemaValidationMode() {
        return XQueryEvaluator.getSchemaValidationMode(controller.getSchemaValidationMode());
    }

    /**
     * Set the initial mode for the transformation
     *
     * @param modeName the name of the initial mode. Two special values are recognized, in the
     *                 reserved XSLT namespace:
     *                 xsl:unnamed to indicate the mode with no name, and xsl:default to indicate the
     *                 mode defined in the stylesheet header as the default mode.
     *                 The value null also indicates the default mode (which defaults to the unnamed
     *                 mode, but can be set differently in an XSLT 3.0 stylesheet).
     * @throws SaxonApiException if the requested mode is not defined in the stylesheet, or if
     * it is defined with visibility="private".
     * @since Changed in 9.6 to throw an exception if the mode is not defined in the stylesheet.
     * Changed in 9.7 so that null means the default mode, not necessarily the unnamed mode.
     * Changed in 11 to throw SaxonApiException rather than IllegalArgumentException, to
     * prevent the error code being lost.
     */

    public void setInitialMode(QName modeName) throws SaxonApiException {
        try {
            controller.setInitialMode(modeName == null ? null : modeName.getStructuredQName());
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get the name of the initial mode for the transformation, if one has been set.
     *
     * @return the initial mode for the transformation. Returns null if no mode has been set,
     *         or if the mode was set to null to represent the default (unnamed) mode
     */

    public QName getInitialMode() {
        StructuredQName mode = controller.getInitialModeName();
        if (mode == null) {
            return null;
        } else {
            return new QName(mode);
        }
    }

    /**
     * Get the underlying Controller used to implement this XsltTransformer. This provides access
     * to lower-level methods not otherwise available in the s9api interface. Note that classes
     * and methods obtained by this route cannot be guaranteed stable from release to release.
     *
     * @return the underlying {@link Controller}
     */

    public XsltController getUnderlyingController() {
        return controller;
    }

    /**
     * Get a Receiver corresponding to the chosen Destination for the transformation
     * @param controller the Controller for the transformation
     * @param destination the destination for the results of this transformation
     * @return a receiver that sends the results to this destination
     * @throws SaxonApiException if anything goes wrong
     */

    public Receiver getDestinationReceiver(XsltController controller, Destination destination) throws SaxonApiException {
        Receiver receiver;
        controller.setPrincipalDestination(destination);
        PipelineConfiguration pipe = controller.makePipelineConfiguration();
        SerializationProperties params = controller.getExecutable().getPrimarySerializationProperties();
        receiver = destination.getReceiver(pipe, params);
        if (Configuration.isAssertionsEnabled()) {
            receiver = new RegularSequenceChecker(receiver, true);
        }
        //receiver = new TracingFilter(receiver);
        if (receiver.getPipelineConfiguration().getController() == null) {  // bug 6626
            receiver.getPipelineConfiguration().setController(controller);
        }

        if (baseOutputUriWasSet) {
            try {
                if (destination.getDestinationBaseURI() == null) {
                    destination.setDestinationBaseURI(new URI(controller.getBaseOutputURI()));
                }
            } catch (URISyntaxException e) {
                // no action
            }
        } else if (destination.getDestinationBaseURI() != null) {
            controller.setBaseOutputURI(destination.getDestinationBaseURI().toASCIIString());
        }
        receiver.setSystemId(controller.getBaseOutputURI());
        return receiver;

    }

    /**
     * Return a Receiver which can be used to supply the principal source document for the transformation.
     * This method is intended primarily for internal use, though it can also
     * be called by a user application that wishes to feed events into the transformation engine.
     * <p>Saxon calls this method to obtain a Receiver, to which it then sends
     * a sequence of events representing the content of an XML document. This method is provided so that
     * <code>XsltTransformer</code> implements <code>Destination</code>, allowing one transformation
     * to receive the results of another in a pipeline.</p>
     *
     * @param controller the Controller for the transformation
     * @param parameters the global parameters
     * @param finalDestination the destination for the results of this transformation
     * @return the Receiver to which events are to be sent.
     * @throws SaxonApiException     if the Receiver cannot be created
     * @throws IllegalStateException if no Destination has been supplied
     */

    protected Receiver getReceivingTransformer(XsltController controller, GlobalParameterSet parameters, Destination finalDestination) throws SaxonApiException {
        Configuration config = controller.getConfiguration();
        if (controller.getInitialMode().isDeclaredStreamable()) {
            Receiver sOut = getDestinationReceiver(controller, finalDestination);
            try {
                controller.initializeController(parameters);
                return controller.getStreamingReceiver(controller.getInitialMode(), sOut);
            } catch (TransformerException e) {
                throw new SaxonApiException(e);
            }
        } else {
            final Builder sourceTreeBuilder = controller.makeBuilder();
            sourceTreeBuilder.setDurability(Durability.LASTING);
            if (sourceTreeBuilder instanceof TinyBuilder) {
                ((TinyBuilder) sourceTreeBuilder).setStatistics(config.getTreeStatistics().SOURCE_DOCUMENT_STATISTICS);
            }
            Receiver stripper = controller.makeStripper(sourceTreeBuilder);
            if (controller.isStylesheetStrippingTypeAnnotations()) {
                stripper = controller.getConfiguration().getAnnotationStripper(stripper);
            }
            return makeTreeReceiver(controller, parameters, finalDestination, sourceTreeBuilder, stripper);
        }
    }

    @CSharpInnerClass(outer=true,
            extra={ "Saxon.Hej.@event.Builder sourceTreeBuilder",
                    "Saxon.Hej.trans.XsltController controller",
                    "Saxon.Hej.s9api.Destination finalDestination",
                    "Saxon.Hej.expr.instruct.GlobalParameterSet parameters"})
    private TreeReceiver makeTreeReceiver(XsltController controller, GlobalParameterSet parameters, Destination finalDestination, Builder sourceTreeBuilder, Receiver stripper) {
        return new TreeReceiver(stripper) {
            boolean closed = false;

            @Override
            public void close() throws XPathException {
                if (!closed) {
                    try {
                        NodeInfo doc = sourceTreeBuilder.getCurrentRoot();
                        if (doc != null) {
                            doc.getTreeInfo().setSpaceStrippingRule(controller.getSpaceStrippingRule());
                            Receiver result = getDestinationReceiver(controller, finalDestination);
                            try {
                                controller.setGlobalContextItem(doc);
                                controller.initializeController(parameters);
                                controller.applyTemplates(doc, result);
                            } catch (TransformerException e) {
                                throw new SaxonApiException(e);
                            }
                        }
                    } catch (SaxonApiException e) {
                        throw XPathException.makeXPathException(e);
                    }
                    closed = true;
                }
            }
        };
    }

}

