////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
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

import java.util.EventListener;
import java.util.Map;

/**
 * This interface defines methods that are called by Saxon during the execution of
 * a stylesheet, if tracing is switched on. Tracing can be switched on by nominating
 * an implementation of this class using the TRACE_LISTENER feature of the TransformerFactory,
 * or using the addTraceListener() method of the Controller, which is Saxon's implementation
 * of tyhe JAXP javax.xml.transform.Transformer interface.
 */

public interface TraceListener extends EventListener {

    /**
     * Method called to supply the destination for output
     *
     * @param stream a Logger to which any output produced by the TraceListener should be written
     * @since 8.0. Changed in 9.6 to accept a Logger.
     */

    void setOutputDestination(Logger stream);

    /**
     * Method called at the start of execution, that is, when the run-time transformation starts
     *
     * @param controller identifies the transformation controller, and provides the listener with
     *                   access to context and configuration information
     */

    void open(Controller controller);

    /**
     * Method called at the end of execution, that is, when the run-time execution ends
     */

    void close();

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
     * once for each item processed.
     *
     * @param currentItem the new current item. Item objects are not mutable; it is safe to retain
     *                    a reference to the Item for later use.
     */

    void startCurrentItem(Item currentItem);

    /**
     * Method that is called when an instruction has finished processing a new current item
     * and is ready to select a new current item or revert to the previous current item.
     * The method will be called before the leave() method for the instruction that made this
     * item current.
     *
     * @param currentItem the item that was current, whose processing is now complete. This will represent
     *                    the same underlying item as the corresponding startCurrentItem() call, though it will
     *                    not necessarily be the same actual object.
     */

    void endCurrentItem(Item currentItem);

    /**
     * Method called when a search for a template rule is about to start
     */

    default void startRuleSearch() {}

    /**
     * Method called when a rule search has completed.
     *
     * @param rule the rule (or possible built-in ruleset) that has been selected
     * @param mode the mode in operation
     * @param item the item that was checked against
     */

    default void endRuleSearch(Object rule, Mode mode, Item item) {};

}

