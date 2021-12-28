////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Int64Value;

/**
 * A tuple stream in push mode that implements a "count" clause in an XQuery 3.0 FLWOR expression
 */
public class CountClausePush extends TuplePush {

    TuplePush destination;
    int slot;
    int count = 0;

    public CountClausePush(Outputter outputter, TuplePush destination, CountClause countClause) {
        super(outputter);
        this.destination = destination;
        this.slot = countClause.getRangeVariable().getLocalSlotNumber();
    }

    /*
     * Notify the next tuple.
     */
    @Override
    public void processTuple(XPathContext context) throws XPathException {
        context.setLocalVariable(slot, new Int64Value(++count));
        destination.processTuple(context);
    }

    /**
     * Close the tuple stream, indicating that no more tuples will be supplied
     */
    @Override
    public void close() throws XPathException {
        destination.close();
    }
}

// Copyright (c) 2011-2020 Saxonica Limited
