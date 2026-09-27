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
import net.sf.saxon.om.Genre;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;

/**
 * A coercion plan represents a process for implementing the coercion rules for converting
 * a supplied value to a given required type. An AtomicCoercionPlan is used where the required
 * item type is atomic, and is further refined for specific atomic types where special rules
 * apply.
 */
public class AtomicCoercionPlan extends CoercionPlan {

    private final static AtomicCoercionPlan INSTANCE = new AtomicCoercionPlan();

    public static AtomicCoercionPlan getInstance() {
        return INSTANCE;
    }

    /**
     * Apply the coercion rules (function conversion rules) to a value, given a required type.
     *
     * @param item         the value to be converted
     * @param requiredType the required type (always atomic)
     * @param request      the input to the coercion
     * @return the converted value (possibly as a lazily-evaluated sequence)
     * @throws XPathException if the value cannot be converted to the required type
     */

    public GroundedValue coerceItem(Item item, ItemType requiredType, CoercionRequest request) throws XPathException {

        if (item.getGenre() == Genre.ATOMIC) {
            return coerceAtomicValue((AtomicValue) item, requiredType, request);
        } else {
            AtomicSequence seq;
            try {
                seq = item.atomize();
            } catch (XPathException e) {
                RoleDiagnostic role = request.roleSupplier.get();
                ValidationFailure vf = new ValidationFailure(
                        "Failed to atomize the " + role.getMessage() + ": " + e.getMessage());
                if (e.getErrorCodeQName() == null) {
                    vf.setErrorCode("XPTY0004");
                } else {
                    vf.setErrorCodeQName(e.getErrorCodeQName());
                }
                throw vf.makeException().asTypeError();
            }
            if (seq instanceof AtomicValue) {
                return coerceAtomicValue((AtomicValue) seq, requiredType, request);
            } else {
                ZenoSequence result = new ZenoSequence();
                for (AtomicValue atom : seq) {
                    result = result.append(coerceAtomicValue(atom, requiredType, request));
                }
                return result;
            }
        }
    }

    /**
     * Coerce an atomic value to the required atomic type. This method essentially handles
     * conversion of untypedAtomic values to the target type, followed by type promotion
     * (for example decimal to double).
     * @param atom the atomic value to be converted
     * @param requiredType the required atomic type
     * @param request details of the coercion request for diagnostics
     * @return the converted atomic value
     * @throws XPathException if conversion fails
     */

    protected AtomicValue coerceAtomicValue(AtomicValue atom, ItemType requiredType, CoercionRequest request) throws XPathException {
        if (atom.isUntypedAtomic()) {
            atom = convertUntyped(atom, requiredType, request);
        }
        atom = promote(atom, requiredType, request);
        check(atom, requiredType, request);
        return atom;
    }

    /**
     * Coerce an untyped atomic value to the required atomic type.
     *
     * @param atom         the untyped atomic value to be converted
     * @param requiredType the required atomic type
     * @param request      details of the coercion request for diagnostics
     * @return the converted atomic value
     * @throws XPathException if conversion fails
     */

    protected AtomicValue convertUntyped(AtomicValue atom, ItemType requiredType, CoercionRequest request) throws XPathException {
        if (requiredType.getUType().subsumes(UType.UNTYPED_ATOMIC)) {
            return atom;
        }
        return Converter.convert(
                atom, (AtomicType) requiredType, request.config.getConversionRules());
    }

    /**
     * Promote an atomic value to the required atomic type. This handles, for example,
     * decimal to string conversion or base64Binary to hexBinary. It also handles
     * down-casting to derived atomic types. The default implementation returns
     * the supplied value unchanged.
     *
     * @param atom         the untyped atomic value to be converted
     * @param requiredType the required atomic type
     * @param request      details of the coercion request for diagnostics
     * @return the converted atomic value
     * @throws XPathException if conversion fails
     */


    protected AtomicValue promote(AtomicValue atom, ItemType requiredType, CoercionRequest request) throws XPathException {
        return atom;
    }




}


