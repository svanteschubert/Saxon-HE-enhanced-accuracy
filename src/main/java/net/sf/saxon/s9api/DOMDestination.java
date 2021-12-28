////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.dom.DOMWriter;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.serialize.SerializationProperties;

/**
 * This class represents a Destination (for example, the destination of the output of a transformation)
 * in which the results are written to a newly constructed DOM tree in memory. The caller must supply
 * a Document node, which will be used as the root of the constructed tree
 */

public class DOMDestination extends AbstractDestination {

    private DOMWriter domWriter;

    /**
     * Create a DOMDestination, supplying a node in a DOM document to which the
     * content of the result tree will be attached.
     *
     * @param root the root node for the new tree. This must be a document or element node.
     */

    public DOMDestination(org.w3c.dom.Node root) {
        domWriter = new DOMWriter();
        domWriter.setNode(root);
    }

    /**
     * Return a Receiver. Saxon calls this method to obtain a Receiver, to which it then sends
     * a sequence of events representing the content of an XML document.
     *
     * @param pipe The Saxon configuration. This is supplied so that the destination can
     *               use information from the configuration (for example, a reference to the name pool)
     *               to construct or configure the returned Receiver.
     * @param params
     * @return the Receiver to which events are to be sent.
     */

    @Override
    public Receiver getReceiver(/*@NotNull*/ PipelineConfiguration pipe, SerializationProperties params) {
        domWriter.setPipelineConfiguration(pipe);
        return params.makeSequenceNormalizer(domWriter);
    }

    /**
     * Close the destination, allowing resources to be released. Saxon calls this method when
     * it has finished writing to the destination.
     */

    @Override
    public void close() throws SaxonApiException {
        // no action
    }
}
