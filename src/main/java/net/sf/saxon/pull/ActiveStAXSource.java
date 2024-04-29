////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pull;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.ActiveSource;
import net.sf.saxon.trans.XPathException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stax.StAXSource;

/**
 * An extension of StAXSource that makes the source active: that is, able
 * to deliver itself to a Saxon {@link Receiver}.
 *
 * <p>Saxon can only handle a StAXSource that wraps an XMLStreamReader (not an
 * XMLEventReader)</p>
 */

public class ActiveStAXSource implements ActiveSource {

    StAXSource underlyingSource;

    public ActiveStAXSource(StAXSource source) {
        this.underlyingSource = source;
    }

    public static ActiveStAXSource fromStAXSource(StAXSource source) throws XMLStreamException {
        if (source.getXMLStreamReader() != null) {
            return new ActiveStAXSource(source);
        } else {
            throw new XMLStreamException("Saxon can only handle a StAXSource that wraps an XMLStreamReader");
        }
    }

    @Override
    public void setSystemId(String systemId) {
        underlyingSource.setSystemId(systemId);
    }

    @Override
    public String getSystemId() {
        return underlyingSource.getSystemId();
    }

    @Override
    public void deliver(Receiver receiver, ParseOptions options) throws XPathException {
        XMLStreamReader reader = underlyingSource.getXMLStreamReader();
        if (reader == null) {
            throw new XPathException("Saxon can only handle a StAXSource that wraps an XMLStreamReader");
        }
        StaxBridge bridge = new StaxBridge();
        bridge.setXMLStreamReader(reader);
        new PullSource(bridge).deliver(receiver, options);
    }
}


