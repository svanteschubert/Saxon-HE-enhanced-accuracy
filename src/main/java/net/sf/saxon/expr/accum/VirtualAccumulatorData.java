////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.accum;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.wrapper.VirtualCopy;

/**
 * Holds the values of an accumulator function for a virtual copy of a document (where the accumulator values
 * are copies of those on the underlying document)
 */
public class VirtualAccumulatorData implements IAccumulatorData {

    private IAccumulatorData realData;

    public VirtualAccumulatorData(IAccumulatorData realData) {
        this.realData = realData;
    }

    @Override
    public Accumulator getAccumulator() {
        return realData.getAccumulator();
    }

    /**
     * Get the value of the accumulator for a given node
     *
     * @param node        the node in question
     * @param postDescent false if the pre-descent value of the accumulator is required;
     *                    false if the post-descent value is wanted.
     * @return the value of the accumulator for this node
     */

    @Override
    public Sequence getValue(NodeInfo node, boolean postDescent) throws XPathException {
        NodeInfo original = ((VirtualCopy)node).getOriginalNode();
        return realData.getValue(original, postDescent);
    }


}
