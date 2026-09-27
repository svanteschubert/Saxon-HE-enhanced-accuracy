////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;


/**
 * TinyCommentImpl is a comment node in the TinyTree
 *
 */


final class TinyCommentImpl extends TinyNodeImpl {

    public TinyCommentImpl(TinyTree tree, int nodeNr) {
        super(tree, nodeNr);
    }


    /**
     * Get the XPath string value of the comment
     * @return the string value
     */

    @Override
    public UnicodeString getUnicodeStringValue() {
        int start = tree.alpha[nodeNr];
        int len = tree.beta[nodeNr];
        if (len == 0) {
            return EmptyUnicodeString.getInstance();
        }
        return tree.commentBuffer.substring(start, start + len);
    }

    /**
     * Get the typed value of this node.
     * Returns the string value, as an instance of xs:string
     */

    @Override
    public AtomicSequence atomize() {
        return new StringValue(getUnicodeStringValue());
    }


    /**
     * Get the node type
     *
     * @return Type.COMMENT
     */

    @Override
    public int getNodeKind() {
        return Type.COMMENT;
    }

    /**
     * Copy this node to a given outputter
     */

    @Override
    public void copy(/*@NotNull*/ Receiver out, int copyOptions, Location locationId) throws XPathException {
        out.comment(getUnicodeStringValue(), locationId, ReceiverOption.NONE);
    }

}

