// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.CardinalityCheckingIterator;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.ma.Parcel;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

import java.util.function.Supplier;

/**
 * Implementation of the coercion rules (3.0: function conversion rules) for dynamic
 * function calls. Converts a value supplied as an argument in a function call to
 * the required type. This is used primarily for dynamic function calls; for static calls
 * see the {@link TypeChecker} class, which converts expressions rather than values. The class
 * is also used for converting external parameters to the required type.
 *
 * <p>There are separate versions of this class for XPath 3.1 and XPath 4.0.</p>
 */
public class CoercionRules {


    private Configuration config;
    private int version;



    public static CoercionRules forVersion(Configuration config, int version) {
        return new CoercionRules(config, version);
    }

    public CoercionRules(Configuration config, int version) {
        this.config = config;
        this.version = version;
    }

    /**
     * Apply the coercion rules (function conversion rules) to a value, given a required type.
     *
     * @param value        a value to be converted
     * @param requiredType the required type
     * @param knownType    the item type of the supplied value, to the extent this is known
     * @param config       the Saxon configuration
     * @param roleSupplier identifies the value to be converted in error messages
     * @param locator      identifies the location for error messages
     * @return the converted value (possibly as a lazily-evaluated sequence)
     * @throws net.sf.saxon.trans.XPathException if the value cannot be converted to the required type
     */

    public Sequence coerce(Sequence value,
                           SequenceType requiredType,
                           SequenceType knownType,
                           Configuration config,
                           Supplier<RoleDiagnostic> roleSupplier,
                           Location locator)
            throws XPathException {

        CoercionPlan plan = requiredType.getPrimaryType().getCoercionPlan(version);
        if (plan == null) {
            return value;
        }
        CoercionRequest request = new CoercionRequest(knownType, config, roleSupplier, locator);

        if (value instanceof Item it) {
            if (!(it instanceof Parcel) && requiredType != SequenceType.EMPTY_SEQUENCE) {
                GroundedValue result = plan.coerceItem(it, requiredType.getPrimaryType(), request);
                if (result instanceof Item || requiredType.getCardinality() == StaticProperty.ALLOWS_ZERO_OR_MORE) {
                    return result;
                }
                SequenceIterator checker = new CardinalityCheckingIterator(
                        result.iterate(), requiredType.getCardinality(), request.roleSupplier, request.locator);
                return SequenceTool.toLazySequence(checker);
            }
        }

        return SequenceTool.toLazySequence(
                plan.coerceSequence(value.iterate(), requiredType, request));
    }
    

}

