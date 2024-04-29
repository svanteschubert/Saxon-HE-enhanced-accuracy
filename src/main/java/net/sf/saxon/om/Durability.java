////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.transpile.CSharpSimpleEnum;

/**
 * The Durability of a node affects how it is handled for the purposes of caching by memo functions.
 */

@CSharpSimpleEnum
public enum Durability {

    /**
     * A node is lasting if it is expected to remain accessible throughout the duration of a transformation
     */
    LASTING,
    /**
     * A node is temporary if it is constructed during the course of a transformation, and is likely
     * to be garbage-collected when it goes out of scope
     */
    TEMPORARY,
    /**
     * A node is fleeting if it is part of a streamed document, meaning that it disappears as soon as it
     * has been processed
     */
    FLEETING,
    /**
     * A mutable node is one that can be modified during the course of a query or transformation. Note that
     * wrapped external nodes (such as DOM nodes) are not considered mutable, because Saxon has no control
     * over any modification, and external changes are not allowed even though Saxon cannot prevent them
     * happening.
     */
    MUTABLE,
    /**
     * Durability undefined means we don't know
     */
    UNDEFINED

}

