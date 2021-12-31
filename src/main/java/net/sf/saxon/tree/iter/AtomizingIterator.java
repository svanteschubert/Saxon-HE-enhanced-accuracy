////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;

/**
 * AtomizingIterator returns the atomization of an underlying sequence supplied
 * as an iterator.  We use a specialist class rather than a general-purpose
 * MappingIterator for performance, especially as the relationship of items
 * in the result sequence to those in the base sequence is often one-to-one.
 * <p>This AtomizingIterator is capable of handling list-typed nodes whose atomized value
 * is a sequence of more than one item. When it is known that all input will be untyped,
 * an {@link UntypedAtomizingIterator} is used in preference.</p>
 */
                                    
public class AtomizingIterator implements SequenceIterator /* of AtomicValue */ {

    private SequenceIterator base;
    /*@Nullable*/ private AtomicSequence currentValue = null;
    private int currentValuePosition = 1;
    private int currentValueSize = 1;
    private RoleDiagnostic roleDiagnostic;

    /**
     * Construct an AtomizingIterator that will atomize the values returned by the base iterator.
     *
     * @param base the base iterator
     */

    public AtomizingIterator(SequenceIterator base) {
        this.base = base;
    }

    public void setRoleDiagnostic(RoleDiagnostic role) {
        this.roleDiagnostic = role;
    }

    /*@Nullable*/
    @Override
    public AtomicValue next() throws XPathException {
        while (true) {
            if (currentValue != null) {
                if (currentValuePosition < currentValueSize) {
                    return currentValue.itemAt(currentValuePosition++);
                } else {
                    currentValue = null;
                }
            }
            Item nextSource = base.next();
            if (nextSource != null) {
                try {
                    AtomicSequence v = nextSource.atomize();
                    if (v instanceof AtomicValue) {
                        return (AtomicValue) v;
                    } else {
                        currentValue = v;
                        currentValuePosition = 0;
                        currentValueSize = currentValue.getLength();
                        // now go round the loop to get the first item from the atomized value
                    }
                } catch (XPathException e) {
                    if (roleDiagnostic == null) {
                        throw e;
                    } else {
                        String message = e.getMessage() + ". Failed while atomizing the " + roleDiagnostic.getMessage();
                        throw new XPathException(message, e.getErrorCodeLocalPart(), e.getLocator());
                    }
                }
            } else {
                currentValue = null;
                return null;
            }
        }
    }

    @Override
    public void close() {
        base.close();
    }


}

