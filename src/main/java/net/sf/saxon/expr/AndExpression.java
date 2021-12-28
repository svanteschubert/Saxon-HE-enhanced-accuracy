////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.BooleanValue;

import java.util.Collection;

public class AndExpression extends BooleanExpression {

    /**
     * Construct a boolean AND expression
     *
     * @param p1 the first operand
     * @param p2 the second operand
     */

    public AndExpression(Expression p1, Expression p2) {
        super(p1, Token.AND, p2);
    }

    @Override
    protected Expression preEvaluate() {
        // If the value can be determined from knowledge of one operand, precompute the result

        if (Literal.isConstantBoolean(getLhsExpression(), false) || Literal.isConstantBoolean(getRhsExpression(), false)) {
            // A and false() => false()
            // false() and B => false()
            return Literal.makeLiteral(BooleanValue.FALSE, this);
        } else if (Literal.hasEffectiveBooleanValue(getLhsExpression(), true)) {
            // true() and B => B
            return forceToBoolean(getRhsExpression());
        } else if (Literal.hasEffectiveBooleanValue(getRhsExpression(), true)) {
            // A and true() => A
            return forceToBoolean(getLhsExpression());
        } else {
            return this;
        }

    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
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

        Expression t = super.optimize(visitor, contextInfo);
        if (t != this) {
            return t;
        }

        // Rewrite (A and B) as (if (A) then B else false()). The benefit of this is that when B is a recursive
        // function call, it is treated as a tail call (test qxmp290). To avoid disrupting other optimizations
        // of "and" expressions (specifically, where clauses in FLWOR expressions), do this ONLY if B is a user
        // function call (we can't tell if it's recursive), and it's not in a loop.

        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        if (    getRhsExpression() instanceof UserFunctionCall &&
                th.isSubType(getRhsExpression().getItemType(), BuiltInAtomicType.BOOLEAN) &&
                !ExpressionTool.isLoopingSubexpression(this, null)) {
            Expression cond = Choose.makeConditional(
                    getLhsExpression(), getRhsExpression(), Literal.makeLiteral(BooleanValue.FALSE, this));
            ExpressionTool.copyLocationInfo(this, cond);
            return cond;
        }
        return this;
    }

    @Override
    public double getCost() {
        // Assume the RHS will be evaluated 50% of the time
        return getLhsExpression().getCost() + getRhsExpression().getCost() / 2;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be rebound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        AndExpression a2 = new AndExpression(getLhsExpression().copy(rebindings), getRhsExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, a2);
        return a2;
    }


    /**
     * Return the negation of this boolean expression, that is, an expression that returns true
     * when this expression returns false, and vice versa
     *
     * @return the negation of this expression
     */

    @Override
    public Expression negate() {
        // Apply de Morgan's laws
        // not(A and B) ==> not(A) or not(B)
        Expression not0 = SystemFunction.makeCall("not", getRetainedStaticContext(), getLhsExpression());
        Expression not1 = SystemFunction.makeCall("not", getRetainedStaticContext(), getRhsExpression());
        return new OrExpression(not0, not1);

    }

    /**
     * Get the element name used to identify this expression in exported expression format
     *
     * @return the element name used to identify this expression
     */
    @Override
    protected String tag() {
        return "and";
    }

    /**
     * Evaluate as a boolean.
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext c) throws XPathException {
        return getLhsExpression().effectiveBooleanValue(c) && getRhsExpression().effectiveBooleanValue(c);
    }

    /**
     * Generate an 'and' tree over a set of expressions
     * @param exprs the expressions to be "and'ed" together
     * @return the root of the new expression tree
     */
    public static Expression distribute(Collection<Expression> exprs) {
        Expression result = null;
        if(exprs != null) {
            boolean first = true;
            for(Expression e: exprs)   {
                if(first){
                    first = false;
                    result = e;
                } else  {
                    result = new AndExpression(result,e);
                }
            }
        }
        return result;
    }
}

