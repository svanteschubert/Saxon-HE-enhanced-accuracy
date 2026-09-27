////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

/**
 * Enumeration class giving the different streamability categories defined for stylesheet functions in XSLT 3.0
 */

public enum FunctionStreamability {
    UNCLASSIFIED,
    ABSORBING,
    INSPECTION,
    FILTER,
    SHALLOW_DESCENT,
    DEEP_DESCENT,
    ASCENT
}
