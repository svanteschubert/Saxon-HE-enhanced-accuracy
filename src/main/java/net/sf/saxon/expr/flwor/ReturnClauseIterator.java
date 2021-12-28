////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * This iterator applies the return expression of a FLWOR expression to each
 * of the tuples in a supplied tuple stream, returning the result as an iterator
 */

public class ReturnClauseIterator implements SequenceIterator {

    private TuplePull base;
    private Expression action;
    private XPathContext context;
    private SequenceIterator results = null;

    /**
     * Construct an iterator over the results of the FLWOR expression.
     *
     * @param base    the base iterator
     * @param flwor   the FLWOR expression
     * @param context the XPath dynamic context
     */

    public ReturnClauseIterator(TuplePull base, FLWORExpression flwor, XPathContext context) {
        this.base = base;
        this.action = flwor.getReturnClause();
        this.context = context;
    }

    @Override
    public Item next() throws XPathException {
        Item nextItem;
        while (true) {
            if (results != null) {
                nextItem = results.next();
                if (nextItem != null) {
                    break;
                } else {
                    results = null;
                }
            }
            if (base.nextTuple(context)) {
                // Call the supplied return expression
                results = action.iterate(context);
                nextItem = results.next();
                if (nextItem == null) {
                    results = null;
                } else {
                    break;
                }
                // now go round the loop to get the next item from the base sequence
            } else {
                results = null;
                return null;
            }
        }

        return nextItem;
    }


    @Override
    public void close() {
        if (results != null) {
            results.close();
        }
        base.close();
    }


}

