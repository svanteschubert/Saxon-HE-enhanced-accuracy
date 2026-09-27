// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.DecimalValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.FloatValue;

/**
 * Coercion plan for use when the required item type is xs:float
 */
public class FloatCoercionPlan extends AtomicCoercionPlan {

    private final static FloatCoercionPlan INSTANCE31 = new FloatCoercionPlan(31);
    private final static FloatCoercionPlan INSTANCE40 = new FloatCoercionPlan(40);

    final int version;

    private FloatCoercionPlan(int version) {
        this.version = version;
    }

    public static FloatCoercionPlan getInstance(int version) {
        return version >= 40 ? INSTANCE40 : INSTANCE31;
    }
    @Override
    protected AtomicValue promote(AtomicValue item, ItemType requiredType, CoercionRequest request) throws XPathException {
        if (item instanceof DecimalValue) {
            return new FloatValue(((DecimalValue) item).getFloatValue());
        } else if (item instanceof DoubleValue && version >= 40) {
            return new FloatValue(((DoubleValue) item).getFloatValue());
        } else {
            return item;
        }
    }

}


