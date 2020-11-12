////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

/**
 * Implements the changes to a tuple stream effected by the Let clause in a FLWOR expression
 */
public class LetClausePush extends TuplePush {

    TuplePush destination;
    LetClause letClause;

    public LetClausePush(Outputter outputter, TuplePush destination, LetClause letClause) {
        super(outputter);
        this.destination = destination;
        this.letClause = letClause;
    }

    /*
     * Notify the next tuple.
     */
    @Override
    public void processTuple(XPathContext context) throws XPathException {
        Sequence val = letClause.getEvaluator().evaluate(letClause.getSequence(), context);
        context.setLocalVariable(letClause.getRangeVariable().getLocalSlotNumber(), val);
        destination.processTuple(context);
    }

    /*
     * Close the tuple stream
     */
    @Override
    public void close() throws XPathException {
        destination.close();
    }
}

