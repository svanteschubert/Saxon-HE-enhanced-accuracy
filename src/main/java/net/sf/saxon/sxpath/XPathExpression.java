////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.sxpath;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is a representation of an XPath Expression for use with the {@link XPathEvaluator} class.
 * It is modelled on the XPath API defined in JAXP 1.3, but is free-standing, and is more strongly typed.
 * <p>In Saxon 9.6, the methods that returned Object and did implicit conversion to a native Java object
 * have been removed; all conversions must now be done explicitly.</p>
 *
 * @author Michael H. Kay
 */


public class XPathExpression {

    private StaticContext env;
    private Expression expression;
    private SlotManager stackFrameMap;
    private Executable executable;
    private int numberOfExternalVariables;

    /**
     * The constructor is protected, to ensure that instances can only be
     * created using the createExpression() or createPattern() method of XPathEvaluator
     *
     * @param env       the static context of the expression
     * @param exp       the internal representation of the compiled expression
     * @param exec      the Executable associated with this expression
     */

    protected XPathExpression(StaticContext env, Expression exp, Executable exec) {
        expression = exp;
        this.env = env;
        this.executable = exec;
    }

    /**
     * Get the Executable associated with this expression
     * @return the associated Executable
     */

    public Executable getExecutable() {
        return executable;
    }

    /**
     * Define the number of slots needed for local variables within the expression
     *
     * @param map                       the stack frame map identifying all the variables used by the expression, both
     *                                  those declared internally, and references to variables declared externally
     * @param numberOfExternalVariables the number of slots in the stack frame allocated to
     *                                  externally-declared variables. These must be assigned a value before execution starts
     */

    protected void setStackFrameMap(SlotManager map, int numberOfExternalVariables) {
        stackFrameMap = map;
        this.numberOfExternalVariables = numberOfExternalVariables;
    }

    /**
     * Create a dynamic context suitable for evaluating this expression, without supplying a context
     * item
     *
     * @return an XPathDynamicContext object representing a suitable dynamic context. This will
     *         be initialized with a stack frame suitable for holding the variables used by the
     *         expression.
     */

    public XPathDynamicContext createDynamicContext() {
        XPathContextMajor context = new XPathContextMajor(null, executable);
        context.openStackFrame(stackFrameMap);
        return new XPathDynamicContext(env.getRequiredContextItemType(), context, stackFrameMap);
    }


    /**
     * Create a dynamic context suitable for evaluating this expression
     *
     * @param contextItem the initial context item, which may be null if no
     *                    context item is required, or if it is to be supplied later
     * @return an XPathDynamicContext object representing a suitable dynamic context. This will
     *         be initialized with a stack frame suitable for holding the variables used by the
     *         expression.
     * @throws XPathException if the context item does not match the required context item type
     *                        set up in the static context
     */

    public XPathDynamicContext createDynamicContext(Item contextItem) throws XPathException {
        checkContextItemType(contextItem);
        XPathContextMajor context = new XPathContextMajor(contextItem, executable);
        context.openStackFrame(stackFrameMap);
        return new XPathDynamicContext(env.getRequiredContextItemType(), context, stackFrameMap);
    }

    /**
     * Create a dynamic context suitable for evaluating this expression within an environment
     * represented by an existing controller. The dynamic context will inherit properties of the
     * Controller such as the current error listener, URI resolver, document pool, and implicit
     * timezone.
     *
     * @param controller  an existing controller. May be null, in which case the method is
     *                    equivalent to calling {@link #createDynamicContext(net.sf.saxon.om.Item)}
     * @param contextItem the initial context item, which may be null if no
     *                    context item is required, or if it is to be supplied later
     * @return an XPathDynamicContext object representing a suitable dynamic context. This will
     *         be initialized with a stack frame suitable for holding the variables used by the
     *         expression.
     * @throws XPathException if the context item does not match the required context item type
     *                        set up in the static context
     * @since 9.2
     */

    public XPathDynamicContext createDynamicContext(Controller controller, Item contextItem)
            throws XPathException {
        checkContextItemType(contextItem);
        if (controller == null) {
            return createDynamicContext(contextItem);
        } else {
            XPathContextMajor context = controller.newXPathContext();
            context.openStackFrame(stackFrameMap);
            XPathDynamicContext dc = new XPathDynamicContext(env.getRequiredContextItemType(), context, stackFrameMap);
            if (contextItem != null) {
                dc.setContextItem(contextItem);
            }
            return dc;
        }
    }

    private void checkContextItemType(Item contextItem) throws XPathException {
        if (contextItem != null) {
            ItemType type = env.getRequiredContextItemType();
            TypeHierarchy th = env.getConfiguration().getTypeHierarchy();
            if (!type.matches(contextItem, th)) {
                throw new XPathException("Supplied context item does not match required context item type " +
                                                 type);
            }
        }
    }

    /**
     * Execute the expression, returning the result as a {@link SequenceIterator}, whose members will be instances
     * of the class {@link net.sf.saxon.om.Item}
     * <p>Note that if evaluation of the expression fails with a dynamic error, this will generally
     * be reported in the form of an exception thrown by the next() method of the {@link SequenceIterator}</p>
     *
     * @param context the XPath dynamic context
     * @return an iterator over the result of the expression
     * @throws XPathException if a dynamic error occurs during the expression evaluation (though in many
     *                        cases, the error will only be detected when the iterator is actually used).
     */

    public SequenceIterator iterate(XPathDynamicContext context) throws XPathException {
        context.checkExternalVariables(stackFrameMap, numberOfExternalVariables);
        return expression.iterate(context.getXPathContextObject());
    }

    /**
     * Execute the expression, returning the result as a List, whose members will be instances
     * of the class {@link net.sf.saxon.om.Item}
     *
     * @param context the XPath dynamic context
     * @return a list of items representing the result of the expression
     * @throws XPathException if a dynamic error occurs while evaluation the expression
     */

    public List<Item> evaluate(XPathDynamicContext context) throws XPathException {
        List<Item> list = new ArrayList<>(20);
        expression.iterate(context.getXPathContextObject()).forEachOrFail(list::add);
        return list;
    }

    /**
     * Execute the expression, returning the result as a single {@link net.sf.saxon.om.Item}
     * If the result of the expression is a sequence containing more than one item, items after
     * the first are discarded. If the result is an empty sequence, the method returns null.
     *
     * @param context the XPath dynamic context
     * @return the first item in the result of the expression, or null to indicate that the result
     *         is an empty sequence
     * @throws XPathException if a dynamic error occurs during the expression evaluation
     */


    /*@Nullable*/
    public Item evaluateSingle(XPathDynamicContext context) throws XPathException {
        SequenceIterator iter = expression.iterate(context.getXPathContextObject());
        Item result = iter.next();
        iter.close();
        return result;
    }

    /**
     * Evaluate the expression, returning its effective boolean value
     *
     * @param context the XPath dynamic context
     * @return the effective boolean value of the result, as defined by the fn:boolean() function
     * @throws XPathException if a dynamic error occurs, or if the result is a value for which
     *                        no effective boolean value is defined
     */

    public boolean effectiveBooleanValue(XPathDynamicContext context) throws XPathException {
        return expression.effectiveBooleanValue(context.getXPathContextObject());
    }

    /**
     * Low-level method to get the internal Saxon expression object. This exposes a wide range of
     * internal methods that may be needed by specialized applications, and allows greater control
     * over the dynamic context for evaluating the expression.
     *
     * @return the underlying Saxon expression object.
     */

    public Expression getInternalExpression() {
        return expression;
    }

}

