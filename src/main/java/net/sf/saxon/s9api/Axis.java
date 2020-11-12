////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.om.AxisInfo;

/**
 * This is an enumeration class containing constants representing the thirteen XPath axes
 */
public enum Axis {
    ANCESTOR(AxisInfo.ANCESTOR),
    ANCESTOR_OR_SELF(AxisInfo.ANCESTOR_OR_SELF),
    ATTRIBUTE(AxisInfo.ATTRIBUTE),
    CHILD(AxisInfo.CHILD),
    DESCENDANT(AxisInfo.DESCENDANT),
    DESCENDANT_OR_SELF(AxisInfo.DESCENDANT_OR_SELF),
    FOLLOWING(AxisInfo.FOLLOWING),
    FOLLOWING_SIBLING(AxisInfo.FOLLOWING_SIBLING),
    PARENT(AxisInfo.PARENT),
    PRECEDING(AxisInfo.PRECEDING),
    PRECEDING_SIBLING(AxisInfo.PRECEDING_SIBLING),
    SELF(AxisInfo.SELF),
    NAMESPACE(AxisInfo.NAMESPACE);

    private int number;

    /**
     * Create an Axis
     *
     * @param number the internal axis number as defined in class {@link net.sf.saxon.om.AxisInfo}
     */

    private Axis(int number) {
        this.number = number;
    }

    /**
     * Get the axis number, as defined in class {@link net.sf.saxon.om.AxisInfo}
     *
     * @return the axis number
     */
    public int getAxisNumber() {
        return number;
    }
}

