////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.OperatorSymbol;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.Cardinality;


/**
 * Implementation of the fn:empty function
 */
public class Empty extends Aggregate implements ArityOneFunction {

    @Override
    public Expression makeOptimizedFunctionCall(
        ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, final Expression... arguments) throws XPathException {

        // See if we can deduce the answer from the cardinality
        int c = arguments[0].getCardinality();
        if (!Cardinality.allowsZero(c)) {
            return Literal.makeLiteral(BooleanValue.FALSE, arguments[0]);
        } else if (c == StaticProperty.ALLOWS_ZERO) {
            return Literal.makeLiteral(BooleanValue.TRUE, arguments[0]);
        }

        // Don't sort the argument
        Expression unorderedArg0 = arguments[0].unordered(false, visitor.isOptimizeForStreaming());
        if (unorderedArg0 != arguments[0]) {
            return makeFunctionCall(unorderedArg0);
        }

        // Rewrite
        //    empty(A|B) => empty(A) and empty(B)
        if (arguments[0] instanceof VennExpression && !visitor.isOptimizeForStreaming()) {
            VennExpression v = (VennExpression) arguments[0];
            if (v.getOperator() == OperatorSymbol.UNION) {
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

    /**
     * Call a function with one argument
     *
     * @param context the dynamic evaluation context
     * @param arg0    the first argument
     * @return the result of the function call
     */
    @Override
    public Sequence call1(XPathContext context, Sequence arg0) {
        return BooleanValue.get(empty(arg0.iterate()));
    }

    private static boolean empty(SequenceIterator iter) {
        boolean result;
        if (iter instanceof LookaheadIterator lit && lit.supportsHasNext()) {
            result = !lit.hasNext();
        } else {
            result = iter.next() == null;
        }
        iter.close();
        return result;
    }
    
    @Override
    public String getStreamerName() {
        return "Empty";
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new EmptyFnElaborator();
    }

    private static class EmptyFnElaborator extends BooleanElaborator {

        public BooleanEvaluator elaborateForBoolean() {
            SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            Expression arg = fnc.getArg(0);
            if (Cardinality.allowsMany(arg.getCardinality())) {
                PullEvaluator puller = arg.makeElaborator().elaborateForPull();
                return context -> empty(puller.iterate(context));
            } else {
                ItemEvaluator eval = arg.makeElaborator().elaborateForItem();
                return context -> eval.eval(context) == null;
            }
        }

    }
}

