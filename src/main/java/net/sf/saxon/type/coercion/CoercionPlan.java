// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.expr.CardinalityCheckingIterator;
import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.MappingIterator;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.ma.Parcel;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

/**
 * A coercion plan represents a process for implementing the coercion rules for converting
 * a supplied value to a given required type. A coercion plan is associated with each item
 * type, and may differ depending on the XPath language version in use.
 */
public abstract class CoercionPlan {

    /**
     * Apply the coercion rules (function conversion rules) to a sequence, given a required type.
     * @param value the value to be coerced
     * @param requiredType the required sequence type
     * @param request other input to the coercion, for example diagnostic information
     * @return the converted value (lazily-evaluated)
     * @throws net.sf.saxon.trans.XPathException if the value cannot be converted to the required type
     */

    public final SequenceIterator coerceSequence(SequenceIterator value, SequenceType requiredType, CoercionRequest request)
            throws XPathException {
        try {
            int cardinality = requiredType.getCardinality();
            boolean singleton = false;
            if (value instanceof LastPositionFinder && ((LastPositionFinder) value).supportsGetLength()) {
                int length = ((LastPositionFinder) value).getLength();
                if (length == 0) {
                    if (Cardinality.allowsZero(cardinality)) {
                        return value;
                    } else {
                        // force an error
                        return new CardinalityCheckingIterator(
                                value, cardinality, request.roleSupplier, request.locator);
                    }
                } else if (length == 1) {
                    singleton = true;
                }
            }
            // If the required type is empty-sequence(), check that the sequence is actually empty
            if (cardinality == StaticProperty.ALLOWS_ZERO) {
                return new CardinalityCheckingIterator(
                        value, StaticProperty.ALLOWS_ZERO, request.roleSupplier, request.locator);
            }
            // Convert the items in the sequence (lazily)
            SequenceIterator uncheckedResult;
            if (singleton) {
                Item input = value.next();
                if (input instanceof Parcel) {
                    return coerceSequence(((Parcel)input).getValue().iterate(), requiredType, request);
                }
                uncheckedResult = coerceItem(input, requiredType.getPrimaryType(), request).iterate();
            } else {
                uncheckedResult = makeMappingIterator(value, requiredType.getPrimaryType(), request);
            }
            // If necessary, check the cardinality of the result (lazily)
            if (cardinality != StaticProperty.ALLOWS_ZERO_OR_MORE) {
                return new CardinalityCheckingIterator(
                        uncheckedResult, cardinality, request.roleSupplier, request.locator);
            } else {
                return uncheckedResult;
            }
        } catch (XPathException e) {
            throw e.maybeWithErrorCode("XPTY0004").maybeWithLocation(request.locator);
        }
    }

    private SequenceIterator makeMappingIterator(SequenceIterator value, ItemType requiredType, CoercionRequest request) {
        return new MappingIterator(value, item -> coerceItem(item, requiredType, request).iterate());
    }

    /**
     * Apply the coercion rules (3.1: function conversion rules) to an item, given this target item type.
     *
     * @param item         the item to be coerced
     * @param requiredType the required item type
     * @param request      the input to the coercion
     * @return the converted value. We define this as a grounded value because in the vast
     * majority of cases it will be a single item, and in other cases (a typed node with a list type,
     * an array of atomic values) there is little benefit in lazy evaluation. The implementation
     * is responsible for ensuring that the returned value does indeed consist entirely of items
     * that match the required item type; it is not responsible for cardinality checking. The item
     * type checking can be achieved, if required, by a callback to the check() method.
     * @throws net.sf.saxon.trans.XPathException if the value cannot be converted to the required type
     */

    public abstract GroundedValue coerceItem(Item item, ItemType requiredType, CoercionRequest request) throws XPathException;

    /**
     * Check that the item is actually an instance of the required type, throwing an error if not
     * @param item the item to be checked
     * @param requiredType the required type
     * @param request diagnostic data
     * @throws XPathException if the item is not an instance of the required type
     */
    protected final void check(Item item, ItemType requiredType, CoercionRequest request) throws XPathException {
        if (!requiredType.matches(item)) {
            throw coercionError(item, requiredType, request, null);
        }
    }

    /**
     * Create an exception object representing a coercion error
     *
     * @param value        the input value to the coercion
     * @param requiredType the required item type
     * @param request      the coercion request details
     * @param explanation   further explanation of the reason for failure; may be null
     * @return an exception for the caller to throw
     */

    protected XPathException coercionError(Sequence value, ItemType requiredType, CoercionRequest request, String explanation) {
        RoleDiagnostic role = request.roleSupplier.get();
        String representation = value instanceof Item ? Err.depict((Item)value) : value.toString();
        String message = "Failed to convert the " + role.getMessage() + ": " +
                "Cannot convert " + representation + " to "
                + requiredType;
        if (explanation != null) {
            message = message + ". " + explanation;
        }
        return new XPathException(message)
                .withErrorCode(role.getErrorCode())
                .withLocation(request.locator);
    }
    

}


