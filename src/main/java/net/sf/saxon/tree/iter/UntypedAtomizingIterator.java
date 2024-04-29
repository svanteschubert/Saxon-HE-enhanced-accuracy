////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;

/**
 * AtomizingIterator returns the atomization of an underlying sequence supplied
 * as an iterator.  We use a specialist class rather than a general-purpose
 * MappingIterator for performance, especially as the relationship of items
 * in the result sequence to those in the base sequence is often one-to-one.
 * <p>This UntypedAtomizingIterator is used only when it is known that the input
 * sequence consists entirely of nodes, and that all nodes will be untyped.</p>
 */

public class UntypedAtomizingIterator implements SequenceIterator,
        LastPositionFinder, LookaheadIterator {

    private final SequenceIterator base;

    /**
     * Construct an AtomizingIterator that will atomize the values returned by the base iterator.
     *
     * @param base the base iterator
     */

    public UntypedAtomizingIterator(SequenceIterator base) {
        this.base = base;
    }

    /*@Nullable*/
    @Override
    public AtomicValue next() {
        try {
            Item nextSource = base.next();
            if (nextSource == null) {
                return null;
            } else {
                return (AtomicValue) nextSource.atomize();
            }
        } catch (XPathException e) {
            throw new UncheckedXPathException(e);
        }
    }

    @Override
    public void close() {
        base.close();
    }

    /**
     * Ask whether this iterator supports use of the {@link #getLength()} method. This
     * method should always be called before calling {@link #getLength()}, because an iterator
     * that implements this interface may support use of {@link #getLength()} in some situations
     * and not in others
     *
     * @return true if the {@link #getLength()} method can be called to determine the length
     * of the underlying sequence.
     */
    @Override
    public boolean supportsGetLength() {
        return SequenceTool.supportsGetLength(base);
    }

    @Override
    public int getLength() {
        return SequenceTool.getLength(base);
    }

    @Override
    public boolean supportsHasNext() {
        return base instanceof LookaheadIterator && ((LookaheadIterator) base).supportsHasNext();
    }

    @Override
    public boolean hasNext() {
        return ((LookaheadIterator) base).hasNext();
    }
}

