////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.sapling;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;

import java.util.Objects;

/**
 * A comment node in a sapling tree
 */

public class SaplingComment extends SaplingNode {

    private String value;

    /**
     * Construct a sapling comment node with a given string value
     * @param value the string value of the comment node. This should not contain "--" as a substring,
     *              but this condition is not checked.
     * @throws NullPointerException if the supplied value is null.
     */

    public SaplingComment(String value) {
        Objects.requireNonNull(value);
        this.value = value;
    }

    @Override
    public int getNodeKind() {
        return Type.COMMENT;
    }

    /**
     * Get the string value of the comment node
     * @return the string value of the node
     */

    public String getStringValue() {
        return value;
    }

    @Override
    protected void sendTo(Receiver receiver) throws XPathException {
        receiver.comment(value, Loc.NONE, ReceiverOption.NONE);
    }
}


