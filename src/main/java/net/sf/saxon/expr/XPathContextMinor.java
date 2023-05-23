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
import net.sf.saxon.trace.ContextStackIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.value.DateTimeValue;

import javax.xml.transform.URIResolver;
import java.util.Iterator;
import java.util.function.Function;

/**
 * This class represents a minor change in the dynamic context in which an XPath expression is evaluated:
 * a "major context" object allows all aspects of the dynamic context to change, whereas
 * a "minor context" only allows changes to the focus and the destination for push output.
 */

public class XPathContextMinor implements XPathContext {

    Controller controller;
    FocusIterator currentIterator;
    /*@Nullable*/ LastValue last = null;

    XPathContext caller = null;
    protected StackFrame stackFrame;
    protected String currentDestination = "";
    protected int temporaryOutputState = 0;


    /**
     * Private Constructor
     */

    protected XPathContextMinor() {
    }

    /**
     * Construct a new context as a copy of another. The new context is effectively added
     * to the top of a stack, and contains a pointer to the previous context
     */

    @Override
    public XPathContextMajor newContext() {
        return XPathContextMajor.newContext(this);
    }

    /**
     * Construct a new context as a copy of another. The new context is effectively added
     * to the top of a stack, and contains a pointer to the previous context
     */

    @Override
    public XPathContextMinor newMinorContext() {
        XPathContextMinor c = new XPathContextMinor();
        //System.err.println("NEW MINOR CONTEXT " + c);
        c.controller = controller;
        c.caller = this;
        c.currentIterator = currentIterator;
        c.last = last;
        c.stackFrame = stackFrame;
        c.currentDestination = currentDestination;
        c.temporaryOutputState = temporaryOutputState;
        return c;
    }

    /**
     * Set the calling XPathContext
     */

    @Override
    public void setCaller(XPathContext caller) {
        this.caller = caller;
    }

    /**
     * Construct a new context without copying (used for the context in a function call)
     */

    @Override
    public XPathContextMajor newCleanContext() {
        XPathContextMajor c = new XPathContextMajor(getController());
        c.setCaller(this);
        return c;
    }

    /**
     * Get the local parameters for the current template call.
     *
     * @return the supplied parameters
     */

    @Override
    public ParameterSet getLocalParameters() {
        return getCaller().getLocalParameters();
    }

    /**
     * Get the tunnel parameters for the current template call.
     *
     * @return the supplied tunnel parameters
     */

    @Override
    public ParameterSet getTunnelParameters() {
        return getCaller().getTunnelParameters();
    }

    /**
     * Get the Controller. May return null when running outside XSLT or XQuery
     */

    @Override
    public final Controller getController() {
        return controller;
    }

    /**
     * Get the Configuration
     */

    @Override
    public final Configuration getConfiguration() {
        return controller.getConfiguration();
    }

    /**
     * Get the Name Pool
     */

    @Override
    public final NamePool getNamePool() {
        return controller.getConfiguration().getNamePool();
    }

    /**
     * Get the calling XPathContext (the next one down the stack). This will be null if unknown, or
     * if the bottom of the stack has been reached.
     */

    @Override
    public final XPathContext getCaller() {
        return caller;
    }

    /**
     * Set a new sequence iterator.
     */

    @Override
    public void setCurrentIterator(FocusIterator iter) {
        currentIterator = iter;
        last = new LastValue(-1);
    }

    /**
     * Create, set, and return a focus tracking iterator that wraps a supplied sequence iterator.
     *
     * @param iter the current iterator. The context item, position, and size are determined by reference
     *             to the current iterator.
     */

    @Override
    public FocusIterator trackFocus(SequenceIterator iter) {
        Function<SequenceIterator, FocusTrackingIterator> factory =
                controller.getFocusTrackerFactory(false);
        //noinspection unchecked
        FocusIterator fit = factory.apply(iter);
        setCurrentIterator(fit);
        return fit;
    }

    /**
     * Create, set, and return a focus tracking iterator that wraps a supplied sequence iterator,
     * suitable for use in a multithreaded xsl:for-each iteration
     *
     * @param iter the current iterator. The context item, position, and size are determined by reference
     *             to the current iterator.
     */

    public FocusIterator trackFocusMultithreaded(SequenceIterator iter) {
        Function<SequenceIterator, FocusTrackingIterator> factory = controller.getFocusTrackerFactory(true);
        FocusIterator fit = factory.apply(iter);
        setCurrentIterator(fit);
        return fit;
    }


    /**
     * Get the current iterator.
     * This encapsulates the context item, context position, and context size.
     *
     * @return the current iterator, or null if there is no current iterator
     *         (which means the context item, position, and size are undefined).
     */

    @Override
    public final FocusIterator getCurrentIterator() {
        return currentIterator;
    }

    /**
     * Get the context item
     *
     * @return the context item, or null if the context item is undefined
     */

    @Override
    public final Item getContextItem() {
        if (currentIterator == null) {
            return null;
        }
        return currentIterator.current();
    }

    /**
     * Get the context size (the position of the last item in the current node list)
     *
     * @return the context size
     * @throws XPathException if the context position is undefined
     */

    @Override
    public final int getLast() throws XPathException {
        if (currentIterator == null) {
            XPathException e = new XPathException("The context item is absent, so last() is undefined");
            e.setXPathContext(this);
            e.setErrorCode("XPDY0002");
            throw e;
        }
        if (last.value >= 0) {
            return last.value;
        }
        int length = currentIterator.getLength();
        last = new LastValue(length);
        return length;
    }

    /**
     * Determine whether the context position is the same as the context size
     * that is, whether position()=last()
     */

    @Override
    public final boolean isAtLast() throws XPathException {
        if (currentIterator.getProperties().contains(SequenceIterator.Property.LOOKAHEAD)) {
            return !((LookaheadIterator) currentIterator).hasNext();
        }
        return currentIterator.position() == getLast();
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
        return caller.getURIResolver();
    }

    /**
     * Get the error reporter. If no ErrorReporter
     * has been set locally, the ErrorReporter in the Controller is returned; this in turn defaults
     * to the ErrorReporter set in the Configuration.
     *
     * @return the ErrorReporter in use.
     * @since 9.6. Changed in 10.0 to use ErrorReporter rather than ErrorListener
     */
    @Override
    public ErrorReporter getErrorReporter() {
        return caller.getErrorReporter();
    }

    /**
     * Get the current exception (in saxon:catch)
     *
     * @return the current exception, or null if there is none defined
     */
    @Override
    public XPathException getCurrentException() {
        return caller.getCurrentException();
    }

    /**
     * Get the thread manager used to process asynchronous xsl:result-document threads.
     *
     * @return the current thread manager; or null if multithreading is not supported
     */
    @Override
    public XPathContextMajor.ThreadManager getThreadManager() {
        return caller.getThreadManager();
    }

    /**
     * Get the current component
     */

    @Override
    public Component getCurrentComponent() {
        return caller.getCurrentComponent();
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
        return stackFrame;
    }

    public void makeStackFrameMutable() {
        if (stackFrame == StackFrame.EMPTY) {
            stackFrame = new StackFrame(null, SequenceTool.makeSequenceArray(0));
        }
    }

    /**
     * Get the value of a local variable, identified by its slot number
     */

    @Override
    public final Sequence evaluateLocalVariable(int slotnumber) {
        return stackFrame.slots[slotnumber];
    }

    /**
     * Set the value of a local variable, identified by its slot number
     */

    @Override
    public final void setLocalVariable(int slotNumber, Sequence value) throws XPathException {

        // The following code is deep defence against attempting to store a non-memo Closure in a variable.
        // This should not happen, and if it does, it means that the evaluation mode has been miscalculated.
        // But if it does happen, we recover by wrapping the Closure in a MemoSequence which remembers the
        // value as it is calculated.

        value = value.makeRepeatable();
        try {
            stackFrame.slots[slotNumber] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            if (slotNumber == -999) {
                throw new AssertionError("Internal error: Cannot set local variable: no slot allocated");
            } else {
                throw new AssertionError("Internal error: Cannot set local variable (slot " + slotNumber +
                    " of " + getStackFrame().getStackFrameValues().length + ")");
            }
        }
    }

    @Override
    public synchronized void waitForChildThreads() throws XPathException {
        getCaller().waitForChildThreads();
    }

    /**
     * Set the XSLT output state to "temporary" or "final"
     *
     * @param temporary set non-zero to set temporary output state; zero to set final output state. The integer
     * gives clues as to the reason temporary output state is being set, e.g. StandardNames.XSL_VARIABLE
     * indicates we are evaluating a variable.
     *
     */

    @Override
    public void setTemporaryOutputState(int temporary) {
        temporaryOutputState = temporary;
    }

    /**
     * Ask whether the XSLT output state is "temporary" or "final"
     *
     * @return non-zero if in temporary output state (integer identifies the reason); zero if in final output state
     */

    @Override
    public int getTemporaryOutputState() {
        return temporaryOutputState;
    }

    /**
     * Set the current output URI
     * @param uri the current output URI, or null if in temporary output state
     */

    @Override
    public void setCurrentOutputUri(String uri) {
        currentDestination = uri;
    }

    /**
     * Get the current output URI
     * @return the current output URI, or null if in temporary output state
     */

    @Override
    public String getCurrentOutputUri() {
        return currentDestination;
    }

    /**
     * Use local parameter. This is called when a local xsl:param element is processed.
     * If a parameter of the relevant name was supplied, it is bound to the xsl:param element.
     * Otherwise the method returns false, so the xsl:param default will be evaluated
     *
     * @param parameterId Globally-unique parameter identifier
     * @param slotNumber  Slot number of the parameter within the stack frame of the called template
     * @param isTunnel    True if a tunnel parameter is required, else false  @return ParameterSet.NOT_SUPPLIED, ParameterSet.SUPPLIED, or ParameterSet.SUPPLIED_AND_CHECKED
     */

    @Override
    public int useLocalParameter(
            StructuredQName parameterId, int slotNumber, boolean isTunnel) throws XPathException {
        return getCaller().useLocalParameter(parameterId, slotNumber, isTunnel);
    }

    /**
     * Get the current mode.
     *
     * @return the current mode
     */

    @Override
    public Component.M getCurrentMode() {
        return getCaller().getCurrentMode();
    }

    /**
     * Get the current template. This is used to support xsl:apply-imports
     *
     * @return the current template
     */

    @Override
    public Rule getCurrentTemplateRule() {
        // In a minor context, the current template rule is always null. This is a consequence
        // of the way they are used.
        return null;
        //return getCaller().getCurrentTemplateRule();
    }

    /**
     * Get the current group iterator. This supports the current-group() and
     * current-grouping-key() functions in XSLT 2.0
     *
     * @return the current grouped collection
     */

    @Override
    public GroupIterator getCurrentGroupIterator() {
        return getCaller().getCurrentGroupIterator();
    }

    /**
     * Get the current merge group iterator. This supports the current-merge-group() and
     * current-merge-key() functions in XSLT 3.0
     *
     * @return the current grouped collection
     */

    @Override
    public GroupIterator getCurrentMergeGroupIterator() {
        return getCaller().getCurrentMergeGroupIterator();
    }

    /**
     * Get the current regex iterator. This supports the functionality of the regex-group()
     * function in XSLT 2.0.
     *
     * @return the current regular expressions iterator
     */

    @Override
    public RegexIterator getCurrentRegexIterator() {
        return getCaller().getCurrentRegexIterator();
    }

    /**
     * Get the current date and time for this query or transformation.
     * All calls during one transformation return the same answer.
     *
     * @return Get the current date and time. This will deliver the same value
     *         for repeated calls within the same transformation
     */

    @Override
    public DateTimeValue getCurrentDateTime() {
        return controller.getCurrentDateTime();
    }

    /**
     * Get the implicit timezone, as a positive or negative offset from UTC in minutes.
     * The range is -14hours to +14hours
     *
     * @return the implicit timezone as an offset from UTC in minutes
     */

    @Override
    public final int getImplicitTimezone() {
        return controller.getImplicitTimezone();
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
        return new ContextStackIterator(this);
    }



    @Override
    public Component getTargetComponent(int bindingSlot) {
        return getCaller().getTargetComponent(bindingSlot);
    }

    // Note: consider eliminating this class. A new XPathContextMinor is created under two circumstances,
    // (a) when the focus changes (i.e., a new current iterator), and (b) when the current
    // receiver changes. We could handle these by maintaining a stack of iterators and a stack of
    // receivers in the XPathContextMajor object. Adding a new iterator or receiver to the stack would
    // generally be cheaper than creating the new XPathContextMinor object. The main difficulty (in the
    // case of iterators) is knowing when to pop the stack: currently we rely on the garbage collector.
    // We can only really do this when the iterator comes to its end, which is difficult to detect.
    // Perhaps we should try to do static allocation, so that fixed slots are allocated for different
    // minor-contexts within a Procedure, and a compiled expression that uses the focus knows which
    // slot to look in.

    // Investigated the above Sept 2008. On xmark, with a 100Mb input, the path expression
    // count(site/people/person/watches/watch) takes just 13ms to execute (compared with 6500ms for building
    // the tree). Only 6 context objects are created while doing this. This doesn't appear to be a productive
    // area to look for new optimizations.

    /**
     * Container for cached value of the last() function.
     * This is shared between all context objects that share the same current iterator.
     * Introduced in 9.2 to handle the case where a new context is introduced when the current
     * outputter changes, without changing the current iterator: in this case the cached value
     * was being lost because each call on last() used a different context object.
     */

    protected static class LastValue {
        public final int value;

        public LastValue(int count) {
            value = count;
        }
    }
}

