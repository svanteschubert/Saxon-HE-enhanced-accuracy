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
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;

/**
 * A node in the XML parse tree representing character content.
 *
 * @author Michael H. Kay
 */

public class TextImpl extends NodeImpl {

    private String content;

    public TextImpl(String content) {
        this.content = content;
    }

    /**
     * Append to the content of the text node
     *
     * @param content the new content to be appended
     */

    public void appendStringValue(String content) {
        this.content = this.content + content;
    }

    /**
     * Return the character value of the node.
     *
     * @return the string value of the node
     */

    @Override
    public String getStringValue() {
        return content;
    }

    /**
     * Return the type of node.
     *
     * @return Type.TEXT
     */

    @Override
    public final int getNodeKind() {
        return Type.TEXT;
    }

    /**
     * Copy this node to a given outputter
     */

    @Override
    public void copy(/*@NotNull*/ Receiver out, int copyOptions, Location locationId) throws XPathException {
        out.characters(content, locationId, ReceiverOption.NONE);
    }


    /**
     * Replace the string-value of this node
     *
     * @param stringValue the new string value
     */

    @Override
    public void replaceStringValue(/*@NotNull*/ CharSequence stringValue) {
        if (stringValue.length() == 0) {
            delete();
        } else {
            content = stringValue.toString();
        }
    }

}

