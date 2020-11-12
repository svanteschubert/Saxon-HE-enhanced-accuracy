////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.flwor.Clause;
import net.sf.saxon.expr.flwor.FLWORExpression;
import net.sf.saxon.expr.flwor.TraceClause;
import net.sf.saxon.expr.instruct.ComponentTracer;
import net.sf.saxon.expr.instruct.TraceExpression;
import net.sf.saxon.expr.parser.CodeInjector;
import net.sf.saxon.expr.parser.ExpressionTool;

/**
 * A code injector that wraps every expression (other than a literal) in a TraceExpression, which causes
 * a TraceListener to be notified when the expression is evaluated
 */
public class TraceCodeInjector implements CodeInjector {

    @Override
    public Expression inject(Expression exp) {
        if (!(exp instanceof TraceExpression) && isApplicable(exp)) {
            return new TraceExpression(exp);
        } else {
            return exp;
        }
    }

    protected boolean isApplicable(Expression exp) {
        return false;
    }

    @Override
    public void process(TraceableComponent component) {
        Expression newBody = ExpressionTool.injectCode(component.getBody(), this);
        component.setBody(newBody);
        ComponentTracer trace = new ComponentTracer(component);
        component.setBody(trace);
    }

    /**
     * Insert a tracing or monitoring clause into the pipeline of clauses that evaluates a FLWOR expression
     *
     * @param expression the containing FLWOR expression
     * @param clause     the clause whose execution is being monitored
     */

    @Override
    public Clause injectClause(FLWORExpression expression, Clause clause) {
        return new TraceClause(expression, clause);
    }
}

