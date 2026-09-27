// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SingletonEnumType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

/**
 * Coercion plan for use with 4.0 when the required item type is a singleton enumeration type
 */
public class SingletonEnumerationCoercionPlan extends AtomicCoercionPlan {

    public final static SingletonEnumerationCoercionPlan INSTANCE = new SingletonEnumerationCoercionPlan();

    @Override
    protected AtomicValue convertUntyped(AtomicValue atom, ItemType requiredType, CoercionRequest request) throws XPathException {
        SingletonEnumType enumType = (SingletonEnumType) requiredType;
        if (enumType.matchesString(atom.getUnicodeStringValue())) {
            return new StringValue(atom.getUnicodeStringValue(), enumType);
        } else {
            throw coercionError(atom, requiredType, request, null);
        }
    }
    @Override
    protected AtomicValue promote(AtomicValue atom, ItemType requiredType, CoercionRequest request) throws XPathException {
        SingletonEnumType enumType = (SingletonEnumType)requiredType;
        TypeHierarchy th = request.config.getTypeHierarchy();
        if (enumType.matches(atom)) {
            return atom;
        } else if (enumType.matchesString(atom.getUnicodeStringValue())) {
            return new StringValue(atom.getUnicodeStringValue(), enumType);
        } else {
            throw coercionError(atom, requiredType, request, null);
        }
    }

    // TODO: when coercing a string to an enumeration type, we're throwing exceptions for every value not matched.



}


