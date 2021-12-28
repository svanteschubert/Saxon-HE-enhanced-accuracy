////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

/**
 * The expression is generated when compiling the current() function in XSLT. It differs from
 * the ContextItemExpression "." only in the error code that is returned when there is no context item.
 */

public class CurrentItemExpression extends ContextItemExpression {

    public CurrentItemExpression() {
        setErrorCodeForUndefinedContext("XTDE1360", false);
    }

}

