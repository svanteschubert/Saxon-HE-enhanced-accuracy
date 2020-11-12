////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.om.GenericTreeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;


/**
 * A TypeStrippedDocument represents a view of a real Document in which all nodes are
 * untyped
 */

public class TypeStrippedDocument extends GenericTreeInfo {

    TreeInfo underlyingTree;

    /**
     * Create a type-stripped view of a document
     *
     * @param doc the underlying document
     */

    public TypeStrippedDocument(TreeInfo doc) {
        super(doc.getConfiguration());
        setRootNode(wrap(doc.getRootNode()));
        this.underlyingTree = doc;
    }

    /**
     * Create a wrapped node within this document
     */

    public TypeStrippedNode wrap(NodeInfo node) {
        return TypeStrippedNode.makeWrapper(node, this, null);
    }

    /**
     * Get the element with a given ID, if any
     *
     * @param id        the required ID value
     * @param getParent
     * @return the element with the given ID value, or null if there is none.
     */

    /*@Nullable*/
    @Override
    public NodeInfo selectID(String id, boolean getParent) {
        NodeInfo n = underlyingTree.selectID(id, false);
        if (n == null) {
            return null;
        } else {
            return wrap(n);
        }
    }

}

