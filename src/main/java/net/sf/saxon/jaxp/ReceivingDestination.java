////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.jaxp;

import net.sf.saxon.event.*;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.s9api.AbstractDestination;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.serialize.SerializationProperties;

/**
 * An implementation of {@code Destination} that simply wraps a supplied {@code Receiver}
 * <p>The supplied {@code Receiver} will be wrapped in a sequence normalizer unless requested
 * otherwise. Specifically, it is wrapped unless the method {@link #acceptsRawOutput()}
 * returns true.</p>
 */

public class ReceivingDestination extends AbstractDestination {

    private Receiver outputTarget;

    /**
     * Create a {@code ReceivingDestination} that wraps a supplied {@code Receiver}
     * @param target the supplied {@code Receiver}. This must accept a <b>regular event sequence</b>
     *               as defined in {@link net.sf.saxon.event.RegularSequenceChecker}
     */

    public ReceivingDestination(Receiver target) {
        this.outputTarget = target;
    }

    /**
     * Get a {@code Receiver} to which events may be sent. For this implementation, this
     * will be the wrapped {@code Receiver}.
     * @param pipe The Saxon configuration. Not used in this implementation.
     * @param properties The required serialization properties (not used)
     * @return the wrapped {@code Receiver}
     */

    @Override
    public Receiver getReceiver(PipelineConfiguration pipe, SerializationProperties properties) {
        if (acceptsRawOutput()) {
            return outputTarget;
        } else {
            return properties.makeSequenceNormalizer(outputTarget);
        }
    }

    /**
     * Ask whether this receiver accepts raw output, that is, an arbitrary sequence of items
     * not necessarily forming an XML document or element tree. May be overridden in a subclass.
     * @return false unless one of the following conditions is true:
     *  <ol>
     *      <li>The {@code Receiver} is an instance of {@link SequenceNormalizer}</li>
     *      <li>The {@code Receiver} implements {@link ReceiverWithOutputProperties},
     *      and a call on {@code getOutputProperties(SaxonOutputPropertes.REQUIRE_WELL_FORMED)} returns "no"</li>
     *      <li>The method {#acceptRawOutput()} is implemented in a subclass of {@code ReceivingDestination},
     *      and returns false.</li>
     *  </ol>
     */

    public boolean acceptsRawOutput() {
        if (outputTarget instanceof SequenceNormalizer) {
            return true;
        }
        if (outputTarget instanceof ReceiverWithOutputProperties) {
            return "no".equals(((ReceiverWithOutputProperties)outputTarget)
                                       .getOutputProperties().getProperty(SaxonOutputKeys.REQUIRE_WELL_FORMED));
        }
        return false;
    }

    @Override
    public void close() throws SaxonApiException {
//        try {
//            outputTarget.close();
//        } catch (XPathException e) {
//            throw new SaxonApiException(e);
//        }
    }
}


