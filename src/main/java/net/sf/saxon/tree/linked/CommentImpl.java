////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;

/**
 * CommentImpl is an implementation of a Comment node
 *
 * @author Michael H. Kay
 */


public class CommentImpl extends NodeImpl {

    String comment;
    String systemId;
    int lineNumber = -1;
    int columnNumber = -1;

    public CommentImpl(String content) {
        this.comment = content;
    }

    @Override
    public final String getStringValue() {
        return comment;
    }

    /**
     * Get the typed value of this node.
     * Returns the string value, as an instance of xs:string
     */

    /*@NotNull*/
    @Override
    public AtomicSequence atomize() {
        return new StringValue(getStringValue());
    }

    @Override
    public final int getNodeKind() {
        return Type.COMMENT;
    }

    /**
     * Copy this node to a given outputter
     */

    @Override
    public void copy(/*@NotNull*/ Receiver out, int copyOptions, Location locationId) throws XPathException {
        out.comment(comment, locationId, ReceiverOption.NONE);
    }


    /**
     * Replace the string-value of this node
     *
     * @param stringValue the new string value
     */

    @Override
    public void replaceStringValue(/*@NotNull*/ CharSequence stringValue) {
        comment = stringValue.toString();
    }

    /**
     * Set the system ID and line number
     *
     * @param uri        the system identifier
     * @param lineNumber the line number
     */

    public void setLocation(String uri, int lineNumber, int columnNumber) {
        this.systemId = uri;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    /**
     * Get the system ID for the entity containing this node.
     *
     * @return the system identifier
     */

    @Override
    public String getSystemId() {
        return systemId;
    }

    /**
     * Get the line number of the node within its source entity
     */

    @Override
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Get the column number of the node within its source entity
     */

    @Override
    public int getColumnNumber() {
        return columnNumber;
    }


}

