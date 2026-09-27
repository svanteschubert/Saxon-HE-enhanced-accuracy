// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.RecordType;
import net.sf.saxon.ma.map.StringMapBuilder;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.function.Supplier;

/**
 * A coercion plan represents a process for implementing the coercion rules for converting
 * a supplied value to a given required type. This class handles the case where the required
 * type is a record type.
 */
public final class RecordCoercionPlan extends CoercionPlan {

    public final static RecordCoercionPlan INSTANCE = new RecordCoercionPlan();

    /**
     * Apply the coercion rules (function conversion rules) to a value, given a required type.
     *
     * @param item the map to be converted
     * @param requiredType the required record type
     * @param request      the input to the coercion
     * @return the converted value (possibly as a lazily-evaluated sequence)
     */

    public GroundedValue coerceItem(Item item, ItemType requiredType, CoercionRequest request) throws XPathException {
        if (item instanceof MapItem suppliedMap) {
            RecordType test = (RecordType) requiredType;
            // We need to reorder the entries; so start with those that are present in the record type
            StringMapBuilder builder = new StringMapBuilder(suppliedMap.size());
            for (String s : test.getFieldNames()) {
                 StringValue key = new StringValue(s);
                 GroundedValue val = suppliedMap.get(key);
                 if (val == null) {
                     if (!test.isOptionalField(s)) {
                         throw coercionError(item, requiredType, request, "Field '" + s + "' is required");
                     }
                 } else {
                     SequenceType fieldType = test.getFieldType(s);
                     if (fieldType != null) {
                         CoercionPlan fieldPlan = fieldType.getPrimaryType().getCoercionPlan(40);
                         if (fieldType.matches(val)) {
                             builder.put(key.getUnicodeStringValue(), val);
                         } else {
                             Supplier<RoleDiagnostic> fieldRoleSupplier = () ->
                                     new RoleDiagnostic(RoleDiagnostic.OPTION, s, 0);
                             CoercionRequest subRequest = new CoercionRequest(
                                     SequenceType.ANY_SEQUENCE,
                                     request.config, fieldRoleSupplier, request.locator);
                             SequenceIterator convertedValue =
                                     null;
                             try {
                                 convertedValue = fieldPlan.coerceSequence(val.iterate(), fieldType, subRequest);
                             } catch (XPathException e) {
                                 throw coercionError(item, requiredType, request, "Failed to convert value of field '" + s + "': " + e.getMessage());
                             }
                             builder.put(key.getUnicodeStringValue(), SequenceTool.toGroundedValue(convertedValue));
                         }
                     }
                 }
            }
            return builder.getCompletedMapConfidently();

        } else {
            throw coercionError(item, requiredType, request, null);
        }
    }


}


