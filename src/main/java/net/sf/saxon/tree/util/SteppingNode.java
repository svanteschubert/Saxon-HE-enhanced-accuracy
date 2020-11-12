////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;


import net.sf.saxon.om.NodeInfo;

/**
 * This interface can be implemented by an implementation of NodeInfo to take advantage of a generic implementation
 * of the descendant axis found in class {@link net.sf.saxon.tree.util.SteppingNavigator}
 */
public interface SteppingNode<N extends SteppingNode> extends NodeInfo {

    /**
     * Get the parent of this node
     *
     * @return the parent of this node; or null if it is the root of the tree
     */

    @Override
    N getParent();

    /**
     * Get the next sibling of this node
     *
     * @return the next sibling if there is one, or null otherwise
     */
    N getNextSibling();

    /**
     * Get the previous sibling of this node
     *
     * @return the previous sibling if there is one, or null otherwise
     */

    N getPreviousSibling();

    /**
     * Get the first child of this node
     *
     * @return the first child if there is one, or null otherwise
     */

    N getFirstChild();

    /**
     * Find the next matching element in document order; that is, the first child element
     * with the required name if there is one; otherwise the next sibling element
     * if there is one; otherwise the next sibling element of the parent, grandparent, etc, up to the anchor element.
     *
     * @param anchor the root of the tree within which navigation is confined
     * @param uri    the required namespace URI, or null if any namespace is acceptable
     * @param local  the required local name, or null if any local name is acceptable
     * @return the next element after this one in document order, with the given URI and local name
     *         if specified, or null if this is the last node in the document, or the last node
     *         within the subtree being navigated
     */

    N getSuccessorElement(N anchor, String uri, String local);

}

