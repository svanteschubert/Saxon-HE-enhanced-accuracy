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
 * comparer is used when there is no guarantee that the nodes being compared
 * come from the same document
 */

//@CSharpInjectMembers(code="public override int Compare(net.sf.saxon.om.NodeInfo a, net.sf.saxon.om.NodeInfo b) { return compare(a, b); }")
public final class GlobalOrderComparer implements Comparator<NodeInfo> {

    private static final GlobalOrderComparer instance = new GlobalOrderComparer();

    /**
     * Get an instance of a GlobalOrderComparer. The class maintains no state
     * so this returns the same instance every time.
     * @return an instance of a GlobalOrderComparer
     */

    public static GlobalOrderComparer getInstance() {
        return instance;
    }

    @Override
    public int compare(NodeInfo a, /*@NotNull*/ NodeInfo b) {
        if (a == b) {
            return 0;
        }
        long d1 = a.getTreeInfo().getDocumentNumber();
        long d2 = b.getTreeInfo().getDocumentNumber();
        if (d1 == d2) {
            return a.compareOrder(b);
        }
        return (int)Long.signum(d1 - d2);
    }
}

