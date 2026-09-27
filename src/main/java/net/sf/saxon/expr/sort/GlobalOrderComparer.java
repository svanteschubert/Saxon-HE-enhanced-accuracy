////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.ma.jnode.RootJNode;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.NodeInfo;

import java.util.Comparator;

/**
 * A Comparer used for comparing nodes in document order. This
 * comparer is used when there is no guarantee that the nodes being compared
 * come from the same document
 */

//@CSharpInjectMembers(code="public override int Compare(net.sf.saxon.om.NodeInfo a, net.sf.saxon.om.NodeInfo b) { return compare(a, b); }")
public final class GlobalOrderComparer implements Comparator<GNode> {

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
    public int compare(GNode g1, GNode g2) {
        if (g1 == g2) {
            return 0;
        }
        if (g1 instanceof NodeInfo x1 && g2 instanceof NodeInfo x2) {
            long d1 = x1.getTreeInfo().getDocumentNumber();
            long d2 = x2.getTreeInfo().getDocumentNumber();
            if (d1 == d2) {
                return x1.compareOrder(x2);
            }
            return (int) Long.signum(d1 - d2);
        } else if (g1 instanceof JNode j1 && g2 instanceof JNode j2) {
            RootJNode rootA = j1.getRoot();
            RootJNode rootB = j2.getRoot();
            if (rootA == rootB) {
                return j1.compareOrder(j2);
            } else {
                return rootA.compareOrder(rootB)    ;
            }
        } else return g1 instanceof NodeInfo ? -1 : +1;
    }
}

