////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.accum;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

/**
 * Holds the values of an accumulator function for one non-streamed document
 */
public interface IAccumulatorData {

    /**
     * Get the associated accumulator
     *
     * @return the accumulator
     */

    Accumulator getAccumulator();

    /**
     * Get the value of the accumulator for a given node
     *
     * @param node        the node in question
     * @param postDescent false if the pre-descent value of the accumulator is required;
     *                    false if the post-descent value is wanted.
     * @return the value of the accumulator for this node
     */

    Sequence getValue(NodeInfo node, boolean postDescent) throws XPathException;


}
