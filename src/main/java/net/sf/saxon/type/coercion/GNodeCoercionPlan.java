// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.ma.MapOrArray;
import net.sf.saxon.ma.jnode.RootJNode;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

/**
 * A coercion plan represents a process for implementing the coercion rules for converting
 * a supplied value to a given required type. This class handles the case where the required
 * type is an array type.
 */
public final class GNodeCoercionPlan extends CoercionPlan {

    public final static GNodeCoercionPlan INSTANCE = new GNodeCoercionPlan();

    public static GNodeCoercionPlan getInstance() {
        return INSTANCE;
    }

    /**
     * Apply the coercion rules (function conversion rules) to a value, given a required type.
     *
     * @param item the value to be converted
     * @param requiredType the required array type
     * @param request      the input to the coercion
     * @return the converted value (possibly as a lazily-evaluated sequence)
     */

    public GroundedValue coerceItem(Item item, ItemType requiredType, CoercionRequest request) throws XPathException {
        if (item instanceof GNode) {
            return item;
        }
        if (item instanceof MapOrArray) {
            return RootJNode.obtainRootJNode((MapOrArray)item);
        }
        throw coercionError(item, requiredType, request, null);
    }


}


