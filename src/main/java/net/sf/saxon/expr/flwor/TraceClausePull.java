////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.trans.XPathException;

/**
 * This class represents the tuple stream delivered as the output of a trace clause in a
 * FLWOR expression. It does not change the values of any variables in the tuple stream,
 * but merely informs the TraceListener whenever a new tuple is requested.
 */
public class TraceClausePull extends TuplePull {

    private TuplePull base;
    private Clause baseClause;
    private TraceClause traceClause;

    public TraceClausePull(TuplePull base, TraceClause traceClause, Clause baseClause) {
        this.base = base;
        this.traceClause = traceClause;
        this.baseClause = baseClause;
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
        Controller controller = context.getController();
        if (controller.isTracing()) {
            ClauseInfo baseInfo = new ClauseInfo(baseClause);
            baseInfo.setNamespaceResolver(traceClause.getNamespaceResolver());
            controller.getTraceListener().enter(baseInfo, baseClause.getTraceInfo(), context);
            boolean b = base.nextTuple(context);
            controller.getTraceListener().leave(baseInfo);
            return b;
        } else {
            return base.nextTuple(context);
        }

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

