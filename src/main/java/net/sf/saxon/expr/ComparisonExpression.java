////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.sort.AtomicComparer;

/**
 * Interface implemented by expressions that perform a comparison
 */
public interface ComparisonExpression {

    /**
     * Get the AtomicComparer used to compare atomic values. This encapsulates any collation that is used
     */

    AtomicComparer getAtomicComparer();

    /**
     * Get the primitive (singleton) operator used: one of Token.FEQ, Token.FNE, Token.FLT, Token.FGT,
     * Token.FLE, Token.FGE
     */

    int getSingletonOperator();

    /**
     * Get the two operands of the comparison
     */

    Operand getLhs();

    Operand getRhs();

    Expression getLhsExpression();

    Expression getRhsExpression();

    /**
     * Determine whether untyped atomic values should be converted to the type of the other operand
     *
     * @return true if untyped values should be converted to the type of the other operand, false if they
     * should be converted to strings.
     */

    boolean convertsUntypedToOther();
}

