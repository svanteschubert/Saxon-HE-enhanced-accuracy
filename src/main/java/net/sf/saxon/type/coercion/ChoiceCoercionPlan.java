// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.functions.hof.CoercedFunction;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ChoiceType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.type.TypeHierarchy;

import java.util.ArrayList;
import java.util.List;

/**
 * Coercion plan for use with 4.0 when the required item type is a choice item type or a union type
 */
public class ChoiceCoercionPlan extends AtomicCoercionPlan {

    private final int version;

    public final static ChoiceCoercionPlan INSTANCE31 = new ChoiceCoercionPlan(31);
    public final static ChoiceCoercionPlan INSTANCE40 = new ChoiceCoercionPlan(40);

    private ChoiceCoercionPlan(int version) {
        this.version = version;
    }

    /**
     * Apply the coercion rules (function conversion rules) to a value, given a required type.
     *
     * @param item         the value to be converted
     * @param requiredType the required type
     * @param request      the input to the coercion
     * @return the converted value (possibly as a lazily-evaluated sequence)
     * @throws XPathException if the value cannot be converted to the required type
     */
    @Override
    public GroundedValue coerceItem(Item item, ItemType requiredType, CoercionRequest request) throws XPathException {
        ChoiceType reqType = (ChoiceType)requiredType;
        TypeHierarchy th = request.config.getTypeHierarchy();
        if (requiredType.matches(item)) {
            // Generally we can return the item as is if it matches the required type. But
            // function coercion is needed even if the supplied item matches the required type
            for (ItemType it : reqType.getAlternatives()) {
                if (it.matches(item)) {
                    if (it instanceof SpecificFunctionType) {
                        boolean is40 = version >= 40;
                        return new CoercedFunction((FunctionItem) item, ((SpecificFunctionType) it), is40, is40);
                    } else {
                        return item;
                    }
                }
            }
            throw new AssertionError("Item matches the choice type but doesn't match any of the alternatives");
        }
        List<XPathException> errors = null;
        for (ItemType it : reqType.getAlternatives()) {
            CoercionPlan plan = it.getCoercionPlan(version);
            try {
                return plan.coerceItem(item, it, request);
            } catch (XPathException error) {
                if (errors == null) {
                    errors = new ArrayList<>();
                    errors.add(error);
                }
            }
        }
        assert errors != null;
        throw errors.get(0);
    }



}


