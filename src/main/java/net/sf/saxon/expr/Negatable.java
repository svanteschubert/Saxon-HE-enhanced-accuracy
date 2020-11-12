////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.type.TypeHierarchy;

/**
 * This interface is implemented by expressions that returns a boolean value, and returns an expression
 * whose result is the negated boolean value
 */
public interface Negatable {

    /**
     * Check whether this specific instance of the expression is negatable
     *
     * @return true if it is
     * @param th the type hierarchy cache
     */

    boolean isNegatable(TypeHierarchy th);

    /**
     * Create an expression that returns the negation of this expression
     *
     * @return the negated expression
     */

    Expression negate();
}
