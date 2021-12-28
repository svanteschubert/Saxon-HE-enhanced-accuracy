////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * ContextMappingFunction is an interface that must be satisfied by an object passed to a
 * ContextMappingIterator. It represents an object which, given an Item, can return a
 * SequenceIterator that delivers a sequence of zero or more Items.
 * <p>This is a specialization of the more general MappingFunction class: it differs in that
 * each item being processed becomes the context item while it is being processed.</p>
 *
 */

public interface ContextMappingFunction {

    /**
     * Map one item to a sequence.
     *
     * @param context The processing context. The item to be mapped is the context item identified
     *                from this context: the values of position() and last() also relate to the set of items being mapped
     * @return a SequenceIterator over the sequence of items that the supplied input
     *         item maps to
     * @throws XPathException if a dynamic error is detected
     */

    SequenceIterator map(XPathContext context) throws XPathException;

}

