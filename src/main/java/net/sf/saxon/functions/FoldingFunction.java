////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;


/**
 * Implementation of aggregate functions such as sum() and avg() that supply a fold() function. Such
 * functions are automatically streamable.
 */
public abstract class FoldingFunction extends SystemFunction {

    /**
     * Create the Fold object which actually performs the evaluation. Must be implemented
     * in every subclass.
     * @param context the dynamic evaluation context
     * @param additionalArguments the values of all arguments other than the first.
     * @return the Fold object used to compute the function
     * @throws XPathException if a dynamic error occurs
     */

    public abstract Fold getFold(XPathContext context, Sequence... additionalArguments) throws XPathException;

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
        Sequence[] additionalArgs = new Sequence[arguments.length - 1];
        System.arraycopy(arguments, 1, additionalArgs, 0, additionalArgs.length);
        Fold fold = getFold(context, additionalArgs);
        SequenceIterator iter = arguments[0].iterate();
        Item item;
        while ((item = iter.next()) != null) {
            fold.processItem(item);
            if (fold.isFinished()) {
                break;
            }
        }
        return fold.result();
    }

    @Override
    public String getStreamerName() {
        return "Fold";
    }
}

