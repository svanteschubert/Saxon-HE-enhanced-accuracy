////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Int64Value;

/**
 * A tuple stream that implements a "count" clause in an XQuery 3.0 FLWOR expression
 */
public class CountClausePull extends TuplePull {

    TuplePull base;
    int slot;
    int count = 0;

    public CountClausePull(TuplePull base, CountClause countClause) {
        this.base = base;
        this.slot = countClause.getRangeVariable().getLocalSlotNumber();
    }

    /**
     * Move on to the next tuple. Before returning, this method must set all the variables corresponding
     * to the "returned" tuple in the local stack frame associated with the context object
     *
     * @param context the dynamic evaluation context
     * @return true if another tuple has been generated; false if the tuple stream is exhausted. If the
     *         method returns false, the values of the local variables corresponding to this tuple stream
     *         are undefined.
     */
    @Override
    public boolean nextTuple(XPathContext context) throws XPathException {
        if (!base.nextTuple(context)) {
            count = 0;
            context.setLocalVariable(slot, Int64Value.ZERO);
            return false;
        }
        context.setLocalVariable(slot, new Int64Value(++count));
        return true;
    }
}

// Copyright (c) 2011-2020 Saxonica Limited
