// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.functions.hof.CoercedFunction;
import net.sf.saxon.functions.hof.FunctionSequenceCoercer;
import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;

/**
 * Coercion plan for use when the required item type is a specific function type
 */
public class FunctionItemCoercionPlan extends CoercionPlan {

    private final static FunctionItemCoercionPlan INSTANCE31 = new FunctionItemCoercionPlan(31);
    private final static FunctionItemCoercionPlan INSTANCE40 = new FunctionItemCoercionPlan(40);

    private final int version;

    public static FunctionItemCoercionPlan getInstance(int version) {
        return version >= 40 ? INSTANCE40 : INSTANCE31;
    }

    private FunctionItemCoercionPlan (int version) {
        this.version = version;
    }

    /**
     * Apply the coercion rules (function conversion rules) to a value, given a required type.
     *
     * @param item         an item to be coerced
     * @param requiredType the required item type
     * @param request      the input to the coercion
     * @return the converted value
     * @throws XPathException if the value cannot be converted to the required type
     */
    @Override
    public GroundedValue coerceItem(Item item, ItemType requiredType, CoercionRequest request) throws XPathException {
        boolean is40 = version >= 40;
        if (item instanceof JNode) {
            GroundedValue val = ((JNode)item).getContent();
            if (val instanceof FunctionItem) {
                item = ((FunctionItem)val);
            }
        }
        if (item instanceof FunctionItem) {
            FunctionSequenceCoercer.checkAnnotations((FunctionItem) item,
                                                     (FunctionItemType) requiredType,
                                                     request.config);
            //return item;
            return new CoercedFunction((FunctionItem) item, (SpecificFunctionType) requiredType, is40, is40);
        } else {
            throw coercionError(item, requiredType, request, null);
        }
    }

}


