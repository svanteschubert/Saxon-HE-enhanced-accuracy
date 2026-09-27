////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.TwoItemIterator;
import net.sf.saxon.value.Cardinality;

import java.util.function.Supplier;

/**
 * CardinalityCheckingIterator returns the items in an underlying sequence
 * unchanged, but checks that the number of items conforms to the required
 * cardinality. Because cardinality checks are required to take place even
 * if the consumer of the sequence does not require all the items, we read
 * the first two items at initialization time. This is sufficient to perform
 * the checks; after that we can simply return the items from the base sequence.
 */

public final class CardinalityCheckingIterator implements SequenceIterator {

    private final SequenceIterator base;
    private final Location locator;
    /*@Nullable*/ private Item first = null;
    private Item second = null;
    private int position = 0;

    /**
     * Construct an CardinalityCheckingIterator that will return the same items as the base sequence,
     * checking how many there are
     *
     * @param base                the base iterator
     * @param requiredCardinality the required Cardinality
     * @param roleSupplier information for use if a failure occurs
     * @param locator the location in the source stylesheet or query
     * @throws XPathException if a failure is detected
     */

    public CardinalityCheckingIterator(SequenceIterator base, int requiredCardinality,
                                       Supplier<RoleDiagnostic> roleSupplier, Location locator)
            throws XPathException {
        this.base = base;
        this.locator = locator;
        try {
            first = base.next();
            if (first == null) {
                RoleDiagnostic role = roleSupplier.get();
                if (!Cardinality.allowsZero(requiredCardinality)) {
                    typeError("An empty sequence is not allowed as the " +
                            role.getMessage(), role.getErrorCode());
                }
            } else {
                if (requiredCardinality == StaticProperty.EMPTY) {
                    RoleDiagnostic role = roleSupplier.get();
                    typeError("The only value allowed for the " +
                            role.getMessage() + " is an empty sequence", role.getErrorCode());
                }
                second = base.next();
                if (second != null && !Cardinality.allowsMany(requiredCardinality)) {
                    RoleDiagnostic role = roleSupplier.get();
                    typeError("A sequence of more than one item {" +
                                      CardinalityChecker.depictSequenceStart(new TwoItemIterator(first, second), 2) +
                                      "} is not allowed as the " +
                                      role.getMessage(),
                                role.getErrorCode());
                }
            }
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
        }
    }

    @Override
    public Item next() {
        if (position < 2) {
            if (position == 0) {
                Item current = first;
                position = first == null ? -1 : 1;
                return current;
            } else if (position == 1) {
                Item current = second;
                position = second == null ? -1 : 2;
                return current;
            } else {
                // position == -1
                return null;
            }
        }
        Item nextBase = base.next();
        if (nextBase == null) {
            position = -1;
        } else {
            position++;
        }
        return nextBase;
    }

    @Override
    public void close() {
        base.close();
    }


    private void typeError(String message, String errorCode) throws XPathException {
        XPathException e = new XPathException(message, errorCode, locator);
        e.setIsTypeError(!errorCode.startsWith("FORG"));
        throw e;
    }


}

