////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.jiter.MonoIterator;
import net.sf.saxon.type.ItemType;
import java.util.Iterator;

/**
 * Unary Expression: an expression taking a single operand expression
 */

public abstract class UnaryExpression extends Expression {

    private Operand operand;

    public UnaryExpression(Expression p0) {
        operand = new Operand(this, p0, getOperandRole());
//        if (p0.getRetainedStaticContext() != null) {
//            setRetainedStaticContext(p0.getRetainedStaticContext());
//        }
        ExpressionTool.copyLocationInfo(p0, this);
    }

    /*@NotNull*/
    public Expression getBaseExpression() {
        return operand.getChildExpression();
    }

    public void setBaseExpression(Expression child) {
        operand.setChildExpression(child);
    }

    public Operand getOperand() {
        return operand;
    }

    @Override
    public Iterable<Operand> operands() {
        // For .NEU - don't use a lambda expression here
        return new Iterable<Operand>() {
            @Override
            public Iterator<Operand> iterator() {
                return new MonoIterator<>(operand);
            }
        };
    }

    /**
     * Get the usage (in terms of streamability analysis) of the single operand
     * @return the operand usage
     */

    protected abstract OperandRole getOperandRole();

    /**
     * Type-check the expression. Default implementation for unary operators that accept
     * any kind of operand
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        operand.typeCheck(visitor, contextInfo);

        // if the operand value is known, pre-evaluate the expression
        try {
            if (getBaseExpression() instanceof Literal) {
                Expression e2 = Literal.makeLiteral(
                        iterate(visitor.getStaticContext().makeEarlyEvaluationContext()).materialize(), this);
                ExpressionTool.copyLocationInfo(this, e2);
                return e2;
            }
            //return (Value)ExpressionTool.eagerEvaluate(this, env.makeEarlyEvaluationContext());
        } catch (Exception err) {
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
        operand.optimize(visitor, contextInfo);
        // if the operand value is known, pre-evaluate the expression
        Expression base = getBaseExpression();
        try {
            if (base instanceof Literal) {
                return Literal.makeLiteral(
                        iterate(visitor.getStaticContext().makeEarlyEvaluationContext()).materialize(), this);
            }
        } catch (XPathException err) {
            // if early evaluation fails, suppress the error: the value might
            // not be needed at run-time
        }
        return this;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    @Override
    public int computeSpecialProperties() {
        return getBaseExpression().getSpecialProperties();
    }

    /**
     * Determine the static cardinality. Default implementation returns the cardinality of the operand
     */

    @Override
    public int computeCardinality() {
        return getBaseExpression().getCardinality();
    }

    /**
     * Determine the data type of the expression, if possible. The default
     * implementation for unary expressions returns the item type of the operand
     *
     * @return the item type of the items in the result sequence, insofar as this
     *         is known statically.
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getBaseExpression().getItemType();
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(/*@Nullable*/ Object other) {
        return other != null && this.getClass().equals(other.getClass()) &&
                this.getBaseExpression().isEqual(((UnaryExpression) other).getBaseExpression());
    }

    /**
     * get HashCode for comparing two expressions. Note that this hashcode gives the same
     * result for (A op B) and for (B op A), whether or not the operator is commutative.
     */

    @Override
    public int computeHashCode() {
        return ("UnaryExpression " + getClass()).hashCode() ^ getBaseExpression().hashCode();
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */

    public String toString() {
        return getExpressionName() + "(" + getBaseExpression() + ")";
    }

    @Override
    public String toShortString() {
        return getExpressionName() + "(" + getBaseExpression().toShortString() + ")";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        String name = getExpressionName();
        if (name == null) {
            out.startElement("unaryOperator", this);
            String op = displayOperator(out.getConfiguration());
            if (op != null) {
                out.emitAttribute("op", op);
            }
        } else {
            out.startElement(name, this);
        }
        emitExtraAttributes(out);
        getBaseExpression().export(out);
        out.endElement();
    }

    protected void emitExtraAttributes(ExpressionPresenter out) {
        // default: no action
    }

    /**
     * Give a string representation of the operator for use in diagnostics
     *
     * @param config the Saxon configuration
     * @return the operator, as a string
     */

    /*@Nullable*/
    protected String displayOperator(Configuration config) {
        return null;
    }



}
