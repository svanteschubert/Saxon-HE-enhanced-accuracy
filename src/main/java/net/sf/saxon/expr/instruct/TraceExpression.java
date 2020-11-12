////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.Traceable;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

import java.util.HashMap;
import java.util.Iterator;

/**
 * A wrapper expression used to trace expressions in XSLT and XQuery.
 */

public class TraceExpression extends Instruction implements Traceable {

    private Operand baseOp;
    private HashMap<String, Object> properties = new HashMap<>(10);

    /**
     * Create a trace expression that traces execution of a given child expression
     *
     * @param child the expression to be traced. This will be available to the TraceListener
     *              as the value of the "expression" property of the InstructionInfo.
     */
    public TraceExpression(Expression child) {
        baseOp = new Operand(this, child, OperandRole.SAME_FOCUS_ACTION);
        adoptChildExpression(child);
        child.gatherProperties((k, v) -> properties.put(k, v));
    }

    public Expression getChild() {
        return baseOp.getChildExpression();
    }

    public Expression getBody() {
        return baseOp.getChildExpression();
    }

    @Override
    public Iterable<Operand> operands() {
        return baseOp;
    }


    /**
     * Set a named property of the instruction/expression
     *
     * @param name  the name of the property
     * @param value the value of the property
     */

    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Get a named property of the instruction/expression
     *
     * @param name the name of the property
     * @return the value of the property
     */

    @Override
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property.
     *
     * @return an iterator over the properties
     */

    @Override
    public Iterator<String> getProperties() {
        return properties.keySet().iterator();
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "trace";
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "TraceExpr";
    }

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        TraceExpression t = new TraceExpression(getChild().copy(rebindings));
        t.setLocation(getLocation());   // Bug 3034
        t.properties = properties;
        return t;
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     *
     * @return true if this is an updating expression
     */

    @Override
    public boolean isUpdatingExpression() {
        return getChild().isUpdatingExpression();
    }

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     *
     * @return true if this expression is vacuous
     */

    @Override
    public boolean isVacuousExpression() {
        return getChild().isVacuousExpression();
    }

    /**
     * Check to ensure that this expression does not contain any inappropriate updating subexpressions.
     * This check is overridden for those expressions that permit updating subexpressions.
     *
     * @throws net.sf.saxon.trans.XPathException if the expression has a non-permitted updating subexpression
     */

    @Override
    public void checkForUpdatingSubexpressions() throws XPathException {
        getChild().checkForUpdatingSubexpressions();
    }

    @Override
    public int getImplementationMethod() {
        return getChild().getImplementationMethod();
    }

    /**
     * Execute this instruction, with the possibility of returning tail calls if there are any.
     * This outputs the trace information via the registered TraceListener,
     * and invokes the instruction being traced.
     *
     *
     * @param output the destination for the result
     * @param context the dynamic execution context
     * @return either null, or a tail call that the caller must invoke on return
     * @throws XPathException if execution of the target expression fails
     */
    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        TraceListener listener = controller.getTraceListener();
        Expression child = getChild();
        if (controller.isTracing()) {
            assert listener != null;
            listener.enter(child, properties, context);
            // Don't attempt tail call optimization when tracing, the results are too confusing
            child.process(output, context);
            listener.leave(child);
        } else {
            child.process(output, context);
        }
        return null;
    }

    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @return the static item type of the instruction
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getChild().getItemType();
    }

    /**
     * Determine the static cardinality of the expression. This establishes how many items
     * there will be in the result of the expression, at compile time (i.e., without
     * actually evaluating the result.
     *
     * @return one of the values Cardinality.ONE_OR_MORE,
     * Cardinality.ZERO_OR_MORE, Cardinality.EXACTLY_ONE,
     * Cardinality.ZERO_OR_ONE, Cardinality.EMPTY. This default
     * implementation returns ZERO_OR_MORE (which effectively gives no
     * information).
     */

    @Override
    public int getCardinality() {
        return getChild().getCardinality();
    }

    /**
     * Determine which aspects of the context the expression depends on. The result is
     * a bitwise-or'ed value composed from constants such as {@link net.sf.saxon.expr.StaticProperty#DEPENDS_ON_CONTEXT_ITEM} and
     * {@link net.sf.saxon.expr.StaticProperty#DEPENDS_ON_CURRENT_ITEM}. The default implementation combines the intrinsic
     * dependencies of this expression with the dependencies of the subexpressions,
     * computed recursively. This is overridden for expressions such as FilterExpression
     * where a subexpression's dependencies are not necessarily inherited by the parent
     * expression.
     *
     * @return a set of bit-significant flags identifying the dependencies of
     * the expression
     */

    @Override
    public int getDependencies() {
        return getChild().getDependencies();
    }

    /**
     * Determine whether this instruction potentially creates new nodes.
     */

    @Override
    public final boolean mayCreateNewNodes() {
        return !getChild().hasSpecialProperty(StaticProperty.NO_NODES_NEWLY_CREATED);
    }

    /**
     * Return the estimated cost of evaluating an expression. For a TraceExpression we return zero,
     * because ideally we don't want trace expressions to affect optimization decisions.
     *
     * @return zero
     */
    @Override
    public int getNetCost() {
        return 0;
    }

    /**
     * Evaluate an expression as a single item. This always returns either a single Item or
     * null (denoting the empty sequence). No conversion is done. This method should not be
     * used unless the static type of the expression is a subtype of "item" or "item?": that is,
     * it should not be called if the expression may return a sequence. There is no guarantee that
     * this condition will be detected.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the node or atomic value that results from evaluating the
     * expression; or null to indicate that the result is an empty
     * sequence
     * @throws net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
     *                                           expression
     */

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        Expression child = getChild();
        if (controller.isTracing()) {
            TraceListener listener = controller.getTraceListener();

            listener.enter(child, properties, context);
            Item result = child.evaluateItem(context);
            listener.leave(child);
            return result;
        } else {
            return child.evaluateItem(context);
        }
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     * of the expression
     * @throws net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
     *                                           expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        Expression child = getChild();
        if (controller.isTracing()) {
            TraceListener listener = controller.getTraceListener();
            listener.enter(child, properties, context);
            SequenceIterator result = child.iterate(context);
            listener.leave(child);
            return result;
        } else {
            return child.iterate(context);
        }

    }

    @Override
    public int getInstructionNameCode() {
        if (getChild() instanceof Instruction) {
            return ((Instruction) getChild()).getInstructionNameCode();
        } else {
            return -1;
        }
    }

    /**
     * Export the expression structure. The abstract expression tree
     * is written to the supplied output destination. Note: trace expressions
     * are omitted from the generated SEF file.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        getChild().export(out);
    }

    /**
     * Evaluate an updating expression, adding the results to a Pending Update List.
     * The default implementation of this method, which is used for non-updating expressions,
     * throws an UnsupportedOperationException
     *
     * @param context the XPath dynamic evaluation context
     * @param pul     the pending update list to which the results should be written
     */

    @Override
    public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        if (controller.isTracing()) {
            TraceListener listener = controller.getTraceListener();
            listener.enter(getChild(), properties, context);
            getChild().evaluatePendingUpdates(context, pul);
            listener.leave(getChild());
        } else {
            getChild().evaluatePendingUpdates(context, pul);
        }
    }

    /**
     * Produce a short string identifying the expression for use in error messages
     *
     * @return a short string, sufficient to identify the expression
     */
    @Override
    public String toShortString() {
        return getChild().toShortString();
    }
}

