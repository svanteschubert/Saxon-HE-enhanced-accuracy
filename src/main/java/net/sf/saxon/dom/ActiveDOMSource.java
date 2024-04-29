////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.ActiveSource;
import net.sf.saxon.trans.XPathException;
import org.w3c.dom.Node;

import javax.xml.transform.dom.DOMSource;

/**
 * An extension of {@link DOMSource} that implements the {@link ActiveSource} interface,
 * so it knows how to send itself to a {@link Receiver}
 */

public class ActiveDOMSource extends DOMSource implements ActiveSource {

    public ActiveDOMSource(Node node, String systemId) {
        super(node, systemId);
    }

    /**
     * Construct an ActiveDOMSource from a DOMSource
     * @param source the input source object, whose properties are copied.
     */
    public ActiveDOMSource(DOMSource source) {
        setNode(source.getNode());
        setSystemId((source.getSystemId()));
    }

    @Override
    public void deliver(Receiver receiver, ParseOptions options) throws XPathException {
        DOMObjectModel.sendDOMSource(this, receiver);
    }
}


