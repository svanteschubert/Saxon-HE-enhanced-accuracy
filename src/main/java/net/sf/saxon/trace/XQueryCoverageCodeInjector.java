////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Operand;
import net.sf.saxon.expr.instruct.ComponentTracer;
import net.sf.saxon.expr.instruct.TraceExpression;
import net.sf.saxon.s9api.Location;

import java.util.*;

/**
 * A code injector designed to support the coverage tracing option in XQuery
 */
public class XQueryCoverageCodeInjector extends TraceCodeInjector {
    public final Map<String, CoverageRecord> histograms = new HashMap<>();

    @Override
    protected boolean isApplicable(Expression exp) {
        if (exp != null) {
            gatherTracePoints(exp);
            return true;
        }
        return false;
    }

    private void gatherTracePoints(Expression expr) {
        if (expr instanceof TraceExpression) {
            Expression traceExpression = ((TraceExpression) expr).getBody();
            if (traceExpression instanceof ComponentTracer) {
                gatherTracePoints(((ComponentTracer) traceExpression).getBody());
            } else {
                Location loc = expr.getLocation();
                if (loc != null && loc.getSystemId() != null && loc.getLineNumber() > 0) {
                    CoverageRecord record = histograms.get(loc.getSystemId());
                    if (record == null) {
                        record = new CoverageRecord();
                        histograms.put(loc.getSystemId(), record);
                    }
                    record.addLocation(loc.getLineNumber());
                }
            }
        }

        for (Operand o : expr.operands()) {
            gatherTracePoints(o.getChildExpression());
        }
    }
}
