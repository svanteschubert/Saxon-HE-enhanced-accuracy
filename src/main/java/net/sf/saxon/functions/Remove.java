////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.TailExpression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.hof.FilterFn;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntSet;
import net.sf.saxon.z.IntSingletonSet;

/**
 * The XPath 2.0 remove() function
 */

public class Remove extends SystemFunction {

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {

        if (Literal.isAtomic(arguments[1])) {
            Sequence index = ((Literal) arguments[1]).getGroundedValue();
            if (index instanceof IntegerValue) {
                try {
                    long value = ((IntegerValue) index).longValue();
                    if (value <= 0) {
                        return arguments[0];
                    } else if (value == 1) {
                        return new TailExpression(arguments[0], 2);
                    }
                } catch (XPathException err) {
                    //
                }
            }
        }

        return super.makeFunctionCall(arguments);
    }

    /**
     * Evaluate the expression as a general function call
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        IntSet removePositions;
        if (arguments[1] instanceof net.sf.saxon.value.AtomicValue) {
            NumericValue n = (NumericValue) arguments[1].head();
            int pos = (int) n.longValue();
            if (pos < 1) {
                return arguments[0];
            }
            removePositions = new IntSingletonSet(pos);
        } else {
            IntHashSet positions = new IntHashSet();
            NumericValue n;
            SequenceIterator iter = arguments[1].iterate();
            while ((n = (NumericValue)iter.next()) != null) {
                int pos = (int) n.longValue();
                if (pos >= 1) {
                    positions.add(pos);
                }
            }
            if (positions.isEmpty()) {
                return arguments[0];
            }
            removePositions = positions;
        }
        return SequenceTool.toLazySequence(
                new FilterFn.PositionalFilterIterator(
                        arguments[0].iterate(),
                        (it, pos) -> !removePositions.contains(pos)));
    }
    
    @Override
    public String getStreamerName() {
        return "Remove";
    }

}

