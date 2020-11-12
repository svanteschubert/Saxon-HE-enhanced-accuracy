////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.value.SequenceType;

/**
 * Treat Expression: implements "treat as data-type ( expression )". This is a factory class only.
 */

public abstract class TreatExpression {

    /**
     * This class is never instantiated
     */
    private TreatExpression() {
    }

    /**
     * Make a treat expression with error code XPDY0050
     *
     * @param sequence the expression whose result is to be checked
     * @param type     the type against which the result is to be checked
     * @return the expression
     */

    public static Expression make(Expression sequence, SequenceType type) {
        return make(sequence, type, "XPDY0050");
    }

    /**
     * Make a treat expression with a non-standard error code
     *
     * @param sequence  the expression whose result is to be checked
     * @param type      the type against which the result is to be checked
     * @param errorCode the error code to be returned if the check fails
     * @return the expression
     */

    public static Expression make(Expression sequence, SequenceType type, String errorCode) {
        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.TYPE_OP, "treat as", 0);
        role.setErrorCode(errorCode);
        Expression e = CardinalityChecker.makeCardinalityChecker(sequence, type.getCardinality(), role);
        return new ItemChecker(e, type.getPrimaryType(), role);
    }

}

