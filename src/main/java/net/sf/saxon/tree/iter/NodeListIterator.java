////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;

import java.util.List;

/**
 * Class ListIterator, iterates over a sequence of items held in a Java List.
 */

public class NodeListIterator extends ListIterator.Of<NodeInfo> implements AxisIterator {

    /**
     * Create a ListIterator over a given List
     *
     * @param list the list: all objects in the list must be instances of {@link Item}
     */

    public NodeListIterator(List<NodeInfo> list) {
        super(list);
    }

    /*@Nullable*/
    @Override
    public NodeInfo next() {
        return (NodeInfo)super.next();
    }

}

