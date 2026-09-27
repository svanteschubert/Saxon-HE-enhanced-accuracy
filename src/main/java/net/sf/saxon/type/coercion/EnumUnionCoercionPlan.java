// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.ma.zeno.ZenoSequence;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.EnumerationUnionType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.ValidationFailure;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

/**
 * Coercion plan for use with 4.0 when the required item type is an enum type
 */
public class EnumUnionCoercionPlan extends UnionCoercionPlan {


    public final static EnumUnionCoercionPlan INSTANCE = new EnumUnionCoercionPlan();
    protected EnumUnionCoercionPlan() {
        super(40);
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
        EnumerationUnionType reqType = (EnumerationUnionType)requiredType;
        TypeHierarchy th = request.config.getTypeHierarchy();
        if (!(item instanceof AtomicValue)) {
            AtomicSequence atomizedValue = item.atomize();
            ZenoSequence result = new ZenoSequence();
            for (AtomicValue atom : atomizedValue) {
                result = result.appendSequence(coerceItem(atom, requiredType, request));
            }
            return result;
        }

        if (requiredType.matches(item)) {
            return item;
        }
        if (item instanceof StringValue) {
            // Convert xs:untypedAtomic and xs:anyURI to xs:string
            StringValue s = new StringValue(item.getUnicodeStringValue());
            if (requiredType.matches(s)) {
                return s;
            }
        }
        RoleDiagnostic role = request.roleSupplier.get();
        ValidationFailure vf = new ValidationFailure(
                "Cannot convert the " + role.getMessage() + " to " + reqType.getDescription());
        vf.setErrorCode("XPTY0004");
        throw vf.makeException().asTypeError();
    }



}


