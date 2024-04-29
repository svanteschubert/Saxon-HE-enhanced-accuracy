////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.NumericValue;

/**
 * This class implements the fn:round() function
 */

public final class Round extends SystemFunction {

    /**
     * Determine the cardinality of the function.
     */

    @Override
    public int getCardinality(Expression[] arguments) {
        return arguments[0].getCardinality();
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
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        NumericValue val0 = (NumericValue) arguments[0].head();
        if (val0 == null) {
            return EmptySequence.getInstance();
        }

        int scaleRnd = 0;
        if (arguments.length == 2) {
            NumericValue scaleVal = (NumericValue) arguments[1].head();
            scaleRnd = (int) scaleVal.longValue();
        }
        return val0.round(scaleRnd);

    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new RoundElaborator();
    }


    public static class RoundElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final ItemEvaluator argEval = fnc.getArg(0).makeElaborator().elaborateForItem();
            final boolean nullable = Cardinality.allowsZero(fnc.getArg(0).getCardinality());

            if (fnc.getArity() == 1) {
                if (nullable) {
                    return context -> {
                        NumericValue result = (NumericValue) argEval.eval(context);
                        if (result == null) {
                            return null;
                        }
                        return result.round(0);
                    };
                } else {
                    return context -> ((NumericValue) argEval.eval(context)).round(0);
                }
            } else {
                final ItemEvaluator scaleArg = fnc.getArg(1).makeElaborator().elaborateForItem();
                return context -> {
                    NumericValue result = (NumericValue) argEval.eval(context);
                    if (result == null) {
                        return null;
                    }
                    int scale = (int) ((NumericValue) scaleArg.eval(context)).longValue();
                    return result.round(scale);
                };
            }

        }


    }
}

