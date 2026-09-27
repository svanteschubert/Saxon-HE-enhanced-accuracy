////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.trace.Traceable;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.XPathException;

import java.util.Map;

/**
 * This interface defines methods that are called by Saxon during the execution of
 * a stylesheet or query, if tracing is switched on. Tracing can be switched on by nominating
 * an implementation of this class using the TRACE_LISTENER feature of the TransformerFactory,
 * or using the addTraceListener() method of the Controller.
 */

public interface TraceListener {

    /**
     * Method called to supply the destination for output. The default implementation does nothing.
     *
     * @param stream a Logger to which any output produced by the TraceListener should be written
     * @since 8.0. Changed in 9.6 to accept a Logger.
     */

    default void setOutputDestination(Logger stream) {}

    /**
     * Method called at the start of a run-time transformation. If a transformer
     * is invoked multiple times, the method is called at the start of each transformation.
     * This applies whether the call uses apply-templates, call-template, or invoke-function
     * invocation. The default implementation does nothing.
     *
     * @param controller identifies the transformation controller, and provides the listener with
     *                   access to context and configuration information
     */

    default void open(Controller controller) {};

    /**
     * Method called at the end of each transformation, whether it succeeds or fails. The default
     * implementation does nothing.
     */

    default void close() {};

    /**
     * Method that is called when an instruction in the stylesheet gets processed. Default implementation
     * does nothing.
     *
     * @param instruction gives information about the instruction being
     *                    executed, and about the context in which it is executed. This object is mutable,
     *                    so if information from the InstructionInfo is to be retained, it must be copied.
     * @param properties extra information about the instruction to be included in the trace
     * @param context  the XPath evaluation context
     */

    default void enter(Traceable instruction, Map<String, Object> properties, XPathContext context) {}

    /**
     * Method that is called after processing an instruction of the stylesheet,
     * that is, after any child instructions have been processed. Default implementation does nothing.
     *
     * <p>In normal operation, {@code enter} and {@code leave} operations are paired, reflecting
     * a simple execution stack. There are two things that can disrupt this: dynamic errors, and
     * parallel evaluation.</p>
     *
     * <p>If a dynamic error occurs during evaluation of an instruction, there will be
     * {@code enter} events with no corresponding {@code leave}. If the error is caught,
     * execution may continue. A {@code TraceListener} may attempt to recover from this
     * by implementing the {@link #checkpoint} and @link #recover} methods.</p>
     *
     * <p></p>
     *
     * @param instruction gives the same information that was supplied to the
     *                    enter method, though it is not necessarily the same object. Note that the
     *                    line number of the instruction is that of the start tag in the source stylesheet,
     *                    not the line number of the end tag.
     */

    default void leave(Traceable instruction) {}

    /**
     * Method that is called by an instruction that changes the current item
     * in the source document: that is, xsl:for-each, xsl:apply-templates, xsl:for-each-group.
     * The method is called after the enter method for the relevant instruction, and is called
     * once for each item processed. The default implementation does nothing.
     *
     * @param currentItem the new current item. Item objects are not mutable; it is safe to retain
     *                    a reference to the Item for later use.
     */

    default void startCurrentItem(Item currentItem) {}

    /**
     * Method that is called when an instruction has finished processing a new current item
     * and is ready to select a new current item or revert to the previous current item.
     * The method will be called before the leave() method for the instruction that made this
     * item current. Calls on endCurrentItem() should be properly paired with calls
     * on startCurrentItem(), but this is not guaranteed in the event of dynamic errors.
     * The default implementation does nothing.
     *
     * @param currentItem the item that was current, whose processing is now complete. This will represent
     *                    the same underlying item as the corresponding startCurrentItem() call, though it will
     *                    not necessarily be the same actual object.
     */

    default void endCurrentItem(Item currentItem) {}

    /**
     * Method called immediately after the {@link #enter} method corresponding to an
     * {@code xsl:try} instruction (or XQuery try/catch). The method may return an object
     * containing checkpoint information, which will be passed to the {@link #recover}
     * method if an error occurs and is caught.
     * @return arbitrary checkpoint information, or null. The default implementation returns
     * null.
     */

    default Object checkpoint() {
        return null;
    }

    /**
     * Method called when an error is caught by an {@code xsl:catch} (or XQuery try/catch).
     * @param checkpoint A checkpoint object returned by a previous call to the {@link #checkpoint} method.
     * @param err The error that was caught
     */

    default void recover(Object checkpoint, XPathException err) {}

    /**
     * Method called when a search for a template rule is about to start. The default implementation
     * does nothing.
     */


    default void startRuleSearch() {}

    /**
     * Method called when a rule search has completed. The default implementation
     * does nothing.
     *
     * @param rule the rule (or possible built-in ruleset) that has been selected
     * @param mode the mode in operation
     * @param item the item that was checked against
     */

    default void endRuleSearch(Object rule, Mode mode, Item item) {}

}

