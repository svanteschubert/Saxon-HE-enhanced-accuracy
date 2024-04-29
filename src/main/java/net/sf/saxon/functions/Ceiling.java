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
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.NumericValue;

/**
 * This class supports the ceiling() function
 */

public final class Ceiling extends ScalarSystemFunction {

    @Override
    public NumericValue evaluate(Item arg, XPathContext context) {
        return ((NumericValue)arg).ceiling();
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new CeilingElaborator();
    }

    public static class CeilingElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final ItemEvaluator argEval = fnc.getArg(0).makeElaborator().elaborateForItem();
            final boolean nullable = Cardinality.allowsZero(fnc.getArg(0).getCardinality());
            if (nullable) {
                return context -> {
                    NumericValue result = (NumericValue) argEval.eval(context);
                    if (result == null) {
                        return null;
                    }
                    return result.ceiling();
                };
            } else {
                return context -> ((NumericValue) argEval.eval(context)).ceiling();
            }

        }


    }

}

