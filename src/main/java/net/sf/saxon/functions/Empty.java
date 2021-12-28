////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.value.BooleanValue;


/**
 * Implementation of the fn:empty function
 */
public class Empty extends Aggregate {

    @Override
    public Expression makeOptimizedFunctionCall(
        ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, final Expression... arguments) throws XPathException {

        // See if we can deduce the answer from the cardinality
        int c = arguments[0].getCardinality();
        if (c == StaticProperty.ALLOWS_ONE_OR_MORE) {
            return Literal.makeLiteral(BooleanValue.FALSE, arguments[0]);
        } else if (c == StaticProperty.ALLOWS_ZERO) {
            return Literal.makeLiteral(BooleanValue.TRUE, arguments[0]);
        }

        // Rewrite
        //    empty(A|B) => empty(A) and empty(B)
        if (arguments[0] instanceof VennExpression && !visitor.isOptimizeForStreaming()) {
            VennExpression v = (VennExpression) arguments[0];
            if (v.getOperator() == Token.UNION) {
                Expression e0 = SystemFunction.makeCall("empty", getRetainedStaticContext(), v.getLhsExpression());
                Expression e1 = SystemFunction.makeCall("empty", getRetainedStaticContext(), v.getRhsExpression());
                return new AndExpression(e0, e1).optimize(visitor, contextInfo);
            }
        }
        return null;
    }


    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        return BooleanValue.get(empty(arguments[0].iterate()));
    }

    private static boolean empty(SequenceIterator iter) throws XPathException {
        boolean result;
        if (iter.getProperties().contains(SequenceIterator.Property.LOOKAHEAD)) {
            result = !((LookaheadIterator) iter).hasNext();
        } else {
            result = iter.next() == null;
        }
        iter.close();
        return result;
    }


    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        return new SystemFunctionCall(this, arguments) {

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
             * @throws net.sf.saxon.trans.XPathException if an error is discovered during this phase
             *                                           (typically a type error)
             */

    /*@NotNull*/
            @Override
            public Expression optimize(/*@NotNull*/ ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
                Expression e2 = super.optimize(visitor, contextInfo);
                if (e2 != this) {
                    return e2;
                }
                // See if we can deduce the answer from the cardinality
                int c = getArg(0).getCardinality();
                if (c == StaticProperty.ALLOWS_ONE_OR_MORE) {
                    return Literal.makeLiteral(BooleanValue.FALSE, e2);
                } else if (c == StaticProperty.ALLOWS_ZERO) {
                    return Literal.makeLiteral(BooleanValue.TRUE, e2);
                }

                // Don't sort the argument
                setArg(0, getArg(0).unordered(false, visitor.isOptimizeForStreaming()));

                // Rewrite
                //    empty(A|B) => empty(A) and empty(B)
                if (getArg(0) instanceof VennExpression) {
                    VennExpression v = (VennExpression) getArg(0);
                    if (v.getOperator() == Token.UNION && !visitor.isOptimizeForStreaming()) {
                        Expression e0 = SystemFunction.makeCall("empty", getRetainedStaticContext(), v.getLhsExpression());
                        Expression e1 = SystemFunction.makeCall("empty", getRetainedStaticContext(), v.getRhsExpression());
                        return new AndExpression(e0, e1).optimize(visitor, contextInfo);
                    }
                }
                return this;
            }

            /**
             * Evaluate the function
             */

            @Override
            public BooleanValue evaluateItem(XPathContext context) throws XPathException {
                return BooleanValue.get(effectiveBooleanValue(context));
            }

            /**
             * Evaluate the function in a boolean context
             */

            @Override
            public boolean effectiveBooleanValue(XPathContext c) throws XPathException {
                SequenceIterator iter = getArg(0).iterate(c);
                boolean result;
                if (iter.getProperties().contains(SequenceIterator.Property.LOOKAHEAD)) {
                    result = !((LookaheadIterator) iter).hasNext();
                } else {
                    result = iter.next() == null;
                }
                iter.close();
                return result;
            }

            @Override
            public int getNetCost() {
                return 0;
            }
        };
    }

    @Override
    public String getCompilerName() {
        return "EmptyCompiler";
    }

    @Override
    public String getStreamerName() {
        return "Empty";
    }


}

