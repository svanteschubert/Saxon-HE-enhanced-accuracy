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

/**
 * Represents the values of an accumulator whose evaluation has failed. The error is retained
 * until referenced using accumulator-before() or accumulator-after().
 */
public class FailedAccumulatorData implements IAccumulatorData {

    private Accumulator acc;
    private XPathException error;

    public FailedAccumulatorData(Accumulator acc, XPathException error) {
        this.acc = acc;
        this.error = error;
    }

    @Override
    public Accumulator getAccumulator() {
        return acc;
    }

    @Override
    public Sequence getValue(NodeInfo node, boolean postDescent) throws XPathException {
        throw error;
    }
}
