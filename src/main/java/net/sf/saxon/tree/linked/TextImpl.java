////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;

/**
 * A node in the XML parse tree representing character content.
 *
 */

public class TextImpl extends NodeImpl {

    private UnicodeString content;

    // The location of a text node is held compactly, if available. A value of -1 indicates
    // that the information is not available. Any other value is a pair of 16 bit
    // integers. The first holds the line number relative to the line number of the
    // parent element node, the second holds the absolute column number.

    private int locationDelta = -1;


    public TextImpl(UnicodeString content) {
        this.content = content;
    }

    /**
     * Append to the content of the text node
     *
     * @param content the new content to be appended
     */

    public void appendStringValue(UnicodeString content) {
        this.content = this.content.concat(content);
    }

    /**
     * Return the character value of the node.
     *
     * @return the string value of the node
     */

    @Override
    public UnicodeString getUnicodeStringValue() {
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
    public void replaceStringValue(UnicodeString stringValue) {
        if (stringValue.isEmpty()) {
            delete();
        } else {
            content = stringValue;
        }
    }


    /**
     * Get the line number of this text node, if available
     * @return the line number, or -1 if not available
     */

    @Override
    public int getLineNumber() {
        if (locationDelta == -1) {
            return -1;
        }
        NodeImpl parent = getParent();
        if (parent == null) {
            return -1;
        }
        int lineOffset = (locationDelta >> 16) & 0xffff;
        int parentLine = parent.getLineNumber();
        if (parentLine == -1) {
            return -1;
        }
        return parentLine + lineOffset;
    }

    /**
     * Get the column number of this text node, if available
     *
     * @return the column number, or -1 if not available
     */

    @Override
    public int getColumnNumber() {
        if (locationDelta == -1) {
            return -1;
        }
        return locationDelta & 0xffff;
    }

    public void setLocation(Location loc) {
        NodeImpl parent = getParent();
        if (parent == null) {
            return;
        }
        int line = loc.getLineNumber();
        int parentLine = parent.getLineNumber();
        if (line == -1 || parentLine == -1) {
            return;
        }
        int lineDelta = line - parentLine;
        if (lineDelta >= 65535) {
            return;
        }
        int col = loc.getColumnNumber();
        if (col < 0 || col > 65535) {
            return;
        }
        locationDelta = lineDelta << 16 | col;

    }
}

