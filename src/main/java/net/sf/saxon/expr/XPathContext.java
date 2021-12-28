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
import net.sf.saxon.value.DateTimeValue;

import javax.xml.transform.URIResolver;
import java.util.Iterator;

/**
 * This class represents a context in which an XPath expression is evaluated.
 */

public interface XPathContext {


    /**
     * Construct a new context as a copy of another. The new context is effectively added
     * to the top of a stack, and contains a pointer to the previous context
     *
     * @return a new context, created as a copy of this context
     */

    XPathContextMajor newContext();

    /**
     * Construct a new context without copying (used for the context in a function call)
     *
     * @return a new clean context
     */

    XPathContextMajor newCleanContext();

    /**
     * Construct a new minor context. A minor context can only hold new values of the focus
     * (currentIterator) and current output destination.
     *
     * @return a new minor context
     */

    XPathContextMinor newMinorContext();

    /**
     * Get the local (non-tunnel) parameters that were passed to the current function or template
     *
     * @return a ParameterSet containing the local parameters
     */

    ParameterSet getLocalParameters();

    /**
     * Get the tunnel parameters that were passed to the current function or template. This includes all
     * active tunnel parameters whether the current template uses them or not.
     *
     * @return a ParameterSet containing the tunnel parameters
     */

    ParameterSet getTunnelParameters();

    /**
     * Get the Controller. May return null when running outside XSLT or XQuery
     *
     * @return the controller for this query or transformation
     */

    /*@Nullable*/
    Controller getController();

    /**
     * Get the Configuration
     *
     * @return the Saxon configuration object
     */

    Configuration getConfiguration();

    /**
     * Get the Name Pool
     *
     * @return the name pool
     */

    NamePool getNamePool();

    /**
     * Set the calling XPathContext
     *
     * @param caller the XPathContext of the calling expression
     */

    void setCaller(XPathContext caller);

    /**
     * Get the calling XPathContext (the next one down the stack). This will be null if unknown, or
     * if the bottom of the stack has been reached.
     *
     * @return the XPathContext of the calling expression
     */

    XPathContext getCaller();

    /**
     * Create, set, and return a focus tracking iterator that wraps a supplied sequence iterator.
     *
     * @param iter the current iterator. The context item, position, and size are determined by reference
     *             to the current iterator.
     */

    FocusIterator trackFocus(SequenceIterator iter);

    /**
     * Set a new sequence iterator.
     *
     * @param iter the current iterator. The context item, position, and size are determined by reference
     *             to the current iterator.
     */

    void setCurrentIterator(FocusIterator iter);

    /**
     * Get the current iterator.
     * This encapsulates the context item, context position, and context size.
     *
     * @return the current iterator, or null if there is no current iterator
     *         (which means the context item, position, and size are undefined).
     */

    FocusIterator getCurrentIterator();

    /**
     * Get the context item
     *
     * @return the context item, or null if the context item is undefined
     */

    Item getContextItem();

    /**
     * Get the context size (the position of the last item in the current node list)
     *
     * @return the context size
     * @throws XPathException if the context position is undefined
     */

    int getLast() throws XPathException;

    /**
     * Determine whether the context position is the same as the context size
     * that is, whether position()=last(). In many cases this has better performance
     * than a direct comparison, because it does not require reading to the end of the
     * sequence.
     *
     * @return true if the context position is the same as the context size.
     */

    boolean isAtLast() throws XPathException;

    /**
     * Get the URI resolver. This gets the local URIResolver set in the XPathContext if there
     * is one; if not, it gets the URIResolver from the Controller (which itself defaults to the
     * one set in the Configuration).
     *
     * @return the user-supplied URI resolver if there is one, or null otherwise.
     * @since 9.6
     */

    URIResolver getURIResolver();

    /**
     * Get the error reporter. If no ErrorReporter
     * has been set locally, the ErrorReporter in the Controller is returned; this in turn defaults
     * to the ErrorReporter set in the Configuration.
     *
     * @return the ErrorReporter in use.
     * @since 9.6. Changed in 10.0 from ErrorListener to ErrorReporter
     */

    ErrorReporter getErrorReporter();

    /**
     * Get the current component
     */

    Component getCurrentComponent();

    /**
     * Use local parameter. This is called when a local xsl:param element is processed.
     * If a parameter of the relevant name was supplied, it is bound to the xsl:param element.
     * Otherwise the method returns false, so the xsl:param default will be evaluated
     *
     *
     * @param parameterId Globally-unique parameter identifier
     * @param slotNumber  Slot number of the parameter within the stack frame of the called template
     * @param isTunnel    True if a tunnel parameter is required, else false
     * @return ParameterSet.NOT_SUPPLIED, ParameterSet.SUPPLIED, or ParameterSet.SUPPLIED_AND_CHECKED
     */

    int useLocalParameter(
            StructuredQName parameterId, int slotNumber, boolean isTunnel) throws XPathException;

    /**
     * Get a reference to the local stack frame for variables. Note that it's
     * the caller's job to make a local copy of this. This is used for creating
     * a Closure containing a retained copy of the variables for delayed evaluation.
     *
     * @return array of variables.
     */

    StackFrame getStackFrame();

    /**
     * Get the value of a local variable, identified by its slot number
     *
     * @param slotnumber the slot number allocated at compile time to the variable,
     *                   which identifies its position within the local stack frame
     * @return the value of the variable.
     */

    Sequence evaluateLocalVariable(int slotnumber);

    /**
     * Set the value of a local variable, identified by its slot number
     *  @param slotNumber the slot number allocated at compile time to the variable,
     *                   which identifies its position within the local stack frame
     * @param value      the value of the variable
     */

    void setLocalVariable(int slotNumber, Sequence value) throws XPathException;

    /**
     * Set the XSLT output state to "temporary" or "final"
     *
     * @param temporary set non-zero to set temporary output state; zero to set final output state
     *
     */

    void setTemporaryOutputState(int temporary);

    /**
     * Ask whether the XSLT output state is "temporary" or "final"
     *
     * @return non-zero if in temporary output state (integer identifies the state); zero if in final output state
     */
    int getTemporaryOutputState();

    /**
     * Set the current output URI
     * @param uri the current output URI, or null if in temporary output state
     */

    void setCurrentOutputUri(String uri);

    /**
     * Get the current output URI
     * @return the current output URI, or null if in temporary output state
     */

    String getCurrentOutputUri();

    /**
     * Get the current mode.
     *
     * @return the current mode
     */

    Component.M getCurrentMode();

    /**
     * Get the current template rule. This is used to support xsl:apply-imports and xsl:next-match
     *
     * @return the current template rule
     */

    Rule getCurrentTemplateRule();

    /**
     * Get the current group iterator. This supports the current-group() and
     * current-grouping-key() functions in XSLT 2.0
     *
     * @return the current grouped collection
     */

    GroupIterator getCurrentGroupIterator();

    /**
     * Get the current merge group iterator. This supports the current-merge-group() and
     * current-merge-key() functions in XSLT 2.0
     *
     * @return the current merge group
     */

    GroupIterator getCurrentMergeGroupIterator();

    /**
     * Get the current regex iterator. This supports the functionality of the regex-group()
     * function in XSLT 2.0.
     *
     * @return the current regular expressions iterator
     */

    RegexIterator getCurrentRegexIterator();

    /**
     * Get the current date and time
     *
     * @return the current date and time. All calls within a single query or transformation
     *         will return the same value
     */

    DateTimeValue getCurrentDateTime() throws NoDynamicContextException;

    /**
     * Get the implicit timezone
     *
     * @return the implicit timezone. This will be the timezone of the current date and time, and
     *         all calls within a single query or transformation will return the same value. The result is
     *         expressed as an offset from UTC in minutes. If the implicit timezone is unknown (which is
     *         the case when this is called on an EarlyEvaluationContext), return
     *         {@link net.sf.saxon.value.CalendarValue#NO_TIMEZONE}
     */

    int getImplicitTimezone();

    /**
     * Get the context stack. This method returns an iterator whose items are instances of
     * {@link net.sf.saxon.trace.ContextStackFrame}, starting with the top-most stackframe and
     * ending at the point the query or transformation was invoked by a calling application.
     *
     * @return an iterator over a copy of the run-time call stack
     */

    Iterator iterateStackFrames();

    /**
     * Get the current exception (in saxon:catch)
     *
     * @return the current exception, or null if there is none defined
     */

    XPathException getCurrentException();

    /**
     * Get the thread manager used to process asynchronous xsl:result-document threads.
     * @return the current thread manager; or null if multithreading is not supported
     */

    XPathContextMajor.ThreadManager getThreadManager();

    /**
     * Wait for child threads started under the control of this context to finish.
     * This is called at the end of the (main thread of a) transformation, and also
     * at the end of the "try" part of a try/catch. The threads affected are those
     * used to implement xsl:result-document instructions.
     * @throws XPathException if any of the child threads have failed with a dynamic
     * error.
     */

    void waitForChildThreads() throws XPathException;

    /**
     * Bind a component reference to a component. This is used for binding component references
     * (such as function calls, global variable references, or xsl:call-template) across package
     * boundaries. The binding is done dynamically because, in the presence of overridden components,
     * the choice among different components with the same name depends on which package the caller
     * is in.
     * @param bindingSlot Binding slots are allocated statically to the external component references
     *                    in every component: for example, in the case of a template, to all global
     *                    variable references, named function calls, and named template calls within
     *                    that template. The binding slot therefore identifies the name of the
     *                    component that is required; and the selection of an actual component is
     *                    done by selection from the binding vector of the component currently being
     *                    executed
     * @return the component to be invoked
     */

    Component getTargetComponent(int bindingSlot);

}

