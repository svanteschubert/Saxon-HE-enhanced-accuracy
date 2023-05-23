////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.Controller;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.trans.XPathException;

/**
 * This class represents the tuple stream delivered as the output of a trace clause in a
 * FLWOR expression. It does not change the values of any variables in the tuple stream,
 * but merely notifies the TraceListener whenever a new tuple is available.
 */
public class TraceClausePush extends TuplePush {

    private TuplePush destination;
    TraceClause traceClause;
    private Clause baseClause;

    public TraceClausePush(Outputter outputter, TuplePush destination, TraceClause traceClause, Clause baseClause) {
        super(outputter);
        this.destination = destination;
        this.traceClause = traceClause;
        this.baseClause = baseClause;
    }

    /*
     * Process the next tuple.
     */
    @Override
    public void processTuple(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        if (controller.isTracing()) {
            ClauseInfo baseInfo = new ClauseInfo(baseClause);
            baseInfo.setNamespaceResolver(traceClause.getNamespaceResolver());
            controller.getTraceListener().enter(baseInfo, baseClause.getTraceInfo(), context);
            destination.processTuple(context);
            controller.getTraceListener().leave(baseInfo);
        } else {
            destination.processTuple(context);
        }
    }

    /*
     * Notify the end of the tuple stream
     */
    @Override
    public void close() throws XPathException {
        destination.close();
    }
}

