////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;

import java.util.HashMap;
import java.util.Iterator;

/**
 * A wrapper expression used to trace expressions in XSLT and XQuery.
 */

public class TraceExpression extends Instruction {

    private final Operand baseOp;
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


    public boolean equals(Object other) {
        return other instanceof TraceExpression &&
                getChild().equals(((TraceExpression) other).getChild());
    }

    /**
     * Compute a hash code, which will then be cached for later use
     *
     * @return a computed hash code
     */
    @Override
    protected int computeHashCode() {
        return 0x64646464 ^ getChild().hashCode();
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
        return makeElaborator().elaborateForItem().eval(context);
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
        return makeElaborator().elaborateForPull().iterate(context);
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
     * Perform optimisation of an expression and its subexpressions. This is the third and final
     * phase of static optimization.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor     an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                    The parameter is set to null if it is known statically that the context item will be undefined.
     *                    If the type of the context item is not known statically, the argument is set to
     *                    {@link Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        // See bug 6415
        Expression t = super.optimize(visitor, contextInfo);
        if (t != this) {
            return t;
        }
        if (getChild() instanceof TraceExpression) {
            return getChild();
        }
        return this;
    }

    /**
     * Export the expression structure. The abstract expression tree
     * is written to the supplied output destination. Note: trace expressions
     * are omitted from the generated SEF file.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        getChild().export(out);

        // Following code was written for diagnostics, to show the tree with the trace instructions
//        out.startElement("traceExp");
//        for (Map.Entry<String, Object> prop : properties.entrySet()) {
//            out.emitAttribute(prop.getKey(), prop.getValue().toString());
//        }
//        out.emitAttribute("line", getLocation().getLineNumber()+"");
//        out.emitAttribute("col", getLocation().getColumnNumber() + "");
//        getChild().export(out);
//        out.endElement();
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

    public Elaborator getElaborator() {
        return new TraceExpressionElaborator();
    }

    private static class TraceExpressionElaborator extends FallbackElaborator {
        /**
         * Get a function that evaluates the underlying expression in the form of
         * a Java string, this being the result of applying fn:string() to the result
         * of the expression.
         *
         * @param zeroLengthWhenAbsent if true, then when the result of the expression
         *                             is an empty sequence, the result of the StringEvaluator
         *                             should be a zero-length string. If false, the return value
         *                             should be null.
         * @return an evaluator for the expression that returns a string.
         */
        @Override
        public StringEvaluator elaborateForString(boolean zeroLengthWhenAbsent) {
            TraceExpression expr = (TraceExpression) getExpression();
            Expression body = expr.getBody();
            StringEvaluator baseEval = expr.getBody().makeElaborator().elaborateForString(zeroLengthWhenAbsent);
            return context -> {
                Controller controller = context.getController();
                assert controller != null;
                if (controller.isTracing()) {
                    TraceListener listener = controller.getTraceListener();
                    listener.enter(body, expr.properties, context);
                    String result = baseEval.eval(context);
                    listener.leave(body);
                    return result;
                } else {
                    return baseEval.eval(context);
                }
            };
        }

        @Override
        public UpdateEvaluator elaborateForUpdate() {
            TraceExpression expr = (TraceExpression) getExpression();
            Expression body = expr.getBody();
            UpdateEvaluator baseEval = expr.getBody().makeElaborator().elaborateForUpdate();
            return (context, pul) -> {
                Controller controller = context.getController();
                assert controller != null;
                if (controller.isTracing()) {
                    TraceListener listener = controller.getTraceListener();
                    listener.enter(body, expr.properties, context);
                    baseEval.registerUpdates(context, pul);
                    listener.leave(body);
                } else {
                    baseEval.registerUpdates(context, pul);
                }
            };
        }

//        @Override
//        public SequenceEvaluator eagerly() {
//            TraceExpression expr = (TraceExpression) getExpression();
//            SequenceEvaluator baseEval = expr.getBody().makeElaborator().eagerly();
//            return context -> {
//                Controller controller = context.getController();
//                assert controller != null;
//                if (controller.isTracing()) {
//                    TraceListener listener = controller.getTraceListener();
//
//                    listener.enter(expr, expr.properties, context);
//                    GroundedValue result = (GroundedValue)baseEval.evaluate(context);
//                    listener.leave(expr);
//                    return result;
//                } else {
//                    return (GroundedValue) baseEval.evaluate(context);
//                }
//            };
//        }
//
//        @Override
//        public SequenceEvaluator lazily(boolean repeatable) {
//            return eagerly();
//        }

        @Override
        public PullEvaluator elaborateForPull() {
            TraceExpression expr = (TraceExpression) getExpression();
            Expression body = expr.getBody();
            PullEvaluator baseEval = expr.getBody().makeElaborator().elaborateForPull();
            return context -> {
                Controller controller = context.getController();
                assert controller != null;
                if (controller.isTracing()) {
                    TraceListener listener = controller.getTraceListener();

                    listener.enter(body, expr.properties, context);
                    SequenceIterator result = baseEval.iterate(context);
                    listener.leave(body);
                    return result;
                } else {
                    return baseEval.iterate(context);
                }
            };
        }

        @Override
        public PushEvaluator elaborateForPush() {
            TraceExpression expr = (TraceExpression) getExpression();
            Expression body = expr.getBody();
            PushEvaluator baseEval = body.makeElaborator().elaborateForPush();
            return (output, context) -> {
                Controller controller = context.getController();
                assert controller != null;
                if (controller.isTracing()) {
                    TraceListener listener = controller.getTraceListener();
                    listener.enter(body, expr.properties, context);
                    TailCall tc = baseEval.processLeavingTail(output, context);
                    dispatchTailCall(tc);
                    listener.leave(body);
                } else {
                    dispatchTailCall(baseEval.processLeavingTail(output, context));
                }
                return null;
            };
        }

        @Override
        public ItemEvaluator elaborateForItem() {
            TraceExpression expr = (TraceExpression) getExpression();
            Expression body = expr.getBody();
            ItemEvaluator baseEval = expr.getBody().makeElaborator().elaborateForItem();
            return context -> {
                Controller controller = context.getController();
                assert controller != null;
                if (controller.isTracing()) {
                    TraceListener listener = controller.getTraceListener();

                    listener.enter(body, expr.properties, context);
                    Item result = baseEval.eval(context);
                    listener.leave(body);
                    return result;
                } else {
                    return baseEval.eval(context);
                }
            };
        }

        @Override
        public BooleanEvaluator elaborateForBoolean() {
            TraceExpression expr = (TraceExpression) getExpression();
            Expression body = expr.getBody();
            BooleanEvaluator baseEval = expr.getBody().makeElaborator().elaborateForBoolean();
            return context -> {
                Controller controller = context.getController();
                assert controller != null;
                if (controller.isTracing()) {
                    TraceListener listener = controller.getTraceListener();
                    listener.enter(body, expr.properties, context);
                    boolean result = baseEval.eval(context);
                    listener.leave(body);
                    return result;
                } else {
                    return baseEval.eval(context);
                }
            };
        }

        @Override
        public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent) {
            TraceExpression expr = (TraceExpression) getExpression();
            Expression body = expr.getBody();
            UnicodeStringEvaluator baseEval = expr.getBody().makeElaborator().elaborateForUnicodeString(zeroLengthWhenAbsent);
            return context -> {
                Controller controller = context.getController();
                assert controller != null;
                if (controller.isTracing()) {
                    TraceListener listener = controller.getTraceListener();

                    listener.enter(body, expr.properties, context);
                    UnicodeString result = baseEval.eval(context);
                    listener.leave(body);
                    return result;
                } else {
                    return baseEval.eval(context);
                }
            };
        }
    }
}

