////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Sender;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.ActiveSource;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.trans.XPathException;

/**
 * A JAXP Source object representing a Saxon {@link NodeInfo}. The class implements the
 * {@link ActiveSource} interface, allowing the content to be delivered to a {@link Receiver}.
 */
public class NodeSource implements ActiveSource {

    private final NodeInfo node;
    private String systemId;

    /**
     * Create a <code>NodeSource</code> that wraps a supplied node
     * @param node the supplied node
     */

    public NodeSource(NodeInfo node) {
        this.node = node;
        this.systemId = node.getSystemId();
    }

    /**
     * Deliver the content of the source to a supplied Receiver.
     * <p>For many (but not all) implementations of {@code Source}, this method consumes
     * the source and can therefore only be called once.</p>
     *
     * @param receiver the receiver to which events representing the parsed XML document will be sent
     * @param options  options for parsing the source
     * @throws XPathException if parsing fails for any reason. The detailed diagnostics will
     *                        have been sent to the error reporter.
     */
    @Override
    public void deliver(Receiver receiver, ParseOptions options) throws XPathException {
        Sender.sendDocumentInfo(node, receiver, new Loc(getSystemId(), -1, -1));
    }

    /**
     * Set the system identifier for this Source.
     *
     * <p>The system identifier is optional if the source does not
     * get its data from a URL, but it may still be useful to provide one.
     * The application can use a system identifier, for example, to resolve
     * relative URIs and to include in error messages and warnings.</p>
     *
     * @param systemId The system identifier as a URL string.
     */
    @Override
    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Get the system identifier that was set with setSystemId.
     *
     * @return The system identifier that was set with setSystemId, or null
     * if setSystemId was not called.
     */
    @Override
    public String getSystemId() {
        if (systemId == null) {
            return node.getSystemId();
        } else {
            return this.systemId;
        }
    }

    /**
     * Get the node represented by this {@link NodeSource} object
     * @return the wrapped node
     */

    public NodeInfo getNode() {
        return node;
    }
};


