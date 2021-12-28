////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.UntypedAtomicValue;

/**
 * A node in the XML parse tree representing character content
 *
 * @author Michael H. Kay
 */

public final class TinyTextImpl extends TinyNodeImpl {

    /**
     * Create a text node
     *
     * @param tree   the tree to contain the node
     * @param nodeNr the internal node number
     */

    public TinyTextImpl(TinyTree tree, int nodeNr) {
        this.tree = tree;
        this.nodeNr = nodeNr;
    }

    /**
     * Return the character value of the node.
     *
     * @return the string value of the node
     */

    @Override
    public String getStringValue() {
        return getStringValueCS().toString();
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    @Override
    public CharSequence getStringValueCS() {
        int start = tree.alpha[nodeNr];
        int len = tree.beta[nodeNr];
        return tree.charBuffer.subSequence(start, start + len);
    }

    /**
     * Static method to get the string value of a text node without first constructing the node object
     *
     * @param tree   the tree
     * @param nodeNr the node number of the text node
     * @return the string value of the text node
     */

    public static CharSequence getStringValue(TinyTree tree, int nodeNr) {
        int start = tree.alpha[nodeNr];
        int len = tree.beta[nodeNr];
        return tree.charBuffer.subSequence(start, start + len);
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
        out.characters(getStringValueCS(), locationId, ReceiverOption.NONE);
    }

    /**
     * Get the typed value.  However, this method is often more convenient and may be
     * more efficient, especially in the common case where the value is expected to be a singleton.
     *
     * @return the typed value. It will be a Value representing a sequence whose items are atomic
     *         values.
     * @since 8.5
     */

    /*@NotNull*/
    @Override
    public AtomicSequence atomize() throws XPathException {
        return new UntypedAtomicValue(getStringValueCS());
    }
}

