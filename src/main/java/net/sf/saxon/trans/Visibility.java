////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.transpile.CSharpSimpleEnum;

/**
 * Enumeration class giving the different visibility levels defined for components in XSLT 3.0
 */

@CSharpSimpleEnum
public enum Visibility {
    PUBLIC,
    PRIVATE,
    FINAL,
    ABSTRACT,
    HIDDEN,
    UNDEFINED
}
