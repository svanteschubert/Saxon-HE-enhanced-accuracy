////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.trans.XPathException;

/**
 * The class represents the final stage in a push-mode tuple pipeline. The previous stages
 * have stored the values corresponding to the current tuple in local variables on the
 * stack; all that remains is to evaluate the return expression (with reference to these
 * variables) and send the results to the current receiver.
 */
public class ReturnClausePush extends TuplePush {

    private Expression returnExpr;

    public ReturnClausePush(Outputter outputter, Expression returnExpr) {
        super(outputter);
        this.returnExpr = returnExpr;
    }

    /**
     * Notify the availability of the next tuple. Before calling this method,
     * the supplier of the tuples must set all the variables corresponding
     * to the supplied tuple in the local stack frame associated with the context object
     *
     * @param context the dynamic evaluation context
     */
    @Override
    public void processTuple(XPathContext context) throws XPathException {
        returnExpr.process(getOutputter(), context);
    }

    /**
     * Close the tuple stream, indicating that no more tuples will be supplied
     */
    @Override
    public void close() throws XPathException {
        // no action
    }
}

