////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

/**
 * Interface implemented by expressions that switch the context, for example A/B or A[B]
 */
public interface ContextSwitchingExpression extends ContextOriginator {

    /**
     * Get the subexpression that sets the context item
     *
     * @return the subexpression that sets the context item, position, and size to each of its
     *         items in turn
     */

    Expression getSelectExpression();

    /**
     * Get the subexpression that is evaluated in the new context
     *
     * @return the subexpression evaluated in the context set by the controlling expression
     */

    Expression getActionExpression();
}

