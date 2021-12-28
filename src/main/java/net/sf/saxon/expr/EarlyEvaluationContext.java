////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.instruct.ParameterSet;
import net.sf.saxon.expr.sort.GroupIterator;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.om.*;
import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.value.CalendarValue;
import net.sf.saxon.value.DateTimeValue;

import javax.xml.transform.URIResolver;
import java.util.Collections;
import java.util.Iterator;

/**
 * This class is an implementation of XPathContext used when evaluating constant sub-expressions at
 * compile time.
 */

public class EarlyEvaluationContext implements XPathContext {

    private Configuration config;

    /**
     * Create an early evaluation context, used for evaluating constant expressions at compile time
     *
     * @param config the Saxon configuration
     * //@param map    the available collations
     */

    public EarlyEvaluationContext(Configuration config) {
        this.config = config;
    }

    /**
     * Get the value of a local variable, identified by its slot number
     */

    /*@Nullable*/
    @Override
    public Sequence evaluateLocalVariable(int slotnumber) {
        notAllowed();
        return null;
    }

    /**
     * Get the calling XPathContext (the next one down the stack). This will be null if unknown, or
     * if the bottom of the stack has been reached.
     */

    @Override
    public XPathContext getCaller() {
        return null;
    }

    /**
     * Get the URI resolver. This gets the local URIResolver set in the XPathContext if there
     * is one; if not, it gets the URIResolver from the Controller (which itself defaults to the
     * one set in the Configuration).
     *
     * @return the user-supplied URI resolver if there is one, or null otherwise.
     * @since 9.6
     */
    @Override
    public URIResolver getURIResolver() {
        return config.getURIResolver();
    }

    /**
     * Get the error listener. If no ErrorListener
     * has been set locally, the ErrorListener in the Controller is returned; this in turn defaults
     * to the ErrorListener set in the Configuration.
     *
     * @return the ErrorListener in use. This will always be an UnfailingErrorListener,
     *         which is a Saxon subclass of ErrorListener that throws no exceptions.
     * @since 9.6
     */
    @Override
    public ErrorReporter getErrorReporter() {
        return config.makeErrorReporter();
    }

    /**
     * Get the current component
     */
    @Override
    public Component getCurrentComponent() {
        notAllowed();
        return null;
    }

    /**
     * Get the Configuration
     */

    @Override
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the context item
     *
     * @return the context item, or null if the context item is undefined
     */

    @Override
    public Item getContextItem() {
        return null;
    }

    /**
     * Get the Controller. May return null when running outside XSLT or XQuery
     */

    @Override
    public Controller getController() {
        return null;
    }

    /**
     * Get the current group iterator. This supports the current-group() and
     * current-grouping-key() functions in XSLT 2.0
     *
     * @return the current grouped collection
     */

    @Override
    public GroupIterator getCurrentGroupIterator() {
        notAllowed();
        return null;
    }

    /**
     * Get the current merge group iterator. This supports the current-merge-group() and
     * current-merge-key() functions in XSLT 3.0
     *
     * @return the current grouped collection
     */

    @Override
    public GroupIterator getCurrentMergeGroupIterator() {
        notAllowed();
        return null;
    }


    /**
     * Get the current iterator.
     * This encapsulates the context item, context position, and context size.
     *
     * @return the current iterator, or null if there is no current iterator
     *         (which means the context item, position, and size are undefined).
     */

    @Override
    public FocusTrackingIterator getCurrentIterator() {
        return null;
    }

    /**
     * Get the current mode.
     *
     * @return the current mode
     */

    @Override
    public Component.M getCurrentMode() {
        notAllowed();
        return null;
    }

    /**
     * Get the current regex iterator. This supports the functionality of the regex-group()
     * function in XSLT 2.0.
     *
     * @return the current regular expressions iterator
     */

    @Override
    public RegexIterator getCurrentRegexIterator() {
        return null;
    }

    /**
     * Get the current template. This is used to support xsl:apply-imports
     *
     * @return the current template
     */

    @Override
    public Rule getCurrentTemplateRule() {
        return null;
    }

    /**
     * Get the context size (the position of the last item in the current node list)
     *
     * @return the context size
     * @throws net.sf.saxon.trans.XPathException
     *          if the context position is undefined
     */

    @Override
    public int getLast() throws XPathException {
        XPathException err = new XPathException("The context item is absent");
        err.setErrorCode("XPDY0002");
        throw err;
    }

    /**
     * Get the local (non-tunnel) parameters that were passed to the current function or template
     *
     * @return a ParameterSet containing the local parameters
     */

    @Override
    public ParameterSet getLocalParameters() {
        notAllowed();
        return null;
    }

    /**
     * Get the Name Pool
     */

    @Override
    public NamePool getNamePool() {
        return config.getNamePool();
    }

    /**
     * Get a reference to the local stack frame for variables. Note that it's
     * the caller's job to make a local copy of this. This is used for creating
     * a Closure containing a retained copy of the variables for delayed evaluation.
     *
     * @return array of variables.
     */

    @Override
    public StackFrame getStackFrame() {
        notAllowed();
        return null;
    }

    /**
     * Get the tunnel parameters that were passed to the current function or template. This includes all
     * active tunnel parameters whether the current template uses them or not.
     *
     * @return a ParameterSet containing the tunnel parameters
     */

    @Override
    public ParameterSet getTunnelParameters() {
        notAllowed();
        return null;
    }

    /**
     * Determine whether the context position is the same as the context size
     * that is, whether position()=last()
     */

    @Override
    public boolean isAtLast() throws XPathException {
        XPathException err = new XPathException("The context item is absent");
        err.setErrorCode("XPDY0002");
        throw err;
    }

    /**
     * Construct a new context without copying (used for the context in a function call)
     */

    @Override
    public XPathContextMajor newCleanContext() {
        notAllowed();
        return null;
    }

    /**
     * Construct a new context as a copy of another. The new context is effectively added
     * to the top of a stack, and contains a pointer to the previous context
     */

    @Override
    public XPathContextMajor newContext() {
        Controller controller = new Controller(config);
        return controller.newXPathContext();
    }

    /**
     * Construct a new minor context. A minor context can only hold new values of the focus
     * (currentIterator) and current output destination.
     */

    @Override
    public XPathContextMinor newMinorContext() {
        return newContext().newMinorContext();
    }

    /**
     * Set the calling XPathContext
     */

    @Override
    public void setCaller(XPathContext caller) {
        // no-op
    }

    /**
     * Set a new sequence iterator.
     */

    @Override
    public void setCurrentIterator(FocusIterator iter) {
        notAllowed();
    }

    @Override
    public FocusIterator trackFocus(SequenceIterator iter) {
        notAllowed();
        return null;
    }

    /**
     * Set the value of a local variable, identified by its slot number
     */

    @Override
    public void setLocalVariable(int slotNumber, Sequence value) {
        notAllowed();
    }

    /**
     * Use local parameter. This is called when a local xsl:param element is processed.
     * If a parameter of the relevant name was supplied, it is bound to the xsl:param element.
     * Otherwise the method returns false, so the xsl:param default will be evaluated
     *
     * @param parameterId Globally-unique parameter identifier
     * @param slotNumber  Slot number of the parameter within the stack frame of the called template
     * @param isTunnel    True if a tunnel parameter is required, else false  @return true if a parameter of this name was supplied, false if not
     */

    @Override
    public int useLocalParameter(StructuredQName parameterId, int slotNumber, boolean isTunnel) {
        return ParameterSet.NOT_SUPPLIED;
    }

    /**
     * Get the current date and time. This implementation always throws a
     * NoDynamicContextException.
     *
     * @return the current date and time. All calls within a single query or transformation
     *         will return the same value
     */

    @Override
    public DateTimeValue getCurrentDateTime() throws NoDynamicContextException {
        throw new NoDynamicContextException("current-dateTime");
    }

    /**
     * Get the implicit timezone, as a positive or negative offset from UTC in minutes.
     * The range is -14hours to +14hours. This implementation returns {@link CalendarValue#NO_TIMEZONE},
     * meaning the value is unknown.
     *
     * @return the implicit timezone, as an offset from UTC in minutes
     */

    @Override
    public int getImplicitTimezone() {
        return CalendarValue.MISSING_TIMEZONE;
    }


    /**
     * Get the context stack. This method returns an iterator whose items are instances of
     * {@link net.sf.saxon.trace.ContextStackFrame}, starting with the top-most stackframe and
     * ending at the point the query or transformation was invoked by a calling application.
     *
     * @return an iterator over a copy of the run-time call stack
     */

    @Override
    public Iterator iterateStackFrames() {
        return Collections.EMPTY_LIST.iterator();
    }

    /**
     * Get the current exception (in saxon:catch)
     *
     * @return the current exception, or null if there is none defined
     */

    @Override
    public XPathException getCurrentException() {
        return null;
    }

    @Override
    public void waitForChildThreads() throws XPathException {
        getCaller().waitForChildThreads();
    }

    @Override
    public void setTemporaryOutputState(int temporary) {
        // no action
    }

    /**
     * Ask whether the XSLT output state is "temporary" or "final"
     *
     * @return non-zero in temporary output state; zero in final output state
     */
    @Override
    public int getTemporaryOutputState() {
        return 0;
    }


    /**
     * Set the current output URI
     * @param uri the current output URI, or null if in temporary output state
     */

    @Override
    public void setCurrentOutputUri(String uri) {
        // no action
    }

    /**
     * Get the current output URI
     * @return the current output URI, or null if in temporary output state
     */

    @Override
    public String getCurrentOutputUri() {
        return null;
    }

    /**
     * Throw an error for operations that aren't supported when doing early evaluation of constant
     * subexpressions
     */

    private void notAllowed() {
        throw new UnsupportedOperationException(
                new NoDynamicContextException("Internal error: early evaluation of subexpression with no context"));
    }

    /**
     * Get the thread manager used to process asynchronous xsl:result-document threads.
     *
     * @return the current thread manager; or null if multithreading is not supported
     */
    @Override
    public XPathContextMajor.ThreadManager getThreadManager() {
        return null;
    }

    @Override
    public Component getTargetComponent(int bindingSlot) {
        return null;
    }
}
