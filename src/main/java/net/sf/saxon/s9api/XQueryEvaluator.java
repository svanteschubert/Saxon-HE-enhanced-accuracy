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
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.GlobalContextRequirement;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.*;
import net.sf.saxon.query.DynamicQueryContext;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.s9api.streams.XdmStream;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.type.TypeHierarchy;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * An <code>XQueryEvaluator</code> represents a compiled and loaded query ready for execution.
 * The <code>XQueryEvaluator</code> holds details of the dynamic evaluation context for the query.
 * <p>An <code>XQueryEvaluator</code> must not be used concurrently in multiple threads.
 * It is safe, however, to reuse the object within a single thread to run the same
 * query several times. Running the query does not change the context
 * that has been established.</p>
 * <p>An <code>XQueryEvaluator</code> is always constructed by running the <code>Load</code>
 * method of an {@link net.sf.saxon.s9api.XQueryExecutable}.</p>
 * <p>An <code>XQueryEvaluator</code> is itself a <code>Iterable</code>. This makes it possible to
 * evaluate the results in a for-each expression.</p>
 * <p>An <code>XQueryEvaluator</code> is itself a <code>Destination</code>. This means it is possible to use
 * one <code>XQueryEvaluator</code> as the destination to receive the results of another transformation,
 * this providing a simple way for transformations to be chained into a pipeline. When the query is executed
 * this way, {@link #setDestination(Destination)} must be called to provide a destination for the result of this query.</p>
 * <p>As a {@code Destination}, an {@code XQueryEvaluator} performs <b>sequence normalization</b> as defined
 * in the Serialization specification, including inserting item separators if required. The input to the
 * query (the global context item) will therefore always be a single document node. This will always be built
 * as a tree in memory, it will never be streamed.</p>
 */
public class XQueryEvaluator extends AbstractDestination implements Iterable<XdmItem> {

    private Processor processor;
    private XQueryExpression expression;
    private DynamicQueryContext context;
    private Controller controller;  // used only when making direct calls to global functions
    private Destination destination;
    private Set<XdmNode> updatedDocuments;
    /*@Nullable*/ private Builder sourceTreeBuilder;

    /**
     * Protected constructor
     *
     * @param processor  the Saxon processor
     * @param expression the XQuery expression
     */

    protected XQueryEvaluator(Processor processor, XQueryExpression expression) {
        this.processor = processor;
        this.expression = expression;
        this.context = new DynamicQueryContext(expression.getConfiguration());
    }

    /**
     * Set the schema validation mode for the transformation. This indicates how source documents
     * loaded specifically for this transformation will be handled. This applies to the
     * principal source document if supplied as a SAXSource or StreamSource, and to all
     * documents loaded during the transformation using the <code>doc()</code>, <code>document()</code>,
     * or <code>collection()</code> functions.
     *
     * @param mode the validation mode. Passing null causes no change to the existing value.
     *             Passing <code>Validation.DEFAULT</code> resets to the initial value, which determines
     *             the validation requirements from the Saxon Configuration.
     */

    public void setSchemaValidationMode(ValidationMode mode) {
        if (mode != null) {
            context.setSchemaValidationMode(mode.getNumber());
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
        return ValidationMode.get(context.getSchemaValidationMode());
    }


    /**
     * Set the source document for the query.
     * <p>If the source is an instance of {@link net.sf.saxon.om.NodeInfo}, the supplied node is used
     * directly as the context node of the query.</p>
     * <p>If the source is an instance of {@link javax.xml.transform.dom.DOMSource}, the DOM node identified
     * by the DOMSource is wrapped as a Saxon node, and this is then used as the context item</p>
     * <p>In all other cases a new Saxon tree is built, by calling
     * {@link net.sf.saxon.s9api.DocumentBuilder#build(javax.xml.transform.Source)}, and the document
     * node of this tree is then used as the context item for the query.</p>
     *
     * @param source the source document to act as the initial context item for the query.
     */

    public void setSource(Source source) throws SaxonApiException {
        if (source instanceof NodeInfo) {
            setContextItem(new XdmNode((NodeInfo) source));
        } else if (source instanceof DOMSource) {
            setContextItem(processor.newDocumentBuilder().wrap(source));
        } else {
            setContextItem(processor.newDocumentBuilder().build(source));
        }
    }

    /**
     * Set the initial context item for the query
     *
     * @param item the initial context item, or null if there is to be no initial context item
     * @throws SaxonApiException if the query declares the context item and does not define
     * it to be external
     */

    public void setContextItem(XdmItem item) throws SaxonApiException {
        if (item != null) {
            GlobalContextRequirement gcr = expression.getExecutable().getGlobalContextRequirement();
            if (gcr != null && !gcr.isExternal()) {
                throw new SaxonApiException("The context item for the query is not defined as external");
            }
            context.setContextItem(item.getUnderlyingValue());
        }
    }

    /**
     * Get the initial context item for the query, if one has been set
     *
     * @return the initial context item, or null if none has been set. This will not necessarily
     *         be the same object as was supplied, but it will be an XdmItem object that represents
     *         the same underlying node or atomic value.
     */

    public XdmItem getContextItem() {
        Item item = context.getContextItem();
        if (item == null) {
            return null;
        }
        return (XdmItem) XdmValue.wrap(item);
    }

    /**
     * Set the value of external variable defined in the query
     *
     * @param name  the name of the external variable, as a QName
     * @param value the value of the external variable, or null to clear a previously set value
     * @throws SaxonApiUncheckedException if the value is evaluated lazily, and evaluation fails
     */

    public void setExternalVariable(QName name, XdmValue value) {
        try {
            context.setParameter(name.getStructuredQName(),
                                 value == null ? null : ((Sequence) value.getUnderlyingValue()).materialize());
        } catch (XPathException e) {
            throw new SaxonApiUncheckedException(e);
        }
    }

    /**
     * Get the value that has been set for an external variable
     *
     * @param name the name of the external variable whose value is required
     * @return the value that has been set for the external variable, or null if no value has been set
     */

    public XdmValue getExternalVariable(QName name) {
        GroundedValue oval = context.getParameter(name.getStructuredQName());
        if (oval == null) {
            return null;
        }
        return XdmValue.wrap(oval);
    }

    /**
     * Set an object that will be used to resolve URIs used in
     * fn:doc() and related functions.
     *
     * @param resolver An object that implements the URIResolver interface, or
     *                 null.
     */

    public void setURIResolver(URIResolver resolver) {
        context.setURIResolver(resolver);
    }

    /**
     * Get the URI resolver.
     *
     * @return the user-supplied URI resolver if there is one, or the
     *         system-defined one otherwise
     */

    public URIResolver getURIResolver() {
        return context.getURIResolver();
    }

    /**
     * Set the error listener. The error listener receives reports of all run-time
     * errors and can decide how to report them.
     *
     * @param listener the ErrorListener to be used
     */

    public void setErrorListener(ErrorListener listener) {
        context.setErrorListener(listener);
    }

    /**
     * Get the error listener.
     *
     * @return the ErrorListener in use
     */

    public ErrorListener getErrorListener() {
        return context.getErrorListener();
    }

    /**
     * Supply a callback which will be notified of all dynamic errors and warnings
     * encountered during this query evaluation.
     * <p>Calling this method overwrites the effect of any previous call on {@link #setErrorListener(ErrorListener)}
     * or {@code setErrorList}.</p>
     * <p>If no error reporter is supplied by the caller, error information will be written to the standard error stream.</p>
     *
     * @param reporter a callback function which will be notified of all Static errors and warnings
     *                 encountered during a compilation episode.
     * @since 11.2 and retrofitted to 10.7
     */

    public void setErrorReporter(ErrorReporter reporter) {
        context.setErrorReporter(reporter);
    }

    /**
     * Get the recipient of error information previously registered using {@link #setErrorReporter(ErrorReporter)}.
     *
     * @return the recipient previously registered explicitly using {@link #setErrorReporter(ErrorReporter)},
     * or implicitly using {@link #setErrorListener(ErrorListener)}.
     * If no error reporter has been registered, the result may be null, or may return
     * a system supplied error reporter.
     * @since 11.2 and retrofitted to 10.7
     */

    public ErrorReporter getErrorReporter() {
        return context.getErrorReporter();
    }


    /**
     * Set a TraceListener which will receive messages relating to the evaluation of all expressions.
     * This option has no effect unless the query was compiled to enable tracing.
     *
     * @param listener the TraceListener to use
     */

    public void setTraceListener(TraceListener listener) {
        context.setTraceListener(listener);
    }

    /**
     * Get the registered TraceListener, if any
     *
     * @return listener the TraceListener in use, or null if none has been set
     */

    public TraceListener getTraceListener() {
        return context.getTraceListener();
    }

    /**
     * Set the destination for output from the fn:trace() function.
     * By default, the destination is System.err. If a TraceListener is in use,
     * this is ignored, and the trace() output is sent to the TraceListener.
     *
     * @param stream the PrintStream to which trace output will be sent. If set to
     *               null, trace output is suppressed entirely. It is the caller's responsibility
     *               to close the stream after use.
     * @since 9.1. Changed in 9.6 to use a Logger
     */

    public void setTraceFunctionDestination(Logger stream) {
        context.setTraceFunctionDestination(stream);
    }

    /**
     * Get the destination for output from the fn:trace() function.
     *
     * @return the PrintStream to which trace output will be sent. If no explicitly
     *         destination has been set, returns System.err. If the destination has been set
     *         to null to suppress trace output, returns null.
     * @since 9.1. Changed in 9.6 to use a Logger
     */

    public Logger getTraceFunctionDestination() {
        return context.getTraceFunctionDestination();
    }

    /**
     * Set the destination to be used for the query results
     *
     * @param destination the destination to which the results of the query will be sent
     */

    public void setDestination(Destination destination) {
        this.destination = destination;
    }

    /**
     * Perform the query.
     * <ul><li>In the case of a non-updating query, the results are sent to the
     * registered Destination.</li>
     * <li>In the case of an updating query, all updated documents will be available after query
     * execution as the result of the {@link #getUpdatedDocuments} method.</li>
     * </ul>
     *
     * @throws net.sf.saxon.s9api.SaxonApiException
     *                               if any dynamic error occurs during the query
     * @throws IllegalStateException if this is a non-updating query and no Destination has been
     *                               supplied for the query results
     */

    public void run() throws SaxonApiException {
        try {
            if (expression.isUpdateQuery()) {
                Set<MutableNodeInfo> docs = expression.runUpdate(context);
                updatedDocuments = new HashSet<>();
                for (MutableNodeInfo doc : docs) {
                    updatedDocuments.add(XdmNode.wrapItem(doc));
                }
            } else {
                if (destination == null) {
                    throw new IllegalStateException("No destination supplied");
                }
                run(destination);
//                Result receiver;
//                if (destination instanceof Serializer) {
//                    //receiver = ((Serializer) destination).getResult();
//                    //context.set
//                    receiver = ((Serializer) destination).getReceiver(expression.getExecutable());
//                } else {
//                    receiver = destination.getReceiver(expression.getConfiguration());
//                }
//                expression.run(context, receiver, null);
//                destination.close();
            }
        } catch (TransformerException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Perform the query, sending the results to a specified destination.
     * <p>This method must not be used with an updating query.</p>
     * <p>This method is designed for use with a query that produces a single node (typically
     * a document node or element node) as its result. If the query produces multiple nodes,
     * the effect depends on the kind of destination. For example, if the result is an
     * <code>XdmDestination</code>, only the last of the nodes will be accessible.</p>
     *
     * @param destination The destination where the result document will be sent
     * @throws net.sf.saxon.s9api.SaxonApiException
     *                               if any dynamic error occurs during the query
     * @throws IllegalStateException if this is an updating query
     */

    public void run(Destination destination) throws SaxonApiException {
        if (expression.isUpdateQuery()) {
            throw new IllegalStateException("Query is updating");
        }
        try {
            Receiver out = getDestinationReceiver(destination);
            expression.run(context, out, null);
            destination.closeAndNotify();
        } catch (TransformerException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Perform the query in streaming mode, sending the results to a specified destination.
     * <p>This method must not be used with an updating query.</p>
     * <p>This method is designed for use with a query that produces a single node (typically
     * a document node or element node) as its result. If the query produces multiple nodes,
     * the effect depends on the kind of destination. For example, if the result is an
     * <code>XdmDestination</code>, only the last of the nodes will be accessible.</p>
     *
     * @param source the input stream whose document node will act as the initial context item.
     * Should be a SAXSource or StreamSource
     * @param destination The destination where the result document will be sent
     * @throws net.sf.saxon.s9api.SaxonApiException
     *                               if any dynamic error occurs during the query
     * @throws IllegalStateException if this is an updating query
     */

    public void runStreamed(Source source, Destination destination) throws SaxonApiException {
        if (expression.isUpdateQuery()) {
            throw new IllegalStateException("Query is updating; cannot run with streaming");
        }
        Configuration config = context.getConfiguration();
        if (config.isTiming()) {
            String systemId = source.getSystemId();
            if (systemId == null) {
                systemId = "";
            }
            config.getLogger().info("Processing streamed input " + systemId);
        }
        try {
            SerializationProperties params = expression.getExecutable().getPrimarySerializationProperties();
            Receiver receiver = destination.getReceiver(config.makePipelineConfiguration(), params);
            expression.runStreamed(context, source, receiver, null);
        } catch (TransformerException e) {
            throw new SaxonApiException(e);
        }
    }


    /**
     * Perform the query, returning the results as an XdmValue. This method
     * must not be used with an updating query
     *
     * @return an XdmValue representing the results of the query
     * @throws SaxonApiException     if the query fails with a dynamic error
     * @throws IllegalStateException if this is an updating query
     */

    public XdmValue evaluate() throws SaxonApiException {
        if (expression.isUpdateQuery()) {
            throw new IllegalStateException("Query is updating");
        }
        try {
            SequenceIterator iter = expression.iterator(context);
            return XdmValue.wrap(iter.materialize());
        } catch (UncheckedXPathException e) {
            throw new SaxonApiException(e.getXPathException());
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Evaluate the XQuery expression, returning the result as an <code>XdmItem</code> (that is,
     * a single node or atomic value).
     *
     * @return an <code>XdmItem</code> representing the result of the query, or null if the query
     *         returns an empty sequence. If the expression returns a sequence of more than one item,
     *         any items after the first are ignored.
     * @throws SaxonApiException if a dynamic error occurs during the query evaluation.
     * @since 9.2
     */


    public XdmItem evaluateSingle() throws SaxonApiException {
        try {
            SequenceIterator iter = expression.iterator(context);
            Item next = iter.next();
            return next == null ? null : (XdmItem) XdmValue.wrap(next);
        } catch (UncheckedXPathException e) {
            throw new SaxonApiException(e.getXPathException());
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Evaluate the query, and return an iterator over its results.
     * <p>This method must not be used with an updating query.</p>
     *
     * @return an Iterator&lt;XdmItem&gt;. The XdmSequenceIterator class is a standard Java Iterator with an additional close()
     * method, which should be called to release resources if the client does not intend to read any more items
     * from the iterator.
     *
     * @throws SaxonApiUncheckedException if a dynamic error is detected while constructing the iterator.
     *                                    It is also possible for an SaxonApiUncheckedException to be thrown by the hasNext() method of the
     *                                    returned iterator if a dynamic error occurs while evaluating the result sequence.
     * @throws IllegalStateException      if this is an updating query
     * @since 9.5.1.5. Previously the method returned Iterator&lt;XdmItem&gt;; the signature changed in Saxon 9.5.1.5 for reasons
     * described in bug 2016.
     */

    @Override
    public XdmSequenceIterator<XdmItem> iterator() throws SaxonApiUncheckedException {
        if (expression.isUpdateQuery()) {
            throw new IllegalStateException("Query is updating");
        }
        try {
            return new XdmSequenceIterator<>(expression.iterator(context));
        } catch (XPathException e) {
            throw new SaxonApiUncheckedException(e);
        }
    }

    /**
     * Evaluate the query, returning the result as an <code>Stream</code>.
     *
     * @return A stream delivering the results of the query.
     * Each object in this stream will be an instance of <code>XdmItem</code>. Note
     * that the expression may be evaluated lazily, which means that a successful response
     * from this method does not imply that the expression has executed successfully: failures
     * may be reported later while retrieving items from the iterator.
     * @throws SaxonApiUncheckedException if a dynamic error occurs during XPath evaluation that
     *                                    can be detected at this point.
     */

    public XdmStream<XdmItem> stream() throws SaxonApiUncheckedException {
        return iterator().stream();
    }

    private Receiver getDestinationReceiver(Destination destination) throws SaxonApiException {
        Executable exec = expression.getExecutable();
        PipelineConfiguration pipe = expression.getConfiguration().makePipelineConfiguration();
        Receiver out = destination.getReceiver(pipe, exec.getPrimarySerializationProperties());
        if (Configuration.isAssertionsEnabled()) {
            return new RegularSequenceChecker(out, true);
        } else {
            return out;
        }
    }

    /**
     * Return a Receiver which can be used to supply the principal source document for the transformation.
     * This method is intended primarily for internal use, though it can also
     * be called by a user application that wishes to feed events into the query engine.
     * <p>Saxon calls this method to obtain a Receiver, to which it then sends
     * a sequence of events representing the content of an XML document. This method is provided so that
     * <code>XQueryEvaluator</code> implements <code>Destination</code>, allowing one transformation
     * to receive the results of another in a pipeline.</p>
     * <p>Note that when an <code>XQueryEvaluator</code> is used as a <code>Destination</code>, the initial
     * context node set on that <code>XQueryEvaluator</code> (using {@link #setSource(javax.xml.transform.Source)}) is ignored.</p>
     *
     * @param pipe The Saxon configuration. This is supplied so that the destination can
     *               use information from the configuration (for example, a reference to the name pool)
     *               to construct or configure the returned Receiver.
     * @param params the output serialization properties
     * @return the Receiver to which events are to be sent.
     * @throws SaxonApiException     if the Receiver cannot be created
     * @throws IllegalStateException if no Destination has been supplied
     */

    @Override
    public Receiver getReceiver(PipelineConfiguration pipe, SerializationProperties params) throws SaxonApiException {
        if (destination == null) {
            throw new IllegalStateException("No destination has been supplied");
        }
        try {
            if (controller == null) {
                controller = expression.newController(context);
            } else {
                context.initializeController(controller);
            }
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
        sourceTreeBuilder = controller.makeBuilder();
        if (sourceTreeBuilder instanceof TinyBuilder) {
            ((TinyBuilder) sourceTreeBuilder).setStatistics(context.getConfiguration().getTreeStatistics().SOURCE_DOCUMENT_STATISTICS);
        }
        Receiver out = controller.makeStripper(sourceTreeBuilder);
        SequenceNormalizer sn = params.makeSequenceNormalizer(out);
        sn.onClose(() -> {
            NodeInfo doc = sourceTreeBuilder.getCurrentRoot();
            if (doc == null) {
                throw new SaxonApiException("No source document has been built by the previous pipeline stage");
            }
            doc.getTreeInfo().setSpaceStrippingRule(controller.getSpaceStrippingRule());
            setSource(doc);
            sourceTreeBuilder = null;
            run(destination);
            destination.closeAndNotify();
        });
        return sn;
    }

    /**
     * Close this destination, allowing resources to be released. Used when this <code>XQueryEvaluator</code> is acting
     * as the destination of another transformation or query. Saxon calls this method when it has finished writing
     * to the destination.
     */

    @Override
    public void close() throws SaxonApiException {

    }


    /**
     * After executing an updating query using the {@link #run()} method, iterate over the root
     * nodes of the documents updated by the query.
     * <p>The results remain available until a new query is executed. This method returns the results
     * of the most recently executed query. It does not consume the results.</p>
     *
     * @return an iterator over the root nodes of documents (or other trees) that were updated by the query
     * @since 9.1
     */

    public Iterator<XdmNode> getUpdatedDocuments() {
        return updatedDocuments.iterator();
    }

    /**
     * Call a global user-defined function in the compiled query.
     * <p>If this is called more than once (to evaluate the same function repeatedly with different arguments,
     * or to evaluate different functions) then the sequence of evaluations uses the same values of global
     * variables including external variables (query parameters); the effect of any changes made to query parameters
     * between calls is undefined.</p>
     *
     * @param function  The name of the function to be called
     * @param arguments The values of the arguments to be supplied to the function. If necessary
     * these are converted to the required type by applying the function conversion rules.
     * @return the result of the function call as an XdmValue.
     * @throws SaxonApiException if no function has been defined with the given name and arity;
     *                           or if any of the arguments does not match its required type according to the function
     *                           signature; or if a dynamic error occurs in evaluating the function.
     * @since 9.3. Changed in 9.6 to apply the function conversion rules to the supplied arguments.
     */

    public XdmValue callFunction(QName function, XdmValue[] arguments) throws SaxonApiException {
        final UserFunction fn = expression.getMainModule().getUserDefinedFunction(
                function.getNamespaceURI(), function.getLocalName(), arguments.length);
        if (fn == null) {
            throw new SaxonApiException("No function with name " + function.getClarkName() +
                    " and arity " + arguments.length + " has been declared in the query");
        }
        try {
            if (controller == null) {
                controller = expression.newController(context);
            } else {
                context.initializeController(controller);
            }
            Configuration config = processor.getUnderlyingConfiguration();
            Sequence[] vr = SequenceTool.makeSequenceArray(arguments.length);
            for (int i = 0; i < arguments.length; i++) {
                net.sf.saxon.value.SequenceType type = fn.getParameterDefinitions()[i].getRequiredType();
                vr[i] = arguments[i].getUnderlyingValue();

                TypeHierarchy th = config.getTypeHierarchy();
                if (!type.matches(vr[i], th)) {
                    RoleDiagnostic role = new RoleDiagnostic(
                            RoleDiagnostic.FUNCTION, function.getStructuredQName().getDisplayName(), i);
                    vr[i] = th.applyFunctionConversionRules(vr[i], type, role, Loc.NONE);
                }
            }
            Sequence result = fn.call(vr, controller);
            return XdmValue.wrap(result);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get the underlying dynamic context object. This provides an escape hatch to the underlying
     * implementation classes, which contain methods that may change from one release to another.
     *
     * @return the underlying object representing the dynamic context for query execution
     */

    public DynamicQueryContext getUnderlyingQueryContext() {
        return context;
    }


}

