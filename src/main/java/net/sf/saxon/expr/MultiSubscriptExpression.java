////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.functions.hof.FilterFn;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.z.IntHashSet;

import java.util.function.BiConsumer;

/**
 * A MultiSubscriptExpression represents a FilterExpression of the form EXPR[n]
 * where n is known to be of type xs:numeric* and to be independent of the focus;
 * it does not need to be constant. Used in 4.0 which allows the filter to
 * be a sequence of numbers.
 */
public class MultiSubscriptExpression extends UnaryExpression {

    private final Operand subscriptOp;

    /**
     * Construct a MultiSubscriptExpression
     *
     * @param base      the expression to be filtered
     * @param subscript the positional subscript filter
     */

    public MultiSubscriptExpression(Expression base, Expression subscript) {
        super(base);
        subscriptOp = new Operand(this, subscript, OperandRole.ATOMIC_SEQUENCE);
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.SAME_FOCUS_ACTION;
    }

    /**
     * Get the properties of this object to be included in trace messages, by supplying
     * the property values to a supplied consumer function
     *
     * @param consumer the function to which the properties should be supplied, as (property name,
     *                 value) pairs.
     */
    @Override
    public void gatherProperties(BiConsumer<String, Object> consumer) {
        super.gatherProperties(consumer);
    }

    public Expression getSubscript() {
        return subscriptOp.getChildExpression();
    }

    public void setSubscript(Expression subscript) {
        subscriptOp.setChildExpression(subscript);
    }


    /**
     * Type-check the expression.
     */
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().typeCheck(visitor, contextInfo);
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().optimize(visitor, contextInfo);
        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        MultiSubscriptExpression exp =
                new MultiSubscriptExpression(getBaseExpression().copy(rebindings),
                                             getSubscript().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    @Override
    public Iterable<Operand> operands() {
        return operandList(getOperand(), subscriptOp);
    }

    /**
     * Get the subscript expression
     *
     * @return the expression used to compute the one-based start offset
     */

    public Expression getSubscriptExpression() {
        return getSubscript();
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     * {@link #PROCESS_METHOD}
     */
    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD;
    }

    /**
     * Compare two expressions to see if they are equal
     *
     * @param other the other expression
     * @return true if the expressions are equivalent
     */

    public boolean equals(Object other) {
        return other instanceof MultiSubscriptExpression &&
                getBaseExpression().isEqual(((MultiSubscriptExpression) other).getBaseExpression()) &&
                getSubscript().isEqual(((MultiSubscriptExpression) other).getSubscript());
    }

    @Override
    protected int computeHashCode() {
        return getBaseExpression().hashCode() ^ getSubscript().hashCode();
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "MultiSubscriptExpression";
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
     * @throws XPathException if any dynamic error occurs evaluating the
     *                        expression
     */
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForPull().iterate(context);
    }

    /**
     * Get the item at a specified position within a SequenceIterator
     * @param iter the SequenceIterator (which may be consumed by this operation)
     * @param index the position (1-based) - must be positive
     * @return the item at that position, or null if out of range
     * @throws XPathException if a failure occurs evaluating the iterator
     */

    public static Item getItemAt(SequenceIterator iter, int index) throws XPathException {
        Item item;
        if (index == 1) {
            item = iter.next();
        } else if (iter instanceof MemoSequence.ProgressiveIterator) {
            MemoSequence mem = ((MemoSequence.ProgressiveIterator) iter).getMemoSequence();
            item = mem.itemAt(index - 1);
        } else if (iter instanceof GroundedIterator && ((GroundedIterator) iter).isActuallyGrounded()) {
            try {
                GroundedValue value = SequenceTool.toGroundedValue(iter);
                item = value.itemAt(index - 1);
            } catch (UncheckedXPathException e) {
                throw e.getXPathException();
            }
        } else {
            SequenceIterator tail = TailIterator.make(iter, index);
            item = tail.next();
            tail.close();
        }
        return item;
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in export() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "multiSubscript";
    }



    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("multiSubscript", this);
        getBaseExpression().export(destination);
        getSubscript().export(destination);
        destination.endElement();
    }

    /**
     * <p>The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form.</p>
     * <p>For subclasses of Expression that represent XPath expressions, the result should always be a string that
     * parses as an XPath 3.0 expression.</p>
     *
     * @return a representation of the expression as a string
     */
    @Override
    public String toString() {
        return ExpressionTool.parenthesize(getBaseExpression()) + "[" + getSubscript() + "]";
    }

    /**
     * <p>The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form.</p>
     * <p>For subclasses of Expression that represent XPath expressions, the result should always be a string that
     * parses as an XPath 3.0 expression.</p>
     *
     * @return a representation of the expression as a string
     */
    @Override
    public String toShortString() {
        return ExpressionTool.parenthesize(getBaseExpression()) +
                "[" + getSubscript().toShortString() + "]";
    }


    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new MultiSubscriptExprElaborator();
    }

    /**
     * An elaborator for a "subscript" expression, typically written as {@code X[$n]} where {@code $n} is an
     * integer or a numeric expression.
     */

    public static class MultiSubscriptExprElaborator extends PullElaborator {

        public PullEvaluator elaborateForPull() {
            final MultiSubscriptExpression expr = (MultiSubscriptExpression) getExpression();
            final PullEvaluator baseEval = expr.getBaseExpression().makeElaborator().elaborateForPull();
            final PullEvaluator indexEval = expr.getSubscriptExpression().makeElaborator().elaborateForPull();
            return context -> {
                SequenceIterator indices = indexEval.iterate(context);
                IntHashSet indexSet = new IntHashSet();
                Item item;
                while ((item = indices.next()) != null) {
                    int val = ((NumericValue)item).asSubscript();
                    if (val > 0) {
                        indexSet.add(val);
                    }
                }
                SequenceIterator iter = baseEval.iterate(context);
                return new FilterFn.PositionalFilterIterator(iter, (it, pos) -> indexSet.contains(pos));
            };
        }

    }
}

