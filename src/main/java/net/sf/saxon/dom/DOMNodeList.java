////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import org.w3c.dom.Node;

import java.util.List;

/**
 * This class wraps a list of nodes as a DOM NodeList
 */

public final class DOMNodeList implements org.w3c.dom.NodeList {
    private final List<Node> sequence;

    /**
     * Construct an node list that wraps a supplied list of DOM Nodes.
     *
     * @param extent the list of nodes to be wrapped
     */

    public DOMNodeList(List<Node> extent) {
        sequence = extent;
    }

    /**
     * return the number of nodes in the list (DOM method)
     */

    @Override
    public int getLength() {
        return sequence.size();
    }

    /**
     * Return the n'th item in the list (DOM method)
     *
     * @throws java.lang.ClassCastException if the item is not a DOM Node
     */

    /*@Nullable*/
    @Override
    public Node item(int index) {
        if (index < 0 || index >= sequence.size()) {
            return null;
        } else {
            return sequence.get(index);
        }
    }

}

