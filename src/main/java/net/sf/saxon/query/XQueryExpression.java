////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.ExpressionOwner;
import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.GlobalContextRequirement;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.TraceableComponent;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingException;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.util.*;

/**
 * XQueryExpression represents a compiled query. This object is immutable and thread-safe,
 * the same compiled query may be executed many times in series or in parallel. The object
 * is intended to be created only by using the compileQuery method of the QueryProcessor class.
 * <p>Various methods are provided for evaluating the query, with different options for
 * delivery of the results.</p>
 */
public class XQueryExpression implements Location, ExpressionOwner, TraceableComponent {

    protected Expression expression;
    protected SlotManager stackFrameMap;
    protected Executable executable;
    protected QueryModule mainModule;


    /**
     * The constructor should only be
     * called using the compileQuery() methods of StaticQueryContext
     * @param exp        an expression to be wrapped as an XQueryExpression
     * @param mainModule the static context of the main module
     * @param streaming  true if streamed execution is requested
     * @throws XPathException if an error occurs
     */

    public XQueryExpression(Expression exp,  QueryModule mainModule, boolean streaming)
            throws XPathException {
        Executable exec = mainModule.getExecutable();
        Configuration config = mainModule.getConfiguration();
        stackFrameMap = config.makeSlotManager();
        executable = exec;
        this.mainModule = mainModule;
        exp.setRetainedStaticContext(mainModule.makeRetainedStaticContext());
        try {
            ExpressionVisitor visitor = ExpressionVisitor.make(mainModule);
            Optimizer optimizer = visitor.obtainOptimizer();
            visitor.setOptimizeForStreaming(streaming);
            exp = exp.simplify();
            exp.checkForUpdatingSubexpressions();
            GlobalContextRequirement contextReq = exec.getGlobalContextRequirement();
            ItemType req = contextReq == null ? AnyItemType.getInstance() : contextReq.getRequiredItemType();
            ContextItemStaticInfo cit = config.makeContextItemStaticInfo(req, true);
            Expression e2 = exp.typeCheck(visitor, cit);
            if (e2 != exp) {
                e2.setRetainedStaticContext(exp.getRetainedStaticContext());
                e2.setParentExpression(null);
                exp = e2;
            }
            if (optimizer.isOptionSet(OptimizerOptions.MISCELLANEOUS)) {
                e2 = exp.optimize(visitor, cit);
                if (e2 != exp) {
                    e2.setRetainedStaticContext(exp.getRetainedStaticContext());
                    e2.setParentExpression(null);
                    exp = e2;
                }
            }
            if (optimizer.isOptionSet(OptimizerOptions.LOOP_LIFTING)) {
                e2 = LoopLifter.process(exp, visitor, cit);
                if (e2 != exp) {
                    e2.setRetainedStaticContext(exp.getRetainedStaticContext());
                    e2.setParentExpression(null);
                    exp = e2;
                }
            }
        } catch (XPathException err) {
            //err.printStackTrace();
            mainModule.reportStaticError(err);
            throw err;
        }
        ExpressionTool.allocateSlots(exp, 0, stackFrameMap);
        ExpressionTool.computeEvaluationModesForUserFunctionCalls(exp);
        for (GlobalVariable var : getPackageData().getGlobalVariableList()) {
            Expression top = var.getBody();
            if (top != null) {
                ExpressionTool.computeEvaluationModesForUserFunctionCalls(top);
            }
        }

        expression = exp;
        executable.setConfiguration(config);
    }

    /**
     * Get the expression wrapped in this XQueryExpression object
     *
     * @return the underlying expression
     */

    public Expression getExpression() {
        return expression;
    }

    @Override
    public Expression getBody() {
        return getExpression();
    }

    @Override
    public Expression getChildExpression() {
        return expression;
    }

    @Override
    public void setBody(Expression expression) {
        setChildExpression(expression);
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     *
     * @return the QName of the object declared or manipulated by this instruction or expression
     */
    @Override
    public StructuredQName getObjectName() {
        return null;
    }

    @Override
    public String getTracingTag() {
        return "query";
    }

    @Override
    public Location getLocation() {
        return this;
    }

    /**
     * Get data about the unit of compilation (XQuery module, XSLT package) to which this
     * container belongs
     */
    public PackageData getPackageData() {
        return mainModule.getPackageData();
    }

    /**
     * Get the Configuration to which this Container belongs
     *
     * @return the Configuration
     */
    public Configuration getConfiguration() {
        return mainModule.getConfiguration();
    }

    /**
     * Ask whether this query uses the context item
     *
     * @return true if the context item is referenced either in the query body or in the initializer
     *         of any global variable
     */

    public boolean usesContextItem() {
        if (ExpressionTool.dependsOnFocus(expression)) {
            return true;
        }
        List<GlobalVariable> map = getPackageData().getGlobalVariableList();
        if (map != null) {
            for (GlobalVariable var : map) {
                Expression select = var.getBody();
                if (select != null && ExpressionTool.dependsOnFocus(select)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Ask whether this is an update query
     *
     * @return true if the body of the query is an updating expression
     *         (as defined by the XQuery Update specification). Note that a query can use Update syntax
     *         (notably the copy-modify syntax) without being an updating expression.
     */

    public boolean isUpdateQuery() {
        return false;
    }

    /**
     * Get the stack frame map used for the outermost level of this query
     *
     * @return the stack frame map
     */

    public SlotManager getStackFrameMap() {
        return stackFrameMap;
    }

    /**
     * Output the path map of the query for diagnostics
     */

    public void explainPathMap() {
        // No action (requires Saxon-EE)
    }

    /**
     * Get the static context in which this expression was compiled. This is essentially an internal
     * copy of the original user-created StaticQueryContext object, augmented with information obtained
     * from the query prolog of the main query module, and with information about functions and variables
     * imported from other library modules. The user-created StaticQueryContext object is not modified
     * by Saxon, whereas the QueryModule object includes additional information found in the query prolog.
     *
     * @return the QueryModule object representing the static context of the main module of the query.
     *         This is available for inspection, but must not be modified or reused by the application.
     */
    public QueryModule getMainModule() {
        return mainModule;
    }

    /**
     * Get a list containing the names of the external variables in the query.
     * <p><i>Changed in Saxon 9.0 to return an array of StructuredQName objects rather than
     * integer fingerprints.</i></p>
     *
     * @return an array of StructuredQName objects, representing the names of external variables defined
     *         in the query
     */

    /*@NotNull*/
    public StructuredQName[] getExternalVariableNames() {
        List list = stackFrameMap.getVariableMap();
        StructuredQName[] names = new StructuredQName[stackFrameMap.getNumberOfVariables()];
        for (int i = 0; i < names.length; i++) {
            names[i] = (StructuredQName) list.get(i);
        }
        return names;
    }

    /**
     * Execute a the compiled Query, returning the results as a List of objects
     * obtained by converting the items in the query result to the nearest appropriate
     * Java type
     *
     * @param env Provides the dynamic query evaluation context
     * @return The results of the expression, as a List. The List represents the sequence
     *         of items returned by the expression. Each item in the list will either be an
     *         object representing a node, or an object representing an atomic value.
     *         For the types of Java object that may be returned, see the description of the
     *         {@link net.sf.saxon.xpath.XPathEvaluator#evaluate} method
     *         of class XPathProcessor
     * @throws XPathException if a dynamic error occurs during query evaluation
     */

    /*@NotNull*/
    public List<Object> evaluate(/*@NotNull*/ DynamicQueryContext env) throws XPathException {
        if (isUpdateQuery()) {
            throw new XPathException("Cannot call evaluate() on an updating query");
        }
        ArrayList<Object> list = new ArrayList<>(100);
        iterator(env).forEachOrFail(item -> list.add(SequenceTool.convertToJava(item)));
        return list;
    }

    /**
     * Execute the compiled Query, returning the first item in the result.
     * This is useful where it is known that the expression will only return
     * a singleton value (for example, a single node, or a boolean).
     *
     * @param env Provides the dynamic query evaluation context
     * @return The first item in the sequence returned by the expression. If the expression
     *         returns an empty sequence, this method returns null. Otherwise, it returns the first
     *         item in the result sequence, represented as a Java object using the same mapping as for
     *         the {@link XQueryExpression#evaluate} method
     * @throws XPathException if evaluation fails with a dynamic error
     */

    /*@Nullable*/
    public Object evaluateSingle(/*@NotNull*/ DynamicQueryContext env) throws XPathException {
        if (isUpdateQuery()) {
            throw new XPathException("Cannot call evaluateSingle() on an updating query");
        }
        SequenceIterator iterator = iterator(env);
        Item item = iterator.next();
        if (item == null) {
            return null;
        }
        return SequenceTool.convertToJava(item);
    }

    /**
     * Get an iterator over the results of the expression. This returns results without
     * any conversion of the returned items to "native" Java classes. The iterator will
     * deliver a sequence of Item objects, each item being either a NodeInfo (representing
     * a node) or an AtomicValue (representing an atomic value).
     * <p>To get the results of the query in the form of an XML document in which each
     * item is wrapped by an element indicating its type, use:</p>
     * <p><code>QueryResult.wrap(iterator(env))</code></p>
     * <p>To serialize the results to a file, use the QueryResult.serialize() method.</p>
     *
     * @param env Provides the dynamic query evaluation context
     * @return an iterator over the results of the query. The class SequenceIterator
     *         is modeled on the standard Java Iterator class, but has extra functionality
     *         and can throw exceptions when errors occur.
     * @throws XPathException if a dynamic error occurs in evaluating the query. Some
     *                        dynamic errors will not be reported by this method, but will only be reported
     *                        when the individual items of the result are accessed using the returned iterator.
     */

    /*@NotNull*/
    public SequenceIterator iterator(/*@NotNull*/ DynamicQueryContext env) throws XPathException {
        if (isUpdateQuery()) {
            throw new XPathException("Cannot call iterator() on an updating query");
        }
        if (!env.getConfiguration().isCompatible(getExecutable().getConfiguration())) {
            throw new XPathException("The query must be compiled and executed under the same Configuration", SaxonErrorCode.SXXP0004);
        }
        Controller controller = newController(env);

        try {
            Item contextItem = controller.getGlobalContextItem();
            if (contextItem instanceof NodeInfo && ((NodeInfo)contextItem).getTreeInfo().isTyped() && !getExecutable().isSchemaAware()) {
                throw new XPathException("A typed input document can only be used with a schema-aware query");
            }

            XPathContextMajor context = initialContext(env, controller);

            // In tracing/debugging mode, evaluate all the global variables first
            if (controller.getTraceListener() != null) {
                controller.preEvaluateGlobals(context);
            }

            context.openStackFrame(stackFrameMap);

            SequenceIterator iterator = expression.iterate(context);
            if (iterator.getProperties().contains(SequenceIterator.Property.GROUNDED)) {
                return iterator;
            } else {
                return new ErrorReportingIterator(iterator, controller.getErrorReporter());
            }
        } catch (XPathException err) {
            TransformerException terr = err;
            while (terr.getException() instanceof TransformerException) {
                terr = (TransformerException) terr.getException();
            }
            XPathException de = XPathException.makeXPathException(terr);
            controller.reportFatalError(de);
            throw de;
        }
    }

    /**
     * Run the query, sending the results directly to a JAXP Result object. This way of executing
     * the query is most efficient in the case of queries that produce a single document (or parentless
     * element) as their output, because it avoids constructing the result tree in memory: instead,
     * it is piped straight to the serializer.
     *
     * <p>If the output method specified in the outputProperties, or declared in the query itself, is
     * one of XML, HTML, XHTML, or TEXT, or if it is defaulted to XML, then the query is implicitly
     * wrapped in a document node constructor to implement the "sequence normalization" phase described
     * in the Serialization specification. If the output method is JSON or ADAPTIVE then this phase
     * is skipped and the items produced by the query are piped straight into the serializer.</p>
     *
     * @param env              the dynamic query context
     * @param result           the destination for the results of the query. The query is effectively wrapped
     *                         in a document{} constructor, so that the items in the result are concatenated to form a single
     *                         document; this is then written to the requested Result destination, which may be (for example)
     *                         a DOMResult, a SAXResult, or a StreamResult
     * @param outputProperties Supplies serialization properties, in JAXP format, if the result is to
     *                         be serialized. This parameter can be defaulted to null.
     * @throws XPathException if the query fails.
     */

    public void run(/*@NotNull*/ DynamicQueryContext env, Result result, Properties outputProperties) throws XPathException {
        if (isUpdateQuery()) {
            throw new XPathException("Cannot call run() on an updating query");
        }
        if (!env.getConfiguration().isCompatible(getExecutable().getConfiguration())) {
            throw new XPathException("The query must be compiled and executed under the same Configuration", SaxonErrorCode.SXXP0004);
        }
        Item contextItem = env.getContextItem();
        if (contextItem instanceof NodeInfo && ((NodeInfo) contextItem).getTreeInfo().isTyped() && !getExecutable().isSchemaAware()) {
            throw new XPathException("A typed input document can only be used with a schema-aware query");
        }
        Controller controller = newController(env);
        if (result instanceof Receiver) {
            ((Receiver)result).getPipelineConfiguration().setController(controller);
        }

        Properties actualProperties = validateOutputProperties(controller, outputProperties);

        XPathContextMajor context = initialContext(env, controller);

        // In tracing/debugging mode, evaluate all the global variables first
        TraceListener tracer = controller.getTraceListener();
        if (tracer != null) {
            controller.preEvaluateGlobals(context);
        }

        context.openStackFrame(stackFrameMap);

        boolean mustClose = result instanceof StreamResult && ((StreamResult) result).getOutputStream() == null;
        Receiver out;
        if (result instanceof Receiver) {
            out = (Receiver) result;
        } else {
            SerializerFactory sf = context.getConfiguration().getSerializerFactory();
            PipelineConfiguration pipe = controller.makePipelineConfiguration();
            pipe.setHostLanguage(HostLanguage.XQUERY);
            out = sf.getReceiver(result, new SerializationProperties(actualProperties), pipe);
        }

        ComplexContentOutputter dest = new ComplexContentOutputter(out);
        dest.open();

        // Run the query
        try {
            expression.process(dest, context);
        } catch (XPathException err) {
            controller.reportFatalError(err);
            throw err;
        } finally {
            try {
                if (tracer != null) {
                    tracer.close();
                }
                dest.close();
            } catch (XPathException e) {
                e.printStackTrace();
            }
        }
        if (result instanceof StreamResult) {
            closeStreamIfNecessary((StreamResult) result, mustClose);
        }
    }

    protected void closeStreamIfNecessary(StreamResult result, boolean mustClose) throws XPathException {
        if (mustClose) {
            OutputStream os = result.getOutputStream();
            if (os != null) {
                try {
                    os.close();
                } catch (java.io.IOException err) {
                    throw new XPathException(err);
                }
            }
        }
    }

    /**
     * Run the query in streaming mode, assuming it has been compiled for streaming.
     * This requires Saxon-EE.
     * @param dynamicEnv the dynamic execution context
     * @param source the input document, as a SAXSource or StreamSource
     * @param result the destination for the query results
     * @param outputProperties serialization options for the query result
     * @throws XPathException if streamed evaluation of the query fails
     */

    public void runStreamed(DynamicQueryContext dynamicEnv, Source source, Result result, Properties outputProperties) throws XPathException {
        throw new XPathException("Streaming requires Saxon-EE");
    }

    /**
     * Validate the serialization properties requested; any problems result in warnings being output
     * @param controller The query controller
     * @param outputProperties the requested serialization properties (may be null)
     * @return the properties after validation and normalization
     */

    protected Properties validateOutputProperties(Controller controller, /*@Nullable*/ Properties outputProperties) {
        // Validate the serialization properties requested

        Properties baseProperties = controller.getExecutable().getPrimarySerializationProperties().getProperties();
        SerializerFactory sf = controller.getConfiguration().getSerializerFactory();
        if (outputProperties != null) {
            Enumeration iter = outputProperties.propertyNames();
            while (iter.hasMoreElements()) {
                String key = (String) iter.nextElement();
                String value = outputProperties.getProperty(key);
                try {
                    value = sf.checkOutputProperty(key, value);
                    baseProperties.setProperty(key, value);
                } catch (XPathException dynamicError) {
                    outputProperties.remove(key);
                    XmlProcessingException err = new XmlProcessingException(dynamicError);
                    err.setWarning(true);
                    controller.getErrorReporter().report(err);
                }
            }
        }
        if (baseProperties.getProperty("method") == null) {
            // XQuery forces the default method to XML, unlike XSLT where it depends on the contents of the result tree
            baseProperties.setProperty("method", "xml");
        }
        return baseProperties;
    }

    /**
     * Run an updating query
     *
     * @param dynamicEnv the dynamic context for query execution
     * @return a set of nodes representing the roots of trees that have been modified as a result
     *         of executing the update. Note that this method will never modify persistent data on disk; it returns
     *         the root nodes of the affected documents (which will often be document nodes whose document-uri can
     *         be ascertained), and it is the caller's responsibility to decide what to do with them.
     *         <p>On completion of this method it is generally unsafe to rely on the contents or relationships
     *         of NodeInfo objects that were obtained before the updating query was run. Such objects may or may not
     *         reflect the results of the update operations. This is especially true in the case of nodes that
     *         are part of a subtree that has been deleted (detached from its parent). Instead, the new updated
     *         tree should be accessed by navigation from the root nodes returned by this method.</p>
     * @throws XPathException if evaluation of the update query fails, or it this is not an updating query
     */

    /*@NotNull*/
    public Set<MutableNodeInfo> runUpdate(/*@NotNull*/ DynamicQueryContext dynamicEnv) throws XPathException {
        throw new XPathException("Calling runUpdate() on a non-updating query");
    }

    /**
     * Run an updating query, writing back eligible updated node to persistent storage.
     * <p>A node is eligible to be written back to disk if it is present in the document pool,
     * which generally means that it was originally read using the doc() or collection() function.</p>
     * <p>On completion of this method it is generally unsafe to rely on the contents or relationships
     * of NodeInfo objects that were obtained before the updating query was run. Such objects may or may not
     * reflect the results of the update operations. This is especially true in the case of nodes that
     * are part of a subtree that has been deleted (detached from its parent). Instead, the new updated
     * tree should be accessed by navigation from the root nodes returned by this method.</p>
     * <p>If one or more eligible updated nodes cannot be written back to disk, perhaps because the URI
     * identifies a non-updatable location, then an exception is thrown. In this case it is undefined</p>
     *
     * @param dynamicEnv the dynamic context for query execution
     * @param agent      a callback class that is called to process each document updated by the query
     * @throws XPathException if evaluation of the update query fails, or it this is not an updating query
     */

    public void runUpdate(/*@NotNull*/ DynamicQueryContext dynamicEnv, /*@NotNull*/ UpdateAgent agent) throws XPathException {
        throw new XPathException("Calling runUpdate() on a non-updating query");
    }


    protected XPathContextMajor initialContext(DynamicQueryContext dynamicEnv, Controller controller) throws XPathException {

        Item contextItem = controller.getGlobalContextItem();
        XPathContextMajor context = controller.newXPathContext();

        if (contextItem != null) {
            ManualIterator single = new ManualIterator(contextItem);
            context.setCurrentIterator(single);
            controller.setGlobalContextItem(contextItem);
        }
        return context;
    }

    /**
     * Get a controller that can be used to execute functions in this compiled query.
     * Functions in the query module can be found using {@link QueryModule#getUserDefinedFunction}.
     * They can then be called directly from the Java application using {@link net.sf.saxon.expr.instruct.UserFunction#call}
     * The same Controller can be used for a series of function calls. Note that the Controller should only be used
     * in a single thread.
     * @param env the dynamic context for evaluation
     * @return a newly constructed Controller
     * @throws XPathException if evaluation fails with a dynamic error
     */

    /*@NotNull*/
    public Controller newController(DynamicQueryContext env) throws XPathException {
        Controller controller = new Controller(executable.getConfiguration(), executable);
        env.initializeController(controller);
        //controller.getBindery(getPackageData()).allocateGlobals(executable.getGlobalVariableMap());
        return controller;
    }

    /**
     * Diagnostic method: display a representation of the compiled query on the
     * selected output stream.
     *
     * @param out an ExpressionPresenter to which the XML representation of the compiled query
     *            will be sent
     */

    public void explain(/*@NotNull*/ ExpressionPresenter out) throws XPathException {
        out.startElement("query");
        mainModule.getKeyManager().exportKeys(out, null);
        getExecutable().explainGlobalVariables(out);
        mainModule.explainGlobalFunctions(out);
        out.startElement("body");
        expression.export(out);
        out.endElement();
        out.endElement();
        out.close();
    }

    /**
     * Get the Executable (representing a complete stylesheet or query) of which this Container forms part
     * @return the Executable
     */

    public Executable getExecutable() {
        return executable;
    }

    /**
     * Indicate that document projection is or is not allowed
     *
     * @param allowed true if projection is allowed
     */

    public void setAllowDocumentProjection(boolean allowed) {
        if (allowed) {
            throw new UnsupportedOperationException("Document projection requires Saxon-EE");
        }
    }

    /**
     * Ask whether document projection is allowed
     *
     * @return true if document projection is allowed
     */

    public boolean isDocumentProjectionAllowed() {
        return false;
    }

    /**
     * Return the public identifier for the current document event.
     * <p>The return value is the public identifier of the document
     * entity or of the external parsed entity in which the markup that
     * triggered the event appears.</p>
     *
     * @return A string containing the public identifier, or
     *         null if none is available.
     * @see #getSystemId
     */
    /*@Nullable*/
    @Override
    public String getPublicId() {
        return null;
    }

    /**
     * Return the system identifier for the current document event.
     * <p>The return value is the system identifier of the document
     * entity or of the external parsed entity in which the markup that
     * triggered the event appears.</p>
     * <p>If the system identifier is a URL, the parser must resolve it
     * fully before passing it to the application.</p>
     *
     * @return A string containing the system identifier, or null
     *         if none is available.
     * @see #getPublicId
     */
    /*@Nullable*/
    @Override
    public String getSystemId() {
        return mainModule.getSystemId();
    }

    /**
     * Return the line number where the current document event ends.
     * <p><strong>Warning:</strong> The return value from the method
     * is intended only as an approximation for the sake of error
     * reporting; it is not intended to provide sufficient information
     * to edit the character content of the original XML document.</p>
     * <p>The return value is an approximation of the line number
     * in the document entity or external parsed entity where the
     * markup that triggered the event appears.</p>
     *
     * @return The line number, or -1 if none is available.
     * @see #getColumnNumber
     */
    @Override
    public int getLineNumber() {
        return -1;
    }

    /**
     * Return the character position where the current document event ends.
     * <p><strong>Warning:</strong> The return value from the method
     * is intended only as an approximation for the sake of error
     * reporting; it is not intended to provide sufficient information
     * to edit the character content of the original XML document.</p>
     * <p>The return value is an approximation of the column number
     * in the document entity or external parsed entity where the
     * markup that triggered the event appears.</p>
     *
     * @return The column number, or -1 if none is available.
     * @see #getLineNumber
     */
    @Override
    public int getColumnNumber() {
        return -1;
    }

    /**
     * Get an immutable copy of this Location object. By default Location objects may be mutable, so they
     * should not be saved for later use. The result of this operation holds the same location information,
     * but in an immutable form.
     */
    @Override
    public Location saveLocation() {
        return this;
    }

    /**
     * Get the host language (XSLT, XQuery, XPath) used to implement the code in this container
     *
     * @return {@link HostLanguage#XQUERY}
     */

    public HostLanguage getHostLanguage() {
        return HostLanguage.XQUERY;
    }

    @Override
    public void setChildExpression(Expression expr) {
            expression = expr;
    }

    /**
     * ErrorReportingIterator is an iterator that wraps a base iterator and reports
     * any exceptions that are raised to the ErrorListener
     */

    private class ErrorReportingIterator implements SequenceIterator {
        private SequenceIterator base;
        private ErrorReporter reporter;

        public ErrorReportingIterator(SequenceIterator base, ErrorReporter reporter) {
            this.base = base;
            this.reporter = reporter;
        }

        /*@Nullable*/
        @Override
        public Item next() throws XPathException {
            try {
                return base.next();
            } catch (XPathException e1) {
                e1.maybeSetLocation(expression.getLocation());
                XmlProcessingException err = new XmlProcessingException(e1);
                reporter.report(err);
                e1.setHasBeenReported(true);
                throw e1;
            }
        }

        @Override
        public void close() {
            base.close();
        }

    }

}

