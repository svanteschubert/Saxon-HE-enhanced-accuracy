////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;

/**
 * A Comparer used for comparing nodes in document order. This
 * comparer assumes that the nodes being compared come from the same document
 *
 * @author Michael H. Kay
 */

public final class LocalOrderComparer implements ItemOrderComparer {

    private static LocalOrderComparer instance = new LocalOrderComparer();

    /**
     * Get an instance of a LocalOrderComparer. The class maintains no state
     * so this returns the same instance every time.
     */

    /*@NotNull*/
    public static LocalOrderComparer getInstance() {
        return instance;
    }

    @Override
    public int compare(Item a, Item b) {
        NodeInfo n1 = (NodeInfo) a;
        NodeInfo n2 = (NodeInfo) b;
        return n1.compareOrder(n2);
    }
}

