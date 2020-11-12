////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.stax;

import net.sf.saxon.event.NamespaceReducer;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.s9api.AbstractDestination;
import net.sf.saxon.s9api.Destination;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.serialize.SerializationProperties;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * XMLStreamWriterDestination is a s9api {@link Destination} that writes output to a supplied XMLStreamWriter
 */
public class XMLStreamWriterDestination extends AbstractDestination {

    private XMLStreamWriter writer;

    /**
     * Create an XMLStreamWriterDestination based on a supplied XMLStreamWriter
     * @param writer the supplied XmlStreamWriter
     */

    public XMLStreamWriterDestination(XMLStreamWriter writer) {
        this.writer = writer;
    }

    /**
     * Get the XMLStreamWriter to which this XMLStreamWriterDestination is writing
     * @return the XMLStreamWriter that was provided as the destination of this
     * XMLStreamWriterDestination
     */

    public XMLStreamWriter getXMLStreamWriter() {
        return writer;
    }

    /**
     * Return a Receiver. Saxon calls this method to obtain a Receiver, to which it then sends
     * a sequence of events representing the content of an XML document. The method is intended
     * primarily for internal use, and may give poor diagnostics if used incorrectly.
     *
     * @param pipe The Saxon configuration. This is supplied so that the destination can
     *               use information from the configuration (for example, a reference to the name pool)
     *               to construct or configure the returned Receiver.
     * @param params
     * @return the Receiver to which events are to be sent. It is the caller's responsibility to
     *         initialize this Receiver with a {@link net.sf.saxon.event.PipelineConfiguration} before calling
     *         its <code>open()</code> method. The caller is also responsible for ensuring that the sequence
     *         of events sent to the Receiver represents a well-formed document: in particular the event
     *         stream must include namespace events corresponding exactly to the namespace declarations
     *         that are required. If the calling code cannot guarantee this, it should insert a
     *         {@link NamespaceReducer} into the pipeline in front of the returned
     *         Receiver.
     * @throws SaxonApiException if the Receiver cannot be created
     */

    @Override
    public Receiver getReceiver(PipelineConfiguration pipe, SerializationProperties params) throws SaxonApiException {
        Receiver r = new ReceiverToXMLStreamWriter(writer);
        r.setPipelineConfiguration(pipe);
        return r;
    }

    /**
     * Close the destination, allowing resources to be released. Saxon calls this method when
     * it has finished writing to the destination.
     * <p>The close() method should not cause any adverse effects if it is called more than
     * once. If any other method is called after the close() call, the results are undefined.
     * This means that a Destination is not, in general, serially reusable.</p>
     *
     * @throws SaxonApiException if any failure occurs
     */


    @Override
    public void close() throws SaxonApiException {
        try {
            writer.close();
        } catch (XMLStreamException e) {
            throw new SaxonApiException(e);
        }
    }
}
