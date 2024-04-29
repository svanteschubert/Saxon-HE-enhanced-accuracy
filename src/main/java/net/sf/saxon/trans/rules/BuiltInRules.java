////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.rules;

import net.sf.saxon.transpile.CSharpSimpleEnum;

/**
 * Enumeration representing the permitted values of <code>xsl:mode/@on-no-match</code>, decomposed into more
 * primitive actions
 */

@CSharpSimpleEnum
public enum BuiltInRules {

    DEEP_COPY,
    DEEP_SKIP,
    FAIL,
    SHALLOW_COPY,
    APPLY_TEMPLATES_TO_ATTRIBUTES,
    APPLY_TEMPLATES_TO_CHILDREN;

}

