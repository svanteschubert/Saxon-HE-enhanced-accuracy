////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;

/**
 * Implementation of the fn:count function
 */
public class Count extends SystemFunction implements ArityOneFunction {

    /**
     * Get the number of items in a sequence identified by a SequenceIterator
     *
     * @param iter The SequenceIterator. This method moves the current position
     *             of the supplied iterator; if this isn't safe, make a copy of the iterator
     *             first by calling getAnother(). The supplied iterator must be positioned
     *             before the first item (there must have been no call on next()).
     * @return the number of items in the underlying sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a failure occurs reading the input sequence
     */

    public static int count(/*@NotNull*/ SequenceIterator iter) throws XPathException {
        if (SequenceTool.supportsGetLength(iter)) {
            return SequenceTool.getLength(iter);
        } else {
            int n = 0;
            while (iter.next() != null) {
                n++;
            }
            return n;
        }
    }

    /**
     * Get the number of items in a sequence identified by a SequenceIterator
     *
     * @param iter The SequenceIterator. The supplied iterator must be positioned
     *             before the first item (there must have been no call on next()). It will
     *             always be consumed
     * @return the number of items in the underlying sequence
     * @throws UncheckedXPathException if a failure occurs reading the input sequence
     */

    public static int steppingCount(SequenceIterator iter) {
        int n = 0;
        while (iter.next() != null) {
            n++;
        }
        return n;
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
    public IntegerValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        return call1(context, arguments[0]);
    }

    /**
     * Call a function with one argument
     *
     * @param context the dynamic evaluation context
     * @param arg0    the first argument
     * @return the result of the function call
     * @throws XPathException if the call fails with a dynamic error
     */
    @Override
    public IntegerValue call1(XPathContext context, Sequence arg0) throws XPathException {
        int size = arg0 instanceof GroundedValue ? ((GroundedValue) arg0).getLength() : count(arg0.iterate());
        return Int64Value.makeIntegerValue(size);
    }

    @Override
    public String getStreamerName() {
        return "Count";
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new CountFnElaborator();
    }


    public static class CountFnElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            Expression arg = fnc.getArg(0);
            PullEvaluator puller = arg.makeElaborator().elaborateForPull();
            return context -> Int64Value.makeIntegerValue(count(puller.iterate(context)));
        }


    }
}

