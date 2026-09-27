////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.NumericValue;

/**
 * This class supports the round-to-half-even() function
 */

public final class RoundHalfToEven extends SystemFunction {

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
            return EmptySequence.INSTANCE;
        }

        int scale = 0;
        if (arguments.length == 2) {
            NumericValue scaleVal = (NumericValue) arguments[1].head();
            if (scaleVal != null) {
                if (scaleVal.compareTo(Integer.MAX_VALUE) > 0) {
                    return val0;
                } else if (scaleVal.compareTo(Integer.MIN_VALUE) < 0) {
                    scale = Integer.MIN_VALUE;
                } else {
                    scale = (int) scaleVal.longValue();
                }
            }
        }
        return val0.round(scale, Round.RoundingRule.HALF_TO_EVEN);
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new RoundHalfToEvenElaborator();
    }


    public static class RoundHalfToEvenElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final ItemEvaluator arg0eval = fnc.getArg(0).makeElaborator().elaborateForItem();
            final boolean nullable = Cardinality.allowsZero(fnc.getArg(0).getCardinality());

            if (fnc.getArity() == 1) {
                if (nullable) {
                    return context -> {
                        NumericValue result = (NumericValue) arg0eval.eval(context);
                        if (result == null) {
                            return null;
                        }
                        return result.round(0, Round.RoundingRule.HALF_TO_EVEN);
                    };
                } else {
                    return context -> ((NumericValue) arg0eval.eval(context)).round(0, Round.RoundingRule.HALF_TO_EVEN);
                }
            } else if (fnc.getArg(1) instanceof Literal && ((Literal)fnc.getArg(1)).getGroundedValue() instanceof NumericValue) {
                final NumericValue scaleVal = (NumericValue) ((Literal) fnc.getArg(1)).getGroundedValue();
                if (scaleVal.compareTo(Integer.MAX_VALUE) > 0) {
                    return arg0eval;
                } else {
                    try {
                        int scale = scaleVal.compareTo(Integer.MIN_VALUE) < 0 ? Integer.MIN_VALUE: (int) scaleVal.longValue();
                        return context -> {
                            NumericValue result = (NumericValue) arg0eval.eval(context);
                            if (result == null) {
                                return null;
                            }
                            return result.round(scale, Round.RoundingRule.HALF_TO_EVEN);
                        };
                    } catch (XPathException e) {
                        return context -> {
                            throw e;
                        };
                    }

                }
            } else {
                final ItemEvaluator scaleArg = fnc.getArg(1).makeElaborator().elaborateForItem();
                return context -> {
                    NumericValue result = (NumericValue) arg0eval.eval(context);
                    if (result == null) {
                        return null;
                    }
                    NumericValue scaleVal = (NumericValue) scaleArg.eval(context);
                    int scale = 0;
                    if (scaleVal != null) {
                        if (scaleVal.compareTo(Integer.MAX_VALUE) > 0) {
                            return result;
                        } else if (scaleVal.compareTo(Integer.MIN_VALUE) < 0) {
                            scale = Integer.MIN_VALUE;
                        } else {
                            scale = (int) scaleVal.longValue();
                        }
                    }
                    return result.round(scale, Round.RoundingRule.HALF_TO_EVEN);
                };
            }

        }


    }

}

