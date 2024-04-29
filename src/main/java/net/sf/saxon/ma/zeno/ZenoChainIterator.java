////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.zeno;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * A (Java) Iterator over a ZenoChain
 * @param <U> the type of the elements in the chain
 */
public class ZenoChainIterator<U> implements Iterator<U> {

    private int majorIndex = 0;
    private int minorIndex = 0;
    private final ArrayList<ArrayList<U>> masterList;

    public ZenoChainIterator(ArrayList<ArrayList<U>> masterList) {
        this.masterList = masterList;
    }

    public boolean hasNext() {
        return majorIndex < masterList.size() && minorIndex < masterList.get(majorIndex).size();
    }

    public U next() {
        ArrayList<U> currentSegment = masterList.get(majorIndex);
        U result = currentSegment.get(minorIndex);
        if (++minorIndex >= currentSegment.size()) {
            majorIndex++;
            minorIndex = 0;
            // Assumes no zero-length segments
        }
        return result;
    }
}

