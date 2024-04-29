////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

/**
 * A {@code Message} represents the output of an {@code xsl:message} or
 * {@code xsl:assert} instruction.
 * The content of a message is always an XDM document node (in the common case
 * of purely textual messages, the document node will have a single text node
 * child).
 */

public class Message {

    private final XdmNode _content;
    private final QName _errorCode;
    private final boolean _terminate;
    private final Location _location;

    /**
     * Construct a Message. This constructor is used internally by the <code>xsl:message</code>
     * instruction.
     * @param content the content of the message
     * @param errorCode the error code associated with the message
     * @param terminate true if <code>terminate="yes"</code> was specified on the xsl:message
     * @param location the location of the xsl:message instruction
     */

    public Message(XdmNode content, QName errorCode, boolean terminate, Location location) {
        this._content = content;
        this._errorCode = errorCode;
        this._terminate = terminate;
        this._location = location;
    }

    /**
     * Get the content of the message
     * @return the content of the message as an XDM document node
     */

    public XdmNode getContent() {
        return _content;
    }

    /**
     * Get the string value of the message. This is a shortcut for {@code getContent().getStringValue()}.
     * It returns the content of the message as a string, without XML escaping, and ignoring any XML markup.
     * @return the message as an unescaped string
     */

    public String getStringValue() {
        return _content.getStringValue();
    }

    /**
     * Get the message content as a string, using XML serialization with options method="xml",
     * indent="yes", omit-xml-declaration="yes". This is a shortcut for
     * {@code getContent().toString()}. The result may include XML markup and XML escaping,
     * though there is no guarantee that it will be a well-formed XML document (it may be
     * a document fragment).
     */

    public String toString() {
        return _content.toString();
    }

    /**
     * Get the error code associated with the message. If no error
     * code was supplied in the call of {@code xsl:message} or {@code xsl:assert},
     * the default code {@code XTMM9000} or {@code XTMM9001} is used.
     * @return the error code
     */

    public QName getErrorCode() {
        return _errorCode;
    }

    /**
     * Ask whether <code>terminate="yes"</code> was specified on the
     * call of {@code xsl:message}. For an {@code xsl:assert} instruction
     * the value is always true.
     * @return true if the transformation will terminate after the message
     * has been output
     */

    public boolean isTerminate() {
        return _terminate;
    }

    /**
     * Get the location of the {@code xsl:message} or {@code xsl:assert}
     * instruction within the containing stylesheet
     * @return the location of the originating instruction
     */

    public Location getLocation() {
        return _location;
    }


}

