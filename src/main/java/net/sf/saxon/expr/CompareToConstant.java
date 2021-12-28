////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.BooleanValue;

/**
 * This class implements a comparison of a computed value to a literal constant using one of the operators
 * eq, ne, lt, gt, le, ge. The semantics are identical to ValueComparison, but this is a fast path for an
 * important common case. Different subclasses handle different types of constant.
 */

public abstract class CompareToConstant extends UnaryExpression implements ComparisonExpression {

    protected int operator;

    public CompareToConstant(Expression p0) {
        super(p0);
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.SINGLE_ATOMIC;
    }

    @Override
    public Expression getLhsExpression() {
        return getBaseExpression();
    }

    @Override
    public Operand getLhs() {
        return getOperand();
    }

    @Override
    public abstract Expression getRhsExpression();

    @Override
    public Operand getRhs() {
        return new Operand(this, getRhsExpression(), OperandRole.SINGLE_ATOMIC);
    }

    /**
     * Get the comparison operator
     *
     * @return one of {@link Token#FEQ}, {@link Token#FNE}, {@link Token#FGE},
     *         {@link Token#FGT}, {@link Token#FLE}, {@link Token#FLT}
     */

    public int getComparisonOperator() {
        return operator;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the value {@link #EVALUATE_METHOD}
     */

    @Override
    public int getImplementationMethod() {
        return EVALUATE_METHOD;
    }

    @Override
    public int computeSpecialProperties() {
        return StaticProperty.NO_NODES_NEWLY_CREATED;
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
     *         expression; or null to indicate that the result is an empty
     *         sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    @Override
    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        return BooleanValue.get(effectiveBooleanValue(context));
    }

    /**
     * Perform optimisation of an expression and its subexpressions. This is the third and final
     * phase of static optimization.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         the expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().optimize(visitor, contextInfo);
        if (getLhsExpression() instanceof Literal) {
            return Literal.makeLiteral(BooleanValue.get(effectiveBooleanValue(null)), this);
        }
        return this;
    }

    /**
     * Determine the data type of the expression, if possible. All expression return
     * sequences, in general; this method determines the type of the items within the
     * sequence, assuming that (a) this is known in advance, and (b) it is the same for
     * all items in the sequence.
     * <p>This method should always return a result, though it may be the best approximation
     * that is available at the time.</p>
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER,
     *         Type.NODE, or Type.ITEM (meaning not known at compile time)
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return BuiltInAtomicType.BOOLEAN;
    }

    /**
     * Get the primitive (singleton) operator used: one of Token.FEQ, Token.FNE, Token.FLT, Token.FGT,
     * Token.FLE, Token.FGE
     */

    @Override
    public int getSingletonOperator() {
        return operator;
    }

    /**
     * Determine whether untyped atomic values should be converted to the type of the other operand
     *
     * @return true if untyped values should be converted to the type of the other operand, false if they
     *         should be converted to strings.
     */

    @Override
    public boolean convertsUntypedToOther() {
        return true;
    }

    /**
     * Interpret the result of the comparison
     * @param c -1, 0, or +1 depending how the operands compare
     * @return true or false depending on the operator in use
     */

    boolean interpretComparisonResult(int c) {
        switch (operator) {
            case Token.FEQ:
                return c == 0;
            case Token.FNE:
                return c != 0;
            case Token.FGT:
                return c > 0;
            case Token.FLT:
                return c < 0;
            case Token.FGE:
                return c >= 0;
            case Token.FLE:
                return c <= 0;
            default:
                throw new UnsupportedOperationException("Unknown operator " + operator);
        }
    }
}

