////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.om.NodeInfo;

import java.util.Comparator;

/**
 * A Comparer used for comparing nodes in document order. This
 * comparer assumes that the nodes being compared come from the same document
 */

//@CSharpInjectMembers(code = "public override int Compare(net.sf.saxon.om.NodeInfo a, net.sf.saxon.om.NodeInfo b) { return compare(a, b); }")
public final class LocalOrderComparer implements Comparator<NodeInfo> {

    private static final LocalOrderComparer instance = new LocalOrderComparer();

    /**
     * Get an instance of a LocalOrderComparer. The class maintains no state
     * so this returns the same instance every time.
     * @return an instance of a LocalOrderComparer
     */

    /*@NotNull*/
    public static LocalOrderComparer getInstance() {
        return instance;
    }

    @Override
    public int compare(NodeInfo a, NodeInfo b) {
        return a.compareOrder(b);
    }
}

