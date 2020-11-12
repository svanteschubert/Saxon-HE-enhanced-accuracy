////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;

/**
 * Interface implemented by an iterator that can deliver atomized results.
 *
 * <p>This exists to make it possible to iterate over the atomized nodes
 * in a sequence, especially a sequence obtained from the TinyTree,
 * without actually instantiating the nodes themselves.</p>
 */

public interface AtomizedValueIterator extends SequenceIterator {

    /**
     * Deliver the atomic value that is next in the atomized result
     * @return the next atomic value
     * @throws XPathException if a failure occurs reading or atomizing the next value
     */

    AtomicSequence nextAtomizedValue() throws XPathException;

}

