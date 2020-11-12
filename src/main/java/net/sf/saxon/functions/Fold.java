////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

/* Interface implemented by operations that compute the value of an expression using
 * a "fold" operation: that is, by applying a function to each item in the input sequence
 * in turn, maintaining working data as the the items are processed, and finally computing
 * the final result of the expression from the value of the working data.
 */
public interface Fold {

    /**
     * Process one item in the input sequence, returning a new copy of the working data
     * @param item the item to be processed from the input sequence
     * @throws XPathException if a dynamic error occurs
     */

    void processItem(Item item) throws XPathException;

    /**
     * Ask whether the computation has completed. A function that can deliver its final
     * result without reading the whole input should return true; this will be followed
     * by a call on result() to deliver the final result.
     * @return true if the result of the function is now available even though not all
     * items in the sequence have been processed
     */

    boolean isFinished();

    /**
     * Compute the final result of the function, when all the input has been processed
     * @return the result of the function
     * @throws XPathException if a dynamic error occurs
     */

    Sequence result() throws XPathException;

}

