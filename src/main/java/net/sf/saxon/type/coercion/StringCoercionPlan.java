// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

/**
 * Coercion plan for use when the required item type is xs:string
 */
public class StringCoercionPlan extends AtomicCoercionPlan {

    private static final StringCoercionPlan INSTANCE = new StringCoercionPlan();

    public static StringCoercionPlan getInstance() {
        return INSTANCE;
    }


    @Override
    protected AtomicValue promote(AtomicValue item, ItemType requiredType, CoercionRequest request) throws XPathException {
        if (item instanceof AnyURIValue) {
            return new StringValue(item.getUnicodeStringValue());
        } else {
            return item;
        }
    }

}


