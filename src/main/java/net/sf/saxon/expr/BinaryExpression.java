////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.jiter.PairIterator;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.value.Cardinality;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Binary Expression: a numeric or boolean expression consisting of the
 * two operands and an operator
 */

public abstract class BinaryExpression extends Expression {

    private Operand lhs;
    private Operand rhs;
    protected int operator;       // represented by the token number from class Tokenizer

    /**
     * Create a binary expression identifying the two operands and the operator
     *
     * @param p0 the left-hand operand
     * @param op the operator, as a token returned by the Tokenizer (e.g. Token.AND)
     * @param p1 the right-hand operand
     */

    public BinaryExpression(Expression p0, int op, Expression p1) {
        operator = op;
//        p0.verifyParentPointers();
//        p1.verifyParentPointers();
        lhs = new Operand(this, p0, getOperandRole(0));
        rhs = new Operand(this, p1, getOperandRole(1));
        adoptChildExpression(p0);
        adoptChildExpression(p1);
    }

    @Override
    final public Iterable<Operand> operands() {
        // For .NEU - don't use a lambda expression here
        return new Iterable<Operand>() {
            @Override
            public Iterator<Operand> iterator() {
                return new PairIterator<>(lhs, rhs);
            }
        };
    }


    /**
     * Get the operand role
     * @param arg which argument: 0 for the lhs, 1 for the rhs
     * @return the operand role
     */

    protected OperandRole getOperandRole(int arg) {
        return OperandRole.SINGLE_ATOMIC;
    }

    /**
     * Get the left-hand operand
     * @return the left-hand operand
     */

    public Operand getLhs() {
        return lhs;
    }

    /**
     * Get the left-hand operand expression
     * @return the left-hand operand child expression
     */

    public final Expression getLhsExpression() {
        return lhs.getChildExpression();
    }

    /**
     * Set the left-hand operand expression
     * @param child the left-hand operand expression
     */

    public void setLhsExpression(Expression child) {
        lhs.setChildExpression(child);
    }

    /**
     * Get the right-hand operand
     * @return the right-hand operand
     */

    public Operand getRhs() {
        return rhs;
    }

    /**
     * Get the right-hand operand expression
     * @return the right-hand operand expression
     */

    public final Expression getRhsExpression() {
        return rhs.getChildExpression();
    }

    /**
     * Set the right-hand operand expression
     * @param child the right-hand operand expression
     */

    public void setRhsExpression(Expression child) {
        rhs.setChildExpression(child);
    }

    /**
     * Type-check the expression. Default implementation for binary operators that accept
     * any kind of operand
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        resetLocalStaticProperties();
        lhs.typeCheck(visitor, contextInfo);
        rhs.typeCheck(visitor, contextInfo);

        // if both operands are known, pre-evaluate the expression
        try {
            if ((getLhsExpression() instanceof Literal) && (getRhsExpression() instanceof Literal)) {
                GroundedValue v = evaluateItem(visitor.getStaticContext().makeEarlyEvaluationContext()).materialize();
                return Literal.makeLiteral(v, this);
            }
        } catch (XPathException err) {
            // if early evaluation fails, suppress the error: the value might
            // not be needed at run-time
        }
        return this;
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
        lhs.optimize(visitor, contextItemType);
        rhs.optimize(visitor, contextItemType);

        // if both operands are known, pre-evaluate the expression
        try {
            Optimizer opt = visitor.obtainOptimizer();
            if (opt.isOptionSet(OptimizerOptions.CONSTANT_FOLDING) &&
                    (getLhsExpression() instanceof Literal) && (getRhsExpression() instanceof Literal)) {
                Item item = evaluateItem(visitor.getStaticContext().makeEarlyEvaluationContext());
                if (item != null) {
                    GroundedValue v = item.materialize();
                    return Literal.makeLiteral(v, this);
                }
            }
        } catch (XPathException err) {
            // if early evaluation fails, suppress the error: the value might
            // not be needed at run-time
        }
        return this;
    }

    /**
     * Mark an expression as being "flattened". This is a collective term that includes extracting the
     * string value or typed value, or operations such as simple value construction that concatenate text
     * nodes before atomizing. The implication of all of these is that although the expression might
     * return nodes, the identity of the nodes has no significance. This is called during type checking
     * of the parent expression.
     *
     * @param flattened set to true if the result of the expression is atomized or otherwise turned into
     *                  an atomic value
     */

    @Override
    public void setFlattened(boolean flattened) {
        getLhsExpression().setFlattened(flattened);
        getRhsExpression().setFlattened(flattened);
    }

    /**
     * Get the operator
     *
     * @return the operator, for example {@link Token#PLUS}
     */

    public int getOperator() {
        return operator;
    }

    /**
     * Determine the static cardinality. Default implementation returns [0..1] if either operand
     * can be empty, or [1..1] otherwise, provided that the arguments are of atomic type. This
     * caveat is necessary because the method can be called before type-checking, and a node
     * or array with cardinality [1..n] might be atomized to an empty sequence.
     */

    @Override
    public int computeCardinality() {
        Expression lhs = getLhsExpression();
        Expression rhs = getRhsExpression();
        if (!Cardinality.allowsZero(lhs.getCardinality()) &&
                lhs.getItemType() instanceof AtomicType &&
                !Cardinality.allowsZero(rhs.getCardinality()) &&
                rhs.getItemType() instanceof AtomicType) {
            return StaticProperty.EXACTLY_ONE;
        } else {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        }
    }

    /**
     * Determine the special properties of this expression
     *
     * @return {@link StaticProperty#NO_NODES_NEWLY_CREATED}. This is overridden
     *         for some subclasses.
     */

    @Override
    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        return p | StaticProperty.NO_NODES_NEWLY_CREATED;
    }

    /**
     * Determine whether a binary operator is commutative, that is, A op B = B op A.
     *
     * @param operator the operator, for example {@link Token#PLUS}
     * @return true if the operator is commutative
     */

    protected static boolean isCommutative(int operator) {
        return operator == Token.AND ||
                operator == Token.OR ||
                operator == Token.UNION ||
                operator == Token.INTERSECT ||
                operator == Token.PLUS ||
                operator == Token.MULT ||
                operator == Token.EQUALS ||
                operator == Token.FEQ ||
                operator == Token.NE ||
                operator == Token.FNE;
    }

    /**
     * Determine whether an operator is associative, that is, ((a^b)^c) = (a^(b^c))
     *
     * @param operator the operator, for example {@link Token#PLUS}
     * @return true if the operator is associative
     */

    protected static boolean isAssociative(int operator) {
        return operator == Token.AND ||
                operator == Token.OR ||
                operator == Token.UNION ||
                operator == Token.INTERSECT ||
                operator == Token.PLUS ||
                operator == Token.MULT;
    }

    /**
     * Test if one operator is the inverse of another, so that (A op1 B) is
     * equivalent to (B op2 A). Commutative operators are the inverse of themselves
     * and are therefore not listed here.
     *
     * @param op1 the first operator
     * @param op2 the second operator
     * @return true if the operators are the inverse of each other
     */
    protected static boolean isInverse(int op1, int op2) {
        return op1 != op2 && op1 == Token.inverse(op2);
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
        return EVALUATE_METHOD | ITERATE_METHOD;
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        if (other instanceof BinaryExpression && hasCompatibleStaticContext((Expression)other)) {
            BinaryExpression b = (BinaryExpression) other;
            Expression lhs1 = getLhsExpression();
            Expression rhs1 = getRhsExpression();
            Expression lhs2 = b.getLhsExpression();
            Expression rhs2 = b.getRhsExpression();
            if (operator == b.operator) {
                if (lhs1.isEqual(lhs2) && rhs1.isEqual(rhs2)) {
                    return true;
                }
                if (isCommutative(operator) && lhs1.isEqual(rhs2) && rhs1.isEqual(lhs2)) {
                    return true;
                }
                if (isAssociative(operator) &&
                        pairwiseEqual(flattenExpression(new ArrayList<>(4)),
                                b.flattenExpression(new ArrayList<>(4)))) {
                    return true;
                }
            }
            return isInverse(operator, b.operator) && lhs1.isEqual(rhs2) && rhs1.isEqual(lhs2);
        }
        return false;
    }

    /**
     * Flatten an expression with respect to an associative operator: for example
     * the expression (a+b) + (c+d) becomes list(a,b,c,d), with the list in canonical
     * order (sorted by hashCode)
     *
     * @param list a list provided by the caller to contain the result
     * @return the list of expressions
     */

    private List<Expression> flattenExpression(List<Expression> list) {
        if (getLhsExpression() instanceof BinaryExpression &&
                ((BinaryExpression) getLhsExpression()).operator == operator) {
            ((BinaryExpression) getLhsExpression()).flattenExpression(list);
        } else {
            int h = getLhsExpression().hashCode();
            list.add(getLhsExpression());
            int i = list.size() - 1;
            while (i > 0 && h > list.get(i - 1).hashCode()) {
                list.set(i, list.get(i - 1));
                list.set(i - 1, getLhsExpression());
                i--;
            }
        }
        if (getRhsExpression() instanceof BinaryExpression &&
                ((BinaryExpression) getRhsExpression()).operator == operator) {
            ((BinaryExpression) getRhsExpression()).flattenExpression(list);
        } else {
            int h = getRhsExpression().hashCode();
            list.add(getRhsExpression());
            int i = list.size() - 1;
            while (i > 0 && h > list.get(i - 1).hashCode()) {
                list.set(i, list.get(i - 1));
                list.set(i - 1, getRhsExpression());
                i--;
            }
        }
        return list;
    }

    /**
     * Compare whether two lists of expressions are pairwise equal
     *
     * @param a the first list of expressions
     * @param b the second list of expressions
     * @return true if the two lists are equal
     */

    private boolean pairwiseEqual(List a, List b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get a hashCode for comparing two expressions. Note that this hashcode gives the same
     * result for (A op B) and for (B op A), whether or not the operator is commutative.
     */

    @Override
    public int computeHashCode() {
        // Ensure that an operator and its inverse get the same hash code,
        // so that (A lt B) has the same hash code as (B gt A)
        int op = Math.min(operator, Token.inverse(operator));
        return ("BinaryExpression " + op).hashCode()
                ^ getLhsExpression().hashCode()
                ^ getRhsExpression().hashCode();
    }

    /**
     * Represent the expression as a string. The resulting string will be a valid XPath 3.0 expression
     * with no dependencies on namespace bindings other than the binding of the prefix "xs" to the XML Schema
     * namespace.
     *
     * @return the expression as a string in XPath 3.0 syntax
     */

    public String toString() {
        return ExpressionTool.parenthesize(getLhsExpression()) +
                " " + displayOperator() + " " +
                ExpressionTool.parenthesize(getRhsExpression());
    }

    @Override
    public String toShortString() {
        return parenthesize(getLhsExpression()) + " " + displayOperator() + " " + parenthesize(getRhsExpression());
    }

    private String parenthesize(Expression operand) {
        String operandStr = operand.toShortString();
        if (operand instanceof BinaryExpression &&
                XPathParser.operatorPrecedence(((BinaryExpression) operand).operator) < XPathParser.operatorPrecedence(operator)) {
            operandStr = "(" + operandStr + ")";
        }
        return operandStr;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the output destination for the displayed expression tree
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement(tag(), this);
        out.emitAttribute("op", displayOperator());
        explainExtraAttributes(out);
        getLhsExpression().export(out);
        getRhsExpression().export(out);
        out.endElement();
    }

    /**
     * Get the element name used to identify this expression in exported expression format
     * @return the element name used to identify this expression
     */

    protected String tag() {
        return "operator";
    }

    /**
     * Add subclass-specific attributes to the expression tree explanation. Default implementation
     * does nothing; this is provided for subclasses to override.
     *
     * @param out the output destination for the displayed expression tree
     */

    protected void explainExtraAttributes(ExpressionPresenter out) {
    }

    /**
     * Display the operator used by this binary expression
     *
     * @return String representation of the operator (for diagnostic display only)
     */

    protected String displayOperator() {
        return Token.tokens[operator];
    }


}

