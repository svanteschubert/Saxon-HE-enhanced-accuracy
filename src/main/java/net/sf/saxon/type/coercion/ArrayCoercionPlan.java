// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;

/**
 * A coercion plan represents a process for implementing the coercion rules for converting
 * a supplied value to a given required type. This class handles the case where the required
 * type is an array type.
 */
public final class ArrayCoercionPlan extends CoercionPlan {

    public final static ArrayCoercionPlan INSTANCE = new ArrayCoercionPlan();

    public static ArrayCoercionPlan getInstance() {
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
        SequenceType requiredMemberType = ((ArrayItemType)requiredType).getMemberType();
        if (item instanceof JNode) {
            GroundedValue gv = ((JNode)item).getContent();
            SequenceIterator coercedValue = coerceSequence(gv.iterate(), SequenceType.zeroOrMore(requiredType), request);
            return SequenceExtent.from(coercedValue);
        }
        if (item instanceof ArrayItem) {
            List<GroundedValue> result = new ArrayList<>(((ArrayItem)item).arrayLength());
            CoercionPlan memberPlan = requiredMemberType.getPrimaryType().getCoercionPlan(40);
            if (memberPlan == null) {
                memberPlan = new IdentityCoercionPlan();
            }
            for (GroundedValue member : ((ArrayItem)item).members()) {
                 SequenceIterator newMember = memberPlan.coerceSequence(member.iterate(), requiredMemberType, request);
                 result.add(SequenceExtent.from(newMember));
            }
            return new SimpleArrayItem(result);
        } else {
            throw coercionError(item, requiredType, request, null);
        }
    }


}


