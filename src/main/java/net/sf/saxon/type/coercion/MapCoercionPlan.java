// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.ma.map.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.PlainType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.SequenceType;

/**
 * A coercion plan represents a process for implementing the coercion rules for converting
 * a supplied value to a given required type. This class handles the case where the required
 * type is an array type.
 */
public final class MapCoercionPlan extends CoercionPlan {

    public final static MapCoercionPlan INSTANCE = new MapCoercionPlan();

    public static MapCoercionPlan getInstance() {
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
        PlainType requiredKeyType = ((MapType) requiredType).getKeyType();
        SequenceType requiredValueType = ((MapType)requiredType).getValueType();
        if (item instanceof JNode) {
            GroundedValue gv = ((JNode) item).getContent();
            SequenceIterator coercedValue = coerceSequence(gv.iterate(), SequenceType.zeroOrMore(requiredType), request);
            return SequenceExtent.from(coercedValue);
        }
        if (item instanceof MapItem) {
            if (requiredType.matches(item)) {
                return item;
            } else {
                GeneralMapBuilder builder = AbstractFixedMap.getBuilder(40);
                CoercionPlan keyPlan = requiredKeyType.getCoercionPlan(40);
                CoercionPlan valuePlan = requiredValueType.getPrimaryType().getCoercionPlan(40);
                if (keyPlan == null) {
                    keyPlan = new IdentityCoercionPlan();
                }
                if (valuePlan == null) {
                    valuePlan = new IdentityCoercionPlan();
                }
                for (KeyValuePair pair : ((MapItem) item).keyValuePairs()) {
                    builder.put(
                            (AtomicValue) keyPlan.coerceItem(pair.key(), requiredKeyType, request),
                            SequenceTool.toGroundedValue(valuePlan.coerceSequence(
                                    pair.value().iterate(), requiredValueType, request))
                    );
                }
                try {
                    return builder.getCompletedMap();
                } catch (XPathException e) {
                    throw e.withErrorCode("XPTY0004").asTypeError();
                }
            }
        } else {
            throw coercionError(item, requiredType, request, null);
        }
    }


}


