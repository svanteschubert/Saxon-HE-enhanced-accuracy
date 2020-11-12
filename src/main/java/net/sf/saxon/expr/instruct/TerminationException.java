////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.trans.XPathException;

/**
 * An exception thrown by xsl:message terminate="yes".
 */

public class TerminationException extends XPathException {

    /**
     * Construct a TerminationException
     *
     * @param message the text of the message to be output
     */

    public TerminationException(String message) {
        super(message);
        setErrorCode("XTMM9000");
    }

}
