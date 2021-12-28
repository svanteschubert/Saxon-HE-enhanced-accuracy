////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.expr.Expression;

/**
 * A construct whose execution can be notified to a TraceListener. In practice this is
 * a function or template or global variable.
 */

public interface TraceableComponent extends Traceable {

    /**
     * Get the expression at the root of the expression tree used to evaluate this construct
     * @return the root expression
     */

    Expression getBody();

    /**
     * Set the expression to be used to evaluate this construct
     * @param expression the evaluation expression
     */

    void setBody(Expression expression);

    /**
     * Get a string to identify the type of component
     * @return an identifying string
     */

    String getTracingTag();

}

