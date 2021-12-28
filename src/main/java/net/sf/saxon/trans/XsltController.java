////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.accum.AccumulatorManager;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Destination;
import net.sf.saxon.serialize.Emitter;
import net.sf.saxon.serialize.MessageEmitter;
import net.sf.saxon.serialize.PrincipalOutputGatekeeper;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.wrapper.SpaceStrippedDocument;
import net.sf.saxon.tree.wrapper.SpaceStrippedNode;
import net.sf.saxon.tree.wrapper.TypeStrippedDocument;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.util.*;
import java.util.function.Supplier;

/**
 * This class is an extension of the Controller class, used when executing XSLT stylesheets.
 *
 * @since 9.9
 */

public class XsltController extends Controller {

    private final Map<StructuredQName, Integer> messageCounters = new HashMap<>();
    private Receiver explicitMessageReceiver = null;
    private Supplier<Receiver> messageFactory = () -> new NamespaceDifferencer(new MessageEmitter(), new Properties());
    private boolean assertionsEnabled = true;
    private ResultDocumentResolver resultDocumentResolver;
    private HashSet<DocumentKey> allOutputDestinations;
    private Component.M initialMode = null;
    private Function initialFunction = null;
    private Map<StructuredQName, Sequence> initialTemplateParams;
    private Map<StructuredQName, Sequence> initialTemplateTunnelParams;
    private Map<Long, Stack<AttributeSet>> attributeSetEvaluationStacks = new HashMap<>();
    private AccumulatorManager accumulatorManager = new AccumulatorManager();
    private PrincipalOutputGatekeeper gatekeeper = null;
    private Destination principalDestination;

    public XsltController(Configuration config, PreparedStylesheet pss) {
        super(config, pss);
    }

    /**
     * <p>Reset this <code>Transformer</code> to its original configuration.</p>
     * <p><code>Transformer</code> is reset to the same state as when it was created with
     * {@link javax.xml.transform.TransformerFactory#newTransformer()},
     * {@link javax.xml.transform.TransformerFactory#newTransformer(javax.xml.transform.Source source)} or
     * {@link javax.xml.transform.Templates#newTransformer()}.
     * <code>reset()</code> is designed to allow the reuse of existing <code>Transformer</code>s
     * thus saving resources associated with the creation of new <code>Transformer</code>s.</p>
     * <p><i>The above is from the JAXP specification. With Saxon, it's unlikely that reusing a Transformer will
     * give any performance benefits over creating a new one. The one case where it might be beneficial is
     * to reuse the document pool (the set of documents that have been loaded using the doc() or document()
     * functions). Therefore, this method does not clear the document pool. If you want to clear the document
     * pool, call the method {@link #clearDocumentPool} as well.</i></p>
     * <p>The reset <code>Transformer</code> is not guaranteed to have the same {@link javax.xml.transform.URIResolver}
     * or {@link javax.xml.transform.ErrorListener} <code>Object</code>s, e.g. {@link Object#equals(Object obj)}.
     * It is guaranteed to have a functionally equal <code>URIResolver</code>
     * and <code>ErrorListener</code>.</p>
     *
     * @since 1.5
     */

    @Override
    public void reset() {
        super.reset();
        Configuration config = getConfiguration();
        validationMode = config.getSchemaValidationMode();
        accumulatorManager = new AccumulatorManager();

        traceListener = null;
        TraceListener tracer;
        try {
            tracer = config.makeTraceListener();
        } catch (XPathException err) {
            throw new IllegalStateException(err.getMessage());
        }
        if (tracer != null) {
            addTraceListener(tracer);
        }

        setModel(config.getParseOptions().getModel());

        globalContextItem = null;
        initialMode = null;
        clearPerTransformationData();
    }

    /**
     * Reset variables that need to be reset for each transformation if the controller
     * is serially reused
     */

    @Override
    protected synchronized void clearPerTransformationData() {
        super.clearPerTransformationData();
        principalResult = null;
        allOutputDestinations = null;
        if (messageCounters != null) {
            messageCounters.clear();
        }
    }

    /**
     * Set the initial mode for the transformation.
     * <p>XSLT 2.0 allows a transformation to be started in a mode other than the default mode.
     * The transformation then starts by looking for the template rule in this mode that best
     * matches the initial context node.</p>
     * <p>This method may eventually be superseded by a standard JAXP method.</p>
     *
     * @param expandedModeName the name of the initial mode.  The mode is
     *                         supplied as an expanded QName. Two special values are recognized, in the
     *                         reserved XSLT namespace:
     *                         xsl:unnamed to indicate the mode with no name, and xsl:default to indicate the
     *                         mode defined in the stylesheet header as the default mode.
     *                         The value null also indicates the unnamed mode.
     * @throws XPathException if the requested mode is not defined in the stylesheet
     * @since 8.4 Changed in 9.6 to accept a StructuredQName, and to throw an error if the mode
     * has not been defined. Changed in 9.7 so that null indicates the default mode, which defaults
     * to the unnamed mode but can be set otherwise in the stylesheet.
     */

    public void setInitialMode(StructuredQName expandedModeName) throws XPathException {
        if (expandedModeName == null || expandedModeName.equals(Mode.UNNAMED_MODE_NAME)) {
            Mode initial = ((PreparedStylesheet) executable).getRuleManager().obtainMode(Mode.UNNAMED_MODE_NAME, true);
            initialMode = initial.getDeclaringComponent();
        } else {
            StylesheetPackage topLevelPackage = (StylesheetPackage) executable.getTopLevelPackage();
            if (expandedModeName.equals(Mode.DEFAULT_MODE_NAME)) {
                StructuredQName defaultModeName = topLevelPackage.getDefaultMode();
                if (!expandedModeName.equals(defaultModeName)) {
                    setInitialMode(defaultModeName);
                }
            } else {
                boolean declaredModes = topLevelPackage.isDeclaredModes();
                SymbolicName sn = new SymbolicName(StandardNames.XSL_MODE, expandedModeName);
                Component.M c = (Component.M) topLevelPackage.getComponent(sn);
                if (c == null) {
                    throw new XPathException("Requested initial mode " + expandedModeName + " is not defined in the stylesheet", "XTDE0045");
                }
                if (!((PreparedStylesheet) executable).isEligibleInitialMode(c)) {
                    throw new XPathException("Requested initial mode " + expandedModeName + " is private in the top-level package", "XTDE0045");
                }
                initialMode = c;
                if (!declaredModes && initialMode.getActor().isEmpty() && !expandedModeName.equals(topLevelPackage.getDefaultMode())) {
                    throw new XPathException("Requested initial mode " + expandedModeName + " contains no template rules", "XTDE0045");
                }
            }
        }
    }

    /**
     * Get the name of the initial mode for the transformation
     *
     * @return the name of the initial mode; null indicates
     * that the initial mode is the unnamed mode.
     */

    public StructuredQName getInitialModeName() {
        return initialMode == null ? null : initialMode.getActor().getModeName();
    }

    /**
     * Get the initial mode for the transformation
     *
     * @return the initial mode. This will be the default/unnamed mode if no specific mode
     * has been requested
     */

    /*@NotNull*/
    public Mode getInitialMode() {
        if (initialMode == null) {
            StylesheetPackage top = (StylesheetPackage) executable.getTopLevelPackage();
            StructuredQName defaultMode = top.getDefaultMode();
            if (defaultMode == null) {
                defaultMode = Mode.UNNAMED_MODE_NAME;
            }
            Component.M c = (Component.M) top.getComponent(new SymbolicName(StandardNames.XSL_MODE, defaultMode));
            initialMode = c;
            return c.getActor();
        } else {
            return initialMode.getActor();
        }
    }

    /**
     * Get the accumulator manager for this transformation. May be null if no accumulators are in use
     *
     * @return the accumulator manager, which holds dynamic information about the values of each
     * accumulator for each document
     */

    public AccumulatorManager getAccumulatorManager() {
        return accumulatorManager;
    }

    /**
     * Check that an output destination has not been used before, optionally adding
     * this URI to the set of URIs that have been used.
     * <p>This method is intended for internal use only.</p>
     *
     * @param uri the URI to be used as the output destination
     * @return true if the URI is available for use; false if it has already been used.
     */

    public synchronized boolean checkUniqueOutputDestination(/*@Nullable*/ DocumentKey uri) {
        if (uri == null) {
            return true;    // happens when writing say to an anonymous StringWriter
        }
        if (allOutputDestinations == null) {
            allOutputDestinations = new HashSet<>(20);
        }

        return !allOutputDestinations.contains(uri);
    }

    /**
     * Add a URI to the set of output destinations that cannot be written to, either because
     * they have already been written to, or because they have been read
     *
     * @param uri A URI that is not available as an output destination
     */

    public void addUnavailableOutputDestination(DocumentKey uri) {
        if (allOutputDestinations == null) {
            allOutputDestinations = new HashSet<>(20);
        }
        allOutputDestinations.add(uri);
    }

    /**
     * Remove a URI from the set of output destinations that cannot be written to or read from.
     * Used to support saxon:discard-document()
     *
     * @param uri A URI that is being made available as an output destination
     */

    public void removeUnavailableOutputDestination(DocumentKey uri) {
        if (allOutputDestinations != null) {
            allOutputDestinations.remove(uri);
        }
    }

    /**
     * Determine whether an output URI is available for use. This method is intended
     * for use by applications, via an extension function.
     *
     * @param uri A uri that the application is proposing to use in the href attribute of
     *            xsl:result-document: if this function returns false, then the xsl:result-document
     *            call will fail saying the URI has already been used.
     * @return true if the URI is available for use. Note that this function is not "stable":
     * it may return different results for the same URI at different points in the transformation.
     */

    public boolean isUnusedOutputDestination(DocumentKey uri) {
        return allOutputDestinations == null || !allOutputDestinations.contains(uri);
    }

    /**
     * Set parameters for the initial template (whether this is a named template, or a template
     * rule invoked to process the initial input item)
     *
     * @param params Tunnel or non-tunnel parameters to be supplied to the initial template. The supplied
     *               values will be converted to the required type using the function conversion rules.
     *               Any surplus parameters are silently ignored. May be null.
     * @param tunnel true if these are tunnel parameters; false if they are non-tunnel parameters
     * @since 9.6
     */

    public void setInitialTemplateParameters(Map<StructuredQName, Sequence> params, boolean tunnel) {
        if (tunnel) {
            this.initialTemplateTunnelParams = params;
        } else {
            this.initialTemplateParams = params;
        }

    }

    /**
     * Get the parameters for the initial template
     *
     * @param tunnel true if the parameters to be returned are the tunnel parameters; false for the non-tunnel parameters
     * @return the parameters supplied using {@link #setInitialTemplateParameters(Map, boolean)}.
     * May be null.
     * @since 9.6
     */

    public Map<StructuredQName, Sequence> getInitialTemplateParameters(boolean tunnel) {
        return tunnel ? initialTemplateTunnelParams : initialTemplateParams;
    }

    /**
     * Supply a factory function that is called every time xsl:message is executed; the factory function
     * is responsible for creating a {@link Outputter} that receives the content of the message, and does
     * what it will with it.
     * @param messageReceiverFactory a factory function whose job it is to create a {@link Outputter} for
     *                               xsl:message output; the function should supply a new {@code Receiver}
     *                               each time it is called, because xsl:message calls may arise in different
     *                               threads and the Receiver is unlikely to be thread-safe.
     */

    public void setMessageFactory(Supplier<Receiver> messageReceiverFactory) {
        this.messageFactory = messageReceiverFactory;
    }

    /**
     * Set the message receiver class name. This is an alternative way (retained for compatibility)
     * of providing a factory for message receivers; it causes a message factory to be established
     * that instantiates the supplied class
     *
     * @param name the full name of the class to be instantiated to provide a receiver for xsl:message output. The name must
     *             be the name of a class that implements the {@link Receiver} interface, and that has a zero-argument public
     *             constructor.
     */

    public void setMessageReceiverClassName(String name) {
        if (!name.equals(MessageEmitter.class.getName())) {
            this.messageFactory = () -> {
                try {
                    Object messageReceiver = getConfiguration().getInstance(name, null);
                    if (!(messageReceiver instanceof Receiver)) {
                        throw new XPathException(name + " is not a Receiver");
                    }
                    return (Receiver) messageReceiver;
                } catch (XPathException e) {
                    throw new UncheckedXPathException(e);
                }
            };
        }
    }

    /**
     * Make a Receiver to be used for xsl:message output.
     * <p>This method is intended for internal use only. From 9.9.0.2 (bug 3979) this method
     * is called to obtain a new Receiver each time an xsl:message instruction is evaluated.</p>
     *
     * @return The newly constructed message Receiver
     */

    /*@NotNull*/
    public Receiver makeMessageReceiver() {
        return messageFactory.get();
    }

    /**
     * Set the Receiver to be used for xsl:message output.
     * <p>
     * Recent versions of the JAXP interface specify that by default the
     * output of xsl:message is sent to the registered ErrorListener. Saxon
     * does not implement this convention. Instead, the output is sent
     * to a default message emitter, which is a slightly customised implementation
     * of the standard Saxon Emitter interface.</p>
     * <p>
     * This interface can be used to change the way in which Saxon outputs
     * xsl:message output.</p>
     * <p>
     * It is not necessary to use this interface in order to change the destination
     * to which messages are written: that can be achieved by obtaining the standard
     * message emitter and calling its {@link Emitter#setWriter} method.</p>
     * <p>
     * Although any <code>Receiver</code> can be supplied as the destination for messages,
     * applications may find it convenient to implement a subclass of {@link SequenceWriter},
     * in which only the abstract <code>write()</code> method is implemented. This will have the effect that the
     * <code>write()</code> method is called to output each message as it is generated, with the <code>Item</code>
     * that is passed to the <code>write()</code> method being the document node at the root of an XML document
     * containing the contents of the message.
     * <p>
     * This method is intended for use by advanced applications. The Receiver interface
     * itself is subject to change in new Saxon releases.</p>
     * <p>
     * The supplied Receiver will have its open() method called once at the start of
     * the transformation, and its close() method will be called once at the end of the
     * transformation. Each individual call of an xsl:message instruction is wrapped by
     * calls of startDocument() and endDocument(). If terminate="yes" is specified on the
     * xsl:message call, the properties argument of the startDocument() call will be set
     * to the value {@link ReceiverOption#TERMINATE}.</p>
     *
     * @param receiver The receiver to receive xsl:message output.
     * @since 8.4; changed in 8.9 to supply a Receiver rather than an Emitter. Changed
     * in 9.9.0.2 so it is no longer supported in a configuration that allows multi-threading.
     */

    public void setMessageEmitter(Receiver receiver) {
        if (getConfiguration().getBooleanProperty(Feature.ALLOW_MULTITHREADING)) {
            throw new IllegalStateException("XsltController#setMessageEmitter() is not supported for a configuration that allows multi-threading. Use setMessageFactory() instead");
        }
        final Receiver messageReceiver = explicitMessageReceiver = receiver;
        receiver.setPipelineConfiguration(makePipelineConfiguration());
        if (receiver instanceof Emitter && ((Emitter) receiver).getOutputProperties() == null) {
            try {
                Properties props = new Properties();
                props.setProperty(OutputKeys.METHOD, "xml");
                props.setProperty(OutputKeys.INDENT, "yes");
                props.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                ((Emitter) receiver).setOutputProperties(props);
            } catch (XPathException e) {
                // no action
            }
        }
        setMessageFactory(() -> new ProxyReceiver(messageReceiver) {
            /**
             * End of output. Note that closing this receiver also closes the rest of the
             * pipeline.
             */
            @Override
            public void close() {
                //super.close();
            }
        });

    }

    /**
     * Get the Receiver used for xsl:message output. This returns the emitter
     * previously supplied to the {@link #setMessageEmitter} method, or the
     * default message emitter otherwise.
     *
     * @return the Receiver being used for xsl:message output
     * @since 8.4; changed in 8.9 to return a Receiver rather than an Emitter
     * @deprecated since 9.9.0.2; always returns null.
     */

    /*@Nullable*/
    public Receiver getMessageEmitter() {
        return explicitMessageReceiver;
    }

    /**
     * Increment a counter in the message counters. This method is called automatically
     * when xsl:message is executed, to increment the counter associated with a given
     * error code used in the xsl:message. The counters are available (in Saxon-PE and -EE)
     * using the saxon:message-counter() extension function.
     *
     * @param code the error code whose counter is to be incremented
     */

    public void incrementMessageCounter(StructuredQName code) {
        synchronized(messageCounters) {
            Integer c = messageCounters.get(code);
            int n = c == null ? 1 : c + 1;
            messageCounters.put(code, n);
        }
    }

    /**
     * Get the message counters
     *
     * @return a snapshot copy of the table of message counters, indicating the number of times xsl:message
     * has been called for each distinct error code
     */

    public Map<StructuredQName, Integer> getMessageCounters() {
        synchronized(messageCounters) {
            return new HashMap<>(messageCounters);
        }
    }

    /**
     * Set the URI resolver for secondary output documents.
     * <p>XSLT 2.0 introduces the <code>xsl:result-document</code> instruction,
     * allowing a transformation to have multiple result documents. JAXP does
     * not yet support this capability. This method allows an OutputURIResolver
     * to be specified that takes responsibility for deciding the destination
     * (and, if it wishes, the serialization properties) of secondary output files.</p>
     * <p>In Saxon 9.5, because xsl:result-document is now multi-threaded, the
     * supplied resolver is cloned each time a new result document is created.
     * The cloned resolved is therefore able to maintain information about
     * the specific result document for use when its close() method is called,
     * without worrying about thread safety.</p>
     *
     * @param resolver An object that implements the OutputURIResolver
     *                 interface, or null.
     * @since 8.4. Retained for backwards compatibility in 9.9, but superseded
     * by {@link #setResultDocumentResolver(ResultDocumentResolver)}
     */

    public void setOutputURIResolver(/*@Nullable*/ OutputURIResolver resolver) {
        OutputURIResolver our = resolver == null ? getConfiguration().getOutputURIResolver() : resolver;
        setResultDocumentResolver(new OutputURIResolverWrapper(our));
    }

    public ResultDocumentResolver getResultDocumentResolver() {
        return resultDocumentResolver;
    }

    public void setResultDocumentResolver(ResultDocumentResolver resultDocumentResolver) {
        this.resultDocumentResolver = resultDocumentResolver;
    }

    /**
     * Get the output URI resolver.
     *
     * @return the user-supplied URI resolver if there is one, or the
     * system-defined one otherwise.
     * @see #setOutputURIResolver
     * @since 8.4. Retained for backwards compatibility in 9.9; superseded by
     * {@link #getResultDocumentResolver()}
     */

    /*@Nullable*/
    public OutputURIResolver getOutputURIResolver() {
        if (resultDocumentResolver instanceof OutputURIResolverWrapper) {
            return ((OutputURIResolverWrapper) resultDocumentResolver).getOutputURIResolver();
        } else {
            return getConfiguration().getOutputURIResolver();
        }
    }

    /**
     * Supply the Controller with information about the principal destination of the transformation
     *
     * @param destination the principal destination
     */

    public void setPrincipalDestination(Destination destination) {
        principalDestination = destination;
    }

    /**
     * Get the principal destination that was supplied to the Controller
     * @return the principal destination
     */

    public Destination getPrincipalDestination() {
        return principalDestination;
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
        return assertionsEnabled;
    }

    /**
     * Ask whether assertions (xsl:assert instructions) have been enabled at run time. By default
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
        this.assertionsEnabled = enabled;
    }

    @Override
    public void preEvaluateGlobals(XPathContext context) throws XPathException {
        openMessageEmitter();
        super.preEvaluateGlobals(context);
        closeMessageEmitter();
    }

    /**
     * Perform a transformation from a Source document to a Result document.
     *
     * @param source The input for the source tree. May be null if and only if an
     *               initial template has been supplied.
     * @param out    The destination for the result sequence. The events that are written to this
     *               {@code Receiver} will form a <em>regular event sequence</em>
     *               as defined in {@link RegularSequenceChecker}. This event sequence represents
     *               the <em>raw results</em> of the transformation; subsequent processing
     *               such as sequence normalization (to construct a document node) is the
     *               responsibility of the Receiver itself.
     * @throws XPathException if the transformation fails. As a
     *                        special case, the method throws a TerminationException (a subclass
     *                        of XPathException) if the transformation was terminated using
     *                        xsl:message terminate="yes".
     */

    public void applyTemplates(Sequence source, Receiver out) throws XPathException {

        checkReadiness();
        openMessageEmitter();

        try {
            ComplexContentOutputter dest = prepareOutputReceiver(out);

            XPathContextMajor initialContext = newXPathContext();
            initialContext.createThreadManager();
            initialContext.setOrigin(this);

            boolean close = false;

            Mode mode = getInitialMode();
            if (mode == null) {
                throw new XPathException("Requested initial mode " +
                                                 (initialMode == null ? "#unnamed" : initialMode.getActor().getModeName().getDisplayName()) +
                                                 " does not exist", "XTDE0045");
            }
            if (!((PreparedStylesheet) executable).isEligibleInitialMode(initialMode)) {
                throw new XPathException("Requested initial mode " +
                                                 (mode.getModeName().getDisplayName()) +
                                                 " is not public or final", "XTDE0045");
            }

            warningIfStreamable(mode);

            // Determine whether we need to close the output stream at the end. We
            // do this if the Result object is a StreamResult and is supplied as a
            // system ID, not as a Writer or OutputStream

            boolean mustClose = false;

            // Process the source document by applying template rules to the initial context node

            ParameterSet ordinaryParams = null;
            if (initialTemplateParams != null) {
                ordinaryParams = new ParameterSet(initialTemplateParams);
            }
            ParameterSet tunnelParams = null;
            if (initialTemplateTunnelParams != null) {
                tunnelParams = new ParameterSet(initialTemplateTunnelParams);
            }

            SequenceIterator iter = source.iterate();

            MappingFunction preprocessor = getInputPreprocessor(mode);
            iter = new MappingIterator(iter, preprocessor);

            initialContext.trackFocus(iter);
            initialContext.setCurrentMode(initialMode);
            initialContext.setCurrentComponent(initialMode);

            TailCall tc = mode.applyTemplates(ordinaryParams, tunnelParams, null, dest, initialContext, Loc.NONE);
            while (tc != null) {
                tc = tc.processLeavingTail();
            }

            initialContext.waitForChildThreads();

            dest.close();

        } catch (TerminationException err) {
            //System.err.println("Processing terminated using xsl:message");
            if (!err.hasBeenReported()) {
                reportFatalError(err);
            }
            throw err;
        } catch (UncheckedXPathException err) {
            handleXPathException(err.getXPathException());
        } catch (XPathException err) {
            handleXPathException(err);
        } finally {
            inUse = false;
            closeMessageEmitter();
            if (traceListener != null) {
                traceListener.close();
            }
            principalResultURI = null;
        }
    }

    /**
     * Takes a Receiver that expects raw output (such as might be obtained from {@link Destination#getReceiver})
     * and prepends a pipeline of receivers needed to support transformation tasks, especially a
     * {@link ComplexContentOutputter}.
     * @param out a receiver expecting raw output
     * @return a receiver to which instructions in the stylesheet can write events. This receiver will have
     * been opened.
     * @throws XPathException if any failure occurs
     */

    private ComplexContentOutputter prepareOutputReceiver(Receiver out) throws XPathException {
        principalResult = out;
        if (principalResultURI == null) {
            principalResultURI = out.getSystemId();
        }

        if (getExecutable().createsSecondaryResult()) {
            // This is for the case where the stylesheet writes no output to the primary destination,
            // and then calls xsl:result-document with a null or empty href, in which case the xsl:result-document
            // output is sent to the primary output destination, but with different serialization properties.
            out = this.gatekeeper = new PrincipalOutputGatekeeper(this, out);
        }

        //out = new RegularSequenceChecker(out); // uncomment for debugging
        //out = new TracingFilter(out); // uncomment for debugging
        //NamespaceReducer nr = new NamespaceReducer(out);
        ComplexContentOutputter cco = new ComplexContentOutputter(out);
        cco.setSystemId(out.getSystemId());
        cco.open();
        return cco;
    }

    /**
     * Get the Gatekeeper object, which is used to ensure that we don't write a secondary
     * result document to the same destination as the principal result document
     * @return the gatekeeper object, or null if none is in use
     */

    public PrincipalOutputGatekeeper getGatekeeper() {
        return gatekeeper;
    }

    private MappingFunction getInputPreprocessor(Mode finalMode) {
        return item -> {
            if (item instanceof NodeInfo) {
                NodeInfo node = (NodeInfo) item;
                if (node.getConfiguration() == null) {
                    // must be a non-standard document implementation
                    throw new XPathException("The supplied source document must be associated with a Configuration");
                }

                if (!node.getConfiguration().isCompatible(executable.getConfiguration())) {
                    throw new XPathException(
                            "Source document and stylesheet must use the same or compatible Configurations",
                            SaxonErrorCode.SXXP0004);
                }

                if (node.getTreeInfo().isTyped() && !executable.isSchemaAware()) {
                    throw new XPathException("Cannot use a schema-validated source document unless the stylesheet is schema-aware");
                }

                if (isStylesheetStrippingTypeAnnotations() && node != globalContextItem) {
                    TreeInfo docInfo = node.getTreeInfo();
                    if (docInfo.isTyped()) {
                        TypeStrippedDocument strippedDoc = new TypeStrippedDocument(docInfo);
                        node = strippedDoc.wrap(node);
                    }
                }

                SpaceStrippingRule spaceStrippingRule = getSpaceStrippingRule();
                if (isStylesheetContainingStripSpace() && isStripSourceTree() && !(node instanceof SpaceStrippedNode)
                        && node != globalContextItem && node.getTreeInfo().getSpaceStrippingRule() != spaceStrippingRule) {
                    SpaceStrippedDocument strippedDoc = new SpaceStrippedDocument(node.getTreeInfo(), spaceStrippingRule);
                    // Edge case: the item might itself be a whitespace text node that is stripped
                    if (!SpaceStrippedNode.isPreservedNode(node, strippedDoc, node.getParent())) {
                        return EmptyIterator.emptyIterator();
                    }
                    node = strippedDoc.wrap(node);
                }

                if (getAccumulatorManager() != null) {
                    getAccumulatorManager().setApplicableAccumulators(node.getTreeInfo(), finalMode.getAccumulators());
                }
                return SingletonIterator.makeIterator(node);
            } else {
                return SingletonIterator.makeIterator(item);
            }
        };
    }

    private void warningIfStreamable(Mode mode) {
        if (mode.isDeclaredStreamable()) {
            warning((initialMode == null ? "" : getInitialMode().getModeTitle()) +
                            " is streamable, but the input is not supplied as a stream", SaxonErrorCode.SXWN9000, Loc.NONE);
        }
    }

    /**
     * Execute a transformation by evaluating a named template and delivering
     * the "raw result".
     *
     * <p>If any child threads are started (as a result of calls to xsl:result-document)
     * the method will wait for these to finish before returning to the caller.</p>
     *
     * @param initialTemplateName the entry point, the name of a named template
     * @param out    The destination for the result sequence. The events that are written to this
     *               {@code Receiver} will form a <em>regular event sequence</em>
     *               as defined in {@link RegularSequenceChecker}. This event sequence represents
     *               the <em>raw results</em> of the transformation; subsequent processing
     *               such as sequence normalization (to construct a document node) is the
     *               responsibility of the Receiver itself.
     * @throws XPathException if any dynamic error occurs (in this thread or in a child thread).
     *                        This includes the case where the named template does not exist.
     */

    public void callTemplate(StructuredQName initialTemplateName, Receiver out)
            throws XPathException {
        checkReadiness();
        openMessageEmitter();

        try {
            ComplexContentOutputter dest = prepareOutputReceiver(out);

            XPathContextMajor initialContext = newXPathContext();
            initialContext.createThreadManager();
            initialContext.setOrigin(this);

            if (globalContextItem != null) {
                initialContext.setCurrentIterator(new ManualIterator(globalContextItem));
            }

            // Process the source document by invoking the initial named template

            ParameterSet ordinaryParams = null;
            if (initialTemplateParams != null) {
                ordinaryParams = new ParameterSet(initialTemplateParams);
            }
            ParameterSet tunnelParams = null;
            if (initialTemplateTunnelParams != null) {
                tunnelParams = new ParameterSet(initialTemplateTunnelParams);
            }

            StylesheetPackage pack = (StylesheetPackage) executable.getTopLevelPackage();
            Component initialComponent = pack.getComponent(new SymbolicName(StandardNames.XSL_TEMPLATE, initialTemplateName));
            if (initialComponent == null) {
                throw new XPathException("Template " + initialTemplateName.getDisplayName() + " does not exist", "XTDE0040");
            }
            if (!pack.isImplicitPackage() && !(initialComponent.getVisibility() == Visibility.PUBLIC || initialComponent.getVisibility() == Visibility.FINAL)) {
                throw new XPathException("Template " + initialTemplateName.getDisplayName() + " is " + initialComponent.getVisibility().show(), "XTDE0040");
            }
            NamedTemplate t = (NamedTemplate) initialComponent.getActor();

            XPathContextMajor c2 = initialContext.newContext();
            initialContext.setOrigin(this);
            c2.setCurrentComponent(initialComponent);
            c2.openStackFrame(t.getStackFrameMap());
            c2.setLocalParameters(ordinaryParams);
            c2.setTunnelParameters(tunnelParams);


            TailCall tc = t.expand(dest, c2);
            while (tc != null) {
                tc = tc.processLeavingTail();
            }

            initialContext.waitForChildThreads();
            dest.close();
        } catch (UncheckedXPathException err) {
            handleXPathException(err.getXPathException());
        } catch (XPathException err) {
            handleXPathException(err);
        } finally {
            if (traceListener != null) {
                traceListener.close();
            }
            closeMessageEmitter();
            inUse = false;
        }
    }

    /**
     * Perform a transformation by applying templates in a streamable mode to a streamable
     * input document.
     *
     * @param source The input for the source tree. Must be (or resolve to) a StreamSource
     *               or SAXSource.
     * @param out    The destination for the result sequence. The events that are written to this
     *               {@code Receiver} will form a <em>regular event sequence</em>
     *               as defined in {@link RegularSequenceChecker}. This event sequence represents
     *               the <em>raw results</em> of the transformation; subsequent processing
     *               such as sequence normalization (to construct a document node) is the
     *               responsibility of the Receiver itself.
     * @throws XPathException if the transformation fails. As a
     *                        special case, the method throws a TerminationException (a subclass
     *                        of XPathException) if the transformation was terminated using
     *                        xsl:message terminate="yes".
     */

    public void applyStreamingTemplates(Source source, Receiver out) throws XPathException {
        checkReadiness();
        openMessageEmitter();

        ComplexContentOutputter dest = prepareOutputReceiver(out);

        boolean close = false;
        try {
            int validationMode = getSchemaValidationMode();

            Source underSource = source;
            if (source instanceof AugmentedSource) {
                close = ((AugmentedSource) source).isPleaseCloseAfterUse();
                int localValidate = ((AugmentedSource) source).getSchemaValidation();
                if (localValidate != Validation.DEFAULT) {
                    validationMode = localValidate;
                }
                underSource = ((AugmentedSource) source).getContainedSource();
            }
            Configuration config = getConfiguration();
            Source s2 = config.getSourceResolver().resolveSource(underSource, config);
            if (s2 != null) {
                underSource = s2;
            }
            if (!(source instanceof SAXSource || source instanceof StreamSource || source instanceof EventSource)) {
                throw new IllegalArgumentException("Streaming requires a SAXSource, StreamSource, or EventSource");
            }
            if (!initialMode.getActor().isDeclaredStreamable()) {
                throw new IllegalArgumentException("Initial mode is not streamable");
            }
            if (source instanceof SAXSource && config.getBooleanProperty(Feature.IGNORE_SAX_SOURCE_PARSER)) {
                // This option is provided to allow the parser set by applications such as Ant to be overridden by
                // the parser requested using FeatureKeys.SOURCE_PARSER
                ((SAXSource) source).setXMLReader(null);
            }

            XPathContextMajor initialContext = newXPathContext();
            initialContext.createThreadManager();
            initialContext.setOrigin(this);

            // Process the source document by applying template rules to the initial context node

            ParameterSet ordinaryParams = null;
            if (initialTemplateParams != null) {
                ordinaryParams = new ParameterSet(initialTemplateParams);
            }
            ParameterSet tunnelParams = null;
            if (initialTemplateTunnelParams != null) {
                tunnelParams = new ParameterSet(initialTemplateTunnelParams);
            }

            Receiver despatcher = config.makeStreamingTransformer(initialMode.getActor(), ordinaryParams, tunnelParams, dest, initialContext);
            if (config.isStripsAllWhiteSpace() || isStylesheetContainingStripSpace()) {
                despatcher = makeStripper(despatcher);
            }
            PipelineConfiguration pipe = despatcher.getPipelineConfiguration();
            pipe.getParseOptions().setSchemaValidationMode(this.validationMode);
            boolean verbose = getConfiguration().isTiming();
            if (verbose) {
                getConfiguration().getLogger().info("Streaming " + source.getSystemId());
            }
            try {
                Sender.send(source, despatcher, null);
            } catch (QuitParsingException e) {
                if (verbose) {
                    getConfiguration().getLogger().info("Streaming " + source.getSystemId() + " : early exit");
                }
            }

            initialContext.waitForChildThreads();
            dest.close();


        } catch (TerminationException err) {
            //System.err.println("Processing terminated using xsl:message");
            if (!err.hasBeenReported()) {
                reportFatalError(err);
            }
            throw err;
        } catch (UncheckedXPathException err) {
            handleXPathException(err.getXPathException());
        } catch (XPathException err) {
            handleXPathException(err);
        } finally {
            inUse = false;
            if (close && source instanceof AugmentedSource) {
                ((AugmentedSource) source).close();
            }
            if (traceListener != null) {
                traceListener.close();
            }
        }
    }



    /**
     * Get a receiver to which the input to this transformation can be supplied
     * as a stream of events, causing the transformation to be executed in streaming mode. <br>
     *
     * @param mode   the initial mode, which must be a streaming mode
     * @param result The output destination
     * @return a receiver to which events can be streamed
     * @throws XPathException if any dynamic error occurs
     */

    /*@Nullable*/
    public Receiver getStreamingReceiver(Mode mode, Receiver result)
            throws XPathException {
        // System.err.println("*** TransformDocument");

        checkReadiness();
        openMessageEmitter();

        // Determine whether we need to close the output stream at the end. We
        // do this if the Result object is a StreamResult and is supplied as a
        // system ID, not as a Writer or OutputStream

        ComplexContentOutputter dest = prepareOutputReceiver(result);

        final XPathContextMajor initialContext = newXPathContext();
        initialContext.setOrigin(this);
        //initialContext.setReceiver(dest);

        globalContextItem = null;

        // Process the source document by applying template rules to the initial context node

        if (!mode.isDeclaredStreamable()) {
            throw new XPathException("mode supplied to getStreamingReceiver() must be streamable");
        }
        Configuration config = getConfiguration();
        Receiver despatcher = config.makeStreamingTransformer(mode, null, null, dest, initialContext);
        if (despatcher == null) {
            throw new XPathException("Streaming requires Saxon-EE");
        }
        if (config.isStripsAllWhiteSpace() || isStylesheetContainingStripSpace()) {
            despatcher = makeStripper(despatcher);
        }
        despatcher.setPipelineConfiguration(makePipelineConfiguration());

        final Outputter finalResult = dest;
        return new ProxyReceiver(despatcher) {
            @Override
            public void close() throws XPathException {
                if (traceListener != null) {
                    traceListener.close();
                }
                closeMessageEmitter();
                finalResult.close();
                inUse = false;
            }
        };

    }

    private void openMessageEmitter() throws XPathException {
        if (explicitMessageReceiver != null) {
            explicitMessageReceiver.open();
            if (explicitMessageReceiver instanceof Emitter && ((Emitter) explicitMessageReceiver).getWriter() == null) {
                ((Emitter) explicitMessageReceiver).setStreamResult(getConfiguration().getLogger().asStreamResult());
            }
        }
    }

    private void closeMessageEmitter() throws XPathException {
        if (explicitMessageReceiver != null) {
            explicitMessageReceiver.close();
        }
    }

    /**
     * Get the stack of attribute sets being evaluated (for detection of cycles)
     *
     * @return the attribute set evaluation stack
     */

    public synchronized Stack<AttributeSet> getAttributeSetEvaluationStack() {
        long thread = Thread.currentThread().getId();
        return attributeSetEvaluationStacks.computeIfAbsent(thread, k -> new Stack<>());
    }

    public synchronized void releaseAttributeSetEvaluationStack() {
        long thread = Thread.currentThread().getId();
        attributeSetEvaluationStacks.remove(thread);
    }
}

