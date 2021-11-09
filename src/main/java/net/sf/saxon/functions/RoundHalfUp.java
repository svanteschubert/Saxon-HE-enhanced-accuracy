////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.One;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.NumericValue;

/**
 * This class supports the round-half-up() function
 */

public final class RoundHalfUp extends SystemFunction {

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
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        NumericValue val0 = (NumericValue) arguments[0].head();
        if (val0 == null) {
            return ZeroOrOne.empty();
        }

        int scale = 0;
        if (arguments.length == 2) {
            NumericValue scaleVal = (NumericValue) arguments[1].head();
            if (scaleVal.compareTo(Integer.MAX_VALUE) > 0) {
                return new ZeroOrOne(val0);
            } else if (scaleVal.compareTo(Integer.MIN_VALUE) < 0) {
                scale = Integer.MIN_VALUE;
            } else {
                scale = (int) scaleVal.longValue();
            }
        }
        return new One(val0.roundHalfUp(scale));
    }

    @Override
    public String getCompilerName() {
        return "RoundingCompiler";
    }

}

