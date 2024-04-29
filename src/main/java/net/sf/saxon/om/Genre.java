////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.transpile.CSharpSimpleEnum;

/**
 * The Genre of an item is the top-level classification of its item type: one of Atomic, Node, Function,
 * Map, Array, or External
 */

@CSharpSimpleEnum
public enum Genre {

    ANY,
    ATOMIC,
    NODE,
    FUNCTION,
    MAP,
    ARRAY,
    EXTERNAL;

}

