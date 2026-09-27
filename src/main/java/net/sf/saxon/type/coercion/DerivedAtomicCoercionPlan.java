// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.AtomicValue;

/**
 * Coercion plan for use with 4.0 when the required item type is a derived atomic type
 */
public class DerivedAtomicCoercionPlan extends AtomicCoercionPlan {

    private static final DerivedAtomicCoercionPlan INSTANCE = new DerivedAtomicCoercionPlan();

    public static DerivedAtomicCoercionPlan getInstance() {
        return INSTANCE;
    }

    @Override
    protected AtomicValue promote(AtomicValue atom, ItemType requiredType, CoercionRequest request) throws XPathException {
        AtomicType req = (AtomicType) requiredType;
        AtomicType prim = (AtomicType) req.getPrimitiveItemType();   // cast needed for C#
        if (prim == BuiltInAtomicType.INTEGER) {
            prim = BuiltInAtomicType.DECIMAL;
        } else if (prim == BuiltInAtomicType.DAY_TIME_DURATION) {
            prim = BuiltInAtomicType.DURATION;
        } else if (prim == BuiltInAtomicType.YEAR_MONTH_DURATION) {
            prim = BuiltInAtomicType.DURATION;
        }
        ConversionRules rules = request.config.getConversionRules();
        TypeHierarchy th = request.config.getTypeHierarchy();

        if (prim.matches(atom)
                && !req.matches(atom)
                && req.validate(atom, null, rules) == null) {
            return atom.withMetadata(req);
            // TODO: else error?
        } else {
            return atom;
        }

    }

}


