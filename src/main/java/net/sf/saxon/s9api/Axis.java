////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.om.AxisInfo;

/**
 * This is an enumeration class containing constants representing the thirteen axes available
 * in XPath versions from 1.0 to 3.1, together with four new axes defined experimentally
 * in XPath 4.0.
 */

public enum Axis {
    /**
     * The ancestor axis: contains a node's parent, the parent's parent, and so on, up to the root
     * of the tree.
     */
    ANCESTOR(AxisInfo.ANCESTOR),
    /**
     * The ancestor-or-self axis: contains the ancestors of a node together with the node itself
     */
    ANCESTOR_OR_SELF(AxisInfo.ANCESTOR_OR_SELF),
    /**
     * The attribute axis. For an element this contains the attributes of the element (these
     * do not include namespace declarations); for other node kinds the axis is empty.
     */
    ATTRIBUTE(AxisInfo.ATTRIBUTE),
    /**
     * The child axis. For a document or element node this contains the children of the node,
     * which will all be elements, text nodes, comments, or processing instructions. For other
     * node kinds the axis is empty.
     */
    CHILD(AxisInfo.CHILD),
    /**
     * The descendant axis. Contains the children of the node, and their children, expanded
     * recursively.
     */
    DESCENDANT(AxisInfo.DESCENDANT),
    /**
     * The descendant-or-self axis. Contains the descendants of a node together with the node itself.
     */
    DESCENDANT_OR_SELF(AxisInfo.DESCENDANT_OR_SELF),
    /**
     * The following axis. Contains all nodes that follow the origin node in document order,
     * other than its descendants.
     */
    FOLLOWING(AxisInfo.FOLLOWING),
    /**
     * The following-or-self axis. Contains all nodes on the following axis, together with
     * the origin node itself. This is newly defined in the draft XPath 4.0 specification which
     * is subject to change.
     */
    FOLLOWING_OR_SELF(AxisInfo.FOLLOWING_OR_SELF),
    /**
     * The following-sibling axis. Contains all children of the origin node's parent that follow
     * the origin node in document order. The axis is empty in the case of a node that has no parent,
     * or that is the last child of its parent.
     */
    FOLLOWING_SIBLING(AxisInfo.FOLLOWING_SIBLING),
    /**
     * The following-sibling-or-self axis. Contains all nodes on the following-sibling axis, together with
     * the origin node itself. This is newly defined in the draft XPath 4.0 specification which
     * is subject to change.
     */
    FOLLOWING_SIBLING_OR_SELF(AxisInfo.FOLLOWING_SIBLING_OR_SELF),
    /**
     * The parent axis. Contains the origin node's parent; the axis is empty in the case of a node
     * that has no parent.
     */
    PARENT(AxisInfo.PARENT),
    /**
     * The preceding axis. Contains all nodes that precede the origin node in document order,
     * other than its ancestors.
     */
    PRECEDING(AxisInfo.PRECEDING),
    /**
     * The preceding-or-self axis. Contains all nodes on the preceding axis, together with
     * the origin node itself. This is newly defined in the draft XPath 4.0 specification which
     * is subject to change.
     */
    PRECEDING_OR_SELF(AxisInfo.PRECEDING_OR_SELF),
    /**
     * The following-sibling axis. Contains all children of the origin node's parent that precede
     * the origin node in document order. The axis is empty in the case of a node that has no parent,
     * or that is the first child of its parent.
     */
    PRECEDING_SIBLING(AxisInfo.PRECEDING_SIBLING),
    /**
     * The preceding-sibling-or-self axis. Contains all nodes on the preceding-sibling axis, together with
     * the origin node itself. This is newly defined in the draft XPath 4.0 specification which
     * is subject to change.
     */
    PRECEDING_SIBLING_OR_SELF(AxisInfo.PRECEDING_SIBLING_OR_SELF),
    /**
     * The self axis. Contains the origin node only.
     */
    SELF(AxisInfo.SELF),
    /**
     * The namespace axis. For an element node, this contains one namespace node for each in-scope namespace
     * declaration, that is, a namespace declared either on the element itself, or on an ancestor element (if
     * not overridden); also contains a namespace node for the implicitly-declared XML namespace. For other
     * node kinds the axis is empty.
     */
    NAMESPACE(AxisInfo.NAMESPACE);


    private final int axisNumber;

    /**
     * Create an Axis
     *
     * @param axisNumber the internal axis number as defined in class {@link net.sf.saxon.om.AxisInfo}
     */

    Axis(int axisNumber) {
        this.axisNumber = axisNumber;
    }

    /**
     * Get the axis number, as defined in class {@link net.sf.saxon.om.AxisInfo}
     *
     * @return the axis number
     */
    public int getAxisNumber() {
        return axisNumber;
    }

}

