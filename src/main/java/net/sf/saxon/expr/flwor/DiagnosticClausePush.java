////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.Trace;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * Implements the changes to a tuple stream effected by the Let clause in a FLWOR expression
 */
public class DiagnosticClausePush extends TuplePush {

    TuplePush destination;
    DiagnosticClause diagClause;
    int tupleSeq = 1;

    public DiagnosticClausePush(Outputter outputter, TuplePush destination, DiagnosticClause diagClause) {
        super(outputter);
        this.destination = destination;
        this.diagClause = diagClause;
    }

    /*
     * Notify the next tuple.
     */
    @Override
    public void processTuple(XPathContext context) throws XPathException {
        SequenceIterator val = diagClause.getEvaluator().iterate(context);
        Logger out = context.getController().getTraceFunctionDestination();
        Item item;
        int pos = 1;
        while ((item = val.next()) != null) {
            String label = "FLWOR-> " + tupleSeq + "/" + pos++;
            Trace.traceItem(item, label, out);
        }
        destination.processTuple(context);
        tupleSeq++;
    }

    /*
     * Close the tuple stream
     */
    @Override
    public void close() throws XPathException {
        destination.close();
    }
}

