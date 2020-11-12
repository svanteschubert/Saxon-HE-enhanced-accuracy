////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Sink;
import net.sf.saxon.serialize.SerializationProperties;

/**
 * A NullDestination is a Destination that discards all output sent to it.
 * @since 9.9
 */
public class NullDestination extends AbstractDestination {

    @Override
    public Receiver getReceiver(PipelineConfiguration pipe, SerializationProperties params) {
        return new Sink(pipe);
    }

    @Override
    public void close() {}
}
