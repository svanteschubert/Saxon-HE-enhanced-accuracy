////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.lib.StringCollator;

/**
 * Interface implemented by expressions that perform a comparison
 */
public interface ComparisonExpression {

    /**
     * Get the AtomicComparer used to compare atomic values. This encapsulates any collation that is used
     * @return the comparer
     */

    AtomicComparer getAtomicComparer();

    /**
     * Get the StringCollator used to compare string values.
     *
     * @return the collator. May return null if the expression will never be used to compare strings
     */

    StringCollator getStringCollator();

    /**
     * Get the primitive (singleton) operator used: one of Token.FEQ, Token.FNE, Token.FLT, Token.FGT,
     * Token.FLE, Token.FGE
     * @return the operator, as defined in class {@link net.sf.saxon.expr.parser.Token}
     */

    int getSingletonOperator();

    /**
     * Get the left-hand operand of the comparison
     * @return the first operand
     */

    Operand getLhs();

    /**
     * Get the right-hand operand of the comparison
     *
     * @return the second operand
     */

    Operand getRhs();

    /**
     * Get the left-hand expression
     *
     * @return the first operand expression
     */

    Expression getLhsExpression();

    /**
     * Get the right-hand expression
     *
     * @return the second operand expression
     */

    Expression getRhsExpression();

    /**
     * Determine whether untyped atomic values should be converted to the type of the other operand
     *
     * @return true if untyped values should be converted to the type of the other operand, false if they
     * should be converted to strings.
     */

    boolean convertsUntypedToOther();
}

