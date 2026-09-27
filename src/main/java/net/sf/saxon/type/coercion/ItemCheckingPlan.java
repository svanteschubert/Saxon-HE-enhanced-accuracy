// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

/**
 * A coercion plan represents a process for implementing the coercion rules for converting
 * a supplied value to a given required type. The {@code ItemCheckingPlan} is used for types
 * such as item(), node(), or map(*) where no item-level coercion take place, but where the
 * only required action is to check that the items have the correct type and raise an
 * error if not.
 */
public final class ItemCheckingPlan extends CoercionPlan {

    public final static ItemCheckingPlan INSTANCE = new ItemCheckingPlan();

    /**
     * Apply the coercion rules (function conversion rules) to a value, given a required type.
     *
     * @param item the item to be (not) coerced
     * @param requiredType the required type
     * @param request      the input to the coercion
     * @return the converted value (possibly as a lazily-evaluated sequence)
     */

    public GroundedValue coerceItem(Item item, ItemType requiredType, CoercionRequest request) throws XPathException {
        if (item instanceof JNode) {
            return ((JNode)item).getContent();
        }
        check(item, requiredType, request);
        return item;
    }



}


