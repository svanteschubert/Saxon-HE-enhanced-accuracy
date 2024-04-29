////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.elab.BooleanEvaluator;
import net.sf.saxon.expr.elab.BooleanElaborator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;

public class OrExpression extends BooleanExpression {


    /**
     * Construct a boolean OR expression
     *
     * @param p1 the first operand
     * @param p2 the second operand
     */

    public OrExpression(Expression p1, Expression p2) {
        super(p1, Token.OR, p2);
    }

    @Override
    protected Expression preEvaluate() {
        if (Literal.hasEffectiveBooleanValue(getLhsExpression(), true) || Literal.hasEffectiveBooleanValue(getRhsExpression(), true)) {
            // A or true() => true()
            // true() or B => true()
            return Literal.makeLiteral(BooleanValue.TRUE, this);
        } else if (Literal.hasEffectiveBooleanValue(getLhsExpression(), false)) {
            // false() or B => B
            return forceToBoolean(getRhsExpression());
        } else if (Literal.hasEffectiveBooleanValue(getRhsExpression(), false)) {
            // A or false() => A
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
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {

        final Expression e = super.optimize(visitor, contextItemType);
        if (e != this) {
            return e;
        }

        // If this is a top-level or-expression then try to replace multiple branches with a general comparison
        if (!(getParentExpression() instanceof OrExpression)) {
            final Expression e2 = visitor.obtainOptimizer().tryGeneralComparison(visitor, contextItemType, this);
            if (e2 != null && e2 != this) {
                return e2;
            }
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
     * @param rebindings  variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        OrExpression exp = new OrExpression(getLhsExpression().copy(rebindings), getRhsExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
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
        // not(A or B) => not(A) and not(B)
        Expression not0 = SystemFunction.makeCall("not", getRetainedStaticContext(), getLhsExpression());
        Expression not1 = SystemFunction.makeCall("not", getRetainedStaticContext(), getRhsExpression());
        AndExpression result = new AndExpression(not0, not1);
        ExpressionTool.copyLocationInfo(this, result);
        return result;
    }

    /**
     * Get the element name used to identify this expression in exported expression format
     *
     * @return the element name used to identify this expression
     */
    @Override
    protected String tag() {
        return "or";
    }

    /**
     * Evaluate as a boolean.
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext c) throws XPathException {
        return getLhsExpression().effectiveBooleanValue(c) || getRhsExpression().effectiveBooleanValue(c);
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new OrElaborator();
    }

    /**
     * Elaborator for an "or" expression ({@code A or B})
     */

    public static class OrElaborator extends BooleanElaborator {

        public BooleanEvaluator elaborateForBoolean() {

            OrExpression expr = (OrExpression) getExpression();
            BooleanEvaluator eval0 = expr.getLhsExpression().makeElaborator().elaborateForBoolean();
            BooleanEvaluator eval1 = expr.getRhsExpression().makeElaborator().elaborateForBoolean();

            // Don't throw an error if either branch returns true.
            // See bug 5721. Conforms with the 4.0 rules for guarded expressions, even if the
            // operands are reordered
            return context -> {
                XPathException saved = null;
                try {
                    boolean b0 = eval0.eval(context);
                    if (b0) {
                        return true;
                    }
                } catch (UncheckedXPathException err) {
                    saved = err.getXPathException();
                } catch (XPathException err) {
                    saved = err;
                }
                boolean b1 = eval1.eval(context);
                if (b1) {
                    return true;
                }
                if (saved != null) {
                    throw saved;
                }
                return false;
            };
        }


    }
}

