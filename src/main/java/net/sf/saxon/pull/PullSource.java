////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pull;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Sender;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.ActiveSource;
import net.sf.saxon.trans.XPathException;

/**
 * A PullSource is a JAXP Source that encapsulates a PullProvider - that is, an object
 * that supplies an XML document as a sequence of events that are read under the control
 * of the recipient. Note that although PullSource implements the JAXP Source interface,
 * it is not necessarily acceptable to every JAXP implementation that accepts a Source
 * as input: Source is essentially a marker interface and users of Source objects need
 * to understand the individual implementation.
 */

public class PullSource implements ActiveSource {

    private String systemId;
    private final PullProvider provider;

    /**
     * Create a PullSource based on a supplied PullProvider
     *
     * @param provider the underlying PullProvider
     */

    public PullSource(/*@NotNull*/ PullProvider provider) {
        this.provider = provider;
        if (provider.getSourceLocator() != null) {
            systemId = provider.getSourceLocator().getSystemId();
        }
    }

    /**
     * Get the PullProvider
     *
     * @return the underlying PullProvider
     */

    public PullProvider getPullProvider() {
        return provider;
    }

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

    @Override
    public void deliver(Receiver receiver, ParseOptions options) throws XPathException {
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        boolean xInclude = options.isXIncludeAware();
        if (xInclude) {
            throw new XPathException("XInclude processing is not supported with a pull parser");
        }
        // TODO: Sender has already put a validator on the pipeline...?
        receiver = Sender.makeValidator(receiver, getSystemId(), options);

        PullProvider provider = getPullProvider();

        provider.setPipelineConfiguration(pipe);
        receiver.setPipelineConfiguration(pipe);
        PullPushCopier copier = new PullPushCopier(provider, receiver);
        try {
            copier.copy();
        } finally {
            if (options.isPleaseCloseAfterUse()) {
                provider.close();
            }
        }
    }
}

