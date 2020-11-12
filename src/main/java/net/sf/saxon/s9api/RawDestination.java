////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.event.*;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.XPathException;

/**
 * An <code>RawDestination</code> is a {@link Destination} that accepts a sequence output
 * by a stylesheet or query and returns it directly as an {@link XdmValue}, without
 * constructing an XML tree, and without serialization. It corresponds to the serialization
 * option <code>build-tree="no"</code>
 */

public class RawDestination extends AbstractDestination {

    private SequenceCollector sequenceOutputter;
    private boolean closed = false;


    public RawDestination() {

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
    public Receiver getReceiver(PipelineConfiguration pipe, SerializationProperties params) {
        // The Receiver returned by this method is a SequenceOutputter. The SequenceOutputter
        // builds a list of all top-level items passed to it. A top-level document or element
        // node can be passed as a sequence of events, in which case a ComplexContentOutputter
        // is created to build the tree represented by these events; the root document or element
        // node in this tree is then added to the same list as a composed items. On completion
        // the sequence represented by the list of items is available by calling the getXmlValue()
        // method.
        sequenceOutputter = new SequenceCollector(pipe);
        closed = false;
        helper.onClose(() -> closed=true);
        return new CloseNotifier(sequenceOutputter, helper.getListeners());
    }

    /**
     * Close the destination, allowing resources to be released. Saxon calls this method when
     * it has finished writing to the destination.
     */

    @Override
    public void close() throws SaxonApiException {
        try {
            sequenceOutputter.close();
            closed = true;
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Return the result sequence, after it has been constructed.
     * <p>This method should not be called until the destination has been closed.</p>
     *
     * @return the the result sequence
     * @throws IllegalStateException if called during the execution of the process that
     *                               is writing the tree.
     */

    public XdmValue getXdmValue() {
        if (!closed) {
            throw new IllegalStateException("The result sequence has not yet been closed");
        }
        return XdmValue.wrap(sequenceOutputter.getSequence());
    }

}

