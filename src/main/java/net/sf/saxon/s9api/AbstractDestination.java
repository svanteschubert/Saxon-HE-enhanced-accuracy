////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import java.net.URI;
import java.util.function.Consumer;

/**
 * An abstract class providing reusable code for implementing the {@link Destination} interface}
 */
abstract public class AbstractDestination implements Destination {

    protected DestinationHelper helper = new DestinationHelper(this);
    private URI baseURI;

    /**
     * Set the base URI of the resource being written to this destination
     *
     * @param baseURI the base URI to be used
     */

    @Override
    public void setDestinationBaseURI(URI baseURI) {
        this.baseURI = baseURI;
    }

    /**
     * Get the base URI of the resource being written to this destination
     *
     * @return the baseURI, or null if none is known
     */

    @Override
    public URI getDestinationBaseURI() {
        return baseURI;
    }

    /**
     * Register a listener to be notified when this destination is closed
     * @param listener an object to be notified when writing to the destination
     *                 is successfully completed
     */

    @Override
    final public void onClose(Action listener) {
        helper.onClose(listener);
    }

    /**
     * Close the destination and notify all registered listeners that it has been closed.
     * This method is intended for internal use by Saxon. The method first calls {@link #close}
     * to close the destination, then it calls {@link Consumer#accept} on each of the
     * listeners in turn to notify the fact that it has been closed.
     * @throws SaxonApiException if the close() method throws {@code SaxonApiException}.
     */

    @Override
    public void closeAndNotify() throws SaxonApiException {
        helper.closeAndNotify();
    }

}

