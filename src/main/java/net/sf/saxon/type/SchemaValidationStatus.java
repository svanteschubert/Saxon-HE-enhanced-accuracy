////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.transpile.CSharpSimpleEnum;

/**
 * Internal indicator representing progress in validating components of a schema
 */

@CSharpSimpleEnum
public enum SchemaValidationStatus {
    UNVALIDATED,
    FIXED_UP,
    VALIDATING,
    VALIDATED,
    INVALID,
    INCOMPLETE
}

