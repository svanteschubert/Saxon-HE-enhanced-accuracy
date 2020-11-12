////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
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
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
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

/**
 * A class that exists to contain common code shared between XsltTransformer and Xslt30Transformer
 */

abstract class AbstractXsltTransformer {
    protected Processor processor;
    protected XsltController controller;
    protected boolean baseOutputUriWasSet = false;
    private MessageListener messageListener;
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
     */

    public void setURIResolver(URIResolver resolver) {
        controller.setURIResolver(resolver);
    }

    /**
     * Get the URI resolver.
     *
     * @return the user-supplied URI resolver if there is one, or null otherwise
     */

    public URIResolver getURIResolver() {
        return controller.getURIResolver();
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
     * @return listener The error listener in use. This is notified of all dynamic errors detected during the
     * transformation. If no user-supplied ErrorListener has been set the method will return a system-supplied
     * ErrorListener. If an explicit ErrorListener has been set using {@link #setErrorListener(ErrorListener)},
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
     * Set a callback that will be used when reporting a dynamic error or warning
     */

    public void setErrorReporter(ErrorReporter reporter) {
        controller.setErrorReporter(reporter);
    }

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
     * a {@code Receiver} for the secondary output is obtained by making a second call on {@link Destination#getReceiver(PipelineConfiguration, SerializationProperties)}
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
     * Set the MessageListener to be notified whenever the stylesheet evaluates an
     * <code>xsl:message</code> instruction.  If no MessageListener is nominated,
     * the output of <code>xsl:message</code> instructions will be serialized and sent
     * to the standard error stream.
     *
     * @param listener the MessageListener to be used
     * @deprecated since 10.0 - use {@link #setMessageListener(MessageListener2)}
     */

    public synchronized void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
        controller.setMessageFactory(() -> new MessageListenerProxy(listener, controller.makePipelineConfiguration()));
    }

    /**
     * Set the MessageListener to be notified whenever the stylesheet evaluates an
     * <code>xsl:message</code> instruction.  If no MessageListener is nominated,
     * the output of <code>xsl:message</code> instructions will be serialized and sent
     * to the standard error stream.
     * <p>
     * <p>The <code>MessageListener2</code> interface differs from <code>MessageListener</code>
     * in allowing the error code supplied to xsl:message to be notified.</p>
     *
     * @param listener the MessageListener to be used
     */

    public synchronized void setMessageListener(MessageListener2 listener) {
        this.messageListener2 = listener;
        controller.setMessageFactory(() -> new MessageListener2Proxy(listener, controller.makePipelineConfiguration()));
    }

    /**
     * Get the MessageListener to be notified whenever the stylesheet evaluates an
     * <code>xsl:message</code> instruction. If no MessageListener has been nominated,
     * return null
     *
     * @return the user-supplied MessageListener, or null if none has been supplied
     */

    public MessageListener getMessageListener() {
        return messageListener;
    }

    /**
     * Get the MessageListener2 to be notified whenever the stylesheet evaluates an
     * <code>xsl:message</code> instruction. If no MessageListener2 has been nominated,
     * return null
     *
     * @return the user-supplied MessageListener2, or null if none has been supplied
     */

    public MessageListener2 getMessageListener2() {
        return messageListener2;
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
        if (controller.getInitialMode().isDeclaredStreamable() && isStreamableSource(source)) {
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
            source = config.getSourceResolver().resolveSource(source, config);
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
            controller.setSchemaValidationMode(mode.getNumber());
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
        return ValidationMode.get(controller.getSchemaValidationMode());
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
     * @throws IllegalArgumentException if the requested mode is not defined in the stylesheet
     * @since changed in 9.6 to throw an exception if the mode is not defined in the stylesheet.
     * Chaned in 9.7 so that null means the default mode, not necessarily the unnamed mode.
     */

    public void setInitialMode(QName modeName) throws IllegalArgumentException {
        try {
            controller.setInitialMode(modeName == null ? null : modeName.getStructuredQName());
        } catch (XPathException e) {
            throw new IllegalArgumentException(e);
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
        receiver.getPipelineConfiguration().setController(controller);

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
            if (sourceTreeBuilder instanceof TinyBuilder) {
                ((TinyBuilder) sourceTreeBuilder).setStatistics(config.getTreeStatistics().SOURCE_DOCUMENT_STATISTICS);
            }
            Receiver stripper = controller.makeStripper(sourceTreeBuilder);
            if (controller.isStylesheetStrippingTypeAnnotations()) {
                stripper = controller.getConfiguration().getAnnotationStripper(stripper);
            }
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

}

