////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.SequenceIterator;


/**
 * A SequenceIterator is used to iterate over a sequence. A LookaheadIterator
 * is one that supports a hasNext() method to determine if there are more nodes
 * after the current node.
 */

public interface LookaheadIterator extends SequenceIterator {

    /**
     * Determine whether there are more items to come. Note that this operation
     * is stateless and it is not necessary (or usual) to call it before calling
     * next(). It is used only when there is an explicit need to tell if we
     * are at the last element.
     * <p>This method must not be called unless the result of getProperties() on the iterator
     * includes the property {@link net.sf.saxon.om.SequenceIterator.Property#LOOKAHEAD} </p>
     *
     * @return true if there are more items in the sequence
     */

    boolean hasNext();


}

