// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.transpile.CSharpSimpleEnum;

/**
 * Indicates whether something is required, prohibited, or optional. Used for
 * an attributeUse in a schema, also for the existence of a context item
 * in the static context.
 */

@CSharpSimpleEnum
public enum Optionality {REQUIRED, PROHIBITED, OPTIONAL}

