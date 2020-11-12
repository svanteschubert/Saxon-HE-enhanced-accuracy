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
 * A processing-instruction node in a sapling tree
 */

public class SaplingProcessingInstruction extends SaplingNode {

    private String name;
    private String value;

    /**
     * Construct a sapling processing-instruction node with a given name and string value
     * @param name the name of the processing-instruction node (also called the "target").
     *             This should be an NCName; but this is not checked.
     * @param value the string value of the processing-instruction node (also called the "data").
     *              This should not start with an initial space,
     *              and it should not contain the substring {@code "?>"}, but this is not checked.
     * @throws NullPointerException if either name or value is null
     */

    public SaplingProcessingInstruction(String name, String value) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
        this.name = name;
        this.value = value;
    }

    /**
     * Get the name of the processing instruction node
     * @return the name of the node (the processing-instruction target)
     */

    public String getName() {
        return name;
    }

    /**
     * Get the string value of the processing instruction node
     * @return the string value of the node (the processing-instruction data)
     */

    public String getStringValue() {
        return value;
    }

    @Override
    public int getNodeKind() {
        return Type.PROCESSING_INSTRUCTION;
    }

    @Override
    protected void sendTo(Receiver receiver) throws XPathException {
        receiver.processingInstruction(name, value, Loc.NONE, ReceiverOption.NONE);
    }
}


