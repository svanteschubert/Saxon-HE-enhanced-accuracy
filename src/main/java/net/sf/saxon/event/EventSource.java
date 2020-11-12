////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Source;

/**
 * An implementation of the JAXP Source class that supplies a document in the form of a stream
 * of push events sent to a Receiver
 *
 * @since 9.1
 */
public abstract class EventSource implements Source {

    private String systemId;

    /**
     * Set the system identifier for this Source.
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
     *         if setSystemId was not called.
     */
    @Override
    public String getSystemId() {
        return systemId;
    }

    /**
     * Supply events to a Receiver.
     *
     * @param out the Receiver to which events will be sent. It is the caller's responsibility
     *            to initialize the receiver with a PipelineConfiguration, and to call the open() and close()
     *            methods on the receiver before and after calling this send() method.
     * @throws net.sf.saxon.trans.XPathException
     *          if any error occurs
     */

    public abstract void send(Receiver out) throws XPathException;
}

