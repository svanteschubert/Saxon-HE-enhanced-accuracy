////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;

/**
 * A compiled xsl:sequence instruction. Used only when strict streamability analysis is
 * required; the instruction differs from its operand only by being marked as an instruction,
 * which is sometimes relevant for streamability analysis. Used for xsl:sequence and also
 * for xsl:map and xsl:map-entry; and also to wrap expressions in text value templates.
 */
public class SequenceInstr extends UnaryExpression {


    /**
     * Create the instruction
     */
    public SequenceInstr(Expression base) {
        super(base);
    }

    /**
     * Ask whether this expression is an instruction. In XSLT streamability analysis this
     * is used to distinguish constructs corresponding to XSLT instructions from other constructs.
     *
     * @return true if this construct originates as an XSLT instruction
     */
    @Override
    public boolean isInstruction() {
        return true;
    }

    /**
     * Get the usage (in terms of streamability analysis) of the single operand
     *
     * @return the operand usage
     */
    @Override
    protected OperandRole getOperandRole() {
        return new OperandRole(0, OperandUsage.TRANSMISSION);
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation simplifies its operands.
     *
     * @return the simplified expression (or the original if unchanged, or if modified in-situ)
     * @throws XPathException if an error is discovered during expression
     *                        rewriting
     */
//    @Override
//    public Expression simplify() throws XPathException {
//        return getBaseExpression();
//    }

    /**
     * Type-check the expression. Default implementation for unary operators that accept
     * any kind of operand
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().typeCheck(visitor, contextInfo);
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor     an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                    The parameter is set to null if it is known statically that the context item will be undefined.
     *                    If the type of the context item is not known statically, the argument is set to
     *                    {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().optimize(visitor, contextInfo);
        return this;
    }


    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        return new SequenceInstr(getBaseExpression().copy(rebindings));
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
        return getBaseExpression().getImplementationMethod();
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
        return "sequence";
    }

    /**
     * Process the instruction, without returning any tail calls
     *
     *
     * @param output the destination for the result
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     * @throws XPathException if a dynamic error occurs
     */
    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        getBaseExpression().process(output, context);
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
     *                                           expression
     */
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return getBaseExpression().iterate(context);
    }



    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        getBaseExpression().export(out);
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {

        return "SequenceInstr";
    }
}

