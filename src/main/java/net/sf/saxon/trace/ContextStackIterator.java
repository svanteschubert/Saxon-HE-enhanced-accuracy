////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.ContextOriginator;
import net.sf.saxon.expr.UserFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.trans.rules.BuiltInRuleSet;

import java.util.Iterator;

/**
 * This class provides a representation of the current runtime call stack, as represented by the stack
 * of XPathContext objects.
 */
public class ContextStackIterator implements Iterator<ContextStackFrame> {

    // TODO: this class is no longer used by StandardErrorListener.printStackTrace(). It's potentially
    // still useful to have a programmatic way of obtaining the stack trace, but this class is rather
    // clumsy and probably isn't used.

    private XPathContextMajor next;

    /**
     * Create an iterator over the stack of XPath dynamic context objects, starting with the top-most
     * stackframe and working down. The objects returned by this iterator will be of class {@link ContextStackFrame}.
     * Note that only "major" context objects are considered - those that have a stack frame of their own.
     *
     * @param context the current context
     */

    public ContextStackIterator(XPathContext context) {
        if (!(context instanceof XPathContextMajor)) {
            context = getMajorCaller(context);
        }
        next = (XPathContextMajor) context;
    }

    /**
     * Returns <tt>true</tt> if the iteration has more elements. (In other
     * words, returns <tt>true</tt> if <tt>next</tt> would return an element
     * rather than throwing an exception.)
     *
     * @return <tt>true</tt> if the iterator has more elements.
     */
    @Override
    public boolean hasNext() {
        return next != null;
    }

    /**
     * Returns the next element in the iteration.  Calling this method
     * repeatedly until the {@link #hasNext()} method returns false will
     * return each element in the underlying collection exactly once.
     *
     * @return the next element in the iteration, which will always be an instance
     *         of {@link ContextStackFrame}
     * @throws java.util.NoSuchElementException
     *          iteration has no more elements.
     */
    /*@Nullable*/
    @Override
    public ContextStackFrame next() {
        XPathContextMajor context = next;
        if (context == null) {
            return null;
        }
        ContextOriginator origin = context.getOrigin();

        if (origin instanceof Controller) {
            next = getMajorCaller(context);
            return new ContextStackFrame.CallingApplication();
        } else if (origin instanceof BuiltInRuleSet) {
            next = getMajorCaller(context);
            return new ContextStackFrame.BuiltInTemplateRule(context);
        } else if (origin instanceof UserFunction) {
            ContextStackFrame.FunctionCall sf = new ContextStackFrame.FunctionCall();
            UserFunction ufc = (UserFunction) origin;
            sf.setLocation(ufc.getLocation());
            sf.setFunctionName(ufc.getFunctionName());
            sf.setContextItem(context.getContextItem());
            sf.setContext(context);
            next = getMajorCaller(context);
            return sf;
        } else if (origin instanceof UserFunctionCall) {
            // No longer used? Bug 3671
            ContextStackFrame.FunctionCall sf = new ContextStackFrame.FunctionCall();
            UserFunctionCall ufc = (UserFunctionCall) origin;
            sf.setLocation(ufc.getLocation());
            sf.setFunctionName(ufc.getFunctionName());
            sf.setContextItem(context.getContextItem());
            sf.setContext(context);
            next = getMajorCaller(context);
            return sf;
        } else if (origin instanceof ApplyTemplates) {
            ContextStackFrame.ApplyTemplates sf = new ContextStackFrame.ApplyTemplates();
            ApplyTemplates loc = (ApplyTemplates) origin;
            sf.setLocation(loc.getLocation());
            sf.setContextItem(context.getContextItem());
            sf.setContext(context);
            next = getMajorCaller(context);
            return sf;
        } else if (origin instanceof CallTemplate) {
            ContextStackFrame.CallTemplate sf = new ContextStackFrame.CallTemplate();
            CallTemplate loc = (CallTemplate) origin;
            sf.setLocation(loc.getLocation());
            sf.setTemplateName(loc.getObjectName());
            sf.setContextItem(context.getContextItem());
            sf.setContext(context);
            next = getMajorCaller(context);
            return sf;
        } else if (origin instanceof GlobalVariable) {
            ContextStackFrame.VariableEvaluation sf = new ContextStackFrame.VariableEvaluation();
            GlobalVariable var = (GlobalVariable) origin;
            sf.setLocation(var.getLocation());
            sf.setContextItem(context.getContextItem());
            sf.setVariableName(var.getVariableQName());
            sf.setComponent(var);
            sf.setContext(context);
            next = getMajorCaller(context);
            return sf;
        } else {
            //other context changes are not considered significant enough to report
            //out.println("    In unidentified location " + construct);
            next = getMajorCaller(context);
            ContextStackFrame csf = next();
            if (csf == null) {
                // we can't return null, because hasNext() returned true...
                return new ContextStackFrame.CallingApplication();
            } else {
                return csf;
            }
        }

    }

    private static XPathContextMajor getMajorCaller(XPathContext context) {
        XPathContext caller = context.getCaller();
        while (!(caller == null || caller instanceof XPathContextMajor)) {
            caller = caller.getCaller();
        }
        return (XPathContextMajor) caller;
    }

    /**
     * Removes from the underlying collection the last element returned by the
     * iterator (optional operation).
     *
     * @throws UnsupportedOperationException as the <tt>remove</tt>
     *                                       operation is not supported by this Iterator.
     */
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }


}

