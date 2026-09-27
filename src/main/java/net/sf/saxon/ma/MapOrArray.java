// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma;

import net.sf.saxon.ma.jnode.RootJNode;
import net.sf.saxon.om.FunctionItem;

public abstract class MapOrArray implements FunctionItem {

    private RootJNode rootJNode;

    public synchronized RootJNode obtainRootJNode() {
        if (rootJNode == null) {
            return rootJNode = new RootJNode(this);
        } else {
            return rootJNode;
        }
    }
}

