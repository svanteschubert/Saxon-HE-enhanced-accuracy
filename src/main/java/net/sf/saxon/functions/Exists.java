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
import net.sf.saxon.value.Cardinality;


/**
 * Implementation of the fn:exists function
 */
public class Exists extends Aggregate {

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        return new SystemFunctionCall(this, arguments) {

            @Override
            public Expression optimize(/*@NotNull*/ ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
                Expression e2 = super.optimize(visitor, contextInfo);
                if (e2 != this) {
                    return e2;
                }
                // See if we can deduce the answer from the cardinality
                int c = getArg(0).getCardinality();
                if (!Cardinality.allowsZero(c)) {
                    return Literal.makeLiteral(BooleanValue.TRUE, e2);
                } else if (c == StaticProperty.ALLOWS_ZERO) {
                    return Literal.makeLiteral(BooleanValue.FALSE, e2);
                }

                // Don't sort the argument
                setArg(0, getArg(0).unordered(false, visitor.isOptimizeForStreaming()));

                // Rewrite
                //    exists(A|B) => exists(A) or exists(B)
                if (getArg(0) instanceof VennExpression && !visitor.isOptimizeForStreaming()) {
                    VennExpression v = (VennExpression) getArg(0);
                    if (v.getOperator() == Token.UNION) {
                        Expression e0 = SystemFunction.makeCall("exists", getRetainedStaticContext(), v.getLhsExpression());
                        Expression e1 = SystemFunction.makeCall("exists", getRetainedStaticContext(), v.getRhsExpression());
                        return new OrExpression(e0, e1).optimize(visitor, contextInfo);
                    }
                }
                return this;
            }

            @Override
            public boolean effectiveBooleanValue(XPathContext c) throws XPathException {
                try {
                    boolean result;
                    SequenceIterator iter = getArg(0).iterate(c);
                    if (iter.getProperties().contains(SequenceIterator.Property.LOOKAHEAD)) {
                        result = ((LookaheadIterator) iter).hasNext();
                    } else {
                        result = iter.next() != null;
                    }
                    iter.close();
                    return result;
                } catch (XPathException e) {
                    e.maybeSetLocation(getLocation());
                    e.maybeSetContext(c);
                    throw e;
                }
            }

            @Override
            public BooleanValue evaluateItem(XPathContext context) throws XPathException {
                return BooleanValue.get(effectiveBooleanValue(context));
            }

            @Override
            public int getNetCost() {
                return 0;
            }
        };
    }


    @Override
    public Expression makeOptimizedFunctionCall(
            ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, final Expression... arguments) throws XPathException {

        // See if we can deduce the answer from the cardinality
        int c = arguments[0].getCardinality();
        if (c == StaticProperty.ALLOWS_ONE_OR_MORE) {
            return Literal.makeLiteral(BooleanValue.TRUE, arguments[0]);
        } else if (c == StaticProperty.ALLOWS_ZERO) {
            return Literal.makeLiteral(BooleanValue.FALSE, arguments[0]);
        }

        // Don't sort the argument
        arguments[0] = arguments[0].unordered(false, visitor.isOptimizeForStreaming());

        // Rewrite
        //    exists(A|B) => exists(A) or exists(B)
        if (arguments[0] instanceof VennExpression && !visitor.isOptimizeForStreaming()) {
            VennExpression v = (VennExpression) arguments[0];
            if (v.getOperator() == Token.UNION) {
                Expression e0 = SystemFunction.makeCall("exists", getRetainedStaticContext(), v.getLhsExpression());
                Expression e1 = SystemFunction.makeCall("exists", getRetainedStaticContext(), v.getRhsExpression());
                return new OrExpression(e0, e1).optimize(visitor, contextInfo);
            }
        }
        return null;
    }



    private static boolean exists(SequenceIterator iter) throws XPathException {
        boolean result;
        if (iter.getProperties().contains(SequenceIterator.Property.LOOKAHEAD)) {
            result = ((LookaheadIterator) iter).hasNext();
        } else {
            result = iter.next() != null;
        }
        iter.close();
        return result;
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        return BooleanValue.get(exists(arguments[0].iterate()));
    }

    @Override
    public String getCompilerName() {
        return "ExistsCompiler";
    }

    @Override
    public String getStreamerName() {
        return "Exists";
    }

}

