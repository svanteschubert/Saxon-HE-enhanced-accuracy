////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.flwor.Clause;
import net.sf.saxon.expr.flwor.FLWORExpression;
import net.sf.saxon.expr.instruct.TraceExpression;
import net.sf.saxon.trace.TraceableComponent;

/**
 * A code injector can be used to add code to the expression tree (for example, diagnostic tracing code)
 * during the process of parsing and tree construction
 */
public interface CodeInjector {

    /**
     * Process an expression.
     * @param exp       the expression to be processed
     * @return a new expression. Possibly the original expression unchanged; possibly a wrapper
     * expression (such as a {@link TraceExpression}; possibly a modified version of the original expression.
     * The default implementation returns the supplied expression unchanged.
     */

    default Expression inject(Expression exp) {
        return exp;
    }

    /**
     * Process a component such as a function, template, or global variable. The default implementation
     * does nothing.
     * @param component the component to be processed
     */

    default void process(TraceableComponent component) {}

    /**
     * Insert a tracing or monitoring clause into the pipeline of clauses that evaluates a FLWOR expression
     * @param expression the containing FLWOR expression
     * @param clause    the clause whose execution is being monitored
     * @return an injected clause; or the original. The default implementation returns the original clause
     * unchanged.
     */

    default Clause injectClause(FLWORExpression expression, Clause clause) {
        return clause;
    }
}

