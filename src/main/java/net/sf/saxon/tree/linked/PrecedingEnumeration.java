////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.om.NodeInfo;

import java.util.function.Predicate;

final class PrecedingEnumeration extends TreeEnumeration {

    /*@Nullable*/ NodeImpl nextAncestor;

    public PrecedingEnumeration(/*@NotNull*/ NodeImpl node, Predicate<? super NodeInfo> nodeTest) {
        super(node, nodeTest);

        // we need to avoid returning ancestors of the starting node
        nextAncestor = (NodeImpl) node.getParent();
        advance();
    }


    /**
     * Special code to skip the ancestors of the start node
     */

    @Override
    protected boolean conforms(/*@Nullable*/ NodeImpl node) {
        // ASSERT: we'll never test the root node, because it's always
        // an ancestor, so nextAncestor will never be null.
        if (node != null) {
            if (node.equals(nextAncestor)) {
                nextAncestor = nextAncestor.getParent();
                return false;
            }
        }
        return super.conforms(node);
    }

    @Override
    protected void step() {
        next = next.getPreviousInDocument();
    }

}

