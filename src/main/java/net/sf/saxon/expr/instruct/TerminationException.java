////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.s9api.Message;
import net.sf.saxon.trans.XPathException;

/**
 * An exception thrown by xsl:message terminate="yes".
 */

public class TerminationException extends XPathException {

    private final Message finalMessage;

    /**
     * Construct a TerminationException
     *
     * @param exceptionText the exception text of the message to be output (typically indicating that processing
     *                was terminated by use of xsl:message)
     * @param messageContent the final xsl:message content that led to termination
     */

    public TerminationException(String exceptionText, Message messageContent) {
        super(exceptionText, "XTMM9000");
        finalMessage = messageContent;
    }

    /**
     * Get the Message object that resulted in this exception
     * @return the Message
     */

    public Message getFinalMessage() {
        return finalMessage;
    }

}
