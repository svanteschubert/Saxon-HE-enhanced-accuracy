// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BigDecimalValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.FloatValue;

/**
 * Coercion plan for use when the required item type is xs:decimal
 */
public class DecimalCoercionPlan extends AtomicCoercionPlan {

    private final static DecimalCoercionPlan INSTANCE = new DecimalCoercionPlan();

    public static DecimalCoercionPlan getInstance() {
        return INSTANCE;
    }

    @Override
    protected AtomicValue promote(AtomicValue item, ItemType requiredType, CoercionRequest request) throws XPathException {
        try {
            if (item instanceof DoubleValue) {
                return new BigDecimalValue(((DoubleValue) item).getDecimalValue());
            } else if (item instanceof FloatValue) {
                return new BigDecimalValue(((FloatValue) item).getDecimalValue());
            } else {
                return item;
            }
        } catch (ValidationException e) {
            throw coercionError(item, requiredType, request, null);
        }
    }

}


