////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

/**
 * The usage of an operand defines how the containing expression makes use of the value of the operand,
 * as defined in the XSLT 3.0 specification.
 */
public enum OperandUsage {

    ABSORPTION,
    INSPECTION,
    TRANSMISSION,
    NAVIGATION
}



