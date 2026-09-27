////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
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
import net.sf.saxon.transpile.CSharpSimpleEnum;
import net.sf.saxon.value.*;

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
            return EmptySequence.INSTANCE;
        }

        int scaleRnd = 0;
        RoundingRule roundingRule = RoundingRule.HALF_TO_CEILING;
        if (arguments.length >= 2) {
            NumericValue scaleVal = (NumericValue) arguments[1].head();
            scaleRnd = scaleVal == null ? 0 : (int) scaleVal.longValue();
        }
        if (arguments.length >= 3) {
            StringValue rounding = (StringValue) arguments[2].head();
            roundingRule = rounding == null
                    ? RoundingRule.HALF_TO_CEILING
                    : getRoundingRule(rounding.getStringValue());
        }
        if (roundingRule == RoundingRule.HALF_TO_CEILING) {
            return val0.round(scaleRnd);
        } else {
            return val0.round(scaleRnd, roundingRule);
        }


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
            } else if (fnc.getArity() == 2) {
                final ItemEvaluator scaleArg = fnc.getArg(1).makeElaborator().elaborateForItem();
                return context -> {
                    NumericValue result = (NumericValue) argEval.eval(context);
                    if (result == null) {
                        return null;
                    }
                    IntegerValue scaleArgVal = ((IntegerValue) scaleArg.eval(context));
                    if (scaleArgVal == null) {
                        return result.round(0);
                    }
                    int scale = (int) scaleArgVal.longValue();
                    return result.round(scale);
                };
            } else {
                final ItemEvaluator scaleArg = fnc.getArg(1).makeElaborator().elaborateForItem();
                final ItemEvaluator modeArg = fnc.getArg(2).makeElaborator().elaborateForItem();
                return context -> {
                    NumericValue result = (NumericValue) argEval.eval(context);
                    if (result == null) {
                        return null;
                    }
                    IntegerValue scaleArgVal = ((IntegerValue) scaleArg.eval(context));
                    int scale = scaleArgVal == null ? 0 : (int) scaleArgVal.longValue();
                    StringValue midpointModeVal = ((StringValue) modeArg.eval(context));
                    RoundingRule mode = midpointModeVal == null
                            ? RoundingRule.HALF_TO_CEILING
                            : getRoundingRule(midpointModeVal.getStringValue());
                    if (mode == RoundingRule.HALF_TO_CEILING) {
                        return result.round(scale);
                    } else {
                        return result.round(scale, mode);
                    }
                };
            }

        }


    }

    public static RoundingRule getRoundingRule(String s) {
        switch (s) {
            case "toward-zero":
                return RoundingRule.TOWARD_ZERO;
            case "away-from-zero":
                return RoundingRule.AWAY_FROM_ZERO;
            case "ceiling":
                return RoundingRule.CEILING;
            case "floor":
                return RoundingRule.FLOOR;
            case "half-toward-zero":
                return RoundingRule.HALF_TOWARD_ZERO;
            case "half-away-from-zero":
                return RoundingRule.HALF_AWAY_FROM_ZERO;
            case "half-to-ceiling":
                return RoundingRule.HALF_TO_CEILING;
            case "half-to-floor":
                return RoundingRule.HALF_TO_FLOOR;
            case "half-to-even":
                return RoundingRule.HALF_TO_EVEN;
            default:
                // Should have been checked by now
                throw new IllegalArgumentException("Invalid rounding mode " + s);

        }
    }

    @CSharpSimpleEnum
    public enum RoundingRule {
        FLOOR, CEILING, TOWARD_ZERO, AWAY_FROM_ZERO,
        HALF_TO_FLOOR, HALF_TO_CEILING, HALF_TOWARD_ZERO, HALF_AWAY_FROM_ZERO, HALF_TO_EVEN
    }
}

