////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.TeeOutputter;
import net.sf.saxon.serialize.SerializationProperties;

/**
 * A TeeDestination allows writing to two destinations at once. For example the output of a transformation
 * can be written simultaneously to a Serializer and to a second Transformation. By chaining together a number
 * of TeeDestinations it is possible to write to any number of destinations at once.
 *
 * @since 9.1
 */

public class TeeDestination extends AbstractDestination {

    private Destination dest0;
    private Destination dest1;

    /**
     * Create a TeeDestination: a destination which copies everything that is sent to it to two
     * separate destinations
     *
     * @param destination0 the first destination
     * @param destination1 the second destination
     */

    public TeeDestination(Destination destination0, Destination destination1) {
        dest0 = destination0;
        dest1 = destination1;
    }

    /**
     * Return a Receiver. Saxon calls this method to obtain a Receiver, to which it then sends
     * a sequence of events representing the content of an XML document.
     *
     * @param pipe The Saxon configuration. This is supplied so that the destination can
     *               use information from the configuration (for example, a reference to the name pool)
     *               to construct or configure the returned Receiver.
     * @param params
     * @return the Receiver to which events are to be sent. It is the caller's responsibility to
     *         initialize this Receiver with a {@link net.sf.saxon.event.PipelineConfiguration} before calling
     *         its <code>open()</code> method.
     * @throws net.sf.saxon.s9api.SaxonApiException
     *          if the Receiver cannot be created
     */

    /*@NotNull*/
    @Override
    public Receiver getReceiver(PipelineConfiguration pipe, SerializationProperties params) throws SaxonApiException {
        return new TeeOutputter(dest0.getReceiver(pipe, params), dest1.getReceiver(pipe, params));
    }

    /**
     * Close the destination, allowing resources to be released. Saxon calls this method when
     * it has finished writing to the destination.
     */

    @Override
    public void close() throws SaxonApiException {
        dest0.close();
        dest1.close();
    }
}

