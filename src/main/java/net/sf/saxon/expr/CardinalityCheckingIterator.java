////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.value.Cardinality;

/**
 * CardinalityCheckingIterator returns the items in an underlying sequence
 * unchanged, but checks that the number of items conforms to the required
 * cardinality. Because cardinality checks are required to take place even
 * if the consumer of the sequence does not require all the items, we read
 * the first two items at initialization time. This is sufficient to perform
 * the checks; after that we can simply return the items from the base sequence.
 */

public final class CardinalityCheckingIterator implements SequenceIterator {

    private SequenceIterator base;
    private Location locator;
    /*@Nullable*/ private Item first = null;
    private Item second = null;
    private int position = 0;

    /**
     * Construct an CardinalityCheckingIterator that will return the same items as the base sequence,
     * checking how many there are
     *
     * @param base                the base iterator
     * @param requiredCardinality the required Cardinality
     * @param role information for use if a failure occurs
     * @param locator the location in the source stylesheet or query
     * @throws XPathException if a failure is detected
     */

    public CardinalityCheckingIterator(SequenceIterator base, int requiredCardinality,
                                       RoleDiagnostic role, Location locator)
            throws XPathException {
        this.base = base;
        this.locator = locator;
        first = base.next();
        if (first == null) {
            if (!Cardinality.allowsZero(requiredCardinality)) {
                typeError("An empty sequence is not allowed as the " +
                        role.getMessage(), role.getErrorCode());
            }
        } else {
            if (requiredCardinality == StaticProperty.EMPTY) {
                typeError("The only value allowed for the " +
                        role.getMessage() + " is an empty sequence", role.getErrorCode());
            }
            second = base.next();
            if (second != null && !Cardinality.allowsMany(requiredCardinality)) {
                Item[] leaders = new Item[]{first, second};
                typeError("A sequence of more than one item is not allowed as the " +
                        role.getMessage() + CardinalityChecker.depictSequenceStart(new ArrayIterator<>(leaders), 2),
                        role.getErrorCode());
            }
        }
    }

    @Override
    public Item next() throws XPathException {
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
        Item current = base.next();
        if (current == null) {
            position = -1;
        } else {
            position++;
        }
        return current;
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

