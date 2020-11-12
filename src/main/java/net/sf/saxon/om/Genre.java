////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

/**
 * The Genre of an item is the top-level classification of its item type: one of Atomic, Node, Function,
 * Map, Array, or External
 */

public enum Genre {

    ANY("any item"),
    ATOMIC("an atomic value"),
    NODE("a node"),
    FUNCTION("a function"),
    MAP("a map"),
    ARRAY("an array"),
    EXTERNAL("an external object");

    private String description;

    Genre(String desc) {
        this.description = desc;
    }

    public String getDescription() {
        return description;
    }

}

