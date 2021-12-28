////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.BooleanFn;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.BooleanValue;

import java.util.List;


/**
 * Boolean expression: two truth values combined using AND or OR.
 */

public abstract class BooleanExpression extends BinaryExpression implements Negatable {

    /**
     * Construct a boolean expression
     *
     * @param p1       the first operand
     * @param operator one of {@link Token#AND} or {@link Token#OR}
     * @param p2       the second operand
     */

    public BooleanExpression(Expression p1, int operator, Expression p2) {
        super(p1, operator, p2);
    }


    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return Token.tokens[getOperator()] + "-expression";
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        getLhs().typeCheck(visitor, contextInfo);
        getRhs().typeCheck(visitor, contextInfo);

        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        XPathException err0 = TypeChecker.ebvError(getLhsExpression(), th);
        if (err0 != null) {
            err0.setLocator(getLocation());
            throw err0;
        }
        XPathException err1 = TypeChecker.ebvError(getRhsExpression(), th);
        if (err1 != null) {
            err1.setLocator(getLocation());
            throw err1;
        }
        // Precompute the EBV of any constant operand
        if (getLhsExpression() instanceof Literal && !(((Literal) getLhsExpression()).getValue() instanceof BooleanValue)) {
            setLhsExpression(Literal.makeLiteral(
                    BooleanValue.get(getLhsExpression().effectiveBooleanValue(visitor.makeDynamicContext())), this));
        }
        if (getRhsExpression() instanceof Literal && !(((Literal) getRhsExpression()).getValue() instanceof BooleanValue)) {
            setRhsExpression(Literal.makeLiteral(
                    BooleanValue.get(getRhsExpression().effectiveBooleanValue(visitor.makeDynamicContext())), this));
        }
        return preEvaluate();
    }

    /**
     * Determine the static cardinality. Returns [1..1]
     */

    @Override
    public int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
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

        optimizeChildren(visitor, contextItemType);

        boolean forStreaming = visitor.isOptimizeForStreaming();
        setLhsExpression(ExpressionTool.unsortedIfHomogeneous(getLhsExpression(), forStreaming));
        setRhsExpression(ExpressionTool.unsortedIfHomogeneous(getRhsExpression(), forStreaming));

        Expression op0 = BooleanFn.rewriteEffectiveBooleanValue(getLhsExpression(), visitor, contextItemType);
        if (op0 != null) {
            setLhsExpression(op0);
        }
        Expression op1 = BooleanFn.rewriteEffectiveBooleanValue(getRhsExpression(), visitor, contextItemType);
        if (op1 != null) {
            setRhsExpression(op1);
        }
        return preEvaluate();
    }

    /**
     * Evaluate the expression statically if either or both operands are literals. For example,
     * (true() or X) returns true().
     * @return a boolean literal if the expression can be evaluated now, or the original expression
     * otherwise.
     */

    protected abstract Expression preEvaluate();

    protected Expression forceToBoolean(Expression in) {
        if (in.getItemType() == BuiltInAtomicType.BOOLEAN && in.getCardinality() == StaticProperty.ALLOWS_ONE) {
            return in;
        } else {
            return SystemFunction.makeCall("boolean", getRetainedStaticContext(), in);
        }
    }

    /**
     * Check whether this specific instance of the expression is negatable
     *
     * @return true if it is
     * @param th the type hierarchy, if needed
     */

    @Override
    public boolean isNegatable(TypeHierarchy th) {
        return true;
    }

    /**
     * Return the negation of this boolean expression, that is, an expression that returns true
     * when this expression returns false, and vice versa
     *
     * @return the negation of this expression
     */

    @Override
    public abstract Expression negate();

    /**
     * Evaluate the expression
     */

    @Override
    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        return BooleanValue.get(effectiveBooleanValue(context));
    }

    /**
     * Evaluate as a boolean.
     */

    @Override
    public abstract boolean effectiveBooleanValue(XPathContext c) throws XPathException;

    /**
     * Determine the data type of the expression
     *
     * @return BuiltInAtomicType.BOOLEAN
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return BuiltInAtomicType.BOOLEAN;
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType static information about the context item
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return UType.BOOLEAN;
    }

    /**
     * Construct a list containing the "anded" subexpressions of an expression:
     * if the expression is (A and B and C), this returns (A, B, C).
     *
     * @param exp  the expression to be decomposed
     * @param list the list to which the subexpressions are to be added.
     */

    public static void listAndComponents(Expression exp, List<Expression> list) {
        if (exp instanceof BooleanExpression && ((BooleanExpression) exp).getOperator() == Token.AND) {
            for (Operand o : exp.operands()) {
                listAndComponents(o.getChildExpression(), list);
            }
        } else {
            list.add(exp);
        }
    }

    @Override
    protected OperandRole getOperandRole(int arg) {
        return OperandRole.INSPECT;
    }


}

