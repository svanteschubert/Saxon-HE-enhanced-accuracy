////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.value.NumericValue;

/**
 * A SubscriptExpression represents a FilterExpression of the form EXPR[n]
 * where n is known to be singleton numeric and to be independent of the focus; it does not need to be constant
 */
public class SubscriptExpression extends SingleItemFilter {

    private final Operand subscriptOp;

    /**
     * Construct a SubscriptExpression
     *
     * @param base      the expression to be filtered
     * @param subscript the positional subscript filter
     */

    public SubscriptExpression(Expression base, Expression subscript) {
        super(base);
        subscriptOp = new Operand(this, subscript, OperandRole.SINGLE_ATOMIC);
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
        if (Literal.isConstantOne(getSubscript())) {
            return FirstItemExpression.makeFirstItemExpression(getBaseExpression());
        }
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
        SubscriptExpression exp = new SubscriptExpression(getBaseExpression().copy(rebindings), getSubscript().copy(rebindings));
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
        return EVALUATE_METHOD;
    }

    /**
     * Compare two expressions to see if they are equal
     *
     * @param other the other expression
     * @return true if the expressions are equivalent
     */

    public boolean equals(Object other) {
        return other instanceof SubscriptExpression &&
                getBaseExpression().isEqual(((SubscriptExpression) other).getBaseExpression()) &&
                getSubscript().isEqual(((SubscriptExpression) other).getSubscript());
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
        return "SubscriptExpression";
    }

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForItem().eval(context);
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
        return "subscript";
    }



    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("subscript", this);
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
        return new SubscriptExprElaborator();
    }

    /**
     * An elaborator for a "subscript" expression, typically written as {@code X[$n]} where {@code $n} is an
     * integer or a numeric expression.
     */

    public static class SubscriptExprElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            final SubscriptExpression expr = (SubscriptExpression) getExpression();
            final PullEvaluator baseEval = expr.getBaseExpression().makeElaborator().elaborateForPull();
            final ItemEvaluator indexEval = expr.getSubscriptExpression().makeElaborator().elaborateForItem();
            return context -> {
                NumericValue index = (NumericValue) indexEval.eval(context);
                if (index == null) {
                    return null;
                }
                int intIndex = index.asSubscript();
                if (intIndex != -1) {
                    SequenceIterator iter = baseEval.iterate(context);
                    return getItemAt(iter, intIndex);
                } else {
                    // there is no item at the required position
                    return null;
                }
            };
        }

    }
}

