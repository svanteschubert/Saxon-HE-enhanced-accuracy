// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.ValidationFailure;
import net.sf.saxon.value.AtomicValue;

/**
 * A coercion plan for use when the target type is QName or NOTATION
 */
public final class QNameCoercionPlan extends AtomicCoercionPlan {

    private final static QNameCoercionPlan INSTANCE = new QNameCoercionPlan();

    public static QNameCoercionPlan getInstance() {
        return INSTANCE;
    }

    protected AtomicValue convertUntyped(AtomicValue atom, ItemType requiredType, CoercionRequest request) throws XPathException {
        RoleDiagnostic role = request.roleSupplier.get();
        ValidationFailure vf = new ValidationFailure(
                "Failed to convert the " + role.getMessage() + ": " +
                        "Implicit conversion of untypedAtomic value to " + requiredType +
                        " is not allowed");
        vf.setErrorCode("XPTY0117");
        throw vf.makeException();
    }


}


