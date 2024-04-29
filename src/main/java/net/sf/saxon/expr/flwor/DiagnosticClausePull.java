////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.Trace;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * Implements the trace clause in a FLWOR expression. This is a Saxon extension that displays the value of a
 * user-specified expression as the tuple stream is evaluated
 */
public class DiagnosticClausePull extends TuplePull {

    TuplePull base;
    DiagnosticClause diagClause;
    int tupleSeq = 1;

    public DiagnosticClausePull(TuplePull base, DiagnosticClause diagClause) {
        this.base = base;
        this.diagClause = diagClause;
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
            return false;
        }
        SequenceIterator val = diagClause.getEvaluator().iterate(context);
        Logger out = context.getController().getTraceFunctionDestination();
        Item item;
        int pos = 1;
        while ((item = val.next()) != null) {
            String label = "FLWOR<- " + tupleSeq + "/" + pos++;
            Trace.traceItem(item, label, out);
        }
        tupleSeq++;
        return true;
    }

    /**
     * Close the tuple stream, indicating that although not all tuples have been read,
     * no further tuples are required and resources can be released
     */
    @Override
    public void close() {
        base.close();
    }
}

