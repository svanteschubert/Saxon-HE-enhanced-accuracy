// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

/**
 * Coercion plan for use when items do not need to be converted, but the cardinality needs to be checked
 */
public class IdentityCoercionPlan extends CoercionPlan {

    private final static IdentityCoercionPlan INSTANCE = new IdentityCoercionPlan();

    public static IdentityCoercionPlan getInstance() {
        return INSTANCE;
    }

    /**
     * Apply the coercion rules (3.1: function conversion rules) to an item, given this target item type.
     *
     * @param item         the item to be coerced
     * @param requiredType the required item type
     * @param request      the input to the coercion
     * @return the converted value. We define this as a grounded value because in the vast
     * majority of cases it will be a single item, and in other cases (a typed node with a list type,
     * an array of atomic values) there is little benefit in lazy evaluation. The implementation
     * is responsible for ensuring that the returned value does indeed consist entirely of items
     * that match the required item type; it is not responsible for cardinality checking. The item
     * type checking can be achieved, if required, by a callback to the check() method.
     * @throws XPathException if the value cannot be converted to the required type
     */
    @Override
    public GroundedValue coerceItem(Item item, ItemType requiredType, CoercionRequest request) throws XPathException {
        return item;
    }
}


