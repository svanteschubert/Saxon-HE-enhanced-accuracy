////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * A SequenceIterator that throws an exception as soon as its next() method is called. Used when
 * the method that returns the iterator isn't allowed to throw a checked exception itself.
 */
public class ErrorIterator implements SequenceIterator {

    private XPathException exception;

    public ErrorIterator(XPathException exception) {
        this.exception = exception;
    }

    /**
     * Get the next item in the sequence. This method changes the state of the
     * iterator, in particular it affects the result of subsequent calls of
     * position() and current().
     *
     * @return the next item, or null if there are no more items. Once a call
     *         on next() has returned null, no further calls should be made. The preferred
     *         action for an iterator if subsequent calls on next() are made is to return
     *         null again, and all implementations within Saxon follow this rule.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs retrieving the next item
     * @since 8.4
     */

    @Override
    public Item next() throws XPathException {
        throw exception;
    }

    @Override
    public void close() {

    }


}

