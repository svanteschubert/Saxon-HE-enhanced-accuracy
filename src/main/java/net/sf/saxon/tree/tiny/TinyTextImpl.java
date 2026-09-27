////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;

/**
 * A node in the XML parse tree representing character content
 */

public final class TinyTextImpl extends TinyNodeImpl {

    /**
     * Create a text node
     *
     * @param tree   the tree to contain the node
     * @param nodeNr the internal node number
     */

    public TinyTextImpl(TinyTree tree, int nodeNr) {
        super(tree, nodeNr);
    }

    /**
     * Static method to get the string value of a text node without first constructing the node object
     *
     * @param tree   the tree
     * @param nodeNr the node number of the text node
     * @return the string value of the text node
     */

    public static UnicodeString getStringValue(TinyTree tree, int nodeNr) {
//        return tree.textChunks[tree.alpha[nodeNr]];
        int start = tree.alpha[nodeNr];
        int len = tree.beta[nodeNr];
        //System.err.println("TinyTextImpl.getXdmStringValue node=" + nodeNr + " s=" + start + " len=" + len);
        return tree.textBuffer.substring(start, start + len);
    }

    /**
     * Return the character value of the node.
     *
     * @return the string value of the node
     */

    @Override
    public UnicodeString getUnicodeStringValue() {
        return getStringValue(tree, nodeNr);
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
    public void copy(Receiver out, int copyOptions, Location locationId) throws XPathException {
        out.characters(getUnicodeStringValue(), locationId, ReceiverOption.NONE);
    }

    /**
     * Get the typed value.
     *
     * @return the typed value. It will be a Value representing a sequence whose items are atomic
     *         values.
     * @since 8.5
     */

    /*@NotNull*/
    @Override
    public AtomicSequence atomize() {
        return StringValue.makeUntypedAtomic(getUnicodeStringValue());
    }
}

